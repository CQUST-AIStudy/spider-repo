"""
性能优化单元测试：
1. 题目详情并发拉取：输出顺序、内容、单题失败隔离、并发提速
2. 用户组答卷导出 filter 锁定：锁定后 api_get 调用次数下降
3. 自适应限流：429 降速、成功恢复
4. 同题集导出并行：多类型 wall-clock 低于串行
5. 题集并行 helper：错误隔离
全部使用 mock，不依赖真实 PTA 登录与网络。
"""
import sys
import json
import time
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import PTAClient, AdaptiveTokenBucketRateLimiter


def _make_detail(pid):
    """构造 get_problem_detail 的假返回值"""
    return {"problemSetProblem": {"id": pid, "content": f"内容-{pid}"}}


class CrawlProblemSetConcurrencyTests(unittest.TestCase):
    """题目详情并发拉取"""

    def setUp(self):
        # 绕过 __init__ 的登录逻辑，直接构造裸实例
        self.client = PTAClient.__new__(PTAClient)
        self.tmp = Path(tempfile.mkdtemp())

    def _fake_dir(self, name):
        d = self.tmp / name
        d.mkdir(parents=True, exist_ok=True)
        return d

    def _patch_externals(self, problems, detail_side_effect):
        """统一 mock 掉网络/导出/提交相关方法，只保留题目详情逻辑"""
        return (
            patch.object(self.client, "get_problems", return_value=problems),
            patch.object(self.client, "get_problem_detail", side_effect=detail_side_effect),
            patch.object(self.client, "get_all_submissions", return_value=[]),
            patch.object(self.client, "_export_required_files"),
            patch.object(self.client, "_required_export_configs", return_value=[]),
        )

    def test_concurrent_fetch_preserves_order_and_content(self):
        problems = [
            {"id": f"pid-{i}", "title": f"题{i}", "label": f"L{i}"}
            for i in range(5)
        ]
        self.client._problem_set_dir = self._fake_dir
        patches = self._patch_externals(problems, lambda ps_id, pid: _make_detail(pid))
        with patches[0], patches[1], patches[2], patches[3], patches[4], \
             patch("pta_spider.spider.time.sleep"):
            self.client._crawl_one_problem_set("ps1", "测试集", export_answer_sheet=False)

        content = (self.tmp / "测试集" / "题目内容.txt").read_text(encoding="utf-8")
        for i in range(5):
            self.assertIn(f"[L{i}] 题{i}", content)
            self.assertIn(f"内容-pid-{i}", content)
        # 顺序保持与输入一致
        pos = [content.index(f"题{i}") for i in range(5)]
        self.assertEqual(pos, sorted(pos), "题目顺序应保持原序")

        detail_json = json.loads((self.tmp / "测试集" / "题目详情.json").read_text(encoding="utf-8"))
        self.assertEqual(len(detail_json), 5)
        self.assertEqual(
            [r["problem_set_problem_id"] for r in detail_json],
            [f"pid-{i}" for i in range(5)],
        )

    def test_single_detail_failure_isolated(self):
        problems = [
            {"id": "good-1", "title": "好题1", "label": "A"},
            {"id": "bad", "title": "坏题", "label": "B"},
            {"id": "good-2", "title": "好题2", "label": "C"},
        ]

        def fake_detail(ps_id, pid):
            if pid == "bad":
                raise RuntimeError("boom")
            return _make_detail(pid)

        self.client._problem_set_dir = self._fake_dir
        patches = self._patch_externals(problems, fake_detail)
        with patches[0], patches[1], patches[2], patches[3], patches[4], \
             patch("pta_spider.spider.time.sleep"), self.assertRaises(RuntimeError):
            self.client._crawl_one_problem_set("ps1", "测试集2", export_answer_sheet=False)

        content = (self.tmp / "测试集2" / "题目内容.txt").read_text(encoding="utf-8")
        self.assertIn("内容-good-1", content)
        self.assertIn("内容-good-2", content)
        self.assertIn("(获取详情失败: boom)", content)
        detail_json = json.loads((self.tmp / "测试集2" / "题目详情.json").read_text(encoding="utf-8"))
        # 坏题不计入 JSON，好题 2 条
        self.assertEqual(len(detail_json), 2)
        status = json.loads((self.tmp / "测试集2" / "problem_crawl_status.json").read_text(encoding="utf-8"))
        self.assertFalse(status["complete"])
        self.assertEqual(status["failed_problem_ids"], ["bad"])

    def test_empty_problem_content_blocks_problem_set_completion(self):
        problems = [{"id": "empty-content", "title": "空题面", "label": "1"}]
        self.client._problem_set_dir = self._fake_dir
        patches = self._patch_externals(
            problems,
            lambda ps_id, pid: {
                "problemSetProblem": {
                    "id": pid,
                    "title": "空题面",
                    "content": "",
                    "description": "",
                }
            },
        )
        with patches[0], patches[1], patches[2], patches[3], patches[4], \
             self.assertRaises(RuntimeError):
            self.client._crawl_one_problem_set(
                "ps-empty-content",
                "空题面题目集",
                export_answer_sheet=False,
            )

        status = json.loads(
            (
                self.tmp
                / "空题面题目集"
                / "problem_crawl_status.json"
            ).read_text(encoding="utf-8")
        )
        self.assertFalse(status["complete"])
        self.assertEqual(status["failed_problem_ids"], ["empty-content"])
        self.assertEqual(
            status["invalid_content_problem_ids"],
            ["empty-content"],
        )

    def test_detail_fetch_is_concurrent(self):
        """5 题每题 sleep 0.4s：串行需 >=2s，并发应明显低于串行"""
        problems = [
            {"id": f"pid-{i}", "title": f"题{i}", "label": f"L{i}"}
            for i in range(5)
        ]

        def slow_detail(ps_id, pid):
            time.sleep(0.4)
            return _make_detail(pid)

        self.client._problem_set_dir = self._fake_dir
        patches = self._patch_externals(problems, slow_detail)
        start = time.time()
        # 只压制末尾 random.uniform 产生的额外 sleep，保留 slow_detail 的 sleep
        with patches[0], patches[1], patches[2], patches[3], patches[4], \
             patch("pta_spider.spider.random.uniform", return_value=0):
            self.client._crawl_one_problem_set("ps1", "测试集3", export_answer_sheet=False)
        elapsed = time.time() - start

        # 串行 5*0.4=2.0s；并发 max_workers=5 约 0.4s。放宽到 1.5s 区分
        self.assertLess(elapsed, 1.5, f"并发拉取应快于串行(2.0s)，实际 {elapsed:.2f}s")

    def test_empty_problem_set_writes_explicit_complete_files(self):
        self.client._problem_set_dir = self._fake_dir
        patches = self._patch_externals([], lambda ps_id, pid: None)
        with patches[0], patches[1], patches[2], patches[3], patches[4]:
            self.client._crawl_one_problem_set("ps-empty", "空题目集", export_answer_sheet=False)

        self.assertEqual((self.tmp / "空题目集" / "题目内容.txt").read_text(encoding="utf-8"), "")
        self.assertEqual(
            json.loads((self.tmp / "空题目集" / "题目详情.json").read_text(encoding="utf-8")),
            [],
        )
        status = json.loads((self.tmp / "空题目集" / "problem_crawl_status.json").read_text(encoding="utf-8"))
        self.assertTrue(status["complete"])
        self.assertEqual(status["listed_problem_count"], 0)

    def test_problem_list_paginates_and_deduplicates(self):
        pages = {
            0: {"problemSetProblems": [{"id": "p1"}, {"id": "p2"}], "total": 3},
            1: {"problemSetProblems": [{"id": "p2"}, {"id": "p3"}], "total": 3},
        }

        def fake_api_get(path, params=None):
            self.assertEqual(params["problem_type"], "PROGRAMMING")
            return pages[params["page"]]

        with patch.object(self.client, "api_get", side_effect=fake_api_get):
            problems = self.client.get_problems("ps1", problem_type="PROGRAMMING", limit=2)

        self.assertEqual([problem["id"] for problem in problems], ["p1", "p2", "p3"])

    def test_submission_evidence_adds_random_pool_problem_details(self):
        self.client._problem_set_dir = self._fake_dir
        with patch.object(
            self.client,
            "get_problems",
            return_value=[{"id": "p1", "title": "Preview", "label": "1"}],
        ), patch.object(
            self.client,
            "get_problem_detail",
            side_effect=lambda ps_id, pid: _make_detail(pid),
        ), patch.object(
            self.client,
            "get_all_submissions",
            return_value=[
                {"problemSetProblemId": "p1"},
                {"problemSetProblemId": "p2"},
            ],
        ), patch.object(
            self.client,
            "_export_required_files",
        ), patch.object(
            self.client,
            "_required_export_configs",
            return_value=[],
        ):
            summary = self.client._crawl_one_problem_set("ps1", "随机题池", export_answer_sheet=False)

        details = json.loads((self.tmp / "随机题池" / "题目详情.json").read_text(encoding="utf-8"))
        self.assertEqual(
            [row["problem_set_problem_id"] for row in details],
            ["p1", "p2"],
        )
        self.assertEqual(summary["problem_count"], 2)
        status = json.loads((self.tmp / "随机题池" / "problem_crawl_status.json").read_text(encoding="utf-8"))
        self.assertEqual(status["submission_evidence_problem_count"], 2)
        self.assertTrue(status["complete"])

    def test_subjective_submission_is_not_required_as_programming_detail(self):
        self.client._problem_set_dir = self._fake_dir
        with patch.object(
            self.client,
            "get_problems",
            return_value=[{"id": "p1", "title": "Programming", "label": "1"}],
        ), patch.object(
            self.client,
            "get_problem_detail",
            side_effect=lambda ps_id, pid: _make_detail(pid),
        ) as detail_mock, patch.object(
            self.client,
            "get_all_submissions",
            return_value=[
                {
                    "problemSetProblemId": "subjective-1",
                    "problemType": "SUBJECTIVE",
                }
            ],
        ), patch.object(
            self.client,
            "_export_required_files",
        ), patch.object(
            self.client,
            "_required_export_configs",
            return_value=[],
        ):
            summary = self.client._crawl_one_problem_set(
                "ps1",
                "含主观题的题目集",
                export_answer_sheet=False,
            )

        self.assertEqual(summary["problem_count"], 1)
        detail_mock.assert_called_once_with("ps1", "p1")
        status = json.loads(
            (
                self.tmp
                / "含主观题的题目集"
                / "problem_crawl_status.json"
            ).read_text(encoding="utf-8")
        )
        self.assertTrue(status["complete"])
        self.assertEqual(
            status["ignored_non_programming_evidence_problem_ids"],
            ["subjective-1"],
        )

    def test_code_completion_problem_is_excluded_from_detail_crawl(self):
        self.client._problem_set_dir = self._fake_dir

        def fake_problems(ps_id, problem_type="PROGRAMMING", limit=200):
            if problem_type == "PROGRAMMING":
                return []
            if problem_type == "CODE_COMPLETION":
                return [
                    {
                        "id": "code-completion-1",
                        "title": "代码填空",
                        "label": "1",
                    }
                ]
            return []

        with patch.object(
            self.client,
            "get_problems",
            side_effect=fake_problems,
        ), patch.object(
            self.client,
            "get_problem_detail",
            side_effect=lambda ps_id, pid: _make_detail(pid),
        ), patch.object(
            self.client,
            "get_all_submissions",
            return_value=[],
        ), patch.object(
            self.client,
            "_export_required_files",
        ), patch.object(
            self.client,
            "_required_export_configs",
            return_value=[],
        ):
            summary = self.client._crawl_one_problem_set(
                "ps-code-completion",
                "代码填空题目集",
                export_answer_sheet=False,
            )

        self.assertEqual(summary["problem_count"], 0)
        details = json.loads(
            (
                self.tmp
                / "代码填空题目集"
                / "题目详情.json"
            ).read_text(encoding="utf-8")
        )
        self.assertEqual(
            [row["problem_set_problem_id"] for row in details],
            [],
        )


