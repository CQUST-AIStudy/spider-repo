#!/usr/bin/env python3
"""
测试推荐API
"""

import requests
import json

def test_recommendation_api():
    """测试推荐API"""
    base_url = "http://localhost:8081"
    
    print("=== 测试智能推荐API ===\n")
    
    # 测试1: 获取学生推荐
    print("1. 测试获取学生推荐...")
    try:
        url = f"{base_url}/api/student/1/recommendedPractices"
        response = requests.get(url, timeout=10)
        
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"✓ 成功获取推荐")
            print(f"✓ 推荐来源: {data.get('source', 'unknown')}")
            
            recommendations = data.get('data', [])
            print(f"✓ 推荐数量: {len(recommendations)}")
            
            if recommendations:
                print("\n前3个推荐题目:")
                for i, item in enumerate(recommendations[:3]):
                    print(f"  {i+1}. {item.get('title', 'Unknown')}")
                    print(f"     难度: {item.get('difficulty', 'Unknown')}")
                    print(f"     总分: {item.get('score', 'N/A')}")
                    print(f"     理由: {item.get('reason', 'N/A')}")
                    print()
            else:
                print("⚠ 没有返回推荐结果")
        else:
            print(f"✗ API调用失败: {response.status_code}")
            print(f"响应: {response.text}")
            
    except Exception as e:
        print(f"✗ 请求异常: {e}")
    
    # 测试2: 直接测试智能推荐API
    print("\n2. 测试智能推荐同步API...")
    try:
        url = f"{base_url}/api/recommendations/leetcode/sync"
        params = {"studentId": 1, "limit": 5}
        response = requests.get(url, params=params, timeout=10)
        
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"✓ 智能推荐成功")
            print(f"✓ 推荐数量: {data.get('itemCount', 0)}")
            
            items = data.get('items', [])
            if items:
                print("\n智能推荐结果:")
                for i, item in enumerate(items):
                    problem = item.get('problem', {})
                    print(f"  {i+1}. {problem.get('titleMain', 'Unknown')}")
                    print(f"     难度: {problem.get('difficulty', 'Unknown')}")
                    print(f"     总分: {item.get('scoreTotal', 'N/A')}")
                    print(f"     薄弱匹配: {item.get('scoreNeedMatch', 'N/A')}")
                    print(f"     难度匹配: {item.get('scoreDifficultyFit', 'N/A')}")
                    print(f"     理由: {item.get('reasonText', 'N/A')}")
                    print()
        else:
            print(f"✗ 智能推荐失败: {response.status_code}")
            print(f"响应: {response.text}")
            
    except Exception as e:
        print(f"✗ 请求异常: {e}")

if __name__ == "__main__":
    test_recommendation_api()