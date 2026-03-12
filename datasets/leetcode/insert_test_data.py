#!/usr/bin/env python3
"""
手动插入几条测试数据到数据库
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

def insert_test_data():
    """插入测试数据"""
    
    test_problems = [
        {
            'source_key': 'test_1',
            'problem_code': 'LCR 001',
            'numeric_id': 1,
            'title_main': '两数相除',
            'title_alt': '整数除法',
            'problem_text': '''# LCR 001. 两数相除

给定两个整数 a 和 b ，求它们的除法的商 a/b ，要求不得使用乘法、除法和 mod 运算符。

整数除法的结果应当截去（truncate）其小数部分，例如：truncate(8.345) = 8 以及 truncate(-2.7335) = -2

假设我们的环境只能存储 32 位有符号整数，其数值范围是 [−2^31, 2^31−1]。本题中，如果除法结果溢出，则返回 2^31 − 1

## 示例 1：
输入：a = 15, b = 2
输出：7
解释：15/2 = truncate(7.5) = 7

## 示例 2：
输入：a = 7, b = -3
输出：-2
解释：7/-3 = truncate(-2.33333..) = -2

## 提示：
- -2^31 <= a, b <= 2^31 - 1
- b != 0''',
            'solution_text': '''## 解题思路

这道题要求实现除法运算，但不能使用乘法、除法和取模运算符。

### 方法一：减法模拟
最直观的方法是用减法来模拟除法过程，但这种方法效率很低。

### 方法二：位运算优化
我们可以使用位运算来优化：
1. 处理符号位
2. 使用位移操作来快速逼近结果
3. 处理溢出情况

```java
class Solution {
    public int divide(int dividend, int divisor) {
        // 处理溢出情况
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return Integer.MAX_VALUE;
        }
        
        // 确定结果符号
        boolean negative = (dividend > 0) ^ (divisor > 0);
        
        // 转换为正数处理
        long a = Math.abs((long) dividend);
        long b = Math.abs((long) divisor);
        
        long result = 0;
        while (a >= b) {
            long temp = b;
            long multiple = 1;
            
            // 找到最大的倍数
            while (a >= (temp << 1)) {
                temp <<= 1;
                multiple <<= 1;
            }
            
            a -= temp;
            result += multiple;
        }
        
        return negative ? (int) -result : (int) result;
    }
}
```

### 复杂度分析
- 时间复杂度：O(log n)
- 空间复杂度：O(1)''',
            'difficulty': 'Medium',
            'estimated_minutes': 30,
            'quality_score': 0.9
        },
        {
            'source_key': 'test_2',
            'problem_code': 'LCR 002',
            'numeric_id': 2,
            'title_main': '二进制求和',
            'title_alt': '二进制加法',
            'problem_text': '''# LCR 002. 二进制求和

给定两个 01 字符串 a 和 b ，请计算它们的和，并以二进制字符串的形式输出。

输入为非空字符串且只包含数字 1 和 0。

## 示例 1：
输入: a = "11", b = "10"
输出: "101"

## 示例 2：
输入: a = "1010", b = "1011"
输出: "10101"

## 提示：
- 每个字符串仅由字符 '0' 或 '1' 组成
- 1 <= a.length, b.length <= 10^4
- 字符串如果不是 "0" ，就都不含前导零''',
            'solution_text': '''## 解题思路

这道题要求实现二进制字符串的加法运算。

### 方法一：模拟加法过程
我们可以模拟手工计算二进制加法的过程：
1. 从右到左逐位相加
2. 处理进位
3. 构建结果字符串

```java
class Solution {
    public String addBinary(String a, String b) {
        StringBuilder result = new StringBuilder();
        int i = a.length() - 1;
        int j = b.length() - 1;
        int carry = 0;
        
        while (i >= 0 || j >= 0 || carry > 0) {
            int sum = carry;
            
            if (i >= 0) {
                sum += a.charAt(i) - '0';
                i--;
            }
            
            if (j >= 0) {
                sum += b.charAt(j) - '0';
                j--;
            }
            
            result.append(sum % 2);
            carry = sum / 2;
        }
        
        return result.reverse().toString();
    }
}
```

### 复杂度分析
- 时间复杂度：O(max(m, n))，其中 m 和 n 分别是字符串 a 和 b 的长度
- 空间复杂度：O(max(m, n))，用于存储结果''',
            'difficulty': 'Easy',
            'estimated_minutes': 20,
            'quality_score': 0.85
        },
        {
            'source_key': 'test_3',
            'problem_code': 'LCR 003',
            'numeric_id': 3,
            'title_main': '比特位计数',
            'title_alt': '前n个数字二进制中1的个数',
            'problem_text': '''# LCR 003. 比特位计数

给定一个非负整数 n ，请计算 0 到 n 之间的每个数字的二进制表示中 1 的个数，并输出一个数组。

## 示例 1:
输入: n = 2
输出: [0,1,1]
解释:
0 --> 0
1 --> 1
2 --> 10

## 示例 2:
输入: n = 5
输出: [0,1,1,2,1,2]
解释:
0 --> 0
1 --> 1
2 --> 10
3 --> 11
4 --> 100
5 --> 101

## 说明:
- 0 <= n <= 10^5

## 进阶:
- 给出时间复杂度为O(n*sizeof(integer))的解答非常容易。但你可以在线性时间O(n)内用一趟扫描做到吗？
- 要求算法的空间复杂度为O(n)。
- 你能进一步完善解法吗？要求在C++或任何其他语言中不使用任何内置函数来执行此操作。''',
            'solution_text': '''## 解题思路

这道题要求计算从0到n的每个数字的二进制表示中1的个数。

### 方法一：动态规划
我们可以利用已计算过的结果来优化：

对于任意数字 i，有以下关系：
- 如果 i 是偶数，则 bits[i] = bits[i/2]
- 如果 i 是奇数，则 bits[i] = bits[i/2] + 1

```java
class Solution {
    public int[] countBits(int n) {
        int[] bits = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            bits[i] = bits[i >> 1] + (i & 1);
        }
        
        return bits;
    }
}
```

### 方法二：Brian Kernighan算法
利用 x & (x-1) 可以消除x的最低位的1：

```java
class Solution {
    public int[] countBits(int n) {
        int[] bits = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            bits[i] = bits[i & (i - 1)] + 1;
        }
        
        return bits;
    }
}
```

### 复杂度分析
- 时间复杂度：O(n)
- 空间复杂度：O(1)（除了返回数组）''',
            'difficulty': 'Easy',
            'estimated_minutes': 25,
            'quality_score': 0.88
        }
    ]
    
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()
        print("数据库连接成功")
        
        # 检查表是否存在
        cursor.execute("SHOW TABLES LIKE 'leetcode_problem_bank'")
        if not cursor.fetchone():
            print("错误：表 leetcode_problem_bank 不存在")
            return
        
        # 清空现有数据
        cursor.execute("DELETE FROM leetcode_problem_bank")
        print("清空现有数据")
        
        # 插入测试数据
        insert_sql = """
        INSERT INTO leetcode_problem_bank 
        (source_key, problem_code, numeric_id, title_main, title_alt, 
         problem_text, solution_text, difficulty, estimated_minutes, quality_score)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        
        for problem in test_problems:
            cursor.execute(insert_sql, (
                problem['source_key'],
                problem['problem_code'],
                problem['numeric_id'],
                problem['title_main'],
                problem['title_alt'],
                problem['problem_text'],
                problem['solution_text'],
                problem['difficulty'],
                problem['estimated_minutes'],
                problem['quality_score']
            ))
            print(f"插入题目：{problem['problem_code']} - {problem['title_main']}")
        
        # 提交事务
        conn.commit()
        print(f"\n成功插入 {len(test_problems)} 条测试数据")
        
        # 验证数据
        cursor.execute("SELECT COUNT(*) FROM leetcode_problem_bank")
        count = cursor.fetchone()[0]
        print(f"数据库中现有记录数：{count}")
        
    except mysql.connector.Error as e:
        print(f"数据库错误：{e}")
    except Exception as e:
        print(f"插入失败：{e}")
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals():
            conn.close()
        print("数据库连接已关闭")

if __name__ == "__main__":
    insert_test_data()