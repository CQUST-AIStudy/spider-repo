import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.sync_to_unified_db import (
    _bulk_ensure_assignment_problems,
    _discover_experiment_source_paths,
    _fail_stale_import_jobs,
    _is_stable_problem_set_source_id,
    _recalc_student_assignment,
    _resolve_named_pta_offering,
)


class RecordingCursor:
    def __init__(self):
        self.sql = ""
        self.rows = []

    def executemany(self, sql, rows):
        self.sql = sql
        self.rows = list(rows)

    def execute(self, sql, params=None):
        self.select_sql = sql
        self.select_params = params

    def fetchall(self):
        return [(101, "pta-problem-1")]


class UnifiedProblemWriteTests(unittest.TestCase):
    def test_problem_detail_populates_statement_and_score(self):
        cursor = RecordingCursor()
        cache = {}

        _bulk_ensure_assignment_problems(
            cursor,
            offering_id=7,
            problem_specs={
                "pta-problem-1": {
                    "title": "A + B",
                    "statement_md": "Solve the problem.",
                    "max_score": 20,
                    "sort_order": 1,
                }
            },
            cache=cache,
        )

        self.assertIn("statement_md", cursor.sql)
        self.assertIn("max_score", cursor.sql)
        self.assertEqual(cursor.rows[0][4], "Solve the problem.")
        self.assertEqual(cursor.rows[0][5], 20.0)
        self.assertEqual(cache["pta-problem-1"], 101)


class StaleImportJobRecoveryTests(unittest.TestCase):
    def test_marks_only_expired_running_jobs_for_the_class(self):
        cursor = RecordingCursor()
        cursor.rowcount = 2

        with patch.dict("os.environ", {"PTA_IMPORT_JOB_STALE_HOURS": "4"}):
            recovered = _fail_stale_import_jobs(cursor, class_id=9)

        self.assertEqual(recovered, 2)
        self.assertIn("status = 'RUNNING'", cursor.select_sql)
        self.assertIn("INTERVAL 4 HOUR", cursor.select_sql)
        self.assertEqual(cursor.select_params, ("PTA", 9))


class SourceDiscoveryTests(unittest.TestCase):
    def test_partial_directory_reports_only_present_roles(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            exp_dir = Path(temp_dir)
            (exp_dir / "题目集信息.json").write_text('{"id":"123"}', encoding="utf-8")
            (exp_dir / "提交记录.csv").write_text("id\n", encoding="utf-8")

            files = _discover_experiment_source_paths(exp_dir)

        self.assertEqual(set(files), {"PROBLEM_SET_INFO", "SUBMISSIONS"})

    def test_name_fallback_is_not_a_stable_problem_set_id(self):
        self.assertFalse(_is_stable_problem_set_source_id("NAME-abc"))
        self.assertFalse(_is_stable_problem_set_source_id(""))
        self.assertTrue(_is_stable_problem_set_source_id("190384721"))


class OfferingResolutionTests(unittest.TestCase):
    def test_name_only_group_export_prefers_consistent_stable_offering(self):
        class Cursor:
            def execute(self, sql, params=None):
                self.sql = sql
                self.params = params

            def fetchall(self):
                return [
                    (
                        44,
                        3,
                        1,
                        "2062567705938599936",
                        "PTA_PROBLEM_SET_OFFERING:2062567705938599936:CLASS:3",
                    ),
                    (
                        107,
                        3,
                        1,
                        "2062567705938599936",
                        "PTA_PROBLEM_SET_OFFERING:NAME-shadow:CLASS:3",
                    ),
                ]

        resolved = _resolve_named_pta_offering(
            Cursor(),
            "计科25数据结构第10次实验（Huffman树与Huffman编码）",
            class_id=3,
            problem_set_source_id="NAME-from-group-export",
        )

        self.assertEqual(resolved, (44, 3, 1))


class AssignmentPartialSyncTests(unittest.TestCase):
    def test_missing_sources_seed_existing_transcript_and_evidence(self):
        class Cursor:
            def __init__(self):
                self.statements = []

            def execute(self, sql, params=None):
                self.statements.append(sql)

            def executemany(self, sql, rows):
                self.statements.append(sql)

        cursor = Cursor()
        with patch(
            "pta_spider.sync_to_unified_db._table_has_column",
            return_value=True,
        ):
            _recalc_student_assignment(
                cursor,
                offering_id=7,
                transcript_rows=[],
                student_no_to_id={},
                answer_sheet_rows=[],
                scored_code_rows=[],
                available_roles={"SUBMISSIONS"},
            )

        sql = "\n".join(cursor.statements)
        self.assertIn("transcript_row_present = TRUE", sql)
        self.assertIn("answer_sheet_count > 0", sql)
        self.assertIn("scored_code_count > 0", sql)


if __name__ == "__main__":
    unittest.main()
