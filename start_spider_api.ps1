# PTA Spider API background start script (Windows PowerShell)
# - checks if already running
# - writes PID file
# - waits for health check
# - uses the selected Python directly; supports .venv and conda envs

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUNBUFFERED = if ($env:PYTHONUNBUFFERED) { $env:PYTHONUNBUFFERED } else { "1" }
$env:CONDA_NO_PLUGINS = "true"
$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($machinePath -or $userPath) {
  $env:Path = (($machinePath, $userPath) | Where-Object { $_ }) -join ";"
  [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
}
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$workspaceRoot = Split-Path -Parent $scriptDir
$backendLocalEnvScript = Join-Path $workspaceRoot "backend-repo\AI_Ds\local.env.ps1"
if (Test-Path $backendLocalEnvScript) {
  . $backendLocalEnvScript
}
$localEnvScript = Join-Path $repoRoot "local.env.ps1"
if (Test-Path $localEnvScript) {
  . $localEnvScript
}
$spiderLocalEnvScript = Join-Path $scriptDir "local.env.ps1"
if (Test-Path $spiderLocalEnvScript) {
  . $spiderLocalEnvScript
}
$portScript = Join-Path $scriptDir "spider_port.ps1"
. $portScript
Initialize-SpiderPort -ProjectRoot $scriptDir
$runtimeDir = if ($env:PTA_RUNTIME_DIR) {
  $env:PTA_RUNTIME_DIR
} else {
  Join-Path $scriptDir "runtime"
}
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$env:PTA_RUNTIME_DIR = $runtimeDir
$env:PTA_CRAWL_DIR = if ($env:PTA_CRAWL_DIR) { $env:PTA_CRAWL_DIR } else { (Join-Path $scriptDir "output") }
$env:JAVA_BACKEND_URL = if ($env:JAVA_BACKEND_URL) { $env:JAVA_BACKEND_URL } else { "http://127.0.0.1:8081" }
$env:PTA_HEADLESS = if ($env:PTA_HEADLESS) { $env:PTA_HEADLESS } else { "false" }
$env:PTA_KEEP_BROWSER_OPEN_ON_FAILURE = if ($env:PTA_KEEP_BROWSER_OPEN_ON_FAILURE) { $env:PTA_KEEP_BROWSER_OPEN_ON_FAILURE } else { "true" }
$env:PTA_SELENIUM_FORM_WAIT_SECONDS = if ($env:PTA_SELENIUM_FORM_WAIT_SECONDS) { $env:PTA_SELENIUM_FORM_WAIT_SECONDS } else { "30" }
$env:PTA_SELENIUM_AUTH_WAIT_SECONDS = if ($env:PTA_SELENIUM_AUTH_WAIT_SECONDS) { $env:PTA_SELENIUM_AUTH_WAIT_SECONDS } else { "60" }
$env:PTA_BROWSER_HOME = if ($env:PTA_BROWSER_HOME) { $env:PTA_BROWSER_HOME } else { (Join-Path $runtimeDir "browser") }
$env:SE_CACHE_PATH = if ($env:SE_CACHE_PATH) { $env:SE_CACHE_PATH } else { (Join-Path $runtimeDir ".selenium") }
$env:ACADEMIC_UNIFIED_IMPORT_ENABLED = if ($env:ACADEMIC_UNIFIED_IMPORT_ENABLED) { $env:ACADEMIC_UNIFIED_IMPORT_ENABLED } else { "true" }
$env:ACADEMIC_LEGACY_WRITE_ENABLED = if ($env:ACADEMIC_LEGACY_WRITE_ENABLED) { $env:ACADEMIC_LEGACY_WRITE_ENABLED } else { "true" }
if (-not $env:DB_USER -and $env:DB_USERNAME) {
  $env:DB_USER = $env:DB_USERNAME
}

$appFile = Join-Path $scriptDir "spider_api.py"
$pidFile = Join-Path $runtimeDir "spider_api.pid"
$outLog = Join-Path $runtimeDir "spider_api.out.log"
$errLog = Join-Path $runtimeDir "spider_api.err.log"
$healthUrl = "http://127.0.0.1:$env:SPIDER_PORT/health"

function Resolve-SpiderPython {
  $candidates = @()

  if ($env:PTA_SPIDER_PYTHON) {
    $candidates += $env:PTA_SPIDER_PYTHON
  }

  $condaRoots = @(
    "D:\Anaconda",
    "$env:USERPROFILE\anaconda3",
    "$env:USERPROFILE\miniconda3",
    "D:\Miniconda",
    "F:\downloads\miniconda"
  )

  if ($env:CONDA_EXE) {
    $condaBase = Split-Path (Split-Path $env:CONDA_EXE -Parent) -Parent
    $condaRoots = @($condaBase) + $condaRoots
  }

  if ($env:PTA_CONDA_ENV) {
    foreach ($root in ($condaRoots | Select-Object -Unique)) {
      if ($root -and (Test-Path $root)) {
        $candidates += (Join-Path $root "envs\$env:PTA_CONDA_ENV\python.exe")
      }
    }
  }

  $candidates += @(
    (Join-Path $scriptDir ".venv\Scripts\python.exe"),
    (Join-Path $scriptDir "venv\Scripts\python.exe")
  )

  if ($env:CONDA_PREFIX -and (Split-Path $env:CONDA_PREFIX -Leaf) -eq "spider") {
    $candidates += (Join-Path $env:CONDA_PREFIX "python.exe")
  }

  if ($env:CONDA_EXE) {
    $condaBase = Split-Path (Split-Path $env:CONDA_EXE -Parent) -Parent
    $candidates += (Join-Path $condaBase "envs\spider\python.exe")
  }

  $preferredCondaEnvs = @("pta_spider_py310", "spider", "dl310", "opencv4", "jujube310")
  foreach ($root in ($condaRoots | Select-Object -Unique)) {
    foreach ($envName in $preferredCondaEnvs) {
      if ($root -and (Test-Path $root)) {
        $candidates += (Join-Path $root "envs\$envName\python.exe")
      }
    }
  }

  foreach ($p in ($candidates | Select-Object -Unique)) {
    if ($p -and (Test-Path $p)) { return $p }
  }

  $pathPython = Get-Command python -ErrorAction SilentlyContinue
  if ($pathPython) { return $pathPython.Source }

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
  Write-Host "python not found. Create .venv or install Python first." -ForegroundColor Red
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

Write-Host "Starting PTA Spider API on port $env:SPIDER_PORT..." -ForegroundColor Cyan
$proc = Start-Process `
  -FilePath $pythonExe `
  -ArgumentList @("`"$appFile`"") `
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
  Write-Host "PTA runtime: $runtimeDir" -ForegroundColor Gray
  Write-Host "PTA browser home: $env:PTA_BROWSER_HOME" -ForegroundColor Gray
  Write-Host "PTA headless: $env:PTA_HEADLESS" -ForegroundColor Gray
  exit 0
}

Write-Host "Start failed: health check did not pass. Check logs:" -ForegroundColor Red
Write-Host "  $outLog" -ForegroundColor Red
Write-Host "  $errLog" -ForegroundColor Red
exit 1
