import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from pta_spider.group_exports import (
    inspect_group_transcript_export,
    split_group_transcript_export,
)
from pta_spider.spider import PTAClient
from pta_spider.sync_to_db import _read_xlsx_rows


class GroupTranscriptSplitTests(unittest.TestCase):
    @staticmethod
    def _write_workbook(path: Path) -> None:
        shared_strings = """<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 count="4" uniqueCount="4">
  <si><t>成绩汇总</t></si>
  <si><t>成绩明细 - 实验一</t></si>
  <si><t>学号</t></si>
  <si><t>20250001</t></si>
</sst>"""
        workbook = """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Detail" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
        rels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Target="worksheets/sheet1.xml"
   Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
  <Relationship Id="rId2" Target="worksheets/sheet2.xml"
   Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/>
</Relationships>"""
        summary = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row></sheetData>
</worksheet>"""
        detail = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="s"><v>1</v></c></row>
    <row r="2"><c r="A2" t="s"><v>2</v></c></row>
    <row r="3"><c r="A3" t="s"><v>3</v></c></row>
  </sheetData>
</worksheet>"""
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as workbook_zip:
            workbook_zip.writestr("xl/sharedStrings.xml", shared_strings)
            workbook_zip.writestr("xl/workbook.xml", workbook)
            workbook_zip.writestr("xl/_rels/workbook.xml.rels", rels)
            workbook_zip.writestr("xl/worksheets/sheet1.xml", summary)
            workbook_zip.writestr("xl/worksheets/sheet2.xml", detail)

    def test_group_workbook_is_split_with_detail_sheet_first(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "group.xlsx"
            self._write_workbook(source)

            summary = inspect_group_transcript_export(source)
            result = split_group_transcript_export(source, root / "crawl")

            self.assertEqual(summary["experiment_count"], 1)
            self.assertEqual(len(result["written"]), 1)
            output = Path(result["written"][0]["path"])
            rows = _read_xlsx_rows(output)

        self.assertEqual(rows[0][0], "成绩明细 - 实验一")
        self.assertEqual(rows[2][0], "20250001")


    def test_non_target_experiment_is_not_written(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "group.xlsx"
            self._write_workbook(source)

            result = split_group_transcript_export(
                source,
                root / "crawl",
                experiment_names={"different-experiment"},
            )

            self.assertEqual(result["written"], [])
            self.assertEqual(
                result["skipped"]["non_target_experiment"],
                1,
            )
            self.assertFalse((root / "crawl").exists())


class GroupTranscriptApiTests(unittest.TestCase):
    def test_create_uses_verified_pta_payload(self) -> None:
        client = PTAClient.__new__(PTAClient)
        response = Mock()
        response.text = "{}"
        response.json.return_value = {
            "export": {
                "id": "export-1",
                "type": "USER_GROUP_TRANSCRIPT",
            }
        }
        client.api_post = Mock(return_value=response)

        marker = client.create_group_transcript_export(
            "group-1",
            "测试组",
        )

        payload = client.api_post.call_args.kwargs["json_data"]
        self.assertEqual(payload["type"], "USER_GROUP_TRANSCRIPT")
        self.assertEqual(
            payload["detail"],
            {
                "exportUserGroupTranscript": {
                    "userGroupId": "group-1",
                }
            },
        )
        self.assertEqual(marker["_requested_type"], "USER_GROUP_TRANSCRIPT")

    def test_wait_matches_only_the_created_transcript(self) -> None:
        client = PTAClient.__new__(PTAClient)
        marker = {
            "_requested_title": "target",
            "_requested_type": "USER_GROUP_TRANSCRIPT",
            "export": {"id": "export-1"},
        }
        client.api_get = Mock(
            return_value={
                "exports": [
                    {
                        "id": "export-1",
                        "type": "USER_GROUP_TRANSCRIPT",
                        "title": "target",
                        "status": "READY",
                        "docUrl": "https://example.test/transcript.xlsx",
                    }
                ]
            }
        )

        with patch("pta_spider.spider._export_poll_sleep"):
            result = client.wait_group_transcript_export_ready(
                "group-1",
                marker,
                timeout=1,
            )

        self.assertEqual(
            result,
            "https://example.test/transcript.xlsx",
        )


if __name__ == "__main__":
    unittest.main()
