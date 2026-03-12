-- 插入测试LeetCode题目数据

INSERT INTO leetcode_problem_bank (
    source_key, problem_code, numeric_id, title_main, title_alt, 
    problem_text, solution_text, difficulty, estimated_minutes, quality_score
) VALUES 
(
    'test_1', 
    'LCR 001', 
    1, 
    '两数相除', 
    '整数除法',
    '# LCR 001. 两数相除\n\n给定两个整数 a 和 b ，求它们的除法的商 a/b ，要求不得使用乘法、除法和 mod 运算符。\n\n整数除法的结果应当截去（truncate）其小数部分，例如：truncate(8.345) = 8 以及 truncate(-2.7335) = -2\n\n假设我们的环境只能存储 32 位有符号整数，其数值范围是 [−2^31, 2^31−1]。本题中，如果除法结果溢出，则返回 2^31 − 1\n\n## 示例 1：\n输入：a = 15, b = 2\n输出：7\n解释：15/2 = truncate(7.5) = 7\n\n## 示例 2：\n输入：a = 7, b = -3\n输出：-2\n解释：7/-3 = truncate(-2.33333..) = -2\n\n## 提示：\n- -2^31 <= a, b <= 2^31 - 1\n- b != 0',
    '## 解题思路\n\n这道题要求我们实现除法运算，但不能使用乘法、除法和取模运算符。我们可以使用**位运算**来解决这个问题。\n\n### 方法：位运算 + 倍增\n\n基本思想是通过**减法**来实现除法，但直接减法会超时，所以我们使用**倍增**的方法来优化。\n\n```java\nclass Solution {\n    public int divide(int dividend, int divisor) {\n        // 处理溢出情况\n        if (dividend == Integer.MIN_VALUE && divisor == -1) {\n            return Integer.MAX_VALUE;\n        }\n        \n        // 确定结果的符号\n        boolean negative = (dividend > 0) ^ (divisor > 0);\n        \n        // 转换为正数处理（使用long避免溢出）\n        long a = Math.abs((long) dividend);\n        long b = Math.abs((long) divisor);\n        \n        long result = 0;\n        \n        while (a >= b) {\n            long temp = b;\n            long multiple = 1;\n            \n            // 倍增找到最大的multiple使得b * multiple <= a\n            while (a >= (temp << 1)) {\n                temp <<= 1;\n                multiple <<= 1;\n            }\n            \n            a -= temp;\n            result += multiple;\n        }\n        \n        return negative ? (int) -result : (int) result;\n    }\n}\n```\n\n### 复杂度分析\n- **时间复杂度**: O(log²n)，其中n是被除数的大小\n- **空间复杂度**: O(1)',
    'Medium',
    30,
    0.85
),
(
    'test_2',
    'LCR 002', 
    2,
    '二进制求和',
    '二进制加法',
    '# LCR 002. 二进制求和\n\n给定两个二进制字符串，返回它们的和（用二进制表示）。\n\n输入为**非空**字符串且只包含数字 1 和 0。\n\n## 示例 1:\n输入: a = "11", b = "1"\n输出: "100"\n\n## 示例 2:\n输入: a = "1010", b = "1011"\n输出: "10101"\n\n## 提示：\n- 每个字符串仅由字符 ''0'' 或 ''1'' 组成。\n- 1 <= a.length, b.length <= 10^4\n- 字符串如果不是 "0" ，就都不含前导零。',
    '## 解题思路\n\n这道题要求我们实现二进制字符串的加法运算。我们可以**从右到左**逐位相加，处理进位。\n\n### 方法：模拟加法\n\n```java\nclass Solution {\n    public String addBinary(String a, String b) {\n        StringBuilder result = new StringBuilder();\n        int i = a.length() - 1;\n        int j = b.length() - 1;\n        int carry = 0;\n        \n        while (i >= 0 || j >= 0 || carry > 0) {\n            int sum = carry;\n            \n            if (i >= 0) {\n                sum += a.charAt(i) - ''0'';\n                i--;\n            }\n            \n            if (j >= 0) {\n                sum += b.charAt(j) - ''0'';\n                j--;\n            }\n            \n            result.append(sum % 2);\n            carry = sum / 2;\n        }\n        \n        return result.reverse().toString();\n    }\n}\n```\n\n### 复杂度分析\n- **时间复杂度**: O(max(m,n))，其中m和n分别是两个字符串的长度\n- **空间复杂度**: O(max(m,n))，用于存储结果',
    'Easy',
    20,
    0.90
);