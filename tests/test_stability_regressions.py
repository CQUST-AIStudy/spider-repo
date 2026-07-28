import sys
import tempfile
import time
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import Mock, patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import AdaptiveTokenBucketRateLimiter, PTAClient
from pta_spider.spider_api import (
    CrawlMode,
    TaskInfo,
    TaskStatus,
    _bypass_phase_cooldown,
    _classify_problem_sets,
    _refresh_all_content,
    _run_crawl,
)


class StableRateLimiterTests(unittest.TestCase):
    def test_single_token_burst_prevents_startup_flood(self) -> None:
        limiter = AdaptiveTokenBucketRateLimiter(
            rate=20,
            per=1,
            rate_min=5,
            rate_max=60,
            burst=1,
        )
        limiter.acquire()
        started = time.monotonic()
        limiter.acquire()
        self.assertGreaterEqual(time.monotonic() - started, 0.03)


class DurableContentFallbackTests(unittest.TestCase):
    @staticmethod
    def _client() -> PTAClient:
        client = PTAClient.__new__(PTAClient)
        client.crawl_dir = Path(tempfile.mkdtemp())
        client.history = type(
            "History",
            (),
            {
                "get_new_sets": lambda self, values: [],
                "is_crawled": lambda self, problem_set_id: True,
            },
        )()
        return client

    def test_verified_database_content_avoids_recrawl_after_cache_loss(self) -> None:
        problem_set = {"id": "ps-1", "name": "experiment-1"}
        pending = self._client().get_sets_requiring_content(
            [problem_set],
            persisted_content_checker=lambda value: value["id"] == "ps-1",
        )
        self.assertEqual(pending, [])

    def test_missing_cache_and_database_content_is_recrawled(self) -> None:
        problem_set = {"id": "ps-1", "name": "experiment-1"}
        pending = self._client().get_sets_requiring_content(
            [problem_set],
            persisted_content_checker=lambda value: False,
        )
        self.assertEqual(pending, [problem_set])

    def test_empty_local_history_still_uses_database_fallback(self) -> None:
        client = self._client()
        client.history = type(
            "History",
            (),
            {
                "get_new_sets": lambda self, values: list(values),
                "is_crawled": lambda self, problem_set_id: False,
            },
        )()
        problem_set = {"id": "ps-1", "name": "experiment-1"}

        pending = client.get_sets_requiring_content(
            [problem_set],
            persisted_content_checker=lambda value: True,
        )

        self.assertEqual(pending, [])


class SyncModeSemanticsTests(unittest.TestCase):
    def test_incremental_force_does_not_recrawl_all_problem_content(self) -> None:
        self.assertFalse(_refresh_all_content(CrawlMode.INCREMENTAL))
        self.assertTrue(
            _bypass_phase_cooldown(CrawlMode.INCREMENTAL, force=True)
        )

    def test_full_mode_refreshes_content_and_dynamic_phases(self) -> None:
        self.assertTrue(_refresh_all_content(CrawlMode.FULL))
        self.assertTrue(_bypass_phase_cooldown(CrawlMode.FULL, force=False))


class ProblemSetClassificationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.now = datetime(2026, 7, 28, 12, 0, 0)

    def test_closed_finalized_complete_set_is_zero_request(self) -> None:
        problem_set = {
            "id": "ps-1",
            "name": "closed-complete",
            "deadline": self.now - timedelta(hours=1),
        }
        state = {
            "ps-1": {
                "content_complete": True,
                "transcript_complete": True,
                "answer_complete": True,
                "submission_complete": True,
                "finalized_at": self.now - timedelta(minutes=30),
            }
        }
        with patch("pta_spider.spider_api.FINALIZE_GRACE_SECONDS", 0):
            decision = _classify_problem_sets(
                [problem_set],
                state,
                CrawlMode.INCREMENTAL,
                now=self.now,
            )[0]
        self.assertEqual(decision["decision"], "CLOSED_COMPLETE")
        self.assertFalse(decision["target"])

    def test_closed_unfinalized_set_gets_one_final_sync(self) -> None:
        problem_set = {
            "id": "ps-1",
            "name": "closed-pending",
            "deadline": self.now - timedelta(hours=1),
        }
        with patch("pta_spider.spider_api.FINALIZE_GRACE_SECONDS", 0):
            decision = _classify_problem_sets(
                [problem_set],
                {},
                CrawlMode.INCREMENTAL,
                now=self.now,
                durable_content_checker=lambda _: True,
            )[0]
        self.assertEqual(decision["decision"], "CLOSED_PENDING_FINAL")
        self.assertTrue(decision["target"])
        self.assertFalse(decision["needs_content"])

    def test_open_set_updates_dynamic_data_without_content_recrawl(self) -> None:
        problem_set = {
            "id": "ps-1",
            "name": "open",
            "deadline": self.now + timedelta(days=1),
        }
        decision = _classify_problem_sets(
            [problem_set],
            {},
            CrawlMode.INCREMENTAL,
            now=self.now,
            durable_content_checker=lambda _: True,
        )[0]
        self.assertEqual(decision["decision"], "OPEN")
        self.assertTrue(decision["target"])
        self.assertFalse(decision["needs_content"])

    def test_full_mode_targets_even_finalized_closed_set(self) -> None:
        problem_set = {
            "id": "ps-1",
            "name": "closed-complete",
            "deadline": self.now - timedelta(hours=1),
        }
        state = {
            "ps-1": {
                "content_complete": True,
                "transcript_complete": True,
                "answer_complete": True,
                "submission_complete": True,
                "finalized_at": self.now,
            }
        }
        with patch("pta_spider.spider_api.FINALIZE_GRACE_SECONDS", 0):
            decision = _classify_problem_sets(
                [problem_set],
                state,
                CrawlMode.FULL,
                now=self.now,
            )[0]
        self.assertTrue(decision["target"])
        self.assertTrue(decision["needs_content"])


