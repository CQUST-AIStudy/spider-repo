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

Write-Host "`n[1/2] Starting unified AI_Ds backend on :8081..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\AI_Ds && mvnw.cmd spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 3

if ($env:START_LEGACY_TAP_BACKEND -eq "true") {
    Write-Host "[legacy] Starting standalone tap-backend on :8080..." -ForegroundColor DarkYellow
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\teacher-assistant-platform && mvn -q -pl :tap-common install && mvn -q -pl :tap-backend spring-boot:run" -WindowStyle Normal
    Start-Sleep -Seconds 3
} else {
    Write-Host "[legacy] Skipping standalone tap-backend; frontend uses AI_Ds :8081" -ForegroundColor DarkGray
}

Write-Host "[2/2] Starting Vue frontend..." -ForegroundColor Yellow
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d G:\myapps\AI_Ds-vue && npm run serve" -WindowStyle Normal

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  All services have been started." -ForegroundColor Green
Write-Host "  AI_Ds backend: http://localhost:8081" -ForegroundColor White
if ($env:START_LEGACY_TAP_BACKEND -eq "true") {
    Write-Host "  legacy tap-backend: http://localhost:8080" -ForegroundColor White
}
Write-Host "  Vue frontend:  http://localhost:8082" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
