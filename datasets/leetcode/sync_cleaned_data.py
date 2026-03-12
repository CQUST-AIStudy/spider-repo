#!/usr/bin/env python3
"""
将清洗后的LeetCode数据同步到MySQL数据库
"""

import json
import mysql.connector
import re
from pathlib import Path

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'ptadatabase',
    'charset': 'utf8mb4'
}

def extract_problem_info(input_text):
    """从题目输入中提取题号和标题"""
    # 匹配模式1：题目：LCR 002. 二进制求和 - 二进制加法
    pattern1 = r'题目：(LCR \d+)\.\s*([^-\n]+)(?:\s*-\s*([^\n]+))?'
    match = re.search(pattern1, input_text)
    
    if match:
        problem_code = match.group(1).strip()  # LCR 002
        title_main = match.group(2).strip()    # 二进制求和
        title_alt = match.group(3).strip() if match.group(3) else None  # 二进制加法
        
        # 提取数字部分
        number_match = re.search(r'LCR (\d+)', problem_code)
        numeric_id = int(number_match.group(1)) if number_match else None
        
        return {
            'problem_code': problem_code,
            'title_main': title_main,
            'title_alt': title_alt,
            'numeric_id': numeric_id
        }
    
    # 匹配模式2：题目：面试题 10.02. 变位词组 - 变位词组
    pattern2 = r'题目：(面试题 [\d.]+)\.\s*([^-\n]+)(?:\s*-\s*([^\n]+))?'
    match = re.search(pattern2, input_text)
    if match:
        problem_code = match.group(1).strip()
        title_main = match.group(2).strip()
        title_alt = match.group(3).strip() if match.group(3) else None
        
        return {
            'problem_code': problem_code,
            'title_main': title_main,
            'title_alt': title_alt,
            'numeric_id': None
        }
    
    # 匹配模式3：更宽松的匹配，只要包含LCR或面试题
    pattern3 = r'(LCR \d+|面试题 [\d.]+)[\.\s]*([^-\n\r]+?)(?:\s*-\s*([^\n\r]+?))?(?:\n|$)'
    match = re.search(pattern3, input_text)
    if match:
        problem_code = match.group(1).strip()
        title_main = match.group(2).strip()
        title_alt = match.group(3).strip() if match.group(3) else None
        
        # 提取数字部分
        number_match = re.search(r'LCR (\d+)', problem_code)
        numeric_id = int(number_match.group(1)) if number_match else None
        
        return {
            'problem_code': problem_code,
            'title_main': title_main,
            'title_alt': title_alt,
            'numeric_id': numeric_id
        }
    
    # 如果都没匹配到，尝试从第一行提取信息
    lines = input_text.split('\n')
    if lines:
        first_line = lines[0].strip()
        # 简单处理：如果第一行包含数字和中文，就当作题目
        if re.search(r'\d+', first_line) and re.search(r'[\u4e00-\u9fff]', first_line):
            return {
                'problem_code': f'UNKNOWN_{hash(first_line) % 10000}',
                'title_main': first_line[:50],  # 截取前50个字符作为标题
                'title_alt': None,
                'numeric_id': None
            }
    
    return None

def determine_difficulty(problem_text):
    """根据题目内容判断难度"""
    if '简单' in problem_text or 'Easy' in problem_text:
        return 'Easy'
    elif '困难' in problem_text or 'Hard' in problem_text:
        return 'Hard'
    else:
        return 'Medium'  # 默认中等

def sync_to_database():
    """同步数据到数据库"""
    input_file = Path("solutions_cleaned.json")
    
    if not input_file.exists():
        print(f"错误：找不到输入文件 {input_file}")
        return
    
    print(f"读取清洗后的数据：{input_file}")
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"数据条数：{len(data)}")
    
    # 连接数据库
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("数据库连接成功")
        
        # 检查表是否存在
        cursor.execute("SHOW TABLES LIKE 'leetcode_problem_bank'")
        if not cursor.fetchone():
            print("错误：表 leetcode_problem_bank 不存在，请先运行数据库迁移")
            return
        
        # 清空现有数据（可选）
        cursor.execute("DELETE FROM leetcode_problem_bank")
        print("清空现有数据")
        
        # 准备插入语句
        insert_sql = """
        INSERT INTO leetcode_problem_bank 
        (source_key, problem_code, numeric_id, title_main, title_alt, 
         problem_text, solution_text, difficulty, estimated_minutes, quality_score)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        success_count = 0
        error_count = 0
        
        for i, item in enumerate(data):
            try:
                if 'input' not in item:
                    continue
                
                # 提取题目信息
                problem_info = extract_problem_info(item['input'])
                if not problem_info:
                    print(f"跳过第 {i+1} 条：无法解析题目信息")
                    error_count += 1
                    continue
                
                # 构建数据
                source_key = f"cleaned_{i+1}"
                problem_code = problem_info['problem_code']
                numeric_id = problem_info['numeric_id']
                title_main = problem_info['title_main']
                title_alt = problem_info['title_alt']
                problem_text = item['input']
                solution_text = item.get('output', '')
                difficulty = determine_difficulty(problem_text)
                estimated_minutes = 30  # 默认30分钟
                quality_score = 0.8000  # 默认质量分数
                
                # 插入数据
                cursor.execute(insert_sql, (
                    source_key, problem_code, numeric_id, title_main, title_alt,
                    problem_text, solution_text, difficulty, estimated_minutes, quality_score
                ))
                
                success_count += 1
                if success_count % 50 == 0:
                    print(f"已处理 {success_count} 条记录...")
                
            except Exception as e:
                print(f"处理第 {i+1} 条记录时出错：{e}")
                error_count += 1
                continue
        
        # 提交事务
        conn.commit()
        print(f"\n同步完成！")
        print(f"成功：{success_count} 条")
        print(f"失败：{error_count} 条")
        
        # 验证数据
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        count = cursor.fetchone()[0]
        print(f"数据库中现有记录数：{count}")
        
    except mysql.connector.Error as e:
        print(f"数据库错误：{e}")
    except Exception as e:
        print(f"同步失败：{e}")
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()
        print("数据库连接已关闭")

if __name__ == "__main__":
    sync_to_database()