class GroupExportOrderingTests(unittest.TestCase):
    def test_problem_sets_are_classified_before_group_exports(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            events = []
            experiment_name = "experiment-1"
            history = type(
                "History",
                (),
                {
                    "mark_export_refreshed": lambda self, value: None,
                },
            )()
            client = type(
                "Client",
                (),
                {
                    "crawl_dir": Path(tmp_dir),
                    "history": history,
                    "ensure_login": lambda self: True,
                    "write_user_group_roster": lambda self, **kwargs: {
                        "group": {
                            "pta_group_id": "group-1",
                            "pta_group_name": "test-group",
                        }
                    },
                },
            )()
            task = TaskInfo(
                "task-1",
                "test-group",
                3,
                None,
                None,
                "group-1",
                "test-group",
                CrawlMode.REFRESH,
            )

            def export_group(client_arg, task_arg, crawl_dir, **kwargs):
                events.append("group-export")
                (Path(crawl_dir) / "_group_exports").mkdir(
                    parents=True,
                    exist_ok=True,
                )
                (Path(crawl_dir) / experiment_name).mkdir(
                    parents=True,
                    exist_ok=True,
                )
                self.assertEqual(
                    set(kwargs["experiment_names"]),
                    {experiment_name},
                )
                return {
                    "written": [
                        {
                            "experiment_name": experiment_name,
                            "path": "experiment-1-ANSWER_SHEET.zip",
                        }
                    ]
                }

            def export_transcript(client_arg, task_arg, crawl_dir, **kwargs):
                events.append("group-transcript")
                self.assertEqual(
                    set(kwargs["experiment_names"]),
                    {experiment_name},
                )
                return {
                    "written": [
                        {
                            "experiment_name": experiment_name,
                            "path": "experiment-1-PAPER_TRANSCRIPT.xlsx",
                        }
                    ]
                }

            def resolve_sets(client_arg, task_arg):
                events.append("problem-sets")
                return [
                    {
                        "id": "ps-1",
                        "name": experiment_name,
                        "deadline": datetime.now() + timedelta(days=1),
                    }
                ]

            def sync_to_database(**kwargs):
                events.append("database-sync")
                self.assertEqual(
                    kwargs["experiment_names"],
                    [experiment_name],
                )
                return {"ok": True}

            with patch(
                "pta_spider.spider_api.PTAClient",
                return_value=client,
            ), patch(
                "pta_spider.spider_api.class_id_exists",
                return_value=True,
            ), patch(
                "pta_spider.spider_api.validate_class_id_for_roster",
            ), patch(
                "pta_spider.spider_api._export_group_answer_or_warn",
                side_effect=export_group,
            ), patch(
                "pta_spider.spider_api._export_group_transcript_or_warn",
                side_effect=export_transcript,
            ), patch(
                "pta_spider.spider_api._resolve_problem_sets",
                side_effect=resolve_sets,
            ), patch(
                "pta_spider.spider_api._load_problem_set_sync_states",
                return_value={},
            ), patch(
                "pta_spider.spider_api._database_has_experiment_data",
                return_value=True,
            ), patch(
                "pta_spider.spider_api.run_configured_sync",
                side_effect=sync_to_database,
            ), patch(
                "pta_spider.spider_api._save_problem_set_sync_states",
            ), patch(
                "pta_spider.spider_api._notify_java",
            ):
                _run_crawl(task)

        self.assertEqual(task.status, TaskStatus.SUCCESS)
        self.assertEqual(
            events,
            [
                "problem-sets",
                "group-export",
                "group-transcript",
                "database-sync",
            ],
        )

    def test_no_target_skips_exports_and_database_sync(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            client = type(
                "Client",
                (),
                {
                    "crawl_dir": Path(tmp_dir),
                    "ensure_login": lambda self: True,
                    "write_user_group_roster": lambda self, **kwargs: {
                        "group": {
                            "pta_group_id": "group-1",
                            "pta_group_name": "test-group",
                        }
                    },
                },
            )()
            task = TaskInfo(
                "task-2",
                "test-group",
                3,
                None,
                None,
                "group-1",
                "test-group",
                CrawlMode.INCREMENTAL,
            )
            closed_set = {
                "id": "ps-closed",
                "name": "closed-complete",
                "deadline": datetime.now() - timedelta(days=1),
            }
            complete_state = {
                "ps-closed": {
                    "content_complete": True,
                    "transcript_complete": True,
                    "answer_complete": True,
                    "submission_complete": True,
                    "finalized_at": datetime.now() - timedelta(hours=1),
                }
            }
            answer_export = Mock()
            transcript_export = Mock()
            database_sync = Mock()

            with patch(
                "pta_spider.spider_api.PTAClient",
                return_value=client,
            ), patch(
                "pta_spider.spider_api.class_id_exists",
                return_value=True,
            ), patch(
                "pta_spider.spider_api.validate_class_id_for_roster",
            ), patch(
                "pta_spider.spider_api._resolve_problem_sets",
                return_value=[closed_set],
            ), patch(
                "pta_spider.spider_api._load_problem_set_sync_states",
                return_value=complete_state,
            ), patch(
                "pta_spider.spider_api._export_group_answer_or_warn",
                answer_export,
            ), patch(
                "pta_spider.spider_api._export_group_transcript_or_warn",
                transcript_export,
            ), patch(
                "pta_spider.spider_api.run_configured_sync",
                database_sync,
            ), patch(
                "pta_spider.spider_api._notify_java",
            ):
                _run_crawl(task)

        self.assertEqual(task.status, TaskStatus.SUCCESS)
        self.assertEqual(task.target_sets_count, 0)
        self.assertEqual(task.skipped_closed_count, 1)
        answer_export.assert_not_called()
        transcript_export.assert_not_called()
        database_sync.assert_not_called()


if __name__ == "__main__":
    unittest.main()
