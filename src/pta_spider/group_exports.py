import posixpath
import re
import zipfile
from collections import Counter, defaultdict
from html import unescape
from pathlib import Path
from xml.etree import ElementTree as ET

try:
    from . import sync_to_db as legacy_sync
except ImportError:
    import sync_to_db as legacy_sync


_XLSX_MAIN_NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
_XLSX_REL_NS = {
    "pr": "http://schemas.openxmlformats.org/package/2006/relationships"
}
_XLSX_REL_ID = (
    "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
)


PTA_EXPORT_DIR = "导出"


def _safe_zip_entry_name(value: str) -> str:
    parts = []
    for part in str(value or "").replace("\\", "/").split("/"):
        part = part.strip()
        if not part or part in {".", ".."}:
            continue
        parts.append(part)
    return "/".join(parts)


def _html_title(html_text: str):
    match = re.search(r"<title[^>]*>(.*?)</title>", html_text or "", flags=re.IGNORECASE | re.DOTALL)
    if not match:
        return None
    title = re.sub(r"\s+", " ", match.group(1)).strip()
    return unescape(title) or None


def _student_html_parts(decoded_name: str, html_text: str = None):
    parts = [part for part in decoded_name.split("/") if part]
    if len(parts) >= 4:
        exp_name, group_name, folder, basename = parts[0], parts[1], parts[-2], parts[-1]
    elif len(parts) >= 3:
        exp_name, group_name, folder, basename = _html_title(html_text), parts[0], parts[-2], parts[-1]
    else:
        return None
    if not exp_name or group_name == "Admins" or folder.lower() != "html" or not basename.lower().endswith(".html"):
        return None
    match = re.match(r"([^/-]+)-(.+)\.html$", basename)
    if not match:
        return None
    return {
        "experiment_name": exp_name,
        "group_name": group_name,
        "student_no": match.group(1).strip(),
        "student_name": match.group(2).strip(),
        "basename": basename,
    }


def inspect_group_answer_export(zip_path):
    """Return a lightweight summary of a PTA user-group answer export."""
    zip_path = Path(zip_path)
    experiments = defaultdict(lambda: {"groups": Counter(), "students": set(), "html_files": 0})
    admin_html_files = 0
    ignored_files = 0

    with zipfile.ZipFile(zip_path, "r") as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            decoded_name = legacy_sync._decode_zip_filename(info.filename)
            html_text = None
            if decoded_name.lower().endswith(".html"):
                html_text = legacy_sync._safe_zip_read(zf, info).decode("utf-8", errors="replace")
            parsed = _student_html_parts(decoded_name, html_text)
            if parsed is None:
                parts = [part for part in decoded_name.split("/") if part]
                if len(parts) >= 2 and parts[1] == "Admins" and decoded_name.lower().endswith(".html"):
                    admin_html_files += 1
                else:
                    ignored_files += 1
                continue
            exp = experiments[parsed["experiment_name"]]
            exp["groups"][parsed["group_name"]] += 1
            exp["students"].add((parsed["student_no"], parsed["student_name"]))
            exp["html_files"] += 1

    return {
        "zip_path": str(zip_path),
        "experiment_count": len(experiments),
        "admin_html_files": admin_html_files,
        "ignored_files": ignored_files,
        "experiments": [
            {
                "experiment_name": name,
                "groups": dict(data["groups"]),
                "student_count": len(data["students"]),
                "html_files": data["html_files"],
            }
            for name, data in sorted(experiments.items())
        ],
    }


