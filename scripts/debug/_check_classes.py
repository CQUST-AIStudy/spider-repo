from db_connection import connect

conn = connect()
cur = conn.cursor()

cur.execute("SELECT * FROM teaching_class LIMIT 10")
rows = cur.fetchall()
cols = [d[0] for d in cur.description]
print(f"=== teaching_class ({len(rows)} rows) ===")
print(f"  columns: {cols}")
for r in rows:
    print(f"  {dict(zip(cols, r))}")

cur.close()
conn.close()
