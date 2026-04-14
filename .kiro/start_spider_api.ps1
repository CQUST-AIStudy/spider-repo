# PTA Spider API background start script (Windows PowerShell)
# - checks if already running
# - writes PID file
# - waits for health check
# - uses spider env python directly (no conda run)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = "utf-8"
$env:CONDA_NO_PLUGINS = "true"
$repoRoot = Split-Path -Parent $PSScriptRoot
$localEnvScript = Join-Path $repoRoot "local.env.ps1"
if (Test-Path $localEnvScript) {
  . $localEnvScript
}
$env:JAVA_BACKEND_URL = if ($env:JAVA_BACKEND_URL) { $env:JAVA_BACKEND_URL } else { "http://127.0.0.1:8081" }
$env:PTA_HEADLESS = if ($env:PTA_HEADLESS) { $env:PTA_HEADLESS } else { "false" }
$env:PTA_BROWSER_HOME = if ($env:PTA_BROWSER_HOME) { $env:PTA_BROWSER_HOME } else { (Join-Path $PSScriptRoot "browser") }
$env:SE_CACHE_PATH = if ($env:SE_CACHE_PATH) { $env:SE_CACHE_PATH } else { (Join-Path $PSScriptRoot ".selenium") }
$env:ACADEMIC_UNIFIED_IMPORT_ENABLED = if ($env:ACADEMIC_UNIFIED_IMPORT_ENABLED) { $env:ACADEMIC_UNIFIED_IMPORT_ENABLED } else { "true" }
$env:ACADEMIC_LEGACY_WRITE_ENABLED = if ($env:ACADEMIC_LEGACY_WRITE_ENABLED) { $env:ACADEMIC_LEGACY_WRITE_ENABLED } else { "true" }

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appFile = Join-Path $scriptDir "spider_api.py"
$pidFile = Join-Path $scriptDir "spider_api.pid"
$outLog = Join-Path $scriptDir "spider_api.out.log"
$errLog = Join-Path $scriptDir "spider_api.err.log"
$healthUrl = "http://127.0.0.1:8100/health"

function Resolve-SpiderPython {
  $candidates = @()

  if ($env:CONDA_PREFIX -and (Split-Path $env:CONDA_PREFIX -Leaf) -eq "spider") {
    $candidates += (Join-Path $env:CONDA_PREFIX "python.exe")
  }

  if ($env:CONDA_EXE) {
    $condaBase = Split-Path (Split-Path $env:CONDA_EXE -Parent) -Parent
    $candidates += (Join-Path $condaBase "envs\spider\python.exe")
  }

  $candidates += @(
    "$env:USERPROFILE\miniconda3\envs\spider\python.exe",
    "$env:USERPROFILE\anaconda3\envs\spider\python.exe",
    "F:\downloads\miniconda\envs\spider\python.exe"
  )

  foreach ($p in ($candidates | Select-Object -Unique)) {
    if ($p -and (Test-Path $p)) { return $p }
  }

  return $null
}

function Test-SpiderHealth {
  try {
    $resp = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
    return ($null -ne $resp -and $resp.status -eq "ok")
  } catch {
    return $false
  }
}

if (-not (Test-Path $appFile)) {
  Write-Host "spider_api.py not found: $appFile" -ForegroundColor Red
  exit 1
}

$pythonExe = Resolve-SpiderPython
if (-not $pythonExe) {
  Write-Host "spider env python not found (expected: <conda_base>\\envs\\spider\\python.exe)" -ForegroundColor Red
  exit 1
}

if (Test-SpiderHealth) {
  Write-Host "PTA Spider API is already running: $healthUrl" -ForegroundColor Green
  exit 0
}

if (Test-Path $pidFile) {
  try {
    $oldPid = [int](Get-Content $pidFile -Raw).Trim()
    $oldProc = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
    if ($null -ne $oldProc) {
      Write-Host "Found stale process (PID=$oldPid), stopping it..." -ForegroundColor Yellow
      Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
      Start-Sleep -Seconds 1
    }
  } catch {
    # ignore stale pid file parse issues
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

Write-Host "Starting PTA Spider API on port 8100..." -ForegroundColor Cyan
$proc = Start-Process `
  -FilePath $pythonExe `
  -ArgumentList @($appFile) `
  -RedirectStandardOutput $outLog `
  -RedirectStandardError $errLog `
  -WindowStyle Hidden `
  -PassThru

$proc.Id | Out-File -FilePath $pidFile -Encoding ascii -Force

$ok = $false
for ($i = 0; $i -lt 20; $i++) {
  Start-Sleep -Seconds 1
  if (Test-SpiderHealth) {
    $ok = $true
    break
  }
}

if ($ok) {
  Write-Host "Started: $healthUrl (PID=$($proc.Id))" -ForegroundColor Green
  Write-Host "Python: $pythonExe" -ForegroundColor Gray
  Write-Host "Log: $outLog" -ForegroundColor Gray
  Write-Host "PTA browser home: $env:PTA_BROWSER_HOME" -ForegroundColor Gray
  Write-Host "PTA headless: $env:PTA_HEADLESS" -ForegroundColor Gray
  exit 0
}

Write-Host "Start failed: health check did not pass. Check logs:" -ForegroundColor Red
Write-Host "  $outLog" -ForegroundColor Red
Write-Host "  $errLog" -ForegroundColor Red
exit 1
