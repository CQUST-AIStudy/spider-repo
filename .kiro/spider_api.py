"""
PTA Spider API v2 - Data-type aware crawling with rate protection
Cooldowns: submissions=4h, exports=24h, content=once
"""
import asyncio, uuid, os, sys, time, csv
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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from spider import PTAClient, CrawlHistory
from sync_to_db import sync_all

app = FastAPI(title="PTA Spider API", version="2.0.0")
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8081")
COOLDOWN_SUBMISSIONS = int(os.getenv("COOLDOWN_SUBMISSIONS", str(4 * 3600)))
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
    STATE_FILE = Path(__file__).resolve().parent / "cooldown_state.json"

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
        with open(self.STATE_FILE, "w", encoding="utf-8") as f:
            json_mod.dump(self._state, f, ensure_ascii=False, indent=2)

    def check(self, keyword, data_type, cooldown_sec):
        k = f"{keyword}::{data_type}"
        last_ts = self._state.get(k)
        if last_ts is None:
            return True, 0, ""
        elapsed = time.time() - last_ts
        if elapsed >= cooldown_sec:
            return True, 0, datetime.fromtimestamp(last_ts).strftime("%m-%d %H:%M")
        remaining = int(cooldown_sec - elapsed)
        return False, remaining, datetime.fromtimestamp(last_ts).strftime("%m-%d %H:%M")

    def mark(self, keyword, data_type):
        self._state[f"{keyword}::{data_type}"] = time.time()
        self._save()

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
    keyword: str
    class_id: int | None = None
    mode: CrawlMode = CrawlMode.INCREMENTAL
    force: bool = False

