import csv
import hashlib
import json
import os
import time
import traceback
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

from . import sync_to_db as legacy_sync

try:
    import bcrypt as _bcrypt

    _BCRYPT_AVAILABLE = True
except ImportError:
    _BCRYPT_AVAILABLE = False

PTA_EXPORT_DIR = "导出"
PTA_SOURCE_SYSTEM = "PTA"
LEGACY_SOURCE_SYSTEM = "LEGACY_TAP"
PTA_USER_GROUP_ROSTER_FILE = "_pta_user_group_roster.json"


def _log_sync_stage(message: str, **fields):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    suffix = ""
    if fields:
        suffix = " " + " ".join(f"{key}={value}" for key, value in fields.items() if value is not None)
    print(f"[SYNC] {timestamp} {message}{suffix}", flush=True)


def _elapsed_ms(start):
    return int((time.perf_counter() - start) * 1000)


def _content_hash(text_content) -> str:
    if text_content is None:
        text = ""
    elif isinstance(text_content, bytes):
        return hashlib.sha256(text_content).hexdigest()
    else:
        text = str(text_content)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


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


def _first_value(data, *keys):
    for key in keys:
        value = (data or {}).get(key)
        if value not in (None, ""):
            return value
    return None


def _read_problem_set_info(exp_dir: Path):
    for info_file in (
        exp_dir / "题目集信息.json",
        exp_dir / "problem_set_info.json",
    ):
        if info_file.exists():
            try:
                data = json.loads(info_file.read_text(encoding="utf-8"))
                return data if isinstance(data, dict) else {}
            except Exception as exc:
                print(f"  [WARN] failed to parse problem-set metadata ({info_file}): {exc}")
                return {}
    return {}


def _read_problem_detail_rows(json_path: Path):
    if not json_path or not json_path.exists():
        return []
    try:
        data = json.loads(json_path.read_text(encoding="utf-8"))
    except Exception as exc:
        print(f"  [WARN] failed to parse problem detail metadata ({json_path}): {exc}")
        return []
    if isinstance(data, dict):
        data = data.get("problems") or data.get("items") or []
    if not isinstance(data, list):
        return []
    return [item for item in data if isinstance(item, dict)]


def _safe_int(value, default=None):
    try:
        if value in (None, ""):
            return default
        return int(float(str(value).strip()))
    except (ValueError, TypeError):
        return default


def _knowledge_strings(points):
    if not isinstance(points, list):
        return "", ""
    paths = []
    leaves = []
    for item in points:
        if isinstance(item, dict):
            path = item.get("path") or item.get("namePath") or item.get("fullName") or item.get("name")
            leaf = item.get("name") or item.get("title") or path
        else:
            path = str(item or "")
            leaf = path
        path = str(path or "").strip()
        leaf = str(leaf or "").strip()
        if path:
            paths.append(path)
        if leaf:
            leaves.append(leaf)
    return "; ".join(dict.fromkeys(paths)), "; ".join(dict.fromkeys(leaves))


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
    try:
        iso_text = text[:-1] + "+00:00" if text.endswith("Z") else text
        parsed = datetime.fromisoformat(iso_text)
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone(timedelta(hours=8))).replace(tzinfo=None)
        return parsed
    except ValueError:
        pass
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


def _pta_problem_set_source_id(problem_set_info: dict, experiment_name: str) -> str:
    for key in ("id", "problemSetId", "problem_set_id", "problemSetID"):
        value = (problem_set_info or {}).get(key)
        if value not in (None, ""):
            return str(value).strip()[:64]
    return f"NAME-{_source_key('problem-set-name', experiment_name)}"


def _template_source_key(problem_set_source_id: str) -> str:
    return f"PTA_PROBLEM_SET_TEMPLATE:{problem_set_source_id}"[:128]


def _offering_source_key(problem_set_source_id: str, class_id=None) -> str:
    base = f"PTA_PROBLEM_SET_OFFERING:{problem_set_source_id}"
    return f"{base}:CLASS:{class_id}" if class_id is not None else base


def _offering_source_keys(problem_set_source_id: str, class_id=None):
    if class_id is None:
        return [_offering_source_key(problem_set_source_id)]
    return [
        _offering_source_key(problem_set_source_id, class_id),
        _offering_source_key(problem_set_source_id),
    ]


def _legacy_offering_source_key(legacy_experiment_id: int, class_id=None) -> str:
    base = f"LEGACY_EXPERIMENT_OFFERING:{legacy_experiment_id}"
    return f"{base}:CLASS:{class_id}" if class_id is not None else base


def _legacy_offering_source_keys(legacy_experiment_id: int, class_id=None):
    if class_id is None:
        return [_legacy_offering_source_key(legacy_experiment_id)]
    return [
        _legacy_offering_source_key(legacy_experiment_id, class_id),
        _legacy_offering_source_key(legacy_experiment_id),
    ]


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
    return sorted(
        [d for d in crawl_dir.iterdir() if d.is_dir() and not d.name.startswith("_")],
        key=lambda p: p.name,
    )


def _load_pta_user_group_roster(crawl_dir: Path):
    path = crawl_dir / PTA_USER_GROUP_ROSTER_FILE
    if not path.exists():
        return None
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        return None
    group = data.get("group") or {}
    members = data.get("members") or []
    if not group.get("pta_group_id") or not isinstance(members, list):
        return None
    return data


def _resolve_class_id_from_roster(cursor, roster_payload):
    if not roster_payload:
        return None
    group = roster_payload.get("group") or {}
    pta_group_id = str(group.get("pta_group_id") or "").strip()
    pta_group_name = str(group.get("pta_group_name") or "").strip()
    if pta_group_id:
        cursor.execute(
            """
            SELECT id
            FROM teaching_class
            WHERE pta_group_id = %s
            LIMIT 1
            """,
            (pta_group_id,),
        )
        row = cursor.fetchone()
        if row:
            return row[0]
    normalized_group_name = _normalize_text(pta_group_name)
    if normalized_group_name:
        cursor.execute(
            """
            SELECT id, name, pta_keyword, pta_group_name
            FROM teaching_class
            """
        )
        for class_id, class_name, pta_keyword, stored_group_name in cursor.fetchall():
            candidates = [
                _normalize_text(stored_group_name),
                _normalize_text(pta_keyword),
                _normalize_text(class_name),
            ]
            if normalized_group_name in candidates:
                return class_id
    return None


def resolve_class_id_for_roster(roster_payload):
    """Resolve a teaching_class.id from a PTA user-group roster payload."""
    conn = legacy_sync.get_db()
    try:
        with conn.cursor() as cursor:
            return _resolve_class_id_from_roster(cursor, roster_payload)
    finally:
        conn.close()


def class_id_exists(class_id) -> bool:
    if class_id is None:
        return False
    conn = legacy_sync.get_db()
    try:
        with conn.cursor() as cursor:
            return _get_class_by_id(cursor, class_id) is not None
    finally:
        conn.close()


def _normalize_text(value) -> str:
    return "".join(str(value or "").split())


def _get_class_by_id(cursor, class_id):
    if class_id is None:
        return None
    cursor.execute(
        """
        SELECT id, teacher_id, name, pta_keyword
        FROM teaching_class
        WHERE id = %s
        LIMIT 1
        """,
        (class_id,),
    )
    row = cursor.fetchone()
    if not row:
        return None
    return {
        "class_id": row[0],
        "teacher_user_id": row[1],
        "class_name": row[2],
        "pta_keyword": row[3],
    }


def _find_best_matching_class(cursor, experiment_name: str, class_id=None):
    explicit_class = _get_class_by_id(cursor, class_id)
    if explicit_class:
        return explicit_class
    if class_id is not None:
        return None

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


def _ensure_assignment_template(cursor, problem_set_source_id: str, experiment_name: str):
    source_template_key = _template_source_key(problem_set_source_id)
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
        (experiment_name, PTA_SOURCE_SYSTEM, source_template_key),
    )
    cursor.execute(
        """
        SELECT id
        FROM assignment_template
        WHERE source_system = %s
          AND source_template_key = %s
        LIMIT 1
        """,
        (PTA_SOURCE_SYSTEM, source_template_key),
    )
    row = cursor.fetchone()
    return row[0] if row else None


