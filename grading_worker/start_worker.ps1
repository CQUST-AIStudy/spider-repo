# Grading Worker Startup Script
# Detached startup that keeps both processes alive after this shell exits.

$PYTHON = "F:\downloads\miniconda\envs\mylangchain\python.exe"
$ROOT = Split-Path -Parent $PSScriptRoot
$LOG_DIR = Join-Path $ROOT "logs"

if (-not (Test-Path $PYTHON)) {
    throw "Python interpreter not found: $PYTHON"
}

if (-not (Test-Path $LOG_DIR)) {
    New-Item -ItemType Directory -Path $LOG_DIR | Out-Null
}

if (-not $env:CELERY_CONCURRENCY) { $env:CELERY_CONCURRENCY = "6" }
if (-not $env:CELERY_POOL) { $env:CELERY_POOL = "threads" }
if (-not $env:DIMENSION_SCORE_CONCURRENCY) { $env:DIMENSION_SCORE_CONCURRENCY = "1" }
if (-not $env:OCR_STRATEGY) { $env:OCR_STRATEGY = "vlm_only" }

$workerOut = Join-Path $LOG_DIR "grading_worker_daemon.out.log"
$workerErr = Join-Path $LOG_DIR "grading_worker_daemon.err.log"
$consumerOut = Join-Path $LOG_DIR "grading_consumer_daemon.out.log"
$consumerErr = Join-Path $LOG_DIR "grading_consumer_daemon.err.log"

foreach ($path in @($workerOut, $workerErr, $consumerOut, $consumerErr)) {
    if (Test-Path $path) {
        Remove-Item $path -Force
    }
}

$workerCmd = "cd /d `"$PSScriptRoot`" && `"$PYTHON`" run_worker.py 1>>`"$workerOut`" 2>>`"$workerErr`""
$consumerCmd = "cd /d `"$PSScriptRoot`" && `"$PYTHON`" run_consumer.py 1>>`"$consumerOut`" 2>>`"$consumerErr`""

Write-Host "Starting grading worker with OCR_STRATEGY=$env:OCR_STRATEGY" -ForegroundColor Green
Write-Host "Logs: $workerOut / $consumerOut" -ForegroundColor DarkCyan

$workerProc = Start-Process -FilePath "C:\Windows\System32\cmd.exe" -ArgumentList "/c", $workerCmd -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 3
$consumerProc = Start-Process -FilePath "C:\Windows\System32\cmd.exe" -ArgumentList "/c", $consumerCmd -WindowStyle Hidden -PassThru

Write-Host "Worker launcher PID: $($workerProc.Id)" -ForegroundColor Cyan
Write-Host "Consumer launcher PID: $($consumerProc.Id)" -ForegroundColor Cyan
Write-Host "Use Get-Content -Tail on the log files to inspect progress." -ForegroundColor Yellow
