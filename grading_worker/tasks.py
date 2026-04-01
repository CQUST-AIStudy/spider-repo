"""Celery tasks for the grading pipeline."""
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from decimal import Decimal

from minio import Minio
import redis as redis_lib

from celery_app import app
from config import (
    DIMENSION_SCORE_CONCURRENCY,
    MINIO_ACCESS_KEY,
    MINIO_BUCKET,
    MINIO_ENDPOINT,
    MINIO_SECURE,
    MINIO_SECRET_KEY,
    OCR_STRATEGY,
    REDIS_HOST,
    REDIS_PORT,
    RESULT_CHANNEL,
)
from models.db_models import (
    EvidenceBlock as EvidenceBlockDB,
    GradingRubric,
    GradingSubmission,
    ReportFile,
    ScoreItem,
    get_session,
)
from models.pipeline_models import EvidenceBlock, ImageKind, TaskMessage
from pipeline.document_parser import parse_document
from pipeline.evidence_builder import build_evidence_packs
from pipeline.image_classifier import classify_image
from pipeline.ocr_processor import run_ocr
from pipeline.score_calculator import calculate_weighted_total
from pipeline.scorer import score_dimension, score_dimensions_batch
from pipeline.trace_logger import trace_step
from pipeline.vlm_client import call_vlm


def _get_minio():
    return Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=MINIO_SECURE,
    )


def _get_redis():
    return redis_lib.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0, decode_responses=True)


def _upload_image(minio_client, submission_id: int, ev_counter: int, image_bytes: bytes):
    """Upload evidence image to MinIO and return object key."""
    img_key = f"grading/{submission_id}/img-{ev_counter}.png"
    try:
        import io

        minio_client.put_object(
            MINIO_BUCKET,
            img_key,
            io.BytesIO(image_bytes),
            len(image_bytes),
            content_type="image/png",
        )
        return img_key
    except Exception:
        return None


def _reset_submission_artifacts(session, submission_id: int):
    """Clear stale grading rows before retrying or rescoring a submission."""
    session.query(ReportFile).filter(ReportFile.submission_id == submission_id).delete(synchronize_session=False)
    session.query(ScoreItem).filter(ScoreItem.submission_id == submission_id).delete(synchronize_session=False)
    session.query(EvidenceBlockDB).filter(EvidenceBlockDB.submission_id == submission_id).delete(synchronize_session=False)
    session.commit()


def _is_useful_ocr(text: str, confidence: float) -> bool:
    stripped = (text or "").strip()
    return len(stripped) >= 40 or (len(stripped) >= 20 and float(confidence or 0.0) >= 0.68)


def _extract_vlm_text(image_bytes: bytes):
    result = call_vlm(image_bytes, task="extract_text")
    payload = result.description_json or {}
    recognized = str(payload.get("recognized_text") or "").strip()
    summary = str(payload.get("summary") or "").strip()
    try:
        confidence = float(payload.get("confidence") or 0.0)
    except Exception:
        confidence = 0.0
    text = recognized if len(recognized) >= len(summary) else summary
    useful = ("error" not in payload) and (len(text) >= 20 or confidence >= 0.55)
    return useful, text, confidence, payload


def _vlm_describe_image(submission_id: int, image_bytes: bytes):
    with trace_step(submission_id, "vlm") as info:
        result = call_vlm(image_bytes, task="describe")
    payload = result.description_json or {}
    useful = payload and "error" not in payload and "VLM not configured" not in str(payload)
    return useful, payload


def _should_try_ocr_first() -> bool:
    return OCR_STRATEGY == "ocr_first"


def _should_allow_ocr_fallback() -> bool:
    return OCR_STRATEGY in ("ocr_first", "qwen_first")


def _run_ocr_if_needed(submission_id: int, image_bytes: bytes):
    if not _should_allow_ocr_fallback():
        return "", 0.0
    with trace_step(submission_id, "ocr") as info:
        ocr_result = run_ocr(image_bytes)
    return ocr_result.text.strip(), ocr_result.confidence


