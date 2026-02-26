"""FastAPI health endpoint for the grading worker."""
from fastapi import FastAPI

app = FastAPI(title="Grading Worker", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "ok", "service": "grading-worker"}
