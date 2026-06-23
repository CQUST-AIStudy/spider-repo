# -*- coding: utf-8 -*-
"""
Crawl per-student PTA submission pages.

Input URLs follow:
  https://pintia.cn/problem-sets/{problem_set_id}/examinees/{pta_user_id}?tab=submissions

By default the script exports compact CSV files. With --write-db it also writes:
  - pta_api_submission_row: raw API lineage
  - student_problem_attempt: one row per submission attempt
  - student_problem_state: per-student per-problem aggregate
  - student_assignment: per-student per-experiment aggregate
"""
import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))


URL_RE = re.compile(r"/problem-sets/(?P<problem_set_id>\d+)/examinees/(?P<pta_user_id>\d+)")

PROBLEM_TYPES = (
    "PROGRAMMING",
    "CODE_COMPLETION",
    "MULTIPLE_CHOICE",
    "SINGLE_CHOICE",
    "MULTI_CHOICE",
    "FILL_IN_BLANK",
    "FUNCTION",
    "SUBJECTIVE",
    "TRUE_OR_FALSE",
)

STATUS_TEXT_ZH = {
    "ACCEPTED": "答案正确",
    "PARTIAL_ACCEPTED": "部分正确",
    "PARTIAL_CORRECT": "部分正确",
    "WRONG_ANSWER": "答案错误",
    "COMPILE_ERROR": "编译错误",
    "TIME_LIMIT_EXCEEDED": "运行超时",
    "MEMORY_LIMIT_EXCEEDED": "内存超限",
    "RUNTIME_ERROR": "运行错误",
    "PENDING": "等待评测",
    "WAITING": "等待评测",
    "JUDGING": "正在评测",
    "OVERRIDDEN": "已被覆盖",
    "OVERWRITTEN": "已被覆盖",
}


def normalize_id(value):
    return str(value or "").strip()


def parse_targets(urls):
    targets = defaultdict(set)
    for raw_url in urls:
        match = URL_RE.search(str(raw_url).strip())
        if not match:
            raise ValueError(f"Cannot parse PTA examinee submission URL: {raw_url}")
        targets[match.group("problem_set_id")].add(match.group("pta_user_id"))
    return targets


def parse_datetime(value):
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    if text.isdigit():
        timestamp = int(text)
        if timestamp > 10_000_000_000:
            timestamp = timestamp / 1000
        return datetime.fromtimestamp(timestamp)
    normalized = text.replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(normalized)
        if dt.tzinfo is not None:
            dt = dt.astimezone().replace(tzinfo=None)
        return dt
    except ValueError:
        pass
    normalized = text.replace("/", "-").replace("T", " ")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M"):
        try:
            return datetime.strptime(normalized, fmt)
        except ValueError:
            continue
    return None


def format_datetime(value):
    parsed = parse_datetime(value)
    return parsed.strftime("%Y/%m/%d %H:%M:%S") if parsed else str(value or "").strip()


def score_value(submission):
    for key in ("score", "totalScore", "finalScore"):
        value = submission.get(key)
        if value is not None and str(value).strip() != "":
            try:
                return float(value)
            except (TypeError, ValueError):
                return None
    return None


def score_text(submission):
    value = score_value(submission)
    if value is None:
        return ""
    return str(int(value)) if value.is_integer() else str(value)


def runtime_ms(submission):
    value = submission.get("time")
    if value is None or str(value).strip() == "":
        value = submission.get("timeUsed")
    if value is None or str(value).strip() == "":
        return None
    try:
        return int(round(float(value) * 1000))
    except (TypeError, ValueError):
        return None


def memory_kb(submission):
    value = submission.get("memory")
    if value is None or str(value).strip() == "":
        return None
    try:
        return int(round(float(value) / 1024))
    except (TypeError, ValueError):
        return None


def compact_problem_source_id(submission):
    pta_problem_id = normalize_id(
        submission.get("problemSetProblemId")
        or submission.get("problemId")
        or submission.get("problem_id")
    )
    problem_type = normalize_id(submission.get("problemType"))
    if pta_problem_id == "0" and problem_type in {"MULTIPLE_CHOICE", "SINGLE_CHOICE", "MULTI_CHOICE"}:
        return "MULTIPLE_CHOICE:0"
    return pta_problem_id


