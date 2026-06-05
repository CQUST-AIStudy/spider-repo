-- Store PTA canonical problem metadata and knowledge point paths.
-- The crawler obtains these fields from /api/problems/{problemId}.

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'pta_global_problem_id'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN pta_global_problem_id VARCHAR(64) NULL AFTER source_problem_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'problem_url'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN problem_url VARCHAR(512) NULL AFTER pta_global_problem_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'pta_difficulty_level'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN pta_difficulty_level TINYINT NULL AFTER problem_url',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'pta_difficulty_label'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN pta_difficulty_label VARCHAR(32) NULL AFTER pta_difficulty_level',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'knowledge_path'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN knowledge_path VARCHAR(1024) NULL AFTER max_score',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'knowledge_leaf'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN knowledge_leaf VARCHAR(256) NULL AFTER knowledge_path',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'knowledge_point_ids'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN knowledge_point_ids JSON NULL AFTER knowledge_leaf',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND column_name = 'knowledge_points_json'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE assignment_problem ADD COLUMN knowledge_points_json JSON NULL AFTER knowledge_point_ids',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND index_name = 'idx_assignment_problem_pta_global'
);
SET @sql := IF(
  @idx = 0,
  'CREATE INDEX idx_assignment_problem_pta_global ON assignment_problem(pta_global_problem_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND index_name = 'idx_assignment_problem_knowledge_leaf'
);
SET @sql := IF(
  @idx = 0,
  'CREATE INDEX idx_assignment_problem_knowledge_leaf ON assignment_problem(knowledge_leaf)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'assignment_problem'
    AND index_name = 'idx_assignment_problem_pta_difficulty'
);
SET @sql := IF(
  @idx = 0,
  'CREATE INDEX idx_assignment_problem_pta_difficulty ON assignment_problem(pta_difficulty_label)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem_score_detail'
    AND column_name = 'pta_global_problem_id'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE problem_score_detail ADD COLUMN pta_global_problem_id VARCHAR(64) NULL AFTER problem_label',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem_score_detail'
    AND column_name = 'knowledge_path'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE problem_score_detail ADD COLUMN knowledge_path VARCHAR(1024) NULL AFTER problem_type',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem_score_detail'
    AND column_name = 'knowledge_leaf'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE problem_score_detail ADD COLUMN knowledge_leaf VARCHAR(256) NULL AFTER knowledge_path',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem_score_detail'
    AND column_name = 'pta_difficulty_level'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE problem_score_detail ADD COLUMN pta_difficulty_level TINYINT NULL AFTER knowledge_leaf',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'problem_score_detail'
    AND column_name = 'pta_difficulty_label'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE problem_score_detail ADD COLUMN pta_difficulty_label VARCHAR(32) NULL AFTER pta_difficulty_level',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
