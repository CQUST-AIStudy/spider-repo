import argparse

from db_connection import connect

parser = argparse.ArgumentParser(description="Deduplicate legacy score rows")
parser.add_argument("--apply", action="store_true", help="Delete duplicates; default is dry-run")
parser.add_argument("--verify-student-no")
parser.add_argument("--verify-experiment-id", type=int)
args = parser.parse_args()

conn = connect()
cur = conn.cursor()

# 统计去重前
cur.execute("SELECT COUNT(*) FROM score")
total_before = cur.fetchone()[0]
print(f"去重前 score 总记录: {total_before}")

cur.execute("SELECT COUNT(DISTINCT username, experiment_id) FROM score")
unique_pairs = cur.fetchone()[0]
print(f"唯一 (username, experiment_id) 对: {unique_pairs}")
print(f"需要删除: {total_before - unique_pairs} 条重复记录")

# 找出每个(username, experiment_id)的最高分记录的score_id
# 策略: 保留每组中score最高的那条，如果score相同保留score_id最大的
cur.execute("""
    CREATE TEMPORARY TABLE keep_ids AS
    SELECT MAX(score_id) as keep_id
    FROM (
        SELECT score_id, username, experiment_id, score,
               ROW_NUMBER() OVER (PARTITION BY username, experiment_id ORDER BY score DESC, score_id DESC) as rn
        FROM score
    ) ranked
    WHERE rn = 1
    GROUP BY username, experiment_id
""")
cur.execute("SELECT COUNT(*) FROM keep_ids")
keep_count = cur.fetchone()[0]
print(f"保留记录数: {keep_count}")

deleted = 0
if args.apply:
    cur.execute("DELETE FROM score WHERE score_id NOT IN (SELECT keep_id FROM keep_ids)")
    deleted = cur.rowcount
    conn.commit()
    print(f"已删除 {deleted} 条重复记录")
else:
    print(f"Dry-run: would delete {total_before - unique_pairs} duplicate records")

# 验证
cur.execute("SELECT COUNT(*) FROM score")
total_after = cur.fetchone()[0]
print(f"去重后 score 总记录: {total_after}")

if args.verify_student_no:
    cur.execute("""
        SELECT e.name, s.score
        FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
        WHERE s.username = %s
        ORDER BY s.experiment_id
    """, (args.verify_student_no,))
    print(f"\n=== {args.verify_student_no} 去重后 ===")
    total = 0
    for r in cur.fetchall():
        print(f"  {r[0]}: {r[1]}")
        if r[1]: total += float(r[1])
    print(f"  总分合计: {total}")

if args.verify_experiment_id:
    cur.execute("""
        SELECT COUNT(*), MIN(score), MAX(score), AVG(score)
        FROM score WHERE experiment_id = %s
    """, (args.verify_experiment_id,))
    r = cur.fetchone()
    print(f"\nexperiment_id={args.verify_experiment_id}: {r[0]} records, min={r[1]}, max={r[2]}, avg={r[3]}")

cur.execute("DROP TEMPORARY TABLE IF EXISTS keep_ids")
cur.close()
conn.close()