def compact_problem_display(submission, problem_map):
    source_id = compact_problem_source_id(submission)
    if source_id in problem_map:
        return problem_map[source_id]["problem_no"]
    if source_id == "MULTIPLE_CHOICE:0":
        return "单选题"
    return source_id


def compact_row(submission, problem_map, fallback_user_id):
    pta_user_id = normalize_id(submission.get("userId")) or fallback_user_id
    status = normalize_id(submission.get("status"))
    runtime = runtime_ms(submission)
    return {
        "学生ID": pta_user_id,
        "提交时间": format_datetime(submission.get("submitAt") or submission.get("submittedAt")),
        "状态": STATUS_TEXT_ZH.get(status, status),
        "分数": score_text(submission),
        "题目": compact_problem_display(submission, problem_map),
        "用时": "" if runtime is None else f"{runtime} ms",
    }


def safe_filename(value):
    return re.sub(r'[<>:"/\\|?*\r\n]+', "_", str(value)).strip() or "problem-set"


def problem_set_name(client, problem_set_id):
    try:
        detail = client.get_problem_set_detail(problem_set_id)
    except Exception:
        return problem_set_id
    if isinstance(detail, dict):
        if isinstance(detail.get("problemSet"), dict):
            name = detail["problemSet"].get("name")
            if name:
                return str(name).strip()
        if detail.get("name"):
            return str(detail["name"]).strip()
    return problem_set_id


def build_problem_map(client, problem_set_id):
    result = {}
    sort_order = 1
    for problem_type in PROBLEM_TYPES:
        try:
            data = client.api_get(
                f"/problem-sets/{problem_set_id}/preview/problems",
                params={"problem_type": problem_type, "page": 0, "limit": 500},
            )
        except Exception:
            continue
        for problem in data.get("problemSetProblems", []):
            pta_problem_id = normalize_id(problem.get("id") or problem.get("problemSetProblemId"))
            if not pta_problem_id:
                continue
            label = str(problem.get("label") or "").strip()
            title = str(problem.get("title") or "").strip()
            nested_problem = problem.get("problem")
            if not title and isinstance(nested_problem, dict):
                title = str(nested_problem.get("title") or "").strip()
            result[pta_problem_id] = {
                "source_problem_id": pta_problem_id,
                "problem_no": label or pta_problem_id,
                "title": title or f"PTA Problem {pta_problem_id}",
                "sort_order": sort_order,
            }
            sort_order += 1
    result.setdefault(
        "MULTIPLE_CHOICE:0",
        {
            "source_problem_id": "MULTIPLE_CHOICE:0",
            "problem_no": "单选题",
            "title": "单选题",
            "sort_order": sort_order,
        },
    )
    return result


def get_user_submissions(client, problem_set_id, pta_user_id):
    submissions = []
    seen_ids = set()
    page = 0
    while page < 100:
        data = client.api_get(
            f"/problem-sets/{problem_set_id}/submissions",
            params={
                "page": page,
                "limit": 200,
                "filter": json.dumps({"userId": pta_user_id}),
            },
        )
        page_items = data.get("submissions", [])
        if not page_items:
            break
        new_count = 0
        for submission in page_items:
            submission_id = normalize_id(submission.get("id"))
            if submission_id and submission_id in seen_ids:
                continue
            if submission_id:
                seen_ids.add(submission_id)
            submissions.append(submission)
            new_count += 1
        if new_count == 0 or len(page_items) < 200:
            break
        page += 1
    return submissions


def load_targets(client, urls):
    targets = parse_targets(urls)
    loaded = {}
    for problem_set_id, pta_user_ids in targets.items():
        name = problem_set_name(client, problem_set_id)
        problem_map = build_problem_map(client, problem_set_id)
        user_submissions = {}
        for pta_user_id in sorted(pta_user_ids):
            user_submissions[pta_user_id] = get_user_submissions(client, problem_set_id, pta_user_id)
        loaded[problem_set_id] = {
            "name": name,
            "problem_map": problem_map,
            "user_submissions": user_submissions,
        }
    return loaded


