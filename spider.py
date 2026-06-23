import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

from pta_spider.spider import *  # noqa: F401,F403

if __name__ == "__main__":
    from pta_spider.spider import main

    main()
