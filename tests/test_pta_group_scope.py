import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.sync_to_unified_db import _filter_rows_to_pta_user_group


class PTAUserGroupScopeTests(unittest.TestCase):
    def test_all_student_sources_are_filtered_by_authoritative_roster(self):
        context = {
            "active_student_nos": {"20250001"},
            "pta_user_to_student_no": {"pta-in": "20250001"},
        }
        transcript = [
            {"student_no": "20250001"},
            {"student_no": "20249999"},
        ]
        submissions = [
            {"pta_user_id": "pta-in"},
            {"pta_user_id": "pta-out"},
            {"pta_user_id": ""},
        ]
        answers = [
            {"student_no": "20250001"},
            {"student_no": "20249999"},
        ]
        scored = [
            {"student_no": "20250001"},
            {"student_no": "20249999"},
        ]

        filtered = _filter_rows_to_pta_user_group(
            context,
            transcript,
            submissions,
            answers,
            scored,
        )

        self.assertEqual(filtered[0], [{"student_no": "20250001"}])
        self.assertEqual(filtered[1], [{"pta_user_id": "pta-in"}])
        self.assertEqual(filtered[2], [{"student_no": "20250001"}])
        self.assertEqual(filtered[3], [{"student_no": "20250001"}])
        self.assertEqual(
            filtered[4],
            {
                "transcript": 1,
                "submissions": 2,
                "answer_sheet": 1,
                "scored_code": 1,
            },
        )

    def test_without_user_group_context_keeps_existing_behavior(self):
        rows = [{"student_no": "x", "pta_user_id": "u"}]
        filtered = _filter_rows_to_pta_user_group(
            None,
            rows,
            rows,
            rows,
            rows,
        )
        self.assertIs(filtered[0], rows)
        self.assertIs(filtered[1], rows)
        self.assertIs(filtered[2], rows)
        self.assertIs(filtered[3], rows)
        self.assertEqual(sum(filtered[4].values()), 0)


if __name__ == "__main__":
    unittest.main()
