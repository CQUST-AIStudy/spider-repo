#!/usr/bin/env python
"""Synchronize an already-complete PTA crawl snapshot without contacting PTA."""

import argparse
import json

from pta_spider.sync_to_unified_db import sync_all


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--crawl-dir", required=True)
    parser.add_argument("--class-id", required=True, type=int)
    parser.add_argument("--experiment", action="append", dest="experiments")
    args = parser.parse_args()
    report = sync_all(
        crawl_dir=args.crawl_dir,
        strict=True,
        class_id=args.class_id,
        experiment_names=args.experiments,
    )
    print("LOCAL_SYNC_RESULT " + json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