def _append_image_failure(evidence_blocks, minio_client, submission_id, ev_counter, page_num, img, kind, confidence, payload=None):
    img_key = _upload_image(minio_client, submission_id, ev_counter, img.image_bytes)
    metadata = {"image_kind": str(kind), "ocr_empty": True}
    if payload:
        metadata["vlm_payload"] = payload
    evidence_blocks.append(EvidenceBlock(
        evidence_id=f"ev-{submission_id}-{ev_counter:04d}",
        kind="vlm_failed",
        page=page_num,
        content="Image evidence exists, but the multimodal model did not extract usable content.",
        confidence=confidence,
        image_key=img_key,
        bbox=img.bbox,
        metadata=metadata,
    ))


@app.task(bind=True, max_retries=3, default_retry_delay=30)
def process_submission(self, task_message_json: str):
    """Main pipeline task: process a single student submission."""
    msg = TaskMessage(**json.loads(task_message_json))
    session = get_session()
    r = _get_redis()

    try:
        sub = session.query(GradingSubmission).get(msg.submissionId)
        if not sub:
            return

        _reset_submission_artifacts(session, msg.submissionId)
        sub.status = "PROCESSING"
        sub.total_score = None
        sub.error_message = None
        session.commit()

        minio_client = _get_minio()
        with trace_step(msg.submissionId, "document_download") as info:
            response = minio_client.get_object(MINIO_BUCKET, msg.pdfObjectKey)
            source_bytes = response.read()
            response.close()
            response.release_conn()

        with trace_step(msg.submissionId, "document_parse") as info:
            parsed = parse_document(source_bytes, msg.originalFilename)
            if parsed.error:
                _fail_submission(session, sub, parsed.error, r, msg.submissionId)
                return

        evidence_blocks: list[EvidenceBlock] = []
        ev_counter = 0

        for page in parsed.pages:
            if page.text.strip():
                ev_counter += 1
                evidence_blocks.append(EvidenceBlock(
                    evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                    kind="text",
                    page=page.page_num,
                    content=page.text[:2000],
                ))

            for img in page.images:
                with trace_step(msg.submissionId, "image_classify") as info:
                    kind = classify_image(img.image_bytes)
                    img.kind = kind

                if img.bbox and len(img.bbox) == 4:
                    w = abs(img.bbox[2] - img.bbox[0])
                    h = abs(img.bbox[3] - img.bbox[1])
                    if w < 20 or h < 20:
                        continue

                if kind in (ImageKind.DIAGRAM, ImageKind.PLOT):
                    vlm_useful, vlm_payload = _vlm_describe_image(msg.submissionId, img.image_bytes)
                    if vlm_useful:
                        ev_counter += 1
                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="vlm",
                            page=page.page_num,
                            content=json.dumps(vlm_payload, ensure_ascii=False),
                            bbox=img.bbox,
                        ))
                        continue

                    ocr_text, ocr_conf = _run_ocr_if_needed(msg.submissionId, img.image_bytes)
                    if _is_useful_ocr(ocr_text, ocr_conf):
                        ev_counter += 1
                        img_key = _upload_image(minio_client, msg.submissionId, ev_counter, img.image_bytes)
                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="ocr",
                            page=page.page_num,
                            content=ocr_text,
                            confidence=ocr_conf,
                            image_key=img_key,
                            bbox=img.bbox,
                        ))
                        continue

                    ev_counter += 1
                    _append_image_failure(
                        evidence_blocks,
                        minio_client,
                        msg.submissionId,
                        ev_counter,
                        page.page_num,
                        img,
                        kind,
                        ocr_conf,
                        vlm_payload,
                    )
                    continue

                ocr_text = ""
                ocr_conf = 0.0
                vlm_useful = False
                vlm_text = ""
                vlm_conf = 0.0
                vlm_payload = {}

                if _should_try_ocr_first():
                    ocr_text, ocr_conf = _run_ocr_if_needed(msg.submissionId, img.image_bytes)
                    if _is_useful_ocr(ocr_text, ocr_conf):
                        ev_counter += 1
                        img_key = _upload_image(minio_client, msg.submissionId, ev_counter, img.image_bytes)
                        evidence_blocks.append(EvidenceBlock(
                            evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                            kind="ocr",
                            page=page.page_num,
                            content=ocr_text,
                            confidence=ocr_conf,
                            image_key=img_key,
                            bbox=img.bbox,
                        ))
                        continue

                    with trace_step(msg.submissionId, "vlm_fallback") as info:
                        vlm_useful, vlm_text, vlm_conf, vlm_payload = _extract_vlm_text(img.image_bytes)
                else:
                    with trace_step(msg.submissionId, "vlm_primary") as info:
                        vlm_useful, vlm_text, vlm_conf, vlm_payload = _extract_vlm_text(img.image_bytes)

                    if not vlm_useful:
                        ocr_text, ocr_conf = _run_ocr_if_needed(msg.submissionId, img.image_bytes)
                        if _is_useful_ocr(ocr_text, ocr_conf):
                            ev_counter += 1
                            img_key = _upload_image(minio_client, msg.submissionId, ev_counter, img.image_bytes)
                            evidence_blocks.append(EvidenceBlock(
                                evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                                kind="ocr",
                                page=page.page_num,
                                content=ocr_text,
                                confidence=ocr_conf,
                                image_key=img_key,
                                bbox=img.bbox,
                            ))
                            continue

                ev_counter += 1
                img_key = _upload_image(minio_client, msg.submissionId, ev_counter, img.image_bytes)
                if vlm_useful:
                    evidence_blocks.append(EvidenceBlock(
                        evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                        kind="vlm",
                        page=page.page_num,
                        content=vlm_text,
                        confidence=max(ocr_conf, vlm_conf),
                        image_key=img_key,
                        bbox=img.bbox,
                        metadata={"image_kind": str(kind), "vlm_payload": vlm_payload},
                    ))
                else:
                    evidence_blocks.append(EvidenceBlock(
                        evidence_id=f"ev-{msg.submissionId}-{ev_counter:04d}",
                        kind="vlm_failed",
                        page=page.page_num,
                        content="Image evidence exists, but the multimodal model did not extract usable content.",
                        confidence=max(ocr_conf, vlm_conf),
                        image_key=img_key,
                        bbox=img.bbox,
                        metadata={"image_kind": str(kind), "ocr_empty": True, "vlm_payload": vlm_payload},
                    ))

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

        rubric = session.query(GradingRubric).get(msg.rubricId)
        if not rubric:
            _fail_submission(session, sub, "Rubric not found", r, msg.submissionId)
            return

        dimensions = []
        for dim in rubric.dimensions:
            dimensions.append({
                "id": dim.id,
                "name": dim.name,
                "description": dim.description or "",
                "max_score": float(dim.max_score),
                "weight": int(dim.weight),
            })

        with trace_step(msg.submissionId, "evidence_build") as info:
            packs = build_evidence_packs(evidence_blocks, dimensions)

        score_guidance = msg.customPrompt
        if msg.scoreRangeMin is not None and msg.scoreRangeMax is not None:
            range_hint = (
                f"Overall score calibration hint: the teacher expects most submissions in this batch "
                f"to fall around {msg.scoreRangeMin:.0f}-{msg.scoreRangeMax:.0f} / 100. "
                f"Use this only as a reference and do not force every dimension to match it."
            )
            score_guidance = f"{score_guidance}\n\n{range_hint}" if score_guidance else range_hint

        def _score_one_dim(dim):
            dim_id = dim["id"]
            pack = packs.get(dim_id)
            if not pack or not pack.blocks:
                return dim_id, {
                    "score": None,
                    "max_score": dim["max_score"],
                    "weight": dim["weight"],
                    "status": "NEED_MORE_EVIDENCE",
                    "comment": "No usable evidence was extracted for this dimension.",
                    "evidence_ids": [],
                }

            with trace_step(msg.submissionId, f"score_dim_{dim_id}") as info:
                sr, trace_info = score_dimension(pack, dim, custom_prompt=score_guidance)
                info["model_used"] = trace_info.get("model_used")
                info["input_tokens"] = trace_info.get("input_tokens")
                info["output_tokens"] = trace_info.get("output_tokens")
            return dim_id, {
                "score": sr.score,
                "max_score": sr.max_score,
                "weight": dim["weight"],
                "status": sr.status,
                "comment": sr.comment,
                "evidence_ids": sr.evidence_ids,
            }

        score_by_dim = {}
        try:
            with trace_step(msg.submissionId, "score_batch") as info:
                batch_results, trace_info = score_dimensions_batch(
                    packs,
                    dimensions,
                    custom_prompt=msg.customPrompt,
                    score_range_min=msg.scoreRangeMin,
                    score_range_max=msg.scoreRangeMax,
                )
                info["model_used"] = trace_info.get("model_used")
                info["input_tokens"] = trace_info.get("input_tokens")
                info["output_tokens"] = trace_info.get("output_tokens")
                info["mode"] = trace_info.get("mode")

            for dim in dimensions:
                sr = batch_results[int(dim["id"])]
                score_by_dim[int(dim["id"])] = {
                    "score": sr.score,
                    "max_score": sr.max_score,
                    "weight": dim["weight"],
                    "status": sr.status,
                    "comment": sr.comment,
                    "evidence_ids": sr.evidence_ids,
                }
        except Exception:
            score_workers = max(1, min(DIMENSION_SCORE_CONCURRENCY, len(dimensions)))
            if score_workers == 1:
                for dim in dimensions:
                    dim_id, sr_data = _score_one_dim(dim)
                    score_by_dim[dim_id] = sr_data
            else:
                with ThreadPoolExecutor(max_workers=score_workers) as pool:
                    futures = [pool.submit(_score_one_dim, dim) for dim in dimensions]
                    for future in as_completed(futures):
                        dim_id, sr_data = future.result()
                        score_by_dim[dim_id] = sr_data

        score_dicts = []
        for dim in dimensions:
            dim_id = dim["id"]
            sr_data = score_by_dim.get(dim_id, {
                "score": None,
                "max_score": dim["max_score"],
                "weight": dim["weight"],
                "status": "NEED_MORE_EVIDENCE",
                "comment": "Scoring did not return a result for this dimension.",
                "evidence_ids": [],
            })

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

        total = calculate_weighted_total(score_dicts)
        need_more_count = sum(1 for s in score_dicts if s["status"] == "NEED_MORE_EVIDENCE")

        sub.total_score = Decimal(str(total))
        sub.status = "NEED_MORE_EVIDENCE" if need_more_count == len(score_dicts) else "SCORED"
        session.commit()

        with trace_step(msg.submissionId, "report_generate") as info:
            try:
                from pipeline.report_builder import generate_pdf

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
                    {
                        "evidence_id": eb.evidence_id,
                        "kind": eb.kind,
                        "page": eb.page,
                        "content": eb.content,
                    }
                    for eb in evidence_blocks
                ]

                pdf_bytes = generate_pdf(
                    sub.student_name or "unknown",
                    report_scores,
                    report_evidence,
                    float(total),
                )

                report_key = f"grading/{msg.submissionId}/report.pdf"
                import io as _io

                minio_client.put_object(
                    MINIO_BUCKET,
                    report_key,
                    _io.BytesIO(pdf_bytes),
                    len(pdf_bytes),
                    content_type="application/pdf",
                )

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

        _notify_result(r, msg.submissionId, sub.status, total)

    except Exception as exc:
        session.rollback()
        try:
            sub = session.query(GradingSubmission).get(msg.submissionId)
            if sub:
                _fail_submission(session, sub, str(exc)[:500], r, msg.submissionId)
        except Exception:
            pass
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


@app.task(bind=True, max_retries=2, default_retry_delay=60)
def process_rag_document(self, task_message_json: str):
    """RAG document processing task."""
    try:
        msg = json.loads(task_message_json)
        course_space_doc_id = msg["courseSpaceDocId"]
        from pipeline.rag.rag_processor import process_document

        process_document(course_space_doc_id)
    except Exception as exc:
        raise self.retry(exc=exc)
