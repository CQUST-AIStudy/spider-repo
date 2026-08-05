import argparse

from db_connection import connect

parser = argparse.ArgumentParser(description="Bind unowned legacy experiments to a teacher")
parser.add_argument("--teacher-id", type=int, required=True)
parser.add_argument("--name-pattern", required=True, help="SQL LIKE pattern, for example 计科24%")
parser.add_argument("--apply", action="store_true", help="Apply the update; default is dry-run")
args = parser.parse_args()

conn = connect()
cur = conn.cursor()

cur.execute(
    "SELECT experiment_id, name, teacher_id FROM experiment WHERE name LIKE %s ORDER BY experiment_id",
    (args.name_pattern,),
)
rows = cur.fetchall()
print("=== Matching experiments ===")
for r in rows:
    print(f'  id={r[0]}, name={r[1]}, teacher_id={r[2]}')

affected = sum(1 for row in rows if row[2] is None)
if args.apply:
    cur.execute(
        "UPDATE experiment SET teacher_id = %s WHERE teacher_id IS NULL AND name LIKE %s",
        (args.teacher_id, args.name_pattern),
    )
    affected = cur.rowcount
    conn.commit()
    print(f"\nBound {affected} experiments to teacher_id={args.teacher_id}")
else:
    print(f"\nDry-run: {affected} experiments would be bound to teacher_id={args.teacher_id}")

# 验证
cur.execute(
    "SELECT experiment_id, name, teacher_id FROM experiment WHERE name LIKE %s ORDER BY experiment_id",
    (args.name_pattern,),
)
rows = cur.fetchall()
print('\n=== 绑定后 计科24 实验 ===')
for r in rows:
    print(f'  id={r[0]}, name={r[1]}, teacher_id={r[2]}')

# 总览
cur.execute("SELECT teacher_id, COUNT(*) as cnt FROM experiment GROUP BY teacher_id")
rows = cur.fetchall()
print('\n=== 实验按teacher_id分布 ===')
for r in rows:
    print(f'  teacher_id={r[0]}: {r[1]}个实验')

cur.close()
conn.close()
