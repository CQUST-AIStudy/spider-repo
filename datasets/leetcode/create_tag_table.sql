-- 创建LeetCode题目标签表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LeetCode题目标签表';