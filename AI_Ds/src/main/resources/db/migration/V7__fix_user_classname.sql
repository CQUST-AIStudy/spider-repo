-- 修复 user 表中学生的 classname 字段编码问题
UPDATE user SET classname = '计科23' WHERE role = 'student' AND (classname LIKE '%?%' OR classname LIKE '%??%');
