"""
测试提交记录分页爬取 + 入库
只爬取提交记录，不触发完整爬取流程。
用法: conda run -n spider python services/pta_spider/test_submissions.py
"""
import sys
import os
import csv
from pathlib import Path
from datetime import datetime

# Windows 终端 UTF-8 输出修复
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))
from pta_spider.spider import PTAClient
from pta_spider.sync_to_db import sync_all, get_db, CRAWL_DIR

CRAWL_BASE = Path(os.getenv("PTA_CRAWL_DIR", str(Path(__file__).resolve().parent / "output"))).resolve()


def test_submissions():
    client = PTAClient()
    if not client.ensure_login():
        print("登录失败")
        return

    # 搜索所有题目集
    group_id = os.getenv("PTA_GROUP_ID")
    group_name = os.getenv("PTA_GROUP_NAME") or os.getenv("experiment_name", "计科23数据结构")
    all_sets = client.search_problem_sets(group_id=group_id, group_name=group_name)
    if not all_sets:
        print("未找到题目集")
        return

    print(f"\n{'='*60}")
    print(f"开始测试提交记录分页爬取，共 {len(all_sets)} 个题目集")
    print(f"{'='*60}")

    total_all = 0
    for ps in all_sets:
        ps_id = ps.get("id", "")
        ps_name = ps.get("name", "未知")

        print(f"\n--- {ps_name} ---")
        subs = client.get_all_submissions(ps_id)
        count = len(subs)
        total_all += count
        print(f"  获取到 {count} 条提交记录")

        # 保存到 爬取结果/{实验名}/提交记录.csv（覆盖旧文件）
        save_dir = CRAWL_BASE / ps_name
        save_dir.mkdir(parents=True, exist_ok=True)
        csv_path = save_dir / "提交记录.csv"
        with open(csv_path, "w", encoding="utf-8", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["用户ID", "题目ID", "状态", "分数", "编译器", "用时", "内存", "提交时间"])
            for sub in subs:
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
        print(f"  已保存: {csv_path}")

    print(f"\n{'='*60}")
    print(f"提交记录爬取完成，共 {total_all} 条")
    print(f"{'='*60}")

    # 同步到数据库
    print(f"\n开始同步到数据库...")
    sync_all()

    # 验证数据库中的提交记录数
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM submit_situation")
    db_count = cursor.fetchone()[0]
    conn.close()
    print(f"\n数据库 submit_situation 表: {db_count} 条")
    print(f"本次爬取: {total_all} 条")


if __name__ == "__main__":
    test_submissions()
