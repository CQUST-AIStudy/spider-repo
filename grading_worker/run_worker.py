"""Launcher script that ensures correct working directory and starts Celery worker."""
import os
import sys

# Set working directory to this script's directory
os.chdir(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from celery_app import app

if __name__ == "__main__":
    app.worker_main([
        "worker",
        "--loglevel=info",
        "--concurrency=1",
        "-Q", "grading",
        "--pool=solo",
    ])