def export_loaded(loaded, output_dir):
    base_output_dir = Path(output_dir).resolve()
    base_output_dir.mkdir(parents=True, exist_ok=True)
    written_files = []
    for problem_set_id, bundle in loaded.items():
        rows = []
        for pta_user_id, submissions in bundle["user_submissions"].items():
            for submission in submissions:
                rows.append(compact_row(submission, bundle["problem_map"], pta_user_id))
        rows.sort(key=lambda row: (row["学生ID"], row["提交时间"]), reverse=True)
        out_dir = base_output_dir / safe_filename(bundle["name"])
        out_dir.mkdir(parents=True, exist_ok=True)
        out_file = out_dir / "学生提交记录_精简.csv"
        with out_file.open("w", encoding="utf-8-sig", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["学生ID", "提交时间", "状态", "分数", "题目", "用时"])
            writer.writeheader()
            writer.writerows(rows)
        print(f"{bundle['name']}: wrote {len(rows)} rows -> {out_file}")
        written_files.append(out_file)
    return written_files


def resolve_offering(cursor, problem_set_id, experiment_name, problem_count):
    import sync_to_unified_db as unified

    cursor.execute(
        """
        SELECT id, class_id
        FROM assignment_offering
        WHERE pta_problem_set_id = %s
        LIMIT 1
        """,
        (problem_set_id,),
    )
    row = cursor.fetchone()
    if row:
        return {"offering_id": row[0], "class_id": row[1]}

    cursor.execute(
        """
        SELECT ao.id, ao.class_id
        FROM assignment_offering ao
        JOIN assignment_template at ON at.id = ao.template_id
        WHERE ao.title_override = %s OR at.title = %s
        ORDER BY ao.id DESC
        LIMIT 1
        """,
        (experiment_name, experiment_name),
    )
    row = cursor.fetchone()
    if row:
        cursor.execute(
            "UPDATE assignment_offering SET pta_problem_set_id = %s, updated_at = CURRENT_TIMESTAMP(3) WHERE id = %s",
            (problem_set_id, row[0]),
        )
        return {"offering_id": row[0], "class_id": row[1]}

    cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s LIMIT 1", (experiment_name,))
    row = cursor.fetchone()
    if row:
        legacy_experiment_id = row[0]
    else:
        cursor.execute("SELECT COALESCE(MAX(num), 0) + 1 FROM experiment")
        next_num = cursor.fetchone()[0]
        cursor.execute(
            "INSERT INTO experiment (num, name, topic_sum) VALUES (%s, %s, %s)",
            (next_num, experiment_name, problem_count),
        )
        legacy_experiment_id = cursor.lastrowid

    resolved = unified._ensure_assignment_offering(cursor, legacy_experiment_id, experiment_name)
    if not resolved:
        raise RuntimeError(f"Cannot match teaching_class for PTA problem set: {experiment_name}")
    cursor.execute(
        "UPDATE assignment_offering SET pta_problem_set_id = %s, updated_at = CURRENT_TIMESTAMP(3) WHERE id = %s",
        (problem_set_id, resolved["offering_id"]),
    )
    return resolved


def ensure_problem(cursor, offering_id, problem_info, cache):
    source_problem_id = problem_info["source_problem_id"]
    if source_problem_id in cache:
        return cache[source_problem_id]
    cursor.execute(
        "SELECT id FROM assignment_problem WHERE offering_id = %s AND source_problem_id = %s LIMIT 1",
        (offering_id, source_problem_id),
    )
    row = cursor.fetchone()
    if row:
        cursor.execute(
            """
            UPDATE assignment_problem
            SET title = COALESCE(NULLIF(%s, ''), title),
                sort_order = CASE WHEN sort_order = 0 THEN %s ELSE sort_order END,
                status = 'ACTIVE',
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = %s
            """,
            (problem_info["title"], problem_info["sort_order"], row[0]),
        )
        cache[source_problem_id] = row[0]
        return row[0]
    cursor.execute(
        """
        INSERT INTO assignment_problem
          (offering_id, problem_no, source_problem_id, title, sort_order, status)
        VALUES
          (%s, %s, %s, %s, %s, 'ACTIVE')
        """,
        (
            offering_id,
            problem_info["problem_no"],
            source_problem_id,
            problem_info["title"],
            problem_info["sort_order"],
        ),
    )
    cache[source_problem_id] = cursor.lastrowid
    return cursor.lastrowid


