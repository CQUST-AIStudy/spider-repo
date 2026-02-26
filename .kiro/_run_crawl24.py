"""临时脚本: 爬取计科24数据结构"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))

if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except: pass

from spider import PTAClient

client = PTAClient()
if not client.ensure_login():
    print("登录失败")
    sys.exit(1)

# 先搜索看看有多少题目集
sets = client.search_problem_sets("计科24数据结构")
print(f"\n搜索到 {len(sets)} 个题目集:")
for s in sets:
    ps_id = s.get("id", "")
    name = s.get("name", "?")
    crawled = "已爬" if client.history.is_crawled(ps_id) else "新"
    print(f"  [{crawled}] {name} (id={ps_id})")
