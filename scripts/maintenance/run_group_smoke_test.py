import argparse
import json
import os
import sys
import uuid
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SRC_ROOT = REPO_ROOT / "src"
if str(SRC_ROOT) not in sys.path:
    sys.path.insert(0, str(SRC_ROOT))

from pta_spider.spider_api import CrawlMode, TaskInfo, TaskStatus, _run_crawl


def main():
    parser = argparse.ArgumentParser(description="Run one real PTA group crawl and database sync.")
    parser.add_argument("--group-name", required=True)
    parser.add_argument("--class-id", required=True, type=int)
    parser.add_argument(
        "--mode",
        choices=[mode.value for mode in CrawlMode],
        default=CrawlMode.FULL.value,
    )
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--headless", action=argparse.BooleanOptionalAction, default=True)
    args = parser.parse_args()

    username = os.getenv("PTA_USERNAME")
    password = os.getenv("PTA_PASSWORD")
    if not username or not password:
        raise SystemExit("PTA_USERNAME and PTA_PASSWORD are required in the process environment")

    task = TaskInfo(
        tid=f"smoke-{uuid.uuid4().hex[:8]}",
        keyword=args.group_name,
        class_id=args.class_id,
        problem_set_id=None,
        problem_set_name=None,
        group_id=None,
        group_name=args.group_name,
        mode=CrawlMode(args.mode),
        force=args.force,
        credential_source="temporary",
        username=username,
        password=password,
        headless=args.headless,
    )
    _run_crawl(task)
    print("SMOKE_TEST_RESULT " + json.dumps(task.to_dict(), ensure_ascii=False), flush=True)
    if task.status != TaskStatus.SUCCESS:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
