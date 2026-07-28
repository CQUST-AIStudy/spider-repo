import unittest
import sys
from pathlib import Path
from unittest.mock import patch

PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.sync_to_unified_db import _recalc_student_assignment


class RecordingCursor:
    def __init__(self) -> None:
        self.statements: list[str] = []

    def execute(self, statement: str, params=None) -> None:
        self.statements.append(statement)

    def executemany(self, statement: str, params) -> None:
        self.statements.append(statement)


class AssignmentCountBoundsTests(unittest.TestCase):
    def test_accepted_count_is_capped_by_submitted_count_expression(self) -> None:
        cursor = RecordingCursor()
        with patch(
            "pta_spider.sync_to_unified_db._table_has_column",
            return_value=True,
        ):
            _recalc_student_assignment(
                cursor,
                offering_id=41,
                transcript_rows=[],
                student_no_to_id={},
                answer_sheet_rows=[],
                scored_code_rows=[],
                available_roles=set(),
            )

        update_sql = next(
            statement
            for statement in cursor.statements
            if "UPDATE student_assignment sa" in statement
        )
        self.assertIn("sa.accepted_problem_count =", update_sql)
        self.assertIn(
            "LEAST(\n          COALESCE(sps.accepted_problem_count, 0)",
            update_sql,
        )


if __name__ == "__main__":
    unittest.main()
