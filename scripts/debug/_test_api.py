import requests, json

# 登录获取token
login = requests.post('http://localhost:8081/api/auth/login', json={'username':'teacher1','password':'password123'})
token = login.json()['data']['accessToken']
h = {'Authorization': f'Bearer {token}'}

# 测试班级前缀
r = requests.get('http://localhost:8081/api/analytics/class-prefixes', headers=h)
print('=== class-prefixes ===')
print(json.dumps(r.json(), ensure_ascii=False, indent=2))

# 测试实验列表（计科23）
r = requests.get('http://localhost:8081/api/analytics/experiments', params={'classPrefix':'计科23'}, headers=h)
exps = r.json()['data']
print(f'\n=== 计科23 实验: {len(exps)} 个 ===')
for e in exps[:3]:
    print(f"  id={e['experimentId']}, name={e['name']}")

# 测试单个实验分析（取第一个）
if exps:
    eid = exps[0]['experimentId']
    r = requests.get(f'http://localhost:8081/api/analytics/experiments/{eid}', headers=h)
    data = r.json()
    print(f'\n=== 实验 {eid} 分析 ===')
    print(f"  status: {data.get('status')}")
    print(f"  code: {data.get('code')}")
    d = data.get('data', {})
    print(f"  overview: {json.dumps(d.get('overview',{}), ensure_ascii=False)}")
    dist = d.get('scoreDistribution', {})
    print(f"  scoreDistribution: {json.dumps(dist, ensure_ascii=False)}")
    acc = d.get('problemAccuracy', [])
    print(f"  problemAccuracy count: {len(acc)}")
    if acc:
        print(f"  first problem: {json.dumps(acc[0], ensure_ascii=False)}")
