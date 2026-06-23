import json
import os
import sys
import time
from pathlib import Path

import requests
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider import spider as spider_mod


def build_driver():
    browser_path, browser_version, browser_major = spider_mod._detect_chrome_binary()
    selected_driver = spider_mod._resolve_chromedriver(browser_major)
    if not browser_path:
        raise RuntimeError("dedicated PTA browser not found")
    if not selected_driver:
        raise RuntimeError(f"no matching ChromeDriver found for browser major {browser_major}")

    options = Options()
    options.binary_location = browser_path
    options.add_argument("--window-size=1440,900")
    options.add_argument("--no-first-run")
    options.add_argument("--no-default-browser-check")
    options.add_argument("--disable-popup-blocking")
    options.add_argument("--disable-background-networking")
    options.add_argument("--disable-blink-features=AutomationControlled")
    options.add_experimental_option("excludeSwitches", ["enable-automation"])
    options.add_experimental_option("useAutomationExtension", False)

    profile_dir = (spider_mod.RUNTIME_DIR / ".chrome-profile-manual").resolve()
    profile_dir.mkdir(parents=True, exist_ok=True)
    (profile_dir / "Default").mkdir(parents=True, exist_ok=True)
    (profile_dir / "First Run").touch(exist_ok=True)
    options.add_argument(f"--user-data-dir={profile_dir}")
    options.add_argument("--profile-directory=Default")

    print(f"Browser: {browser_path}")
    print(f"Browser version: {browser_version}")
    print(f"Driver: {selected_driver['path']}")
    return webdriver.Chrome(service=Service(selected_driver["path"]), options=options)


def cookies_to_session(cookie_list):
    session = requests.Session()
    session.headers.update({
        "user-agent": "Mozilla/5.0",
        "accept": "application/json, text/plain, */*",
        "x-lollipop": "c69dd20235e34148d85ece4af34ed26f",
        "x-marshmallow": "",
    })
    for cookie in cookie_list:
        name = cookie.get("name")
        value = cookie.get("value")
        if not name or not value:
            continue
        session.cookies.set(name, value, domain=cookie.get("domain", ".pintia.cn"))
    return session


def _find_visible_enabled(driver, selectors, timeout_seconds=5):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        for selector in selectors:
            elements = driver.find_elements("css selector", selector)
            for element in elements:
                if element.is_displayed() and element.is_enabled():
                    return element
        time.sleep(0.2)
    return None


def prefill_login_form(driver):
    username = os.getenv("PTA_USERNAME")
    password = os.getenv("PTA_PASSWORD") or os.getenv("PTA_PASSPORT")
    if not username or not password:
        print("PTA_USERNAME or PTA_PASSWORD is missing; leaving the form for manual input.")
        return

    username_input = _find_visible_enabled(driver, [
        "input[autocomplete='username']",
        "input[name='username']",
        "input[name='account']",
        "input[type='email']",
        "input[type='tel']",
        "input[type='text']",
        "input:not([type])",
    ], timeout_seconds=5)

    password_input = _find_visible_enabled(driver, [
        "input[autocomplete='current-password']",
        "input[name='password']",
        "input[type='password']",
    ], timeout_seconds=5)

    submit_button = _find_visible_enabled(driver, [
        "button[type='submit']",
        "button.pc-button",
    ], timeout_seconds=5)

    if username_input is None or password_input is None or submit_button is None:
        print("Login form fields were not detected; leaving the page for manual login.")
        return

    username_input.clear()
    username_input.send_keys(username)
    password_input.clear()
    password_input.send_keys(password)
    submit_button.click()
    print("Filled PTA username/password and clicked login. Please complete slider/captcha if it appears.")


def cookie_valid(cookie_list):
    session = cookies_to_session(cookie_list)
    try:
        resp = session.get(
            f"{spider_mod.API_BASE}/exports",
            params={"page": 0, "limit": 1, "filter": "{}"},
            timeout=10,
        )
        return resp.status_code == 200, resp.status_code
    except Exception:
        return False, "request_failed"


def main():
    timeout_seconds = int(os.getenv("PTA_MANUAL_LOGIN_TIMEOUT", "5"))
    poll_seconds = max(1, int(os.getenv("PTA_MANUAL_LOGIN_POLL_SECONDS", "5")))
    cookie_json = spider_mod.RUNTIME_DIR / "manual_cookies.json"
    cookie_json.parent.mkdir(parents=True, exist_ok=True)
    driver = build_driver()
    try:
        driver.get(f"{spider_mod.BASE_URL}/auth/login")
        print("A visible PTA login window has been opened.")
        prefill_login_form(driver)
        print("Please complete any remaining login steps manually, including slider/captcha.")
        print(
            f"I will wait up to {timeout_seconds} seconds "
            f"(polling every {poll_seconds} seconds) and save cookies to {cookie_json}."
        )

        deadline = time.time() + timeout_seconds
        while time.time() < deadline:
            cookies = driver.get_cookies()
            ok, status = cookie_valid(cookies)
            print(f"Cookie check: {status}")
            if ok:
                cookie_json.write_text(
                    json.dumps(cookies, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                print(f"Saved authenticated cookies to: {cookie_json}")
                return
            remaining = deadline - time.time()
            if remaining <= 0:
                break
            time.sleep(min(poll_seconds, remaining))

        raise RuntimeError("manual login timed out before authenticated cookies were detected")
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
