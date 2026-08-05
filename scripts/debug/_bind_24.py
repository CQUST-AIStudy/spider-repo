import pymysql
conn = pymysql.connect(host='localhost', port=3306, user='root', password='123456', database='ptadatabase', charset='utf8mb4')
cur = conn.cursor()

# 查看当前计科24实验的teacher_id状态
cur.execute("SELECT experiment_id, name, teacher_id FROM experiment WHERE name LIKE '%%计科24%%' ORDER BY experiment_id")
rows = cur.fetchall()
print('=== 绑定前 计科24 实验 ===')
for r in rows:
    print(f'  id={r[0]}, name={r[1]}, teacher_id={r[2]}')

# 绑定到 teacher1 (teacher_id=1)
cur.execute("UPDATE experiment SET teacher_id = 1 WHERE teacher_id IS NULL AND name LIKE '%%计科24%%'")
affected = cur.rowcount
conn.commit()
print(f'\n已绑定 {affected} 个计科24实验到 teacher1 (teacher_id=1)')

# 验证
cur.execute("SELECT experiment_id, name, teacher_id FROM experiment WHERE name LIKE '%%计科24%%' ORDER BY experiment_id")
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
