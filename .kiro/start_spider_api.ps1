# PTA 爬虫 FastAPI 服务启动脚本
# 使用 spider conda 环境运行

# 设置终端 UTF-8 编码，确保中文正常显示
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = "utf-8"

Write-Host "Starting PTA Spider API on port 8100..." -ForegroundColor Cyan
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
conda run -n spider --no-banner python "$scriptDir\spider_api.py"