class ProblemDetailContentSelectionTests(unittest.TestCase):
    def test_zero_difficulty_and_score_are_preserved(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1", "score": 99},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "score": 0,
                    "difficulty": 0,
                },
                "problem": {"difficulty": 5},
            },
        )

        self.assertEqual(record["score"], 0)
        self.assertEqual(record["difficulty_level"], 0)

    def test_pta_template_content_uses_real_description(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1", "title": "选择排序"},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "problemId": "global-1",
                    "content": (
                        "这是一个编程题模板。\n\n"
                        "请在这里写题目描述。例如：本题要求输入两个整数。"
                    ),
                    "description": "使用选择排序将给定的整数排成从小到大的序列。",
                }
            },
        )

        self.assertEqual(
            record["content_md"],
            "使用选择排序将给定的整数排成从小到大的序列。",
        )

    def test_real_content_keeps_priority_over_description(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1"},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "content": "这是教师发布的正式题面。",
                    "description": "备用题面。",
                }
            },
        )

        self.assertEqual(record["content_md"], "这是教师发布的正式题面。")

    def test_html_wrapped_template_uses_real_description(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1"},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "content": "<p>\u200b这是一个编程题模板。</p>",
                    "description": "这是实际题目描述。",
                }
            },
        )

        self.assertEqual(record["content_md"], "这是实际题目描述。")
        self.assertTrue(PTAClient._problem_record_has_valid_content(record))

    def test_function_template_uses_real_description(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1"},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "content": "这是一个函数题模板，这里写题目要求。",
                    "description": "请实现二叉搜索树查找函数。",
                }
            },
        )

        self.assertEqual(record["content_md"], "请实现二叉搜索树查找函数。")

    def test_alternate_statement_field_is_supported(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "set-problem-1"},
            {
                "problemSetProblem": {
                    "id": "set-problem-1",
                    "content": "",
                    "description": "",
                    "statement": "来自备用字段的正式题面。",
                }
            },
        )

        self.assertEqual(record["content_md"], "来自备用字段的正式题面。")

    def test_empty_or_placeholder_only_content_is_invalid(self):
        empty_record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "empty"},
            {"problemSetProblem": {"id": "empty", "content": ""}},
        )
        placeholder_record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "placeholder"},
            {
                "problemSetProblem": {
                    "id": "placeholder",
                    "contentHtml": "<p>请在此处填写题目描述</p>",
                }
            },
        )

        self.assertFalse(PTAClient._problem_record_has_valid_content(empty_record))
        self.assertFalse(
            PTAClient._problem_record_has_valid_content(placeholder_record)
        )

    def test_image_only_statement_is_valid(self):
        record = PTAClient._problem_detail_record(
            "set-1",
            {"id": "image-only"},
            {
                "problemSetProblem": {
                    "id": "image-only",
                    "content": "![](~/problem-image.png)",
                }
            },
        )

        self.assertTrue(PTAClient._problem_record_has_valid_content(record))


