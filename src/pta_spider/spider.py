"""
PTA 数据爬取客户端 - 混合方案
- Selenium 仅用于登录（处理滑块验证码）
- 登录后提取 cookie，所有数据爬取走 requests API
- cookie 缓存到本地，未过期时直接复用，无需重复登录
- 增量爬取：自动检测新题目集，只爬新增的
- 支持多账号配置
"""
import json
import os
import re
import sys
import time
import random
import pickle
import glob
import html
import shutil
import subprocess
import tempfile
import threading
import zipfile
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from datetime import datetime
from difflib import SequenceMatcher

# Windows 终端 UTF-8 输出修复
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import requests
import cv2
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException, TimeoutException
from selenium.webdriver.common.action_chains import ActionChains
from selenium.webdriver.support.wait import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from webdriver_manager.chrome import ChromeDriverManager
from dotenv import load_dotenv

try:
    from .group_exports import inspect_group_answer_export, split_group_answer_export
except ImportError:
    from group_exports import inspect_group_answer_export, split_group_answer_export

PACKAGE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = PACKAGE_DIR.parents[1]
SPIDER_DIR = PROJECT_ROOT
RUNTIME_DIR = Path(
    os.getenv("PTA_RUNTIME_DIR", str(SPIDER_DIR / "runtime"))
).resolve()
RUNTIME_DIR.mkdir(parents=True, exist_ok=True)

# Load secrets from runtime storage or the current process working directory.
_env_candidates = [
    RUNTIME_DIR / ".env",
    Path(".env"),
]
for _env in _env_candidates:
    if _env.exists():
        load_dotenv(_env)
        break
else:
    load_dotenv()  # fallback

BASE_URL = "https://pintia.cn"
API_BASE = "https://pintia.cn/api"
# Track crawled problem set IDs for incremental detection
HISTORY_FILE = str(RUNTIME_DIR / "crawl_history.json")
DEFAULT_BROWSER_HOME = (RUNTIME_DIR / "browser").resolve()
CRAWL_DIR = Path(os.getenv("PTA_CRAWL_DIR", str(SPIDER_DIR / "output"))).resolve()
CAPTCHA_IMAGE_FILE = RUNTIME_DIR / "captcha_bg.jpg"

# PTA 题面里的图片可能使用 ~/xxx 形式，入库前统一转成前端可直接渲染的完整 URL。
IMAGE_MARKDOWN_RE = re.compile(r"!\[[^\]]*\]\(([^)]+)\)")
HTML_IMAGE_RE = re.compile(r"<img\b[^>]*\bsrc=[\"']([^\"']+)[\"'][^>]*>", re.IGNORECASE)
PLACEHOLDER_PROBLEM_CONTENT_PREFIXES = (
    "这是一个编程题模板",
    "请在这里写题目描述",
    "this is a programming problem template",
    "please write the problem description here",
)
PLACEHOLDER_PROBLEM_CONTENT_PATTERNS = (
    re.compile(r"^(?:这是|这是一道?)?一个?(?:编程题|程序设计题|代码填空题|函数题)?模板"),
    re.compile(r"^(?:请)?在(?:这里|此处)(?:填写|编写|输入|添加)(?:题目)?(?:描述|内容)"),
    re.compile(r"^(?:this is )?(?:a )?(?:programming )?problem template"),
    re.compile(
        r"^(?:please )?(?:write|enter|add|fill in) "
        r"(?:the )?problem (?:description|statement)(?: here)?"
    ),
)


def _browser_home():
    configured = os.getenv("PTA_BROWSER_HOME")
    return Path(configured).resolve() if configured else DEFAULT_BROWSER_HOME


def _env_flag(name, default=False):
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _read_binary_version(command_path):
    try:
        output = subprocess.check_output(
            [command_path, "--version"],
            text=True,
            encoding="utf-8",
            errors="ignore",
            timeout=10,
        ).strip()
        return output
    except Exception:
        return ""


def _read_windows_file_version(path):
    if not path or not os.path.exists(path):
        return ""
    try:
        safe_path = path.replace("'", "''")
        command = f"[System.Diagnostics.FileVersionInfo]::GetVersionInfo('{safe_path}').ProductVersion"
        output = subprocess.check_output(
            ["powershell.exe", "-NoProfile", "-Command", command],
            text=True,
            encoding="utf-8",
            errors="ignore",
            timeout=10,
        ).strip()
        return output
    except Exception:
        return ""


def _which(command):
    found = shutil.which(command)
    return found if found and os.path.exists(found) else None


def _parse_major_version(version_text):
    match = re.search(r"(\d+)\.", version_text or "")
    return int(match.group(1)) if match else None


def _detect_chrome_binary():
    browser_home = _browser_home()
    candidates = [
        os.getenv("PTA_CHROME_BINARY"),
        _which("google-chrome"),
        _which("google-chrome-stable"),
        _which("chromium"),
        _which("chromium-browser"),
        str(browser_home / "chrome.exe"),
        str(browser_home / "chrome" / "chrome.exe"),
        str(browser_home / "Chrome" / "Application" / "chrome.exe"),
        str(browser_home / "chrome-win64" / "chrome.exe"),
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    ]
    for path in candidates:
        if path and os.path.exists(path):
            version_text = _read_binary_version(path) or _read_windows_file_version(path)
            return path, version_text, _parse_major_version(version_text)
    return None, "", None


def _iter_local_chromedrivers():
    seen = set()
    candidates = []
    browser_home = _browser_home()

    env_path = os.getenv("PTA_CHROMEDRIVER_PATH")
    if env_path and os.path.exists(env_path):
        candidates.append(("env", env_path))

    candidates.extend([
        ("browser_home", str(browser_home / "chromedriver.exe")),
        ("browser_home", str(browser_home / "chromedriver")),
        ("browser_home", str(browser_home / "chromedriver-win64" / "chromedriver.exe")),
        ("browser_home", str(browser_home / "chromedriver-linux64" / "chromedriver")),
        ("browser_home", str(browser_home / "driver" / "chromedriver.exe")),
        ("browser_home", str(browser_home / "driver" / "chromedriver")),
    ])

    resolved_driver = _which("chromedriver")
    if resolved_driver:
        candidates.append(("path", resolved_driver))

    candidates.extend(
        ("wdm", path)
        for path in glob.glob(
            os.path.join(
                os.path.expanduser("~"),
                ".wdm",
                "drivers",
                "chromedriver",
                "**",
                "chromedriver*",
            ),
            recursive=True,
        )
    )

    for source, path in candidates:
        norm = os.path.normcase(os.path.abspath(path))
        if norm in seen or not os.path.exists(path):
            continue
        seen.add(norm)
        version_text = _read_binary_version(path)
        major = _parse_major_version(version_text)
        yield {
            "source": source,
            "path": path,
            "version_text": version_text,
            "major": major,
        }


def _resolve_chromedriver(browser_major):
    env_override = None
    exact = []
    fallback = []
    for candidate in _iter_local_chromedrivers():
        if candidate["source"] == "env":
            env_override = candidate
        if browser_major is not None and candidate["major"] == browser_major:
            exact.append(candidate)
        else:
            fallback.append(candidate)

    if env_override is not None:
        return env_override

    exact.sort(key=lambda item: item["version_text"], reverse=True)
    fallback.sort(key=lambda item: item["version_text"], reverse=True)
    if browser_major is not None:
        return exact[0] if exact else None
    return fallback[0] if fallback else None


class AdaptiveTokenBucketRateLimiter:
    """
    Token bucket rate limiter with adaptive rate.
    - acquire blocks until a token is available (precise wait, not fixed 0.5s spin)
    - on_success slowly ramps current rate toward rate_max
    - on_rate_limit (429) halves current rate down to rate_min
    """

    def __init__(self, rate=60, per=60, rate_min=10, rate_max=None):
        self.rate_min = float(max(1, rate_min))
        self.rate_max = float(rate_max if rate_max is not None else rate)
        if self.rate_max < self.rate_min:
            self.rate_max = self.rate_min
        self.rate = float(max(self.rate_min, min(rate, self.rate_max)))
        self.per = float(per) if per else 60.0
        self.tokens = float(self.rate)
        self.last_refill = time.monotonic()
        self._lock = threading.Lock()
        self._success_streak = 0

    def _refill_unlocked(self, now=None):
        now = time.monotonic() if now is None else now
        elapsed = now - self.last_refill
        if elapsed > 0 and self.rate > 0:
            self.tokens = min(self.rate, self.tokens + elapsed * (self.rate / self.per))
            self.last_refill = now

    def acquire(self):
        """Acquire a token, block if none available."""
        while True:
            wait = 0.05
            with self._lock:
                self._refill_unlocked()
                if self.tokens >= 1:
                    self.tokens -= 1
                    return
                if self.rate > 0:
                    need = 1.0 - self.tokens
                    wait = need / (self.rate / self.per)
                else:
                    wait = 0.5
            time.sleep(max(0.01, min(wait, 1.0)))

    def on_success(self):
        """Slowly recover rate after consecutive successes."""
        with self._lock:
            self._success_streak += 1
            if self._success_streak >= 20 and self.rate < self.rate_max:
                old = self.rate
                self.rate = min(self.rate_max, self.rate * 1.05)
                self._success_streak = 0
                if self.rate - old > 0.5:
                    print(f"  限流自适应: rate {old:.1f} -> {self.rate:.1f}/min (恢复)")

    def on_rate_limit(self):
        """Halve rate after 429 to ease pressure."""
        with self._lock:
            self._success_streak = 0
            old = self.rate
            self.rate = max(self.rate_min, self.rate * 0.5)
            self.tokens = min(self.tokens, self.rate)
            if abs(self.rate - old) > 0.01:
                print(f"  限流自适应: rate {old:.1f} -> {self.rate:.1f}/min (429 降速)")

    def current_rate(self):
        with self._lock:
            return self.rate


# Backward-compatible alias
TokenBucketRateLimiter = AdaptiveTokenBucketRateLimiter


def _env_int(name, default, minimum=None, maximum=None):
    raw = os.getenv(name)
    if raw is None or str(raw).strip() == "":
        return default
    try:
        value = int(str(raw).strip())
    except ValueError:
        print(f"Invalid integer for {name}: {raw!r}; using {default}")
        return default
    if minimum is not None:
        value = max(minimum, value)
    if maximum is not None:
        value = min(maximum, value)
    return value


def _env_float(name, default, minimum=None, maximum=None):
    raw = os.getenv(name)
    if raw is None or str(raw).strip() == "":
        return default
    try:
        value = float(str(raw).strip())
    except ValueError:
        print(f"Invalid float for {name}: {raw!r}; using {default}")
        return default
    if minimum is not None:
        value = max(minimum, value)
    if maximum is not None:
        value = min(maximum, value)
    return value


EXPORT_RETRY_ROUNDS = _env_int("PTA_EXPORT_RETRY_ROUNDS", 2, minimum=0, maximum=10)
EXPORT_RETRY_DELAY_SECONDS = _env_int("PTA_EXPORT_RETRY_DELAY_SECONDS", 20, minimum=0, maximum=600)
EXPORT_DOWNLOAD_RETRIES = _env_int("PTA_EXPORT_DOWNLOAD_RETRIES", 4, minimum=1, maximum=10)
EXPORT_DOWNLOAD_RETRY_DELAY_SECONDS = _env_float(
    "PTA_EXPORT_DOWNLOAD_RETRY_DELAY_SECONDS", 5.0, minimum=0.0, maximum=120.0
)

# Throughput / concurrency knobs. Defaults favor stable PTA production crawls.
API_RATE_LIMIT_PER_MINUTE = _env_int("PTA_API_RATE_LIMIT_PER_MINUTE", 30, minimum=1, maximum=180)
API_RATE_LIMIT_MIN = _env_int("PTA_API_RATE_LIMIT_MIN", 8, minimum=1, maximum=180)
DETAIL_MAX_WORKERS = _env_int("PTA_DETAIL_MAX_WORKERS", 4, minimum=1, maximum=32)
PROBLEM_SET_MAX_WORKERS = _env_int("PTA_PROBLEM_SET_MAX_WORKERS", 2, minimum=1, maximum=8)
EXPORT_POLL_INTERVAL_SECONDS = _env_float("PTA_EXPORT_POLL_INTERVAL_SECONDS", 1.0, minimum=0.2, maximum=10.0)
EXPORT_CREATE_DELAY_SECONDS = _env_float("PTA_EXPORT_CREATE_DELAY_SECONDS", 0.5, minimum=0.0, maximum=30.0)
EXPORT_BETWEEN_DELAY_SECONDS = _env_float("PTA_EXPORT_BETWEEN_DELAY_SECONDS", 0.5, minimum=0.0, maximum=30.0)
EXPORT_READY_TIMEOUT_SECONDS = _env_int(
    "PTA_EXPORT_READY_TIMEOUT_SECONDS", 900, minimum=60, maximum=3600
)
GROUP_EXPORT_READY_TIMEOUT_SECONDS = _env_int(
    "PTA_GROUP_EXPORT_READY_TIMEOUT_SECONDS", 900, minimum=60, maximum=3600
)
EXPORT_PARALLEL = _env_flag("PTA_EXPORT_PARALLEL", True)
CRAWL_PROBLEM_TYPES = ("PROGRAMMING",)


def _export_poll_sleep(elapsed_seconds):
    """Adaptive poll interval: dense early, slightly slower after 30s."""
    base = EXPORT_POLL_INTERVAL_SECONDS
    if elapsed_seconds > 30:
        base = max(base, min(3.0, base * 2))
    time.sleep(base)


