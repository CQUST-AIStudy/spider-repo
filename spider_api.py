import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

from pta_spider.spider_api import app

if __name__ == "__main__":
    from pta_spider.spider_api import main

    main()
