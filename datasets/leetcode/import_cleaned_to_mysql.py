import argparse
import json
import os
import re
import subprocess
import tempfile
from typing import Dict, List, Optional, Tuple


def normalize_title(s: str) -> str:
    return re.sub(r"\s+", " ", (s or "").strip())


def parse_first_line(input_text: str) -> str:
    first = (input_text or "").split("\n", 1)[0].strip()
    return normalize_title(first.replace("题目：", "", 1).strip())


def parse_title_parts(source_key: str) -> Tuple[str, Optional[str], Optional[int], Optional[str]]:
    """
    Returns:
      (problem_code, title_main, numeric_id, title_alt)
    """
    # examples:
    # 2528. 最大化城市的最小电量 - 最大化城市的最小电量
    # LCR 002. 二进制求和 - 二进制加法
    # 面试题 16.19. 水域大小 - 水域大小
    m = re.match(r"^(.+?)\.\s*(.+)$", source_key)
    if not m:
        return source_key[:64], source_key[:255], None, None
    problem_code = normalize_title(m.group(1))
    title_full = normalize_title(m.group(2))
    if " - " in title_full:
        left, right = title_full.split(" - ", 1)
        title_main = left.strip()
        title_alt = right.strip() or None
    else:
        title_main = title_full
        title_alt = None

    numeric_id = int(problem_code) if re.fullmatch(r"\d{1,9}", problem_code) else None
    return problem_code[:64], title_main[:255], numeric_id, (title_alt[:255] if title_alt else None)


def sql_quote(val: Optional[str]) -> str:
    if val is None:
        return "NULL"
    s = str(val)
    s = s.replace("\\", "\\\\").replace("'", "\\'")
    return f"'{s}'"


def build_url_map(urls_path: str) -> Dict[str, str]:
    with open(urls_path, "r", encoding="utf-8") as f:
        arr = json.load(f)
    m: Dict[str, str] = {}
    for x in arr:
        title = normalize_title(str(x.get("title", "") or ""))
        url = str(x.get("url", "") or "").strip()
        if title and url:
            m[title] = url
    return m


