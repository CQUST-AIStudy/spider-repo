import asyncio
import json
from pathlib import Path

import pytest
from fastapi import HTTPException

from pta_spider import spider, spider_api
from pta_spider.initial_credentials import (
    create_initial_password,
    escrow_initial_credential,
)


def test_random_initial_password_is_not_predictable_student_number(monkeypatch):
    monkeypatch.setenv("PTA_STUDENT_INITIAL_PASSWORD_MODE", "random")
    first, first_needs_escrow = create_initial_password("20260001")
    second, second_needs_escrow = create_initial_password("20260001")

    assert first != "20260001"
    assert second != "20260001"
    assert first != second
    assert first_needs_escrow and second_needs_escrow


def test_credential_escrow_uses_configured_operator_file(monkeypatch, tmp_path):
    path = tmp_path / "issued.jsonl"
    monkeypatch.setenv("PTA_INITIAL_CREDENTIALS_FILE", str(path))

    escrow_initial_credential("student", "generated-value")

    record = json.loads(path.read_text(encoding="utf-8"))
    assert record["username"] == "student"
    assert record["initial_password"] == "generated-value"
    assert record["requires_password_change"] is True


def test_pta_client_separates_cookie_history_and_output(monkeypatch, tmp_path):
    runtime = tmp_path / "runtime"
    output = tmp_path / "output"
    monkeypatch.setattr(spider, "RUNTIME_DIR", runtime)
    monkeypatch.setattr(spider, "CRAWL_DIR", output)

    first = spider.PTAClient(credential_scope="class:1", allow_env_fallback=False)
    second = spider.PTAClient(credential_scope="class:2", allow_env_fallback=False)

    assert first.cookie_file != second.cookie_file
    assert first.manual_cookie_file != second.manual_cookie_file
    assert first.history.path != second.history.path
    assert first.crawl_dir != second.crawl_dir
    assert Path(first.cookie_file).is_relative_to(runtime)


def test_manual_cookie_api_rejects_unscoped_file():
    request = spider_api.ManualCookieRequest(cookies="[]")

    with pytest.raises(HTTPException) as exc:
        asyncio.run(spider_api.manual_update_cookie(request))

    assert exc.value.status_code == 400
