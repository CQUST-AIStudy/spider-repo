#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将现有的 leetcode_solutions 表数据迁移到 LeetCode 推荐系统表结构
"""

import mysql.connector
import re
import logging
from typing import Dict, List, Optional

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

def extract_difficulty(problem_text: str, solution_text: str) -> str:
    """提取题目难度"""
    combined_text = (problem_text + " " + solution_text).lower()
    
    if '简单' in problem_text or 'easy' in combined_text:
        return 'Easy'
    elif '困难' in problem_text or 'hard' in combined_text:
        return 'Hard'
    elif '中等' in problem_text or 'medium' in combined_text:
        return 'Medium'
    
    # 根据题目复杂度推断难度
    if any(keyword in combined_text for keyword in ['动态规划', 'dp', '回溯', '分治']):
        return 'Hard'
    elif any(keyword in combined_text for keyword in ['二分', '双指针', '滑动窗口']):
        return 'Medium'
    else:
        return 'Easy'

def estimate_time(problem_text: str, solution_text: str, difficulty: str) -> int:
    """估算解题时间（分钟）"""
    base_time = {
        'Easy': 20,
        'Medium': 35,
        'Hard': 60
    }
    
    time = base_time.get(difficulty, 30)
    
    # 根据解法复杂度调整
    combined_text = (problem_text + " " + solution_text).lower()
    if any(keyword in combined_text for keyword in ['动态规划', 'dp']):
        time += 15
    if any(keyword in combined_text for keyword in ['回溯', 'backtrack']):
        time += 10
    if any(keyword in combined_text for keyword in ['图', 'graph']):
        time += 10
        
    return min(time, 90)  # 最大90分钟

def calculate_quality_score(problem_text: str, solution_text: str) -> float:
    """计算题目质量分数"""
    score = 0.8  # 基础分数
    
    # 根据解法数量和详细程度调整
    if '方法一' in solution_text and '方法二' in solution_text:
        score += 0.1
    if '复杂度分析' in solution_text:
        score += 0.05
    if len(solution_text) > 1000:
        score += 0.05
    
    return min(score, 1.0)

def extract_tags(problem_text: str, solution_text: str) -> List[str]:
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

def migrate_data():
    """执行数据迁移"""
    try:
        # 连接数据库
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        logger.info("开始数据迁移...")
        
        # 1. 查询现有数据
        cursor.execute("""
            SELECT id, source_key, problem_code, numeric_id, title_main, title_alt,
                   problem_text, solution_text, problem_url
            FROM leetcode_solutions
            ORDER BY id
        """)
        
        source_data = cursor.fetchall()
        logger.info(f"从 leetcode_solutions 表读取到 {len(source_data)} 条记录")
        
        # 2. 迁移到 leetcode_problem_bank
        migrated_count = 0
        tag_count = 0
        
        for row in source_data:
            (old_id, source_key, problem_code, numeric_id, title_main, title_alt,
             problem_text, solution_text, problem_url) = row
            
            # 提取额外信息
            difficulty = extract_difficulty(problem_text, solution_text)
            estimated_minutes = estimate_time(problem_text, solution_text, difficulty)
            quality_score = calculate_quality_score(problem_text, solution_text)
            
            # 插入到 leetcode_problem_bank
            insert_problem_sql = """
                INSERT INTO leetcode_problem_bank (
                    source_key, problem_code, numeric_id, title_main, title_alt,
                    problem_text, solution_text, source_url, difficulty,
                    estimated_minutes, quality_score
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
                ) ON DUPLICATE KEY UPDATE
                    problem_code = VALUES(problem_code),
                    numeric_id = VALUES(numeric_id),
                    title_main = VALUES(title_main),
                    title_alt = VALUES(title_alt),
                    problem_text = VALUES(problem_text),
                    solution_text = VALUES(solution_text),
                    source_url = VALUES(source_url),
                    difficulty = VALUES(difficulty),
                    estimated_minutes = VALUES(estimated_minutes),
                    quality_score = VALUES(quality_score)
            """
            
            cursor.execute(insert_problem_sql, (
                source_key, problem_code, numeric_id, title_main, title_alt,
                problem_text, solution_text, problem_url, difficulty,
                estimated_minutes, quality_score
            ))
            
            # 获取插入的问题ID
            problem_id = cursor.lastrowid
            if cursor.rowcount == 0:  # 如果是更新操作
                cursor.execute("SELECT id FROM leetcode_problem_bank WHERE source_key = %s", (source_key,))
                result = cursor.fetchone()
                if result:
                    problem_id = result[0]
            
            # 提取并插入标签
            tags = extract_tags(problem_text, solution_text)
            for tag_name in tags:
                # 确定标签类别
                if tag_name in ['array', 'linked_list', 'stack', 'queue', 'tree', 'heap', 'hash_table']:
                    tag_category = 'data_structure'
                elif tag_name in ['dfs', 'bfs', 'backtracking', 'greedy', 'divide_conquer', 
                                'two_pointers', 'sliding_window', 'dynamic_programming', 
                                'binary_search', 'bit_manipulation']:
                    tag_category = 'technique'
                else:
                    tag_category = 'algorithm'
                
                # 插入标签
                insert_tag_sql = """
                    INSERT INTO leetcode_problem_tag (
                        problem_id, tag_name, tag_category, relevance_score, is_primary
                    ) VALUES (
                        %s, %s, %s, %s, %s
                    ) ON DUPLICATE KEY UPDATE
                        relevance_score = VALUES(relevance_score)
                """
                
                is_primary = 1 if tags.index(tag_name) == 0 else 0  # 第一个标签为主标签
                cursor.execute(insert_tag_sql, (problem_id, tag_name, tag_category, 1.0, is_primary))
                tag_count += 1
            
            migrated_count += 1
            
            if migrated_count % 50 == 0:
                logger.info(f"已迁移 {migrated_count} 条记录...")
        
        # 提交事务
        conn.commit()
        
        # 3. 统计结果
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        problem_count = cursor.fetchone()[0]
        
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_tag")
        total_tag_count = cursor.fetchone()[0]
        
        logger.info("数据迁移完成！")
        logger.info(f"迁移记录数: {migrated_count}")
        logger.info(f"题库总数: {problem_count}")
        logger.info(f"标签总数: {total_tag_count}")
        
        # 4. 显示一些统计信息
        cursor.execute("""
            SELECT difficulty, COUNT(*) as count 
            FROM leetcode_problem_bank 
            GROUP BY difficulty
        """)
        difficulty_stats = cursor.fetchall()
        
        logger.info("难度分布:")
        for difficulty, count in difficulty_stats:
            logger.info(f"  {difficulty}: {count}")
        
        cursor.execute("""
            SELECT tag_name, COUNT(*) as count 
            FROM leetcode_problem_tag 
            GROUP BY tag_name 
            ORDER BY count DESC 
            LIMIT 10
        """)
        tag_stats = cursor.fetchall()
        
        logger.info("热门标签 (Top 10):")
        for tag_name, count in tag_stats:
            logger.info(f"  {tag_name}: {count}")
        
    except Exception as e:
        logger.error(f"数据迁移失败: {e}")
        if conn:
            conn.rollback()
        raise
    finally:
        if cursor:
            cursor.close()
        if conn:
            conn.close()

if __name__ == "__main__":
    migrate_data()