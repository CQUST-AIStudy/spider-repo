import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.sync_to_unified_db import (
    _bulk_ensure_assignment_participants,
    _ensure_student_assignment_participant,
    _materialize_student_assignments,
)


class RecordingCursor:
    def __init__(self):
        self.statements = []

    def execute(self, sql, params=None):
        self.statements.append((sql, params))

    def executemany(self, sql, rows):
        self.statements.append((sql, list(rows)))


class AssignmentProblemCountRebaseTests(unittest.TestCase):
    def assert_rebases_counts_before_problem_count(self, sql):
        accepted_pos = sql.index("accepted_problem_count = LEAST")
        submitted_pos = sql.index("submitted_problem_count = LEAST")
        problem_pos = sql.index("problem_count = VALUES(problem_count)")

        self.assertLess(accepted_pos, submitted_pos)
        self.assertLess(submitted_pos, problem_pos)
        self.assertIn("student_assignment.submitted_problem_count", sql)
        self.assertGreaterEqual(sql.count("VALUES(problem_count)"), 3)

    def test_bulk_participant_upsert_clamps_stale_counts(self):
        cursor = RecordingCursor()

        _bulk_ensure_assignment_participants(
            cursor,
            offering_id=41,
            student_scopes={101: "PTA_USER_GROUP"},
        )

        self.assert_rebases_counts_before_problem_count(cursor.statements[0][0])

    def test_roster_materialization_clamps_stale_counts(self):
        cursor = RecordingCursor()

        _materialize_student_assignments(
            cursor,
            offering_id=41,
            class_id=3,
            pta_group_context={"pta_user_group_id": 7},
        )

        self.assert_rebases_counts_before_problem_count(cursor.statements[0][0])

    def test_single_participant_upsert_clamps_stale_counts(self):
        cursor = RecordingCursor()

        _ensure_student_assignment_participant(
            cursor,
            offering_id=41,
            student_id=101,
            roster_scope="PTA_USER_GROUP",
        )

        self.assert_rebases_counts_before_problem_count(cursor.statements[0][0])


if __name__ == "__main__":
    unittest.main()
