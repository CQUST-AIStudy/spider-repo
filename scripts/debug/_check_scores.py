from db_connection import connect

conn = connect()
cur = conn.cursor()

# 1. score表结构
cur.execute("DESCRIBE score")
print("=== score 表结构 ===")
for r in cur.fetchall():
    print(f"  {r[0]}: {r[1]}")

# 2. 看看score表中分数>100的记录
cur.execute("""
    SELECT e.name, s.score, COUNT(*) as cnt
    FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
    WHERE s.score > 100
    GROUP BY e.name, s.score
    ORDER BY s.score DESC
    LIMIT 20
""")
print("\n=== score > 100 的记录 ===")
for r in cur.fetchall():
    print(f"  {r[0]}: score={r[1]}, count={r[2]}")

# 3. 看看每个实验的score范围
cur.execute("""
    SELECT e.name, MIN(s.score), MAX(s.score), AVG(s.score), COUNT(*)
    FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
    WHERE s.score IS NOT NULL
    GROUP BY e.experiment_id, e.name
    ORDER BY e.experiment_id
""")
print("\n=== 每个实验的分数范围 ===")
for r in cur.fetchall():
    print(f"  {r[0]}: min={r[1]}, max={r[2]}, avg={r[3]:.1f}, count={r[4]}")

# 4. 看看experiment表的topic_sum（满分）
cur.execute("""
    SELECT experiment_id, name, topic_sum
    FROM experiment
    ORDER BY experiment_id
""")
print("\n=== experiment.topic_sum ===")
for r in cur.fetchall():
    print(f"  id={r[0]}, name={r[1]}, topic_sum={r[2]}")

# 5. 计科24有没有problem_score_detail数据
cur.execute("""
    SELECT e.name, COUNT(*) as cnt
    FROM problem_score_detail p JOIN experiment e ON p.experiment_id = e.experiment_id
    WHERE e.name LIKE '计科24%%'
    GROUP BY e.name
    ORDER BY e.experiment_id
""")
print("\n=== 计科24 problem_score_detail ===")
rows = cur.fetchall()
if rows:
    for r in rows:
        print(f"  {r[0]}: {r[1]} records")
else:
    print("  (无数据)")

# 6. 计科24有没有score数据
cur.execute("""
    SELECT e.name, COUNT(*) as cnt
    FROM score s JOIN experiment e ON s.experiment_id = e.experiment_id
    WHERE e.name LIKE '计科24%%'
    GROUP BY e.name
    ORDER BY e.experiment_id
""")
print("\n=== 计科24 score ===")
rows = cur.fetchall()
if rows:
    for r in rows:
        print(f"  {r[0]}: {r[1]} records")
else:
    print("  (无数据)")

cur.close()
conn.close()