def split_group_answer_export(
    zip_path,
    crawl_dir,
    group_name=None,
    overwrite=True,
    experiment_names=None,
):
    """
    Split one PTA user-group ANSWER_SHEET export into per-experiment zip files.

    Input zip layout:
      <experiment>/<user-group>/html/<student_no>-<student_name>.html

    Output layout understood by sync_to_unified_db:
      <crawl_dir>/<experiment>/导出/<experiment>-ANSWER_SHEET.zip
    """
    zip_path = Path(zip_path)
    crawl_dir = Path(crawl_dir)
    grouped = defaultdict(list)
    seen_entries = set()
    skipped = Counter()
    allowed_experiments = (
        {str(name).strip() for name in experiment_names if str(name).strip()}
        if experiment_names is not None
        else None
    )

    with zipfile.ZipFile(zip_path, "r") as src:
        for info in src.infolist():
            if info.is_dir():
                continue
            decoded_name = legacy_sync._decode_zip_filename(info.filename)
            raw = None
            html_text = None
            if decoded_name.lower().endswith(".html"):
                raw = legacy_sync._safe_zip_read(src, info)
                html_text = raw.decode("utf-8", errors="replace")
            parsed = _student_html_parts(decoded_name, html_text)
            if parsed is None:
                skipped["non_student_html"] += 1
                continue
            if group_name and parsed["group_name"] != group_name:
                skipped["other_group"] += 1
                continue
            if (
                allowed_experiments is not None
                and parsed["experiment_name"] not in allowed_experiments
            ):
                skipped["non_target_experiment"] += 1
                continue
            dedupe_key = (parsed["experiment_name"], parsed["basename"])
            if dedupe_key in seen_entries:
                skipped["duplicate_student_html"] += 1
                continue
            seen_entries.add(dedupe_key)
            grouped[parsed["experiment_name"]].append((info, parsed))

        written = []
        for experiment_name, rows in sorted(grouped.items()):
            export_dir = crawl_dir / experiment_name / PTA_EXPORT_DIR
            export_dir.mkdir(parents=True, exist_ok=True)
            out_path = export_dir / f"{experiment_name}-ANSWER_SHEET.zip"
            if out_path.exists() and not overwrite:
                skipped["existing_output"] += len(rows)
                continue

            tmp_path = out_path.with_suffix(out_path.suffix + ".part")
            if tmp_path.exists():
                tmp_path.unlink()
            try:
                with zipfile.ZipFile(tmp_path, "w", compression=zipfile.ZIP_DEFLATED) as dst:
                    for info, parsed in rows:
                        data = legacy_sync._safe_zip_read(src, info)
                        entry_name = _safe_zip_entry_name(f"html/{parsed['basename']}")
                        dst.writestr(entry_name, data)
                tmp_path.replace(out_path)
            except Exception:
                if tmp_path.exists():
                    tmp_path.unlink()
                raise

            written.append(
                {
                    "experiment_name": experiment_name,
                    "path": str(out_path),
                    "html_files": len(rows),
                    "student_count": len({(row[1]["student_no"], row[1]["student_name"]) for row in rows}),
                }
            )

    return {
        "zip_path": str(zip_path),
        "crawl_dir": str(crawl_dir),
        "group_name": group_name,
        "written": written,
        "skipped": dict(skipped),
    }


def _xlsx_text(node, shared_strings):
    if node is None:
        return ""
    cell_type = node.attrib.get("t", "")
    if cell_type == "inlineStr":
        return "".join(
            text.text or ""
            for text in node.findall(".//x:t", _XLSX_MAIN_NS)
        ).strip()
    raw = node.findtext("x:v", default="", namespaces=_XLSX_MAIN_NS)
    if cell_type == "s" and raw:
        try:
            return shared_strings[int(raw)].strip()
        except (IndexError, ValueError):
            return raw.strip()
    return raw.strip()


def _group_transcript_sheets(zf):
    shared_strings = []
    if "xl/sharedStrings.xml" in zf.namelist():
        shared_root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
        for item in shared_root.findall("x:si", _XLSX_MAIN_NS):
            shared_strings.append(
                "".join(
                    text.text or ""
                    for text in item.findall(".//x:t", _XLSX_MAIN_NS)
                )
            )

    workbook_bytes = zf.read("xl/workbook.xml")
    workbook_root = ET.fromstring(workbook_bytes)
    rels_root = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    rel_map = {
        rel.attrib.get("Id"): rel.attrib.get("Target", "")
        for rel in rels_root.findall("pr:Relationship", _XLSX_REL_NS)
    }

    result = []
    for sheet in workbook_root.findall("x:sheets/x:sheet", _XLSX_MAIN_NS):
        rel_id = sheet.attrib.get(_XLSX_REL_ID)
        target = rel_map.get(rel_id, "")
        if not target:
            continue
        sheet_path = (
            target
            if target.startswith("xl/")
            else posixpath.normpath(posixpath.join("xl", target))
        )
        sheet_root = ET.fromstring(zf.read(sheet_path))
        first_cell = sheet_root.find(
            "x:sheetData/x:row/x:c",
            _XLSX_MAIN_NS,
        )
        first_text = _xlsx_text(first_cell, shared_strings)
        prefix = "成绩明细 - "
        if not first_text.startswith(prefix):
            continue
        experiment_name = first_text[len(prefix):].strip()
        if experiment_name:
            result.append(
                {
                    "experiment_name": experiment_name,
                    "sheet_name": sheet.attrib.get("name", ""),
                    "rel_id": rel_id,
                }
            )
    return workbook_bytes, result