class GroupAnswerExportFilterLockTests(unittest.TestCase):
    """用户组答卷导出 filter 锁定"""

    def test_filter_locked_when_match_in_first_candidate(self):
        client = PTAClient.__new__(PTAClient)
        marker = {
            "_requested_title": "T1",
            "_requested_type": "USER_GROUP_PAPER",
            "id": "exp-1",
        }
        calls = []

        def fake_api_get(path, params=None):
            calls.append(params.get("filter") if params else None)
            if len(calls) == 1:
                return {"exports": [{"type": "USER_GROUP_PAPER", "id": "exp-1",
                                     "title": "T1", "status": "WAITING"}]}
            return {"exports": [{"type": "USER_GROUP_PAPER", "id": "exp-1",
                                 "title": "T1", "status": "READY",
                                 "docUrl": "http://x/y.zip"}]}

        with patch.object(client, "api_get", side_effect=fake_api_get), \
             patch("pta_spider.spider.time.sleep"):
            doc_url = client.wait_group_answer_export_ready("g1", marker, timeout=10)

        self.assertEqual(doc_url, "http://x/y.zip")
        # 第 1 次锁定 userGroupId；第 2 次只查该 filter，不应继续遍历 groupId/{}
        self.assertEqual(len(calls), 2, f"锁定后应只查 1 个 filter，总调用 {len(calls)} 次: {calls}")
        self.assertEqual(calls[1], calls[0], "第 2 次应复用锁定的 filter")

    def test_filter_locked_when_match_in_second_candidate(self):
        """匹配项在第二个 filter 时：第一轮查 2 次后锁定，第二轮只查 1 次"""
        client = PTAClient.__new__(PTAClient)
        marker = {"_requested_title": "T2", "_requested_type": "USER_GROUP_PAPER", "id": "exp-2"}
        calls = []

        def fake_api_get(path, params=None):
            calls.append(params.get("filter") if params else None)
            n = len(calls)
            if n == 1:
                return {"exports": []}  # userGroupId 无结果
            if n == 2:
                return {"exports": [{"type": "USER_GROUP_PAPER", "id": "exp-2",
                                     "title": "T2", "status": "WAITING"}]}  # groupId 命中
            return {"exports": [{"type": "USER_GROUP_PAPER", "id": "exp-2",
                                 "title": "T2", "status": "READY",
                                 "docUrl": "http://x/z.zip"}]}

        with patch.object(client, "api_get", side_effect=fake_api_get), \
             patch("pta_spider.spider.time.sleep"):
            doc_url = client.wait_group_answer_export_ready("g2", marker, timeout=10)

        self.assertEqual(doc_url, "http://x/z.zip")
        # 第一轮: userGroupId(空) + groupId(命中 WAITING) = 2 次
        # 第二轮: 只查 groupId = 1 次；总 3 次（而非每轮 3 次的 6 次）
        self.assertEqual(len(calls), 3, f"预期 3 次调用，实际 {len(calls)}: {calls}")
        self.assertEqual(calls[2], calls[1], "第 3 次应复用第二轮锁定的 filter")


