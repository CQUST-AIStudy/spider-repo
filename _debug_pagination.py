"""诊断: 尝试不同的 API 参数获取更多提交记录"""
import sys
import os
import json
from pathlib import Path

if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

sys.path.insert(0, str(Path(__file__).resolve().parent))
from spider import PTAClient

client = PTAClient()
if not client.ensure_login():
    print("login failed")
    sys.exit(1)

keyword = os.getenv("experiment_name", "计科23数据结构")
all_sets = client.search_problem_sets(keyword)

# Pick a problem set that returned exactly 200 (likely has more)
ps = all_sets[1]  # 第6次实验(二叉树的遍历)
ps_id = ps["id"]
ps_name = ps["name"]
print(f"\n=== {ps_name} (id={ps_id}) ===")

# Test 1: Try different limit values
for limit in [50, 100, 200, 500, 1000]:
    data = client.get_submissions(ps_id, page=0, limit=limit)
    subs = data.get("submissions", [])
    total = data.get("total", "?")
    print(f"  limit={limit}: got {len(subs)}, total={total}")
    import time; time.sleep(1)

# Test 2: Try with filter/sort params
print("\n--- Testing with extra params ---")
import requests as req

# Try the examinees endpoint to get student list
print("\n--- Examinees ---")
try:
    data = client.api_get(f"/problem-sets/{ps_id}/examinees")
    examinees = data.get("examinees", [])
    print(f"  Examinees count: {len(examinees)}")
    if examinees:
        print(f"  Sample: {json.dumps(examinees[0], ensure_ascii=False)[:200]}")
except Exception as e:
    print(f"  Error: {e}")

# Test 3: Try per-student submissions
print("\n--- Per-student submissions ---")
if examinees:
    uid = examinees[0].get("userId", "")
    print(f"  Testing userId={uid}")
    try:
        data = client.api_get(f"/problem-sets/{ps_id}/submissions", params={
            "page": 0, "limit": 200,
            "filter": json.dumps({"userId": uid})
        })
        subs = data.get("submissions", [])
        print(f"  With userId filter: got {len(subs)}")
    except Exception as e:
        print(f"  Error: {e}")

# Test 4: Try the exam-submissions endpoint
print("\n--- exam-submissions endpoint ---")
try:
    data = client.api_get(f"/problem-sets/{ps_id}/exam-submissions", params={
        "page": 0, "limit": 200,
    })
    subs = data.get("submissions", data.get("examSubmissions", []))
    print(f"  exam-submissions: got {len(subs)}")
except Exception as e:
    print(f"  Error: {e}")

# Test 5: Try last-submissions (per-problem final submissions)
print("\n--- last-submissions endpoint ---")
try:
    data = client.api_get(f"/problem-sets/{ps_id}/last-submissions", params={
        "page": 0, "limit": 500,
    })
    print(f"  last-submissions keys: {list(data.keys())[:10]}")
    for k, v in data.items():
        if isinstance(v, list):
            print(f"    {k}: {len(v)} items")
        elif isinstance(v, dict):
            print(f"    {k}: dict with {len(v)} keys")
except Exception as e:
    print(f"  Error: {e}")
