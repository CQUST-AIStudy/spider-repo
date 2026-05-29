import requests, json

# 登录获取token
login = requests.post('http://localhost:8081/api/auth/login', 
    json={'username':'teacher1','password':'password123'},
    headers={'Content-Type':'application/json'})
print(f'Login status: {login.status_code}')
print(f'Login body (first 500): {login.text[:500]}')

if login.status_code == 200:
    try:
        data = login.json()
        token = data['data']['accessToken']
        h = {'Authorization': f'Bearer {token}'}
        
        # 测试单个实验分析 (experiment_id=1)
        r = requests.get('http://localhost:8081/api/analytics/experiments/1', headers=h)
        print(f'\nExperiment 1 status: {r.status_code}')
        result = r.json()
        d = result.get('data', {})
        ov = d.get('overview', {})
        print(f'Overview: totalStudents={ov.get("totalStudents")}, avg={ov.get("avgScore")}, max={ov.get("maxScore")}, min={ov.get("minScore")}')
        print(f'Distribution: {json.dumps(d.get("scoreDistribution",{}), ensure_ascii=False)}')
        acc = d.get('problemAccuracy', [])
        print(f'Problem accuracy: {len(acc)} problems')
        if acc:
            print(f'  First: {json.dumps(acc[0], ensure_ascii=False)}')
    except Exception as e:
        print(f'Error: {e}')
