# Start all local services from `g:\myapps`.
# Usage:
#   powershell -ExecutionPolicy Bypass -File start-all.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting local teaching platform stack" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$localEnvScript = Join-Path $PSScriptRoot "local.env.ps1"
if (Test-Path $localEnvScript) {
    . $localEnvScript
    Write-Host "  Loaded local environment from local.env.ps1" -ForegroundColor DarkGray
} else {
    Write-Host "  local.env.ps1 not found; using current shell environment" -ForegroundColor DarkGray
}

Write-Host "`n[1/3] Starting unified AI_Ds backend on :8081..." -ForegroundColor Yellow
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "G:\myapps\scripts\run_backend_dev.ps1" -WindowStyle Normal

Start-Sleep -Seconds 3

if ($env:START_LEGACY_TAP_BACKEND -eq "true") {
    Write-Host "[legacy] Starting standalone tap-backend on :8080..." -ForegroundColor DarkYellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\teacher-assistant-platform && mvn -q -pl :tap-common install && mvn -q -pl :tap-backend spring-boot:run" -WindowStyle Normal
    Start-Sleep -Seconds 3
} else {
    Write-Host "[legacy] Skipping standalone tap-backend; frontend uses AI_Ds :8081" -ForegroundColor DarkGray
}

Write-Host "[2/3] Starting Vue frontend..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "G:\myapps\scripts\run_frontend_dev.cmd" -WindowStyle Normal

Write-Host "[3/3] Starting grading worker (consumer + celery worker)..." -ForegroundColor Yellow
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "G:\myapps\grading_worker\start_worker.ps1" -WindowStyle Normal

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  All services have been started." -ForegroundColor Green
Write-Host "  AI_Ds backend: http://localhost:8081" -ForegroundColor White
if ($env:START_LEGACY_TAP_BACKEND -eq "true") {
    Write-Host "  legacy tap-backend: http://localhost:8080" -ForegroundColor White
}
Write-Host "  Vue frontend:  http://localhost:8082" -ForegroundColor White
Write-Host "  Grading worker logs: g:\myapps\logs\grading_worker_daemon.err.log" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
