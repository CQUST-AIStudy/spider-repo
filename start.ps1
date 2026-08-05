[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUNBUFFERED = if ($env:PYTHONUNBUFFERED) { $env:PYTHONUNBUFFERED } else { "1" }

Set-Location $PSScriptRoot

$localEnv = Join-Path $PSScriptRoot "local.env.ps1"
if (Test-Path $localEnv) {
  . $localEnv
}
$portScript = Join-Path $PSScriptRoot "spider_port.ps1"
. $portScript
Initialize-SpiderPort -ProjectRoot $PSScriptRoot

$runtimeDir = if ($env:PTA_RUNTIME_DIR) {
  $env:PTA_RUNTIME_DIR
} else {
  Join-Path $PSScriptRoot "runtime"
}
$crawlDir = if ($env:PTA_CRAWL_DIR) {
  $env:PTA_CRAWL_DIR
} else {
  Join-Path $PSScriptRoot "output"
}

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
New-Item -ItemType Directory -Force -Path $crawlDir | Out-Null

$env:PTA_RUNTIME_DIR = $runtimeDir
$env:PTA_CRAWL_DIR = $crawlDir
$env:JAVA_BACKEND_URL = if ($env:JAVA_BACKEND_URL) { $env:JAVA_BACKEND_URL } else { "http://127.0.0.1:8081" }
$env:PTA_HEADLESS = if ($env:PTA_HEADLESS) { $env:PTA_HEADLESS } else { "false" }
$env:PTA_BROWSER_HOME = if ($env:PTA_BROWSER_HOME) { $env:PTA_BROWSER_HOME } else { (Join-Path $runtimeDir "browser") }
$env:SE_CACHE_PATH = if ($env:SE_CACHE_PATH) { $env:SE_CACHE_PATH } else { (Join-Path $runtimeDir ".selenium") }

if (-not $env:DB_USER -and $env:DB_USERNAME) {
  $env:DB_USER = $env:DB_USERNAME
}

$appFile = Join-Path $PSScriptRoot "spider_api.py"
if (-not (Test-Path $appFile)) {
  Write-Host "spider_api.py not found." -ForegroundColor Red
  exit 1
}

$pythonExe = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $pythonExe)) {
  $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
  if (-not $pythonCommand) {
    Write-Host "python not found. Create .venv or install Python first." -ForegroundColor Red
    exit 1
  }
  $pythonExe = $pythonCommand.Source
}

Write-Host "Starting PTA Spider API on http://127.0.0.1:$env:SPIDER_PORT ..." -ForegroundColor Cyan
& $pythonExe $appFile
exit $LASTEXITCODE
