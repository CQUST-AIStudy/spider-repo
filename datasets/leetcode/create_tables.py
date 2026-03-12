#!/usr/bin/env python3
"""
创建推荐系统相关的数据库表
"""

import mysql.connector

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'ptadatabase',
    'charset': 'utf8mb4'
}

def create_tables():
    """创建推荐系统相关表"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("数据库连接成功")
        
        # 1. 创建题目标签表
        create_tag_table_sql = """
        DROP TABLE IF EXISTS leetcode_problem_tag;
        
        CREATE TABLE leetcode_problem_tag (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            problem_id BIGINT NOT NULL,
            tag_type VARCHAR(50) NOT NULL COMMENT '标签类型：algorithm, difficulty, series, topic',
            tag_value VARCHAR(100) NOT NULL COMMENT '标签值',
            confidence DECIMAL(3,2) DEFAULT 0.80 COMMENT '置信度',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            
            INDEX idx_problem_tag (problem_id, tag_type),
            INDEX idx_tag_value (tag_value),
            INDEX idx_tag_type_value (tag_type, tag_value),
            
            FOREIGN KEY (problem_id) REFERENCES leetcode_problem_bank(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LeetCode题目标签表'
        """
        
        for statement in create_tag_table_sql.split(';'):
            if statement.strip():
                cursor.execute(statement)
        
        print("✓ 创建题目标签表成功")
        
        # 2. 创建学生技能状态表（如果不存在）
        create_skill_table_sql = """
        CREATE TABLE IF NOT EXISTS student_skill_state (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            student_id INT NOT NULL,
            tag_name VARCHAR(100) NOT NULL,
            mastery_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '掌握度分数 0-100',
            confidence_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '置信度分数 0-100',
            forgetting_score DECIMAL(5,2) DEFAULT 0.00 COMMENT '遗忘度分数 0-100',
            attempt_count INT DEFAULT 0 COMMENT '尝试次数',
            success_count INT DEFAULT 0 COMMENT '成功次数',
            avg_attempts_to_success DECIMAL(5,2) DEFAULT 0.00 COMMENT '平均成功尝试次数',
            last_practice_at TIMESTAMP NULL COMMENT '最后练习时间',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            
            UNIQUE KEY uk_student_tag (student_id, tag_name),
            INDEX idx_student_id (student_id),
            INDEX idx_tag_name (tag_name),
            INDEX idx_mastery_score (mastery_score),
            INDEX idx_last_practice (last_practice_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生技能状态表'
        """
        cursor.execute(create_skill_table_sql)
        print("✓ 创建学生技能状态表成功")
        
        # 3. 创建推荐结果表
        create_recommendation_table_sql = """
        CREATE TABLE IF NOT EXISTS leetcode_recommendation_result (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            request_id VARCHAR(64) NOT NULL,
            student_id INT NOT NULL,
            experiment_id INT NULL,
            problem_id BIGINT NOT NULL,
            rank_index INT NOT NULL COMMENT '推荐排序位置',
            score_total DECIMAL(6,4) DEFAULT 0.0000 COMMENT '总分',
            score_weakness_match DECIMAL(6,4) DEFAULT 0.0000 COMMENT '薄弱点匹配分',
            score_difficulty_match DECIMAL(6,4) DEFAULT 0.0000 COMMENT '难度匹配分',
            score_novelty DECIMAL(6,4) DEFAULT 0.0000 COMMENT '新颖性分',
            score_diversity DECIMAL(6,4) DEFAULT 0.0000 COMMENT '多样性分',
            score_quality DECIMAL(6,4) DEFAULT 0.0000 COMMENT '质量分',
            reason_text TEXT COMMENT '推荐理由',
            reason_json JSON COMMENT '推荐理由详细信息',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            
            UNIQUE KEY uk_request_rank (request_id, rank_index),
            INDEX idx_student_time (student_id, created_at DESC),
            INDEX idx_experiment_time (experiment_id, created_at DESC),
            INDEX idx_problem_id (problem_id),
            
            FOREIGN KEY (problem_id) REFERENCES leetcode_problem_bank(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LeetCode推荐结果表'
        """
        cursor.execute(create_recommendation_table_sql)
        print("✓ 创建推荐结果表成功")
        
        # 4. 创建推荐反馈表
        create_feedback_table_sql = """
        CREATE TABLE IF NOT EXISTS leetcode_recommendation_feedback (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            request_id VARCHAR(64) NOT NULL,
            student_id INT NOT NULL,
            problem_id BIGINT NOT NULL,
            action VARCHAR(20) NOT NULL COMMENT 'click, start, complete, skip, dislike',
            session_id VARCHAR(64) NULL,
            action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            
            INDEX idx_student_action_time (student_id, action, action_time DESC),
            INDEX idx_request_id (request_id),
            INDEX idx_problem_action (problem_id, action),
            
            FOREIGN KEY (problem_id) REFERENCES leetcode_problem_bank(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LeetCode推荐反馈表'
        """
        cursor.execute(create_feedback_table_sql)
        print("✓ 创建推荐反馈表成功")
        
        # 提交事务
        conn.commit()
        print("\n所有表创建完成！")
        
    except mysql.connector.Error as e:
        print(f"数据库错误：{e}")
    except Exception as e:
        print(f"创建表失败：{e}")
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()
        print("数据库连接已关闭")

if __name__ == "__main__":
    create_tables()