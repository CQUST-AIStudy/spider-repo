import pymysql
conn = pymysql.connect(host='localhost', port=3306, user='root', password='123456', database='ptadatabase', charset='utf8mb4')
cur = conn.cursor()

# 检查problem_score_detail是否有重复
cur.execute("SELECT COUNT(*) FROM problem_score_detail")
total = cur.fetchone()[0]
cur.execute("SELECT COUNT(DISTINCT student_id, experiment_id, problem_label) FROM problem_score_detail")
unique = cur.fetchone()[0]
print(f"problem_score_detail: total={total}, unique={unique}, duplicates={total - unique}")

if total > unique:
    # 去重: 保留每组中actual_score最高的记录
    cur.execute("""
        CREATE TEMPORARY TABLE psd_keep AS
        SELECT MAX(id) as keep_id
        FROM (
            SELECT id, student_id, experiment_id, problem_label, actual_score,
                   ROW_NUMBER() OVER (PARTITION BY student_id, experiment_id, problem_label ORDER BY actual_score DESC, id DESC) as rn
            FROM problem_score_detail
        ) ranked
        WHERE rn = 1
        GROUP BY student_id, experiment_id, problem_label
    """)
    cur.execute("SELECT COUNT(*) FROM psd_keep")
    keep = cur.fetchone()[0]
    print(f"保留: {keep}")
    
    cur.execute("DELETE FROM problem_score_detail WHERE id NOT IN (SELECT keep_id FROM psd_keep)")
    deleted = cur.rowcount
    conn.commit()
    print(f"已删除 {deleted} 条重复记录")
    
    cur.execute("SELECT COUNT(*) FROM problem_score_detail")
    after = cur.fetchone()[0]
    print(f"去重后: {after}")
    cur.execute("DROP TEMPORARY TABLE IF EXISTS psd_keep")
else:
    print("无重复记录")

cur.close()
conn.close()
