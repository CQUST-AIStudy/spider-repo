#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LeetCode推荐系统测试脚本
测试新实现的推荐API接口
"""

import requests
import json
import time

# API基础URL
BASE_URL = "http://localhost:8081"

def test_sync_data():
    """测试数据同步接口"""
    print("=== 测试数据同步接口 ===")
    
    url = f"{BASE_URL}/api/recommendations/leetcode/admin/sync"
    
    try:
        response = requests.post(url, timeout=60)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"同步结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"同步失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def test_get_stats():
    """测试获取统计信息接口"""
    print("\n=== 测试获取统计信息接口 ===")
    
    url = f"{BASE_URL}/api/recommendations/leetcode/admin/stats"
    
    try:
        response = requests.get(url)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"统计信息: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"获取统计信息失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def test_generate_recommendation():
    """测试生成推荐接口"""
    print("\n=== 测试生成推荐接口 ===")
    
    url = f"{BASE_URL}/api/recommendations/leetcode/generate"
    params = {
        'studentId': 1,
        'limit': 10,
        'scene': 'test'
    }
    
    try:
        response = requests.post(url, params=params)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"生成推荐结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
            return data.get('requestId')
        else:
            print(f"生成推荐失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")
    
    return None

def test_get_recommendation_result(request_id):
    """测试查询推荐结果接口"""
    print(f"\n=== 测试查询推荐结果接口 (requestId: {request_id}) ===")
    
    if not request_id:
        print("没有有效的requestId，跳过测试")
        return
    
    url = f"{BASE_URL}/api/recommendations/leetcode/result/{request_id}"
    
    try:
        response = requests.get(url)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"推荐结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"查询推荐结果失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def test_sync_recommendation():
    """测试同步推荐接口"""
    print("\n=== 测试同步推荐接口 ===")
    
    url = f"{BASE_URL}/api/recommendations/leetcode/sync"
    params = {
        'studentId': 1,
        'limit': 5
    }
    
    try:
        response = requests.get(url, params=params)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"同步推荐结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"同步推荐失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def test_legacy_api():
    """测试兼容的旧API接口"""
    print("\n=== 测试兼容的旧API接口 ===")
    
    url = f"{BASE_URL}/api/student/1/recommendedPractices"
    
    try:
        response = requests.get(url)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"旧API推荐结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"旧API调用失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def test_record_feedback():
    """测试记录反馈接口"""
    print("\n=== 测试记录反馈接口 ===")
    
    url = f"{BASE_URL}/api/recommendations/leetcode/feedback"
    params = {
        'requestId': 'test-request-id',
        'studentId': 1,
        'problemId': 1,
        'action': 'click',
        'sessionId': 'test-session'
    }
    
    try:
        response = requests.post(url, params=params)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"反馈记录结果: {json.dumps(data, indent=2, ensure_ascii=False)}")
        else:
            print(f"记录反馈失败: {response.text}")
            
    except Exception as e:
        print(f"请求失败: {e}")

def main():
    """主测试函数"""
    print("开始测试LeetCode推荐系统API接口")
    print("=" * 50)
    
    # 1. 测试数据同步（如果需要）
    # 注意：首次运行时取消注释下面这行
    # test_sync_data()
    
    # 2. 测试获取统计信息
    test_get_stats()
    
    # 3. 测试同步推荐接口
    test_sync_recommendation()
    
    # 4. 测试生成推荐接口
    request_id = test_generate_recommendation()
    
    # 5. 等待一下再查询结果
    if request_id:
        time.sleep(1)
        test_get_recommendation_result(request_id)
    
    # 6. 测试兼容的旧API接口
    test_legacy_api()
    
    # 7. 测试记录反馈接口
    test_record_feedback()
    
    print("\n" + "=" * 50)
    print("测试完成")

if __name__ == "__main__":
    main()