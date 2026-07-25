import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import requests


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import PTAClient
from pta_spider.spider_api import (
    _export_group_answer_or_warn,
    _summarize_group_answer_error,
)


class GroupAnswerExportRetryTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = PTAClient.__new__(PTAClient)

    @staticmethod
    def _http_error(status_code: int) -> requests.HTTPError:
        response = requests.Response()
        response.status_code = status_code
        return requests.HTTPError(
            f"{status_code} Client Error: test failure",
            response=response,
        )

    def test_404_recreates_export_task_for_each_retry(self) -> None:
        calls = []

        def fake_export(**kwargs: object) -> str:
            calls.append(kwargs)
            if len(calls) < 3:
                raise self._http_error(404)
            return "downloaded"

        with patch.object(self.client, "export_group_answer_sheets", side_effect=fake_export), \
             patch("pta_spider.spider.EXPORT_RETRY_ROUNDS", 2), \
             patch("pta_spider.spider.EXPORT_RETRY_DELAY_SECONDS", 0), \
             patch("pta_spider.spider.time.sleep"):
            result = self.client.export_group_answer_sheets_with_retry(
                group_id="group-1",
                group_name="测试用户组",
                crawl_dir=Path("output"),
            )

        self.assertEqual(result, "downloaded")
        self.assertEqual(len(calls), 3)

    def test_non_retryable_error_stops_immediately(self) -> None:
        calls = []

        def fake_export(**kwargs: object) -> str:
            calls.append(kwargs)
            raise self._http_error(403)

        with patch.object(self.client, "export_group_answer_sheets", side_effect=fake_export), \
             patch("pta_spider.spider.EXPORT_RETRY_ROUNDS", 2), \
             patch("pta_spider.spider.time.sleep"):
            with self.assertRaises(requests.HTTPError):
                self.client.export_group_answer_sheets_with_retry(group_id="group-1")

        self.assertEqual(len(calls), 1)


class GroupAnswerExportPolicyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = PTAClient.__new__(PTAClient)
        self.client.export_group_answer_sheets_with_retry = lambda **kwargs: (_ for _ in ()).throw(
            RuntimeError("404 Client Error: missing COS object")
        )
        self.task = type(
            "Task",
            (),
            {"group_id": "group-1", "group_name": "测试用户组"},
        )()

    def test_default_policy_returns_warning_and_continues(self) -> None:
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("PTA_GROUP_ANSWER_EXPORT_REQUIRED", None)
            warning = _export_group_answer_or_warn(
                self.client, self.task, Path("output")
            )

        self.assertIsNotNone(warning)
        self.assertIn("用户组答卷导出失败", warning)

    def test_required_policy_raises(self) -> None:
        with patch.dict(os.environ, {"PTA_GROUP_ANSWER_EXPORT_REQUIRED": "true"}):
            with self.assertRaises(RuntimeError):
                _export_group_answer_or_warn(self.client, self.task, Path("output"))

    def test_warning_summary_redacts_signed_url(self) -> None:
        detail = _summarize_group_answer_error(
            RuntimeError(
                "404 Client Error: Not Found for url: "
                "https://example.com/export.zip?q-signature=secret"
            )
        )
        self.assertIn("<signed-url>", detail)
        self.assertNotIn("q-signature=secret", detail)


if __name__ == "__main__":
    unittest.main()
