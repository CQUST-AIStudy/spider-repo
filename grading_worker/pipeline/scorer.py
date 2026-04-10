"""LLM scorer for rubric dimensions."""
import json
import re
import time

import httpx
import redis

from config import (
    GRADING_AI_PROVIDER,
    GRADING_API_KEY,
    GRADING_BASE_URL,
    GRADING_MODEL,
    GRADING_RATE_LIMIT,
    REDIS_HOST,
    REDIS_PORT,
)
from models.pipeline_models import EvidencePack, ScoreResult

MAX_SCHEMA_RETRIES = 3
MAX_EVIDENCE_BLOCKS_PER_DIM = 4
MAX_EVIDENCE_CHARS_PER_BLOCK = 600
SCORE_FLOOR_RATIO_ON_SUBSTANTIAL_EVIDENCE = 0.35
SCORE_FLOOR_RATIO_ON_EXTRACT_NOISE = 0.15
MAX_HTTP_RETRIES = 4
HTTP_RETRY_BASE_DELAY = 1.5
RETRYABLE_STATUS_CODES = {408, 409, 425, 429, 500, 502, 503, 504}

_redis_client = None


def _get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0)
    return _redis_client


def _rate_limit_wait():
    """Simple sliding window rate limiter via Redis."""
    if GRADING_RATE_LIMIT <= 0:
        return

    r = _get_redis()
    key = f"ratelimit:{GRADING_AI_PROVIDER}"
    now = time.time()
    window = 60

    pipe = r.pipeline()
    pipe.zremrangebyscore(key, 0, now - window)
    pipe.zcard(key)
    pipe.zadd(key, {str(now): now})
    pipe.expire(key, window + 1)
    results = pipe.execute()

    count = results[1]
    if count >= GRADING_RATE_LIMIT:
        oldest = r.zrange(key, 0, 0, withscores=True)
        if oldest:
            wait_time = window - (now - oldest[0][1])
            if wait_time > 0:
                time.sleep(wait_time)


