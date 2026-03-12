#!/usr/bin/env python3
"""
检查数据库状态
"""

import mysql.connector

def check_database():
    try:
        conn = mysql.connector.connect(
            host='localhost',
            port=3306,
            user='root',
            password='123456',
            database='ptadatabase',
            charset='utf8mb4'
        )
        cursor = conn.cursor()
        
        print("=== 数据库连接成功 ===")
        
        # 检查题目数量
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        problem_count = cursor.fetchone()[0]
        print(f"题目总数: {problem_count}")
        
        # 检查标签数量
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_tag")
        tag_count = cursor.fetchone()[0]
        print(f"标签总数: {tag_count}")
        
        # 检查标签分布
        cursor.execute("""
        SELECT tag_type, COUNT(*) as count 
        FROM leetcode_problem_tag 
        GROUP BY tag_type 
        ORDER BY count DESC
        """)
        
        print("\n标签类型分布:")
        for tag_type, count in cursor.fetchall():
            print(f"  {tag_type}: {count}")
        
        # 检查学生技能状态
        cursor.execute("SELECT COUNT(*) FROM student_skill_state")
        skill_count = cursor.fetchone()[0]
        print(f"\n学生技能状态记录数: {skill_count}")
        
        # 创建测试学生技能数据
        if skill_count == 0:
            print("\n创建测试学生技能数据...")
            test_student_id = 1
            
            sample_skills = [
                (test_student_id, '数组', 45.0, 60.0, 20.0, 5, 2),
                (test_student_id, '字符串', 65.0, 75.0, 10.0, 8, 5),
                (test_student_id, '动态规划', 25.0, 40.0, 50.0, 3, 0),
                (test_student_id, '贪心', 55.0, 70.0, 15.0, 6, 3),
                (test_student_id, '图', 30.0, 45.0, 40.0, 2, 1),
                (test_student_id, '树', 70.0, 80.0, 5.0, 10, 7),
            ]
            
            # 先删除现有数据
            cursor.execute("DELETE FROM student_skill_state WHERE student_id = %s", (test_student_id,))
            
            # 插入示例数据
            insert_sql = """
            INSERT INTO student_skill_state 
            (student_id, tag_name, mastery_score, confidence_score, forgetting_score, attempt_count, success_count)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            
            cursor.executemany(insert_sql, sample_skills)
            conn.commit()
            
            print(f"为学生 {test_student_id} 创建了 {len(sample_skills)} 个技能状态记录")
        
        cursor.close()
        conn.close()
        print("\n数据库检查完成！")
        
    except Exception as e:
        print(f"数据库连接失败: {e}")

if __name__ == "__main__":
    check_database()