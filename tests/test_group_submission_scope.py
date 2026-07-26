import sys
import unittest
from pathlib import Path
from unittest.mock import patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import PTAClient


class GroupSubmissionScopeTests(unittest.TestCase):
    def setUp(self):
        self.client = PTAClient.__new__(PTAClient)

    def test_group_roster_is_queried_per_user_and_merged(self):
        rows = {
            "user-a": [
                {"id": "s1", "userId": "user-a", "problemType": "PROGRAMMING"},
                {"id": "s2", "userId": "user-a", "problemType": "PROGRAMMING"},
            ],
            "user-b": [
                {"id": "s3", "userId": "user-b", "problemType": "PROGRAMMING"},
            ],
        }

        def fake_get(ps_id, page=0, limit=200, filter_obj=None):
            self.assertEqual(ps_id, "ps-1")
            self.assertEqual(page, 0)
            self.assertEqual(limit, 200)
            self.assertEqual(filter_obj["problemType"], "PROGRAMMING")
            return {"submissions": rows[filter_obj["userId"]], "total": 0}

        self.client._active_group_user_ids = ["user-b", "user-a"]
        with patch.object(self.client, "get_submissions", side_effect=fake_get):
            result = self.client.get_all_submissions("ps-1")

        self.assertEqual([row["id"] for row in result], ["s1", "s2", "s3"])
        status = self.client._submission_crawl_status["ps-1"]
        self.assertEqual(status["scope"], "PTA_USER_GROUP_MEMBERS")
        self.assertEqual(status["queried_user_count"], 2)
        self.assertTrue(status["complete"])

    def test_exactly_200_repeated_user_rows_are_marked_incomplete(self):
        expected_page_rows = [
            {
                "id": f"s-{index}",
                "userId": "user-a",
                "problemType": "PROGRAMMING",
            }
            for index in range(200)
        ]

        def fake_get(ps_id, page=0, limit=200, filter_obj=None):
            return {"submissions": list(expected_page_rows), "total": 0}

        with patch.object(self.client, "get_submissions", side_effect=fake_get):
            result = self.client.get_all_submissions(
                "ps-2",
                pta_user_ids=["user-a"],
            )

        self.assertEqual(len(result), 200)
        status = self.client._submission_crawl_status["ps-2"]
        self.assertFalse(status["complete"])
        self.assertEqual(status["incomplete_user_ids"], ["user-a"])

    def test_global_snapshot_repetition_is_not_reported_complete(self):
        expected_page_rows = [
            {"id": f"s-{index}", "problemType": "PROGRAMMING"}
            for index in range(200)
        ]

        def fake_get(ps_id, page=0, limit=200, filter_obj=None):
            self.assertEqual(filter_obj, {"problemType": "PROGRAMMING"})
            return {"submissions": list(expected_page_rows), "total": 0}

        with patch.object(self.client, "get_submissions", side_effect=fake_get):
            result = self.client.get_all_submissions(
                "ps-3",
                pta_user_ids=[],
            )

        self.assertEqual(len(result), 200)
        status = self.client._submission_crawl_status["ps-3"]
        self.assertEqual(status["scope"], "PROBLEM_SET_SNAPSHOT")
        self.assertFalse(status["complete"])

    def test_non_programming_rows_are_excluded_even_if_pta_ignores_filter(self):
        rows = [
            {"id": "programming", "problemType": "PROGRAMMING"},
            {"id": "choice", "problemType": "MULTIPLE_CHOICE"},
            {"id": "completion", "problemType": "CODE_COMPLETION"},
        ]

        def fake_get(ps_id, page=0, limit=200, filter_obj=None):
            return {"submissions": rows, "total": len(rows)}

        with patch.object(self.client, "get_submissions", side_effect=fake_get):
            result = self.client.get_all_submissions(
                "ps-4",
                pta_user_ids=["user-a"],
            )

        self.assertEqual([row["id"] for row in result], ["programming"])
        status = self.client._submission_crawl_status["ps-4"]
        self.assertEqual(status["problem_type"], "PROGRAMMING")
        self.assertEqual(status["rows"], 1)


if __name__ == "__main__":
    unittest.main()
