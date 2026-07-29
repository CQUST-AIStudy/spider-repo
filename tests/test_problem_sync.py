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
    _filter_scored_code_rows_by_problem_ids,
    _inspect_code_state_materialization,
    _is_stable_problem_set_source_id,
    _load_supported_problem_ids_for_offering,
    _remove_assignment_problems_outside_supported_scope,
    _recalc_student_assignment,
    _resolve_named_pta_offering,
    _supported_problem_detail_rows,
)


class CodeValidationCursor:
    def __init__(self, rows):
        self.rows = rows
        self.sql = ""
        self.params = None

    def execute(self, sql, params=None):
        self.sql = sql
        self.params = params

    def fetchall(self):
        return self.rows


class CodeMaterializationValidationTests(unittest.TestCase):
    def test_accepts_exact_non_empty_code_artifacts_and_samples_rows(self):
        cursor = CodeValidationCursor(
            [
                (101, 201, 301, 120),
                (102, 201, 302, 80),
            ]
        )

        result = _inspect_code_state_materialization(
            cursor,
            77,
            [
                (301, 77, 101, 201),
                (302, 77, 102, 201),
            ],
        )

        self.assertTrue(result["ok"])
        self.assertEqual(result["expected_problem_student_rows"], 2)
        self.assertEqual(result["materialized_problem_student_rows"], 2)
        self.assertEqual(result["expected_students"], 1)
        self.assertEqual(result["materialized_students"], 1)
        self.assertEqual(result["invalid_expected_rows"], 0)
        self.assertEqual(len(result["sampled_rows"]), 2)
        self.assertEqual(cursor.params, (77,))

    def test_rejects_missing_stale_and_empty_code_artifacts(self):
        cursor = CodeValidationCursor(
            [
                (101, 201, 999, 120),
                (102, 201, 302, 0),
            ]
        )

        result = _inspect_code_state_materialization(
            cursor,
            77,
            [
                (301, 77, 101, 201),
                (302, 77, 102, 201),
                (303, 77, 103, 202),
                (304, 77, None, 203),
            ],
        )

        self.assertFalse(result["ok"])
        self.assertEqual(result["materialized_problem_student_rows"], 0)
        self.assertEqual(result["missing_or_stale_rows"], 2)
        self.assertEqual(result["empty_content_rows"], 1)
        self.assertEqual(result["invalid_expected_rows"], 1)


class ProgrammingProblemScopeTests(unittest.TestCase):
    def test_keeps_only_explicit_programming_problem_details(self):
        programming = {
            "problem_set_problem_id": "p1",
            "problem_type": "PROGRAMMING",
        }
        function_problem = {
            "problem_set_problem_id": "p2",
            "problem_type": "CODE_COMPLETION",
        }

        supported, unsupported = _supported_problem_detail_rows(
            [programming, function_problem]
        )

        self.assertEqual(supported, [programming])
        self.assertEqual(unsupported, [function_problem])

    def test_rejects_missing_problem_type_instead_of_defaulting_to_programming(self):
        with self.assertRaisesRegex(RuntimeError, "missing problem_type"):
            _supported_problem_detail_rows(
                [{"problem_set_problem_id": "unknown", "problem_type": None}]
            )

    def test_scored_code_must_match_supported_problem_id(self):
        rows = [
            {"pta_problem_id": "p1", "student_no": "1"},
            {"pta_problem_id": "p2", "student_no": "1"},
            {"pta_problem_id": "0", "student_no": "1"},
        ]

        accepted, ignored = _filter_scored_code_rows_by_problem_ids(rows, {"p1"})

        self.assertEqual(accepted, [rows[0]])
        self.assertEqual(ignored, rows[1:])

    def test_partial_sync_loads_only_explicit_active_programming_ids(self):
        class Cursor:
            def execute(self, sql, params=None):
                self.sql = sql
                self.params = params

            def fetchall(self):
                return [("p1",), (None,), ("",)]

        cursor = Cursor()
        result = _load_supported_problem_ids_for_offering(cursor, 7)

        self.assertEqual(result, {"p1"})
        self.assertIn("ap.status = 'ACTIVE'", cursor.sql)
        self.assertIn("COALESCE(pd.problem_type, '')", cursor.sql)
        self.assertEqual(cursor.params, (7, "PROGRAMMING"))

    def test_empty_supported_scope_removes_all_active_problem_mappings(self):
        class Cursor:
            rowcount = 3

            def execute(self, sql, params=None):
                self.sql = sql
                self.params = params

        cursor = Cursor()
        removed = _remove_assignment_problems_outside_supported_scope(
            cursor,
            offering_id=7,
            supported_problem_ids=set(),
        )

        self.assertEqual(removed, 3)
        self.assertIn("status = 'REMOVED'", cursor.sql)
        self.assertEqual(cursor.params, (7,))


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
