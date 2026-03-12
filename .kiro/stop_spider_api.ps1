# PTA Spider API stop script (Windows PowerShell)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFile = Join-Path $scriptDir "spider_api.pid"

$stopped = $false

if (Test-Path $pidFile) {
  try {
    $pid = [int](Get-Content $pidFile -Raw).Trim()
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if ($null -ne $proc) {
      Stop-Process -Id $pid -Force
      Write-Host "Stopped Spider API process (PID=$pid)" -ForegroundColor Green
      $stopped = $true
    }
  } catch {
    # ignore parse/process errors
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if (-not $stopped) {
  $portLines = netstat -ano | Select-String ":8100"
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
