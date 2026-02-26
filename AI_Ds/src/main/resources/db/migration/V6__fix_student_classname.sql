-- 修复学生表 class_name 字段编码问题
-- 原始数据因字符集问题导致中文变成 '?' 字符
-- 将所有包含 '?' 的 class_name 统一更新为正确的 '计科23'

UPDATE student SET class_name = '计科23' WHERE class_name LIKE '%?%' OR class_name IS NULL;
