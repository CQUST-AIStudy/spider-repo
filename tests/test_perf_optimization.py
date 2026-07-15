"""
性能优化单元测试：
1. 题目详情并发拉取：输出顺序、内容、单题失败隔离、并发提速
2. 用户组答卷导出 filter 锁定：锁定后 api_get 调用次数下降
全部使用 mock，不依赖真实 PTA 登录与网络。
"""
import sys
import json
import time
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.spider import PTAClient


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
             patch("pta_spider.spider.time.sleep"):
            self.client._crawl_one_problem_set("ps1", "测试集2", export_answer_sheet=False)

        content = (self.tmp / "测试集2" / "题目内容.txt").read_text(encoding="utf-8")
        self.assertIn("内容-good-1", content)
        self.assertIn("内容-good-2", content)
        self.assertIn("(获取详情失败: boom)", content)
        detail_json = json.loads((self.tmp / "测试集2" / "题目详情.json").read_text(encoding="utf-8"))
        # 坏题不计入 JSON，好题 2 条
        self.assertEqual(len(detail_json), 2)

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


if __name__ == "__main__":
    unittest.main()