def find_student_by_pta_user(cursor, pta_user_id):
    cursor.execute(
        """
        SELECT sp.id, sp.student_no, sp.real_name
        FROM external_identity_binding eib
        JOIN student_profile sp ON sp.id = eib.entity_id
        WHERE eib.entity_type = 'STUDENT_PROFILE'
          AND eib.source_system = 'PTA'
          AND eib.binding_type = 'PTA_USER_ID'
          AND eib.external_id = %s
          AND eib.is_active = TRUE
        ORDER BY eib.id DESC
        LIMIT 1
        """,
        (pta_user_id,),
    )
    row = cursor.fetchone()
    if not row:
        return None
    return {"student_id": row[0], "student_no": row[1], "student_name": row[2]}


def source_attempt_key(problem_set_id, submission):
    import sync_to_unified_db as unified

    submission_id = normalize_id(submission.get("id"))
    if submission_id:
        return unified._source_key("PTA_API_SUBMISSION", problem_set_id, submission_id)
    submitted = parse_datetime(submission.get("submitAt") or submission.get("submittedAt"))
    submitted_marker = submitted.strftime("%Y-%m-%d %H:%M:%S") if submitted else ""
    return unified._source_key(
        "PTA_API_SUBMISSION_FALLBACK",
        problem_set_id,
        normalize_id(submission.get("userId")),
        compact_problem_source_id(submission),
        submitted_marker,
        normalize_id(submission.get("status")),
        str(score_value(submission) or ""),
    )


def insert_raw_api_row(
    cursor,
    import_job_id,
    offering_id,
    problem_id,
    student_id,
    problem_set_id,
    pta_user_id,
    submission,
):
    submitted_at = parse_datetime(submission.get("submitAt") or submission.get("submittedAt"))
    score = score_value(submission)
    cursor.execute(
        """
        INSERT INTO pta_api_submission_row (
          import_job_id, offering_id, problem_id, student_id,
          pta_problem_set_id, pta_user_id, pta_submission_id, pta_problem_id,
          problem_type, judge_status, score, compiler, runtime_ms, memory_kb,
          submitted_at, raw_json
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          import_job_id = VALUES(import_job_id),
          offering_id = VALUES(offering_id),
          problem_id = VALUES(problem_id),
          student_id = VALUES(student_id),
          pta_problem_set_id = VALUES(pta_problem_set_id),
          pta_user_id = VALUES(pta_user_id),
          pta_problem_id = VALUES(pta_problem_id),
          problem_type = VALUES(problem_type),
          judge_status = VALUES(judge_status),
          score = VALUES(score),
          compiler = VALUES(compiler),
          runtime_ms = VALUES(runtime_ms),
          memory_kb = VALUES(memory_kb),
          submitted_at = VALUES(submitted_at),
          raw_json = VALUES(raw_json),
          updated_at = CURRENT_TIMESTAMP(3)
        """,
        (
            import_job_id,
            offering_id,
            problem_id,
            student_id,
            problem_set_id,
            pta_user_id,
            normalize_id(submission.get("id")) or None,
            compact_problem_source_id(submission) or None,
            normalize_id(submission.get("problemType")) or None,
            normalize_id(submission.get("status")) or None,
            score,
            normalize_id(submission.get("compiler")) or None,
            runtime_ms(submission),
            memory_kb(submission),
            submitted_at,
            json.dumps(submission, ensure_ascii=False),
        ),
    )
    submission_id = normalize_id(submission.get("id"))
    if submission_id:
        cursor.execute("SELECT id FROM pta_api_submission_row WHERE pta_submission_id = %s", (submission_id,))
        return cursor.fetchone()[0]
    cursor.execute("SELECT LAST_INSERT_ID()")
    return cursor.fetchone()[0]


