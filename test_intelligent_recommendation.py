#!/usr/bin/env python3
"""
智能推荐系统测试脚本
"""

import argparse
import os
from typing import Any, Dict, List

import requests


BASE_URL = os.getenv("AI_DS_BASE_URL", "http://localhost:8081")
TEST_STUDENT_ID = int(os.getenv("AI_DS_TEST_STUDENT_ID", "1"))
REQUEST_TIMEOUT = float(os.getenv("AI_DS_REQUEST_TIMEOUT", "10"))

DB_CONFIG = {
    "host": os.getenv("AI_DS_DB_HOST", "localhost"),
    "port": int(os.getenv("AI_DS_DB_PORT", "3306")),
    "user": os.getenv("AI_DS_DB_USER", "root"),
    "password": os.getenv("AI_DS_DB_PASSWORD", "123456"),
    "database": os.getenv("AI_DS_DB_NAME", "ptadatabase"),
    "charset": "utf8mb4",
}

SAMPLE_SKILLS = [
    ("数组", 45.0, 60.0, 20.0, 5, 2),
    ("字符串", 65.0, 75.0, 10.0, 8, 5),
    ("动态规划", 25.0, 40.0, 50.0, 3, 0),
    ("贪心", 55.0, 70.0, 15.0, 6, 3),
    ("图", 30.0, 45.0, 40.0, 2, 1),
    ("树", 70.0, 80.0, 5.0, 10, 7),
]


def _request_json(path: str) -> Dict[str, Any]:
    url = f"{BASE_URL}{path}"
    response = requests.get(url, timeout=REQUEST_TIMEOUT)
    response.raise_for_status()
    return response.json()


def test_recommendation_api() -> None:
    print("=== 测试智能推荐系统 ===")
    print(f"BASE_URL: {BASE_URL}")
    print(f"TEST_STUDENT_ID: {TEST_STUDENT_ID}")

    try:
        data = _request_json(f"/api/student/{TEST_STUDENT_ID}/recommendedPractices")
    except Exception as exc:
        print(f"[ERROR] 获取推荐失败: {exc}")
        return

    print(f"[OK] success: {data.get('success', False)}")
    print(f"[OK] source: {data.get('source', 'unknown')}")

    recommendations: List[Dict[str, Any]] = data.get("data", [])
    print(f"[OK] 推荐数量: {len(recommendations)}")

    if not recommendations:
        print("[WARN] 没有返回推荐结果")
        return

    print("\n前5条推荐:")
    for idx, item in enumerate(recommendations[:5], start=1):
        print(
            f"{idx}. {item.get('title', 'Unknown')} | "
            f"difficulty={item.get('difficulty', 'Unknown')} | "
            f"score={item.get('score', 'N/A')} | "
            f"reason={item.get('reason', 'N/A')}"
        )


def test_current_user_recommendation() -> None:
    print("\n=== 测试当前用户推荐（需要登录态 Session） ===")
    try:
        data = _request_json("/api/current/recommendedPractices")
        print(f"[OK] success: {data.get('success', False)}")
        print(f"[INFO] message: {data.get('message', '')}")
    except Exception as exc:
        print(f"[ERROR] 当前用户推荐接口调用失败: {exc}")


def test_database_data() -> None:
    print("\n=== 测试数据库数据 ===")

    try:
        import mysql.connector  # lazy import
    except Exception as exc:
        print(f"[ERROR] 缺少 mysql-connector-python: {exc}")
        return

    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()

        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        problem_count = cursor.fetchone()[0]
        print(f"[OK] 题目总数: {problem_count}")

        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_tag")
        tag_count = cursor.fetchone()[0]
        print(f"[OK] 标签总数: {tag_count}")

        cursor.execute(
            """
            SELECT tag_type, COUNT(*) AS count
            FROM leetcode_problem_tag
            GROUP BY tag_type
            ORDER BY count DESC
            """
        )
        print("[INFO] 标签类型分布:")
        for tag_type, count in cursor.fetchall():
            print(f"  - {tag_type}: {count}")

        cursor.execute("SELECT COUNT(*) FROM student_skill_state")
        skill_count = cursor.fetchone()[0]
        print(f"[OK] 学生技能记录数: {skill_count}")
        if skill_count == 0:
            print("[WARN] 没有学生技能数据，推荐系统可能退化为默认策略")

    except Exception as exc:
        print(f"[ERROR] 数据库检查失败: {exc}")
    finally:
        try:
            cursor.close()
            conn.close()
        except Exception:
            pass


def upsert_sample_skill_data() -> None:
    print("\n=== 写入示例学生技能数据（UPSERT，非删除式） ===")

    try:
        import mysql.connector  # lazy import
    except Exception as exc:
        print(f"[ERROR] 缺少 mysql-connector-python: {exc}")
        return

    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()

        insert_sql = """
        INSERT INTO student_skill_state
        (student_id, tag_name, mastery_score, confidence_score, forgetting_score, attempt_count, success_count)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
          mastery_score = VALUES(mastery_score),
          confidence_score = VALUES(confidence_score),
          forgetting_score = VALUES(forgetting_score),
          attempt_count = VALUES(attempt_count),
          success_count = VALUES(success_count)
        """

        rows = [
            (TEST_STUDENT_ID, tag, mastery, confidence, forgetting, attempts, success)
            for tag, mastery, confidence, forgetting, attempts, success in SAMPLE_SKILLS
        ]
        cursor.executemany(insert_sql, rows)
        conn.commit()

        print(f"[OK] 学生 {TEST_STUDENT_ID} 已写入/更新 {len(rows)} 条技能记录")
        cursor.execute(
            "SELECT tag_name, mastery_score, attempt_count FROM student_skill_state WHERE student_id = %s ORDER BY tag_name",
            (TEST_STUDENT_ID,),
        )
        for tag_name, mastery_score, attempt_count in cursor.fetchall():
            print(f"  - {tag_name}: mastery={mastery_score}, attempts={attempt_count}")

    except Exception as exc:
        print(f"[ERROR] 写入示例技能数据失败: {exc}")
    finally:
        try:
            cursor.close()
            conn.close()
        except Exception:
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="智能推荐系统测试工具")
    parser.add_argument("--skip-db", action="store_true", help="跳过数据库检查")
    parser.add_argument("--seed-skills", action="store_true", help="写入示例技能数据")
    parser.add_argument("--test-current", action="store_true", help="测试当前登录用户推荐接口")
    args = parser.parse_args()

    if not args.skip_db:
        test_database_data()

    if args.seed_skills:
        upsert_sample_skill_data()

    test_recommendation_api()

    if args.test_current:
        test_current_user_recommendation()

    print("\n测试完成")


if __name__ == "__main__":
    main()
