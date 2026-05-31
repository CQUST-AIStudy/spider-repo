# PTA 爬虫启动说明

项目目录：

```cmd
D:\AI-study\pta_spider
```

爬虫是 FastAPI 服务，默认端口：

```text
http://127.0.0.1:8100
```

健康检查地址：

```text
http://127.0.0.1:8100/health
```

## 依赖环境

需要安装：

```text
Python 3.10+
Chrome 或 Chromium
```

## 首次安装依赖

```cmd
cd /d D:\AI-study\pta_spider
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

当前启动脚本会优先使用：

```text
D:\AI-study\pta_spider\.venv\Scripts\python.exe
```

## 启动命令

```cmd
cd /d D:\AI-study\pta_spider
powershell -NoProfile -ExecutionPolicy Bypass -File .\start_spider_api.ps1
```

## 查看状态

```cmd
cd /d D:\AI-study\pta_spider
powershell -NoProfile -ExecutionPolicy Bypass -File .\status_spider_api.ps1
```

正常结果应包含：

```text
Health OK: True
```

也可以浏览器打开：

```text
http://127.0.0.1:8100/health
```

正常返回：

```json
{"status":"ok","pending_tasks":0}
```

## 停止命令

```cmd
cd /d D:\AI-study\pta_spider
powershell -NoProfile -ExecutionPolicy Bypass -File .\stop_spider_api.ps1
```

## 后端自动调用爬虫

后端的 `local.env.ps1` 已配置爬虫脚本路径：

```powershell
$env:PTA_SPIDER_START_SCRIPT = "D:\AI-study\pta_spider\start_spider_api.ps1"
$env:PTA_SPIDER_STOP_SCRIPT = "D:\AI-study\pta_spider\stop_spider_api.ps1"
```

后端默认调用爬虫地址：

```text
http://127.0.0.1:8100
```

## 常见问题

### 提示 Python 找不到

重新创建 `.venv` 并安装依赖：

```cmd
cd /d D:\AI-study\pta_spider
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

### 启动后健康检查失败

查看日志：

```text
D:\AI-study\pta_spider\runtime\spider_api.out.log
D:\AI-study\pta_spider\runtime\spider_api.err.log
```

