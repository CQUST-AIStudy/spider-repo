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
import threading
from pathlib import Path
from datetime import datetime

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

# Load .env: prefer PTA project dir, fallback to current dir
_env_candidates = [
    Path(__file__).resolve().parent.parent / "PTA爬虫项目" / ".env",
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
HISTORY_FILE = "crawl_history.json"
CRAWL_DIR = Path(os.getenv("PTA_CRAWL_DIR", str(Path(__file__).resolve().parent.parent / "爬取结果"))).resolve()


def _env_flag(name, default=False):
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


class TokenBucketRateLimiter:
    """Token bucket rate limiter for PTA API request throttling"""

    def __init__(self, rate=20, per=60):
        """rate: tokens, per: time window(sec), default 20/min"""
        self.rate = rate
        self.per = per
        self.tokens = rate
        self.last_refill = time.monotonic()
        self._lock = threading.Lock()

    def acquire(self):
        """Acquire a token, block if none available"""
        while True:
            with self._lock:
                now = time.monotonic()
                elapsed = now - self.last_refill
                self.tokens = min(self.rate, self.tokens + elapsed * (self.rate / self.per))
                self.last_refill = now
                if self.tokens >= 1:
                    self.tokens -= 1
                    return
            time.sleep(0.5)


# Global rate limiter instance
_rate_limiter = TokenBucketRateLimiter(rate=20, per=60)


class CrawlHistory:
    """
    Manage crawl history for incremental crawling.
    每个题目集记录两个时间戳:
      - content_crawled_at: 题目内容首次爬取时间（只爬一次）
      - export_refreshed_at: 导出数据（成绩单/答题卡/得分代码）最后刷新时间
    """

    def __init__(self, path=HISTORY_FILE):
        self.path = path
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
        self.data["last_run"] = datetime.now().isoformat()
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False, indent=2)

    def is_crawled(self, problem_set_id):
        return problem_set_id in self.data["crawled_sets"]

    def mark_crawled(self, problem_set_id, name):
        """标记题目集内容已爬取（首次爬取，含题目内容+导出）"""
        now = datetime.now().isoformat()
        self.data["crawled_sets"][problem_set_id] = {
            "name": name,
            "crawled_at": now,
            "content_crawled_at": now,
            "export_refreshed_at": now,
        }
        self.save()

    def mark_export_refreshed(self, problem_set_id):
        """标记导出数据已刷新（不重新爬取题目内容）"""
        if problem_set_id in self.data["crawled_sets"]:
            self.data["crawled_sets"][problem_set_id]["export_refreshed_at"] = datetime.now().isoformat()
            self.save()

    def get_new_sets(self, all_sets):
        """Filter out already-crawled sets from all sets"""
        new = []
        for ps in all_sets:
            ps_id = ps.get("id", "")
            if not self.is_crawled(ps_id):
                new.append(ps)
        return new

    def get_all_crawled(self):
        """返回所有已爬取的题目集 {id: info}"""
        return self.data.get("crawled_sets", {})


