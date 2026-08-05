import argparse

from db_connection import connect

parser = argparse.ArgumentParser(description="Inspect score records for a student")
parser.add_argument("--student-no", required=True)
args = parser.parse_args()

conn = connect()
cur = conn.cursor()

# 计科24 problem_score_detail
cur.execute("""
    SELECT e.experiment_id, e.name, COUNT(p.id) as cnt
    FROM experiment e LEFT JOIN problem_score_detail p ON e.experiment_id = p.experiment_id
    WHERE e.name LIKE '计科24%%'
    GROUP BY e.experiment_id, e.name
    ORDER BY e.experiment_id
""")
print("=== 计科24 problem_score_detail ===")
for r in cur.fetchall():
    print(f"  id={r[0]}, {r[1]}: {r[2]} records")

# 学生端看到780分的问题 - 看看student1的数据
cur.execute("""
    SELECT s.experiment_id, e.name, s.score
    FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
    WHERE s.username = %s
    ORDER BY s.experiment_id
""", (args.student_no,))
print(f"\n=== {args.student_no} 的分数 ===")
total = 0
for r in cur.fetchall():
    print(f"  {r[1]}: {r[2]}")
    if r[2]: total += float(r[2])
print(f"  总分合计: {total}")

# 看看score表里有没有重复记录（同一学生同一实验多条）
cur.execute("""
    SELECT username, experiment_id, COUNT(*) as cnt
    FROM score
    GROUP BY username, experiment_id
    HAVING cnt > 1
    LIMIT 10
""")
print("\n=== 重复记录 ===")
rows = cur.fetchall()
if rows:
    for r in rows:
        print(f"  username={r[0]}, exp_id={r[1]}, count={r[2]}")
else:
    print("  (无重复)")

# 看看score表里有多少条记录per experiment for 计科23
cur.execute("""
    SELECT e.experiment_id, e.name, COUNT(DISTINCT s.username) as students
    FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
    WHERE e.name LIKE '计科23%%'
    GROUP BY e.experiment_id, e.name
    ORDER BY e.experiment_id
""")
print("\n=== 计科23 每实验学生数 ===")
for r in cur.fetchall():
    print(f"  {r[1]}: {r[2]} students")

cur.close()
conn.close()