def _extract_json_object(content: str) -> dict:
    cleaned = (content or "").strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s*```$", "", cleaned)
        cleaned = cleaned.strip()

    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{[\s\S]*\}", cleaned)
    if not match:
        raise json.JSONDecodeError("No JSON object found", cleaned, 0)
    return json.loads(match.group(0))


def _is_retryable_http_error(exc: httpx.HTTPError) -> bool:
    if isinstance(exc, httpx.HTTPStatusError):
        return exc.response is not None and exc.response.status_code in RETRYABLE_STATUS_CODES
    if isinstance(exc, (httpx.TimeoutException, httpx.NetworkError, httpx.ProtocolError)):
        return True
    message = str(exc).lower()
    return (
        "eof occurred in violation of protocol" in message
        or "connection reset" in message
        or "temporarily unavailable" in message
        or "server disconnected" in message
    )


def _post_chat_json(prompt: str, max_tokens: int) -> tuple[dict, dict]:
    if not GRADING_API_KEY:
        raise RuntimeError(f"{GRADING_AI_PROVIDER} API key is not configured")

    last_error = None
    for attempt in range(MAX_HTTP_RETRIES):
        try:
            resp = httpx.post(
                f"{GRADING_BASE_URL}/chat/completions",
                json={
                    "model": GRADING_MODEL,
                    "messages": [
                        {"role": "system", "content": "Return strict JSON only."},
                        {"role": "user", "content": prompt},
                    ],
                    "temperature": 0.1,
                    "max_tokens": max_tokens,
                },
                headers={
                    "Authorization": f"Bearer {GRADING_API_KEY}",
                    "Content-Type": "application/json",
                },
                timeout=90.0,
            )
            resp.raise_for_status()
            data = resp.json()
            usage = data.get("usage", {})
            content = data["choices"][0]["message"]["content"]
            parsed = _extract_json_object(content)
            trace_info = {
                "model_used": GRADING_MODEL,
                "input_tokens": usage.get("prompt_tokens", 0),
                "output_tokens": usage.get("completion_tokens", 0),
                "http_attempts": attempt + 1,
            }
            return parsed, trace_info
        except httpx.HTTPError as exc:
            last_error = exc
            if attempt == MAX_HTTP_RETRIES - 1 or not _is_retryable_http_error(exc):
                raise
            time.sleep(HTTP_RETRY_BASE_DELAY * (attempt + 1))

    raise last_error


def _to_float(value, default=None):
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _clip_text(text: str, max_len: int) -> str:
    s = (text or "").strip()
    if len(s) <= max_len:
        return s
    return s[: max_len - 3].rstrip() + "..."


def _has_substantial_evidence(evidence_pack: EvidencePack) -> bool:
    if not evidence_pack or not evidence_pack.blocks:
        return False
    text_len = 0
    signal_blocks = 0
    for eb in evidence_pack.blocks:
        if eb.kind in ("text", "ocr", "vlm", "image"):
            txt = (eb.content or "").strip()
            if txt:
                signal_blocks += 1
                text_len += len(txt)
    return signal_blocks >= 2 or text_len >= 120


def _evidence_stats(evidence_pack: EvidencePack) -> tuple[int, int, int]:
    usable_blocks = 0
    failed_blocks = 0
    text_len = 0
    for eb in (evidence_pack.blocks if evidence_pack else []):
        kind = (eb.kind or "").lower()
        content = (eb.content or "").strip()
        if kind in ("text", "ocr", "vlm", "image") and content:
            usable_blocks += 1
            text_len += len(content)
        elif kind == "vlm_failed":
            failed_blocks += 1
    return usable_blocks, failed_blocks, text_len


def _normalize_result(parsed: dict, evidence_pack: EvidencePack, dimension: dict) -> ScoreResult:
    max_score = float(dimension["max_score"])
    valid_evidence_ids = {eb.evidence_id for eb in evidence_pack.blocks}

    status = str(parsed.get("status", "SCORED")).upper()
    if status not in ("SCORED", "NEED_MORE_EVIDENCE"):
        status = "SCORED"

    score = _to_float(parsed.get("score"), default=None)
    if score is not None:
        score = max(0.0, min(max_score, score))

    comment = str(parsed.get("comment", "")).strip()

    evidence_ids = parsed.get("evidence_ids", [])
    if not isinstance(evidence_ids, list):
        evidence_ids = []
    evidence_ids = [str(eid) for eid in evidence_ids if str(eid) in valid_evidence_ids][:8]

    # Avoid false NEED_MORE_EVIDENCE when useful evidence exists.
    if status == "NEED_MORE_EVIDENCE" and _has_substantial_evidence(evidence_pack):
        status = "SCORED"
        if score is None:
            score = round(max_score * SCORE_FLOOR_RATIO_ON_SUBSTANTIAL_EVIDENCE, 2)
        if not comment:
            comment = "存在可用证据，但覆盖不完整，已按保守策略给分。"
        else:
            comment += "（检测到可用证据，已按保守策略评分）"

    if status == "SCORED" and score is None:
        score = 0.0

    usable_blocks, failed_blocks, text_len = _evidence_stats(evidence_pack)
    if (
        status == "SCORED"
        and score is not None
        and usable_blocks >= 2
        and text_len >= 160
        and failed_blocks >= 2
        and score < max_score * SCORE_FLOOR_RATIO_ON_EXTRACT_NOISE
    ):
        score = round(max_score * SCORE_FLOOR_RATIO_ON_EXTRACT_NOISE, 2)
        if comment:
            comment += "（提取噪声较高，已启用最低分保护）"
        else:
            comment = "提取噪声较高，已启用最低分保护。"

    if status == "SCORED" and not evidence_ids and evidence_pack.blocks:
        evidence_ids = [evidence_pack.blocks[0].evidence_id]

    return ScoreResult(
        dimension_id=int(parsed.get("dimension_id", dimension["id"])),
        score=score,
        max_score=max_score,
        comment=comment,
        evidence_ids=evidence_ids,
        status=status,
    )


def score_dimensions_batch(
    evidence_packs: dict[int, EvidencePack],
    dimensions: list[dict],
    custom_prompt: str = None,
    score_range_min: float = None,
    score_range_max: float = None,
) -> tuple[dict[int, ScoreResult], dict]:
    """Score all rubric dimensions for one submission in a single model call."""
    _rate_limit_wait()

    custom_section = ""
    if custom_prompt and custom_prompt.strip():
        custom_section = (
            "\nTeacher custom requirements:\n"
            f"{custom_prompt.strip()}\n"
        )

    range_section = ""
    if score_range_min is not None and score_range_max is not None:
        range_section = (
            "\nOverall score calibration:\n"
            f"- The teacher expects most submissions in this batch to fall around {score_range_min:.0f}-{score_range_max:.0f} / 100.\n"
            "- Use this only as a calibration hint. Do not force every student into that range.\n"
        )

    request_payload = []
    for dim in dimensions:
        pack = evidence_packs.get(dim["id"])
        blocks = []
        usable_blocks = [eb for eb in (pack.blocks if pack else []) if (eb.kind or "").lower() != "vlm_failed"]
        for eb in usable_blocks[:MAX_EVIDENCE_BLOCKS_PER_DIM]:
            blocks.append({
                "evidence_id": eb.evidence_id,
                "kind": eb.kind,
                "page": eb.page,
                "confidence": eb.confidence,
                "content": _clip_text(eb.content, MAX_EVIDENCE_CHARS_PER_BLOCK),
            })
        request_payload.append({
            "dimension_id": dim["id"],
            "name": dim["name"],
            "description": dim.get("description", ""),
            "max_score": dim["max_score"],
            "weight": dim["weight"],
            "evidence_blocks": blocks,
        })

    prompt = (
        "You are a fair, evidence-grounded lab report grading assistant.\n"
        "Grade every rubric dimension below based only on the provided evidence.\n"
        "Comments must be in Chinese.\n"
        f"{custom_section}"
        f"{range_section}"
        "\nRules:\n"
        "1. If a dimension has some relevant evidence, return status=SCORED and give partial credit.\n"
        "2. Use status=NEED_MORE_EVIDENCE only when all provided evidence is irrelevant or missing for that dimension.\n"
        "3. score must be between 0 and max_score.\n"
        "4. evidence_ids must only use IDs that appear in that dimension's evidence_blocks.\n"
        "5. Do not over-penalize extraction uncertainty. If core steps/results exist, avoid near-zero scores.\n"
        "6. Return JSON only. No markdown.\n"
        "\nReturn this schema exactly:\n"
        "{\n"
        '  "results": [\n'
        "    {\n"
        '      "dimension_id": 1,\n'
        '      "score": 0,\n'
        '      "max_score": 10,\n'
        '      "comment": "中文评分理由",\n'
        '      "evidence_ids": ["ev-1"],\n'
        '      "status": "SCORED"\n'
        "    }\n"
        "  ]\n"
        "}\n"
        "\nInput JSON:\n"
        f"{json.dumps(request_payload, ensure_ascii=False)}"
    )

    start = time.time()
    for attempt in range(MAX_SCHEMA_RETRIES):
        try:
            parsed, trace_info = _post_chat_json(prompt, max_tokens=1600)
            raw_results = parsed.get("results")
            if not isinstance(raw_results, list):
                raise ValueError("results must be a list")

            raw_by_dim = {}
            for item in raw_results:
                if isinstance(item, dict) and item.get("dimension_id") is not None:
                    raw_by_dim[int(item["dimension_id"])] = item

            normalized = {}
            for dim in dimensions:
                dim_id = int(dim["id"])
                if dim_id not in raw_by_dim:
                    raise ValueError(f"missing result for dimension {dim_id}")
                pack = evidence_packs.get(dim_id) or EvidencePack(dimension_id=dim_id, blocks=[])
                normalized[dim_id] = _normalize_result(raw_by_dim[dim_id], pack, dim)

            trace_info["duration_ms"] = int((time.time() - start) * 1000)
            trace_info["mode"] = "batch"
            return normalized, trace_info
        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as e:
            if attempt == MAX_SCHEMA_RETRIES - 1:
                raise ValueError(f"batch scoring returned invalid json: {e}") from e
            continue


def score_dimension(
    evidence_pack: EvidencePack,
    dimension: dict,  # {id, name, description, max_score, weight}
    custom_prompt: str = None,
) -> tuple[ScoreResult, dict]:
    """Score one rubric dimension using the configured chat model. Returns (ScoreResult, trace_info)."""
    _rate_limit_wait()

    evidence_text = "\n\n".join(
        [
            f"[证据 {eb.evidence_id}] (类型: {eb.kind}, 页码: {eb.page}, 置信度: {eb.confidence or 'N/A'})\n{eb.content}"
            for eb in evidence_pack.blocks
        ]
    )

    custom_section = ""
    if custom_prompt and custom_prompt.strip():
        custom_section = f"\n## 教师自定义评分要求\n{custom_prompt.strip()}\n"

    prompt = f"""你是一个公平、严谨的实验报告评分助手。请根据以下评分维度和证据材料进行评分。{custom_section}
## 评分维度
- 名称: {dimension["name"]}
- 描述: {dimension.get("description", "")}
- 满分: {dimension["max_score"]}

## 证据材料
{evidence_text}

## 规则
1. 只要存在部分相关证据，必须返回 status=SCORED，并给出合理的部分分。
2. 只有在所有证据都与该维度完全无关时，才可返回 status=NEED_MORE_EVIDENCE。
3. score 必须在 0 到 {dimension["max_score"]} 之间。
4. evidence_ids 只能填写上面已出现的证据ID。
5. 不要因识别噪声过度扣分；核心步骤或结果已出现时，不应给接近零分。

## 输出要求
只输出严格 JSON，不要任何额外文字：
{{
  "dimension_id": {dimension["id"]},
  "score": <number or null>,
  "max_score": {dimension["max_score"]},
  "comment": "<评分理由>",
  "evidence_ids": ["ev-..."],
  "status": "SCORED"
}}"""

    trace_info = {"model_used": GRADING_MODEL, "input_tokens": 0, "output_tokens": 0, "mode": "single"}
    start = time.time()

    for attempt in range(MAX_SCHEMA_RETRIES):
        try:
            parsed, call_trace = _post_chat_json(prompt, max_tokens=500)
            trace_info["input_tokens"] = call_trace["input_tokens"]
            trace_info["output_tokens"] = call_trace["output_tokens"]
            result = _normalize_result(parsed, evidence_pack, dimension)
            trace_info["duration_ms"] = int((time.time() - start) * 1000)
            return result, trace_info

        except (json.JSONDecodeError, KeyError, TypeError, ValueError) as e:
            if attempt == MAX_SCHEMA_RETRIES - 1:
                trace_info["duration_ms"] = int((time.time() - start) * 1000)
                if _has_substantial_evidence(evidence_pack):
                    return (
                        ScoreResult(
                            dimension_id=dimension["id"],
                            score=round(float(dimension["max_score"]) * SCORE_FLOOR_RATIO_ON_SUBSTANTIAL_EVIDENCE, 2),
                            max_score=dimension["max_score"],
                            comment=f"模型响应格式异常，已按保守策略评分：{str(e)[:120]}",
                            evidence_ids=[evidence_pack.blocks[0].evidence_id] if evidence_pack.blocks else [],
                            status="SCORED",
                        ),
                        trace_info,
                    )
                return (
                    ScoreResult(
                        dimension_id=dimension["id"],
                        score=None,
                        max_score=dimension["max_score"],
                        comment=f"评分失败: JSON解析错误 ({str(e)[:120]})",
                        evidence_ids=[],
                        status="NEED_MORE_EVIDENCE",
                    ),
                    trace_info,
                )
            continue
        except Exception:
            trace_info["duration_ms"] = int((time.time() - start) * 1000)
            raise
