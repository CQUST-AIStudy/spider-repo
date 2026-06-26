"""
PTA Spider API v2 - Data-type aware crawling with per-problem-set refresh policy.
Open problem sets refresh daily; ended sets are skipped automatically unless forced or missing data.
"""
import asyncio, uuid, os, sys, time, csv, shutil
import json as json_mod
from pathlib import Path
from datetime import datetime
from enum import Enum

if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import re

from .spider import PTAClient, CrawlHistory, RUNTIME_DIR
from . import sync_to_db as legacy_sync
from .sync_to_unified_db import run_configured_sync

app = FastAPI(title="PTA Spider API", version="2.0.0")
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://127.0.0.1:8081")
COOLDOWN_SUBMISSIONS = int(os.getenv("COOLDOWN_SUBMISSIONS", str(24 * 3600)))
COOLDOWN_EXPORTS = int(os.getenv("COOLDOWN_EXPORTS", str(24 * 3600)))
_cors_origins_raw = os.getenv("SPIDER_CORS_ALLOW_ORIGINS", "*").strip()
if _cors_origins_raw == "*":
    _cors_origins = ["*"]
else:
    _cors_origins = [x.strip() for x in _cors_origins_raw.split(",") if x.strip()]

# Frontend DataSyncPanel calls this service directly from browser.
# Enable CORS so preflight OPTIONS requests for POST /crawl succeed.
app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class CooldownManager:
    STATE_FILE = RUNTIME_DIR / "cooldown_state.json"

    def __init__(self):
        self._state = self._load()

    def _load(self):
        if self.STATE_FILE.exists():
            try:
                with open(self.STATE_FILE, "r", encoding="utf-8") as f:
                    return json_mod.load(f)
            except Exception:
                pass
        return {}

    def _save(self):
        self.STATE_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(self.STATE_FILE, "w", encoding="utf-8") as f:
            json_mod.dump(self._state, f, ensure_ascii=False, indent=2)

    def check(self, keyword, data_type, cooldown_sec):
        return self.check_key(self._key(keyword, data_type), cooldown_sec)

    def check_key(self, key, cooldown_sec):
        last_ts = self._state.get(key)
        if last_ts is None:
            return True, 0, ""
        elapsed = time.time() - last_ts
        if elapsed >= cooldown_sec:
            return True, 0, datetime.fromtimestamp(last_ts).strftime("%m-%d %H:%M")
        remaining = int(cooldown_sec - elapsed)
        return False, remaining, datetime.fromtimestamp(last_ts).strftime("%m-%d %H:%M")

    def mark(self, keyword, data_type):
        self.mark_key(self._key(keyword, data_type))

    def mark_key(self, key):
        self._state[key] = time.time()
        self._save()

    @staticmethod
    def _key(keyword, data_type):
        return f"{keyword}::{data_type}"

    def get_status(self, keyword):
        result = {}
        for dt, cd in [("submissions", COOLDOWN_SUBMISSIONS), ("exports", COOLDOWN_EXPORTS)]:
            ok, rem, last = self.check(keyword, dt, cd)
            h, m = divmod(rem // 60, 60)
            result[dt] = {
                "allowed": ok, "remaining_sec": rem,
                "remaining_human": f"{h}h{m}m" if rem > 0 else "",
                "last_time": last, "cooldown_hours": cd / 3600,
            }
        return result


_cooldown = CooldownManager()
DEADLINE_KEYS = (
    "endAt", "deadlineAt", "deadline",
    "end_at", "deadline_at",
)


def _first_non_empty(data, keys):
    for key in keys:
        value = data.get(key) if isinstance(data, dict) else None
        if value not in (None, ""):
            return value
    return None


def _parse_pta_time(value):
    if value in (None, ""):
        return None
    if isinstance(value, (int, float)):
        timestamp = value / 1000 if value > 10_000_000_000 else value
        try:
            return datetime.fromtimestamp(timestamp)
        except (OSError, ValueError):
            return None

    text = str(value).strip()
    if not text:
        return None
    if text.isdigit():
        return _parse_pta_time(int(text))

    normalized = text.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone().replace(tzinfo=None)
        return parsed
    except ValueError:
        pass

    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y/%m/%d %H:%M:%S", "%Y/%m/%d %H:%M"):
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue
    return None


def _problem_set_time(problem_set, keys):
    return _parse_pta_time(_first_non_empty(problem_set, keys))


def _problem_set_name(problem_set):
    return str(problem_set.get("name") or problem_set.get("id") or "unknown").strip()


def _normalize_text(value):
    if value is None:
        return ""
    return re.sub(r"\s+", "", str(value)).strip().lower()


def _filter_problem_sets(problem_sets, problem_set_id=None, problem_set_name=None):
    expected_id = str(problem_set_id or "").strip()
    expected_name = _normalize_text(problem_set_name)
    if not expected_id and not expected_name:
        return problem_sets

    filtered = []
    for problem_set in problem_sets or []:
        ps_id = str(problem_set.get("id") or "").strip()
        ps_name = _normalize_text(problem_set.get("name"))
        if expected_id and ps_id == expected_id:
            filtered.append(problem_set)
        elif expected_name and ps_name == expected_name:
            filtered.append(problem_set)
    return filtered


def _offering_source_keys(experiment_id, class_id=None):
    base = f"LEGACY_EXPERIMENT_OFFERING:{experiment_id}"
    if class_id is None:
        return [base]
    return [f"{base}:CLASS:{class_id}", base]


def _is_problem_set_closed(problem_set, now=None):
    deadline = _problem_set_time(problem_set, DEADLINE_KEYS)
    return deadline is not None and deadline <= (now or datetime.now())


def _database_has_experiment_data(problem_set, class_id=None):
    name = _problem_set_name(problem_set)
    if not name or name == "unknown":
        return False

    has_data = False
    conn = None
    try:
        conn = legacy_sync.get_db()
        with conn.cursor() as cursor:
            cursor.execute("SELECT experiment_id FROM experiment WHERE name = %s LIMIT 1", (name,))
            row = cursor.fetchone()
            if not row:
                return False
            experiment_id = row[0]

            if class_id is None:
                for table in ("submit_situation", "score", "student_code", "problem_score_detail"):
                    try:
                        cursor.execute(f"SELECT COUNT(*) FROM {table} WHERE experiment_id = %s", (experiment_id,))
                        count_row = cursor.fetchone()
                        if count_row and int(count_row[0] or 0) > 0:
                            has_data = True
                            break
                    except Exception:
                        continue

            if not has_data:
                try:
                    source_keys = _offering_source_keys(experiment_id, class_id)
                    placeholders = ", ".join(["%s"] * len(source_keys))
                    params = ["LEGACY_TAP", *source_keys]
                    class_filter = ""
                    if class_id is not None:
                        class_filter = " AND class_id = %s"
                        params.append(class_id)
                    cursor.execute(
                        f"""
                        SELECT id
                        FROM assignment_offering
                        WHERE source_system = %s
                          AND source_offering_key IN ({placeholders})
                          {class_filter}
                        LIMIT 1
                        """,
                        tuple(params),
                    )
                    offering_row = cursor.fetchone()
                    if offering_row:
                        cursor.execute(
                            "SELECT COUNT(*) FROM student_problem_attempt WHERE offering_id = %s",
                            (offering_row[0],),
                        )
                        attempt_row = cursor.fetchone()
                        has_data = bool(attempt_row and int(attempt_row[0] or 0) > 0)
                except Exception:
                    pass

            if not has_data and class_id is not None:
                try:
                    cursor.execute(
                        """
                        SELECT COUNT(*)
                        FROM import_job
                        WHERE source_system = 'PTA'
                          AND class_id = %s
                          AND status = 'SUCCEEDED'
                          AND JSON_UNQUOTE(JSON_EXTRACT(summary_json, '$.experiment')) = %s
                        """,
                        (class_id, name),
                    )
                    import_row = cursor.fetchone()
                    has_data = bool(import_row and int(import_row[0] or 0) > 0)
                except Exception:
                    pass
    except Exception as exc:
        print(f"database existence check failed for {name}: {exc}")
        has_data = False
    finally:
        if conn is not None:
            try:
                conn.close()
            except Exception:
                pass

    return has_data


def _problem_set_cooldown(problem_set, data_type, now=None):
    return COOLDOWN_EXPORTS if data_type == "exports" else COOLDOWN_SUBMISSIONS


def _problem_set_cooldown_key(problem_set, data_type):
    ps_id = str(problem_set.get("id") or "").strip()
    ps_name = str(problem_set.get("name") or "").strip()
    identity = ps_id or f"name:{ps_name}"
    return f"problem-set:{identity}::{data_type}"


def _format_duration(seconds):
    seconds = max(0, int(seconds))
    days, rem = divmod(seconds, 24 * 3600)
    hours, rem = divmod(rem, 3600)
    minutes = rem // 60
    if days:
        return f"{days}d{hours}h"
    if hours:
        return f"{hours}h{minutes}m"
    return f"{minutes}m"


def _should_refresh_problem_set(problem_set, data_type, force=False, now=None, class_id=None):
    now = now or datetime.now()
    ps_name = _problem_set_name(problem_set)
    if force:
        return True, 0, f"{ps_name}: force"

    has_database_data = None
    if class_id is not None:
        has_database_data = _database_has_experiment_data(problem_set, class_id)
        if not has_database_data:
            return True, 0, f"{ps_name}: database data is missing"

    deadline = _problem_set_time(problem_set, DEADLINE_KEYS)
    if deadline is not None and deadline <= now:
        if has_database_data is None and not _database_has_experiment_data(problem_set, class_id):
            return True, 0, f"{ps_name}: deadline passed but database data is missing"
        return False, 0, f"{ps_name}: deadline passed at {deadline.strftime('%Y-%m-%d %H:%M')}"

    cooldown = _problem_set_cooldown(problem_set, data_type, now)
    ok, remaining, last = _cooldown.check_key(
        _problem_set_cooldown_key(problem_set, data_type),
        cooldown,
    )
    if ok:
        return True, cooldown, f"{ps_name}: allowed every {_format_duration(cooldown)}"
    return False, remaining, (
        f"{ps_name}: {data_type} cooldown, last {last}, "
        f"remaining {_format_duration(remaining)}"
    )


def _mark_problem_set_refreshed(problem_set, data_type):
    _cooldown.mark_key(_problem_set_cooldown_key(problem_set, data_type))

class TaskStatus(str, Enum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"

class CrawlMode(str, Enum):
    INCREMENTAL = "incremental"
    SUBMISSIONS = "submissions"
    REFRESH = "refresh"
    FULL = "full"

class CrawlRequest(BaseModel):
    class_id: int | None = None
    classId: int | None = None
    problem_set_id: str | None = None
    problemSetId: str | None = None
    problem_set_name: str | None = None
    problemSetName: str | None = None
    group_id: str | None = None
    groupId: str | None = None
    ptaGroupId: str | None = None
    group_name: str | None = None
    groupName: str | None = None
    ptaGroupName: str | None = None
    keyword: str | None = None
    ptaKeyword: str | None = None
    mode: str | None = CrawlMode.INCREMENTAL.value
    force: bool = False
    credential_source: str | None = None
    credentialSource: str | None = None
    username: str | None = None
    password: str | None = None
    force_selenium_login: bool = False
    forceSeleniumLogin: bool | None = None
    headless: bool | None = None

class TaskInfo:
    def __init__(
        self,
        tid,
        keyword,
        class_id,
        problem_set_id,
        problem_set_name,
        group_id,
        group_name,
        mode,
        force=False,
        credential_source=None,
        username=None,
        password=None,
        force_selenium_login=False,
        headless=None,
    ):
        self.task_id = tid
        self.keyword = keyword
        self.class_id = class_id
        self.problem_set_id = problem_set_id
        self.problem_set_name = problem_set_name
        self.group_id = group_id
        self.group_name = group_name
        self.mode = mode
        self.force = force
        self.credential_source = credential_source or ("temporary" if username and password else "cookie")
        self.username = username
        self.password = password
        self.force_selenium_login = force_selenium_login
        self.headless = headless
        self.status = TaskStatus.QUEUED
        self.created_at = datetime.now().isoformat()
        self.started_at = None
        self.finished_at = None
        self.error = None
        self.new_sets_count = 0
        self.refreshed_count = 0
        self.submissions_count = 0
        self.skipped_cooldown = []
        self.crawl_dir = None

    def to_dict(self):
        return {
            "task_id": self.task_id,
            "class_id": self.class_id,
            "problem_set_id": self.problem_set_id,
            "problem_set_name": self.problem_set_name,
            "group_id": self.group_id,
            "group_name": self.group_name,
            "mode": self.mode.value,
            "force": self.force, "status": self.status.value,
            "credential_source": self.credential_source,
            "force_selenium_login": self.force_selenium_login,
            "headless": self.headless,
            "created_at": self.created_at, "started_at": self.started_at,
            "finished_at": self.finished_at, "error": self.error,
            "new_sets_count": self.new_sets_count,
            "refreshed_count": self.refreshed_count,
            "submissions_count": self.submissions_count,
            "skipped_cooldown": self.skipped_cooldown,
            "crawl_dir": self.crawl_dir,
        }

MAX_QUEUE_SIZE = 5
_task_store = {}
_queue = None
_worker_started = False

def _get_queue():
    global _queue
    if _queue is None:
        _queue = asyncio.Queue(maxsize=MAX_QUEUE_SIZE)
    return _queue

def _keyword_in_queue(keyword, mode, class_id=None, group_id=None, problem_set_id=None, problem_set_name=None):
    for t in _task_store.values():
        if (
            t.keyword == keyword
            and t.mode == mode
            and t.class_id == class_id
            and t.group_id == group_id
            and t.problem_set_id == problem_set_id
            and t.problem_set_name == problem_set_name
            and t.status in (TaskStatus.QUEUED, TaskStatus.RUNNING)
        ):
            return t
    return None

def _notify_java(class_id, status, task_id=None):
    if class_id is None:
        return
    try:
        payload = {"status": status}
        if task_id:
            payload["taskId"] = task_id
        with httpx.Client(timeout=10) as c:
            resp = c.put(
                f"{JAVA_BACKEND_URL}/api/classes/{class_id}/pta-sync/callback",
                json=payload,
            )
            resp.raise_for_status()
            print(f"callback ok: class_id={class_id}, status={status}, code={resp.status_code}")
    except Exception as e:
        print(f"  callback failed: {e}")


def _write_submissions_csv(client, problem_set, submissions):
    base_dir = client._problem_set_dir(problem_set.get("name", ""))
    with open(base_dir / "提交记录.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["用户ID", "题目ID", "状态", "分数", "编译器", "用时", "内存", "提交时间"])
        for s in submissions:
            w.writerow([
                s.get("userId", ""), s.get("problemSetProblemId", ""),
                s.get("status", ""), s.get("score", ""), s.get("compiler", ""),
                s.get("time", ""), s.get("memory", ""), s.get("submitAt", "")
            ])


def _safe_path_fragment(value):
    text = str(value or "").strip()
    text = re.sub(r"[^\w.-]+", "_", text, flags=re.UNICODE).strip("._")
    return text[:80] or "none"


def _task_crawl_dir(base_dir: Path, task: TaskInfo) -> Path:
    class_part = f"class-{task.class_id}" if task.class_id is not None else "class-none"
    label = task.problem_set_id or task.problem_set_name or task.group_id or task.group_name or task.keyword
    task_part = _safe_path_fragment(f"{class_part}-{label}-{task.task_id}")
    return (Path(base_dir).resolve() / "_task_runs" / task_part).resolve()


def _cleanup_task_crawl_dir(task_dir: Path, base_dir: Path):
    task_dir = Path(task_dir).resolve()
    task_runs_dir = (Path(base_dir).resolve() / "_task_runs").resolve()
    try:
        task_dir.relative_to(task_runs_dir)
    except ValueError as exc:
        raise RuntimeError(f"refusing to cleanup outside task runs directory: {task_dir}") from exc
    if task_dir.exists():
        shutil.rmtree(task_dir)

def _cleanup_old_tasks():
    now = datetime.now()
    to_rm = [tid for tid, t in _task_store.items()
             if t.status in (TaskStatus.SUCCESS, TaskStatus.FAILED) and t.finished_at
             and (now - datetime.fromisoformat(t.finished_at)).total_seconds() > 3600]
    for tid in to_rm:
        del _task_store[tid]


def _resolve_problem_sets(client, task):
    problem_sets = client.search_problem_sets(
        group_id=task.group_id,
        group_name=task.group_name,
    )
    filtered = _filter_problem_sets(
        problem_sets,
        problem_set_id=task.problem_set_id,
        problem_set_name=task.problem_set_name,
    )
    if (task.problem_set_id or task.problem_set_name) and not filtered:
        target = task.problem_set_id or task.problem_set_name
        raise RuntimeError(f"bound PTA problem set is not authorized for this user group: {target}")
    return filtered

async def _worker():
    q = _get_queue()
    while True:
        tid = await q.get()
        task = _task_store.get(tid)
        if task:
            loop = asyncio.get_event_loop()
            await loop.run_in_executor(None, _run_crawl, task)
        q.task_done()
        _cleanup_old_tasks()


def _run_crawl(task):
    client = None
    base_crawl_dir = None
    task_crawl_dir = None
    try:
        task.status = TaskStatus.RUNNING
        task.started_at = datetime.now().isoformat()
        client = PTAClient(task.username, task.password, allow_env_fallback=False)
        base_crawl_dir = Path(client.crawl_dir).resolve()
        task_crawl_dir = _task_crawl_dir(base_crawl_dir, task)
        task_crawl_dir.mkdir(parents=True, exist_ok=True)
        client.crawl_dir = task_crawl_dir
        task.crawl_dir = str(task_crawl_dir)
        client.force_selenium_login = task.force_selenium_login
        if task.headless is not None:
            client.headless = task.headless
        if not client.ensure_login():
            raise RuntimeError("PTA login failed, cookie may be expired")

        roster_payload = client.write_user_group_roster(
            group_id=task.group_id,
            group_name=task.group_name,
            crawl_dir=task_crawl_dir,
        )
        task.group_id = roster_payload["group"]["pta_group_id"]
        task.group_name = roster_payload["group"]["pta_group_name"]

        mode = task.mode
        all_sets = None
        content_crawled_ids = set()
        crawl_errors = []
        needs_group_answer_export = False
        completed_content_sets = []

        if mode in (CrawlMode.INCREMENTAL, CrawlMode.FULL):
            all_sets = _resolve_problem_sets(client, task)
            if all_sets:
                new_sets = client.get_sets_requiring_content(all_sets)
                task.new_sets_count = len(new_sets)
                for ps in new_sets:
                    try:
                        if (
                            not task.force
                            and _is_problem_set_closed(ps)
                            and _database_has_experiment_data(ps, task.class_id)
                        ):
                            reason = (
                                f"{_problem_set_name(ps)}: deadline passed and database data exists; "
                                "skip content crawl"
                            )
                            task.skipped_cooldown.append(reason)
                            client.history.mark_crawled(ps["id"], ps.get("name", ""))
                            continue
                        client._write_problem_set_info(ps["id"], ps.get("name", ""), ps)
                        client._crawl_one_problem_set(ps["id"], ps.get("name", ""), export_answer_sheet=False)
                        completed_content_sets.append(ps)
                        content_crawled_ids.add(ps.get("id", ""))
                        needs_group_answer_export = True
                        _mark_problem_set_refreshed(ps, "submissions")
                        _mark_problem_set_refreshed(ps, "exports")
                    except Exception as e:
                        print(f"crawl {ps.get('name', '')} failed: {e}")
                        crawl_errors.append(f"{ps.get('name', '')}: {e}")

        if mode in (CrawlMode.SUBMISSIONS, CrawlMode.FULL):
            if all_sets is None:
                all_sets = _resolve_problem_sets(client, task)
            crawled = client.history.get_all_crawled()
            total_subs = 0
            for ps in (all_sets or []):
                ps_id = ps.get("id", "")
                if ps_id in content_crawled_ids:
                    continue
                if ps_id not in crawled:
                    continue
                ok, _, reason = _should_refresh_problem_set(
                    ps, "submissions", task.force, class_id=task.class_id
                )
                if not ok:
                    task.skipped_cooldown.append(reason)
                    continue
                try:
                    client._write_problem_set_info(ps["id"], ps.get("name", ""), ps)
                    subs = client.get_all_submissions(ps["id"])
                    if subs:
                        _write_submissions_csv(client, ps, subs)
                        total_subs += len(subs)
                    _mark_problem_set_refreshed(ps, "submissions")
                    time.sleep(1)
                except Exception as e:
                    print(f"pull submissions failed {ps.get('name', '')}: {e}")
                    crawl_errors.append(f"{ps.get('name', '')} submissions: {e}")
            task.submissions_count = total_subs

        if mode in (CrawlMode.REFRESH, CrawlMode.FULL):
            if all_sets is None:
                all_sets = _resolve_problem_sets(client, task)
            refreshed = 0
            for ps in (all_sets or []):
                ps_id = ps.get("id", "")
                ps_name = ps.get("name", "")
                if ps_id in content_crawled_ids:
                    continue
                if not client.history.is_crawled(ps_id):
                    continue
                ok, _, reason = _should_refresh_problem_set(
                    ps, "exports", task.force, class_id=task.class_id
                )
                if not ok:
                    task.skipped_cooldown.append(reason)
                    continue
                try:
                    client._write_problem_set_info(ps_id, ps_name, ps)
                    client._refresh_one_problem_set(ps_id, ps_name, export_answer_sheet=False)
                    client.history.mark_export_refreshed(ps_id)
                    _mark_problem_set_refreshed(ps, "exports")
                    needs_group_answer_export = True
                    refreshed += 1
                except Exception as e:
                    print(f"refresh exports failed {ps_name}: {e}")
                    crawl_errors.append(f"{ps_name} exports: {e}")
            task.refreshed_count = refreshed

        if needs_group_answer_export and not crawl_errors:
            try:
                client.export_group_answer_sheets(
                    group_id=task.group_id,
                    group_name=task.group_name,
                    crawl_dir=task_crawl_dir,
                )
                for ps in completed_content_sets:
                    client.history.mark_crawled(ps.get("id", ""), ps.get("name", ""))
            except Exception as e:
                print(f"group answer export failed: {e}")
                crawl_errors.append(f"group answer export: {e}")

        if crawl_errors:
            raise RuntimeError("; ".join(crawl_errors[:5]))

        if not task_crawl_dir or not any(p.is_dir() for p in task_crawl_dir.iterdir()):
            raise RuntimeError("no PTA data was downloaded for this task")

        print("syncing to database...")
        report = run_configured_sync(crawl_dir=task_crawl_dir, strict=True, class_id=task.class_id)
        if not report.get("ok"):
            raise RuntimeError(report.get("error") or "database sync failed")

        task.status = TaskStatus.SUCCESS
        task.finished_at = datetime.now().isoformat()
        _cleanup_task_crawl_dir(task_crawl_dir, base_crawl_dir)
        _notify_java(task.class_id, "SUCCESS", task.task_id)
    except Exception as e:
        task.status = TaskStatus.FAILED
        task.error = str(e)
        task.finished_at = datetime.now().isoformat()
        _notify_java(task.class_id, "FAILED", task.task_id)
        print(f"task {task.task_id} failed: {e}")


@app.on_event("startup")
async def startup():
    global _worker_started
    if not _worker_started:
        asyncio.create_task(_worker())
        _worker_started = True

@app.get("/health")
async def health():
    pending = sum(1 for t in _task_store.values() if t.status in (TaskStatus.QUEUED, TaskStatus.RUNNING))
    return {"status": "ok", "pending_tasks": pending}


def _first_text(*values):
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def _parse_mode(value):
    raw = _first_text(value) or CrawlMode.INCREMENTAL.value
    normalized = raw.lower()
    for mode in CrawlMode:
        if normalized == mode.value:
            return mode
    allowed = ", ".join(mode.value for mode in CrawlMode)
    raise HTTPException(400, f"invalid mode: {raw}; allowed: {allowed}")


@app.post("/crawl")
async def crawl(req: CrawlRequest):
    class_id = req.class_id if req.class_id is not None else req.classId
    group_id = _first_text(req.group_id, req.groupId, req.ptaGroupId)
    group_name = _first_text(req.group_name, req.groupName, req.ptaGroupName, req.keyword, req.ptaKeyword)
    problem_set_id = _first_text(req.problem_set_id, req.problemSetId)
    problem_set_name = _first_text(req.problem_set_name, req.problemSetName)
    credential_source = _first_text(req.credential_source, req.credentialSource)
    force_selenium_login = req.force_selenium_login
    if req.forceSeleniumLogin is not None:
        force_selenium_login = req.forceSeleniumLogin
    mode = _parse_mode(req.mode)
    keyword = (group_name or group_id or "").strip()
    if not keyword:
        raise HTTPException(400, "group_id or group_name required")
    if bool(req.username and req.username.strip()) != bool(req.password):
        raise HTTPException(400, "username and password must be provided together")
    existing = _keyword_in_queue(
        keyword,
        mode,
        class_id,
        group_id,
        problem_set_id,
        problem_set_name,
    )
    if existing and not req.force:
        return {"task_id": existing.task_id, "status": existing.status.value, "message": "same task already queued"}
    q = _get_queue()
    if q.qsize() >= MAX_QUEUE_SIZE:
        raise HTTPException(429, "queue full")
    tid = uuid.uuid4().hex[:12]
    username = req.username.strip() if req.username else None
    password = req.password if req.password else None
    task = TaskInfo(
        tid,
        keyword,
        class_id,
        problem_set_id,
        problem_set_name,
        group_id,
        group_name,
        mode,
        req.force,
        credential_source,
        username,
        password,
        force_selenium_login,
        req.headless,
    )
    _task_store[tid] = task
    await q.put(tid)
    mode_cn = {"incremental": "incremental", "submissions": "submissions", "refresh": "refresh", "full": "full"}
    force_hint = " (force)" if req.force else ""
    return {"task_id": tid, "status": task.status.value,
            "credential_source": task.credential_source,
            "message": f"queued: {mode_cn.get(mode.value, mode.value)}{force_hint}"}

@app.get("/status/{task_id}")
async def status(task_id: str):
    task = _task_store.get(task_id)
    if not task:
        raise HTTPException(404, "task not found")
    return task.to_dict()

@app.get("/tasks")
async def list_tasks():
    tasks = sorted(_task_store.values(), key=lambda t: t.created_at, reverse=True)
    return [t.to_dict() for t in tasks[:20]]

@app.get("/cooldown/{keyword}")
async def get_cooldown(keyword: str):
    return _cooldown.get_status(keyword)

_cookie_status = {"status": "UNKNOWN", "error": "", "updated_at": None}

class CookieStatusRequest(BaseModel):
    status: str
    error: str = ""

class ManualCookieRequest(BaseModel):
    cookies: str

@app.put("/cookie/status")
async def update_cookie_status(req: CookieStatusRequest):
    _cookie_status["status"] = req.status
    _cookie_status["error"] = req.error
    _cookie_status["updated_at"] = datetime.now().isoformat()
    return {"ok": True}

@app.get("/cookie/status")
async def get_cookie_status():
    return _cookie_status

@app.post("/cookie/update")
async def manual_update_cookie(req: ManualCookieRequest):
    try:
        cookies = json_mod.loads(req.cookies)
        if not isinstance(cookies, list) or len(cookies) == 0:
            raise HTTPException(400, "Cookie format error, need JSON array")
    except json_mod.JSONDecodeError:
        raise HTTPException(400, "Cookie is not valid JSON")
    cookie_path = RUNTIME_DIR / "manual_cookies.json"
    cookie_path.parent.mkdir(parents=True, exist_ok=True)
    with open(cookie_path, "w", encoding="utf-8") as f:
        json_mod.dump(cookies, f, ensure_ascii=False, indent=2)
    client = PTAClient()
    for c in cookies:
        name = c.get("name", c.get("Name", ""))
        value = c.get("value", c.get("Value", ""))
        domain = c.get("domain", c.get("Domain", ".pintia.cn"))
        if name and value:
            client.session.cookies.set(name, value, domain=domain)
    if client._check_cookie_valid():
        client._save_cookies(cookies)
        _cookie_status.update({"status": "OK", "error": "", "updated_at": datetime.now().isoformat()})
        return {"valid": True, "message": "Cookie valid, saved. Sync restored."}
    else:
        return {"valid": False, "message": "Cookie invalid or expired."}


def main():
    import uvicorn
    port = int(os.getenv("SPIDER_PORT", "8100"))
    uvicorn.run("pta_spider.spider_api:app", host="0.0.0.0", port=port, reload=False)


if __name__ == "__main__":
    main()
