# PTA Spider API status script (Windows PowerShell)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pidFile = Join-Path $scriptDir "spider_api.pid"
$healthUrl = "http://127.0.0.1:8100/health"

$pidText = ""
$procAlive = $false

if (Test-Path $pidFile) {
  try {
    $pidText = (Get-Content $pidFile -Raw).Trim()
    if ($pidText -match "^\d+$") {
      $proc = Get-Process -Id ([int]$pidText) -ErrorAction SilentlyContinue
      $procAlive = $null -ne $proc
    }
  } catch {
    # ignore
  }
}

$healthOk = $false
$healthRaw = ""
try {
  $resp = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
  $healthRaw = ($resp | ConvertTo-Json -Compress)
  if ($resp.status -eq "ok") { $healthOk = $true }
} catch {
  $healthRaw = $_.Exception.Message
}

Write-Host "PID file: $pidFile" -ForegroundColor Gray
Write-Host "PID value: $pidText" -ForegroundColor Gray
Write-Host "Process alive: $procAlive" -ForegroundColor Gray
Write-Host "Health OK: $healthOk" -ForegroundColor Gray
Write-Host "Health detail: $healthRaw" -ForegroundColor Gray
