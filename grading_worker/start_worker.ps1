# Grading Worker Startup Script
# Uses conda environment 'mylangchain' (Python 3.10)
# Requires: Redis running, MySQL accessible, MinIO running
#
# Two processes need to run:
# 1. Celery worker - executes the actual grading pipeline tasks
# 2. Queue consumer - bridges Redis list (from Spring Boot) to Celery task queue

$PYTHON = "F:\downloads\miniconda\envs\mylangchain\python.exe"

Write-Host "Starting Grading Worker (Python: $PYTHON)..." -ForegroundColor Green

# Start Celery worker in background
$celeryJob = Start-Job -ScriptBlock {
    param($py)
    & $py "$using:PSScriptRoot\run_worker.py"
} -ArgumentList $PYTHON
Write-Host "Celery worker started (Job ID: $($celeryJob.Id))" -ForegroundColor Cyan

# Give Celery a moment to initialize
Start-Sleep -Seconds 5

# Start queue consumer in foreground
Write-Host "Starting queue consumer..." -ForegroundColor Cyan
& $PYTHON "$PSScriptRoot\run_consumer.py"
