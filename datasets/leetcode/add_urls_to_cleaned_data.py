#!/usr/bin/env python3
"""
为清洗后的LeetCode数据添加URL字段
从题目标题中提取题号，生成对应的LeetCode URL
"""

import json
import re
from pathlib import Path

def extract_problem_info(input_text):
    """从题目输入中提取题号和标题"""
    # 匹配模式：题目：LCR 002. 二进制求和 - 二进制加法
    pattern = r'题目：(LCR \d+)\.\s*([^-\n]+)(?:\s*-\s*([^\n]+))?'
    match = re.search(pattern, input_text)
    
    if match:
        problem_code = match.group(1).strip()  # LCR 002
        title_main = match.group(2).strip()    # 二进制求和
        title_alt = match.group(3).strip() if match.group(3) else None  # 二进制加法
        
        # 提取数字部分用于生成URL
        number_match = re.search(r'LCR (\d+)', problem_code)
        if number_match:
            problem_number = number_match.group(1)
            # 生成LeetCode CN URL
            url = f"https://leetcode.cn/problems/lcr-{problem_number.zfill(3)}/"
            return {
                'problem_code': problem_code,
                'title_main': title_main,
                'title_alt': title_alt,
                'url': url,
                'problem_number': problem_number
            }
    
    # 尝试匹配其他格式，如面试题
    interview_pattern = r'题目：(面试题 [\d.]+)\.\s*([^-\n]+)(?:\s*-\s*([^\n]+))?'
    match = re.search(interview_pattern, input_text)
    if match:
        problem_code = match.group(1).strip()
        title_main = match.group(2).strip()
        title_alt = match.group(3).strip() if match.group(3) else None
        
        # 面试题的URL格式不同，这里先用通用格式
        url = f"https://leetcode.cn/problems/{title_main.lower().replace(' ', '-')}/"
        return {
            'problem_code': problem_code,
            'title_main': title_main,
            'title_alt': title_alt,
            'url': url,
            'problem_number': None
        }
    
    return None

def add_urls_to_cleaned_data():
    """为清洗后的数据添加URL字段"""
    input_file = Path("datasets/leetcode/solutions_cleaned.json")
    output_file = Path("datasets/leetcode/solutions_with_urls.json")
    
    if not input_file.exists():
        print(f"错误：找不到输入文件 {input_file}")
        return
    
    print(f"读取清洗后的数据：{input_file}")
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"原始数据条数：{len(data)}")
    
    updated_count = 0
    for item in data:
        if 'input' in item:
            problem_info = extract_problem_info(item['input'])
            if problem_info:
                # 添加URL和其他信息
                item['url'] = problem_info['url']
                item['problem_code'] = problem_info['problem_code']
                item['title'] = problem_info['title_main']
                item['title_alt'] = problem_info['title_alt']
                item['problem_number'] = problem_info['problem_number']
                item['difficulty'] = 'Medium'  # 默认难度，后续可以根据题目内容分析
                updated_count += 1
                
                print(f"✓ {problem_info['problem_code']}: {problem_info['title_main']} -> {problem_info['url']}")
    
    print(f"\n成功为 {updated_count} 条记录添加URL")
    
    # 保存更新后的数据
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    
    print(f"更新后的数据已保存到：{output_file}")
    
    # 生成统计报告
    report = {
        "total_records": len(data),
        "updated_records": updated_count,
        "success_rate": f"{updated_count/len(data)*100:.1f}%",
        "sample_urls": [item.get('url') for item in data[:5] if item.get('url')]
    }
    
    report_file = Path("datasets/leetcode/url_addition_report.json")
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    
    print(f"统计报告已保存到：{report_file}")

if __name__ == "__main__":
    add_urls_to_cleaned_data()