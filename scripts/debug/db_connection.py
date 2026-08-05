import os

import pymysql


def connect():
    password = os.getenv("DB_PASSWORD") or os.getenv("DB_PASS")
    if not password:
        raise RuntimeError("DB_PASSWORD or DB_PASS is required")
    return pymysql.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        user=os.getenv("DB_USERNAME", "root"),
        password=password,
        database=os.getenv("DB_NAME", "ptadatabase"),
        charset="utf8mb4",
    )