class AdaptiveRateLimiterTests(unittest.TestCase):
    """自适应令牌桶"""

    def test_rate_halves_on_429_and_recovers(self):
        limiter = AdaptiveTokenBucketRateLimiter(rate=40, per=60, rate_min=10, rate_max=40)
        self.assertAlmostEqual(limiter.current_rate(), 40.0, places=1)
        limiter.on_rate_limit()
        self.assertAlmostEqual(limiter.current_rate(), 20.0, places=1)
        limiter.on_rate_limit()
        self.assertAlmostEqual(limiter.current_rate(), 10.0, places=1)
        # 不低于 rate_min
        limiter.on_rate_limit()
        self.assertAlmostEqual(limiter.current_rate(), 10.0, places=1)

        # 连续成功后缓慢回升
        for _ in range(20):
            limiter.on_success()
        self.assertGreater(limiter.current_rate(), 10.0)

    def test_acquire_blocks_then_returns(self):
        limiter = AdaptiveTokenBucketRateLimiter(rate=2, per=1, rate_min=1, rate_max=2)
        # 先耗尽初始 token
        limiter.acquire()
        limiter.acquire()
        start = time.time()
        limiter.acquire()  # 应等待约 0.5s 才有下一个 token
        elapsed = time.time() - start
        self.assertGreaterEqual(elapsed, 0.3, f"无 token 时应阻塞，实际 {elapsed:.2f}s")
        self.assertLess(elapsed, 1.5, f"精确等待不应过久，实际 {elapsed:.2f}s")


