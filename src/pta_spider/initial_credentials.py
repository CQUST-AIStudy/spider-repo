import json
import os
import secrets
import threading
from datetime import datetime, timezone
from pathlib import Path

_credential_file_lock = threading.Lock()


def create_initial_password(username: str) -> tuple[str, bool]:
    """Return an unpredictable initial password and whether it needs escrow."""
    mode = os.getenv("PTA_STUDENT_INITIAL_PASSWORD_MODE", "random").strip().lower()
    if mode == "random":
        return secrets.token_urlsafe(24), True
    if mode == "configured":
        password = os.getenv("PTA_STUDENT_INITIAL_PASSWORD", "")
        if len(password) < 16:
            raise RuntimeError(
                "PTA_STUDENT_INITIAL_PASSWORD must contain at least 16 characters"
            )
        if password == str(username):
            raise RuntimeError("configured initial password must not equal the username")
        return password, False
    raise RuntimeError(
        "PTA_STUDENT_INITIAL_PASSWORD_MODE must be 'random' or 'configured'"
    )


def escrow_initial_credential(username: str, password: str) -> Path:
    """Append a one-time random credential to a local operator-only JSONL file."""
    project_root = Path(__file__).resolve().parents[2]
    runtime_dir = Path(
        os.getenv("PTA_RUNTIME_DIR", str(project_root / "runtime"))
    ).resolve()
    path = Path(
        os.getenv(
            "PTA_INITIAL_CREDENTIALS_FILE",
            str(runtime_dir / "student_initial_credentials.jsonl"),
        )
    ).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    record = json.dumps(
        {
            "username": str(username),
            "initial_password": password,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "requires_password_change": True,
        },
        ensure_ascii=False,
    ) + "\n"
    with _credential_file_lock:
        descriptor = os.open(path, os.O_APPEND | os.O_CREAT | os.O_WRONLY, 0o600)
        try:
            os.write(descriptor, record.encode("utf-8"))
        finally:
            os.close(descriptor)
        try:
            os.chmod(path, 0o600)
        except OSError:
            pass
    return path