# Global rate limiter instance (adaptive)
_rate_limiter = AdaptiveTokenBucketRateLimiter(
    rate=API_RATE_LIMIT_PER_MINUTE,
    per=60,
    rate_min=min(API_RATE_LIMIT_MIN, API_RATE_LIMIT_PER_MINUTE),
    rate_max=API_RATE_LIMIT_PER_MINUTE,
)


class CrawlHistory:
    """
    Manage crawl history for incremental crawling.
    每个题目集记录两个时间戳:
      - content_crawled_at: 题目内容首次爬取时间（只爬一次）
      - export_refreshed_at: 导出数据（成绩单/答题卡/得分代码）最后刷新时间
    """

    def __init__(self, path=HISTORY_FILE):
        self.path = path
        self._lock = threading.RLock()
        self.data = self._load()

    def _load(self):
        if os.path.exists(self.path):
            with open(self.path, "r", encoding="utf-8") as f:
                data = json.load(f)
            # 兼容旧格式: 如果只有 crawled_at 没有新字段，自动迁移
            for ps_id, info in data.get("crawled_sets", {}).items():
                if "content_crawled_at" not in info:
                    info["content_crawled_at"] = info.get("crawled_at", datetime.now().isoformat())
                if "export_refreshed_at" not in info:
                    info["export_refreshed_at"] = info.get("crawled_at", datetime.now().isoformat())
            return data
        return {"crawled_sets": {}, "last_run": None}

    def save(self):
        with self._lock:
            self.data["last_run"] = datetime.now().isoformat()
            with open(self.path, "w", encoding="utf-8") as f:
                json.dump(self.data, f, ensure_ascii=False, indent=2)

    def is_crawled(self, problem_set_id):
        with self._lock:
            return problem_set_id in self.data["crawled_sets"]

    def mark_crawled(self, problem_set_id, name):
        """标记题目集内容已爬取（首次爬取，含题目内容+导出）"""
        with self._lock:
            now = datetime.now().isoformat()
            self.data["crawled_sets"][problem_set_id] = {
                "name": name,
                "crawled_at": now,
                "content_crawled_at": now,
                "export_refreshed_at": now,
            }
            self.data["last_run"] = now
            with open(self.path, "w", encoding="utf-8") as f:
                json.dump(self.data, f, ensure_ascii=False, indent=2)

    def mark_export_refreshed(self, problem_set_id):
        """标记导出数据已刷新（不重新爬取题目内容）"""
        with self._lock:
            if problem_set_id in self.data["crawled_sets"]:
                self.data["crawled_sets"][problem_set_id]["export_refreshed_at"] = datetime.now().isoformat()
                self.data["last_run"] = datetime.now().isoformat()
                with open(self.path, "w", encoding="utf-8") as f:
                    json.dump(self.data, f, ensure_ascii=False, indent=2)

    def get_new_sets(self, all_sets):
        """Filter out already-crawled sets from all sets"""
        new = []
        with self._lock:
            crawled = self.data["crawled_sets"]
            for ps in all_sets:
                ps_id = ps.get("id", "")
                if ps_id not in crawled:
                    new.append(ps)
        return new

    def get_all_crawled(self):
        """返回所有已爬取的题目集 {id: info}"""
        with self._lock:
            return dict(self.data.get("crawled_sets", {}))


