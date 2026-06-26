import re
import zipfile
from collections import Counter, defaultdict
from html import unescape
from pathlib import Path

try:
    from . import sync_to_db as legacy_sync
except ImportError:
    import sync_to_db as legacy_sync


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


def split_group_answer_export(zip_path, crawl_dir, group_name=None, overwrite=True):
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
