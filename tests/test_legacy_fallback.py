import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

import pta_spider.sync_to_unified_db as sync_to_unified_db


class MissingLegacyExperimentTable(RuntimeError):
    pass


class FakeCursor:
    def execute(self, sql, params=None):
        if "FROM experiment" in sql:
            raise MissingLegacyExperimentTable("Table 'ptadatabase.experiment' doesn't exist")
        raise AssertionError(f"unexpected SQL reached fallback cursor: {sql}")

    def fetchone(self):
        return None


class LegacyFallbackTests(unittest.TestCase):
    def test_resolve_legacy_offering_returns_none_when_legacy_experiment_table_is_missing(self):
        cursor = FakeCursor()

        result = sync_to_unified_db._resolve_legacy_offering(cursor, "Some PTA Problem Set", class_id=1)

        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
