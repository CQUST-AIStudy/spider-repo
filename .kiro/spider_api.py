"""
PTA 爬虫 FastAPI 服务
- 全局任务队列，同一时间只允许 1 个爬取任务运行
- 队列最大容量 5，相同 keyword 去重
- Java 后端通过 HTTP 调用触发爬取
- 任务完成后回调 Java 后端更新同步状态
"""
import asyncio
import threading
import uuid
import time
import os
import sys
from pathlib import Path
from datetime import datetime
from enum import Enum

# Windows 终端 UTF-8 输出修复
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Ensure spider.py in same directory is importable
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from spider import PTAClient, CrawlHistory
from sync_to_db import sync_all

app = FastAPI(title="PTA Spider API", version="1.0.0")

# Java 后端地址，用于回调更新同步状态
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8081")

# ==================== 任务模型 ====================

class TaskStatus(str, Enum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"

class CrawlMode(str, Enum):
    INCREMENTAL = "incremental"  # 只爬新题目集
    REFRESH = "refresh"          # 刷新已有题目集的导出数据
    FULL = "full"                # 增量 + 刷新

class CrawlRequest(BaseModel):
    keyword: str
    class_id: int | None = None
    mode: CrawlMode = CrawlMode.INCREMENTAL

class TaskInfo:
    def __init__(self, task_id: str, keyword: str, class_id: int | None, mode: CrawlMode = CrawlMode.INCREMENTAL):
        self.task_id = task_id
        self.keyword = keyword
        self.class_id = class_id
        self.mode = mode
        self.status = TaskStatus.QUEUED
        self.created_at = datetime.now().isoformat()
        self.started_at = None
        self.finished_at = None
        self.error = None
        self.new_sets_count = 0
        self.refreshed_count = 0

    def to_dict(self):
        return {
            "task_id": self.task_id,
            "keyword": self.keyword,
            "class_id": self.class_id,
            "mode": self.mode.value,
            "status": self.status.value,
            "created_at": self.created_at,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "error": self.error,
            "new_sets_count": self.new_sets_count,
            "refreshed_count": self.refreshed_count,
        }

# ==================== 全局状态 ====================

MAX_QUEUE_SIZE = 5
_task_store: dict[str, TaskInfo] = {}
_queue: asyncio.Queue | None = None
_worker_started = False


def _get_queue() -> asyncio.Queue:
    global _queue
    if _queue is None:
        _queue = asyncio.Queue(maxsize=MAX_QUEUE_SIZE)
    return _queue


def _keyword_in_queue(keyword: str) -> bool:
    """检查队列中是否已有相同 keyword 的任务（含正在运行的）"""
    for task in _task_store.values():
        if task.keyword == keyword and task.status in (TaskStatus.QUEUED, TaskStatus.RUNNING):
            return True
    return False


def _notify_java_backend(class_id: int | None, status: str):
    """回调 Java 后端，更新 teaching_class 的 sync_status"""
    if class_id is None:
        return
    try:
        with httpx.Client(timeout=10) as client:
            client.put(
                f"{JAVA_BACKEND_URL}/api/classes/{class_id}/pta-sync/callback",
                json={"status": status},
            )
        print(f"  回调 Java 后端: class_id={class_id}, status={status}")
    except Exception as e:
        print(f"  回调 Java 后端失败: {e}")


def _cleanup_old_tasks():
    """清理已完成超过 1 小时的任务，防止内存泄漏"""
    now = datetime.now()
    to_remove = []
    for tid, task in _task_store.items():
        if task.status in (TaskStatus.SUCCESS, TaskStatus.FAILED) and task.finished_at:
            try:
                finished = datetime.fromisoformat(task.finished_at)
                if (now - finished).total_seconds() > 3600:
                    to_remove.append(tid)
            except Exception:
                pass
    for tid in to_remove:
        del _task_store[tid]
    if to_remove:
        print(f"  清理了 {len(to_remove)} 个过期任务")


def _run_crawl(task: TaskInfo):
    """在线程中执行实际爬取（阻塞操作）"""
    try:
        task.status = TaskStatus.RUNNING
        task.started_at = datetime.now().isoformat()

        client = PTAClient()
        if not client.ensure_login():
            raise RuntimeError("PTA 登录失败")

        # 增量爬取新题目集
        if task.mode in (CrawlMode.INCREMENTAL, CrawlMode.FULL):
            all_sets = client.search_problem_sets(task.keyword)
            if all_sets:
                new_sets = client.history.get_new_sets(all_sets)
                task.new_sets_count = len(new_sets)
                for ps in new_sets:
                    ps_id = ps.get("id", "")
                    ps_name = ps.get("name", "未知")
                    try:
                        client._crawl_one_problem_set(ps_id, ps_name)
                        client.history.mark_crawled(ps_id, ps_name)
                    except Exception as e:
                        print(f"爬取 {ps_name} 失败: {e}")

        # 刷新已有题目集的导出数据
        if task.mode in (CrawlMode.REFRESH, CrawlMode.FULL):
            task.refreshed_count = client.refresh_exports(task.keyword)

        # 爬取/刷新完成后，自动同步数据到数据库
        try:
            print("开始同步爬取数据到数据库...")
            sync_all()
            print("数据库同步完成")
        except Exception as e:
            print(f"数据库同步失败（不影响爬取结果）: {e}")

        task.status = TaskStatus.SUCCESS
        task.finished_at = datetime.now().isoformat()
        _notify_java_backend(task.class_id, "SUCCESS")

    except Exception as e:
        task.status = TaskStatus.FAILED
        task.error = str(e)
        task.finished_at = datetime.now().isoformat()
        _notify_java_backend(task.class_id, "FAILED")
        print(f"任务 {task.task_id} 失败: {e}")


async def _worker():
    """单 worker 消费队列，保证同一时间只有一个爬取任务"""
    q = _get_queue()
    while True:
        task_id = await q.get()
        task = _task_store.get(task_id)
        if task is None:
            q.task_done()
            continue

        loop = asyncio.get_event_loop()
        await loop.run_in_executor(None, _run_crawl, task)
        q.task_done()

        # 每次任务完成后清理过期任务
        _cleanup_old_tasks()


# ==================== API 接口 ====================

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
        raise HTTPException(status_code=400, detail="keyword 不能为空")

    # 相同 keyword + mode 去重
    if _keyword_in_queue(keyword):
        existing = next(
            t for t in _task_store.values()
            if t.keyword == keyword and t.status in (TaskStatus.QUEUED, TaskStatus.RUNNING)
        )
        return {"task_id": existing.task_id, "status": existing.status.value, "message": "相同关键词任务已在队列中"}

    q = _get_queue()
    if q.qsize() >= MAX_QUEUE_SIZE:
        raise HTTPException(status_code=429, detail="任务队列已满，请稍后再试")

    task_id = uuid.uuid4().hex[:12]
    task = TaskInfo(task_id, keyword, req.class_id, req.mode)
    _task_store[task_id] = task

    await q.put(task_id)
    mode_cn = {"incremental": "增量爬取", "refresh": "刷新导出", "full": "增量+刷新"}
    return {"task_id": task_id, "status": task.status.value, "message": f"任务已入队 ({mode_cn.get(req.mode.value, req.mode.value)})"}


@app.post("/refresh")
async def refresh(req: CrawlRequest):
    """便捷接口: 刷新已有题目集的导出数据"""
    req.mode = CrawlMode.REFRESH
    return await crawl(req)


@app.get("/status/{task_id}")
async def status(task_id: str):
    task = _task_store.get(task_id)
    if task is None:
        raise HTTPException(status_code=404, detail="任务不存在")
    return task.to_dict()


@app.get("/tasks")
async def list_tasks():
    """列出所有任务（最近的在前）"""
    tasks = sorted(_task_store.values(), key=lambda t: t.created_at, reverse=True)
    return [t.to_dict() for t in tasks[:20]]


# ==================== Cookie 管理 ====================

# 内存中的 cookie 状态（也可以持久化到文件，但重启后会自动检测）
_cookie_status = {"status": "UNKNOWN", "error": "", "updated_at": None}


class CookieStatusRequest(BaseModel):
    status: str          # OK / EXPIRED
    error: str = ""


class ManualCookieRequest(BaseModel):
    cookies: str         # JSON 字符串，教师从浏览器复制的 cookie


@app.put("/cookie/status")
async def update_cookie_status(req: CookieStatusRequest):
    """爬虫内部调用：上报 cookie 状态"""
    _cookie_status["status"] = req.status
    _cookie_status["error"] = req.error
    _cookie_status["updated_at"] = datetime.now().isoformat()
    return {"ok": True}


@app.get("/cookie/status")
async def get_cookie_status():
    """Java 后端 / 前端查询 cookie 状态"""
    return _cookie_status


@app.post("/cookie/update")
async def manual_update_cookie(req: ManualCookieRequest):
    """教师手动提交 cookie，验证后保存"""
    import json as json_mod
    try:
        cookies = json_mod.loads(req.cookies)
        if not isinstance(cookies, list) or len(cookies) == 0:
            raise HTTPException(status_code=400, detail="Cookie 格式错误，需要 JSON 数组")
    except json_mod.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Cookie 不是有效的 JSON")

    # 写入 manual_cookies.json
    cookie_path = Path(__file__).resolve().parent / "manual_cookies.json"
    with open(cookie_path, "w", encoding="utf-8") as f:
        json_mod.dump(cookies, f, ensure_ascii=False, indent=2)

    # 验证 cookie 是否有效
    client = PTAClient()
    for c in cookies:
        name = c.get("name", c.get("Name", ""))
        value = c.get("value", c.get("Value", ""))
        domain = c.get("domain", c.get("Domain", ".pintia.cn"))
        if name and value:
            client.session.cookies.set(name, value, domain=domain)

    if client._check_cookie_valid():
        # 有效 → 保存到标准 cookie 文件并更新状态
        client._save_cookies(cookies)
        _cookie_status["status"] = "OK"
        _cookie_status["error"] = ""
        _cookie_status["updated_at"] = datetime.now().isoformat()
        return {"valid": True, "message": "Cookie 有效，已保存。数据同步功能已恢复。"}
    else:
        return {"valid": False, "message": "Cookie 无效或已过期，请重新获取。"}


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("SPIDER_PORT", "8100"))
    uvicorn.run("spider_api:app", host="0.0.0.0", port=port, reload=False)
