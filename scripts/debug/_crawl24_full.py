"""爬取计科24数据结构的所有新题目集（带403重试）"""
import sys, os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "src")))

if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except: pass

from pta_spider.spider import PTAClient

client = PTAClient()
client.crawl_incremental("计科24数据结构")
print("\n===== 计科24爬取完成 =====")