def inspect_group_transcript_export(xlsx_path):
    """Return the per-experiment sheets in a PTA user-group transcript."""
    xlsx_path = Path(xlsx_path)
    with zipfile.ZipFile(xlsx_path, "r") as zf:
        _, sheets = _group_transcript_sheets(zf)
    return {
        "xlsx_path": str(xlsx_path),
        "experiment_count": len(sheets),
        "experiments": sheets,
    }


def split_group_transcript_export(
    xlsx_path,
    crawl_dir,
    overwrite=True,
    experiment_names=None,
):
    """Create per-experiment transcript workbooks from one group workbook.

    Each output keeps the original workbook data and moves its target detail
    sheet to the first position, which is the sheet consumed by the database
    transcript parser.
    """
    xlsx_path = Path(xlsx_path)
    crawl_dir = Path(crawl_dir)
    written = []
    skipped = Counter()
    allowed_experiments = (
        {str(name).strip() for name in experiment_names if str(name).strip()}
        if experiment_names is not None
        else None
    )

    with zipfile.ZipFile(xlsx_path, "r") as src:
        workbook_bytes, sheets = _group_transcript_sheets(src)
        source_entries = [
            (info, src.read(info))
            for info in src.infolist()
            if not info.is_dir()
        ]

        for sheet_info in sheets:
            experiment_name = sheet_info["experiment_name"]
            if (
                allowed_experiments is not None
                and experiment_name not in allowed_experiments
            ):
                skipped["non_target_experiment"] += 1
                continue
            export_dir = crawl_dir / experiment_name / PTA_EXPORT_DIR
            export_dir.mkdir(parents=True, exist_ok=True)
            out_path = export_dir / f"{experiment_name}-PAPER_TRANSCRIPT.xlsx"
            if out_path.exists() and not overwrite:
                skipped["existing_output"] += 1
                continue

            workbook_root = ET.fromstring(workbook_bytes)
            sheets_node = workbook_root.find("x:sheets", _XLSX_MAIN_NS)
            target_sheet = None
            if sheets_node is not None:
                for sheet in list(sheets_node):
                    if sheet.attrib.get(_XLSX_REL_ID) == sheet_info["rel_id"]:
                        target_sheet = sheet
                        break
            if sheets_node is None or target_sheet is None:
                skipped["missing_sheet_relation"] += 1
                continue
            sheets_node.remove(target_sheet)
            sheets_node.insert(0, target_sheet)
            rewritten_workbook = ET.tostring(
                workbook_root,
                encoding="utf-8",
                xml_declaration=True,
            )

            tmp_path = out_path.with_suffix(out_path.suffix + ".part")
            if tmp_path.exists():
                tmp_path.unlink()
            try:
                with zipfile.ZipFile(
                    tmp_path,
                    "w",
                    compression=zipfile.ZIP_DEFLATED,
                ) as dst:
                    for info, data in source_entries:
                        dst.writestr(
                            info,
                            rewritten_workbook
                            if info.filename == "xl/workbook.xml"
                            else data,
                        )
                tmp_path.replace(out_path)
            except Exception:
                if tmp_path.exists():
                    tmp_path.unlink()
                raise

            written.append(
                {
                    "experiment_name": experiment_name,
                    "path": str(out_path),
                    "sheet_name": sheet_info["sheet_name"],
                }
            )

    return {
        "xlsx_path": str(xlsx_path),
        "crawl_dir": str(crawl_dir),
        "written": written,
        "skipped": dict(skipped),
    }