class ExportParallelTests(unittest.TestCase):
    """同题集多类型导出并行"""

    def test_parallel_export_faster_than_serial(self):
        client = PTAClient.__new__(PTAClient)
        calls = []
        lock = threading.Lock()

        def slow_export(ps_id, ps_name, export_type, save_dir, max_retries=3):
            with lock:
                calls.append(export_type)
            time.sleep(0.35)
            return f"{save_dir}/{export_type}.xlsx"

        configs = [
            ("PAPER_TRANSCRIPT", "成绩单"),
            ("SCORED_CODE", "得分代码"),
        ]
        with patch.object(client, "export_and_download", side_effect=slow_export), \
             patch("pta_spider.spider.EXPORT_PARALLEL", True), \
             patch("pta_spider.spider.EXPORT_RETRY_ROUNDS", 0):
            start = time.time()
            client._export_required_files("ps1", "实验1", Path(tempfile.mkdtemp()), configs)
            elapsed = time.time() - start

        self.assertEqual(sorted(calls), ["PAPER_TRANSCRIPT", "SCORED_CODE"])
        # 串行约 0.7s，并行约 0.35s；放宽到 0.6s
        self.assertLess(elapsed, 0.6, f"并行导出应明显快于串行，实际 {elapsed:.2f}s")


    def test_export_timeout_does_not_create_duplicate_retry_task(self):
        client = PTAClient.__new__(PTAClient)
        calls = []

        def timed_out_export(ps_id, ps_name, export_type, save_dir, max_retries=3):
            calls.append(export_type)
            raise TimeoutError(f"{export_type} still queued")

        configs = [("PAPER_TRANSCRIPT", "成绩单")]
        with patch.object(
            client,
            "export_and_download",
            side_effect=timed_out_export,
        ), patch(
            "pta_spider.spider.EXPORT_PARALLEL",
            False,
        ), patch(
            "pta_spider.spider.EXPORT_RETRY_ROUNDS",
            2,
        ), patch(
            "pta_spider.spider.EXPORT_RETRY_DELAY_SECONDS",
            0,
        ), self.assertRaises(TimeoutError):
            client._export_required_files(
                "ps1",
                "实验1",
                Path(tempfile.mkdtemp()),
                configs,
            )

        self.assertEqual(calls, ["PAPER_TRANSCRIPT"])


class ProblemSetParallelHelperTests(unittest.TestCase):
    """spider_api 题集并行 helper"""

    def test_error_isolation_and_ok_results(self):
        from pta_spider.spider_api import _map_problem_sets_parallel

        items = [{"id": "1", "name": "A"}, {"id": "2", "name": "B"}, {"id": "3", "name": "C"}]

        def worker(ps):
            if ps["id"] == "2":
                raise RuntimeError("fail-2")
            time.sleep(0.2)
            return ("ok", None, ps)

        with patch("pta_spider.spider_api.PROBLEM_SET_MAX_WORKERS", 3):
            start = time.time()
            results = _map_problem_sets_parallel(items, worker, label="test")
            elapsed = time.time() - start

        statuses = {r[2]["id"]: r[0] for r in results}
        self.assertEqual(statuses["1"], "ok")
        self.assertEqual(statuses["2"], "error")
        self.assertEqual(statuses["3"], "ok")
        self.assertLess(elapsed, 0.5, f"3 路并行应约 0.2s，实际 {elapsed:.2f}s")


if __name__ == "__main__":
    unittest.main()
