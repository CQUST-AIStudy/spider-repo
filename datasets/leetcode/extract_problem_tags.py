#!/usr/bin/env python3
"""
从LeetCode题目中提取算法标签和技能标签
"""

import json
import re
import mysql.connector
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

# 算法标签映射
ALGORITHM_TAGS = {
    # 数据结构
    '数组': ['array', 'list'],
    '链表': ['linked_list', 'list_node'],
    '栈': ['stack'],
    '队列': ['queue'],
    '哈希表': ['hash_table', 'map', 'dict'],
    '树': ['tree', 'binary_tree'],
    '二叉搜索树': ['bst', 'binary_search_tree'],
    '堆': ['heap', 'priority_queue'],
    '图': ['graph'],
    '字符串': ['string'],
    
    # 算法思想
    '双指针': ['two_pointers'],
    '滑动窗口': ['sliding_window'],
    '二分查找': ['binary_search'],
    '深度优先搜索': ['dfs', 'depth_first'],
    '广度优先搜索': ['bfs', 'breadth_first'],
    '动态规划': ['dp', 'dynamic_programming'],
    '贪心': ['greedy'],
    '回溯': ['backtrack'],
    '分治': ['divide_conquer'],
    '排序': ['sort'],
    '位运算': ['bit_manipulation'],
    
    # 具体算法
    '快速排序': ['quick_sort'],
    '归并排序': ['merge_sort'],
    '拓扑排序': ['topological_sort'],
    '最短路径': ['shortest_path'],
    '最小生成树': ['mst'],
    '并查集': ['union_find'],
    '前缀和': ['prefix_sum'],
    '差分数组': ['difference_array'],
    '单调栈': ['monotonic_stack'],
    '单调队列': ['monotonic_queue']
}

# 难度关键词
DIFFICULTY_KEYWORDS = {
    'easy': ['简单', '基础', '入门'],
    'medium': ['中等', '中级'],
    'hard': ['困难', '复杂', '高级', '进阶']
}
def extract_tags_from_text(text):
    """从题目文本中提取标签"""
    tags = set()
    text_lower = text.lower()
    
    # 提取算法标签
    for tag_name, keywords in ALGORITHM_TAGS.items():
        for keyword in keywords:
            if keyword in text_lower or tag_name in text:
                tags.add(tag_name)
                break
    
    # 基于题目内容的模式匹配
    patterns = {
        '数组': [r'数组', r'array', r'列表', r'list'],
        '字符串': [r'字符串', r'string', r'字符', r'文本'],
        '链表': [r'链表', r'linked.*list', r'节点', r'node'],
        '树': [r'二叉树', r'binary.*tree', r'树', r'tree'],
        '图': [r'图', r'graph', r'节点.*边', r'顶点'],
        '动态规划': [r'动态规划', r'dp', r'最优子结构', r'重叠子问题'],
        '贪心': [r'贪心', r'greedy', r'局部最优'],
        '回溯': [r'回溯', r'backtrack', r'递归.*撤销'],
        '双指针': [r'双指针', r'two.*pointer', r'左右指针'],
        '滑动窗口': [r'滑动窗口', r'sliding.*window', r'窗口'],
        '二分查找': [r'二分', r'binary.*search', r'折半查找'],
        '排序': [r'排序', r'sort', r'升序', r'降序'],
        '哈希表': [r'哈希', r'hash', r'映射', r'字典']
    }
    
    for tag, pattern_list in patterns.items():
        for pattern in pattern_list:
            if re.search(pattern, text, re.IGNORECASE):
                tags.add(tag)
                break
    
    return list(tags)

def analyze_difficulty(text):
    """分析题目难度"""
    text_lower = text.lower()
    
    # 检查难度关键词
    for difficulty, keywords in DIFFICULTY_KEYWORDS.items():
        for keyword in keywords:
            if keyword in text:
                return difficulty
    
    # 基于复杂度分析
    complexity_indicators = {
        'easy': ['O(n)', 'O(1)', '遍历', '简单'],
        'medium': ['O(n log n)', 'O(n²)', '递归', '动态规划'],
        'hard': ['O(2^n)', 'O(n!)', '复杂', '困难', '进阶']
    }
    
    for difficulty, indicators in complexity_indicators.items():
        for indicator in indicators:
            if indicator in text:
                return difficulty
    
    return 'medium'  # 默认中等

def extract_problem_tags():
    """提取所有题目的标签"""
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
        
        # 创建标签表（如果不存在）
        create_tag_table_sql = """
        CREATE TABLE IF NOT EXISTS leetcode_problem_tag (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            problem_id BIGINT NOT NULL,
            tag_type VARCHAR(50) NOT NULL,
            tag_value VARCHAR(100) NOT NULL,
            confidence DECIMAL(3,2) DEFAULT 0.80,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_problem_tag (problem_id, tag_type),
            INDEX idx_tag_value (tag_value),
            FOREIGN KEY (problem_id) REFERENCES leetcode_problem_bank(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
        cursor.execute(create_tag_table_sql)
        
        # 清空现有标签
        cursor.execute("DELETE FROM leetcode_problem_tag")
        print("清空现有标签数据")
        
        # 获取所有题目
        cursor.execute("SELECT id, problem_code, title_main, problem_text, solution_text, difficulty FROM leetcode_problem_bank")
        problems = cursor.fetchall()
        
        tag_insert_sql = """
        INSERT INTO leetcode_problem_tag (problem_id, tag_type, tag_value, confidence)
        VALUES (%s, %s, %s, %s)
        """
        
        success_count = 0
        
        for problem in problems:
            problem_id, problem_code, title_main, problem_text, solution_text, difficulty = problem
            
            try:
                # 合并题目文本用于分析
                full_text = f"{title_main} {problem_text} {solution_text}"
                
                # 提取算法标签
                algorithm_tags = extract_tags_from_text(full_text)
                
                # 插入算法标签
                for tag in algorithm_tags:
                    cursor.execute(tag_insert_sql, (problem_id, 'algorithm', tag, 0.85))
                
                # 插入难度标签
                cursor.execute(tag_insert_sql, (problem_id, 'difficulty', difficulty, 0.95))
                
                # 基于题目代码提取系列标签
                if 'LCR' in problem_code:
                    cursor.execute(tag_insert_sql, (problem_id, 'series', 'LCR', 0.99))
                elif '面试题' in problem_code:
                    cursor.execute(tag_insert_sql, (problem_id, 'series', '面试题', 0.99))
                
                success_count += 1
                if success_count % 50 == 0:
                    print(f"已处理 {success_count} 个题目...")
                
            except Exception as e:
                print(f"处理题目 {problem_id} 时出错：{e}")
                continue
        
        # 提交事务
        conn.commit()
        print(f"\n标签提取完成！")
        print(f"成功处理：{success_count} 个题目")
        
        # 统计标签分布
        cursor.execute("""
        SELECT tag_type, tag_value, COUNT(*) as count 
        FROM leetcode_problem_tag 
        GROUP BY tag_type, tag_value 
        ORDER BY tag_type, count DESC
        """)
        
        print("\n标签分布统计：")
        current_type = None
        for tag_type, tag_value, count in cursor.fetchall():
            if current_type != tag_type:
                print(f"\n{tag_type.upper()}:")
                current_type = tag_type
            print(f"  {tag_value}: {count}")
        
    except mysql.connector.Error as e:
        print(f"数据库错误：{e}")
    except Exception as e:
        print(f"标签提取失败：{e}")
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()
        print("\n数据库连接已关闭")

if __name__ == "__main__":
    extract_problem_tags()