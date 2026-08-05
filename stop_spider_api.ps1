# PTA Spider API stop script (Windows PowerShell)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$localEnvScript = Join-Path $scriptDir "local.env.ps1"
if (Test-Path $localEnvScript) {
  . $localEnvScript
}
$portScript = Join-Path $scriptDir "spider_port.ps1"
. $portScript
Initialize-SpiderPort -ProjectRoot $scriptDir
$spiderPort = $env:SPIDER_PORT
$runtimeDir = if ($env:PTA_RUNTIME_DIR) {
  $env:PTA_RUNTIME_DIR
} else {
  Join-Path $scriptDir "runtime"
}
$pidFile = Join-Path $runtimeDir "spider_api.pid"

$stopped = $false

if (Test-Path $pidFile) {
  try {
    $spiderPid = [int](Get-Content $pidFile -Raw).Trim()
    $proc = Get-Process -Id $spiderPid -ErrorAction SilentlyContinue
    if ($null -ne $proc) {
      Stop-Process -Id $spiderPid -Force
      Write-Host "Stopped Spider API process (PID=$spiderPid)" -ForegroundColor Green
      $stopped = $true
    }
  } catch {
    # ignore parse/process errors
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if (-not $stopped) {
  $portLines = netstat -ano -p tcp | Select-String "LISTENING" | Select-String ":$spiderPort\s"
  if ($portLines) {
    $pids = @()
    foreach ($line in $portLines) {
      $parts = ($line.ToString() -split "\s+") | Where-Object { $_ -ne "" }
      if ($parts.Length -ge 5) {
        $pids += $parts[-1]
      }
    }
    $pids = $pids | Select-Object -Unique
    foreach ($p in $pids) {
      if ($p -match "^\d+$") {
        Stop-Process -Id ([int]$p) -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped process by port (PID=$p)" -ForegroundColor Yellow
        $stopped = $true
      }
    }
  }
}

if (-not $stopped) {
  Write-Host "Spider API is not running" -ForegroundColor Yellow
}