class PTAClient:
    """PTA data crawler client, auto cookie management, API-first"""

    def __init__(self, username=None, password=None):
        self.username = username or os.getenv("PTA_USERNAME")
        self.password = password or os.getenv("PTA_PASSPORT")
        self.crawl_dir = CRAWL_DIR
        self.force_selenium_login = _env_flag("PTA_FORCE_SELENIUM_LOGIN", False)
        # Per-account cookie file, supports multi-account
        safe_name = re.sub(r'[^\w]', '_', self.username)
        self.cookie_file = f"pta_cookies_{safe_name}.pkl"
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
        manual_cookie_path = os.path.join(os.path.dirname(__file__), "manual_cookies.json")
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
            sync_module_path = os.path.join(os.path.dirname(__file__), "sync_to_db.py")
            if os.path.exists(sync_module_path):
                import sys as _sys
                _sys.path.insert(0, os.path.dirname(__file__))
                from sync_to_db import sync_all
                sync_all()
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
            with open("captcha_bg.jpg", "wb") as f:
                f.write(resp.content)
            dis = self._get_captcha_offset()
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
    def _get_captcha_offset(image_path="captcha_bg.jpg"):
        image = cv2.imread(image_path)
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
        """Unified GET with auto re-login, 429 backoff, token bucket rate limiting"""
        max_retries = 3
        for attempt in range(max_retries + 1):
            _rate_limiter.acquire()
            resp = self.session.get(f"{API_BASE}{path}", params=params, timeout=30)
            if resp.status_code in (401, 403):
                print("认证失效，重新登录...")
                self.ensure_login()
                resp = self.session.get(f"{API_BASE}{path}", params=params, timeout=30)
            if resp.status_code == 429:
                if attempt < max_retries:
                    wait = 30 * (attempt + 1)
                    print(f"  429 请求过于频繁，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                else:
                    print("  429 重试次数已用尽")
            resp.raise_for_status()
            return resp.json()
        resp.raise_for_status()
        return resp.json()

    def search_problem_sets(self, keyword):
        """Search teaching problem sets (/api/problem-sets/admin)"""
        result = []
        page = 0
        while True:
            filter_param = json.dumps({
                "ownerId": "0",
                "keyword": keyword,
                "stage": {"stage": "NORMAL"},
            })
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
            for s in sets:
                print(f"  发现: {s.get('name', '?')}")
            result.extend(sets)
            if len(result) >= data.get("total", 0):
                break
            page += 1
            time.sleep(random.uniform(0.5, 1))
        print(f"搜索到 {len(result)} 个题目集")
        return result

    def get_problem_set_detail(self, ps_id):
        """Get single problem set detail"""
        return self.api_get(f"/problem-sets/{ps_id}")

    def get_problems(self, ps_id):
        """Get all problems in a problem set"""
        data = self.api_get(f"/problem-sets/{ps_id}/preview/problems", params={
            "problem_type": "PROGRAMMING",
            "page": 0,
            "limit": 500,
        })
        return data.get("problemSetProblems", [])

    def get_problem_detail(self, ps_id, problem_id):
        """Get detailed content of a single problem"""
        return self.api_get(f"/problem-sets/{ps_id}/preview/problems/{problem_id}")

    def get_submissions(self, ps_id, page=0, limit=200):
        """Get submissions for a problem set"""
        return self.api_get(f"/problem-sets/{ps_id}/submissions", params={
            "page": page, "limit": limit,
        })

    def get_all_submissions(self, ps_id):
        """
        Paginate to get all submissions.
        Does not rely on 'total' field (PTA may truncate it). Instead keeps paging until:
        1. Empty response, or
        2. All records on page are duplicates (API looping), or
        3. Safety cap reached (prevent infinite loop)
        """
        all_subs = []
        seen_ids = set()  # Dedup by submission key
        page = 0
        max_pages = 500  # Safety cap: 500 pages x 200 = 100k records

        while page < max_pages:
            data = self.get_submissions(ps_id, page=page)
            subs = data.get("submissions", [])
            if not subs:
                break

            # Dedup: if all records on this page are seen, API is looping
            new_count = 0
            for sub in subs:
                # Use submitAt + userId + problemSetProblemId as unique key
                sub_key = (sub.get("submitAt", ""), sub.get("userId", ""),
                           sub.get("problemSetProblemId", ""))
                if sub_key not in seen_ids:
                    seen_ids.add(sub_key)
                    all_subs.append(sub)
                    new_count += 1

            if new_count == 0:
                # All duplicates on this page, stop
                break

            # Fewer than limit results means last page
            if len(subs) < 200:
                break

            page += 1
            time.sleep(random.uniform(0.5, 1.0))

        return all_subs

    def get_rankings(self, ps_id):
        """Get rankings"""
        return self.api_get(f"/problem-sets/{ps_id}/rankings")

    def get_examinees(self, ps_id):
        """Get examinee list"""
        return self.api_get(f"/problem-sets/{ps_id}/examinees")

    # ==================== Export (answer sheet/transcript/plagiarism) ====================

    def api_post(self, path, json_data=None):
        """Unified POST with auto re-login, 429/403 backoff, token bucket rate limiting"""
        max_retries = 3
        for attempt in range(max_retries + 1):
            _rate_limiter.acquire()
            resp = self.session.post(f"{API_BASE}{path}", json=json_data, timeout=30)
            if resp.status_code == 401:
                print("认证失效，重新登录...")
                self.ensure_login()
                resp = self.session.post(f"{API_BASE}{path}", json=json_data, timeout=30)
            if resp.status_code == 429:
                if attempt < max_retries:
                    wait = 30 * (attempt + 1)
                    print(f"  429 请求过于频繁，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                else:
                    print("  429 重试次数已用尽")
            if resp.status_code == 403:
                if attempt < max_retries:
                    wait = 10 * (attempt + 1)
                    print(f"  403 无权限，等待 {wait}s 后重试 ({attempt+1}/{max_retries})...")
                    time.sleep(wait)
                    continue
                else:
                    print("  403 重试次数已用尽")
            resp.raise_for_status()
            return resp

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
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

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
        return resp.json() if resp.text else {}

    def wait_export_ready(self, ps_id, export_type="ANSWER_SHEET", timeout=120):
        """
        轮询等待导出任务完成，返回 docUrl 下载链接。
        status: WAITING → READY 表示完成
        """
        filter_param = json.dumps({"problemSetId": ps_id})
        start = time.time()

        while time.time() - start < timeout:
            data = self.api_get("/exports", params={
                "page": 0, "limit": 10,
                "filter": filter_param,
            })
            exports = data.get("exports", [])
            for exp in exports:
                if exp.get("type") == export_type and exp.get("status") == "READY":
                    doc_url = exp.get("docUrl", "")
                    if doc_url:
                        print(f"\n  导出完成，获取到下载链接")
                        return doc_url
            elapsed = int(time.time() - start)
            print(f"  等待导出完成... ({elapsed}s)", end="\r")
            time.sleep(3)

        print(f"\n  导出超时({timeout}s)")
        return None

    def download_export(self, download_url, save_path):
        """Download export file (COS signed URL)"""
        # Try download without cookie first
        resp = requests.get(download_url, stream=True, timeout=120)
        if resp.status_code == 403:
            # COS signature may need referer
            resp = requests.get(
                download_url, stream=True, timeout=120,
                headers={"Referer": "https://pintia.cn/"}
            )
        resp.raise_for_status()
        os.makedirs(os.path.dirname(save_path) or ".", exist_ok=True)
        with open(save_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=8192):
                f.write(chunk)
        size_mb = os.path.getsize(save_path) / 1024 / 1024
        print(f"  已下载: {save_path} ({size_mb:.1f}MB)")

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

                self.create_export(ps_id, ps_name, export_type)
                time.sleep(3)  # Wait for task creation, avoid rate limit

                doc_url = self.wait_export_ready(ps_id, export_type)
                if doc_url:
                    # Determine file extension from docUrl
                    if doc_url.endswith(".xlsx") or ".xlsx?" in doc_url:
                        ext = ".xlsx"
                    else:
                        ext = ".zip"
                    save_path = os.path.join(save_dir, f"{ps_name}-{export_type}{ext}")
                    self.download_export(doc_url, save_path)
                    return save_path
                return None

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

    # ==================== Incremental Crawl Core ====================

    def crawl_incremental(self, keyword=None):
        """
        增量爬取：自动检测新题目集，只处理新增的。
        keyword: 搜索关键词，如 '计科23数据结构'，默认从 .env 读取
        """
        keyword = keyword or os.getenv("experiment_name")
        print(f"\n{'='*50}")
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 开始增量爬取: {keyword}")
        print(f"{'='*50}")

        # 1. ensure login
        if not self.ensure_login():
            print("登录失败，本次爬取终止")
            return

        # 2. search all problem sets
        all_sets = self.search_problem_sets(keyword)
        if not all_sets:
            print("未搜索到任何题目集")
            return

        # 3. filter new sets
        new_sets = self.history.get_new_sets(all_sets)
        if not new_sets:
            print("没有新的题目集，本次无需爬取")
            return

        print(f"发现 {len(new_sets)} 个新题目集:")
        for ps in new_sets:
            print(f"  - {ps.get('name', '未知')}")

        # 4. crawl each new set
        for ps in new_sets:
            ps_id = ps.get("id", "")
            ps_name = ps.get("name", "未知")
            try:
                print(f"\n--- 正在爬取: {ps_name} ---")
                self._crawl_one_problem_set(ps_id, ps_name)
                self.history.mark_crawled(ps_id, ps_name)
                print(f"完成: {ps_name}")
            except Exception as e:
                print(f"爬取 {ps_name} 失败: {e}")

        print(f"\n本次增量爬取完成，处理了 {len(new_sets)} 个新题目集")

        # 自动同步到数据库
        self._auto_sync_to_db()

    def _crawl_one_problem_set(self, ps_id, ps_name):
        """Crawl all data for a single problem set, save to ./爬取结果/"""
        base_dir = self._problem_set_dir(ps_name)

        # 1. Crawl problem content
        try:
            problems = self.get_problems(ps_id)
            print(f"  题目数量: {len(problems)}")
            if problems:
                with open(base_dir / "题目内容.txt", "w", encoding="utf-8") as f:
                    for p in problems:
                        pid = p.get("id", "")
                        title = p.get("title", "")
                        label = p.get("label", "")
                        f.write(f"[{label}] {title}\n")
                        if pid:
                            try:
                                detail = self.get_problem_detail(ps_id, pid)
                                psp = detail.get("problemSetProblem", {})
                                content = psp.get("content", "") or psp.get("description", "")
                                f.write(f"{content}\n")
                            except Exception as e:
                                f.write(f"(获取详情失败: {e})\n")
                            time.sleep(random.uniform(0.3, 0.8))
                        f.write(f"\n{'='*40}\n\n")
        except Exception as e:
            print(f"  获取题目列表失败: {e}")

        # 2. Crawl submissions
        try:
            submissions = self.get_all_submissions(ps_id)
            if submissions:
                import csv
                with open(base_dir / "提交记录.csv", "w", encoding="utf-8", newline="") as f:
                    writer = csv.writer(f)
                    writer.writerow(["用户ID", "题目ID", "状态", "分数", "编译器", "用时", "内存", "提交时间"])
                    for sub in submissions:
                        writer.writerow([
                            sub.get("userId", ""),
                            sub.get("problemSetProblemId", ""),
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

        # 3. Export essential types only (PAPER/PAPER_ACCURATE/PAPER_ANALYSIS are redundant/computable)
        export_dir = base_dir / "导出"
        export_configs = [
            ("ANSWER_SHEET",     "答题卡"),
            ("PAPER_TRANSCRIPT", "成绩单"),
            ("SCORED_CODE",      "得分代码"),
        ]
        for export_type, cn_name in export_configs:
            try:
                self.export_and_download(ps_id, ps_name, export_type, str(export_dir))
                time.sleep(random.uniform(3, 5))  # Export is heavy, longer interval
            except requests.exceptions.HTTPError as e:
                if e.response is not None and e.response.status_code == 403:
                    print(f"  导出{cn_name}: 无权限（重试后仍失败），跳过")
                elif e.response is not None and e.response.status_code == 429:
                    print(f"  导出{cn_name}: 请求过于频繁，等待30s后继续...")
                    time.sleep(30)
                else:
                    print(f"  导出{cn_name}失败: {e}")
            except Exception as e:
                print(f"  导出{cn_name}失败: {e}")

        time.sleep(random.uniform(0.5, 1))

    def _refresh_one_problem_set(self, ps_id, ps_name):
        """
        刷新已爬取题目集的导出数据（不重新爬取题目内容和提交记录）。
        只重新导出: PAPER_TRANSCRIPT, ANSWER_SHEET, SCORED_CODE
        """
        base_dir = self._problem_set_dir(ps_name)
        export_dir = base_dir / "导出"
        export_dir.mkdir(parents=True, exist_ok=True)

        export_configs = [
            ("PAPER_TRANSCRIPT", "成绩单"),
            ("ANSWER_SHEET",     "答题卡"),
            ("SCORED_CODE",      "得分代码"),
        ]
        for export_type, cn_name in export_configs:
            try:
                self.export_and_download(ps_id, ps_name, export_type, str(export_dir))
                time.sleep(random.uniform(3, 5))
            except requests.exceptions.HTTPError as e:
                if e.response is not None and e.response.status_code == 403:
                    print(f"  导出{cn_name}: 无权限（重试后仍失败），跳过")
                elif e.response is not None and e.response.status_code == 429:
                    print(f"  导出{cn_name}: 请求过于频繁，等待30s后继续...")
                    time.sleep(30)
                else:
                    print(f"  导出{cn_name}失败: {e}")
            except Exception as e:
                print(f"  导出{cn_name}失败: {e}")

    def refresh_exports(self, keyword=None):
        """
        刷新所有已爬取题目集的导出数据（成绩单/答题卡/得分代码）。
        用于学生持续提交后，重新拉取最新数据覆盖旧文件。
        不重新爬取题目内容（题目发布后不变）。
        """
        keyword = keyword or os.getenv("experiment_name")
        print(f"\n{'='*50}")
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 刷新导出数据: {keyword}")
        print(f"{'='*50}")

        if not self.ensure_login():
            print("登录失败，本次刷新终止")
            return 0

        # 搜索所有题目集（需要获取 ps_id）
        all_sets = self.search_problem_sets(keyword)
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

        refreshed = 0
        for ps in to_refresh:
            ps_id = ps.get("id", "")
            ps_name = ps.get("name", "未知")
            try:
                print(f"\n--- 刷新导出: {ps_name} ---")
                self._refresh_one_problem_set(ps_id, ps_name)
                self.history.mark_export_refreshed(ps_id)
                refreshed += 1
                print(f"完成: {ps_name}")
            except Exception as e:
                print(f"刷新 {ps_name} 失败: {e}")

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
        {"username": "teacher1@xxx.com", "password": "xxx", "keyword": "计科23数据结构"},
        {"username": "teacher2@xxx.com", "password": "xxx", "keyword": "软工24程序设计"}
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
        "password": os.getenv("PTA_PASSPORT"),
        "keyword": os.getenv("experiment_name"),
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
            keyword = acc.get("keyword")
            if mode in ("incremental", "full"):
                client.crawl_incremental(keyword)
            if mode in ("refresh", "full"):
                client.refresh_exports(keyword)
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


if __name__ == "__main__":
    import sys

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
    elif len(sys.argv) > 1 and sys.argv[1] == "--keyword":
        # python spider.py --keyword "keyword_here"
        keyword = sys.argv[2] if len(sys.argv) > 2 else None
        client = PTAClient()
        client.crawl_incremental(keyword)
    else:
        # python spider.py  -> 默认增量爬取
        run_once(mode="incremental")