def write_loaded_to_db(loaded):
    from pta_spider import sync_to_db as legacy_sync
    import sync_to_unified_db as unified

    conn = legacy_sync.get_db()
    report = {
        "problem_sets": [],
        "raw_rows_upserted": 0,
        "attempts_upserted": 0,
        "unmapped_pta_user_ids": [],
    }
    try:
        with conn.cursor() as cursor:
            for problem_set_id, bundle in loaded.items():
                problem_count = max(0, len(bundle["problem_map"]) - 1)
                resolved = resolve_offering(cursor, problem_set_id, bundle["name"], problem_count)
                offering_id = resolved["offering_id"]
                class_id = resolved["class_id"]
                import_job_id = unified._ensure_import_job(
                    cursor,
                    class_id,
                    {
                        "job_type": "PTA_EXAMINEE_SUBMISSIONS",
                        "pta_problem_set_id": problem_set_id,
                        "experiment_name": bundle["name"],
                    },
                )
                unified._materialize_student_assignments(cursor, offering_id, class_id)

                problem_cache = {}
                set_report = {
                    "pta_problem_set_id": problem_set_id,
                    "experiment_name": bundle["name"],
                    "offering_id": offering_id,
                    "raw_rows": 0,
                    "attempts": 0,
                    "unmapped_pta_user_ids": [],
                }
                touched_student_ids = set()

                for pta_user_id, submissions in bundle["user_submissions"].items():
                    student = find_student_by_pta_user(cursor, pta_user_id)
                    if not student:
                        set_report["unmapped_pta_user_ids"].append(pta_user_id)
                    for submission in submissions:
                        source_problem_id = compact_problem_source_id(submission)
                        problem_info = bundle["problem_map"].get(
                            source_problem_id,
                            {
                                "source_problem_id": source_problem_id,
                                "problem_no": source_problem_id,
                                "title": f"PTA Problem {source_problem_id}",
                                "sort_order": 0,
                            },
                        )
                        problem_id = ensure_problem(cursor, offering_id, problem_info, problem_cache)
                        student_id = student["student_id"] if student else None
                        raw_api_id = insert_raw_api_row(
                            cursor,
                            import_job_id,
                            offering_id,
                            problem_id,
                            student_id,
                            problem_set_id,
                            pta_user_id,
                            submission,
                        )
                        set_report["raw_rows"] += 1
                        report["raw_rows_upserted"] += 1
                        if not student:
                            continue
                        touched_student_ids.add(student["student_id"])

                        submitted_at = parse_datetime(submission.get("submitAt") or submission.get("submittedAt"))
                        if submitted_at is None:
                            submitted_at = datetime(2000, 1, 1)
                        cursor.execute(
                            """
                            INSERT INTO student_problem_attempt (
                              offering_id, problem_id, student_id, pta_user_id,
                              source_system, source_attempt_key, submitted_at,
                              judge_status, score, compiler, runtime_ms, memory_kb,
                              raw_api_submission_id
                            )
                            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                            ON DUPLICATE KEY UPDATE
                              judge_status = VALUES(judge_status),
                              score = VALUES(score),
                              compiler = VALUES(compiler),
                              runtime_ms = VALUES(runtime_ms),
                              memory_kb = VALUES(memory_kb),
                              raw_api_submission_id = VALUES(raw_api_submission_id)
                            """,
                            (
                                offering_id,
                                problem_id,
                                student["student_id"],
                                pta_user_id,
                                unified.PTA_SOURCE_SYSTEM,
                                source_attempt_key(problem_set_id, submission),
                                submitted_at,
                                normalize_id(submission.get("status")) or None,
                                score_value(submission),
                                normalize_id(submission.get("compiler")) or None,
                                runtime_ms(submission),
                                memory_kb(submission),
                                raw_api_id,
                            ),
                        )
                        set_report["attempts"] += 1
                        report["attempts_upserted"] += 1

                unified._recalc_problem_state(cursor, offering_id)
                unified._recalc_student_assignment(cursor, offering_id, [], {})
                refresh_direct_assignment_summary(cursor, offering_id, sorted(touched_student_ids))
                unified._update_import_job(cursor, import_job_id, "SUCCEEDED", set_report, None)
                report["problem_sets"].append(set_report)

        conn.commit()
        report["unmapped_pta_user_ids"] = sorted(
            {
                user_id
                for item in report["problem_sets"]
                for user_id in item.get("unmapped_pta_user_ids", [])
            }
        )
        report["ok"] = True
        return report
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def refresh_direct_assignment_summary(cursor, offering_id, student_ids):
    if not student_ids:
        return
    placeholders = ", ".join(["%s"] * len(student_ids))
    params = [offering_id, offering_id, offering_id, *student_ids]
    cursor.execute(
        f"""
        UPDATE student_assignment sa
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
        SET
          sa.first_submit_at = spa.first_submit_at,
          sa.last_submit_at = spa.last_submit_at,
          sa.accepted_problem_count = COALESCE(sps.accepted_problem_count, 0),
          sa.submitted_problem_count = LEAST(
            sa.problem_count,
            COALESCE(spa.submitted_problem_count, 0)
          ),
          sa.submission_attempt_count = COALESCE(spa.submission_attempt_count, 0),
          sa.best_total_score = CASE
            WHEN sa.transcript_row_present = 0 THEN sps.best_total_score
            ELSE sa.best_total_score
          END,
          sa.latest_total_score = CASE
            WHEN sa.transcript_row_present = 0 THEN sps.best_total_score
            ELSE sa.latest_total_score
          END,
          sa.submission_status = CASE
            WHEN COALESCE(spa.submission_attempt_count, 0) = 0 THEN 'NOT_STARTED'
            WHEN sa.problem_count > 0
             AND COALESCE(sps.accepted_problem_count, 0) >= sa.problem_count THEN 'GRADED'
            WHEN COALESCE(spa.submitted_problem_count, 0) > 0 THEN 'IN_PROGRESS'
            ELSE sa.submission_status
          END,
          sa.completion_evidence = CASE
            WHEN sa.transcript_row_present = 1 THEN 'TRANSCRIPT_SCORE'
            WHEN sa.answer_sheet_count > 0 THEN 'ANSWER_SHEET'
            WHEN sa.scored_code_count > 0 THEN 'SCORED_CODE'
            WHEN COALESCE(spa.submission_attempt_count, 0) > 0 THEN 'SUBMISSION_ATTEMPT'
            ELSE 'NONE'
          END,
          sa.latest_sync_at = CURRENT_TIMESTAMP(3),
          sa.updated_at = CURRENT_TIMESTAMP(3)
        WHERE sa.offering_id = %s
          AND sa.student_id IN ({placeholders})
        """,
        params,
    )


def main():
    parser = argparse.ArgumentParser(description="Crawl PTA examinee submission pages")
    parser.add_argument("urls", nargs="+", help="PTA examinee submission page URLs")
    parser.add_argument("--output-dir", help="CSV output directory. Defaults to PTA_CRAWL_DIR.")
    parser.add_argument("--write-db", action="store_true", help="Write normalized records to MySQL")
    parser.add_argument("--no-csv", action="store_true", help="Skip CSV export")
    args = parser.parse_args()

    from pta_spider.spider import PTAClient

    client = PTAClient()
    if not client.ensure_login():
        raise RuntimeError("PTA login failed. Update cookies or credentials first.")

    loaded = load_targets(client, args.urls)
    if not args.no_csv:
        output_dir = args.output_dir or str(client.crawl_dir)
        export_loaded(loaded, output_dir)
    if args.write_db:
        report = write_loaded_to_db(loaded)
        print(json.dumps(report, ensure_ascii=False, indent=2, default=str))


if __name__ == "__main__":
    main()
