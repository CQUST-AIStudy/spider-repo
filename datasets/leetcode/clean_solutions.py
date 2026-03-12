import json
import os
import re
from collections import Counter
from dataclasses import dataclass
from glob import glob
from typing import Dict, List, Optional, Tuple


BASE_DIR = os.path.dirname(os.path.abspath(__file__))


FAIL_OUTPUTS = {
    "获取题解内容失败",
    "[VIP内容，无法获取题解]",
}


def normalize_text(s: str) -> str:
    if s is None:
        return ""
    # Replace NBSP and normalize line endings.
    s = s.replace("\r\n", "\n").replace("\r", "\n").replace("\u00a0", " ")
    # Collapse trailing spaces per line.
    s = "\n".join(line.rstrip() for line in s.split("\n"))
    return s.strip()


def parse_json_lenient(path: str):
    with open(path, "r", encoding="utf-8") as f:
        txt = f.read()
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        # Repair one common case: trailing comma before final closing list bracket.
        repaired = re.sub(r",\s*\]\s*$", "\n]", txt, flags=re.S)
        return json.loads(repaired)


def extract_problem_key(input_text: str) -> str:
    first_line = (input_text or "").split("\n", 1)[0].strip()
    # Prefer numeric problem ID when present.
    m = re.search(r"(\d{1,6})\.", first_line)
    if m:
        return f"id:{m.group(1)}"
    return f"title:{first_line}"


def output_score(output_text: str) -> int:
    out = normalize_text(output_text)
    if not out or out in FAIL_OUTPUTS:
        return 0
    return len(out)


@dataclass
class RowSource:
    row: Dict
    file_name: str
    score: int


def iter_candidate_files() -> List[str]:
    files = []
    for p in glob(os.path.join(BASE_DIR, "solutions*.json*")):
        if os.path.isfile(p):
            files.append(p)
    files.sort()
    return files


def load_and_merge() -> Tuple[Dict[str, RowSource], Dict]:
    best_rows: Dict[str, RowSource] = {}
    parse_failures = []
    file_row_counts = {}

    for path in iter_candidate_files():
        name = os.path.basename(path)
        try:
            data = parse_json_lenient(path)
        except Exception as e:
            parse_failures.append({"file": name, "error": str(e)})
            continue

        if not isinstance(data, list):
            parse_failures.append({"file": name, "error": "json_root_not_list"})
            continue

        file_row_counts[name] = len(data)
        for item in data:
            if not isinstance(item, dict):
                continue
            instruction = normalize_text(str(item.get("instruction", "")))
            input_text = normalize_text(str(item.get("input", "")))
            output_text = normalize_text(str(item.get("output", "")))

            if not input_text:
                continue

            cleaned = {
                "instruction": instruction,
                "input": input_text,
                "output": output_text,
            }
            key = extract_problem_key(input_text)
            score = output_score(output_text)
            current = best_rows.get(key)
            if current is None or score > current.score:
                best_rows[key] = RowSource(row=cleaned, file_name=name, score=score)

    meta = {
        "parse_failures": parse_failures,
        "file_row_counts": file_row_counts,
    }
    return best_rows, meta


def build_outputs(best_rows: Dict[str, RowSource], meta: Dict):
    merged_raw = [v.row for v in best_rows.values()]
    # Keep only rows with meaningful output.
    cleaned = []
    dropped = Counter()
    for row in merged_raw:
        out = normalize_text(row["output"])
        if not out:
            dropped["empty_output"] += 1
            continue
        if out in FAIL_OUTPUTS:
            dropped["known_failure_output"] += 1
            continue
        if len(out) < 30:
            dropped["too_short_output"] += 1
            continue
        cleaned.append(row)

    # Stable sort by extracted numeric id when available.
    def sort_key(r):
        m = re.search(r"(\d{1,6})\.", r["input"].split("\n", 1)[0])
        if m:
            return (0, int(m.group(1)))
        return (1, r["input"].split("\n", 1)[0])

    merged_raw.sort(key=sort_key)
    cleaned.sort(key=sort_key)

    # Source contribution (for best rows).
    source_counter = Counter(v.file_name for v in best_rows.values() if v.score > 0)

    report = {
        "input_files": sorted(meta.get("file_row_counts", {}).keys()),
        "parse_failures": meta.get("parse_failures", []),
        "file_row_counts": meta.get("file_row_counts", {}),
        "merged_total_rows": len(merged_raw),
        "cleaned_total_rows": len(cleaned),
        "dropped_counts": dict(dropped),
        "source_contribution_non_empty_top20": source_counter.most_common(20),
    }
    return merged_raw, cleaned, report


def write_json(path: str, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main():
    best_rows, meta = load_and_merge()
    merged_raw, cleaned, report = build_outputs(best_rows, meta)

    merged_path = os.path.join(BASE_DIR, "solutions_merged_raw.json")
    cleaned_path = os.path.join(BASE_DIR, "solutions_cleaned.json")
    report_path = os.path.join(BASE_DIR, "solutions_cleaning_report.json")

    write_json(merged_path, merged_raw)
    write_json(cleaned_path, cleaned)
    write_json(report_path, report)

    print("done")
    print(f"merged_raw: {merged_path} rows={len(merged_raw)}")
    print(f"cleaned:    {cleaned_path} rows={len(cleaned)}")
    print(f"report:     {report_path}")
    print(f"dropped:    {report['dropped_counts']}")


if __name__ == "__main__":
    main()