def _ensure_assignment_offering(cursor, experiment_name: str, class_id=None, problem_set_info=None):
    class_match = _find_best_matching_class(cursor, experiment_name, class_id)
    if not class_match:
        return None
    problem_set_source_id = _pta_problem_set_source_id(problem_set_info or {}, experiment_name)
    template_id = _ensure_assignment_template(cursor, problem_set_source_id, experiment_name)
    if template_id is None:
        return None
    seq_no = _safe_float(_first_value(problem_set_info or {}, "num", "seqNo", "seq_no", "order"), None)
    if seq_no is not None:
        seq_no = int(seq_no)
    deadline_at = _parse_pta_datetime(_first_value(
        problem_set_info or {}, "endAt", "deadlineAt", "deadline", "end_at", "deadline_at"
    ))
    source_offering_key = _offering_source_key(problem_set_source_id, class_match["class_id"])
    cursor.execute(
        """
        INSERT INTO assignment_offering
          (
            template_id, class_id, teacher_id, seq_no, title_override, deadline_at,
            published_at, status, source_system, source_offering_key, pta_problem_set_id
          )
        VALUES
          (%s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP(3), 'PUBLISHED', %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          template_id = VALUES(template_id),
          class_id = VALUES(class_id),
          teacher_id = VALUES(teacher_id),
          seq_no = COALESCE(VALUES(seq_no), assignment_offering.seq_no),
          title_override = VALUES(title_override),
          deadline_at = COALESCE(VALUES(deadline_at), assignment_offering.deadline_at),
          pta_problem_set_id = COALESCE(VALUES(pta_problem_set_id), assignment_offering.pta_problem_set_id),
          status = 'PUBLISHED'
        """,
        (
            template_id,
            class_match["class_id"],
            class_match["teacher_user_id"],
            seq_no,
            experiment_name,
            deadline_at,
            PTA_SOURCE_SYSTEM,
            source_offering_key,
            problem_set_source_id,
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
        (PTA_SOURCE_SYSTEM, source_offering_key),
    )
    row = cursor.fetchone()
    if not row:
        return None
    return {"offering_id": row[0], "class_id": row[1], "teacher_id": row[2]}


def _sync_assignment_offering_deadline(cursor, offering_id: int, problem_set_info=None):
    if not offering_id:
        return
    deadline_at = _parse_pta_datetime(_first_value(
        problem_set_info or {}, "endAt", "deadlineAt", "deadline", "end_at", "deadline_at"
    ))
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
    cursor.execute("SELECT id, user_id FROM student_profile WHERE student_no = %s", (student_no,))
    row = cursor.fetchone()
    student_profile_id = row[0]
    user_id = row[1]

    # Auto-create tap_user account for PTA-imported students without login credentials
    if user_id is None:
        _ensure_tap_user_and_bind(cursor, student_profile_id, student_no, student_name)

    return student_profile_id


def _ensure_tap_user_and_bind(cursor, student_profile_id, student_no, student_name):
    """Create a tap_user account for PTA-imported students and bind it to student_profile."""
    if not _BCRYPT_AVAILABLE:
        print(f"  [WARN] bcrypt not available, skipping tap_user creation for {student_no}")
        return

    # Check if tap_user already exists for this student_no (username)
    cursor.execute("SELECT id FROM tap_user WHERE username = %s", (student_no,))
    row = cursor.fetchone()
    if row:
        tap_user_id = row[0]
    else:
        # Default password = student_no, hashed with BCrypt (compatible with Java BCryptPasswordEncoder)
        password_hash = _bcrypt.hashpw(
            student_no.encode("utf-8"),
            _bcrypt.gensalt(),
        ).decode("utf-8")

        cursor.execute(
            """
            INSERT INTO tap_user (username, display_name, password_hash, role, enabled, created_at, updated_at)
            VALUES (%s, %s, %s, 'STUDENT', TRUE, NOW(3), NOW(3))
            """,
            (student_no, student_name, password_hash),
        )
        tap_user_id = cursor.lastrowid
        print(f"  [INFO] Created tap_user account for {student_no} (id={tap_user_id})")

    # Bind tap_user.id to student_profile.user_id (only if still NULL)
    cursor.execute(
        "UPDATE student_profile SET user_id = %s WHERE id = %s AND user_id IS NULL",
        (tap_user_id, student_profile_id),
    )
    if cursor.rowcount > 0:
        print(f"  [INFO] Bound tap_user({tap_user_id}) to student_profile({student_profile_id}) for {student_no}")


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


def _ensure_pta_user_group_roster(cursor, roster_payload, class_id=None):
    if not roster_payload:
        return None

    group = roster_payload.get("group") or {}
    pta_group_id = str(group.get("pta_group_id") or "").strip()
    if not pta_group_id:
        return None
    pta_group_name = str(group.get("pta_group_name") or "").strip() or pta_group_id
    members = [
        item for item in (roster_payload.get("members") or [])
        if _is_valid_student_identity(item.get("student_no"), item.get("student_name"))
    ]
    cursor.execute(
        """
        INSERT INTO pta_user_group
          (class_id, pta_group_id, pta_group_name, member_count, last_roster_sync_at, raw_json)
        VALUES
          (%s, %s, %s, %s, CURRENT_TIMESTAMP(3), %s)
        ON DUPLICATE KEY UPDATE
          id = LAST_INSERT_ID(id),
          class_id = COALESCE(VALUES(class_id), pta_user_group.class_id),
          pta_group_name = VALUES(pta_group_name),
          member_count = VALUES(member_count),
          last_roster_sync_at = CURRENT_TIMESTAMP(3),
          raw_json = VALUES(raw_json),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (
            class_id,
            pta_group_id,
            pta_group_name,
            len(members),
            json.dumps(group.get("raw_json") or group, ensure_ascii=False),
        ),
    )
    pta_user_group_id = cursor.lastrowid

    student_no_to_id = {}
    student_no_to_name = {}
    pta_user_to_student_no = {}
    active_student_nos = set()

    for member in members:
        student_no = str(member.get("student_no") or "").strip()
        student_name = str(member.get("student_name") or "").strip() or student_no
        active_student_nos.add(student_no)
        student_no_to_name[student_no] = student_name
        student_id = _ensure_student_profile(cursor, student_no, student_name)
        student_no_to_id[student_no] = student_id
        if class_id is not None:
            _ensure_class_member(cursor, class_id, student_id)
        if member.get("pta_user_id"):
            pta_user_id = str(member.get("pta_user_id")).strip()
            pta_user_to_student_no[pta_user_id] = student_no
            _ensure_binding(cursor, student_id, pta_user_id)

        cursor.execute(
            """
            INSERT INTO pta_user_group_member
              (
                pta_user_group_id, class_id, student_id, student_no, student_name,
                pta_member_id, pta_user_id, pta_student_user_id,
                member_status, left_at, raw_json
              )
            VALUES
              (%s, %s, %s, %s, %s, %s, %s, %s, 'ACTIVE', NULL, %s)
            ON DUPLICATE KEY UPDATE
              class_id = VALUES(class_id),
              student_id = VALUES(student_id),
              student_name = VALUES(student_name),
              pta_member_id = VALUES(pta_member_id),
              pta_user_id = VALUES(pta_user_id),
              pta_student_user_id = VALUES(pta_student_user_id),
              member_status = 'ACTIVE',
              left_at = NULL,
              raw_json = VALUES(raw_json),
              updated_at = CURRENT_TIMESTAMP(3)
            """,
            (
                pta_user_group_id,
                class_id,
                student_id,
                student_no,
                student_name,
                member.get("pta_member_id"),
                member.get("pta_user_id"),
                member.get("pta_student_user_id"),
                json.dumps(member.get("raw_json") or member, ensure_ascii=False),
            ),
        )

    if active_student_nos:
        placeholders = ", ".join(["%s"] * len(active_student_nos))
        cursor.execute(
            f"""
            UPDATE pta_user_group_member
            SET member_status = 'LEFT',
                left_at = COALESCE(left_at, CURRENT_TIMESTAMP(3)),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE pta_user_group_id = %s
              AND member_status = 'ACTIVE'
              AND student_no NOT IN ({placeholders})
            """,
            tuple([pta_user_group_id, *sorted(active_student_nos)]),
        )
    else:
        cursor.execute(
            """
            UPDATE pta_user_group_member
            SET member_status = 'LEFT',
                left_at = COALESCE(left_at, CURRENT_TIMESTAMP(3)),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE pta_user_group_id = %s
              AND member_status = 'ACTIVE'
            """,
            (pta_user_group_id,),
        )

    return {
        "pta_user_group_id": pta_user_group_id,
        "pta_group_id": pta_group_id,
        "pta_group_name": pta_group_name,
        "student_no_to_id": student_no_to_id,
        "student_no_to_name": student_no_to_name,
        "pta_user_to_student_no": pta_user_to_student_no,
        "active_student_nos": active_student_nos,
    }


def _update_assignment_offering_pta_group(cursor, offering_id: int, pta_group_context):
    if not pta_group_context:
        return
    cursor.execute(
        """
        UPDATE assignment_offering
        SET pta_user_group_id = %s,
            pta_group_id = %s,
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE id = %s
        """,
        (
            pta_group_context["pta_user_group_id"],
            pta_group_context["pta_group_id"],
            offering_id,
        ),
    )


def _is_official_roster_student(pta_group_context, student_no: str):
    if not pta_group_context:
        return True
    return str(student_no or "").strip() in pta_group_context.get("active_student_nos", set())


def _ensure_class_member_if_official(cursor, class_id, student_id, student_no, pta_group_context):
    if class_id is None:
        return
    if _is_official_roster_student(pta_group_context, student_no):
        _ensure_class_member(cursor, class_id, student_id)


def _participant_roster_scope(pta_group_context, student_no: str):
    if not pta_group_context:
        return "CLASS_ROSTER"
    if _is_official_roster_student(pta_group_context, student_no):
        return "PTA_USER_GROUP"
    return "GUEST_PARTICIPANT"


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
    content_hash = _content_hash(text_content)
    cursor.execute(
        """
        INSERT INTO artifact
          (owner_type, owner_id, artifact_type, storage_type, text_content, content_hash, mime_type, source_system, source_key)
        VALUES
          ('PTA_IMPORT_JOB', %s, %s, 'INLINE', %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          id = LAST_INSERT_ID(id),
          text_content = IF(COALESCE(content_hash, '') <> VALUES(content_hash), VALUES(text_content), text_content),
          content_hash = VALUES(content_hash),
          mime_type = VALUES(mime_type)
        """,
        (owner_id, artifact_type, text_content, content_hash, mime_type, PTA_SOURCE_SYSTEM, scoped_source_key),
    )
    return cursor.lastrowid


def _bulk_ensure_artifacts(cursor, owner_id: int, artifact_rows):
    """
    Batch upsert artifacts and return caller source_key -> artifact.id.

    artifact_rows items: (artifact_type, text_content, source_key, mime_type)
    where source_key is the unscoped logical key used by existing _ensure_artifact callers.
    """
    normalized = []
    source_key_by_scoped = {}
    for artifact_type, text_content, source_key, mime_type in artifact_rows or []:
        if source_key is None:
            continue
        scoped_source_key = _source_key("PTA_IMPORT_JOB", str(owner_id), source_key)
        normalized.append(
            (
                owner_id,
                artifact_type,
                text_content,
                _content_hash(text_content),
                mime_type,
                PTA_SOURCE_SYSTEM,
                scoped_source_key,
            )
        )
        source_key_by_scoped[scoped_source_key] = source_key
    if not normalized:
        return {}

    result = {}
    scoped_keys = list(source_key_by_scoped.keys())
    existing_meta = {}
    for chunk in _chunks(scoped_keys):
        placeholders = ", ".join(["%s"] * len(chunk))
        cursor.execute(
            f"""
            SELECT source_key, content_hash, mime_type
            FROM artifact
            WHERE source_system = %s
              AND source_key IN ({placeholders})
            """,
            tuple([PTA_SOURCE_SYSTEM, *chunk]),
        )
        for scoped_source_key, content_hash, mime_type in cursor.fetchall():
            existing_meta[scoped_source_key] = (content_hash, mime_type)

    rows_to_write = [
        row for row in normalized
        if existing_meta.get(row[6]) != (row[3], row[4])
    ]
    if rows_to_write:
        cursor.executemany(
            """
            INSERT INTO artifact
              (owner_type, owner_id, artifact_type, storage_type, text_content, content_hash, mime_type, source_system, source_key)
            VALUES
              ('PTA_IMPORT_JOB', %s, %s, 'INLINE', %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE
              text_content = IF(COALESCE(content_hash, '') <> VALUES(content_hash), VALUES(text_content), text_content),
              content_hash = VALUES(content_hash),
              mime_type = VALUES(mime_type)
            """,
            rows_to_write,
        )

    for chunk in _chunks(scoped_keys):
        placeholders = ", ".join(["%s"] * len(chunk))
        cursor.execute(
            f"""
            SELECT source_key, id
            FROM artifact
            WHERE source_system = %s
              AND source_key IN ({placeholders})
            """,
            tuple([PTA_SOURCE_SYSTEM, *chunk]),
        )
        for scoped_source_key, artifact_id in cursor.fetchall():
            result[source_key_by_scoped.get(scoped_source_key)] = artifact_id
    return result


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
          id = LAST_INSERT_ID(id),
          title = VALUES(title),
          sort_order = VALUES(sort_order),
          status = 'ACTIVE'
        """,
        (offering_id, pta_problem_id, pta_problem_id, title or f"PTA Problem {pta_problem_id}", sort_order),
    )
    problem_id = cursor.lastrowid
    cache[pta_problem_id] = problem_id
    return problem_id


def _chunks(items, size=500):
    items = list(items or [])
    for idx in range(0, len(items), size):
        yield items[idx:idx + size]


def _select_student_profiles_by_no(cursor, student_nos):
    result = {}
    student_nos = sorted({str(x or "").strip() for x in student_nos if str(x or "").strip()})
    for chunk in _chunks(student_nos):
        placeholders = ", ".join(["%s"] * len(chunk))
        cursor.execute(
            f"SELECT id, student_no, user_id FROM student_profile WHERE student_no IN ({placeholders})",
            tuple(chunk),
        )
        for student_id, student_no, user_id in cursor.fetchall():
            result[str(student_no)] = {"id": student_id, "user_id": user_id}
    return result


def _bulk_ensure_student_profiles(cursor, student_names):
    """
    Upsert student profiles in batches and return student_no -> student_profile.id.

    tap_user creation remains lazy/per-student for profiles without user_id because it
    hashes a default password and updates another table; the expensive student_profile
    lookup/upsert path is batched.
    """
    normalized = {}
    for student_no, student_name in (student_names or {}).items():
        student_no = str(student_no or "").strip()
        student_name = str(student_name or "").strip() or student_no
        if _is_valid_student_identity(student_no, student_name):
            normalized[student_no] = student_name
    if not normalized:
        return {}

    rows = [(student_no, student_name) for student_no, student_name in sorted(normalized.items())]
    cursor.executemany(
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
        rows,
    )

    profiles = _select_student_profiles_by_no(cursor, normalized.keys())
    for student_no, profile in profiles.items():
        if profile.get("user_id") is None:
            _ensure_tap_user_and_bind(cursor, profile["id"], student_no, normalized.get(student_no, student_no))
    return {student_no: profile["id"] for student_no, profile in profiles.items()}


def _bulk_ensure_class_members(cursor, class_id, student_ids):
    if class_id is None:
        return
    rows = sorted({int(student_id) for student_id in student_ids if student_id})
    if not rows:
        return
    cursor.executemany(
        """
        INSERT INTO class_member (class_id, student_id, member_status)
        VALUES (%s, %s, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
          member_status = 'ACTIVE',
          left_at = NULL,
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        [(class_id, student_id) for student_id in rows],
    )


def _bulk_ensure_assignment_participants(cursor, offering_id: int, student_scopes):
    rows = {}
    for student_id, roster_scope in (student_scopes or {}).items():
        if student_id:
            rows[int(student_id)] = roster_scope or "GUEST_PARTICIPANT"
    if not rows:
        return
    cursor.executemany(
        """
        INSERT INTO student_assignment (
          offering_id, student_id, roster_scope, submission_status,
          accepted_problem_count, submitted_problem_count, problem_count,
          created_at, updated_at
        )
        VALUES (
          %s, %s, %s, 'NOT_STARTED',
          0, 0,
          (SELECT COUNT(*) FROM assignment_problem ap WHERE ap.offering_id = %s AND ap.status = 'ACTIVE'),
          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
        )
        ON DUPLICATE KEY UPDATE
          roster_scope = CASE
            WHEN student_assignment.roster_scope IN ('CLASS_ROSTER', 'PTA_USER_GROUP')
            THEN student_assignment.roster_scope
            ELSE VALUES(roster_scope)
          END,
          problem_count = VALUES(problem_count),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        [(offering_id, student_id, roster_scope, offering_id) for student_id, roster_scope in sorted(rows.items())],
    )


def _bulk_ensure_assignment_problems(cursor, offering_id: int, problem_specs, cache: dict):
    specs = {}
    for pta_problem_id, spec in (problem_specs or {}).items():
        pta_problem_id = str(pta_problem_id or "").strip()
        if not pta_problem_id:
            continue
        specs[pta_problem_id] = {
            "title": (spec or {}).get("title") or f"PTA Problem {pta_problem_id}",
            "sort_order": int((spec or {}).get("sort_order") or 0),
        }
    if not specs:
        return cache

    rows = [
        (
            offering_id,
            pta_problem_id,
            pta_problem_id,
            spec["title"],
            spec["sort_order"],
        )
        for pta_problem_id, spec in sorted(specs.items(), key=lambda item: item[1]["sort_order"])
    ]
    cursor.executemany(
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
        rows,
    )

    for chunk in _chunks(specs.keys()):
        placeholders = ", ".join(["%s"] * len(chunk))
        cursor.execute(
            f"""
            SELECT id, source_problem_id
            FROM assignment_problem
            WHERE offering_id = %s
              AND source_problem_id IN ({placeholders})
            """,
            tuple([offering_id, *chunk]),
        )
        for problem_id, source_problem_id in cursor.fetchall():
            cache[str(source_problem_id)] = problem_id
    return cache


def _bulk_upsert_pta_problem_details(cursor, legacy_experiment_id, experiment_name, problem_set_id, detail_rows):
    if not legacy_experiment_id or not detail_rows:
        return 0
    if not _table_exists(cursor, "pta_problem_detail"):
        return 0

    rows = []
    for item in detail_rows:
        problem_set_problem_id = str(item.get("problem_set_problem_id") or item.get("id") or "").strip()
        if not problem_set_problem_id:
            continue
        knowledge_points = item.get("knowledge_points") or item.get("knowledgePoints") or []
        knowledge_path, knowledge_leaf = _knowledge_strings(knowledge_points)
        knowledge_point_ids = item.get("knowledge_point_ids") or item.get("knowledgePointIds") or []
        content = item.get("content_md") or item.get("content_html") or item.get("content") or ""
        content_format = item.get("content_format")
        if not content_format:
            content_format = "markdown" if item.get("content_md") else ("html" if item.get("content_html") else "markdown")
        image_urls = item.get("image_urls") or item.get("imageUrls") or []
        raw_json = item.get("raw_json") or item
        rows.append(
            (
                legacy_experiment_id,
                experiment_name,
                str(item.get("problem_set_id") or problem_set_id or ""),
                problem_set_problem_id,
                str(item.get("pta_global_problem_id") or "") or None,
                str(item.get("problem_url") or "") or None,
                str(item.get("problem_label") or "") or None,
                str(item.get("title") or "") or None,
                _safe_float(item.get("score"), None),
                str(item.get("problem_type") or "") or None,
                _safe_int(item.get("difficulty_level")),
                str(item.get("difficulty_label") or "") or None,
                _safe_int(item.get("problem_pool_index")),
                _safe_int(item.get("index_in_problem_pool")),
                knowledge_path or None,
                knowledge_leaf or None,
                json.dumps(knowledge_point_ids, ensure_ascii=False),
                json.dumps(knowledge_points, ensure_ascii=False),
                content,
                str(content_format or "markdown"),
                json.dumps(image_urls, ensure_ascii=False),
                json.dumps(raw_json, ensure_ascii=False),
            )
        )
    if not rows:
        return 0

    cursor.executemany(
        """
        INSERT INTO pta_problem_detail (
          experiment_id, experiment_name, problem_set_id,
          problem_set_problem_id, pta_global_problem_id, problem_url,
          problem_label, title, score, problem_type,
          difficulty_level, difficulty_label,
          problem_pool_index, index_in_problem_pool,
          knowledge_path, knowledge_leaf,
          knowledge_point_ids, knowledge_points_json,
          content, content_format, image_urls_json, raw_json
        )
        VALUES (
          %s, %s, %s,
          %s, %s, %s,
          %s, %s, %s, %s,
          %s, %s,
          %s, %s,
          %s, %s,
          %s, %s,
          %s, %s, %s, %s
        )
        ON DUPLICATE KEY UPDATE
          experiment_name = VALUES(experiment_name),
          problem_set_id = VALUES(problem_set_id),
          pta_global_problem_id = VALUES(pta_global_problem_id),
          problem_url = VALUES(problem_url),
          problem_label = VALUES(problem_label),
          title = VALUES(title),
          score = VALUES(score),
          problem_type = VALUES(problem_type),
          difficulty_level = VALUES(difficulty_level),
          difficulty_label = VALUES(difficulty_label),
          problem_pool_index = VALUES(problem_pool_index),
          index_in_problem_pool = VALUES(index_in_problem_pool),
          knowledge_path = VALUES(knowledge_path),
          knowledge_leaf = VALUES(knowledge_leaf),
          knowledge_point_ids = VALUES(knowledge_point_ids),
          knowledge_points_json = VALUES(knowledge_points_json),
          content = VALUES(content),
          content_format = VALUES(content_format),
          image_urls_json = VALUES(image_urls_json),
          raw_json = VALUES(raw_json)
        """,
        rows,
    )
    return len(rows)


def _bulk_upsert_raw_submission_rows(cursor, import_job_id, source_file_id, submission_rows):
    if not source_file_id or not submission_rows:
        return {}
    rows = [
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
        )
        for row in submission_rows
    ]
    cursor.executemany(
        """
        INSERT INTO pta_raw_submission_row
          (import_job_id, source_file_id, row_no, pta_user_id, pta_problem_id, judge_status, score_text, compiler, runtime_text, memory_text, submitted_at_text, raw_json)
        VALUES
          (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          pta_user_id = VALUES(pta_user_id),
          pta_problem_id = VALUES(pta_problem_id),
          judge_status = VALUES(judge_status),
          score_text = VALUES(score_text),
          compiler = VALUES(compiler),
          runtime_text = VALUES(runtime_text),
          memory_text = VALUES(memory_text),
          submitted_at_text = VALUES(submitted_at_text),
          raw_json = VALUES(raw_json)
        """,
        rows,
    )

    result = {}
    row_nos = [row["row_no"] for row in submission_rows]
    for chunk in _chunks(row_nos):
        placeholders = ", ".join(["%s"] * len(chunk))
        cursor.execute(
            f"""
            SELECT row_no, id
            FROM pta_raw_submission_row
            WHERE source_file_id = %s
              AND row_no IN ({placeholders})
            """,
            tuple([source_file_id, *chunk]),
        )
        for row_no, raw_row_id in cursor.fetchall():
            result[int(row_no)] = raw_row_id
    return result


def _bulk_upsert_student_problem_attempts(cursor, attempt_rows):
    if not attempt_rows:
        return 0
    cursor.executemany(
        """
        INSERT INTO student_problem_attempt
          (offering_id, problem_id, student_id, pta_user_id, source_system, source_attempt_key, submitted_at, judge_status, score, compiler, runtime_ms, memory_kb, raw_row_id)
        VALUES
          (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          submitted_at = VALUES(submitted_at),
          judge_status = VALUES(judge_status),
          score = VALUES(score),
          compiler = VALUES(compiler),
          runtime_ms = VALUES(runtime_ms),
          memory_kb = VALUES(memory_kb),
          raw_row_id = VALUES(raw_row_id)
        """,
        attempt_rows,
    )
    return len(attempt_rows)


def _materialize_student_assignments(cursor, offering_id: int, class_id: int, pta_group_context=None):
    roster_scope_value = "PTA_USER_GROUP" if pta_group_context else "CLASS_ROSTER"
    roster_scope_update = """
              roster_scope = CASE
                WHEN student_assignment.roster_scope = 'GUEST_PARTICIPANT'
                THEN student_assignment.roster_scope
                ELSE VALUES(roster_scope)
              END,
    """
    if pta_group_context:
        cursor.execute(
            f"""
            INSERT INTO student_assignment (
              offering_id, student_id, roster_scope, submission_status,
              accepted_problem_count, submitted_problem_count, problem_count,
              created_at, updated_at
            )
            SELECT
              %s,
              ugm.student_id,
              %s,
              'NOT_STARTED',
              0,
              0,
              (SELECT COUNT(*) FROM assignment_problem ap WHERE ap.offering_id = %s AND ap.status = 'ACTIVE'),
              CURRENT_TIMESTAMP(3),
              CURRENT_TIMESTAMP(3)
            FROM pta_user_group_member ugm
            WHERE ugm.pta_user_group_id = %s
              AND ugm.member_status = 'ACTIVE'
              AND ugm.student_id IS NOT NULL
            ON DUPLICATE KEY UPDATE
              {roster_scope_update}
              problem_count = VALUES(problem_count),
              updated_at = CURRENT_TIMESTAMP(3)
            """,
            (offering_id, roster_scope_value, offering_id, pta_group_context["pta_user_group_id"]),
        )
        return

    cursor.execute(
        f"""
        INSERT INTO student_assignment (
          offering_id, student_id, roster_scope, submission_status,
          accepted_problem_count, submitted_problem_count, problem_count,
          created_at, updated_at
        )
        SELECT
          %s,
          cm.student_id,
          %s,
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
          {roster_scope_update}
          problem_count = VALUES(problem_count),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (offering_id, roster_scope_value, offering_id, class_id),
    )


def _ensure_student_assignment_participant(cursor, offering_id: int, student_id: int, roster_scope: str):
    cursor.execute(
        """
        INSERT INTO student_assignment (
          offering_id, student_id, roster_scope, submission_status,
          accepted_problem_count, submitted_problem_count, problem_count,
          created_at, updated_at
        )
        VALUES (
          %s, %s, %s, 'NOT_STARTED',
          0, 0,
          (SELECT COUNT(*) FROM assignment_problem ap WHERE ap.offering_id = %s AND ap.status = 'ACTIVE'),
          CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
        )
        ON DUPLICATE KEY UPDATE
          roster_scope = CASE
            WHEN student_assignment.roster_scope IN ('CLASS_ROSTER', 'PTA_USER_GROUP')
            THEN student_assignment.roster_scope
            ELSE VALUES(roster_scope)
          END,
          problem_count = VALUES(problem_count),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (offering_id, student_id, roster_scope, offering_id),
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


def _table_exists(cursor, table_name: str):
    cursor.execute(
        """
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = %s
        """,
        (table_name,),
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
        INSERT INTO student_problem_state (
          offering_id, problem_id, student_id,
          latest_attempt_id, best_attempt_id, latest_status,
          best_score, attempt_count, accepted_at
        )
        SELECT
          agg.offering_id,
          agg.problem_id,
          agg.student_id,
          latest.id AS latest_attempt_id,
          best.id AS best_attempt_id,
          latest.judge_status AS latest_status,
          best.score AS best_score,
          agg.attempt_count,
          agg.accepted_at
        FROM (
          SELECT
            offering_id,
            problem_id,
            student_id,
            COUNT(*) AS attempt_count,
            MIN(CASE
              WHEN UPPER(COALESCE(judge_status, '')) IN ('AC', 'ACCEPTED', 'CORRECT', 'PASS', 'PASSED')
                OR COALESCE(judge_status, '') IN ('答案正确')
              THEN submitted_at
              ELSE NULL
            END) AS accepted_at
          FROM student_problem_attempt
          WHERE offering_id = %s
          GROUP BY offering_id, problem_id, student_id
        ) agg
        JOIN (
          SELECT id, offering_id, problem_id, student_id, judge_status
          FROM (
            SELECT
              spa.*,
              ROW_NUMBER() OVER (
                PARTITION BY offering_id, problem_id, student_id
                ORDER BY submitted_at DESC, id DESC
              ) AS rn
            FROM student_problem_attempt spa
            WHERE offering_id = %s
          ) ranked_latest
          WHERE rn = 1
        ) latest
          ON latest.offering_id = agg.offering_id
         AND latest.problem_id = agg.problem_id
         AND latest.student_id = agg.student_id
        JOIN (
          SELECT id, offering_id, problem_id, student_id, score
          FROM (
            SELECT
              spa.*,
              ROW_NUMBER() OVER (
                PARTITION BY offering_id, problem_id, student_id
                ORDER BY COALESCE(score, 0) DESC, submitted_at DESC, id DESC
              ) AS rn
            FROM student_problem_attempt spa
            WHERE offering_id = %s
          ) ranked_best
          WHERE rn = 1
        ) best
          ON best.offering_id = agg.offering_id
         AND best.problem_id = agg.problem_id
         AND best.student_id = agg.student_id
        ON DUPLICATE KEY UPDATE
          latest_attempt_id = VALUES(latest_attempt_id),
          best_attempt_id = VALUES(best_attempt_id),
          latest_status = VALUES(latest_status),
          best_score = VALUES(best_score),
          attempt_count = VALUES(attempt_count),
          accepted_at = VALUES(accepted_at),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (offering_id, offering_id, offering_id),
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
    transcript_rows = transcript_rows or []

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

    cursor.execute("DROP TEMPORARY TABLE IF EXISTS tmp_pta_transcript_sync")
    cursor.execute(
        """
        CREATE TEMPORARY TABLE tmp_pta_transcript_sync (
          student_id BIGINT NOT NULL PRIMARY KEY,
          total_score DECIMAL(10,2) NULL,
          ranking INT NULL
        ) ENGINE=MEMORY
        """
    )
    transcript_values = [
        (
            student_id,
            row["total_score"],
            row["ranking"] if row and row["total_score"] is not None else None,
        )
        for student_id, row in transcript_by_student_id.items()
    ]
    if transcript_values:
        cursor.executemany(
            """
            INSERT INTO tmp_pta_transcript_sync (student_id, total_score, ranking)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE
              total_score = VALUES(total_score),
              ranking = VALUES(ranking)
            """,
            transcript_values,
        )

    cursor.execute("DROP TEMPORARY TABLE IF EXISTS tmp_pta_evidence_sync")
    cursor.execute(
        """
        CREATE TEMPORARY TABLE tmp_pta_evidence_sync (
          student_id BIGINT NOT NULL PRIMARY KEY,
          answer_sheet_count INT NOT NULL DEFAULT 0,
          scored_code_count INT NOT NULL DEFAULT 0
        ) ENGINE=MEMORY
        """
    )
    evidence_student_ids = sorted(set(answer_sheet_count_by_student_id) | set(scored_code_count_by_student_id))
    if evidence_student_ids:
        cursor.executemany(
            """
            INSERT INTO tmp_pta_evidence_sync (student_id, answer_sheet_count, scored_code_count)
            VALUES (%s, %s, %s)
            ON DUPLICATE KEY UPDATE
              answer_sheet_count = VALUES(answer_sheet_count),
              scored_code_count = VALUES(scored_code_count)
            """,
            [
                (
                    student_id,
                    answer_sheet_count_by_student_id.get(student_id, 0),
                    scored_code_count_by_student_id.get(student_id, 0),
                )
                for student_id in evidence_student_ids
            ],
        )

    evidence_problem_count_expr = """
        CASE
          WHEN sa.problem_count > 0 THEN LEAST(
            sa.problem_count,
            GREATEST(
              COALESCE(spa.submitted_problem_count, 0),
              COALESCE(ev.answer_sheet_count, 0),
              COALESCE(ev.scored_code_count, 0)
            )
          )
          ELSE GREATEST(
            COALESCE(spa.submitted_problem_count, 0),
            COALESCE(ev.answer_sheet_count, 0),
            COALESCE(ev.scored_code_count, 0)
          )
        END
    """
    joined_aggregates = """
        LEFT JOIN tmp_pta_transcript_sync tr
          ON tr.student_id = sa.student_id
        LEFT JOIN tmp_pta_evidence_sync ev
          ON ev.student_id = sa.student_id
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
            COUNT(DISTINCT CASE WHEN accepted_at IS NOT NULL THEN problem_id END) AS accepted_problem_count
          FROM student_problem_state
          WHERE offering_id = %s
          GROUP BY offering_id, student_id
        ) sps
          ON sps.offering_id = sa.offering_id
         AND sps.student_id = sa.student_id
    """
    if has_evidence_columns:
        cursor.execute(
            f"""
            UPDATE student_assignment sa
            {joined_aggregates}
            SET sa.submission_status = CASE
                  WHEN tr.total_score IS NOT NULL THEN 'GRADED'
                  WHEN COALESCE(ev.answer_sheet_count, 0) > 0 THEN 'SUBMITTED'
                  WHEN COALESCE(ev.scored_code_count, 0) > 0 THEN 'SUBMITTED'
                  WHEN COALESCE(spa.submitted_problem_count, 0) = 0 THEN 'NOT_STARTED'
                  ELSE 'IN_PROGRESS'
                END,
                sa.first_submit_at = spa.first_submit_at,
                sa.last_submit_at = spa.last_submit_at,
                sa.accepted_problem_count = COALESCE(sps.accepted_problem_count, 0),
                sa.submitted_problem_count = {evidence_problem_count_expr},
                sa.best_total_score = tr.total_score,
                sa.latest_total_score = tr.total_score,
                sa.ranking = CASE WHEN tr.total_score IS NOT NULL THEN tr.ranking ELSE NULL END,
                sa.transcript_row_present = CASE WHEN tr.student_id IS NOT NULL THEN TRUE ELSE FALSE END,
                sa.answer_sheet_count = COALESCE(ev.answer_sheet_count, 0),
                sa.scored_code_count = COALESCE(ev.scored_code_count, 0),
                sa.submission_attempt_count = COALESCE(spa.submission_attempt_count, 0),
                sa.completion_evidence = CASE
                  WHEN tr.total_score IS NOT NULL THEN 'TRANSCRIPT_SCORE'
                  WHEN COALESCE(ev.answer_sheet_count, 0) > 0 THEN 'ANSWER_SHEET'
                  WHEN COALESCE(ev.scored_code_count, 0) > 0 THEN 'SCORED_CODE'
                  WHEN COALESCE(spa.submitted_problem_count, 0) = 0 THEN 'NONE'
                  ELSE 'SUBMISSION_ATTEMPT'
                END,
                sa.latest_sync_at = CURRENT_TIMESTAMP(3),
                sa.updated_at = CURRENT_TIMESTAMP(3)
            WHERE sa.offering_id = %s
            """,
            (offering_id, offering_id, offering_id),
        )
    else:
        cursor.execute(
            f"""
            UPDATE student_assignment sa
            {joined_aggregates}
            SET sa.submission_status = CASE
                  WHEN tr.total_score IS NOT NULL THEN 'GRADED'
                  WHEN COALESCE(ev.answer_sheet_count, 0) > 0 THEN 'SUBMITTED'
                  WHEN COALESCE(ev.scored_code_count, 0) > 0 THEN 'SUBMITTED'
                  WHEN COALESCE(spa.submitted_problem_count, 0) = 0 THEN 'NOT_STARTED'
                  ELSE 'IN_PROGRESS'
                END,
                sa.first_submit_at = spa.first_submit_at,
                sa.last_submit_at = spa.last_submit_at,
                sa.accepted_problem_count = COALESCE(sps.accepted_problem_count, 0),
                sa.submitted_problem_count = {evidence_problem_count_expr},
                sa.best_total_score = tr.total_score,
                sa.latest_total_score = tr.total_score,
                sa.ranking = CASE WHEN tr.total_score IS NOT NULL THEN tr.ranking ELSE NULL END,
                sa.latest_sync_at = CURRENT_TIMESTAMP(3),
                sa.updated_at = CURRENT_TIMESTAMP(3)
            WHERE sa.offering_id = %s
            """,
            (offering_id, offering_id, offering_id),
        )
    cursor.execute("DROP TEMPORARY TABLE IF EXISTS tmp_pta_transcript_sync")
    cursor.execute("DROP TEMPORARY TABLE IF EXISTS tmp_pta_evidence_sync")
    return

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


def _resolve_experiment_and_offering(cursor, exp_dir: Path, class_id=None):
    experiment_name = exp_dir.name
    problem_set_info = _read_problem_set_info(exp_dir)
    problem_set_source_id = _pta_problem_set_source_id(problem_set_info, experiment_name)
    source_keys = _offering_source_keys(problem_set_source_id, class_id)
    placeholders = ", ".join(["%s"] * len(source_keys))
    params = [PTA_SOURCE_SYSTEM, *source_keys, class_id, class_id]
    cursor.execute(
        f"""
        SELECT id, class_id, teacher_id
        FROM assignment_offering
        WHERE source_system = %s
          AND source_offering_key IN ({placeholders})
          AND (%s IS NULL OR class_id = %s)
        ORDER BY CASE WHEN source_offering_key LIKE %s THEN 0 ELSE 1 END
        LIMIT 1
        """,
        tuple([*params, "%:CLASS:%"]),
    )
    offering_row = cursor.fetchone()
    if not offering_row:
        legacy_row = _resolve_legacy_offering(cursor, experiment_name, class_id)
        if legacy_row:
            offering_row = _migrate_legacy_offering_to_pta(
                cursor,
                legacy_row,
                experiment_name,
                problem_set_source_id,
                problem_set_info,
            )
    if not offering_row:
        ensured = _ensure_assignment_offering(cursor, experiment_name, class_id, problem_set_info)
        if ensured:
            offering_row = (ensured["offering_id"], ensured["class_id"], ensured["teacher_id"])
    if not offering_row:
        return None
    _sync_assignment_offering_deadline(cursor, offering_row[0], problem_set_info)
    cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s", (experiment_name,))
    experiment_row = cursor.fetchone()
    return {
        "pta_problem_set_id": problem_set_source_id,
        "legacy_experiment_id": experiment_row[0] if experiment_row else None,
        "offering_id": offering_row[0],
        "class_id": offering_row[1],
        "teacher_id": offering_row[2],
    }


def _resolve_legacy_offering(cursor, experiment_name: str, class_id=None):
    cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s", (experiment_name,))
    experiment_row = cursor.fetchone()
    if not experiment_row:
        return None
    source_keys = _legacy_offering_source_keys(experiment_row[0], class_id)
    placeholders = ", ".join(["%s"] * len(source_keys))
    params = [LEGACY_SOURCE_SYSTEM, *source_keys, class_id, class_id]
    cursor.execute(
        f"""
        SELECT id, class_id, teacher_id
        FROM assignment_offering
        WHERE source_system = %s
          AND source_offering_key IN ({placeholders})
          AND (%s IS NULL OR class_id = %s)
        ORDER BY CASE WHEN source_offering_key LIKE %s THEN 0 ELSE 1 END
        LIMIT 1
        """,
        tuple([*params, "%:CLASS:%"]),
    )
    return cursor.fetchone()


def _migrate_legacy_offering_to_pta(
    cursor,
    offering_row,
    experiment_name: str,
    problem_set_source_id: str,
    problem_set_info=None,
):
    offering_id, offering_class_id, teacher_id = offering_row
    template_id = _ensure_assignment_template(cursor, problem_set_source_id, experiment_name)
    source_offering_key = _offering_source_key(problem_set_source_id, offering_class_id)
    deadline_at = _parse_pta_datetime(_first_value(
        problem_set_info or {}, "endAt", "deadlineAt", "deadline", "end_at", "deadline_at"
    ))
    cursor.execute(
        """
        UPDATE assignment_offering
        SET template_id = COALESCE(%s, template_id),
            source_system = %s,
            source_offering_key = %s,
            pta_problem_set_id = %s,
            deadline_at = COALESCE(%s, deadline_at),
            updated_at = CURRENT_TIMESTAMP(3)
        WHERE id = %s
        """,
        (
            template_id,
            PTA_SOURCE_SYSTEM,
            source_offering_key,
            problem_set_source_id,
            deadline_at,
            offering_id,
        ),
    )
    return (offering_id, offering_class_id, teacher_id)


def _sync_one_experiment(
    conn,
    crawl_dir: Path,
    exp_dir: Path,
    pta_user_map: dict,
    student_name_map: dict,
    class_id=None,
    pta_group_context=None,
):
    cursor = conn.cursor()
    _log_sync_stage("开始同步实验", experiment=exp_dir.name, class_id=class_id)
    resolved = _resolve_experiment_and_offering(cursor, exp_dir, class_id)
    if not resolved:
        _log_sync_stage("跳过实验同步", experiment=exp_dir.name, reason="missing_assignment_offering_mapping")
        return {"experiment": exp_dir.name, "skipped": True, "reason": "missing assignment_offering mapping"}
    _update_assignment_offering_pta_group(cursor, resolved["offering_id"], pta_group_context)

    import_job_id = _ensure_import_job(cursor, resolved["class_id"], {"experiment": exp_dir.name})
    conn.commit()
    _log_sync_stage(
        "实验同步任务已创建",
        experiment=exp_dir.name,
        import_job_id=import_job_id,
        offering_id=resolved["offering_id"],
        class_id=resolved["class_id"],
    )

    report = {
        "experiment": exp_dir.name,
        "import_job_id": import_job_id,
        "offering_id": resolved["offering_id"],
        "class_id": resolved["class_id"],
        "raw_submission_rows": 0,
        "raw_transcript_rows": 0,
        "raw_answer_sheet_rows": 0,
        "problem_detail_rows": 0,
        "attempts_upserted": 0,
        "students_resolved": 0,
        "official_roster_students": len(pta_group_context.get("active_student_nos", [])) if pta_group_context else None,
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
        for relative_name, role in (
            ("题目内容.txt", "PROBLEM_CONTENT"),
            ("题目详情.json", "PROBLEM_DETAILS"),
            ("提交记录.csv", "SUBMISSIONS"),
        ):
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

        _log_sync_stage("开始读取同步源文件", experiment=exp_dir.name, files=",".join(sorted(files.keys())) or "none")
        stage_start = time.perf_counter()
        transcript_rows = _read_transcript_rows(files["PAPER_TRANSCRIPT"][0]) if "PAPER_TRANSCRIPT" in files else []
        submission_rows = _read_submission_rows(files["SUBMISSIONS"][0]) if "SUBMISSIONS" in files else []
        problem_detail_rows = _read_problem_detail_rows(files["PROBLEM_DETAILS"][0]) if "PROBLEM_DETAILS" in files else []
        answer_sheet_rows = _read_answer_sheet_rows(files["ANSWER_SHEET"][0]) if "ANSWER_SHEET" in files else []
        scored_code_rows = _read_scored_code_rows(files["SCORED_CODE"][0]) if "SCORED_CODE" in files else []
        _log_sync_stage(
            "同步源文件读取完成",
            experiment=exp_dir.name,
            transcript_rows=len(transcript_rows),
            submission_rows=len(submission_rows),
            problem_detail_rows=len(problem_detail_rows),
            answer_sheet_rows=len(answer_sheet_rows),
            scored_code_rows=len(scored_code_rows),
            elapsed_ms=_elapsed_ms(stage_start),
        )

        student_no_to_id = dict(pta_group_context.get("student_no_to_id", {})) if pta_group_context else {}
        binding_cache = set()

        def ensure_binding_once(student_id, pta_user_id):
            if not pta_user_id:
                return
            key = (student_id, pta_user_id)
            if key in binding_cache:
                return
            _ensure_binding(cursor, student_id, pta_user_id)
            binding_cache.add(key)

        _log_sync_stage("开始同步学生档案", experiment=exp_dir.name, transcript_rows=len(transcript_rows))
        stage_start = time.perf_counter()
        student_names_to_ensure = {}
        for row in transcript_rows:
            student_names_to_ensure[row["student_no"]] = row["student_name"]
        for row in answer_sheet_rows:
            student_names_to_ensure.setdefault(row["student_no"], row["student_name"])
        for row in scored_code_rows:
            student_names_to_ensure.setdefault(
                row["student_no"],
                student_name_map.get(row["student_no"], row["student_no"]),
            )
        for pta_user_id, student_no in pta_user_map.items():
            if student_no:
                student_names_to_ensure.setdefault(student_no, student_name_map.get(student_no, student_no))
        for student_no, student_name in student_name_map.items():
            if not _is_valid_student_identity(student_no, student_name):
                continue
            if student_no in student_names_to_ensure:
                student_names_to_ensure.setdefault(student_no, student_name)
        ensured_students = _bulk_ensure_student_profiles(cursor, student_names_to_ensure)
        student_no_to_id.update(ensured_students)
        official_student_ids = [
            student_id
            for student_no, student_id in student_no_to_id.items()
            if _is_official_roster_student(pta_group_context, student_no)
        ]
        _bulk_ensure_class_members(cursor, resolved["class_id"], official_student_ids)
        _bulk_ensure_assignment_participants(
            cursor,
            resolved["offering_id"],
            {
                student_id: _participant_roster_scope(pta_group_context, student_no)
                for student_no, student_id in student_no_to_id.items()
                if student_id
            },
        )
        report["students_resolved"] = len(student_no_to_id)
        _log_sync_stage("学生档案同步完成", experiment=exp_dir.name, students_resolved=report["students_resolved"], elapsed_ms=_elapsed_ms(stage_start))

        for pta_user_id, student_no in pta_user_map.items():
            student_id = student_no_to_id.get(student_no)
            if student_id:
                ensure_binding_once(student_id, pta_user_id)

        if "PAPER_TRANSCRIPT" in files:
            _log_sync_stage("开始同步学生成绩单", experiment=exp_dir.name, rows=len(transcript_rows))
            stage_start = time.perf_counter()
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
            _log_sync_stage("学生成绩单同步完成", experiment=exp_dir.name, rows=report["raw_transcript_rows"], elapsed_ms=_elapsed_ms(stage_start))

        _log_sync_stage("开始生成学生作业参与记录", experiment=exp_dir.name)
        stage_start = time.perf_counter()
        _materialize_student_assignments(
            cursor,
            resolved["offering_id"],
            resolved["class_id"],
            pta_group_context,
        )
        _log_sync_stage("学生作业参与记录生成完成", experiment=exp_dir.name, elapsed_ms=_elapsed_ms(stage_start))

        problem_cache = {}
        code_artifact_updates = {}
        answer_sheet_artifact_by_student = {}
        problem_order = {}
        next_problem_order = 1
        unmapped_pta_users = set()
        problem_specs = {}
        for row in problem_detail_rows:
            pta_problem_id = str(row.get("problem_set_problem_id") or "").strip()
            if pta_problem_id and pta_problem_id not in problem_order:
                problem_order[pta_problem_id] = next_problem_order
                next_problem_order += 1
            if pta_problem_id:
                problem_specs[pta_problem_id] = {
                    "title": row.get("title") or f"PTA Problem {pta_problem_id}",
                    "sort_order": problem_order[pta_problem_id],
                }
        for row in sorted(scored_code_rows, key=lambda item: (item["pta_problem_id"], item["student_no"], item["relative_name"])):
            pta_problem_id = row["pta_problem_id"]
            if pta_problem_id and pta_problem_id not in problem_order:
                problem_order[pta_problem_id] = next_problem_order
                next_problem_order += 1
            if pta_problem_id:
                problem_specs[pta_problem_id] = {
                    "title": row["problem_title"],
                    "sort_order": problem_order[pta_problem_id],
                }
        for row in submission_rows:
            pta_problem_id = row["pta_problem_id"]
            if pta_problem_id and pta_problem_id not in problem_order:
                problem_order[pta_problem_id] = next_problem_order
                next_problem_order += 1
            if pta_problem_id:
                problem_specs.setdefault(
                    pta_problem_id,
                    {
                        "title": f"PTA Problem {pta_problem_id}",
                        "sort_order": problem_order[pta_problem_id],
                    },
                )
        stage_start = time.perf_counter()
        _bulk_ensure_assignment_problems(cursor, resolved["offering_id"], problem_specs, problem_cache)
        _log_sync_stage("题目映射预加载完成", experiment=exp_dir.name, problems=len(problem_specs), elapsed_ms=_elapsed_ms(stage_start))

        if problem_detail_rows:
            stage_start = time.perf_counter()
            report["problem_detail_rows"] = _bulk_upsert_pta_problem_details(
                cursor,
                resolved.get("legacy_experiment_id"),
                exp_dir.name,
                resolved.get("pta_problem_set_id"),
                problem_detail_rows,
            )
            _log_sync_stage(
                "题目详情同步完成",
                experiment=exp_dir.name,
                rows=report["problem_detail_rows"],
                elapsed_ms=_elapsed_ms(stage_start),
            )

        _log_sync_stage("开始同步评分代码", experiment=exp_dir.name, rows=len(scored_code_rows))
        stage_start = time.perf_counter()
        scored_code_artifact_ids = _bulk_ensure_artifacts(
            cursor,
            import_job_id,
            [
                (
                    "SCORED_CODE_SOURCE",
                    row["code_text"],
                    _source_key(exp_dir.name, "scored-code", row["relative_name"]),
                    "text/plain",
                )
                for row in scored_code_rows
            ],
        )
        scored_code_participants = {}
        for row in sorted(scored_code_rows, key=lambda item: (item["pta_problem_id"], item["student_no"], item["relative_name"])):
            problem_id = problem_cache.get(row["pta_problem_id"])
            student_id = student_no_to_id.get(row["student_no"])
            if not student_id:
                student_name = student_name_map.get(row["student_no"], row["student_no"])
                student_id = _ensure_student_profile(cursor, row["student_no"], student_name)
                _ensure_class_member_if_official(
                    cursor,
                    resolved["class_id"],
                    student_id,
                    row["student_no"],
                    pta_group_context,
                )
                student_no_to_id[row["student_no"]] = student_id
            ensure_binding_once(student_id, row["pta_user_id"])
            scored_code_participants[student_id] = _participant_roster_scope(pta_group_context, row["student_no"])
            code_artifact_id = scored_code_artifact_ids.get(
                _source_key(exp_dir.name, "scored-code", row["relative_name"])
            )
            code_artifact_updates[(problem_id, student_id)] = code_artifact_id
        _bulk_ensure_assignment_participants(cursor, resolved["offering_id"], scored_code_participants)
        _log_sync_stage("评分代码同步完成", experiment=exp_dir.name, rows=len(scored_code_rows), artifacts=len(scored_code_artifact_ids), elapsed_ms=_elapsed_ms(stage_start))

        _log_sync_stage("开始同步提交记录", experiment=exp_dir.name, rows=len(submission_rows))
        stage_start = time.perf_counter()
        raw_submission_ids = {}
        if "SUBMISSIONS" in files:
            substage_start = time.perf_counter()
            raw_submission_ids = _bulk_upsert_raw_submission_rows(
                cursor,
                import_job_id,
                files["SUBMISSIONS"][1],
                submission_rows,
            )
            _log_sync_stage("提交记录原始行批量写入完成", experiment=exp_dir.name, rows=len(raw_submission_ids), elapsed_ms=_elapsed_ms(substage_start))
        attempt_rows = []
        submission_participants = {}
        for row in submission_rows:
            problem_id = problem_cache.get(row["pta_problem_id"])
            student_no = pta_user_map.get(row["pta_user_id"])
            student_name = student_name_map.get(student_no, student_no) if student_no else ""
            if not student_no and row["pta_user_id"]:
                unmapped_pta_users.add(row["pta_user_id"])
                report["unmapped_submission_rows"] += 1
            if student_no and not _is_valid_student_identity(student_no, student_name):
                student_no = None
            if student_no and student_no not in student_no_to_id:
                student_id = _ensure_student_profile(cursor, student_no, student_name)
                _ensure_class_member_if_official(
                    cursor,
                    resolved["class_id"],
                    student_id,
                    student_no,
                    pta_group_context,
                )
                student_no_to_id[student_no] = student_id
            student_id = student_no_to_id.get(student_no)
            if student_id:
                ensure_binding_once(student_id, row["pta_user_id"])

            if not student_id or not problem_id:
                continue
            submission_participants[student_id] = _participant_roster_scope(pta_group_context, student_no)

            source_attempt_key = _attempt_source_key(resolved["offering_id"], row, student_no)
            attempt_rows.append(
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
                    raw_submission_ids.get(int(row["row_no"])),
                )
            )
        _bulk_ensure_assignment_participants(cursor, resolved["offering_id"], submission_participants)
        substage_start = time.perf_counter()
        report["attempts_upserted"] = _bulk_upsert_student_problem_attempts(cursor, attempt_rows)
        _log_sync_stage("提交记录attempt批量写入完成", experiment=exp_dir.name, attempts=report["attempts_upserted"], elapsed_ms=_elapsed_ms(substage_start))
        report["raw_submission_rows"] = len(submission_rows)
        _log_sync_stage(
            "提交记录同步完成",
            experiment=exp_dir.name,
            rows=report["raw_submission_rows"],
            attempts_upserted=report["attempts_upserted"],
            unmapped_submission_rows=report["unmapped_submission_rows"],
            elapsed_ms=_elapsed_ms(stage_start),
        )
        if "SUBMISSIONS" in files:
            _log_sync_stage("开始清理过期提交记录", experiment=exp_dir.name)
            stage_start = time.perf_counter()
            report["stale_attempts_pruned"] = _prune_stale_attempts_for_source_file(
                cursor,
                resolved["offering_id"],
                files["SUBMISSIONS"][1],
            )
            _log_sync_stage("过期提交记录清理完成", experiment=exp_dir.name, pruned=report["stale_attempts_pruned"], elapsed_ms=_elapsed_ms(stage_start))

        _log_sync_stage("开始同步答题卡", experiment=exp_dir.name, rows=len(answer_sheet_rows))
        stage_start = time.perf_counter()
        answer_sheet_artifact_rows = []
        for row in answer_sheet_rows:
            answer_sheet_artifact_rows.append(
                (
                    "ANSWER_SHEET_HTML",
                    row["html_text"],
                    _source_key(exp_dir.name, "html", row["relative_name"]),
                    "text/html",
                )
            )
            if row["code_text"]:
                answer_sheet_artifact_rows.append(
                    (
                        "ANSWER_SHEET_CODE",
                        row["code_text"],
                        _source_key(exp_dir.name, "code", row["relative_name"]),
                        "text/plain",
                    )
                )
            if row["test_report_text"]:
                answer_sheet_artifact_rows.append(
                    (
                        "ANSWER_SHEET_REPORT",
                        row["test_report_text"],
                        _source_key(exp_dir.name, "report", row["relative_name"]),
                        "text/markdown",
                    )
                )
        answer_sheet_artifact_ids = _bulk_ensure_artifacts(
            cursor,
            import_job_id,
            answer_sheet_artifact_rows,
        )
        _log_sync_stage("答题卡artifact批量写入完成", experiment=exp_dir.name, artifacts=len(answer_sheet_artifact_ids), elapsed_ms=_elapsed_ms(stage_start))
        substage_start = time.perf_counter()
        answer_sheet_participants = {}
        raw_answer_sheet_rows = []
        for row in answer_sheet_rows:
            student_id = student_no_to_id.get(row["student_no"])
            if not student_id:
                student_id = _ensure_student_profile(cursor, row["student_no"], row["student_name"])
                _ensure_class_member_if_official(
                    cursor,
                    resolved["class_id"],
                    student_id,
                    row["student_no"],
                    pta_group_context,
                )
                student_no_to_id[row["student_no"]] = student_id
            answer_sheet_participants[student_id] = _participant_roster_scope(pta_group_context, row["student_no"])
            html_artifact_id = answer_sheet_artifact_ids.get(
                _source_key(exp_dir.name, "html", row["relative_name"])
            )
            code_artifact_id = None
            if row["code_text"]:
                code_artifact_id = answer_sheet_artifact_ids.get(
                    _source_key(exp_dir.name, "code", row["relative_name"])
                )
            report_artifact_id = None
            if row["test_report_text"]:
                report_artifact_id = answer_sheet_artifact_ids.get(
                    _source_key(exp_dir.name, "report", row["relative_name"])
                )
            answer_sheet_artifact_by_student[student_id] = html_artifact_id
            if "ANSWER_SHEET" in files:
                source_file_id = files["ANSWER_SHEET"][1]
                raw_answer_sheet_rows.append(
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
        if raw_answer_sheet_rows:
            cursor.executemany(
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
                raw_answer_sheet_rows,
            )
        _bulk_ensure_assignment_participants(cursor, resolved["offering_id"], answer_sheet_participants)
        report["raw_answer_sheet_rows"] = len(answer_sheet_rows)
        _log_sync_stage("答题卡同步完成", experiment=exp_dir.name, rows=report["raw_answer_sheet_rows"], raw_elapsed_ms=_elapsed_ms(substage_start), elapsed_ms=_elapsed_ms(stage_start))

        # Materialize once after all new students/problems have been discovered.
        # Running this inside row loops repeats a class-wide INSERT...SELECT for
        # every newly discovered student and slows large first-time imports.
        _log_sync_stage("开始刷新作业参与和题目状态", experiment=exp_dir.name)
        stage_start = time.perf_counter()
        substage_start = time.perf_counter()
        _materialize_student_assignments(
            cursor,
            resolved["offering_id"],
            resolved["class_id"],
            pta_group_context,
        )
        _bulk_ensure_assignment_participants(
            cursor,
            resolved["offering_id"],
            {
                student_id: _participant_roster_scope(pta_group_context, student_no)
                for student_no, student_id in student_no_to_id.items()
                if student_id
            },
        )
        _log_sync_stage("刷新参与记录补齐完成", experiment=exp_dir.name, elapsed_ms=_elapsed_ms(substage_start))
        substage_start = time.perf_counter()
        report["stale_problem_states_pruned"] = _prune_orphan_problem_states(cursor, resolved["offering_id"])
        _log_sync_stage("孤立题目状态清理完成", experiment=exp_dir.name, pruned=report["stale_problem_states_pruned"], elapsed_ms=_elapsed_ms(substage_start))
        substage_start = time.perf_counter()
        _recalc_problem_state(cursor, resolved["offering_id"])
        _log_sync_stage("题目状态批量刷新完成", experiment=exp_dir.name, elapsed_ms=_elapsed_ms(substage_start))
        code_state_updates = [
            (artifact_id, resolved["offering_id"], problem_id, student_id)
            for (problem_id, student_id), artifact_id in code_artifact_updates.items()
            if artifact_id
        ]
        if code_state_updates:
            substage_start = time.perf_counter()
            cursor.executemany(
                """
                UPDATE student_problem_state
                SET latest_code_artifact_id = %s,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND problem_id = %s
                  AND student_id = %s
                """,
                code_state_updates,
            )
            _log_sync_stage("代码artifact状态回填完成", experiment=exp_dir.name, rows=len(code_state_updates), elapsed_ms=_elapsed_ms(substage_start))
        answer_state_updates = [
            (artifact_id, resolved["offering_id"], student_id)
            for student_id, artifact_id in answer_sheet_artifact_by_student.items()
            if artifact_id
        ]
        if answer_state_updates:
            substage_start = time.perf_counter()
            cursor.executemany(
                """
                UPDATE student_problem_state
                SET latest_answer_sheet_artifact_id = %s,
                    updated_at = CURRENT_TIMESTAMP(3)
                WHERE offering_id = %s
                  AND student_id = %s
                """,
                answer_state_updates,
            )
            _log_sync_stage("答题卡artifact状态回填完成", experiment=exp_dir.name, rows=len(answer_state_updates), elapsed_ms=_elapsed_ms(substage_start))
        report["students_resolved"] = len(student_no_to_id)
        if unmapped_pta_users:
            report["unmapped_pta_user_ids"] = sorted(unmapped_pta_users)[:20]
        substage_start = time.perf_counter()
        _recalc_student_assignment(
            cursor,
            resolved["offering_id"],
            transcript_rows,
            student_no_to_id,
            answer_sheet_rows,
            scored_code_rows,
        )
        _log_sync_stage("学生作业状态批量刷新完成", experiment=exp_dir.name, elapsed_ms=_elapsed_ms(substage_start))
        _log_sync_stage(
            "作业参与和题目状态刷新完成",
            experiment=exp_dir.name,
            students_resolved=report["students_resolved"],
            stale_problem_states_pruned=report["stale_problem_states_pruned"],
            elapsed_ms=_elapsed_ms(stage_start),
        )

        _update_import_job(cursor, import_job_id, "SUCCEEDED", report, None)
        stage_start = time.perf_counter()
        conn.commit()
        _log_sync_stage("实验同步完成", experiment=exp_dir.name, import_job_id=import_job_id, commit_elapsed_ms=_elapsed_ms(stage_start))
        return report
    except Exception as exc:
        _log_sync_stage("实验同步失败", experiment=exp_dir.name, error=str(exc)[:300])
        conn.rollback()
        fail_cursor = conn.cursor()
        _update_import_job(fail_cursor, import_job_id, "FAILED", report, str(exc)[:1000])
        conn.commit()
        raise


def sync_all(crawl_dir=None, strict=True, class_id=None):
    crawl_dir = _get_crawl_dir(crawl_dir)
    _log_sync_stage("开始统一库同步", crawl_dir=crawl_dir, class_id=class_id)
    roster_payload = _load_pta_user_group_roster(crawl_dir)
    report = {
        "ok": False,
        "mode": "unified",
        "crawl_dir": str(crawl_dir),
        "class_id": class_id,
        "experiments": [],
    }
    conn = legacy_sync.get_db()
    try:
        exp_dirs = _iter_experiment_dirs(crawl_dir)
        _log_sync_stage("实验目录扫描完成", crawl_dir=crawl_dir, experiments=len(exp_dirs))
        if not exp_dirs:
            message = f"No experiment data found in crawl directory: {crawl_dir}"
            report["error"] = message
            _log_sync_stage("统一库同步结束", ok=False, error=message)
            if strict:
                raise RuntimeError(message)
            return report
        if class_id is None:
            with conn.cursor() as cursor:
                class_id = _resolve_class_id_from_roster(cursor, roster_payload)
            report["class_id"] = class_id
            _log_sync_stage("班级解析完成", class_id=class_id)
        if class_id is None:
            message = (
                "Unified PTA sync requires class_id or a PTA user group bound to teaching_class. "
                "Set teaching_class.pta_group_id/pta_group_name or pass class_id in the crawl request."
            )
            report["error"] = message
            _log_sync_stage("统一库同步结束", ok=False, error="missing_class_id")
            if strict:
                raise RuntimeError(message)
            return report
        with conn.cursor() as cursor:
            if _get_class_by_id(cursor, class_id) is None:
                message = f"Unified PTA sync requires an existing teaching_class, class_id={class_id} was not found."
                report["error"] = message
                _log_sync_stage("统一库同步结束", ok=False, error="class_not_found", class_id=class_id)
                if strict:
                    raise RuntimeError(message)
                return report
        pta_group_context = None
        if roster_payload:
            with conn.cursor() as cursor:
                pta_group_context = _ensure_pta_user_group_roster(cursor, roster_payload, class_id)
            conn.commit()
            if pta_group_context:
                report["pta_user_group"] = {
                    "pta_group_id": pta_group_context["pta_group_id"],
                    "pta_group_name": pta_group_context["pta_group_name"],
                    "member_count": len(pta_group_context["active_student_nos"]),
                }
                _log_sync_stage(
                    "PTA用户组花名册同步完成",
                    class_id=class_id,
                    pta_group_id=pta_group_context["pta_group_id"],
                    member_count=len(pta_group_context["active_student_nos"]),
                )
        pta_user_map, student_name_map = _build_student_maps_from_crawl(crawl_dir, pta_group_context)
        report["pta_user_mappings"] = len(pta_user_map)
        report["student_name_mappings"] = len(student_name_map)
        _log_sync_stage(
            "学生映射构建完成",
            pta_user_mappings=len(pta_user_map),
            student_name_mappings=len(student_name_map),
        )

        for exp_dir in exp_dirs:
            try:
                report["experiments"].append(
                    _sync_one_experiment(
                        conn,
                        crawl_dir,
                        exp_dir,
                        pta_user_map,
                        student_name_map,
                        class_id,
                        pta_group_context,
                    )
                )
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
        _log_sync_stage("统一库同步完成", experiments=len(report["experiments"]), ok=True)
        return report
    except Exception as exc:
        report["error"] = str(exc)
        _log_sync_stage("统一库同步失败", error=str(exc)[:300])
        if strict:
            raise
        return report
    finally:
        conn.close()


def run_configured_sync(crawl_dir=None, strict=True, class_id=None):
    use_unified = _flag("ACADEMIC_UNIFIED_IMPORT_ENABLED", True)
    _log_sync_stage("同步配置加载完成", unified_enabled=use_unified, class_id=class_id)
    result = {
        "ok": False,
        "legacy_enabled": False,
        "unified_enabled": use_unified,
    }
    if use_unified:
        _log_sync_stage("开始执行统一库导入器", crawl_dir=crawl_dir, class_id=class_id)
        result["unified"] = sync_all(crawl_dir=crawl_dir, strict=strict, class_id=class_id)
    if use_unified:
        result["ok"] = bool(result.get("unified", {}).get("ok"))
    else:
        result["ok"] = True
        result["message"] = "Unified importer disabled"
    _log_sync_stage("同步配置执行完成", ok=result["ok"], unified_enabled=use_unified)
    return result


def _experiment_export_files(exp_dir: Path):
    export_dir = exp_dir / PTA_EXPORT_DIR
    files = {}
    if not export_dir.exists():
        return files
    for pattern, role in (
        ("*PAPER_TRANSCRIPT*.xlsx", "PAPER_TRANSCRIPT"),
        ("*ANSWER_SHEET*.zip", "ANSWER_SHEET"),
        ("*SCORED_CODE*.zip", "SCORED_CODE"),
    ):
        matched = sorted(export_dir.glob(pattern))
        if matched:
            files[role] = matched[0]
    return files


def _build_student_maps_from_crawl(crawl_dir: Path, pta_group_context=None):
    pta_user_map = {}
    student_name_map = {}
    if pta_group_context:
        pta_user_map.update(pta_group_context.get("pta_user_to_student_no", {}))
        student_name_map.update(pta_group_context.get("student_no_to_name", {}))

    for exp_dir in _iter_experiment_dirs(crawl_dir):
        files = _experiment_export_files(exp_dir)
        if "PAPER_TRANSCRIPT" in files:
            for row in _read_transcript_rows(files["PAPER_TRANSCRIPT"]):
                student_name_map[row["student_no"]] = row["student_name"]
        if "ANSWER_SHEET" in files:
            for row in _read_answer_sheet_rows(files["ANSWER_SHEET"]):
                student_name_map[row["student_no"]] = row["student_name"]
        if "SCORED_CODE" in files:
            for row in _read_scored_code_rows(files["SCORED_CODE"]):
                if row.get("pta_user_id") and row.get("student_no"):
                    pta_user_map.setdefault(row["pta_user_id"], row["student_no"])
    return pta_user_map, student_name_map


if __name__ == "__main__":
    crawl_dir = os.sys.argv[1] if len(os.sys.argv) > 1 else None
    print(json.dumps(run_configured_sync(crawl_dir=crawl_dir, strict=False), ensure_ascii=False, indent=2))
