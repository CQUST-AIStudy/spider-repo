# PTA 爬虫服务

这是 PTA 数据爬取与同步服务，可以作为独立的 FastAPI 服务运行，供 Java 后端调用，也可以直接运行脚本进行爬取和同步。

## 目录说明

```text
.
├─ spider.py                  # PTA 爬虫核心逻辑
├─ spider_api.py              # FastAPI 接口服务
├─ capture_pta_cookie.py      # 手动获取/更新 PTA cookie
├─ sync_to_db.py              # 旧版数据库同步脚本
├─ sync_to_unified_db.py      # 统一数据库同步脚本
├─ start_spider_api.ps1       # Windows 启动脚本
├─ status_spider_api.ps1      # 查看服务状态
├─ stop_spider_api.ps1        # 停止服务
├─ output/                    # 爬取结果，Git 不上传
└─ runtime/                   # cookie、日志、PID、浏览器缓存，Git 不上传
```

默认情况下：

```text
爬取结果输出到：output/
运行状态保存到：runtime/
```

如果需要自定义路径，可以设置环境变量：

```powershell
$env:PTA_CRAWL_DIR = "D:\path\to\output"
$env:PTA_RUNTIME_DIR = "D:\path\to\runtime"
```

## 环境准备

建议使用 Python 虚拟环境：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

还需要本机安装 Chrome 或 Chromium，因为 PTA 登录需要 Selenium 启动浏览器。

## 配置文件

复制一份环境变量模板：

```powershell
Copy-Item .env.example .env
```

然后填写 `.env` 中的 PTA 账号、数据库连接等配置。

常用配置：

```text
PTA_USERNAME=PTA账号
PTA_PASSWORD=PTA密码
JAVA_BACKEND_URL=http://127.0.0.1:8081
SPIDER_PORT=8100
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=ptadatabase
DB_USERNAME=root
DB_PASSWORD=数据库密码
```

注意：`.env` 里可能包含账号密码，不要上传到 GitHub。

## 启动服务

Windows 下推荐使用：

```powershell
.\start_spider_api.ps1
```

也可以直接运行：

```powershell
python spider_api.py
```

默认服务地址：

```text
http://127.0.0.1:8100
```

后端项目需要把 `PTA_SPIDER_URL` 指向这个地址，例如：

```text
PTA_SPIDER_URL=http://127.0.0.1:8100
```

## 查看和停止服务

查看状态：

```powershell
.\status_spider_api.ps1
```

停止服务：

```powershell
.\stop_spider_api.ps1
```

## Git 上传注意事项

需要上传：

```text
*.py
*.ps1
README.md
requirements.txt
.env.example
.gitignore
tools/
```

不要上传：

```text
runtime/
output/
.env
__pycache__/
.venv/
```

这些已经写进 `.gitignore`。
