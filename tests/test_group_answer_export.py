import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch

import requests


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import PTAClient
from pta_spider.group_exports import split_group_answer_export
from pta_spider.spider_api import (
    _drain_callback_outbox,
    _export_group_answer_or_warn,
    _load_callback_outbox,
    _notify_java,
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

    def test_aggregated_create_400_and_404_is_retried(self) -> None:
        calls = []

        def fake_export(**kwargs: object) -> str:
            calls.append(kwargs)
            if len(calls) == 1:
                raise RuntimeError(
                    "failed to create export; tried: 400: primary; 404: fallback"
                )
            return "downloaded"

        with patch.object(self.client, "export_group_answer_sheets", side_effect=fake_export), \
             patch("pta_spider.spider.EXPORT_RETRY_ROUNDS", 1), \
             patch("pta_spider.spider.EXPORT_RETRY_DELAY_SECONDS", 0), \
             patch("pta_spider.spider.time.sleep"):
            result = self.client.export_group_answer_sheets_with_retry(
                group_id="group-1"
            )

        self.assertEqual(result, "downloaded")
        self.assertEqual(len(calls), 2)

    def test_download_retries_ready_object_404_before_success(self) -> None:
        not_ready = requests.Response()
        not_ready.status_code = 404
        not_ready.url = "https://example.com/export.zip"
        not_ready.raw = Mock()
        ready = requests.Response()
        ready.status_code = 200
        ready.url = "https://example.com/export.zip"
        ready._content = b"export-data"
        ready.headers["Content-Length"] = str(len(ready.content))

        with tempfile.TemporaryDirectory() as tmp_dir, \
             patch(
                 "pta_spider.spider.requests.get",
                 side_effect=[not_ready, ready],
             ) as get_mock, \
             patch("pta_spider.spider.EXPORT_DOWNLOAD_RETRIES", 2), \
             patch("pta_spider.spider.EXPORT_DOWNLOAD_RETRY_DELAY_SECONDS", 0), \
             patch.object(self.client, "_validate_downloaded_export"):
            save_path = Path(tmp_dir) / "export.zip"
            self.client.download_export(
                "https://example.com/export.zip",
                str(save_path),
            )
            self.assertEqual(save_path.read_bytes(), b"export-data")

        self.assertEqual(get_mock.call_count, 2)


class GroupAnswerSplitTests(unittest.TestCase):
    def test_non_target_experiment_is_not_written(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "group.zip"
            with zipfile.ZipFile(
                source,
                "w",
                compression=zipfile.ZIP_DEFLATED,
            ) as archive:
                archive.writestr(
                    "experiment-1/test-group/html/20250001-student.html",
                    "<html><title>experiment-1</title></html>",
                )

            result = split_group_answer_export(
                source,
                root / "crawl",
                group_name="test-group",
                experiment_names={"experiment-2"},
            )

            self.assertEqual(result["written"], [])
            self.assertEqual(
                result["skipped"]["non_target_experiment"],
                1,
            )
            self.assertFalse((root / "crawl").exists())


class GroupAnswerExportPolicyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = PTAClient.__new__(PTAClient)
        self.client.export_group_answer_sheets_with_retry = lambda **kwargs: (_ for _ in ()).throw(
            RuntimeError("404 Client Error: missing COS object")
        )
        self.task = type(
            "Task",
            (),
            {
                "group_id": "group-1",
                "group_name": "测试用户组",
                "submissions_count": 1,
            },
        )()

    def test_default_policy_requires_answers_when_submissions_exist(self) -> None:
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("PTA_GROUP_ANSWER_EXPORT_REQUIRED", None)
            with self.assertRaises(RuntimeError):
                _export_group_answer_or_warn(
                    self.client, self.task, Path("output")
                )

    def test_default_policy_warns_when_no_submissions_exist(self) -> None:
        self.task.submissions_count = 0
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("PTA_GROUP_ANSWER_EXPORT_REQUIRED", None)
            warning = _export_group_answer_or_warn(
                self.client, self.task, Path("output")
            )

        self.assertIsNotNone(warning)
        self.assertIn("用户组答卷导出失败", warning)

    def test_optional_policy_can_continue_with_submissions(self) -> None:
        with patch.dict(os.environ, {"PTA_GROUP_ANSWER_EXPORT_REQUIRED": "false"}):
            warning = _export_group_answer_or_warn(
                self.client, self.task, Path("output")
            )

        self.assertIsNotNone(warning)

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


class BackendCallbackOutboxTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.outbox = Path(self.tmp.name) / "callback-outbox.json"

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_failed_callback_is_persisted_and_retried(self) -> None:
        with patch(
            "pta_spider.spider_api.CALLBACK_OUTBOX_FILE", self.outbox
        ), patch(
            "pta_spider.spider_api._send_java_callback", return_value=False
        ):
            self.assertFalse(_notify_java(7, "SUCCESS", "task-1"))
            queued = _load_callback_outbox()

        self.assertEqual(len(queued), 1)
        self.assertEqual(queued[0]["class_id"], 7)
        self.assertEqual(queued[0]["task_id"], "task-1")

        with patch(
            "pta_spider.spider_api.CALLBACK_OUTBOX_FILE", self.outbox
        ), patch(
            "pta_spider.spider_api._send_java_callback", return_value=True
        ):
            self.assertEqual(_drain_callback_outbox(), 1)
            self.assertEqual(_load_callback_outbox(), [])

    def test_newer_status_replaces_queued_status_for_same_task(self) -> None:
        with patch(
            "pta_spider.spider_api.CALLBACK_OUTBOX_FILE", self.outbox
        ), patch(
            "pta_spider.spider_api._send_java_callback", return_value=False
        ):
            _notify_java(7, "FAILED", "task-1")
            _notify_java(7, "SUCCESS", "task-1")
            queued = _load_callback_outbox()

        self.assertEqual(len(queued), 1)
        self.assertEqual(queued[0]["status"], "SUCCESS")


if __name__ == "__main__":
    unittest.main()
