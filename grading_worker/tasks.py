"""Celery tasks for the grading pipeline."""
import json
import uuid
import redis as redis_lib
from decimal import Decimal
from minio import Minio
from celery_app import app
from config import (
    MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET, MINIO_SECURE,
    REDIS_HOST, REDIS_PORT, RESULT_CHANNEL,
)
from models.pipeline_models import (
    TaskMessage, EvidenceBlock, ImageKind,
)
from models.db_models import (
    get_session, GradingSubmission, GradingRubric, RubricDimension,
    EvidenceBlock as EvidenceBlockDB, ScoreItem, GradingTrace,
)
from pipeline.pdf_parser import parse_pdf
from pipeline.image_classifier import classify_image
from pipeline.ocr_processor import run_ocr
from pipeline.vlm_client import call_vlm
from pipeline.evidence_builder import build_evidence_packs
from pipeline.scorer import score_dimension
from pipeline.score_calculator import calculate_weighted_total
from pipeline.trace_logger import trace_step


def _get_minio():
    return Minio(MINIO_ENDPOINT, access_key=MINIO_ACCESS_KEY,
                 secret_key=MINIO_SECRET_KEY, secure=MINIO_SECURE)


def _get_redis():
    return redis_lib.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0, decode_responses=True)


@app.task(bind=True, max_retries=3, default_retry_delay=30)
def process_submission(self, task_message_json: str):
    """Main pipeline task: process a single student submission."""
    msg = TaskMessage(**json.loads(task_message_json))
    session = get_session()
    r = _get_redis()

    try:
        # Update submission status to PROCESSING
        sub = session.query(GradingSubmission).get(msg.submissionId)
        if not sub:
            return
        sub.status = "PROCESSING"
        session.commit()

        # 1. Download PDF from MinIO
        minio_client = _get_minio()
        with trace_step(msg.submissionId, "pdf_download") as info:
            response = minio_client.get_object(MINIO_BUCKET, msg.pdfObjectKey)
            pdf_bytes = response.read()
            response.close()
            response.release_conn()

        # 2. Parse PDF
        with trace_step(msg.submissionId, "pdf_parse") as info:
            parsed = parse_pdf(pdf_bytes)
            if parsed.error:
                _fail_submission(session, sub, parsed.error, r, msg.submissionId)
                return

        # 3. Classify images and run OCR/VLM
        evidence_blocks: list[EvidenceBlock] = []
        ev_counter = 0

        for page in parsed.pages:
            # Add page text as evidence
            if page.text.strip():
                ev_counter += 1
                evidence_blocks.append(EvidenceBlock(
                    evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                    kind="text", page=page.page_num,
                    content=page.text[:2000],  # Truncate long text
                ))

            for img in page.images:
                # Classify
                with trace_step(msg.submissionId, "image_classify") as info:
                    kind = classify_image(img.image_bytes)
                    img.kind = kind

                # Skip very small images (likely icons/decorations)
                if img.bbox and len(img.bbox) == 4:
                    w = abs(img.bbox[2] - img.bbox[0])
                    h = abs(img.bbox[3] - img.bbox[1])
                    if w < 20 or h < 20:
                        continue

                # All image types: try OCR first
                ocr_text = ""
                ocr_conf = 0.0
                with trace_step(msg.submissionId, "ocr") as info:
                    ocr_result = run_ocr(img.image_bytes)
                    ocr_text = ocr_result.text.strip()
                    ocr_conf = ocr_result.confidence

                if kind in (ImageKind.DIAGRAM, ImageKind.PLOT):
                    # For diagrams/plots: try VLM first, fall back to OCR
                    with trace_step(msg.submissionId, "vlm") as info:
                        vlm_result = call_vlm(img.image_bytes)

                    vlm_useful = (vlm_result.description_json
                                  and "error" not in vlm_result.description_json
                                  and "VLM not configured" not in str(vlm_result.description_json))

                    if vlm_useful:
                        ev_counter += 1
                        vlm_content = json.dumps(vlm_result.description_json, ensure_ascii=False)
                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="vlm", page=page.page_num,
                            content=vlm_content,
                            bbox=img.bbox,
                        ))
                    elif ocr_text:
                        # VLM unavailable, use OCR result instead
                        ev_counter += 1
                        img_key = f"grading/{msg.submissionId}/img-{ev_counter}.png"
                        try:
                            import io
                            minio_client.put_object(MINIO_BUCKET, img_key,
                                                     io.BytesIO(img.image_bytes),
                                                     len(img.image_bytes),
                                                     content_type="image/png")
                        except Exception:
                            img_key = None
                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="ocr", page=page.page_num,
                            content=ocr_text,
                            confidence=ocr_conf,
                            image_key=img_key,
                            bbox=img.bbox,
                        ))
                else:
                    # CODE_SCREENSHOT, TERMINAL_LOG, OTHER: use OCR
                    if ocr_text:
                        ev_counter += 1
                        img_key = f"grading/{msg.submissionId}/img-{ev_counter}.png"
                        try:
                            import io
                            minio_client.put_object(MINIO_BUCKET, img_key,
                                                     io.BytesIO(img.image_bytes),
                                                     len(img.image_bytes),
                                                     content_type="image/png")
                        except Exception:
                            img_key = None

                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="ocr", page=page.page_num,
                            content=ocr_text,
                            confidence=ocr_conf,
                            image_key=img_key,
                            bbox=img.bbox,
                        ))

        # 4. Save evidence blocks to DB
        with trace_step(msg.submissionId, "save_evidence") as info:
            for eb in evidence_blocks:
                db_eb = EvidenceBlockDB(
                    submission_id=msg.submissionId,
                    evidence_id=eb.evidence_id,
                    kind=eb.kind,
                    page=eb.page,
                    bbox_json=json.dumps(eb.bbox) if eb.bbox else None,
                    content=eb.content,
                    confidence=eb.confidence,
                    image_key=eb.image_key,
                    metadata_json=json.dumps(eb.metadata) if eb.metadata else None,
                )
                session.add(db_eb)
            session.commit()

        # 5. Load rubric dimensions
        rubric = session.query(GradingRubric).get(msg.rubricId)
        if not rubric:
            _fail_submission(session, sub, "Rubric not found", r, msg.submissionId)
            return

        dimensions = []
        for dim in rubric.dimensions:
            dimensions.append({
                "id": dim.id, "name": dim.name,
                "description": dim.description or "",
                "max_score": float(dim.max_score), "weight": int(dim.weight),
            })

        # 6. Build evidence packs
        with trace_step(msg.submissionId, "evidence_build") as info:
            packs = build_evidence_packs(evidence_blocks, dimensions)

        # 7. Score each dimension
        score_dicts = []
        for dim in dimensions:
            dim_id = dim["id"]
            pack = packs.get(dim_id)
            if not pack or not pack.blocks:
                # No evidence for this dimension
                sr_data = {
                    "score": None, "max_score": dim["max_score"],
                    "weight": dim["weight"], "status": "NEED_MORE_EVIDENCE",
                    "comment": "无可用证据", "evidence_ids": [],
                }
            else:
                with trace_step(msg.submissionId, f"score_dim_{dim_id}") as info:
                    sr, trace_info = score_dimension(pack, dim, custom_prompt=msg.customPrompt)
                    info["model_used"] = trace_info.get("model_used")
                    info["input_tokens"] = trace_info.get("input_tokens")
                    info["output_tokens"] = trace_info.get("output_tokens")
                sr_data = {
                    "score": sr.score, "max_score": sr.max_score,
                    "weight": dim["weight"], "status": sr.status,
                    "comment": sr.comment, "evidence_ids": sr.evidence_ids,
                }

            # Save score item
            db_si = ScoreItem(
                submission_id=msg.submissionId,
                dimension_id=dim_id,
                score=sr_data["score"],
                max_score=sr_data["max_score"],
                weight=sr_data["weight"],
                comment=sr_data["comment"],
                evidence_ids_json=json.dumps(sr_data["evidence_ids"]),
                status=sr_data["status"],
            )
            session.add(db_si)
            score_dicts.append(sr_data)

        session.commit()

        # 8. Calculate total score
        total = calculate_weighted_total(score_dicts)
        has_need_more = any(s["status"] == "NEED_MORE_EVIDENCE" for s in score_dicts)

        sub.total_score = Decimal(str(total))
        sub.status = "NEED_MORE_EVIDENCE" if has_need_more else "SCORED"
        session.commit()

        # 9. Generate PDF report
        with trace_step(msg.submissionId, "report_generate") as info:
            try:
                from pipeline.report_builder import generate_pdf
                from models.db_models import ReportFile

                # Build score dicts with dimension names for report
                report_scores = []
                for dim, sd in zip(dimensions, score_dicts):
                    report_scores.append({
                        "dimension_name": dim["name"],
                        "score": sd["score"],
                        "max_score": sd["max_score"],
                        "weight": sd["weight"],
                        "comment": sd["comment"],
                        "status": sd["status"],
                        "evidence_ids": sd["evidence_ids"],
                    })

                report_evidence = [
                    {"evidence_id": eb.evidence_id, "kind": eb.kind,
                     "page": eb.page, "content": eb.content}
                    for eb in evidence_blocks
                ]

                pdf_bytes = generate_pdf(
                    sub.student_name or "未知",
                    report_scores, report_evidence, float(total)
                )

                # Upload to MinIO
                report_key = f"grading/{msg.submissionId}/report.pdf"
                import io as _io
                minio_client.put_object(
                    MINIO_BUCKET, report_key,
                    _io.BytesIO(pdf_bytes), len(pdf_bytes),
                    content_type="application/pdf"
                )

                # Save report file record
                report_file = ReportFile(
                    task_id=msg.taskId,
                    submission_id=msg.submissionId,
                    file_type="pdf",
                    object_key=report_key,
                )
                session.add(report_file)
                session.commit()
            except Exception as report_err:
                info["status"] = "FAILED"
                info["error_message"] = str(report_err)[:500]
                # Don't fail the whole pipeline for report generation errors

        # 10. Notify Spring Boot via Redis
        _notify_result(r, msg.submissionId, sub.status, total)

    except Exception as exc:
        session.rollback()
        try:
            sub = session.query(GradingSubmission).get(msg.submissionId)
            if sub:
                _fail_submission(session, sub, str(exc)[:500], r, msg.submissionId)
        except Exception:
            pass
        # Retry on transient errors
        raise self.retry(exc=exc)
    finally:
        session.close()


def _fail_submission(session, sub, error_msg, redis_client, submission_id):
    sub.status = "FAILED"
    sub.error_message = error_msg
    session.commit()
    _notify_result(redis_client, submission_id, "FAILED", None)


def _notify_result(redis_client, submission_id, status, total_score):
    msg = json.dumps({
        "submissionId": submission_id,
        "status": status,
        "totalScore": str(total_score) if total_score is not None else None,
    })
    try:
        redis_client.publish(RESULT_CHANNEL, msg)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# RAG document processing task
# ---------------------------------------------------------------------------

@app.task(bind=True, max_retries=2, default_retry_delay=60)
def process_rag_document(self, task_message_json: str):
    """RAG document processing task.

    Expects a JSON string with at least ``courseSpaceDocId``.
    """
    try:
        msg = json.loads(task_message_json)
        course_space_doc_id = msg["courseSpaceDocId"]
        from pipeline.rag.rag_processor import process_document
        process_document(course_space_doc_id)
    except Exception as exc:
        raise self.retry(exc=exc)