class PTAClient:
    """PTA data crawler client, auto cookie management, API-first"""

    def __init__(self, username=None, password=None, allow_env_fallback=True):
        self.allow_env_fallback = allow_env_fallback
        env_username = os.getenv("PTA_USERNAME") if allow_env_fallback else None
        env_password = (
            os.getenv("PTA_PASSWORD") or os.getenv("PTA_PASSPORT")
        ) if allow_env_fallback else None
        self.username = username if username is not None else env_username
        self.password = password if password is not None else env_password
        self.crawl_dir = CRAWL_DIR
        self.force_selenium_login = _env_flag("PTA_FORCE_SELENIUM_LOGIN", False)
        self.headless = _env_flag("PTA_HEADLESS", False)
        # Per-account cookie file, supports multi-account
        safe_name = re.sub(r'[^\w]', '_', self.username or "default")
        self.cookie_file = str(RUNTIME_DIR / f"pta_cookies_{safe_name}.pkl")
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                          "AppleWebKit/537.36 (KHTML, like Gecko) "
                          "Chrome/120.0.0.0 Safari/537.36",
            "Accept": "application/json;charset=UTF-8",
            "Content-Type": "application/json;charset=UTF-8",
            "Accept-Language": "zh-CN",
            "Referer": "https://pintia.cn/",
            "x-lollipop": "c69dd20235e34148d85ece4af34ed26f",
            "x-marshmallow": "",
        })
        # Protect shared requests.Session under ThreadPool concurrency
        self._session_lock = threading.RLock()
        self.driver = None
        self.history = CrawlHistory()
        self.crawl_dir.mkdir(parents=True, exist_ok=True)

    # ==================== Cookie Management ====================

    def _save_cookies(self, cookies):
        with open(self.cookie_file, "wb") as f:
            pickle.dump(cookies, f)
        print("Cookie 已缓存到本地")

    def _load_cookies(self):
        if not os.path.exists(self.cookie_file):
            return None
        try:
            with open(self.cookie_file, "rb") as f:
                return pickle.load(f)
        except Exception:
            return None

    def _check_cookie_valid(self):
        """Verify cookie validity via exports API"""
        try:
            resp = self.session.get(
                f"{API_BASE}/exports",
                params={"page": 0, "limit": 1, "filter": "{}"},
                timeout=10,
            )
            if resp.status_code == 200:
                print("Cookie 有效")
                return True
            elif resp.status_code in (401, 403):
                print("Cookie 无效（未认证）")
            else:
                print(f"  [调试] 验证状态码: {resp.status_code}")
            return False
        except Exception as e:
            print(f"  [调试] 验证异常: {e}")
            return False

    def ensure_login(self):
        """登录流程：缓存cookie → Selenium重试(3次递增间隔) → 手动cookie文件 → 通知后端告警"""
        # 1) 尝试缓存 cookie
        if self.force_selenium_login:
            print("PTA_FORCE_SELENIUM_LOGIN=true，跳过缓存 Cookie，强制打开浏览器登录...")
        else:
            cached = self._load_cookies()
            if cached:
                for c in cached:
                    self.session.cookies.set(
                        c["name"], c["value"],
                        domain=c.get("domain", ".pintia.cn")
                    )
                if self._check_cookie_valid():
                    self._notify_cookie_status("OK")
                    return True
                print("Cookie 已过期，尝试 Selenium 重新登录...")

        # 2) Selenium 重试（最多3次，间隔递增 30s/60s/120s）
        retry_delays = [30, 60, 120]
        last_error = None
        for attempt, delay in enumerate(retry_delays, 1):
            try:
                print(f"Selenium 登录尝试 {attempt}/{len(retry_delays)}...")
                self._selenium_login()
                if self._check_cookie_valid():
                    print("Selenium 登录成功")
                    self._notify_cookie_status("OK")
                    return True
                print("Selenium 登录后 cookie 仍无效")
            except Exception as e:
                last_error = str(e)
                print(f"Selenium 尝试 {attempt} 失败: {e}")
            if attempt < len(retry_delays):
                print(f"等待 {delay}s 后重试...")
                time.sleep(delay)

        # 3) 尝试手动 cookie 文件
        manual_cookie_path = str(RUNTIME_DIR / "manual_cookies.json")
        if os.path.exists(manual_cookie_path):
            print(f"尝试从手动 cookie 文件恢复: {manual_cookie_path}")
            with open(manual_cookie_path, "r", encoding="utf-8") as f:
                manual_cookies = json.load(f)
            for c in manual_cookies:
                name = c.get("name", c.get("Name", ""))
                value = c.get("value", c.get("Value", ""))
                domain = c.get("domain", c.get("Domain", ".pintia.cn"))
                if name and value:
                    self.session.cookies.set(name, value, domain=domain)
            if self._check_cookie_valid():
                self._save_cookies(manual_cookies)
                print("手动 cookie 有效，已保存")
                self._notify_cookie_status("OK")
                return True
            print("手动 cookie 也已过期")

        # 4) 全部失败 → 通知后端，让前端提示教师手动提供 cookie
        error_msg = last_error or "所有登录方式均失败"
        self._notify_cookie_status("EXPIRED", error_msg)
        print("=" * 50)
        print("自动登录全部失败，已通知系统。")
        print("教师可在「班级管理 → PTA同步设置」中手动更新 Cookie。")
        print("=" * 50)
        return False

    def _problem_set_dir(self, ps_name):
        base_dir = self.crawl_dir / ps_name
        base_dir.mkdir(parents=True, exist_ok=True)
        return base_dir

    def _write_problem_set_info(self, ps_id, ps_name, problem_set_info):
        """Persist PTA problem-set metadata, including deadline fields."""
        if not isinstance(problem_set_info, dict):
            return
        base_dir = self._problem_set_dir(ps_name)
        info = dict(problem_set_info)
        info.setdefault("id", ps_id)
        info.setdefault("name", ps_name)
        with open(base_dir / "题目集信息.json", "w", encoding="utf-8") as f:
            json.dump(info, f, ensure_ascii=False, indent=2)

    def get_sets_requiring_content(self, all_sets):
        """Return new sets plus history entries whose local content was lost."""
        pending = self.history.get_new_sets(all_sets)
        pending_ids = {ps.get("id", "") for ps in pending}
        for ps in all_sets:
            ps_id = ps.get("id", "")
            ps_name = ps.get("name", "")
            if ps_id in pending_ids or not self.history.is_crawled(ps_id):
                continue
            content_file = self.crawl_dir / ps_name / "题目内容.txt"
            if not content_file.exists():
                print(f"本地爬取内容缺失，将重新抓取: {ps_name}")
                pending.append(ps)
                pending_ids.add(ps_id)
        return pending

    def _notify_cookie_status(self, status: str, error: str = ""):
        """通知 Java 后端 cookie 状态（OK / EXPIRED），用于前端告警"""
        backend_url = os.getenv("JAVA_BACKEND_URL", "http://localhost:8081")
        try:
            import requests as req_lib
            req_lib.put(
                f"{backend_url}/api/pta-cookie/status",
                json={"status": status, "error": error},
                timeout=5
            )
        except Exception as e:
            print(f"通知后端 cookie 状态失败（不影响主流程）: {e}")

    def _auto_sync_to_db(self):
        """爬取/刷新完成后自动将数据同步到数据库"""
        try:
            print("\n--- 自动同步数据到数据库 ---")
            # sync_to_db.py 和 spider.py 在同一目录
            import importlib
            sync_module_path = os.path.join(os.path.dirname(__file__), "sync_to_unified_db.py")
            if os.path.exists(sync_module_path):
                import sys as _sys
                _sys.path.insert(0, os.path.dirname(__file__))
                from .sync_to_unified_db import run_configured_sync
                report = run_configured_sync(strict=True)
                if not report.get("ok"):
                    raise RuntimeError(report.get("error") or "database sync failed")
                print("数据库同步完成")
            else:
                print("sync_to_db.py 不存在，跳过自动入库")
        except Exception as e:
            print(f"自动入库失败（不影响爬取结果）: {e}")


    # ==================== Selenium Login (only here) ====================

    def _selenium_login(self):
        chrome_options = Options()
        chrome_options.add_argument("--disable-blink-features=AutomationControlled")
        chrome_options.add_argument("--disable-infobars")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--remote-debugging-port=0")
        chrome_options.add_argument("--window-size=1440,900")
        chrome_options.add_argument("--disable-software-rasterizer")
        chrome_options.add_argument("--disable-extensions")
        chrome_options.add_argument("--disable-background-networking")
        chrome_options.add_argument("--no-first-run")
        chrome_options.add_argument("--no-default-browser-check")
        chrome_options.add_argument("--disable-popup-blocking")
        chrome_options.add_argument("--disable-features=Translate,AutomationControlled")
        if _env_flag("PTA_HEADLESS", True):
            # The spider usually runs in the background; headless mode avoids
            # Chrome startup failures in service environments.
            chrome_options.add_argument("--headless=new")

        # 优先使用本地缓存的 ChromeDriver，避免网络下载失败
        import glob
        local_drivers = glob.glob(
            os.path.join(os.path.expanduser("~"), ".wdm", "drivers",
                         "chromedriver", "**", "chromedriver.exe"),
            recursive=True
        )
        if local_drivers:
            driver_path = sorted(local_drivers)[-1]
            print(f"使用本地 ChromeDriver: {driver_path}")
            service = Service(driver_path)
        else:
            print("本地无缓存，尝试在线下载 ChromeDriver...")
            service = Service(ChromeDriverManager().install())
        try:
            self.driver = webdriver.Chrome(service=service, options=chrome_options)
        except Exception as e:
            raise RuntimeError(
                "failed to start Chrome for PTA auto-login; "
                "try updating Chrome/ChromeDriver or set PTA_HEADLESS=false for interactive debug"
            ) from e
        self.driver.implicitly_wait(10)

        try:
            print("Selenium 登录中...")
            self.driver.get(f"{BASE_URL}/auth/login")
            time.sleep(random.uniform(1, 3))

            self.driver.find_element(
                By.CSS_SELECTOR, "input[placeholder='电子邮箱或手机号码']"
            ).send_keys(self.username)
            time.sleep(random.uniform(0.5, 1.5))

            self.driver.find_element(
                By.CSS_SELECTOR, "input[placeholder='密码'][type='password']"
            ).send_keys(self.password)
            time.sleep(random.uniform(0.5, 1.5))

            self.driver.find_element(
                By.CSS_SELECTOR, "button[type='submit']"
            ).click()
            time.sleep(random.uniform(2, 3))

            self._handle_captcha()
            time.sleep(3)

            selenium_cookies = self.driver.get_cookies()
            self._save_cookies(selenium_cookies)
            for c in selenium_cookies:
                self.session.cookies.set(
                    c["name"], c["value"],
                    domain=c.get("domain", ".pintia.cn")
                )
            print("登录完成，cookie 已转移到 requests")
        finally:
            if self.driver:
                self.driver.quit()
                self.driver = None

    def _handle_captcha(self):
        iframes = self.driver.find_elements(By.ID, "tcaptcha_iframe_dy")
        if not iframes:
            return
        print("处理滑块验证码...")
        self.driver.switch_to.frame(iframes[0])
        bg_element = WebDriverWait(self.driver, 10).until(
            EC.presence_of_element_located((By.ID, "slideBg"))
        )
        bg_style = self.driver.execute_script(
            "return window.getComputedStyle(arguments[0]).backgroundImage;",
            bg_element
        )
        url_match = re.search(r'url\(["\']?(https?://[^"\')]+)', bg_style)
        if url_match:
            captcha_url = url_match.group(1).replace("&amp;", "&")
            resp = requests.get(captcha_url)
            with open(CAPTCHA_IMAGE_FILE, "wb") as f:
                f.write(resp.content)
            dis = self._get_captcha_offset(CAPTCHA_IMAGE_FILE)
            time.sleep(random.uniform(1, 3))
            slider = self.driver.find_element(
                By.CSS_SELECTOR, 'div.tc-fg-item[aria-label="拖动下方滑块完成拼图"]'
            )
            new_dis = int(dis * 340 / 672 - slider.location["x"])
            ActionChains(self.driver).click_and_hold(slider).perform()
            time.sleep(random.uniform(0, 0.5))
            moved = 0
            while moved < new_dis - 3:
                x = random.randint(5, 10)
                moved += x
                ActionChains(self.driver).move_by_offset(xoffset=x, yoffset=0).perform()
            ActionChains(self.driver).release().perform()
        self.driver.switch_to.default_content()

    @staticmethod
    def _get_captcha_offset(image_path=CAPTCHA_IMAGE_FILE):
        image = cv2.imread(str(image_path))
        blurred = cv2.GaussianBlur(image, (5, 5), 0, 0)
        canny = cv2.Canny(blurred, 0, 100)
        contours, _ = cv2.findContours(canny, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            area = cv2.contourArea(contour)
            length = cv2.arcLength(contour, True)
            if 5025 < area < 7225 and 300 < length < 380:
                x, y, w, h = cv2.boundingRect(contour)
                return x
        return 0

    # ==================== API Data Crawling ====================

    def api_get(self, path, params=None):
        """Unified GET with bounded retries for auth, throttling, and transient failures."""
        max_retries = 3
        auth_retried = False
        for attempt in range(max_retries + 1):
            _rate_limiter.acquire()
            try:
                with self._session_lock:
                    resp = self.session.get(f"{API_BASE}{path}", params=params, timeout=30)
            except requests.exceptions.RequestException as exc:
                if attempt >= max_retries:
                    raise
                wait = min(30, 2 ** attempt) + random.uniform(0, 0.5)
                print(
                    f"  PTA network error, retrying in {wait:.1f}s "
                    f"({attempt + 1}/{max_retries}): {exc}"
                )
                time.sleep(wait)
                continue
            if resp.status_code in (401, 403):
                if not auth_retried:
                    auth_retried = True
                    print("认证失效，重新登录...")
                    self.ensure_login()
                    continue
            if resp.status_code == 429:
                _rate_limiter.on_rate_limit()
                if attempt < max_retries:
                    retry_after = resp.headers.get("Retry-After")
                    if retry_after and str(retry_after).isdigit():
                        wait = max(1, int(retry_after))
                    else:
                        wait = min(90, 10 * (2 ** attempt))
                    print(f"  429 请求过于频繁，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                print("  429 重试次数已用尽")
            if resp.status_code in (408, 425, 500, 502, 503, 504) and attempt < max_retries:
                wait = min(30, 2 ** attempt) + random.uniform(0, 0.5)
                print(
                    f"  PTA HTTP {resp.status_code}, retrying in {wait:.1f}s "
                    f"({attempt + 1}/{max_retries})"
                )
                time.sleep(wait)
                continue
            resp.raise_for_status()
            _rate_limiter.on_success()
            return resp.json()
        resp.raise_for_status()
        return resp.json()

    def get_user_groups(self, include_archived=None, search_text=None):
        """List PTA user groups visible to the current teacher account."""
        if include_archived is None:
            include_archived = _env_flag("PTA_INCLUDE_ARCHIVED_USER_GROUPS", True)

        primary_groups = self._fetch_user_groups(search_text=search_text)
        if primary_groups or not include_archived:
            return primary_groups

        return self._dedupe_user_groups(
            self._fetch_user_groups(search_text=search_text, archived=False)
            + self._fetch_user_groups(search_text=search_text, archived=True)
        )

    def _fetch_user_groups(self, search_text=None, archived=None):
        """Fetch one PTA user-group page with optional archived filter."""
        search_text = str(search_text or "").strip()
        params = {}
        if archived is not None:
            params["archived"] = "true" if archived else "false"
        if search_text:
            params["keyword"] = search_text

        try:
            data = self.api_get("/user-groups", params=params or None)
        except requests.exceptions.HTTPError as e:
            if search_text:
                print(f"user group filtered query failed ({e}); falling back to unfiltered list")
                params.pop("keyword", None)
                data = self.api_get("/user-groups", params=params or None)
            else:
                raise

        groups = []
        for group in data.get("userGroups", []) if isinstance(data, dict) else []:
            if isinstance(group, dict):
                group = dict(group)
                if archived is not None:
                    group["_query_archived"] = archived
                groups.append(group)
        return groups

    def _fetch_user_group_candidates(self, search_text):
        """Prefer /user-groups, then combine archived=false and archived=true."""
        primary_groups = self._dedupe_user_groups(
            self._fetch_user_groups(search_text=search_text)
        )
        if primary_groups:
            return primary_groups, "primary"

        fallback_groups = self._dedupe_user_groups(
            self._fetch_user_groups(search_text=search_text, archived=False)
            + self._fetch_user_groups(search_text=search_text, archived=True)
        )
        return fallback_groups, "archived-filtered"

    def _dedupe_user_groups(self, groups):
        result = []
        seen_ids = set()
        for group in groups:
            group_id = str(group.get("id") or "").strip()
            dedupe_key = group_id or self._normalize_group_name(group.get("name"))
            if dedupe_key and dedupe_key in seen_ids:
                continue
            if dedupe_key:
                seen_ids.add(dedupe_key)
            result.append(group)
        return result

    @staticmethod
    def _normalize_group_name(value):
        if value is None:
            return ""
        return re.sub(r"\s+", "", str(value)).strip().lower()

    @classmethod
    def _user_group_fuzzy_score(cls, target, candidate):
        target = cls._normalize_group_name(target)
        candidate = cls._normalize_group_name(candidate)
        if not target or not candidate or target == candidate:
            return 1.0 if target and target == candidate else 0.0

        # Avoid broad matches like "计科25" selecting a specific class by accident.
        if min(len(target), len(candidate)) < 6:
            return 0.0

        ratio = SequenceMatcher(None, target, candidate).ratio()
        if target in candidate or candidate in target:
            coverage = min(len(target), len(candidate)) / max(len(target), len(candidate))
            if coverage >= 0.72:
                return max(ratio, 0.86 + min(0.08, coverage * 0.08))

        ordered_pattern = ".*?".join(re.escape(ch) for ch in target)
        if re.search(ordered_pattern, candidate):
            coverage = len(target) / len(candidate)
            if coverage >= 0.65:
                return max(ratio, 0.82 + min(0.08, coverage * 0.08))

        return ratio

    def find_user_group(self, group_name):
        """Find one user group by its unique display name."""
        normalized_target = self._normalize_group_name(group_name)
        if not normalized_target:
            return None

        groups, source = self._fetch_user_group_candidates(group_name)
        return self._select_user_group_match(group_name, groups, source)

    def _select_user_group_match(self, group_name, groups, source="primary"):
        normalized_target = self._normalize_group_name(group_name)
        exact = [
            group for group in groups
            if self._normalize_group_name(group.get("name")) == normalized_target
        ]
        if len(exact) == 1:
            return exact[0]
        if len(exact) > 1:
            active = [group for group in exact if not group.get("archived")]
            if len(active) == 1:
                return active[0]
            names = ", ".join(f"{g.get('name')}({g.get('id')})" for g in exact)
            raise RuntimeError(f"PTA user group name is not unique: {group_name}; matches: {names}")

        min_score = float(os.getenv("PTA_USER_GROUP_FUZZY_MIN_SCORE", "0.82"))
        scored = []
        for group in groups:
            score = self._user_group_fuzzy_score(group_name, group.get("name"))
            if score >= min_score:
                scored.append((score, group))

        scored.sort(key=lambda item: item[0], reverse=True)
        if not scored:
            return None

        top_score, top_group = scored[0]
        near = [(score, group) for score, group in scored if top_score - score < 0.04]
        if len(near) == 1:
            print(
                "Fuzzy matched PTA user group: "
                f"{group_name} -> {top_group.get('name')}({top_group.get('id')}), "
                f"score={top_score:.2f}, source={source}"
            )
            return top_group

        active_near = [
            (score, group) for score, group in near
            if not group.get("archived") and group.get("_query_archived") is not True
        ]
        if len(active_near) == 1 and active_near[0][0] >= 0.88:
            score, group = active_near[0]
            print(
                "Fuzzy matched active PTA user group: "
                f"{group_name} -> {group.get('name')}({group.get('id')}), "
                f"score={score:.2f}, source={source}"
            )
            return group

        names = ", ".join(
            f"{group.get('name')}({group.get('id')}, score={score:.2f})"
            for score, group in near[:8]
        )
        raise RuntimeError(f"PTA user group fuzzy match is ambiguous: {group_name}; candidates: {names}")

        return None

    def get_user_group_permissions(self, group_id):
        """Return authorization records from /api/user-groups/{id}/permissions."""
        data = self.api_get(f"/user-groups/{group_id}/permissions")
        return data.get("userGroupProblemSets", [])

    def _find_user_group_by_id(self, group_id):
        """Find one visible PTA user group by id, used to enrich roster metadata."""
        target = str(group_id or "").strip()
        if not target:
            return None
        for group in self.get_user_groups(include_archived=True):
            if str(group.get("id") or "").strip() == target:
                return group
        return None

    def get_user_group_members(self, group_id, limit=100):
        """Fetch authoritative PTA user-group members from the overview member API."""
        group_id = str(group_id or "").strip()
        if not group_id:
            raise RuntimeError("PTA user group id is required for member fetch")

        members = []
        page = 0
        total = None
        while True:
            payload = {
                "page": page,
                "limit": limit,
                "studentUserFilter": {
                    "field": "STUDENT_NUMBER",
                    "keyword": "",
                    "unbindOnly": False,
                },
            }
            resp = self.api_post(
                f"/user-groups/{group_id}/user-group-members",
                json_data=payload,
            )
            data = resp.json() if resp.text else {}
            if not isinstance(data, dict):
                raise RuntimeError(f"unexpected PTA user-group members payload: {type(data)}")

            total = data.get("total", total)
            student_user_by_id = data.get("studentUserById") or {}
            user_by_id = data.get("userById") or {}
            page_members = data.get("members") or []

            for item in page_members:
                if not isinstance(item, dict):
                    continue
                student_user_id = str(item.get("studentUserId") or "").strip()
                pta_user_id = str(item.get("userId") or "").strip()
                student_user = student_user_by_id.get(student_user_id) or {}
                user_info = user_by_id.get(pta_user_id) or {}
                student_no = str(
                    student_user.get("studentNumber")
                    or student_user.get("studentNo")
                    or user_info.get("studentNumber")
                    or ""
                ).strip()
                student_name = str(
                    student_user.get("name")
                    or user_info.get("name")
                    or user_info.get("nickname")
                    or ""
                ).strip()
                if not student_no:
                    continue
                members.append(
                    {
                        "pta_group_id": group_id,
                        "pta_member_id": str(item.get("id") or "").strip() or None,
                        "pta_user_id": pta_user_id or None,
                        "pta_student_user_id": student_user_id or None,
                        "student_no": student_no,
                        "student_name": student_name or student_no,
                        "raw_json": {
                            "member": item,
                            "studentUser": student_user,
                            "user": user_info,
                        },
                    }
                )

            if len(page_members) < limit:
                break
            if total is not None and len(members) >= int(total):
                break
            page += 1
            # api_post 内令牌桶限流器已统一控制请求频率，无需额外 sleep

        return {
            "pta_group_id": group_id,
            "total": total if total is not None else len(members),
            "members": members,
        }

    def write_user_group_roster(self, group_id=None, group_name=None, crawl_dir=None):
        """Write PTA user-group roster metadata for the database sync step."""
        group_id, group_name = self._resolve_user_group_id(group_id, group_name)
        if not group_id:
            raise RuntimeError("PTA user group id or exact user group name is required for roster sync")
        group_meta = self._find_user_group_by_id(group_id) or {}
        group_name = group_name or group_meta.get("name") or group_id
        roster = self.get_user_group_members(group_id)
        crawl_dir = Path(crawl_dir or self.crawl_dir)
        crawl_dir.mkdir(parents=True, exist_ok=True)
        payload = {
            "fetched_at": datetime.now().isoformat(timespec="seconds"),
            "group": {
                "pta_group_id": str(group_id),
                "pta_group_name": group_name,
                "raw_json": group_meta,
            },
            "members": roster["members"],
            "member_count": len(roster["members"]),
            "reported_total": roster.get("total"),
        }
        # Keep the authoritative user-group scope on this client. Submission
        # crawling uses it to query one roster member at a time, which both
        # excludes shared-problem-set outsiders and avoids the global 200-row
        # snapshot limit.
        self._active_group_user_ids = sorted({
            str(member.get("pta_user_id") or "").strip()
            for member in roster["members"]
            if str(member.get("pta_user_id") or "").strip()
        })
        self._active_group_id = str(group_id)
        out_path = crawl_dir / "_pta_user_group_roster.json"
        with out_path.open("w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"  PTA用户组花名册已写入: {out_path} ({len(roster['members'])}人)")
        return payload

    def get_problem_sets_by_user_group(self, group_id, group_name=None):
        """Fetch problem sets authorized to a PTA user group."""
        result = []
        seen_ids = set()
        page = 0
        label = group_name or group_id
        print(f"Searching problem sets by user group: {label} ({group_id})", flush=True)
        while True:
            filter_param = json.dumps({
                "userGroupId": str(group_id),
                "stage": {"stage": "NORMAL"},
            }, ensure_ascii=False)
            sort_param = json.dumps({"type": "UPDATE_AT", "asc": False})
            data = self.api_get("/problem-sets/admin", params={
                "sort_by": sort_param,
                "page": page,
                "limit": 50,
                "filter": filter_param,
            })
            sets = data.get("problemSets", [])
            if not sets:
                break
            for problem_set in sets:
                ps_id = problem_set.get("id")
                if ps_id and ps_id in seen_ids:
                    continue
                if ps_id:
                    seen_ids.add(ps_id)
                print(f"  Found: {problem_set.get('name', '?')}")
                result.append(problem_set)
            if len(sets) < 50 or len(result) >= data.get("total", 0):
                break
            page += 1
            # api_get 内令牌桶限流器已统一控制请求频率，无需额外 sleep

        print(f"Found {len(result)} problem sets by user group")
        return result

    def search_problem_sets(self, group_id=None, group_name=None):
        """Fetch teaching problem sets strictly through a PTA user group."""
        if group_id:
            return self.get_problem_sets_by_user_group(group_id, group_name)

        group = self.find_user_group(group_name)
        if group:
            return self.get_problem_sets_by_user_group(group.get("id"), group.get("name"))

        raise RuntimeError("PTA user group id or exact user group name is required")

    def get_problem_set_detail(self, ps_id):
        """Get single problem set detail"""
        return self.api_get(f"/problem-sets/{ps_id}")

    def get_problems(self, ps_id, problem_type="PROGRAMMING", limit=200):
        """Get every preview-visible problem of one PTA type, with pagination."""
        all_problems = []
        seen_ids = set()
        page = 0
        max_pages = 100

        while page < max_pages:
            data = self.api_get(
                f"/problem-sets/{ps_id}/preview/problems",
                params={
                    "problem_type": problem_type,
                    "page": page,
                    "limit": limit,
                },
            )
            page_problems = data.get("problemSetProblems", [])
            if not page_problems:
                break

            new_count = 0
            for problem in page_problems:
                problem_id = str(problem.get("id") or "").strip()
                dedup_key = problem_id or json.dumps(problem, sort_keys=True, ensure_ascii=False)
                if dedup_key in seen_ids:
                    continue
                seen_ids.add(dedup_key)
                all_problems.append(problem)
                new_count += 1

            total = data.get("total")
            if new_count == 0:
                break
            if total is not None and len(all_problems) >= int(total):
                break
            if len(page_problems) < limit:
                break
            page += 1

        if page >= max_pages:
            raise RuntimeError(
                f"problem pagination exceeded safety cap for problem set {ps_id}, "
                f"type {problem_type}"
            )
        return all_problems

    def get_problem_detail(self, ps_id, problem_id):
        """Get detailed content of a single problem"""
        return self.api_get(f"/problem-sets/{ps_id}/preview/problems/{problem_id}")

    @staticmethod
    def _normalize_asset_url(url):
        text = str(url or "").strip()
        if not text:
            return ""
        if " " in text:
            quoted_match = re.match(r'^([^\s]+)\s+["\'].*["\']$', text)
            if quoted_match:
                text = quoted_match.group(1)
        if text.startswith(("http://", "https://", "data:")):
            return text
        if text.startswith("~/"):
            return f"https://images.ptausercontent.com/{text[2:]}"
        if text.startswith("/"):
            return f"{BASE_URL}{text}"
        return text

    @classmethod
    def _extract_image_urls(cls, content):
        urls = []
        seen = set()
        for pattern in (IMAGE_MARKDOWN_RE, HTML_IMAGE_RE):
            for match in pattern.finditer(content or ""):
                url = cls._normalize_asset_url(match.group(1))
                if url and url not in seen:
                    seen.add(url)
                    urls.append(url)
        return urls

    @classmethod
    def _normalize_markdown_image_urls(cls, content):
        def replace_image(match):
            target = str(match.group(1) or "").strip()
            if not target:
                return match.group(0)
            title = ""
            url = target
            title_match = re.match(r'^([^\s]+)(\s+["\'].*["\'])$', target)
            if title_match:
                url = title_match.group(1)
                title = title_match.group(2)
            normalized = cls._normalize_asset_url(url)
            return match.group(0).replace(target, f"{normalized}{title}", 1)

        return IMAGE_MARKDOWN_RE.sub(replace_image, content or "")

    @classmethod
    def _normalize_html_image_urls(cls, content):
        def replace_src(match):
            return f"{match.group(1)}{cls._normalize_asset_url(match.group(2))}{match.group(3)}"

        return re.sub(
            r'(<img\b[^>]*\bsrc=["\'])([^"\']+)(["\'][^>]*>)',
            replace_src,
            content or "",
            flags=re.IGNORECASE,
        )

    @staticmethod
    def _visible_problem_content_text(content):
        if not isinstance(content, str):
            return ""
        normalized = html.unescape(content)
        normalized = normalized.replace("\ufeff", "").replace("\u200b", "")
        normalized = re.sub(
            r"<(?:script|style)\b[^>]*>.*?</(?:script|style)>",
            " ",
            normalized,
            flags=re.IGNORECASE | re.DOTALL,
        )
        normalized = re.sub(r"<!--.*?-->", " ", normalized, flags=re.DOTALL)
        normalized = re.sub(r"<[^>]+>", " ", normalized)
        normalized = re.sub(r"!\[[^\]]*]\([^)]+\)", " ", normalized)
        normalized = re.sub(r"[#>*_`~\[\]{}]+", " ", normalized)
        normalized = re.sub(r"\s+", " ", normalized).strip().lower()
        return normalized

    @classmethod
    def _is_placeholder_problem_content(cls, content):
        normalized = cls._visible_problem_content_text(content)
        return any(
            normalized.startswith(prefix)
            for prefix in PLACEHOLDER_PROBLEM_CONTENT_PREFIXES
        ) or any(pattern.search(normalized) for pattern in PLACEHOLDER_PROBLEM_CONTENT_PATTERNS)

    @classmethod
    def _has_meaningful_problem_content(cls, content):
        if not isinstance(content, str) or not content.strip():
            return False
        if cls._is_placeholder_problem_content(content):
            return False
        if cls._visible_problem_content_text(content):
            return True
        return bool(
            IMAGE_MARKDOWN_RE.search(content)
            or HTML_IMAGE_RE.search(content)
        )

    @classmethod
    def _select_content_candidate(cls, mappings, field_names):
        first_non_empty = ""
        for mapping in mappings:
            mapping = mapping or {}
            for field_name in field_names:
                candidate = mapping.get(field_name)
                if not isinstance(candidate, str):
                    continue
                text = candidate.strip()
                if not text:
                    continue
                if not first_non_empty:
                    first_non_empty = text
                if cls._has_meaningful_problem_content(text):
                    return text
        return first_non_empty

    @classmethod
    def _select_problem_markdown(cls, psp, raw_problem):
        return cls._select_content_candidate(
            (psp, raw_problem),
            (
                "content",
                "description",
                "statement",
                "problemDescription",
                "contentMarkdown",
                "markdownContent",
            ),
        )

    @classmethod
    def _select_problem_html(cls, psp, raw_problem):
        return cls._select_content_candidate(
            (psp, raw_problem),
            ("contentHtml", "htmlContent", "renderedContent"),
        )

    @classmethod
    def _problem_record_has_valid_content(cls, record):
        return cls._has_meaningful_problem_content(
            (record or {}).get("content_md")
        ) or cls._has_meaningful_problem_content(
            (record or {}).get("content_html")
        )

    @classmethod
    def _problem_detail_record(cls, ps_id, problem, detail):
        psp = (detail or {}).get("problemSetProblem") or {}
        raw_problem = (detail or {}).get("problem") or {}
        score = psp.get("score")
        if score is None:
            score = problem.get("score")
        difficulty_level = psp.get("difficulty")
        if difficulty_level is None:
            difficulty_level = raw_problem.get("difficulty")
        content_md = cls._select_problem_markdown(psp, raw_problem)
        content_html = cls._select_problem_html(psp, raw_problem)
        content_md = cls._normalize_markdown_image_urls(content_md)
        content_html = cls._normalize_html_image_urls(content_html)
        image_urls = cls._extract_image_urls("\n".join([str(content_md or ""), str(content_html or "")]))
        pta_global_problem_id = (
            psp.get("problemId")
            or psp.get("globalProblemId")
            or raw_problem.get("id")
            or raw_problem.get("problemId")
        )
        return {
            "problem_set_id": str(ps_id or ""),
            "problem_set_problem_id": str(psp.get("id") or problem.get("id") or ""),
            "pta_global_problem_id": str(pta_global_problem_id or ""),
            "problem_url": f"{BASE_URL}/problem-sets/{ps_id}/problems/{psp.get('id') or problem.get('id') or ''}",
            "problem_label": str(psp.get("label") or problem.get("label") or ""),
            "title": str(psp.get("title") or raw_problem.get("title") or problem.get("title") or ""),
            "score": score,
            "problem_type": str(
                psp.get("problemType")
                or psp.get("type")
                or raw_problem.get("problemType")
                or raw_problem.get("type")
                or problem.get("problemType")
                or problem.get("type")
                or ""
            ),
            "difficulty_level": difficulty_level,
            "difficulty_label": str(psp.get("difficultyLabel") or raw_problem.get("difficultyLabel") or ""),
            "problem_pool_index": psp.get("problemPoolIndex"),
            "index_in_problem_pool": psp.get("indexInProblemPool"),
            "knowledge_point_ids": psp.get("knowledgePointIds") or raw_problem.get("knowledgePointIds") or [],
            "knowledge_points": psp.get("knowledgePoints") or raw_problem.get("knowledgePoints") or [],
            "content_md": content_md,
            "content_html": content_html,
            "content_format": "markdown" if content_md else ("html" if content_html else "markdown"),
            "image_urls": image_urls,
            "raw_json": detail or {},
        }

    def get_submissions(self, ps_id, page=0, limit=200, filter_obj=None):
        """Get submissions for a problem set"""
        params = {"page": page, "limit": limit}
        if filter_obj is not None:
            params["filter"] = json.dumps(filter_obj, ensure_ascii=False)
        return self.api_get(f"/problem-sets/{ps_id}/submissions", params=params)

    @staticmethod
    def _submission_dedup_key(sub):
        submission_id = str(sub.get("id") or "").strip()
        if submission_id:
            return ("id", submission_id)
        return (
            "fields",
            str(sub.get("submitAt") or ""),
            str(sub.get("userId") or ""),
            str(sub.get("problemSetProblemId") or ""),
            str(sub.get("status") or ""),
            str(sub.get("score") or ""),
        )

    def _get_all_submissions_for_filter(self, ps_id, filter_obj=None):
        """
        Fetch one submission query and detect PTA's repeated-page behavior.

        PTA currently ignores page/offset pagination on this endpoint. A query
        that fills the 200-row server cap and repeats on page 1 is explicitly
        marked incomplete instead of silently reported as complete.
        """
        all_subs = []
        seen_ids = set()
        page = 0
        max_pages = 500
        repeated_page = False
        hit_server_cap = False

        while page < max_pages:
            data = self.get_submissions(
                ps_id,
                page=page,
                limit=200,
                filter_obj=filter_obj,
            )
            subs = data.get("submissions", [])
            if not subs:
                break

            # Dedup: if all records on this page are seen, API is looping
            new_count = 0
            for sub in subs:
                sub_key = self._submission_dedup_key(sub)
                if sub_key not in seen_ids:
                    seen_ids.add(sub_key)
                    all_subs.append(sub)
                    new_count += 1

            if new_count == 0:
                repeated_page = True
                break

            if len(subs) < 200:
                break

            hit_server_cap = True
            page += 1

        return all_subs, {
            "rows": len(all_subs),
            "filter": filter_obj or {},
            "hit_server_cap": hit_server_cap,
            "repeated_page": repeated_page,
            "complete": not (hit_server_cap and repeated_page),
        }

    def get_all_submissions(self, ps_id, pta_user_ids=None):
        """Fetch only PROGRAMMING submissions in the authoritative group scope."""
        if pta_user_ids is None:
            pta_user_ids = getattr(self, "_active_group_user_ids", None)

        status = {
            "problem_set_id": str(ps_id),
            "scope": "PROBLEM_SET_SNAPSHOT",
            "complete": True,
            "rows": 0,
            "queried_user_count": 0,
            "incomplete_user_ids": [],
        }
        if pta_user_ids:
            merged = []
            seen = set()
            incomplete_user_ids = []
            user_ids = sorted({
                str(value or "").strip()
                for value in pta_user_ids
                if str(value or "").strip()
            })
            for user_id in user_ids:
                rows, query_status = self._get_all_submissions_for_filter(
                    ps_id,
                    {"userId": user_id, "problemType": "PROGRAMMING"},
                )
                if not query_status["complete"]:
                    incomplete_user_ids.append(user_id)
                for sub in rows:
                    key = self._submission_dedup_key(sub)
                    if key in seen:
                        continue
                    seen.add(key)
                    merged.append(sub)
            status.update({
                "scope": "PTA_USER_GROUP_MEMBERS",
                "complete": not incomplete_user_ids,
                "rows": len(merged),
                "queried_user_count": len(user_ids),
                "incomplete_user_ids": incomplete_user_ids,
            })
            submissions = merged
        else:
            submissions, query_status = self._get_all_submissions_for_filter(
                ps_id,
                {"problemType": "PROGRAMMING"},
            )
            status.update({
                "complete": query_status["complete"],
                "rows": len(submissions),
                "global_query": query_status,
            })

        # Enforce the requested scope locally as well, in case PTA ignores an
        # unsupported filter shape and returns mixed problem types.
        submissions = [
            submission
            for submission in submissions
            if str(submission.get("problemType") or "").strip().upper()
            == "PROGRAMMING"
        ]
        status["problem_type"] = "PROGRAMMING"
        status["rows"] = len(submissions)
        if not hasattr(self, "_submission_crawl_status"):
            self._submission_crawl_status = {}
        self._submission_crawl_status[str(ps_id)] = status
        if not status["complete"]:
            print(
                f"  警告: 提交记录仍受 PTA 200 条上限影响: "
                f"{ps_id} ({len(status['incomplete_user_ids'])} 个学生)"
            )
        return submissions

    def write_submission_crawl_status(self, ps_id, base_dir):
        status = getattr(self, "_submission_crawl_status", {}).get(str(ps_id))
        if status is None:
            return
        with (Path(base_dir) / "submission_crawl_status.json").open(
            "w", encoding="utf-8"
        ) as f:
            json.dump(status, f, ensure_ascii=False, indent=2)

    def get_rankings(self, ps_id):
        """Get rankings"""
        return self.api_get(f"/problem-sets/{ps_id}/rankings")

    def get_examinees(self, ps_id):
        """Get examinee list"""
        return self.api_get(f"/problem-sets/{ps_id}/examinees")

    # ==================== Export (answer sheet/transcript/plagiarism) ====================

    def api_post(self, path, json_data=None):
        """Unified POST with auto re-login, 429/403 backoff, adaptive token bucket rate limiting"""
        max_retries = 3
        for attempt in range(max_retries + 1):
            _rate_limiter.acquire()
            with self._session_lock:
                resp = self.session.post(f"{API_BASE}{path}", json=json_data, timeout=30)
            if resp.status_code == 401:
                print("认证失效，重新登录...")
                self.ensure_login()
                _rate_limiter.acquire()
                with self._session_lock:
                    resp = self.session.post(f"{API_BASE}{path}", json=json_data, timeout=30)
            if resp.status_code == 429:
                _rate_limiter.on_rate_limit()
                if attempt < max_retries:
                    retry_after = resp.headers.get("Retry-After")
                    if retry_after and str(retry_after).isdigit():
                        wait = max(1, int(retry_after))
                    else:
                        wait = min(90, 10 * (2 ** attempt))
                    print(f"  429 请求过于频繁，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                print("  429 重试次数已用尽")
            if resp.status_code == 403:
                if attempt < max_retries:
                    wait = min(40, 8 * (attempt + 1))
                    print(f"  403 无权限，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                print("  403 重试次数已用尽")
            resp.raise_for_status()
            _rate_limiter.on_success()
            return resp

    @staticmethod
    def _export_id_from_payload(payload):
        if not isinstance(payload, dict):
            return None
        for key in ("id", "exportId", "export_id"):
            value = payload.get(key)
            if value:
                return str(value)
        for key in ("export", "data", "result"):
            value = PTAClient._export_id_from_payload(payload.get(key))
            if value:
                return value
        return None

    @staticmethod
    def _validate_downloaded_export(path, expected_bytes=None):
        path = Path(path)
        if not path.exists():
            raise RuntimeError(f"downloaded export is missing: {path}")
        actual_bytes = path.stat().st_size
        if actual_bytes <= 0:
            raise RuntimeError(f"downloaded export is empty: {path}")
        if expected_bytes is not None and actual_bytes != expected_bytes:
            raise RuntimeError(
                f"downloaded export is incomplete: {path} "
                f"({actual_bytes}/{expected_bytes} bytes)"
            )
        if any(suffix.lower() in {".zip", ".xlsx"} for suffix in path.suffixes):
            try:
                with zipfile.ZipFile(path, "r") as zf:
                    suffixes = {suffix.lower() for suffix in path.suffixes}
                    if not zf.infolist() and ".xlsx" in suffixes:
                        raise RuntimeError(f"downloaded export has no members: {path}")
            except zipfile.BadZipFile as exc:
                raise RuntimeError(f"downloaded export is not a valid zip/xlsx: {path}") from exc
        return True

    def create_export(self, ps_id, ps_name, export_type="ANSWER_SHEET"):
        """
        创建导出任务。
        export_type:
          ANSWER_SHEET     — 答题卡
          PAPER            — 答卷
          PAPER_TRANSCRIPT — 成绩单
          PAPER_ACCURATE   — 正答率
          SCORED_CODE      — 得分代码
          PAPER_ANALYSIS   — 答卷分析
        """
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")

        type_config = {
            "ANSWER_SHEET": ("答题卡", "exportAnswerSheet"),
            "PAPER": ("答卷", "exportPaper"),
            "PAPER_TRANSCRIPT": ("成绩单", "exportPaperTranscript"),
            "PAPER_ACCURATE": ("正答率", "exportPaperAccurate"),
            "SCORED_CODE": ("得分代码", "exportScoredCode"),
            "PAPER_ANALYSIS": ("答卷分析", "exportPaperAnalysis"),
        }

        if export_type not in type_config:
            raise ValueError(f"不支持的导出类型: {export_type}")

        cn_name, detail_key = type_config[export_type]
        payload = {
            "type": export_type,
            "title": f"{ps_name}-{cn_name}-{timestamp}",
            "detail": {detail_key: {"problemSetId": ps_id}},
        }

        print(f"  创建导出任务: {payload['title']}")
        resp = self.api_post("/exports", json_data=payload)
        result = resp.json() if resp.text else {}
        if not isinstance(result, dict):
            result = {"raw": result}
        result["_requested_title"] = payload["title"]
        result["_requested_at"] = time.time()
        result["_requested_type"] = export_type
        return result

    def wait_export_ready(
        self,
        ps_id,
        export_type="ANSWER_SHEET",
        timeout=None,
        export_marker=None,
    ):
        """
        轮询等待导出任务完成，返回 docUrl 下载链接。
        status: WAITING → READY 表示完成
        """
        timeout = timeout or EXPORT_READY_TIMEOUT_SECONDS
        filter_param = json.dumps({"problemSetId": ps_id})
        start = time.time()
        expected_id = self._export_id_from_payload(export_marker or {})
        expected_title = (export_marker or {}).get("_requested_title")

        while time.time() - start < timeout:
            data = self.api_get("/exports", params={
                "page": 0, "limit": 20,
                "filter": filter_param,
            })
            exports = data.get("exports", [])
            for exp in exports:
                if exp.get("type") != export_type:
                    continue
                exp_id = self._export_id_from_payload(exp)
                exp_title = exp.get("title") or exp.get("name")
                if expected_id and exp_id and exp_id != expected_id:
                    continue
                if expected_title and exp_title != expected_title:
                    continue
                status = exp.get("status")
                if status == "FAILED":
                    raise RuntimeError(f"PTA export failed: {expected_title or export_type}")
                if status == "READY":
                    doc_url = exp.get("docUrl", "")
                    if doc_url:
                        print(f"\n  导出完成，获取到下载链接")
                        return doc_url
            elapsed = time.time() - start
            print(f"  等待导出完成... ({int(elapsed)}s)", end="\r")
            _export_poll_sleep(elapsed)

        raise TimeoutError(f"PTA export timed out: {expected_title or export_type} ({timeout}s)")

    def download_export(self, download_url, save_path):
        """Download an export, tolerating READY-before-object-visible races."""
        resp = None
        for attempt in range(1, EXPORT_DOWNLOAD_RETRIES + 1):
            # Try download without cookie first. COS signed URLs do not need the
            # PTA session and forwarding it can cause signature/header issues.
            resp = requests.get(download_url, stream=True, timeout=120)
            if resp.status_code == 403:
                # Some COS responses require PTA as the referrer.
                resp.close()
                resp = requests.get(
                    download_url,
                    stream=True,
                    timeout=120,
                    headers={"Referer": "https://pintia.cn/"},
                )
            if resp.status_code not in {404, 408, 429, 500, 502, 503, 504}:
                break
            if attempt >= EXPORT_DOWNLOAD_RETRIES:
                break
            status_code = resp.status_code
            resp.close()
            delay = EXPORT_DOWNLOAD_RETRY_DELAY_SECONDS * attempt
            print(
                f"  导出文件暂不可用 (HTTP {status_code})，"
                f"{delay:g}s 后重试同一下载地址 "
                f"({attempt + 1}/{EXPORT_DOWNLOAD_RETRIES})"
            )
            if delay > 0:
                time.sleep(delay)

        if resp is None:
            raise RuntimeError("PTA export download returned no response")
        resp.raise_for_status()
        os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
        expected_bytes = resp.headers.get("Content-Length")
        expected_bytes = int(expected_bytes) if expected_bytes and expected_bytes.isdigit() else None
        tmp_path = f"{save_path}.part-{os.getpid()}-{int(time.time() * 1000)}"
        bytes_written = 0
        try:
            with open(tmp_path, "wb") as f:
                for chunk in resp.iter_content(chunk_size=1024 * 1024):
                    if not chunk:
                        continue
                    f.write(chunk)
                    bytes_written += len(chunk)
                f.flush()
                os.fsync(f.fileno())
            self._validate_downloaded_export(tmp_path, expected_bytes)
            os.replace(tmp_path, save_path)
        except Exception:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
            raise
        size_mb = os.path.getsize(save_path) / 1024 / 1024
        print(f"  已下载: {save_path} ({size_mb:.1f}MB, {bytes_written} bytes)")

    def export_and_download(self, ps_id, ps_name, export_type="ANSWER_SHEET", save_dir="./答题情况", max_retries=3):
        """
        One-click export and download: create task -> wait READY -> download via docUrl.
        403 重试: PTA 导出 API 有时首次请求返回 403（权限缓存延迟），
        等待后重试通常能成功。最多重试 max_retries 次。
        """
        os.makedirs(save_dir, exist_ok=True)

        for attempt in range(max_retries):
            try:
                if attempt > 0:
                    wait = 10 * attempt
                    print(f"  403 重试 ({attempt}/{max_retries})，等待 {wait}s...")
                    time.sleep(wait)

                export_marker = self.create_export(ps_id, ps_name, export_type)
                if EXPORT_CREATE_DELAY_SECONDS > 0:
                    time.sleep(EXPORT_CREATE_DELAY_SECONDS)

                doc_url = self.wait_export_ready(ps_id, export_type, export_marker=export_marker)
                if doc_url:
                    # Determine file extension from docUrl
                    if doc_url.endswith(".xlsx") or ".xlsx?" in doc_url:
                        ext = ".xlsx"
                    else:
                        ext = ".zip"
                    save_path = os.path.join(save_dir, f"{ps_name}-{export_type}{ext}")
                    self.download_export(doc_url, save_path)
                    self._validate_downloaded_export(save_path)
                    return save_path
                raise RuntimeError(f"PTA export did not produce a download URL: {ps_name} {export_type}")

            except requests.exceptions.HTTPError as e:
                if e.response is not None and e.response.status_code == 403:
                    if attempt < max_retries - 1:
                        print(f"  导出 403 无权限（可能是延迟），将重试...")
                        continue
                    else:
                        print(f"  导出 403 无权限，{max_retries}次重试均失败，跳过")
                        raise
                else:
                    raise  # 非 403 错误直接抛出

        return None

    def _resolve_user_group_id(self, group_id=None, group_name=None):
        if group_id:
            return str(group_id), group_name
        if not group_name:
            return None, group_name
        group = self.find_user_group(group_name)
        if not group:
            raise RuntimeError(f"PTA user group not found: {group_name}")
        return str(group.get("id")), group.get("name") or group_name

    def _group_answer_export_payloads(self, group_id, title):
        group_id = str(group_id)
        return [
            {
                "type": "USER_GROUP_PAPER",
                "title": title,
                "detail": {"exportUserGroupPaper": {"userGroupId": group_id}},
            },
            {
                "type": "ANSWER_SHEET",
                "title": title,
                "detail": {"exportAnswerSheet": {"userGroupId": group_id}},
            },
        ]

    def create_group_answer_sheet_export(self, group_id, group_name=None):
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")
        title = f"{group_name or group_id}-用户组答卷-{timestamp}"
        errors = []
        for payload in self._group_answer_export_payloads(group_id, title):
            try:
                print(f"  创建用户组答卷导出任务: {payload['title']}")
                resp = self.api_post("/exports", json_data=payload)
                result = resp.json() if resp.text else {}
                if not isinstance(result, dict):
                    result = {"raw": result}
                result["_requested_title"] = title
                result["_requested_at"] = time.time()
                result["_requested_type"] = payload.get("type")
                result["_requested_group_id"] = str(group_id)
                return result
            except requests.exceptions.HTTPError as exc:
                status = exc.response.status_code if exc.response is not None else "unknown"
                errors.append(f"{status}: {payload.get('detail')}")
                if status not in (400, 403, 404, 422):
                    raise
        raise RuntimeError(f"failed to create PTA user-group answer export; tried: {'; '.join(errors)}")

    def wait_group_answer_export_ready(self, group_id, export_marker, timeout=None):
        timeout = timeout or GROUP_EXPORT_READY_TIMEOUT_SECONDS
        start = time.time()
        expected_id = self._export_id_from_payload(export_marker or {})
        expected_title = (export_marker or {}).get("_requested_title")
        expected_type = (export_marker or {}).get("_requested_type") or "USER_GROUP_PAPER"
        filter_candidates = [
            {"userGroupId": str(group_id)},
            {"groupId": str(group_id)},
            {},
        ]
        # 一旦在某 filter 下匹配到目标导出任务，后续轮询只查该 filter，
        # 避免每轮都跑 3 个 filter 候选（每次都是一次 api_get，消耗令牌桶配额）
        effective_filter = None
        seen = []

        while time.time() - start < timeout:
            candidates = [effective_filter] if effective_filter is not None else filter_candidates
            for filter_obj in candidates:
                data = self.api_get("/exports", params={
                    "page": 0,
                    "limit": 20,
                    "filter": json.dumps(filter_obj, ensure_ascii=False),
                })
                found = False
                for exp in data.get("exports", []):
                    if exp.get("type") != expected_type:
                        continue
                    exp_id = self._export_id_from_payload(exp)
                    exp_title = exp.get("title") or exp.get("name")
                    seen.append(exp_title)
                    if expected_id and exp_id and exp_id != expected_id:
                        continue
                    if expected_title and exp_title != expected_title:
                        continue
                    # 匹配到目标导出任务，锁定该 filter 并结束本轮遍历
                    found = True
                    effective_filter = filter_obj
                    status = exp.get("status")
                    if status == "FAILED":
                        raise RuntimeError(f"PTA user-group answer export failed: {expected_title}")
                    if status == "READY":
                        doc_url = exp.get("docUrl", "")
                        if doc_url:
                            print("\n  用户组答卷导出完成，获取到下载链接")
                            return doc_url
                    break
                if found:
                    break  # 已锁定 filter，下一轮只查它
            elapsed = time.time() - start
            print(f"  等待用户组答卷导出完成... ({int(elapsed)}s)", end="\r")
            _export_poll_sleep(elapsed)

        sample = ", ".join(str(x) for x in seen[:5])
        raise TimeoutError(f"PTA user-group answer export timed out: {expected_title}; seen: {sample}")

    def export_group_answer_sheets_with_retry(
        self,
        group_id: str | None = None,
        group_name: str | None = None,
        crawl_dir: Path | None = None,
    ) -> object:
        """Create a fresh group-answer export for every retry attempt.

        PTA returns a short-lived COS URL for the export. A failed download must
        therefore create a new export task instead of retrying the stale URL.
        """
        # A group export can temporarily return 400 while PTA is still
        # releasing a previous task for the same group. Unlike ordinary API
        # validation errors, recreating this export after a delay is safe.
        retryable_statuses = {400, 404, 408, 429, 500, 502, 503, 504}
        attempts = max(1, EXPORT_RETRY_ROUNDS + 1)
        last_error: Exception | None = None

        for attempt in range(1, attempts + 1):
            if attempt > 1 and EXPORT_RETRY_DELAY_SECONDS > 0:
                delay = EXPORT_RETRY_DELAY_SECONDS
                print(
                    f"  用户组答卷导出将在 {delay}s 后重新创建任务 "
                    f"({attempt}/{attempts})"
                )
                time.sleep(delay)

            try:
                # This call creates a new PTA export task and obtains a new
                # signed COS URL on every attempt.
                return self.export_group_answer_sheets(
                    group_id=group_id,
                    group_name=group_name,
                    crawl_dir=crawl_dir,
                )
            except Exception as exc:
                last_error = exc
                response = getattr(exc, "response", None)
                status_code = getattr(response, "status_code", None)
                status_codes = (
                    {int(value) for value in re.findall(r"\b(\d{3})\b", str(exc))}
                    if status_code is None
                    else {int(status_code)}
                )
                retryable_codes = sorted(status_codes & retryable_statuses)
                retryable = bool(retryable_codes)
                if not retryable or attempt >= attempts:
                    raise
                print(
                    f"  用户组答卷导出第 {attempt} 次失败 "
                    f"(HTTP {','.join(map(str, retryable_codes)) or 'unknown'}): {exc}"
                )

        raise last_error or RuntimeError("group answer export failed")

    def export_group_answer_sheets(self, group_id=None, group_name=None, crawl_dir=None):
        group_id, group_name = self._resolve_user_group_id(group_id, group_name)
        if not group_id:
            raise RuntimeError("PTA user group id or exact user group name is required for group answer export")
        crawl_dir = Path(crawl_dir or self.crawl_dir)
        save_dir = crawl_dir / "_group_exports"
        save_dir.mkdir(parents=True, exist_ok=True)

        marker = self.create_group_answer_sheet_export(group_id, group_name)
        if EXPORT_CREATE_DELAY_SECONDS > 0:
            time.sleep(EXPORT_CREATE_DELAY_SECONDS)
        doc_url = self.wait_group_answer_export_ready(group_id, marker)
        title = marker.get("_requested_title") or f"{group_name or group_id}-用户组答卷"
        save_path = save_dir / f"{title}.zip"
        self.download_export(doc_url, str(save_path))
        self._validate_downloaded_export(str(save_path))
        summary = inspect_group_answer_export(save_path)
        if summary.get("experiment_count", 0) <= 0:
            raise RuntimeError(f"PTA user-group answer export has no student answer sheets: {save_path}")
        split_result = split_group_answer_export(
            save_path,
            crawl_dir,
            group_name=group_name,
            overwrite=True,
        )
        if not split_result.get("written"):
            raise RuntimeError(f"PTA user-group answer export produced no per-experiment answer sheets: {save_path}")
        print(f"  用户组答卷已拆分为 {len(split_result['written'])} 个实验 ANSWER_SHEET.zip")
        return split_result

    # ==================== Incremental Crawl Core ====================

    def crawl_incremental(self, group_id=None, group_name=None):
        """
        增量爬取：自动检测新题目集，只处理新增的。
        """
        group_id = group_id or os.getenv("PTA_GROUP_ID")
        group_name = group_name or os.getenv("PTA_GROUP_NAME")
        label = group_name or group_id
        if not label:
            raise RuntimeError("PTA user group id or exact user group name is required")
        print(f"\n{'='*50}")
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 开始增量爬取用户组: {label}")
        print(f"{'='*50}")

        # 1. ensure login
        if not self.ensure_login():
            print("登录失败，本次爬取终止")
            return

        # 2. search all problem sets
        roster_payload = self.write_user_group_roster(group_id=group_id, group_name=group_name)
        group_id = roster_payload["group"]["pta_group_id"]
        group_name = roster_payload["group"]["pta_group_name"]

        all_sets = self.search_problem_sets(group_id=group_id, group_name=group_name)
        if not all_sets:
            print("未搜索到任何题目集")
            return

        # 3. filter new sets
        new_sets = self.get_sets_requiring_content(all_sets)
        if not new_sets:
            print("没有新的题目集，本次无需爬取")
            return

        print(f"发现 {len(new_sets)} 个新题目集:")
        for ps in new_sets:
            print(f"  - {ps.get('name', '未知')}")

        # 4. crawl each new set (optionally parallel); answer sheets once at user-group level.
        completed_sets = []
        workers = min(PROBLEM_SET_MAX_WORKERS, max(1, len(new_sets)))

        def _crawl_one(ps):
            ps_id = ps.get("id", "")
            ps_name = ps.get("name", "未知")
            print(f"\n--- 正在爬取: {ps_name} ---")
            self._write_problem_set_info(ps_id, ps_name, ps)
            self._crawl_one_problem_set(ps_id, ps_name, export_answer_sheet=False)
            print(f"完成: {ps_name}")
            return ps

        if workers <= 1:
            for ps in new_sets:
                try:
                    completed_sets.append(_crawl_one(ps))
                except Exception as e:
                    print(f"爬取 {ps.get('name', '未知')} 失败: {e}")
        else:
            print(f"题集并行爬取: workers={workers}, sets={len(new_sets)}")
            with ThreadPoolExecutor(max_workers=workers) as pool:
                futures = {pool.submit(_crawl_one, ps): ps for ps in new_sets}
                for fut in as_completed(futures):
                    ps = futures[fut]
                    try:
                        completed_sets.append(fut.result())
                    except Exception as e:
                        print(f"爬取 {ps.get('name', '未知')} 失败: {e}")

        if completed_sets:
            try:
                self.export_group_answer_sheets_with_retry(group_id=group_id, group_name=group_name)
                for ps in completed_sets:
                    self.history.mark_crawled(ps.get("id", ""), ps.get("name", ""))
            except Exception as e:
                print(f"用户组答卷导出失败，本次不写入数据库: {e}")
                return

        print(f"\n本次增量爬取完成，处理了 {len(completed_sets)}/{len(new_sets)} 个新题目集")

        # 自动同步到数据库
        self._auto_sync_to_db()

    def _required_export_configs(self, export_answer_sheet=False, answer_sheet_index=0):
        export_configs = [
            ("PAPER_TRANSCRIPT", "成绩单"),
            ("SCORED_CODE", "得分代码"),
        ]
        if export_answer_sheet:
            export_configs.insert(answer_sheet_index, ("ANSWER_SHEET", "答题卡"))
        return export_configs

    def _format_export_failure(self, cn_name, exc):
        if isinstance(exc, requests.exceptions.HTTPError) and exc.response is not None:
            status_code = exc.response.status_code
            if status_code == 403:
                return f"导出{cn_name}: 无权限/签名未就绪"
            if status_code == 429:
                return f"导出{cn_name}: PTA 限流"
            return f"导出{cn_name}: HTTP {status_code} {exc}"
        return f"导出{cn_name}: {exc}"

    def _export_required_files(self, ps_id, ps_name, export_dir, export_configs):
        pending = list(export_configs)
        attempts = defaultdict(list)
        max_round = EXPORT_RETRY_ROUNDS + 1
        use_parallel = EXPORT_PARALLEL and len(pending) > 1

        for round_no in range(1, max_round + 1):
            if round_no > 1:
                names = ", ".join(cn_name for _, cn_name in pending)
                print(f"  集中重试 PTA 导出 ({round_no}/{max_round}): {ps_name} -> {names}")
                if EXPORT_RETRY_DELAY_SECONDS > 0:
                    time.sleep(EXPORT_RETRY_DELAY_SECONDS)

            failed = []
            if use_parallel:
                def _one(item):
                    export_type, cn_name = item
                    try:
                        self.export_and_download(ps_id, ps_name, export_type, str(export_dir))
                        return None
                    except Exception as exc:
                        return (item, self._format_export_failure(cn_name, exc), exc)

                workers = min(4, len(pending))
                with ThreadPoolExecutor(max_workers=workers) as pool:
                    results = list(pool.map(_one, pending))
                for result in results:
                    if result is None:
                        continue
                    item, message, exc = result
                    print(f"  {message}")
                    attempts[item].append(message)
                    # A timed-out task is still queued on PTA. Creating another
                    # task cannot accelerate it and only leaves duplicate export
                    # records, so let the caller resume/check it later.
                    if isinstance(exc, TimeoutError):
                        raise exc
                    failed.append(item)
                    if (
                        isinstance(exc, requests.exceptions.HTTPError)
                        and exc.response is not None
                        and exc.response.status_code == 429
                    ):
                        _rate_limiter.on_rate_limit()
                        time.sleep(min(30, 10 * round_no))
            else:
                for export_type, cn_name in pending:
                    try:
                        self.export_and_download(ps_id, ps_name, export_type, str(export_dir))
                        if EXPORT_BETWEEN_DELAY_SECONDS > 0:
                            time.sleep(EXPORT_BETWEEN_DELAY_SECONDS)
                    except Exception as exc:
                        message = self._format_export_failure(cn_name, exc)
                        print(f"  {message}")
                        attempts[(export_type, cn_name)].append(message)
                        if isinstance(exc, TimeoutError):
                            raise
                        failed.append((export_type, cn_name))
                        if (
                            isinstance(exc, requests.exceptions.HTTPError)
                            and exc.response is not None
                            and exc.response.status_code == 429
                        ):
                            _rate_limiter.on_rate_limit()
                            time.sleep(min(30, 10 * round_no))

            if not failed:
                if round_no > 1:
                    print(f"  PTA 导出补跑成功: {ps_name}")
                return
            pending = failed

        failed_names = [cn_name for _, cn_name in pending]
        details = []
        for key in pending:
            messages = attempts.get(key) or []
            details.append(f"{key[1]}({messages[-1]})" if messages else key[1])
        print(f"  PTA 导出最终失败点: {ps_name} -> {'; '.join(details)}")
        raise RuntimeError(
            f"required PTA exports failed after {EXPORT_RETRY_ROUNDS} retry round(s): "
            f"{', '.join(failed_names)}"
        )

    def _crawl_one_problem_set(self, ps_id, ps_name, export_answer_sheet=False):
        """Crawl all data for a single problem set, save to ./爬取结果/"""
        base_dir = self._problem_set_dir(ps_name)

        # 1. Crawl problem content (题目详情并发拉取，写文件保持原顺序)
        try:
            problems = []
            seen_problem_ids = set()
            for problem_type in CRAWL_PROBLEM_TYPES:
                for problem in self.get_problems(
                    ps_id,
                    problem_type=problem_type,
                ):
                    problem = dict(problem)
                    problem.setdefault("problemType", problem_type)
                    problem_id = str(problem.get("id") or "").strip()
                    dedup_key = problem_id or json.dumps(
                        problem,
                        sort_keys=True,
                        ensure_ascii=False,
                    )
                    if dedup_key in seen_problem_ids:
                        continue
                    seen_problem_ids.add(dedup_key)
                    problems.append(problem)
            print(f"  题目数量: {len(problems)}")
            # 并发拉取题目详情；api_get 内令牌桶限流器保证请求频率安全
            def _fetch_detail(p):
                pid = p.get("id", "")
                if not pid:
                    return ValueError("problem id is missing")
                try:
                    return self.get_problem_detail(ps_id, pid)
                except Exception as e:
                    return e

            details = []
            if problems:
                max_workers = min(DETAIL_MAX_WORKERS, max(1, len(problems)))
                with ThreadPoolExecutor(max_workers=max_workers) as pool:
                    details = list(pool.map(_fetch_detail, problems))

            detail_records = []
            failed_problem_ids = []
            invalid_content_problem_ids = []
            with open(base_dir / "题目内容.txt", "w", encoding="utf-8") as f:
                for p, detail in zip(problems, details):
                    title = p.get("title", "")
                    label = p.get("label", "")
                    f.write(f"[{label}] {title}\n")
                    pid = str(p.get("id") or "").strip()
                    if isinstance(detail, Exception):
                        failed_problem_ids.append(pid or "<missing-id>")
                        f.write(f"(获取详情失败: {detail})\n")
                    elif detail:
                        record = self._problem_detail_record(ps_id, p, detail)
                        detail_records.append(record)
                        content = record.get("content_md") or record.get("content_html") or ""
                        if not self._problem_record_has_valid_content(record):
                            failed_problem_ids.append(pid or "<missing-id>")
                            invalid_content_problem_ids.append(pid or "<missing-id>")
                            f.write("(problem content is empty or only a placeholder)\n")
                        else:
                            f.write(f"{content}\n")
                    else:
                        failed_problem_ids.append(pid or "<missing-id>")
                        f.write("(获取详情失败: empty response)\n")
                    f.write(f"\n{'='*40}\n\n")
            with open(base_dir / "题目详情.json", "w", encoding="utf-8") as f:
                json.dump(detail_records, f, ensure_ascii=False, indent=2)
            with open(base_dir / "problem_crawl_status.json", "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "problem_set_id": str(ps_id),
                        "problem_type_filter": list(CRAWL_PROBLEM_TYPES),
                        "listed_problem_count": len(problems),
                        "detail_problem_count": len(detail_records),
                        "failed_problem_ids": failed_problem_ids,
                        "invalid_content_problem_ids": invalid_content_problem_ids,
                        "complete": not failed_problem_ids,
                    },
                    f,
                    ensure_ascii=False,
                    indent=2,
                )
            print(f"  题目详情: {len(detail_records)} 条")
            if failed_problem_ids:
                raise RuntimeError(
                    f"{len(failed_problem_ids)} problem detail(s) failed: "
                    f"{', '.join(failed_problem_ids[:10])}"
                )
        except Exception as e:
            print(f"  获取题目列表失败: {e}")
            raise

        # 2. Crawl submissions
        try:
            submissions = self.get_all_submissions(ps_id)
            self.write_submission_crawl_status(ps_id, base_dir)
            submission_status = getattr(self, "_submission_crawl_status", {}).get(
                str(ps_id),
                {},
            )
            if submission_status and submission_status.get("complete") is not True:
                raise RuntimeError(
                    f"submission crawl is incomplete for problem set {ps_id}: "
                    f"{len(submission_status.get('incomplete_user_ids') or [])} "
                    f"user(s) still hit the PTA 200-row limit"
                )
            if submissions:
                import csv
                with open(base_dir / "提交记录.csv", "w", encoding="utf-8", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(
                        [
                            "提交ID",
                            "用户ID",
                            "题目ID",
                            "题型",
                            "状态",
                            "分数",
                            "编译器",
                            "用时",
                            "内存",
                            "提交时间",
                        ]
                    )
                    for sub in submissions:
                        writer.writerow([
                            sub.get("id", ""),
                            sub.get("userId", ""),
                            sub.get("problemSetProblemId", ""),
                            sub.get("problemType", ""),
                            sub.get("status", ""),
                            sub.get("score", ""),
                            sub.get("compiler", ""),
                            sub.get("time", ""),
                            sub.get("memory", ""),
                            sub.get("submitAt", ""),
                        ])
                print(f"  提交记录: {len(submissions)} 条")
        except Exception as e:
            print(f"  获取提交记录失败: {e}")
            raise

        # A preview may expose only one draw from a randomized problem pool.
        # Submission rows reveal additional problem-set-problem ids, so fetch
        # those details explicitly before declaring the problem set complete.
        known_problem_ids = {
            str(record.get("problem_set_problem_id") or "").strip()
            for record in detail_records
        }
        evidence_problem_ids = []
        ignored_non_programming_evidence_ids = []
        seen_evidence_ids = set()
        for submission in submissions:
            problem_id = str(submission.get("problemSetProblemId") or "").strip()
            if not problem_id or problem_id == "0" or problem_id in seen_evidence_ids:
                continue
            seen_evidence_ids.add(problem_id)
            problem_type = str(submission.get("problemType") or "").strip().upper()
            if problem_type and problem_type not in CRAWL_PROBLEM_TYPES:
                ignored_non_programming_evidence_ids.append(problem_id)
                continue
            evidence_problem_ids.append(problem_id)
        missing_evidence_ids = [
            problem_id
            for problem_id in evidence_problem_ids
            if problem_id not in known_problem_ids
        ]
        evidence_failures = []
        evidence_invalid_content_ids = []
        if missing_evidence_ids:
            def _fetch_evidence_detail(problem_id):
                try:
                    return self.get_problem_detail(ps_id, problem_id)
                except Exception as exc:
                    return exc

            max_workers = min(DETAIL_MAX_WORKERS, len(missing_evidence_ids))
            with ThreadPoolExecutor(max_workers=max_workers) as pool:
                evidence_details = list(pool.map(_fetch_evidence_detail, missing_evidence_ids))
            for problem_id, detail in zip(missing_evidence_ids, evidence_details):
                if isinstance(detail, Exception) or not detail:
                    evidence_failures.append(problem_id)
                    continue
                record = self._problem_detail_record(
                    ps_id,
                    {"id": problem_id},
                    detail,
                )
                detail_records.append(record)
                if not self._problem_record_has_valid_content(record):
                    evidence_failures.append(problem_id)
                    evidence_invalid_content_ids.append(problem_id)

            with open(base_dir / "题目内容.txt", "w", encoding="utf-8") as f:
                for record in detail_records:
                    f.write(f"[{record.get('problem_label', '')}] {record.get('title', '')}\n")
                    content = record.get("content_md") or record.get("content_html") or ""
                    f.write(f"{content}\n\n{'='*40}\n\n")
            with open(base_dir / "题目详情.json", "w", encoding="utf-8") as f:
                json.dump(detail_records, f, ensure_ascii=False, indent=2)
            print(
                f"  提交记录补充题目详情: "
                f"+{len(missing_evidence_ids) - len(evidence_failures)} 条"
            )

        with open(base_dir / "problem_crawl_status.json", "w", encoding="utf-8") as f:
            json.dump(
                {
                    "problem_set_id": str(ps_id),
                    "problem_type_filter": list(CRAWL_PROBLEM_TYPES),
                    "listed_problem_count": len(problems),
                    "submission_evidence_problem_count": len(evidence_problem_ids),
                    "ignored_non_programming_evidence_problem_ids": (
                        ignored_non_programming_evidence_ids
                    ),
                    "detail_problem_count": len(detail_records),
                    "failed_problem_ids": evidence_failures,
                    "invalid_content_problem_ids": evidence_invalid_content_ids,
                    "complete": not evidence_failures,
                },
                f,
                ensure_ascii=False,
                indent=2,
            )
        if evidence_failures:
            raise RuntimeError(
                f"{len(evidence_failures)} evidence problem detail(s) failed: "
                f"{', '.join(evidence_failures[:10])}"
            )

        # 3. Export essential types only (PAPER/PAPER_ACCURATE/PAPER_ANALYSIS are redundant/computable)
        export_dir = base_dir / "导出"
        self._export_required_files(
            ps_id,
            ps_name,
            export_dir,
            self._required_export_configs(export_answer_sheet, answer_sheet_index=0),
        )
        if EXPORT_BETWEEN_DELAY_SECONDS > 0:
            time.sleep(min(EXPORT_BETWEEN_DELAY_SECONDS, 1.0))
        return {
            "problem_count": len(detail_records),
            "problem_detail_count": len(detail_records),
            "submission_count": len(submissions),
        }

    def _refresh_one_problem_set(self, ps_id, ps_name, export_answer_sheet=False):
        """
        刷新已爬取题目集的导出数据（不重新爬取题目内容和提交记录）。
        只重新导出: PAPER_TRANSCRIPT, SCORED_CODE；ANSWER_SHEET 可由用户组总导出替代
        """
        base_dir = self._problem_set_dir(ps_name)
        export_dir = base_dir / "导出"
        export_dir.mkdir(parents=True, exist_ok=True)

        self._export_required_files(
            ps_id,
            ps_name,
            export_dir,
            self._required_export_configs(export_answer_sheet, answer_sheet_index=1),
        )

    def refresh_exports(self, group_id=None, group_name=None):
        """
        刷新所有已爬取题目集的导出数据（成绩单/答题卡/得分代码）。
        用于学生持续提交后，重新拉取最新数据覆盖旧文件。
        不重新爬取题目内容（题目发布后不变）。
        """
        group_id = group_id or os.getenv("PTA_GROUP_ID")
        group_name = group_name or os.getenv("PTA_GROUP_NAME")
        label = group_name or group_id
        if not label:
            raise RuntimeError("PTA user group id or exact user group name is required")
        print(f"\n{'='*50}")
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 刷新用户组导出数据: {label}")
        print(f"{'='*50}")

        if not self.ensure_login():
            print("登录失败，本次刷新终止")
            return 0

        # 搜索所有题目集（需要获取 ps_id）
        roster_payload = self.write_user_group_roster(group_id=group_id, group_name=group_name)
        group_id = roster_payload["group"]["pta_group_id"]
        group_name = roster_payload["group"]["pta_group_name"]

        all_sets = self.search_problem_sets(group_id=group_id, group_name=group_name)
        if not all_sets:
            print("未搜索到任何题目集")
            return 0

        # 只刷新已爬取过的题目集
        crawled = self.history.get_all_crawled()
        to_refresh = [ps for ps in all_sets if ps.get("id", "") in crawled]

        if not to_refresh:
            print("没有需要刷新的题目集")
            return 0

        print(f"将刷新 {len(to_refresh)} 个题目集的导出数据:")
        for ps in to_refresh:
            print(f"  - {ps.get('name', '未知')}")

        refreshed_sets = []
        workers = min(PROBLEM_SET_MAX_WORKERS, max(1, len(to_refresh)))

        def _refresh_one(ps):
            ps_id = ps.get("id", "")
            ps_name = ps.get("name", "未知")
            print(f"\n--- 刷新导出: {ps_name} ---")
            self._write_problem_set_info(ps_id, ps_name, ps)
            self._refresh_one_problem_set(ps_id, ps_name, export_answer_sheet=False)
            print(f"完成: {ps_name}")
            return ps

        if workers <= 1:
            for ps in to_refresh:
                try:
                    refreshed_sets.append(_refresh_one(ps))
                except Exception as e:
                    print(f"刷新 {ps.get('name', '未知')} 失败: {e}")
        else:
            print(f"题集并行刷新导出: workers={workers}, sets={len(to_refresh)}")
            with ThreadPoolExecutor(max_workers=workers) as pool:
                futures = {pool.submit(_refresh_one, ps): ps for ps in to_refresh}
                for fut in as_completed(futures):
                    ps = futures[fut]
                    try:
                        refreshed_sets.append(fut.result())
                    except Exception as e:
                        print(f"刷新 {ps.get('name', '未知')} 失败: {e}")

        refreshed = len(refreshed_sets)
        if refreshed_sets:
            try:
                self.export_group_answer_sheets_with_retry(group_id=group_id, group_name=group_name)
                for ps in refreshed_sets:
                    self.history.mark_export_refreshed(ps.get("id", ""))
            except Exception as e:
                print(f"用户组答卷导出失败，本次不写入数据库: {e}")
                return 0

        print(f"\n导出数据刷新完成，处理了 {refreshed}/{len(to_refresh)} 个题目集")

        # 自动同步到数据库
        self._auto_sync_to_db()

        return refreshed


# ==================== Multi-Account Config ====================

def load_accounts():
    """
    从 accounts.json 加载多个教师账号配置。
    格式示例:
    [
        {"username": "teacher1@xxx.com", "password": "xxx", "group_name": "计科25数据结构"},
        {"username": "teacher2@xxx.com", "password": "xxx", "group_id": "2028307022170722304"}
    ]
    如果没有 accounts.json，就用 .env 里的单账号。
    """
    accounts_file = "accounts.json"
    if os.path.exists(accounts_file):
        with open(accounts_file, "r", encoding="utf-8") as f:
            return json.load(f)
    # Fallback to .env single account
    return [{
        "username": os.getenv("PTA_USERNAME"),
        "password": os.getenv("PTA_PASSWORD") or os.getenv("PTA_PASSPORT"),
        "group_id": os.getenv("PTA_GROUP_ID"),
        "group_name": os.getenv("PTA_GROUP_NAME"),
    }]


def run_once(mode="incremental"):
    """
    Run one crawl cycle (all accounts).
    mode:
      - "incremental": 只爬取新题目集（含题目内容+导出）
      - "refresh": 刷新所有已爬取题目集的导出数据
      - "full": 先增量爬新的，再刷新所有已有的导出
    """
    accounts = load_accounts()
    for acc in accounts:
        try:
            client = PTAClient(acc["username"], acc["password"])
            group_id = acc.get("group_id")
            group_name = acc.get("group_name")
            if mode in ("incremental", "full"):
                client.crawl_incremental(group_id=group_id, group_name=group_name)
            if mode in ("refresh", "full"):
                client.refresh_exports(group_id=group_id, group_name=group_name)
        except Exception as e:
            print(f"账号 {acc['username']} 爬取失败: {e}")


def run_scheduled(interval_hours=24):
    """
    定时循环执行，默认每 24 小时跑一次 full 模式（增量+刷新）。
    也可以不用这个，直接用 Windows 任务计划程序调 run_once()。
    """
    import schedule

    print(f"定时任务已启动，每 {interval_hours} 小时执行一次 (full 模式)")
    print(f"下次执行时间: 立即执行第一次\n")

    schedule.every(interval_hours).hours.do(lambda: run_once(mode="full"))
    run_once(mode="full")  # Run immediately first

    while True:
        schedule.run_pending()
        time.sleep(60)


def _pta_ensure_login_override(self):
    manual_cookie_path = str(RUNTIME_DIR / "manual_cookies.json")

    def load_cookie_list(cookie_list):
        for c in cookie_list:
            name = c.get("name", c.get("Name", ""))
            value = c.get("value", c.get("Value", ""))
            domain = c.get("domain", c.get("Domain", ".pintia.cn"))
            if name and value:
                self.session.cookies.set(name, value, domain=domain)

    def try_manual_cookie_file():
        if not os.path.exists(manual_cookie_path):
            return False
        print(f"尝试从手动 cookie 文件恢复: {manual_cookie_path}")
        with open(manual_cookie_path, "r", encoding="utf-8") as f:
            manual_cookies = json.load(f)
        load_cookie_list(manual_cookies)
        if self._check_cookie_valid():
            self._save_cookies(manual_cookies)
            print("手动 cookie 有效，已保存")
            self._notify_cookie_status("OK")
            return True
        print("手动 cookie 已过期")
        return False

    if self.force_selenium_login:
        print("PTA_FORCE_SELENIUM_LOGIN=true，跳过缓存 Cookie，直接尝试 Selenium 登录...")
    else:
        cached = self._load_cookies()
        if cached:
            load_cookie_list(cached)
            if self._check_cookie_valid():
                self._notify_cookie_status("OK")
                return True
            print("缓存 Cookie 已过期，先尝试手动 cookie，再尝试 Selenium 登录...")

        if try_manual_cookie_file():
            return True

    has_login_credentials = bool((self.username or "").strip()) and bool(self.password)
    if not has_login_credentials:
        error_msg = (
            "PTA credentials are not available; bind credentials in the teacher profile, "
            "submit temporary credentials for this sync, or update the PTA cookie manually."
        )
        self._notify_cookie_status("EXPIRED", error_msg)
        print(error_msg)
        return False

    login_attempts = max(1, int(os.getenv("PTA_SELENIUM_LOGIN_ATTEMPTS", "1")))
    retry_delay = max(0, int(os.getenv("PTA_SELENIUM_RETRY_DELAY_SECONDS", "5")))
    last_error = None
    for attempt in range(1, login_attempts + 1):
        delay = retry_delay
        try:
            print(f"Selenium 登录尝试 {attempt}/{login_attempts}...")
            self._selenium_login()
            if self._check_cookie_valid():
                print("Selenium 登录成功")
                self._notify_cookie_status("OK")
                return True
            print("Selenium 登录后 cookie 仍无效")
        except Exception as e:
            last_error = str(e)
            print(f"Selenium 尝试 {attempt} 失败: {e}")
        if attempt < login_attempts and retry_delay > 0:
            print(f"等待 {delay}s 后重试...")
            time.sleep(retry_delay)

    if try_manual_cookie_file():
        return True

    error_msg = last_error or "所有登录方式均失败"
    self._notify_cookie_status("EXPIRED", error_msg)
    print("=" * 50)
    print("自动登录全部失败，已通知系统。")
    print("教师可在“班级管理 -> PTA同步设置”中手动更新 Cookie。")
    print("=" * 50)
    return False


def _pta_sync_driver_cookies_to_session(self):
    selenium_cookies = self.driver.get_cookies()
    for cookie in selenium_cookies:
        name = cookie.get("name")
        value = cookie.get("value")
        if not name or not value:
            continue
        self.session.cookies.set(
            name,
            value,
            domain=cookie.get("domain", ".pintia.cn"),
        )
    return selenium_cookies


def _pta_wait_for_authenticated_cookie(self, timeout_seconds=5):
    deadline = time.time() + timeout_seconds
    captcha_attempted = False
    while time.time() < deadline:
        if self.driver is None:
            return False, []

        selenium_cookies = _pta_sync_driver_cookies_to_session(self)
        if self._check_cookie_valid():
            return True, selenium_cookies

        iframe_count = len(self.driver.find_elements(By.ID, "tcaptcha_iframe_dy"))
        if iframe_count and not captcha_attempted:
            print("Detected slider captcha, attempting automatic solve...")
            self._handle_captcha()
            captcha_attempted = True
            time.sleep(min(2, max(0, deadline - time.time())))
            continue

        remaining = deadline - time.time()
        if remaining <= 0:
            break
        time.sleep(min(1, remaining))

    selenium_cookies = _pta_sync_driver_cookies_to_session(self)
    return self._check_cookie_valid(), selenium_cookies


def _pta_find_visible_enabled(driver, selectors, timeout_seconds=5):
    def find_one(_driver):
        for selector in selectors:
            for element in _driver.find_elements(By.CSS_SELECTOR, selector):
                if element.is_displayed() and element.is_enabled():
                    return element
        return False

    return WebDriverWait(driver, timeout_seconds, poll_frequency=0.2).until(find_one)


def _pta_dump_login_debug(self, tag="pta_login_failed"):
    if self.driver is None:
        return
    debug_dir = (RUNTIME_DIR / "login_debug").resolve()
    debug_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    html_path = debug_dir / f"{tag}_{timestamp}.html"
    png_path = debug_dir / f"{tag}_{timestamp}.png"
    try:
        html_path.write_text(self.driver.page_source, encoding="utf-8")
    except Exception:
        pass
    try:
        self.driver.save_screenshot(str(png_path))
    except Exception:
        pass


def _pta_selenium_login_override(self):
    chrome_options = Options()
    chrome_options.add_argument("--disable-blink-features=AutomationControlled")
    chrome_options.add_argument("--disable-infobars")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--remote-debugging-port=0")
    chrome_options.add_argument("--window-size=1440,900")
    chrome_options.add_argument("--disable-software-rasterizer")
    chrome_options.add_argument("--disable-extensions")
    chrome_options.add_argument("--disable-background-networking")
    chrome_options.add_argument("--no-first-run")
    chrome_options.add_argument("--no-default-browser-check")
    chrome_options.add_argument("--disable-popup-blocking")
    chrome_options.add_argument("--disable-features=Translate,AutomationControlled")
    chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
    chrome_options.add_experimental_option("useAutomationExtension", False)
    if getattr(self, "headless", _env_flag("PTA_HEADLESS", False)):
        chrome_options.add_argument("--headless=new")

    print(f"PTA browser home: {_browser_home()}")
    browser_path, browser_version, browser_major = _detect_chrome_binary()
    if browser_path:
        chrome_options.binary_location = browser_path
        print(f"Chrome binary: {browser_path}")
        if browser_version:
            print(f"Chrome version: {browser_version}")
    else:
        print("Chrome binary not found in standard paths; Selenium will use default discovery.")

    profile_dir = (RUNTIME_DIR / ".chrome-profile").resolve()
    profile_dir.mkdir(parents=True, exist_ok=True)
    (profile_dir / "Default").mkdir(parents=True, exist_ok=True)
    (profile_dir / "First Run").touch(exist_ok=True)
    chrome_options.add_argument(f"--user-data-dir={profile_dir}")
    chrome_options.add_argument("--profile-directory=Default")

    selected_driver = _resolve_chromedriver(browser_major)
    last_error = None
    try:
        if selected_driver and selected_driver["path"]:
            print(f"Selected ChromeDriver: {selected_driver['version_text']} @ {selected_driver['path']}")
            try:
                self.driver = webdriver.Chrome(
                    service=Service(selected_driver["path"]),
                    options=chrome_options,
                )
            except Exception as e:
                last_error = e
                print(f"Local ChromeDriver startup failed: {e}")

        if self.driver is None:
            cache_dir = str((RUNTIME_DIR / ".selenium").resolve())
            os.environ.setdefault("SE_CACHE_PATH", cache_dir)
            print(f"Trying Selenium Manager with cache: {cache_dir}")
            try:
                self.driver = webdriver.Chrome(options=chrome_options)
            except Exception as e:
                last_error = e
                print(f"Selenium Manager startup failed: {e}")

        if self.driver is None:
            print("Trying webdriver-manager download fallback...")
            self.driver = webdriver.Chrome(
                service=Service(ChromeDriverManager().install()),
                options=chrome_options,
            )
    except Exception as e:
        detail = last_error or e
        raise RuntimeError(
            "failed to start Chrome for PTA auto-login; "
            f"chrome={browser_version or 'unknown'}, "
            f"driver={selected_driver['version_text'] if selected_driver else 'none'}, "
            f"detail={detail}"
        ) from e

    self.driver.implicitly_wait(0)
    login_ok = False

    try:
        print("Selenium login starting...", flush=True)
        try:
            self.driver.set_window_position(80, 40)
            self.driver.set_window_size(1440, 900)
        except Exception:
            pass
        self.driver.get(f"{BASE_URL}/auth/login")
        form_wait_seconds = max(1, int(os.getenv("PTA_SELENIUM_FORM_WAIT_SECONDS", "5")))
        WebDriverWait(self.driver, form_wait_seconds).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "form, input, button[type='submit']"))
        )

        username_input = _pta_find_visible_enabled(self.driver, [
            "input[autocomplete='username']",
            "input[name='username']",
            "input[name='account']",
            "input[type='email']",
            "input[type='tel']",
            "input[type='text']",
            "input:not([type])",
        ], timeout_seconds=form_wait_seconds)

        username_input.clear()
        username_input.send_keys(self.username)

        password_input = _pta_find_visible_enabled(self.driver, [
            "input[autocomplete='current-password']",
            "input[name='password']",
            "input[type='password']",
        ], timeout_seconds=form_wait_seconds)

        password_input.clear()
        password_input.send_keys(self.password)

        submit_button = _pta_find_visible_enabled(self.driver, [
            "button[type='submit']",
            "button.pc-button",
        ], timeout_seconds=form_wait_seconds)

        submit_button.click()

        wait_seconds = max(1, int(os.getenv("PTA_SELENIUM_AUTH_WAIT_SECONDS", "5")))
        authenticated, selenium_cookies = _pta_wait_for_authenticated_cookie(self, timeout_seconds=wait_seconds)
        self._save_cookies(selenium_cookies)
        if not authenticated:
            _pta_dump_login_debug(self)
            raise RuntimeError("login submitted but authenticated cookie was not detected")
        login_ok = True
        print("Selenium login completed, cookies moved to requests", flush=True)
    finally:
        if self.driver:
            keep_open_on_failure = (
                not login_ok
                and not getattr(self, "headless", _env_flag("PTA_HEADLESS", False))
                and _env_flag("PTA_KEEP_BROWSER_OPEN_ON_FAILURE", True)
            )
            if keep_open_on_failure:
                print("Selenium login failed; keeping Chrome open for inspection.", flush=True)
            else:
                self.driver.quit()
                self.driver = None
        pass


PTAClient._selenium_login = _pta_selenium_login_override
PTAClient.ensure_login = _pta_ensure_login_override


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--schedule":
        # python spider.py --schedule        -> every 24h (full mode)
        # python spider.py --schedule 12     -> every 12h
        hours = int(sys.argv[2]) if len(sys.argv) > 2 else 24
        run_scheduled(hours)
    elif len(sys.argv) > 1 and sys.argv[1] == "--refresh":
        # python spider.py --refresh  -> 只刷新导出数据
        run_once(mode="refresh")
    elif len(sys.argv) > 1 and sys.argv[1] == "--full":
        # python spider.py --full  -> 增量爬取 + 刷新导出
        run_once(mode="full")
    elif len(sys.argv) > 1 and sys.argv[1] == "--group-id":
        # python spider.py --group-id "2028307022170722304" ["计科25数据结构"]
        group_id = sys.argv[2] if len(sys.argv) > 2 else None
        group_name = sys.argv[3] if len(sys.argv) > 3 else None
        client = PTAClient()
        client.crawl_incremental(group_id=group_id, group_name=group_name)
    elif len(sys.argv) > 1 and sys.argv[1] == "--group-name":
        # python spider.py --group-name "计科25数据结构"
        group_name = sys.argv[2] if len(sys.argv) > 2 else None
        client = PTAClient()
        client.crawl_incremental(group_name=group_name)
    else:
        # python spider.py  -> 默认增量爬取
        run_once(mode="incremental")


if __name__ == "__main__":
    main()