def build_rows(cleaned_path: str, url_map: Dict[str, str]) -> List[Dict]:
    with open(cleaned_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    rows = []
    for item in data:
        input_text = str(item.get("input", "") or "").strip()
        output_text = str(item.get("output", "") or "").strip()
        if not input_text or not output_text:
            continue
        source_key = parse_first_line(input_text)
        problem_code, title_main, numeric_id, title_alt = parse_title_parts(source_key)
        url = url_map.get(source_key)
        rows.append(
            {
                "source_key": source_key,
                "problem_code": problem_code,
                "numeric_id": numeric_id,
                "title_main": title_main,
                "title_alt": title_alt,
                "problem_text": input_text,
                "solution_text": output_text,
                "problem_url": url,
            }
        )
    return rows


def generate_sql(rows: List[Dict], table_name: str, source_dataset: str, truncate: bool) -> str:
    parts: List[str] = []
    parts.append(
        f"""
CREATE TABLE IF NOT EXISTS `{table_name}` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `source_key` VARCHAR(255) NOT NULL COMMENT '题目标识行（题号+标题）',
  `problem_code` VARCHAR(64) NOT NULL COMMENT '题号编码（如2528/LCR 002/面试题 16.19）',
  `numeric_id` INT NULL COMMENT '纯数字题号（可空）',
  `title_main` VARCHAR(255) NULL COMMENT '主标题',
  `title_alt` VARCHAR(255) NULL COMMENT '副标题',
  `problem_text` LONGTEXT NOT NULL COMMENT '题面文本（input）',
  `solution_text` LONGTEXT NOT NULL COMMENT '题解文本（output）',
  `problem_url` VARCHAR(600) NULL COMMENT 'leetcode题解链接',
  `source_dataset` VARCHAR(128) NOT NULL COMMENT '来源数据集文件',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_key` (`source_key`),
  KEY `idx_problem_code` (`problem_code`),
  KEY `idx_numeric_id` (`numeric_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
""".strip()
    )

    if truncate:
        parts.append(f"TRUNCATE TABLE `{table_name}`;")

    for r in rows:
        values = [
            sql_quote(r["source_key"]),
            sql_quote(r["problem_code"]),
            "NULL" if r["numeric_id"] is None else str(r["numeric_id"]),
            sql_quote(r["title_main"]),
            sql_quote(r["title_alt"]),
            sql_quote(r["problem_text"]),
            sql_quote(r["solution_text"]),
            sql_quote(r["problem_url"]),
            sql_quote(source_dataset),
        ]
        parts.append(
            f"""INSERT INTO `{table_name}`
(`source_key`,`problem_code`,`numeric_id`,`title_main`,`title_alt`,`problem_text`,`solution_text`,`problem_url`,`source_dataset`)
VALUES ({",".join(values)})
ON DUPLICATE KEY UPDATE
`problem_code`=VALUES(`problem_code`),
`numeric_id`=VALUES(`numeric_id`),
`title_main`=VALUES(`title_main`),
`title_alt`=VALUES(`title_alt`),
`problem_text`=VALUES(`problem_text`),
`solution_text`=VALUES(`solution_text`),
`problem_url`=VALUES(`problem_url`),
`source_dataset`=VALUES(`source_dataset`);
""".strip()
        )

    return "\n\n".join(parts) + "\n"


def run_mysql(sql_text: str, host: str, port: int, user: str, password: str, database: str) -> None:
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".sql", delete=False) as tf:
        tf.write(sql_text)
        sql_path = tf.name
    try:
        env = os.environ.copy()
        env["MYSQL_PWD"] = password
        cmd = [
            "mysql",
            "-h",
            host,
            "-P",
            str(port),
            "-u",
            user,
            "-D",
            database,
            "--default-character-set=utf8mb4",
            "--binary-mode=1",
        ]
        with open(sql_path, "rb") as fh:
            proc = subprocess.run(cmd, stdin=fh, env=env, capture_output=True)
        if proc.returncode != 0:
            raise RuntimeError(proc.stderr.decode("utf-8", errors="replace"))
    finally:
        try:
            os.remove(sql_path)
        except OSError:
            pass


def query_count(table_name: str, host: str, port: int, user: str, password: str, database: str) -> str:
    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    cmd = [
        "mysql",
        "-h",
        host,
        "-P",
        str(port),
        "-u",
        user,
        "-D",
        database,
        "--default-character-set=utf8mb4",
        "-N",
        "-e",
        f"SELECT COUNT(*) AS cnt FROM `{table_name}`;",
    ]
    proc = subprocess.run(cmd, env=env, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.decode("utf-8", errors="replace"))
    return proc.stdout.decode("utf-8", errors="replace").strip()


def main():
    parser = argparse.ArgumentParser(description="Import cleaned leetcode dataset into MySQL.")
    parser.add_argument("--cleaned", default=os.path.join(os.path.dirname(__file__), "solutions_cleaned.json"))
    parser.add_argument("--urls", default=os.path.join(os.path.dirname(__file__), "urls.json"))
    parser.add_argument("--table", default="leetcode_solutions")
    parser.add_argument("--db-host", default="localhost")
    parser.add_argument("--db-port", type=int, default=3306)
    parser.add_argument("--db-user", default="root")
    parser.add_argument("--db-password", default="123456")
    parser.add_argument("--db-name", default="ptadatabase")
    parser.add_argument("--truncate", action="store_true")
    args = parser.parse_args()

    url_map = build_url_map(args.urls)
    rows = build_rows(args.cleaned, url_map)
    sql_text = generate_sql(rows, args.table, os.path.basename(args.cleaned), args.truncate)
    run_mysql(sql_text, args.db_host, args.db_port, args.db_user, args.db_password, args.db_name)
    cnt = query_count(args.table, args.db_host, args.db_port, args.db_user, args.db_password, args.db_name)

    mapped_url = sum(1 for r in rows if r.get("problem_url"))
    print("import done")
    print(f"table={args.table}")
    print(f"rows_input={len(rows)}")
    print(f"rows_with_url={mapped_url}")
    print(f"rows_in_db={cnt}")


if __name__ == "__main__":
    main()