class TaskInfo:
    def __init__(self, tid, keyword, class_id, mode, force=False):
        self.task_id = tid
        self.keyword = keyword
        self.class_id = class_id
        self.mode = mode
        self.force = force
        self.status = TaskStatus.QUEUED
        self.created_at = datetime.now().isoformat()
        self.started_at = None
        self.finished_at = None
        self.error = None
        self.new_sets_count = 0
        self.refreshed_count = 0
        self.submissions_count = 0
        self.skipped_cooldown = []

    def to_dict(self):
        return {
            "task_id": self.task_id, "keyword": self.keyword,
            "class_id": self.class_id, "mode": self.mode.value,
            "force": self.force, "status": self.status.value,
            "created_at": self.created_at, "started_at": self.started_at,
            "finished_at": self.finished_at, "error": self.error,
            "new_sets_count": self.new_sets_count,
            "refreshed_count": self.refreshed_count,
            "submissions_count": self.submissions_count,
            "skipped_cooldown": self.skipped_cooldown,
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

def _keyword_in_queue(keyword, mode):
    for t in _task_store.values():
        if t.keyword == keyword and t.mode == mode and t.status in (TaskStatus.QUEUED, TaskStatus.RUNNING):
            return t
    return None

def _notify_java(class_id, status):
    if class_id is None:
        return
    try:
        with httpx.Client(timeout=10) as c:
            c.put(f"{JAVA_BACKEND_URL}/api/classes/{class_id}/pta-sync/callback",
                  json={"status": status})
    except Exception as e:
        print(f"  callback failed: {e}")

def _cleanup_old_tasks():
    now = datetime.now()
    to_rm = [tid for tid, t in _task_store.items()
             if t.status in (TaskStatus.SUCCESS, TaskStatus.FAILED) and t.finished_at
             and (now - datetime.fromisoformat(t.finished_at)).total_seconds() > 3600]
    for tid in to_rm:
        del _task_store[tid]

def _run_crawl(task):
    try:
        task.status = TaskStatus.RUNNING
        task.started_at = datetime.now().isoformat()
        client = PTAClient()
        if not client.ensure_login():
            raise RuntimeError("PTA login failed, cookie may be expired")
        kw = task.keyword
        mode = task.mode
        all_sets = None

        # incremental / full: detect new problem sets
        if mode in (CrawlMode.INCREMENTAL, CrawlMode.FULL):
            all_sets = client.search_problem_sets(kw)
            if all_sets:
                new_sets = client.history.get_new_sets(all_sets)
                task.new_sets_count = len(new_sets)
                for ps in new_sets:
                    try:
                        client._crawl_one_problem_set(ps["id"], ps.get("name", ""))
                        client.history.mark_crawled(ps["id"], ps.get("name", ""))
                    except Exception as e:
                        print(f"crawl {ps.get('name','')} failed: {e}")

        # submissions / full: pull submission records
        if mode in (CrawlMode.SUBMISSIONS, CrawlMode.FULL):
            ok, rem, _ = _cooldown.check(kw, "submissions", COOLDOWN_SUBMISSIONS)
            if ok or task.force:
                if all_sets is None:
                    all_sets = client.search_problem_sets(kw)
                crawled = client.history.get_all_crawled()
                total_subs = 0
                for ps in (all_sets or []):
                    if ps["id"] in crawled:
                        try:
                            subs = client.get_all_submissions(ps["id"])
                            if subs:
                                base_dir = client._problem_set_dir(ps.get("name", ""))
                                with open(base_dir / "\u63d0\u4ea4\u8bb0\u5f55.csv", "w", encoding="utf-8", newline="") as f:
                                    w = csv.writer(f)
                                    w.writerow(["\u7528\u6237ID","\u9898\u76eeID","\u72b6\u6001","\u5206\u6570","\u7f16\u8bd1\u5668","\u7528\u65f6","\u5185\u5b58","\u63d0\u4ea4\u65f6\u95f4"])
                                    for s in subs:
                                        w.writerow([s.get("userId",""),s.get("problemSetProblemId",""),
                                                     s.get("status",""),s.get("score",""),s.get("compiler",""),
                                                     s.get("time",""),s.get("memory",""),s.get("submitAt","")])
                                total_subs += len(subs)
                            time.sleep(1)
                        except Exception as e:
                            print(f"pull submissions failed {ps.get('name','')}: {e}")
                task.submissions_count = total_subs
                _cooldown.mark(kw, "submissions")
            else:
                h, m = divmod(rem // 60, 60)
                task.skipped_cooldown.append(f"submissions(cooldown {h}h{m}m)")

        # refresh / full: re-export
        if mode in (CrawlMode.REFRESH, CrawlMode.FULL):
            ok, rem, _ = _cooldown.check(kw, "exports", COOLDOWN_EXPORTS)
            if ok or task.force:
                task.refreshed_count = client.refresh_exports(kw)
                _cooldown.mark(kw, "exports")
            else:
                h, m = divmod(rem // 60, 60)
                task.skipped_cooldown.append(f"exports(cooldown {h}h{m}m)")

        # sync to database
        try:
            print("syncing to database...")
            sync_all()
        except Exception as e:
            print(f"db sync failed: {e}")

        task.status = TaskStatus.SUCCESS
        task.finished_at = datetime.now().isoformat()
        _notify_java(task.class_id, "SUCCESS")
    except Exception as e:
        task.status = TaskStatus.FAILED
        task.error = str(e)
        task.finished_at = datetime.now().isoformat()
        _notify_java(task.class_id, "FAILED")
        print(f"task {task.task_id} failed: {e}")

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

@app.post("/crawl")
async def crawl(req: CrawlRequest):
    keyword = req.keyword.strip()
    if not keyword:
        raise HTTPException(400, "keyword required")
    existing = _keyword_in_queue(keyword, req.mode)
    if existing:
        return {"task_id": existing.task_id, "status": existing.status.value, "message": "same task already queued"}
    if not req.force:
        if req.mode == CrawlMode.SUBMISSIONS:
            ok, rem, last = _cooldown.check(keyword, "submissions", COOLDOWN_SUBMISSIONS)
            if not ok:
                h, m = divmod(rem // 60, 60)
                return {"blocked": True, "reason": "submissions_cooldown", "remaining_sec": rem,
                        "last_time": last, "message": f"submissions cooldown, last: {last}, remaining {h}h{m}m"}
        elif req.mode == CrawlMode.REFRESH:
            ok, rem, last = _cooldown.check(keyword, "exports", COOLDOWN_EXPORTS)
            if not ok:
                h, m = divmod(rem // 60, 60)
                return {"blocked": True, "reason": "exports_cooldown", "remaining_sec": rem,
                        "last_time": last, "message": f"exports cooldown, last: {last}, remaining {h}h{m}m"}
    q = _get_queue()
    if q.qsize() >= MAX_QUEUE_SIZE:
        raise HTTPException(429, "queue full")
    tid = uuid.uuid4().hex[:12]
    task = TaskInfo(tid, keyword, req.class_id, req.mode, req.force)
    _task_store[tid] = task
    await q.put(tid)
    mode_cn = {"incremental": "incremental", "submissions": "submissions", "refresh": "refresh", "full": "full"}
    force_hint = " (force)" if req.force else ""
    return {"task_id": tid, "status": task.status.value,
            "message": f"queued: {mode_cn.get(req.mode.value, req.mode.value)}{force_hint}"}

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
    cookie_path = Path(__file__).resolve().parent / "manual_cookies.json"
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


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("SPIDER_PORT", "8100"))
    uvicorn.run("spider_api:app", host="0.0.0.0", port=port, reload=False)
