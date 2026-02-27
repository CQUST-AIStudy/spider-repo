"""
PTA 爬虫 FastAPI 服务 v2
- 细粒度数据类型感知爬取：题目内容(一次) / 提交记录(高频) / 导出数据(定期)
- 全局任务队列，同一时间只允许 1 个爬取任务运行
- 队列最大容量 5，相同 keyword+mode 去重
- Java 后端通过 HTTP 调用触发爬取
- 任务完成后回调 Java 后端更新同步状态
"""
import asyncio
import uuid
import os
import sys
import json as json_mod
from pathlib import Path
from datetime import datetime
from enum import Enum

# Windows 终端 UTF-8 输出修复
if sys.stdout and hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Ensure spider.py in same directory is importable
sys.path.insert(0, str(Path(__file__).resolve().parent))
from spider import PTAClient, CrawlHistory
from sync_to_db import sync_all

app = FastAPI(title="PTA Spider API", version="2.0.0")

# Java 后端地址，用于回调更新同步状态
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8081")
