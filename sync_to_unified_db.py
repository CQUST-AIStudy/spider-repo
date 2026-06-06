import csv
import hashlib
import json
import os
import traceback
import zipfile
from datetime import datetime, timedelta
from pathlib import Path

import sync_to_db as legacy_sync

PTA_EXPORT_DIR = "导出"
PTA_SOURCE_SYSTEM = "PTA"
LEGACY_SOURCE_SYSTEM = "LEGACY_TAP"


def _flag(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _safe_float(value, default=0.0):
    try:
        if value is None:
            return default
        text = str(value).strip()
        if not text or text in {"-", "None"}:
            return default
        return float(text)
    except (ValueError, TypeError):
        return default


def _is_valid_student_identity(student_no, student_name=""):
    no = str(student_no or "").strip()
    name = str(student_name or "").strip()
    invalid_tokens = {"", "0", "none", "null", "n/a", "na", "blank"}
    if no.lower() in invalid_tokens:
        return False
    if name and name.lower() in invalid_tokens:
        return False
    return True


def _parse_pta_datetime(raw_text):
    if raw_text is None:
        return None
    text = str(raw_text).strip()
    if not text:
        return None
    normalized = text.replace("/", "-").replace("T", " ")
    for fmt in (
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
        "%m-%d %H:%M:%S",
        "%m-%d %H:%M",
    ):
        try:
            parsed = datetime.strptime(normalized, fmt)
            if parsed.year == 1900:
                parsed = parsed.replace(year=datetime.now().year)
            return parsed
        except ValueError:
            continue
    return None


def _fallback_submitted_at(row: dict) -> datetime:
    row_no = int(row.get("row_no") or 0)
    # Use a stable in-range timestamp when PTA export time is missing or malformed.
    return datetime(2000, 1, 1) + timedelta(seconds=row_no)


def _accepted_status(status_text) -> bool:
    if not status_text:
        return False
    normalized = str(status_text).strip().upper()
    return normalized in {"AC", "ACCEPTED", "答案正确", "CORRECT", "PASS", "PASSED"}


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _source_key(*parts: str) -> str:
    return hashlib.sha1("::".join(parts).encode("utf-8")).hexdigest()[:40]


def _attempt_source_key(offering_id: int, row: dict, student_no: str = "") -> str:
    submitted_marker = (row.get("submitted_at_text") or "").strip()
    if submitted_marker:
        parsed = _parse_pta_datetime(submitted_marker)
        if parsed is not None:
            submitted_marker = parsed.strftime("%Y-%m-%d %H:%M:%S")
        else:
            submitted_marker = f"row:{row.get('row_no') or 0}"
    else:
        submitted_marker = f"row:{row.get('row_no') or 0}"
    return _source_key(
        str(offering_id),
        row.get("pta_user_id") or student_no or "",
        row.get("pta_problem_id") or "",
        submitted_marker,
        row.get("compiler") or "",
    )


def _get_crawl_dir(crawl_dir=None) -> Path:
    if crawl_dir:
        legacy_sync.CRAWL_DIR = Path(crawl_dir).resolve()
    return Path(legacy_sync.CRAWL_DIR).resolve()


def _iter_experiment_dirs(crawl_dir: Path):
    if not crawl_dir.exists():
        return []
    return sorted([d for d in crawl_dir.iterdir() if d.is_dir()], key=lambda p: p.name)


def _normalize_text(value) -> str:
    return "".join(str(value or "").split())


def _find_best_matching_class(cursor, experiment_name: str):
    target = _normalize_text(experiment_name)
    if not target:
        return None
    cursor.execute(
        """
        SELECT id, teacher_id, name, pta_keyword
        FROM teaching_class
        """
    )
    best = None
    best_score = None
    for class_id, teacher_id, class_name, pta_keyword in cursor.fetchall():
        candidates = []
        for raw in (pta_keyword, class_name):
            text = _normalize_text(raw)
            if text:
                candidates.append(text)
        if not candidates:
            continue
        score = None
        for candidate in candidates:
            if target.startswith(candidate):
                score = max(score or 0, 10000 + len(candidate))
            elif candidate in target:
                score = max(score or 0, 1000 + len(candidate))
        if score is None:
            continue
        if best is None or score > best_score:
            best = {
                "class_id": class_id,
                "teacher_user_id": teacher_id,
                "class_name": class_name,
                "pta_keyword": pta_keyword,
            }
            best_score = score
    return best


def _resolve_legacy_teacher_id(cursor, teacher_user_id):
    if teacher_user_id is None:
        return None
    cursor.execute(
        """
        SELECT lt.teacher_id
        FROM teacher lt
        JOIN tap_user tu
          ON tu.username COLLATE utf8mb4_unicode_ci = lt.username COLLATE utf8mb4_unicode_ci
        WHERE tu.id = %s
        LIMIT 1
        """,
        (teacher_user_id,),
    )
    row = cursor.fetchone()
    return str(row[0]) if row and row[0] is not None else None


def _ensure_assignment_template(cursor, legacy_experiment_id: int, experiment_name: str):
    source_template_key = f"LEGACY_EXPERIMENT_TEMPLATE:{legacy_experiment_id}"
    cursor.execute(
        """
        INSERT INTO assignment_template
          (title, source_system, source_template_key, status)
        VALUES
          (%s, %s, %s, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
          title = VALUES(title),
          status = 'ACTIVE'
        """,
        (experiment_name, LEGACY_SOURCE_SYSTEM, source_template_key),
    )
    cursor.execute(
        """
        SELECT id
        FROM assignment_template
        WHERE source_system = %s
          AND source_template_key = %s
        LIMIT 1
        """,
        (LEGACY_SOURCE_SYSTEM, source_template_key),
    )
    row = cursor.fetchone()
    return row[0] if row else None


def _ensure_assignment_offering(cursor, legacy_experiment_id: int, experiment_name: str):
    class_match = _find_best_matching_class(cursor, experiment_name)
    if not class_match:
        return None
    template_id = _ensure_assignment_template(cursor, legacy_experiment_id, experiment_name)
    if template_id is None:
        return None
    cursor.execute("SELECT num, deadline FROM experiment WHERE experiment_id = %s", (legacy_experiment_id,))
    experiment_row = cursor.fetchone()
    seq_no = experiment_row[0] if experiment_row else None
    deadline_at = experiment_row[1] if experiment_row and len(experiment_row) > 1 else None
    source_offering_key = f"LEGACY_EXPERIMENT_OFFERING:{legacy_experiment_id}"
    cursor.execute(
        """
        INSERT INTO assignment_offering
          (template_id, class_id, teacher_id, seq_no, title_override, deadline_at, published_at, status, source_system, source_offering_key)
        VALUES
          (%s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP(3), 'PUBLISHED', %s, %s)
        ON DUPLICATE KEY UPDATE
          template_id = VALUES(template_id),
          class_id = VALUES(class_id),
          teacher_id = VALUES(teacher_id),
          seq_no = COALESCE(VALUES(seq_no), assignment_offering.seq_no),
          title_override = VALUES(title_override),
          deadline_at = COALESCE(VALUES(deadline_at), assignment_offering.deadline_at),
          status = 'PUBLISHED'
        """,
        (
            template_id,
            class_match["class_id"],
            class_match["teacher_user_id"],
            seq_no,
            experiment_name,
            deadline_at,
            LEGACY_SOURCE_SYSTEM,
            source_offering_key,
        ),
    )
    cursor.execute(
        """
        SELECT id, class_id, teacher_id
        FROM assignment_offering
        WHERE source_system = %s
          AND source_offering_key = %s
        LIMIT 1
        """,
        (LEGACY_SOURCE_SYSTEM, source_offering_key),
    )
    row = cursor.fetchone()
    if not row:
        return None
    return {"offering_id": row[0], "class_id": row[1], "teacher_id": row[2]}


def _sync_assignment_offering_deadline(cursor, offering_id: int, legacy_experiment_id: int):
    if not offering_id or not legacy_experiment_id:
        return
    cursor.execute("SELECT deadline FROM experiment WHERE experiment_id = %s", (legacy_experiment_id,))
    row = cursor.fetchone()
    deadline_at = row[0] if row else None
    if deadline_at is None:
        return
    cursor.execute(
        """
        UPDATE assignment_offering
        SET deadline_at = %s,
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE id = %s
        """,
        (deadline_at, offering_id),
    )


def _read_transcript_rows(xlsx_path: Path):
    rows = legacy_sync._read_xlsx_rows(xlsx_path)
    if len(rows) < 4:
        return []

    header_idx = 2
    student_no_col, student_name_col, total_score_col, ranking_col = 1, 2, 4, 5
    for i, row in enumerate(rows[:5]):
        for j, value in enumerate(row):
            text = str(value) if value is not None else ""
            if "学号" in text:
                header_idx = i
                student_no_col = j
            if "姓名" in text:
                student_name_col = j
            if "总分" in text:
                total_score_col = j
            if "排名" in text:
                ranking_col = j

    result = []
    for row in rows[header_idx + 1:]:
        if not row or student_no_col >= len(row) or not row[student_no_col]:
            continue
        student_no = str(row[student_no_col]).strip()
        if not student_no or student_no == "None":
            continue
        student_name = str(row[student_name_col]).strip() if student_name_col < len(row) and row[student_name_col] else ""
        if not _is_valid_student_identity(student_no, student_name):
            continue
        total_score = _safe_float(row[total_score_col] if total_score_col < len(row) else None, None)
        ranking = int(_safe_float(row[ranking_col] if ranking_col < len(row) else None, 0))
        result.append(
            {
                "student_no": student_no,
                "student_name": student_name,
                "total_score": total_score,
                "ranking": ranking,
            }
        )
    return result


def _read_submission_rows(csv_path: Path):
    result = []
    with csv_path.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row_no, row in enumerate(reader, start=1):
            result.append(
                {
                    "row_no": row_no,
                    "pta_user_id": (row.get("用户ID") or "").strip(),
                    "pta_problem_id": (row.get("题目ID") or "").strip(),
                    "judge_status": (row.get("状态") or "").strip(),
                    "score_text": (row.get("分数") or "").strip(),
                    "compiler": (row.get("编译器") or "").strip(),
                    "runtime_text": (row.get("用时") or "").strip(),
                    "memory_text": (row.get("内存") or "").strip(),
                    "submitted_at_text": (row.get("提交时间") or "").strip(),
                    "raw_json": json.dumps(row, ensure_ascii=False),
                }
            )
    return result


def _read_answer_sheet_rows(zip_path: Path):
    result = []
    with zipfile.ZipFile(zip_path, "r") as zf:
        for info in zf.infolist():
            if info.is_dir() or not info.filename.lower().endswith(".html"):
                continue
            decoded_name = legacy_sync._decode_zip_filename(info.filename)
            basename = decoded_name.split("/")[-1] if "/" in decoded_name else decoded_name
            stem = basename[:-5] if basename.lower().endswith(".html") else basename
            parts = stem.split("-", 1)
            if len(parts) < 2:
                continue
            student_no = parts[0].strip()
            student_name = parts[1].strip()
            if not _is_valid_student_identity(student_no, student_name):
                continue
            problem_key = None
            if "/" in decoded_name:
                path_parts = [p for p in decoded_name.split("/") if p]
                if len(path_parts) >= 2 and path_parts[-2].lower() != "html":
                    problem_key = path_parts[-2]
            raw = legacy_sync._safe_zip_read(zf, info)
            html_text = raw.decode("utf-8", errors="replace")
            pre_blocks, md_table = legacy_sync._parse_answer_sheet_html(html_text)
            merged_code = []
            question_no = 1
            for idx, code_text in enumerate(pre_blocks):
                if idx % 2 == 0:
                    merged_code.append(f"Question {question_no}\n{code_text}")
                    question_no += 1
            result.append(
                {
                    "relative_name": decoded_name,
                    "student_no": student_no,
                    "student_name": student_name,
                    "problem_key": problem_key,
                    "html_text": html_text,
                    "code_text": "\n\n".join(merged_code).strip() or None,
                    "test_report_text": md_table.strip() or None,
                }
            )
    return result


def _read_scored_code_rows(zip_path: Path):
    result = []
    with zipfile.ZipFile(zip_path, "r") as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            decoded_name = legacy_sync._decode_zip_filename(info.filename)
            path_parts = [p for p in decoded_name.split("/") if p]
            if len(path_parts) < 2:
                continue
            problem_part = path_parts[-2]
            basename = path_parts[-1]
            if "_" not in problem_part:
                continue
            pta_problem_id, _, problem_title = problem_part.partition("_")
            file_parts = basename.split("_")
            if len(file_parts) < 3:
                continue
            student_no = file_parts[0].strip()
            pta_user_id = file_parts[1].strip()
            if not _is_valid_student_identity(student_no):
                continue
            raw = legacy_sync._safe_zip_read(zf, info)
            code_text = raw.decode("utf-8", errors="replace")
            result.append(
                {
                    "relative_name": decoded_name,
                    "pta_problem_id": pta_problem_id.strip(),
                    "problem_title": problem_title.strip() or f"PTA Problem {pta_problem_id.strip()}",
                    "student_no": student_no,
                    "pta_user_id": pta_user_id,
                    "code_text": code_text,
                }
            )
    return result


def _ensure_student_profile(cursor, student_no: str, student_name: str):
    if not _is_valid_student_identity(student_no, student_name):
        raise ValueError(f"invalid student identity: student_no={student_no!r}, student_name={student_name!r}")
    cursor.execute(
        """
        INSERT INTO student_profile (student_no, real_name, status)
        VALUES (%s, %s, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
          real_name = COALESCE(NULLIF(VALUES(real_name), ''), student_profile.real_name),
          status = CASE
            WHEN student_profile.status = 'DELETED' THEN student_profile.status
            ELSE 'ACTIVE'
          END
        """,
        (student_no, student_name or student_no),
    )
    cursor.execute("SELECT id FROM student_profile WHERE student_no = %s", (student_no,))
    return cursor.fetchone()[0]


def _ensure_class_member(cursor, class_id: int, student_id: int):
    cursor.execute(
        """
        INSERT INTO class_member (class_id, student_id, member_status)
        VALUES (%s, %s, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
          member_status = 'ACTIVE',
          left_at = NULL,
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (class_id, student_id),
    )


def _ensure_binding(cursor, entity_id: int, external_id: str):
    if not external_id:
        return
    cursor.execute(
        """
        SELECT id
        FROM external_identity_binding
        WHERE entity_type = 'STUDENT_PROFILE'
          AND source_system = %s
          AND external_id = %s
          AND is_active = TRUE
        ORDER BY id DESC
        LIMIT 1
        """,
        (PTA_SOURCE_SYSTEM, external_id),
    )
    row = cursor.fetchone()
    if row:
        cursor.execute(
            """
            UPDATE external_identity_binding
            SET entity_id = %s,
                binding_type = 'PTA_USER_ID',
                confidence = 1.0000,
                valid_to = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = %s
            """,
            (entity_id, row[0]),
        )
        return
    cursor.execute(
        """
        INSERT INTO external_identity_binding
          (entity_type, entity_id, source_system, external_id, binding_type, confidence, is_active, metadata_json)
        VALUES
          ('STUDENT_PROFILE', %s, %s, %s, 'PTA_USER_ID', 1.0000, TRUE, NULL)
        """,
        (entity_id, PTA_SOURCE_SYSTEM, external_id),
    )


def _ensure_import_job(cursor, class_id, summary=None):
    cursor.execute(
        """
        INSERT INTO import_job (source_system, job_type, class_id, trigger_type, status, summary_json)
        VALUES (%s, %s, %s, 'MANUAL', 'RUNNING', %s)
        """,
        (PTA_SOURCE_SYSTEM, "UNIFIED_SYNC", class_id, json.dumps(summary or {}, ensure_ascii=False)),
    )
    return cursor.lastrowid


def _update_import_job(cursor, job_id: int, status: str, summary=None, error_message=None):
    cursor.execute(
        """
        UPDATE import_job
        SET status = %s,
            finished_at = CURRENT_TIMESTAMP(3),
            summary_json = %s,
            error_message = %s
        WHERE id = %s
        """,
        (status, json.dumps(summary or {}, ensure_ascii=False), error_message, job_id),
    )


def _register_source_file(cursor, import_job_id: int, path: Path, crawl_dir: Path, file_role: str):
    relative_path = path.relative_to(crawl_dir).as_posix()
    cursor.execute(
        """
        INSERT INTO import_source_file
          (import_job_id, file_role, relative_path, sha256, size_bytes, parse_status, parsed_at)
        VALUES
          (%s, %s, %s, %s, %s, 'PARSED', CURRENT_TIMESTAMP(3))
        """,
        (import_job_id, file_role, relative_path, _sha256_file(path), path.stat().st_size),
    )
    return cursor.lastrowid


def _ensure_artifact(cursor, owner_id: int, artifact_type: str, text_content, source_key: str, mime_type: str):
    scoped_source_key = _source_key("PTA_IMPORT_JOB", str(owner_id), source_key)
    cursor.execute(
        """
        INSERT INTO artifact
          (owner_type, owner_id, artifact_type, storage_type, text_content, mime_type, source_system, source_key)
        VALUES
          ('PTA_IMPORT_JOB', %s, %s, 'INLINE', %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          text_content = VALUES(text_content),
          mime_type = VALUES(mime_type)
        """,
        (owner_id, artifact_type, text_content, mime_type, PTA_SOURCE_SYSTEM, scoped_source_key),
    )
    cursor.execute(
        "SELECT id FROM artifact WHERE source_system = %s AND source_key = %s",
        (PTA_SOURCE_SYSTEM, scoped_source_key),
    )
    return cursor.fetchone()[0]


def _ensure_assignment_problem(cursor, offering_id: int, pta_problem_id: str, cache: dict, title=None, sort_order=0):
    if not pta_problem_id:
        return None
    if pta_problem_id in cache:
        return cache[pta_problem_id]
    cursor.execute(
        """
        INSERT INTO assignment_problem
          (offering_id, problem_no, source_problem_id, title, sort_order, status)
        VALUES
          (%s, %s, %s, %s, %s, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
          title = VALUES(title),
          sort_order = VALUES(sort_order),
          status = 'ACTIVE'
        """,
        (offering_id, pta_problem_id, pta_problem_id, title or f"PTA Problem {pta_problem_id}", sort_order),
    )
    cursor.execute(
        "SELECT id FROM assignment_problem WHERE offering_id = %s AND source_problem_id = %s",
        (offering_id, pta_problem_id),
    )
    problem_id = cursor.fetchone()[0]
    cache[pta_problem_id] = problem_id
    return problem_id


def _materialize_student_assignments(cursor, offering_id: int, class_id: int):
    cursor.execute(
        """
        INSERT INTO student_assignment (
          offering_id, student_id, submission_status,
          accepted_problem_count, submitted_problem_count, problem_count,
          created_at, updated_at
        )
        SELECT
          %s,
          cm.student_id,
          'NOT_STARTED',
          0,
          0,
          (SELECT COUNT(*) FROM assignment_problem ap WHERE ap.offering_id = %s AND ap.status = 'ACTIVE'),
          CURRENT_TIMESTAMP(3),
          CURRENT_TIMESTAMP(3)
        FROM class_member cm
        WHERE cm.class_id = %s
          AND cm.member_status = 'ACTIVE'
        ON DUPLICATE KEY UPDATE
          problem_count = VALUES(problem_count),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (offering_id, offering_id, class_id),
    )


def _table_has_column(cursor, table_name: str, column_name: str):
    cursor.execute(
        """
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = %s
          AND column_name = %s
        """,
        (table_name, column_name),
    )
    return bool(cursor.fetchone()[0])


def _prune_stale_attempts_for_source_file(cursor, offering_id: int, current_source_file_id: int):
    stale_attempt_filter = """
        FROM student_problem_attempt spa
        JOIN pta_raw_submission_row prs
          ON prs.id = spa.raw_row_id
        JOIN import_source_file isf
          ON isf.id = prs.source_file_id
        WHERE spa.offering_id = %s
          AND spa.source_system = %s
          AND isf.relative_path = (
            SELECT current_isf.relative_path
            FROM import_source_file current_isf
            WHERE current_isf.id = %s
          )
          AND prs.source_file_id <> %s
    """
    params = (offering_id, PTA_SOURCE_SYSTEM, current_source_file_id, current_source_file_id)

    cursor.execute(
        f"""
        UPDATE student_problem_state sps
        JOIN (
          SELECT spa.id, spa.offering_id, spa.problem_id, spa.student_id
          {stale_attempt_filter}
        ) stale
          ON stale.offering_id = sps.offering_id
         AND stale.problem_id = sps.problem_id
         AND stale.student_id = sps.student_id
        SET sps.latest_attempt_id = CASE
              WHEN sps.latest_attempt_id = stale.id THEN NULL
              ELSE sps.latest_attempt_id
            END,
            sps.best_attempt_id = CASE
              WHEN sps.best_attempt_id = stale.id THEN NULL
              ELSE sps.best_attempt_id
            END,
            sps.updated_at = CURRENT_TIMESTAMP(3)
        WHERE sps.offering_id = %s
          AND (sps.latest_attempt_id = stale.id OR sps.best_attempt_id = stale.id)
        """,
        params + (offering_id,),
    )

    cursor.execute(
        f"""
        DELETE spa
        {stale_attempt_filter}
        """,
        params,
    )
    return cursor.rowcount


def _prune_orphan_problem_states(cursor, offering_id: int):
    cursor.execute(
        """
        DELETE sps
        FROM student_problem_state sps
        LEFT JOIN student_problem_attempt spa
          ON spa.offering_id = sps.offering_id
         AND spa.problem_id = sps.problem_id
         AND spa.student_id = sps.student_id
        WHERE sps.offering_id = %s
          AND spa.id IS NULL
        """,
        (offering_id,),
    )
    return cursor.rowcount


def _recalc_problem_state(cursor, offering_id: int):
    cursor.execute(
        """
        SELECT id, problem_id, student_id, submitted_at, judge_status, score
        FROM student_problem_attempt
        WHERE offering_id = %s
        ORDER BY student_id, problem_id, submitted_at, id
        """,
        (offering_id,),
    )
    grouped = {}
    for attempt_id, problem_id, student_id, submitted_at, judge_status, score in cursor.fetchall():
        grouped.setdefault((problem_id, student_id), []).append(
            {
                "id": attempt_id,
                "submitted_at": submitted_at,
                "judge_status": judge_status,
                "score": score,
            }
        )

    for (problem_id, student_id), rows in grouped.items():
        latest = max(rows, key=lambda item: (item["submitted_at"], item["id"]))
        best = max(rows, key=lambda item: ((item["score"] or 0), item["submitted_at"], item["id"]))
        accepted_times = [item["submitted_at"] for item in rows if _accepted_status(item["judge_status"])]
        accepted_at = min(accepted_times) if accepted_times else None
        cursor.execute(
            """
            INSERT INTO student_problem_state (
              offering_id, problem_id, student_id,
              latest_attempt_id, best_attempt_id, latest_status,
              best_score, attempt_count, accepted_at
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
              latest_attempt_id = VALUES(latest_attempt_id),
              best_attempt_id = VALUES(best_attempt_id),
              latest_status = VALUES(latest_status),
              best_score = VALUES(best_score),
              attempt_count = VALUES(attempt_count),
              accepted_at = VALUES(accepted_at),
              updated_at = CURRENT_TIMESTAMP(3)
            """,
            (
                offering_id,
                problem_id,
                student_id,
                latest["id"],
                best["id"],
                latest["judge_status"],
                best["score"],
                len(rows),
                accepted_at,
            ),
        )


def _recalc_student_assignment(
    cursor,
    offering_id: int,
    transcript_rows,
    student_no_to_id,
    answer_sheet_rows=None,
    scored_code_rows=None,
):
    has_evidence_columns = _table_has_column(cursor, "student_assignment", "completion_evidence")
    answer_sheet_rows = answer_sheet_rows or []
    scored_code_rows = scored_code_rows or []
    transcript_by_student_id = {}
    for row in transcript_rows:
        student_id = student_no_to_id.get(row["student_no"])
        if student_id:
            transcript_by_student_id[student_id] = row

    answer_sheet_count_by_student_id = {}
    for row in answer_sheet_rows:
        student_id = student_no_to_id.get(row["student_no"])
        if student_id:
            answer_sheet_count_by_student_id[student_id] = answer_sheet_count_by_student_id.get(student_id, 0) + 1

    scored_code_count_by_student_id = {}
    for row in scored_code_rows:
        student_id = student_no_to_id.get(row["student_no"])
        if student_id:
            scored_code_count_by_student_id[student_id] = scored_code_count_by_student_id.get(student_id, 0) + 1

    cursor.execute(
        """
        SELECT
          sa.student_id,
          COALESCE(spa.submitted_problem_count, 0) AS submitted_problem_count,
          COALESCE(spa.submission_attempt_count, 0) AS submission_attempt_count,
          spa.first_submit_at,
          spa.last_submit_at,
          COALESCE(sps.accepted_problem_count, 0) AS accepted_problem_count,
          sps.best_total_score AS best_total_score
        FROM student_assignment sa
        LEFT JOIN (
          SELECT
            offering_id,
            student_id,
            COUNT(DISTINCT problem_id) AS submitted_problem_count,
            COUNT(*) AS submission_attempt_count,
            MIN(submitted_at) AS first_submit_at,
            MAX(submitted_at) AS last_submit_at
          FROM student_problem_attempt
          WHERE offering_id = %s
          GROUP BY offering_id, student_id
        ) spa
          ON spa.offering_id = sa.offering_id
         AND spa.student_id = sa.student_id
        LEFT JOIN (
          SELECT
            offering_id,
            student_id,
            COUNT(DISTINCT CASE WHEN accepted_at IS NOT NULL THEN problem_id END) AS accepted_problem_count,
            CASE
              WHEN COUNT(CASE WHEN best_score IS NOT NULL THEN 1 END) > 0
              THEN COALESCE(SUM(COALESCE(best_score, 0)), 0)
              ELSE NULL
            END AS best_total_score
          FROM student_problem_state
          WHERE offering_id = %s
          GROUP BY offering_id, student_id
        ) sps
          ON sps.offering_id = sa.offering_id
         AND sps.student_id = sa.student_id
        WHERE sa.offering_id = %s
        """,
        (offering_id, offering_id, offering_id),
    )
    aggregates = {
        row[0]: {
            "submitted_problem_count": int(row[1] or 0),
            "submission_attempt_count": int(row[2] or 0),
            "first_submit_at": row[3],
            "last_submit_at": row[4],
            "accepted_problem_count": int(row[5] or 0),
            "best_total_score": float(row[6]) if row[6] is not None else None,
        }
        for row in cursor.fetchall()
    }

    cursor.execute("SELECT student_id, problem_count FROM student_assignment WHERE offering_id = %s", (offering_id,))
    for student_id, problem_count in cursor.fetchall():
        aggregate = aggregates.get(
            student_id,
            {
                "submitted_problem_count": 0,
                "submission_attempt_count": 0,
                "first_submit_at": None,
                "last_submit_at": None,
                "accepted_problem_count": 0,
                "best_total_score": None,
            },
        )
        transcript_row = transcript_by_student_id.get(student_id)
        transcript_score = transcript_row["total_score"] if transcript_row else None
        latest_total_score = transcript_score
        ranking = transcript_row["ranking"] if transcript_row and latest_total_score is not None else None
        transcript_row_present = bool(transcript_row)
        answer_sheet_count = answer_sheet_count_by_student_id.get(student_id, 0)
        scored_code_count = scored_code_count_by_student_id.get(student_id, 0)
        submission_attempt_count = aggregate["submission_attempt_count"]
        evidence_problem_count = max(
            aggregate["submitted_problem_count"],
            answer_sheet_count,
            scored_code_count,
        )
        if problem_count and evidence_problem_count > problem_count:
            evidence_problem_count = problem_count

        if transcript_score is not None:
            submission_status = "GRADED"
            completion_evidence = "TRANSCRIPT_SCORE"
        elif answer_sheet_count > 0:
            submission_status = "SUBMITTED"
            completion_evidence = "ANSWER_SHEET"
        elif scored_code_count > 0:
            submission_status = "SUBMITTED"
            completion_evidence = "SCORED_CODE"
        elif aggregate["submitted_problem_count"] == 0:
            submission_status = "NOT_STARTED"
            completion_evidence = "NONE"
        elif problem_count and aggregate["submitted_problem_count"] >= problem_count:
            submission_status = "IN_PROGRESS"
            completion_evidence = "SUBMISSION_ATTEMPT"
        else:
            submission_status = "IN_PROGRESS"
            completion_evidence = "SUBMISSION_ATTEMPT"

        if has_evidence_columns:
            cursor.execute(
                """
                UPDATE student_assignment
                SET submission_status = %s,
                    first_submit_at = %s,
                    last_submit_at = %s,
                    accepted_problem_count = %s,
                    submitted_problem_count = %s,
                    best_total_score = %s,
                    latest_total_score = %s,
                    ranking = %s,
                    transcript_row_present = %s,
                    answer_sheet_count = %s,
                    scored_code_count = %s,
                    submission_attempt_count = %s,
                    completion_evidence = %s,
                    latest_sync_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND student_id = %s
                """,
                (
                    submission_status,
                    aggregate["first_submit_at"],
                    aggregate["last_submit_at"],
                    aggregate["accepted_problem_count"],
                    evidence_problem_count,
                    transcript_score,
                    latest_total_score,
                    ranking,
                    1 if transcript_row_present else 0,
                    answer_sheet_count,
                    scored_code_count,
                    submission_attempt_count,
                    completion_evidence,
                    offering_id,
                    student_id,
                ),
            )
        else:
            cursor.execute(
                """
                UPDATE student_assignment
                SET submission_status = %s,
                    first_submit_at = %s,
                    last_submit_at = %s,
                    accepted_problem_count = %s,
                    submitted_problem_count = %s,
                    best_total_score = %s,
                    latest_total_score = %s,
                    ranking = %s,
                    latest_sync_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND student_id = %s
                """,
                (
                    submission_status,
                    aggregate["first_submit_at"],
                    aggregate["last_submit_at"],
                    aggregate["accepted_problem_count"],
                    evidence_problem_count,
                    transcript_score,
                    latest_total_score,
                    ranking,
                    offering_id,
                    student_id,
                ),
            )


def _resolve_experiment_and_offering(cursor, experiment_name: str):
    cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s", (experiment_name,))
    experiment_row = cursor.fetchone()
    if not experiment_row:
        return None
    legacy_experiment_id = experiment_row[0]
    cursor.execute(
        """
        SELECT id, class_id, teacher_id
        FROM assignment_offering
        WHERE source_system = %s
          AND source_offering_key = %s
        """,
        (LEGACY_SOURCE_SYSTEM, f"LEGACY_EXPERIMENT_OFFERING:{legacy_experiment_id}"),
    )
    offering_row = cursor.fetchone()
    if not offering_row:
        ensured = _ensure_assignment_offering(cursor, legacy_experiment_id, experiment_name)
        if ensured:
            offering_row = (ensured["offering_id"], ensured["class_id"], ensured["teacher_id"])
    if not offering_row:
        return None
    _sync_assignment_offering_deadline(cursor, offering_row[0], legacy_experiment_id)
    return {
        "legacy_experiment_id": legacy_experiment_id,
        "offering_id": offering_row[0],
        "class_id": offering_row[1],
        "teacher_id": offering_row[2],
    }


def _sync_one_experiment(conn, crawl_dir: Path, exp_dir: Path, pta_user_map: dict, student_name_map: dict):
    cursor = conn.cursor()
    resolved = _resolve_experiment_and_offering(cursor, exp_dir.name)
    if not resolved:
        return {"experiment": exp_dir.name, "skipped": True, "reason": "missing assignment_offering mapping"}

    import_job_id = _ensure_import_job(cursor, resolved["class_id"], {"experiment": exp_dir.name})
    conn.commit()

    report = {
        "experiment": exp_dir.name,
        "import_job_id": import_job_id,
        "offering_id": resolved["offering_id"],
        "class_id": resolved["class_id"],
        "raw_submission_rows": 0,
        "raw_transcript_rows": 0,
        "raw_answer_sheet_rows": 0,
        "attempts_upserted": 0,
        "students_resolved": 0,
        "unmapped_submission_rows": 0,
        "stale_attempts_pruned": 0,
        "stale_problem_states_pruned": 0,
    }

    try:
        cursor.execute(
            """
            UPDATE student_problem_state
            SET latest_attempt_id = NULL,
                best_attempt_id = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE offering_id = %s
              AND (latest_attempt_id IS NOT NULL OR best_attempt_id IS NOT NULL)
            """,
            (resolved["offering_id"],),
        )
        if _table_has_column(cursor, "student_assignment", "completion_evidence"):
            cursor.execute(
                """
                UPDATE student_assignment
                SET submission_status = 'NOT_STARTED',
                    first_submit_at = NULL,
                    last_submit_at = NULL,
                    accepted_problem_count = 0,
                    submitted_problem_count = 0,
                    best_total_score = NULL,
                    latest_total_score = NULL,
                    ranking = NULL,
                    transcript_row_present = FALSE,
                    answer_sheet_count = 0,
                    scored_code_count = 0,
                    submission_attempt_count = 0,
                    completion_evidence = 'NONE',
                    latest_sync_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                """,
                (resolved["offering_id"],),
            )
        else:
            cursor.execute(
                """
                UPDATE student_assignment
                SET submission_status = 'NOT_STARTED',
                    first_submit_at = NULL,
                    last_submit_at = NULL,
                    accepted_problem_count = 0,
                    submitted_problem_count = 0,
                    best_total_score = NULL,
                    latest_total_score = NULL,
                    ranking = NULL,
                    latest_sync_at = CURRENT_TIMESTAMP(3),
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                """,
                (resolved["offering_id"],),
            )

        export_dir = exp_dir / PTA_EXPORT_DIR
        files = {}
        for relative_name, role in (("题目内容.txt", "PROBLEM_CONTENT"), ("提交记录.csv", "SUBMISSIONS")):
            path = exp_dir / relative_name
            if path.exists():
                files[role] = (path, _register_source_file(cursor, import_job_id, path, crawl_dir, role))
        if export_dir.exists():
            for pattern, role in (
                ("*PAPER_TRANSCRIPT*.xlsx", "PAPER_TRANSCRIPT"),
                ("*ANSWER_SHEET*.zip", "ANSWER_SHEET"),
                ("*SCORED_CODE*.zip", "SCORED_CODE"),
            ):
                matched = sorted(export_dir.glob(pattern))
                if matched:
                    files[role] = (matched[0], _register_source_file(cursor, import_job_id, matched[0], crawl_dir, role))

        transcript_rows = _read_transcript_rows(files["PAPER_TRANSCRIPT"][0]) if "PAPER_TRANSCRIPT" in files else []
        submission_rows = _read_submission_rows(files["SUBMISSIONS"][0]) if "SUBMISSIONS" in files else []
        answer_sheet_rows = _read_answer_sheet_rows(files["ANSWER_SHEET"][0]) if "ANSWER_SHEET" in files else []
        scored_code_rows = _read_scored_code_rows(files["SCORED_CODE"][0]) if "SCORED_CODE" in files else []

        student_no_to_id = {}
        for row in transcript_rows:
            student_id = _ensure_student_profile(cursor, row["student_no"], row["student_name"])
            _ensure_class_member(cursor, resolved["class_id"], student_id)
            student_no_to_id[row["student_no"]] = student_id
        for student_no, student_name in student_name_map.items():
            if student_no in student_no_to_id:
                continue
            if not _is_valid_student_identity(student_no, student_name):
                continue
            if any(r["student_no"] == student_no for r in answer_sheet_rows):
                student_id = _ensure_student_profile(cursor, student_no, student_name)
                _ensure_class_member(cursor, resolved["class_id"], student_id)
                student_no_to_id[student_no] = student_id
        report["students_resolved"] = len(student_no_to_id)

        for pta_user_id, student_no in pta_user_map.items():
            student_id = student_no_to_id.get(student_no)
            if student_id:
                _ensure_binding(cursor, student_id, pta_user_id)

        if "PAPER_TRANSCRIPT" in files:
            source_file_id = files["PAPER_TRANSCRIPT"][1]
            for row_no, row in enumerate(transcript_rows, start=1):
                cursor.execute(
                    """
                    INSERT INTO pta_raw_transcript_row
                      (import_job_id, source_file_id, row_no, student_no, student_name, total_score_text, ranking_text, raw_json)
                    VALUES
                      (%s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      student_no = VALUES(student_no),
                      student_name = VALUES(student_name),
                      total_score_text = VALUES(total_score_text),
                      ranking_text = VALUES(ranking_text),
                      raw_json = VALUES(raw_json)
                    """,
                    (
                        import_job_id,
                        source_file_id,
                        row_no,
                        row["student_no"],
                        row["student_name"],
                        None if row["total_score"] is None else str(row["total_score"]),
                        str(row["ranking"]) if row["ranking"] else None,
                        json.dumps(row, ensure_ascii=False),
                    ),
                )
            report["raw_transcript_rows"] = len(transcript_rows)

        _materialize_student_assignments(cursor, resolved["offering_id"], resolved["class_id"])

        problem_cache = {}
        code_artifact_updates = {}
        answer_sheet_artifact_by_student = {}
        problem_order = {}
        next_problem_order = 1
        unmapped_pta_users = set()
        for row in sorted(scored_code_rows, key=lambda item: (item["pta_problem_id"], item["student_no"], item["relative_name"])):
            if row["pta_problem_id"] not in problem_order:
                problem_order[row["pta_problem_id"]] = next_problem_order
                next_problem_order += 1
            problem_id = _ensure_assignment_problem(
                cursor,
                resolved["offering_id"],
                row["pta_problem_id"],
                problem_cache,
                title=row["problem_title"],
                sort_order=problem_order[row["pta_problem_id"]],
            )
            student_id = student_no_to_id.get(row["student_no"])
            if not student_id:
                student_name = student_name_map.get(row["student_no"], row["student_no"])
                student_id = _ensure_student_profile(cursor, row["student_no"], student_name)
                _ensure_class_member(cursor, resolved["class_id"], student_id)
                student_no_to_id[row["student_no"]] = student_id
                _materialize_student_assignments(cursor, resolved["offering_id"], resolved["class_id"])
            if row["pta_user_id"]:
                _ensure_binding(cursor, student_id, row["pta_user_id"])
            code_artifact_id = _ensure_artifact(
                cursor,
                import_job_id,
                "SCORED_CODE_SOURCE",
                row["code_text"],
                _source_key(exp_dir.name, "scored-code", row["relative_name"]),
                "text/plain",
            )
            code_artifact_updates[(problem_id, student_id)] = code_artifact_id

        for row in submission_rows:
            if row["pta_problem_id"] and row["pta_problem_id"] not in problem_order:
                problem_order[row["pta_problem_id"]] = next_problem_order
                next_problem_order += 1
            problem_id = _ensure_assignment_problem(
                cursor,
                resolved["offering_id"],
                row["pta_problem_id"],
                problem_cache,
                sort_order=problem_order.get(row["pta_problem_id"], 0),
            )
            student_no = pta_user_map.get(row["pta_user_id"])
            student_name = student_name_map.get(student_no, student_no) if student_no else ""
            if not student_no and row["pta_user_id"]:
                unmapped_pta_users.add(row["pta_user_id"])
                report["unmapped_submission_rows"] += 1
            if student_no and not _is_valid_student_identity(student_no, student_name):
                student_no = None
            if student_no and student_no not in student_no_to_id:
                student_id = _ensure_student_profile(cursor, student_no, student_name)
                _ensure_class_member(cursor, resolved["class_id"], student_id)
                student_no_to_id[student_no] = student_id
                _materialize_student_assignments(cursor, resolved["offering_id"], resolved["class_id"])
            student_id = student_no_to_id.get(student_no)
            if student_id and row["pta_user_id"]:
                _ensure_binding(cursor, student_id, row["pta_user_id"])

            raw_row_id = None
            if "SUBMISSIONS" in files:
                source_file_id = files["SUBMISSIONS"][1]
                cursor.execute(
                    """
                    INSERT INTO pta_raw_submission_row
                      (import_job_id, source_file_id, row_no, pta_user_id, pta_problem_id, judge_status, score_text, compiler, runtime_text, memory_text, submitted_at_text, raw_json)
                    VALUES
                      (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      judge_status = VALUES(judge_status),
                      score_text = VALUES(score_text),
                      compiler = VALUES(compiler),
                      runtime_text = VALUES(runtime_text),
                      memory_text = VALUES(memory_text),
                      submitted_at_text = VALUES(submitted_at_text),
                      raw_json = VALUES(raw_json)
                    """,
                    (
                        import_job_id,
                        source_file_id,
                        row["row_no"],
                        row["pta_user_id"] or None,
                        row["pta_problem_id"] or None,
                        row["judge_status"] or None,
                        row["score_text"] or None,
                        row["compiler"] or None,
                        row["runtime_text"] or None,
                        row["memory_text"] or None,
                        row["submitted_at_text"] or None,
                        row["raw_json"],
                    ),
                )
                cursor.execute("SELECT id FROM pta_raw_submission_row WHERE source_file_id = %s AND row_no = %s", (source_file_id, row["row_no"]))
                raw_row_id = cursor.fetchone()[0]

            if not student_id or not problem_id:
                continue

            source_attempt_key = _attempt_source_key(resolved["offering_id"], row, student_no)
            cursor.execute(
                """
                INSERT INTO student_problem_attempt
                  (offering_id, problem_id, student_id, pta_user_id, source_system, source_attempt_key, submitted_at, judge_status, score, compiler, runtime_ms, memory_kb, raw_row_id)
                VALUES
                  (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                  judge_status = VALUES(judge_status),
                  score = VALUES(score),
                  compiler = VALUES(compiler),
                  runtime_ms = VALUES(runtime_ms),
                  memory_kb = VALUES(memory_kb),
                  raw_row_id = VALUES(raw_row_id)
                """,
                (
                    resolved["offering_id"],
                    problem_id,
                    student_id,
                    row["pta_user_id"] or None,
                    PTA_SOURCE_SYSTEM,
                    source_attempt_key,
                    _parse_pta_datetime(row["submitted_at_text"]) or _fallback_submitted_at(row),
                    row["judge_status"] or None,
                    _safe_float(row["score_text"], None),
                    row["compiler"] or None,
                    int(_safe_float(row["runtime_text"], 0) * 1000) if row["runtime_text"] else None,
                    int(_safe_float(row["memory_text"], 0) / 1024) if row["memory_text"] else None,
                    raw_row_id,
                ),
            )
            report["attempts_upserted"] += 1
        report["raw_submission_rows"] = len(submission_rows)
        if "SUBMISSIONS" in files:
            report["stale_attempts_pruned"] = _prune_stale_attempts_for_source_file(
                cursor,
                resolved["offering_id"],
                files["SUBMISSIONS"][1],
            )

        for row in answer_sheet_rows:
            student_id = student_no_to_id.get(row["student_no"])
            if not student_id:
                student_id = _ensure_student_profile(cursor, row["student_no"], row["student_name"])
                _ensure_class_member(cursor, resolved["class_id"], student_id)
                student_no_to_id[row["student_no"]] = student_id
                _materialize_student_assignments(cursor, resolved["offering_id"], resolved["class_id"])
            html_artifact_id = _ensure_artifact(
                cursor,
                import_job_id,
                "ANSWER_SHEET_HTML",
                row["html_text"],
                _source_key(exp_dir.name, "html", row["relative_name"]),
                "text/html",
            )
            code_artifact_id = None
            if row["code_text"]:
                code_artifact_id = _ensure_artifact(
                    cursor,
                    import_job_id,
                    "ANSWER_SHEET_CODE",
                    row["code_text"],
                    _source_key(exp_dir.name, "code", row["relative_name"]),
                    "text/plain",
                )
            report_artifact_id = None
            if row["test_report_text"]:
                report_artifact_id = _ensure_artifact(
                    cursor,
                    import_job_id,
                    "ANSWER_SHEET_REPORT",
                    row["test_report_text"],
                    _source_key(exp_dir.name, "report", row["relative_name"]),
                    "text/markdown",
                )
            answer_sheet_artifact_by_student[student_id] = html_artifact_id
            if "ANSWER_SHEET" in files:
                source_file_id = files["ANSWER_SHEET"][1]
                cursor.execute(
                    """
                    INSERT INTO pta_raw_answer_sheet
                      (import_job_id, source_file_id, student_no, student_name, problem_key, html_artifact_id, code_artifact_id, test_report_artifact_id, raw_json)
                    VALUES
                      (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      student_no = VALUES(student_no),
                      student_name = VALUES(student_name),
                      problem_key = VALUES(problem_key),
                      code_artifact_id = VALUES(code_artifact_id),
                      test_report_artifact_id = VALUES(test_report_artifact_id),
                      raw_json = VALUES(raw_json)
                    """,
                    (
                        import_job_id,
                        source_file_id,
                        row["student_no"],
                        row["student_name"],
                        row["problem_key"],
                        html_artifact_id,
                        code_artifact_id,
                        report_artifact_id,
                        json.dumps(
                            {
                                "relative_name": row["relative_name"],
                                "student_no": row["student_no"],
                                "student_name": row["student_name"],
                                "problem_key": row["problem_key"],
                            },
                            ensure_ascii=False,
                        ),
                    ),
                )
        report["raw_answer_sheet_rows"] = len(answer_sheet_rows)

        _materialize_student_assignments(cursor, resolved["offering_id"], resolved["class_id"])
        report["stale_problem_states_pruned"] = _prune_orphan_problem_states(cursor, resolved["offering_id"])
        _recalc_problem_state(cursor, resolved["offering_id"])
        for (problem_id, student_id), artifact_id in code_artifact_updates.items():
            cursor.execute(
                """
                UPDATE student_problem_state
                SET latest_code_artifact_id = %s,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND problem_id = %s
                  AND student_id = %s
                """,
                (artifact_id, resolved["offering_id"], problem_id, student_id),
            )
        for student_id, artifact_id in answer_sheet_artifact_by_student.items():
            cursor.execute(
                """
                UPDATE student_problem_state
                SET latest_answer_sheet_artifact_id = %s,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND student_id = %s
                """,
                (artifact_id, resolved["offering_id"], student_id),
            )
        report["students_resolved"] = len(student_no_to_id)
        if unmapped_pta_users:
            report["unmapped_pta_user_ids"] = sorted(unmapped_pta_users)[:20]
        _recalc_student_assignment(
            cursor,
            resolved["offering_id"],
            transcript_rows,
            student_no_to_id,
            answer_sheet_rows,
            scored_code_rows,
        )

        _update_import_job(cursor, import_job_id, "SUCCEEDED", report, None)
        conn.commit()
        return report
    except Exception as exc:
        conn.rollback()
        fail_cursor = conn.cursor()
        _update_import_job(fail_cursor, import_job_id, "FAILED", report, str(exc)[:1000])
        conn.commit()
        raise


def sync_all(crawl_dir=None, strict=True):
    crawl_dir = _get_crawl_dir(crawl_dir)
    report = {
        "ok": False,
        "mode": "unified",
        "crawl_dir": str(crawl_dir),
        "experiments": [],
    }
    conn = legacy_sync.get_db()
    try:
        exp_dirs = _iter_experiment_dirs(crawl_dir)
        if not exp_dirs:
            message = f"No experiment data found in crawl directory: {crawl_dir}"
            report["error"] = message
            if strict:
                raise RuntimeError(message)
            return report
        exp_map = {}
        with conn.cursor() as cursor:
            for exp_dir in exp_dirs:
                cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s", (exp_dir.name,))
                row = cursor.fetchone()
                if row:
                    exp_map[exp_dir.name] = row[0]
        pta_user_map = legacy_sync.build_pta_user_map(exp_map)
        student_name_map = legacy_sync.build_student_name_map(exp_map)

        for exp_dir in exp_dirs:
            try:
                report["experiments"].append(_sync_one_experiment(conn, crawl_dir, exp_dir, pta_user_map, student_name_map))
            except Exception as exc:
                report["experiments"].append(
                    {
                        "experiment": exp_dir.name,
                        "error": str(exc),
                        "traceback": traceback.format_exc(limit=1),
                    }
                )
                if strict:
                    raise
        report["ok"] = True
        return report
    except Exception as exc:
        report["error"] = str(exc)
        if strict:
            raise
        return report
    finally:
        conn.close()


def run_configured_sync(crawl_dir=None, strict=True):
    use_unified = _flag("ACADEMIC_UNIFIED_IMPORT_ENABLED", False)
    legacy_write_enabled = _flag("ACADEMIC_LEGACY_WRITE_ENABLED", True)
    result = {
        "ok": False,
        "legacy_enabled": legacy_write_enabled,
        "unified_enabled": use_unified,
    }
    if legacy_write_enabled:
        result["legacy"] = legacy_sync.sync_all(crawl_dir=crawl_dir, strict=strict)
    if use_unified:
        result["unified"] = sync_all(crawl_dir=crawl_dir, strict=strict)
    if use_unified:
        result["ok"] = bool(result.get("unified", {}).get("ok"))
    elif legacy_write_enabled:
        result["ok"] = bool(result.get("legacy", {}).get("ok"))
    else:
        result["ok"] = True
        result["message"] = "No importer enabled"
    return result


if __name__ == "__main__":
    crawl_dir = os.sys.argv[1] if len(os.sys.argv) > 1 else None
    print(json.dumps(run_configured_sync(crawl_dir=crawl_dir, strict=False), ensure_ascii=False, indent=2))
