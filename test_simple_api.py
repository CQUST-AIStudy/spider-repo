#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
简单的API测试脚本
"""

import urllib.request
import json

def test_api():
    """测试推荐API"""
    url = "http://localhost:8081/api/student/1/recommendedPractices"
    
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            data = response.read().decode('utf-8')
            result = json.loads(data)
            print("API响应:")
            print(json.dumps(result, indent=2, ensure_ascii=False))
            
            if result.get('success'):
                practices = result.get('data', [])
                print(f"\n推荐练习数量: {len(practices)}")
                
                if practices:
                    print("\n前3个推荐:")
                    for i, practice in enumerate(practices[:3]):
                        print(f"{i+1}. {practice}")
                        
    except Exception as e:
        print(f"测试失败: {e}")

if __name__ == "__main__":
    test_api()