# Start all local services from `g:\myapps`.
# Usage:
#   powershell -ExecutionPolicy Bypass -File start-all.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Starting local teaching platform stack" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

function Test-PortListening {
    param([int]$Port)

    return @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    ).Count -gt 0
}

function Stop-GradingWorkerProcesses {
    $patterns = @(
        'g:\myapps\grading_worker\run_worker.py',
        'g:\myapps\grading_worker\run_consumer.py'
    )

    $targets = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            if (-not $_.CommandLine) {
                return $false
            }

            $commandLine = $_.CommandLine.ToLower()
            foreach ($pattern in $patterns) {
                if ($commandLine.Contains($pattern)) {
                    return $true
                }
            }

            return $false
        }

    foreach ($proc in $targets) {
        try {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
            Write-Host ("  Stopped old grading process PID {0}" -f $proc.ProcessId) -ForegroundColor DarkGray
        } catch {
            Write-Host ("  Failed to stop old grading process PID {0}: {1}" -f $proc.ProcessId, $_.Exception.Message) -ForegroundColor DarkYellow
        }
    }
}

$localEnvScript = Join-Path $PSScriptRoot "local.env.ps1"
if (Test-Path $localEnvScript) {
    . $localEnvScript
    Write-Host "  Loaded local environment from local.env.ps1" -ForegroundColor DarkGray
} else {
    Write-Host "  local.env.ps1 not found; using current shell environment" -ForegroundColor DarkGray
}

$frontendPort = 8080
$backendPort = 8081

Write-Host ("`n[1/3] Ensuring unified AI_Ds backend on :{0}..." -f $backendPort) -ForegroundColor Yellow
if (Test-PortListening -Port $backendPort) {
    Write-Host ("  Backend already listening on :{0}; skipping duplicate start" -f $backendPort) -ForegroundColor DarkGray
} else {
    Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "G:\myapps\scripts\run_backend_dev.ps1" -WindowStyle Normal
    Start-Sleep -Seconds 3
}

if ($env:START_LEGACY_TAP_BACKEND -eq "true") {
    Write-Host ("[legacy] START_LEGACY_TAP_BACKEND=true, but frontend now uses :{0}; skipping legacy backend to avoid port conflict." -f $frontendPort) -ForegroundColor DarkYellow
} else {
    Write-Host "[legacy] Skipping standalone tap-backend; frontend uses AI_Ds :8081" -ForegroundColor DarkGray
}

Write-Host ("[2/3] Ensuring Vue frontend on :{0}..." -f $frontendPort) -ForegroundColor Yellow
if (Test-PortListening -Port $frontendPort) {
    Write-Host ("  Frontend already listening on :{0}; skipping duplicate start" -f $frontendPort) -ForegroundColor DarkGray
} else {
    Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "G:\myapps\scripts\run_frontend_dev.cmd" -WindowStyle Normal
}

Write-Host "[3/3] Restarting grading worker (consumer + celery worker)..." -ForegroundColor Yellow
Stop-GradingWorkerProcesses
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "G:\myapps\grading_worker\start_worker.ps1" -WindowStyle Normal

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "  All services have been started." -ForegroundColor Green
Write-Host ("  AI_Ds backend: http://localhost:{0}" -f $backendPort) -ForegroundColor White
Write-Host ("  Vue frontend:  http://localhost:{0}" -f $frontendPort) -ForegroundColor White
Write-Host "  Legacy backend: disabled by start-all.ps1 to avoid frontend port conflict" -ForegroundColor White
Write-Host "  Grading worker logs: g:\myapps\logs\grading_worker_daemon.err.log" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
