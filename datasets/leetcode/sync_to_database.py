#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LeetCode数据同步到数据库脚本
将清洗后的JSON数据同步到MySQL数据库
"""

import json
import mysql.connector
import re
from datetime import datetime
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'ptadatabase',
    'charset': 'utf8mb4'
}

# 标签关键词映射
TAG_KEYWORDS = {
    'array': r'数组|Array|array',
    'linked_list': r'链表|LinkedList|linked.*list',
    'stack': r'栈|Stack|stack',
    'queue': r'队列|Queue|queue',
    'tree': r'树|Tree|tree|二叉树',
    'heap': r'堆|Heap|heap|优先队列',
    'hash_table': r'哈希|Hash|hash|散列',
    'string': r'字符串|String|string',
    'sorting': r'排序|Sort|sort',
    'binary_search': r'二分|Binary.*Search|binary.*search',
    'dfs': r'深度优先|DFS|dfs|递归',
    'bfs': r'广度优先|BFS|bfs',
    'backtracking': r'回溯|Backtrack|backtrack',
    'greedy': r'贪心|Greedy|greedy',
    'divide_conquer': r'分治|Divide.*Conquer|divide.*conquer',
    'two_pointers': r'双指针|Two.*Pointer|two.*pointer',
    'sliding_window': r'滑动窗口|Sliding.*Window|sliding.*window',
    'dynamic_programming': r'动态规划|Dynamic.*Programming|dp|DP',
    'bit_manipulation': r'位运算|Bit.*Manipulation|bit.*manipulation',
    'math': r'数学|Math|math',
    'simulation': r'模拟|Simulation|simulation'
}

def extract_title(input_text):
    """从input中提取题目标题"""
    lines = input_text.split('\n')
    for line in lines:
        if line.startswith('题目：'):
            return line[3:].strip()
    return "未知题目"

def extract_problem_code(title):
    """提取题目编号，如 LCR 002"""
    if 'LCR' in title:
        match = re.search(r'LCR\s+\d+', title)
        if match:
            return match.group().strip()
    return None

def extract_numeric_id(problem_code):
    """从题目编号中提取数字ID"""
    if problem_code and ' ' in problem_code:
        try:
            return int(problem_code.split(' ')[1])
        except:
            pass
    return None

def extract_difficulty(input_text):
    """提取题目难度"""
    lower_input = input_text.lower()
    if '简单' in input_text or 'easy' in lower_input:
        return 'Easy'
    elif '困难' in input_text or 'hard' in lower_input:
        return 'Hard'
    elif '中等' in input_text or 'medium' in lower_input:
        return 'Medium'
    return 'Unknown'

def generate_source_key(title, problem_code):
    """生成唯一的source_key"""
    if problem_code:
        return f"code:{problem_code}"
    return f"title:{hash(title)}"

def extract_tags(problem_text, solution_text):
    """提取题目标签"""
    combined_text = (problem_text + " " + solution_text).lower()
    extracted_tags = []
    
    for tag_name, keywords in TAG_KEYWORDS.items():
        if re.search(keywords, combined_text, re.IGNORECASE):
            extracted_tags.append(tag_name)
    
    # 如果没有提取到标签，添加默认标签
    if not extracted_tags:
        extracted_tags.append('algorithm')
    
    return extracted_tags

def sync_problems_to_database(json_file_path):
    """同步题目到数据库"""
    try:
        # 连接数据库
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        logger.info(f"开始从 {json_file_path} 同步数据到数据库")
        
        # 读取JSON文件
        with open(json_file_path, 'r', encoding='utf-8') as f:
            problems_data = json.load(f)
        
        sync_count = 0
        total_count = len(problems_data)
        
        for i, problem_data in enumerate(problems_data):
            try:
                # 解析题目数据
                input_text = problem_data.get('input', '')
                output_text = problem_data.get('output', '')
                
                title = extract_title(input_text)
                problem_code = extract_problem_code(title)
                numeric_id = extract_numeric_id(problem_code)
                difficulty = extract_difficulty(input_text)
                source_key = generate_source_key(title, problem_code)
                
                # 插入或更新题目
                problem_sql = """
                INSERT INTO leetcode_problem_bank (
                    source_key, problem_code, numeric_id, title_main, 
                    problem_text, solution_text, difficulty, 
                    estimated_minutes, quality_score
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, %s
                ) ON DUPLICATE KEY UPDATE
                    problem_code = VALUES(problem_code),
                    numeric_id = VALUES(numeric_id),
                    title_main = VALUES(title_main),
                    problem_text = VALUES(problem_text),
                    solution_text = VALUES(solution_text),
                    difficulty = VALUES(difficulty),
                    estimated_minutes = VALUES(estimated_minutes),
                    quality_score = VALUES(quality_score),
                    updated_at = CURRENT_TIMESTAMP
                """
                
                cursor.execute(problem_sql, (
                    source_key, problem_code, numeric_id, title,
                    input_text, output_text, difficulty, 30, 0.8000
                ))
                
                # 获取题目ID
                problem_id = cursor.lastrowid
                if problem_id == 0:  # 如果是更新操作，获取现有ID
                    cursor.execute("SELECT id FROM leetcode_problem_bank WHERE source_key = %s", (source_key,))
                    result = cursor.fetchone()
                    if result:
                        problem_id = result[0]
                
                # 提取并插入标签
                if problem_id:
                    tags = extract_tags(input_text, output_text)
                    for j, tag_name in enumerate(tags):
                        tag_category = 'algorithm'  # 默认分类
                        if tag_name in ['array', 'linked_list', 'stack', 'queue', 'tree', 'heap', 'hash_table', 'string']:
                            tag_category = 'data_structure'
                        elif tag_name in ['two_pointers', 'sliding_window', 'dynamic_programming', 'bit_manipulation', 'math', 'simulation']:
                            tag_category = 'technique'
                        
                        is_primary = 1 if j == 0 else 0  # 第一个标签设为主标签
                        
                        tag_sql = """
                        INSERT INTO leetcode_problem_tag (
                            problem_id, tag_name, tag_category, is_primary
                        ) VALUES (%s, %s, %s, %s)
                        ON DUPLICATE KEY UPDATE
                            tag_category = VALUES(tag_category),
                            is_primary = VALUES(is_primary)
                        """
                        
                        cursor.execute(tag_sql, (problem_id, tag_name, tag_category, is_primary))
                
                sync_count += 1
                
                if (i + 1) % 50 == 0:
                    conn.commit()
                    logger.info(f"已处理 {i + 1}/{total_count} 个题目")
                    
            except Exception as e:
                logger.error(f"处理第 {i + 1} 个题目时出错: {e}")
                continue
        
        # 提交事务
        conn.commit()
        
        # 获取统计信息
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        total_problems = cursor.fetchone()[0]
        
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_tag")
        total_tags = cursor.fetchone()[0]
        
        logger.info(f"同步完成！")
        logger.info(f"成功同步 {sync_count} 个题目")
        logger.info(f"数据库中共有 {total_problems} 个题目")
        logger.info(f"数据库中共有 {total_tags} 个标签")
        
        cursor.close()
        conn.close()
        
        return sync_count
        
    except Exception as e:
        logger.error(f"同步数据失败: {e}")
        raise

if __name__ == "__main__":
    json_file = "solutions_cleaned.json"
    try:
        sync_count = sync_problems_to_database(json_file)
        print(f"数据同步完成，共同步 {sync_count} 个题目")
    except Exception as e:
        print(f"数据同步失败: {e}")
        exit(1)