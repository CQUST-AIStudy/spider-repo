import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider import sync_to_unified_db as sync


class _BindingCursor:
    def __init__(self, row):
        self.row = row

    def execute(self, _sql, _params):
        return None

    def fetchone(self):
        return self.row


class ImportSafetyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        (self.tmp / "题目集信息.json").write_text(
            json.dumps({"id": "ps-1", "name": "测试题目集"}, ensure_ascii=False),
            encoding="utf-8",
        )

    def _write_submission_csv(self, rows):
        path = self.tmp / "提交记录.csv"
        with path.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(
                stream,
                fieldnames=[
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
                ],
            )
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_submission_id_prevents_same_second_collision(self):
        path = self._write_submission_csv(
            [
                {
                    "提交ID": "submission-1",
                    "用户ID": "user-1",
                    "题目ID": "0",
                    "题型": "MULTIPLE_CHOICE",
                    "状态": "OVERRIDDEN",
                    "分数": "0",
                    "编译器": "NO_COMPILER",
                    "提交时间": "2026-07-01T00:00:00Z",
                },
                {
                    "提交ID": "submission-2",
                    "用户ID": "user-1",
                    "题目ID": "0",
                    "题型": "MULTIPLE_CHOICE",
                    "状态": "OVERRIDDEN",
                    "分数": "0",
                    "编译器": "NO_COMPILER",
                    "提交时间": "2026-07-01T00:00:00Z",
                },
            ]
        )
        rows = sync._read_submission_rows(path)
        self.assertEqual(rows[0]["pta_problem_id"], "MULTIPLE_CHOICE:0")
        self.assertNotEqual(
            sync._attempt_source_key(1, rows[0], "20250001"),
            sync._attempt_source_key(1, rows[1], "20250001"),
        )

    def test_incomplete_submission_snapshot_is_rejected(self):
        path = self._write_submission_csv(
            [
                {
                    "提交ID": "submission-1",
                    "用户ID": "user-1",
                    "题目ID": "problem-1",
                    "题型": "PROGRAMMING",
                    "提交时间": "2026-07-01T00:00:00Z",
                }
            ]
        )
        (self.tmp / sync.SUBMISSION_CRAWL_STATUS_FILE).write_text(
            json.dumps(
                {
                    "complete": False,
                    "rows": 1,
                    "incomplete_user_ids": ["user-1"],
                }
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "submission crawl is incomplete"):
            sync._validate_experiment_snapshot(
                self.tmp,
                {"SUBMISSIONS": path},
                [],
                sync._read_submission_rows(path),
            )

    def test_legacy_submission_csv_without_submission_id_is_rejected(self):
        path = self._write_submission_csv(
            [
                {
                    "用户ID": "user-1",
                    "题目ID": "problem-1",
                    "题型": "PROGRAMMING",
                    "提交时间": "2026-07-01T00:00:00Z",
                }
            ]
        )
        (self.tmp / sync.SUBMISSION_CRAWL_STATUS_FILE).write_text(
            json.dumps(
                {
                    "problem_set_id": "ps-1",
                    "scope": "PTA_USER_GROUP_MEMBERS",
                    "complete": True,
                    "rows": 1,
                    "queried_user_count": 1,
                    "incomplete_user_ids": [],
                }
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "has no PTA submission ID"):
            sync._validate_experiment_snapshot(
                self.tmp,
                {"SUBMISSIONS": path},
                [],
                sync._read_submission_rows(path),
                expected_group_member_count=1,
            )

    def test_complete_global_snapshot_filtered_by_roster_is_accepted(self):
        self.assertTrue(
            sync._submission_scope_covers_roster(
                {
                    "strategy": "GLOBAL_COMPLETE_THEN_LOCAL_FILTER",
                    "queried_user_count": 0,
                    "global_query": {
                        "complete": True,
                        "hit_server_cap": False,
                    },
                },
                58,
            )
        )

    def test_empty_problem_content_is_rejected_before_db_sync(self):
        detail_path = self.tmp / "题目详情.json"
        detail_path.write_text(
            json.dumps(
                [
                    {
                        "problem_set_id": "ps-1",
                        "problem_set_problem_id": "problem-1",
                        "content_md": "这是一个编程题模板",
                    }
                ],
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        (self.tmp / sync.PROBLEM_CRAWL_STATUS_FILE).write_text(
            json.dumps(
                {
                    "complete": True,
                    "detail_problem_count": 1,
                    "failed_problem_ids": [],
                    "invalid_content_problem_ids": [],
                }
            ),
            encoding="utf-8",
        )
        rows = sync._read_problem_detail_rows(detail_path)
        with self.assertRaisesRegex(RuntimeError, "empty/template content"):
            sync._validate_experiment_snapshot(
                self.tmp,
                {"PROBLEM_DETAILS": detail_path},
                rows,
                [],
            )

    def test_roster_count_mismatch_is_rejected(self):
        roster_path = self.tmp / sync.PTA_USER_GROUP_ROSTER_FILE
        roster_path.write_text(
            json.dumps(
                {
                    "group": {"pta_group_id": "group-1"},
                    "member_count": 1,
                    "reported_total": 2,
                    "members": [
                        {
                            "student_no": "20250001",
                            "student_name": "测试学生",
                            "pta_user_id": "pta-user-1",
                        }
                    ],
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "roster is incomplete"):
            sync._load_pta_user_group_roster(self.tmp)

    def test_explicit_class_id_cannot_bypass_stable_group_binding(self):
        cursor = _BindingCursor(("configured-group", "配置组", "配置组"))
        roster = {
            "group": {
                "pta_group_id": "different-group",
                "pta_group_name": "另一个组",
            }
        }
        with self.assertRaisesRegex(RuntimeError, "does not match teaching_class binding"):
            sync._validate_class_group_binding(cursor, 1, roster)

    def test_matching_stable_group_id_allows_display_name_variance(self):
        cursor = _BindingCursor(("group-1", "旧显示名称", "旧显示名称"))
        roster = {
            "group": {
                "pta_group_id": "group-1",
                "pta_group_name": "PTA 当前显示名称",
            }
        }
        sync._validate_class_group_binding(cursor, 1, roster)


if __name__ == "__main__":
    unittest.main()
