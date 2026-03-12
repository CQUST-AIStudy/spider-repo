"""DeepSeek LLM scorer for rubric dimensions."""
import json
import re
import time

import httpx
import redis

from config import (
    DEEPSEEK_API_KEY,
    DEEPSEEK_BASE_URL,
    DEEPSEEK_MODEL,
    DEEPSEEK_RATE_LIMIT,
    REDIS_HOST,
    REDIS_PORT,
)
from models.pipeline_models import EvidencePack, ScoreResult

MAX_SCHEMA_RETRIES = 3
_redis_client = None


def _get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0)
    return _redis_client


def _rate_limit_wait():
    """Simple sliding window rate limiter via Redis."""
    if DEEPSEEK_RATE_LIMIT <= 0:
        return

    r = _get_redis()
    key = "ratelimit:deepseek"
    now = time.time()
    window = 60  # 1 minute window

    pipe = r.pipeline()
    pipe.zremrangebyscore(key, 0, now - window)
    pipe.zcard(key)
    pipe.zadd(key, {str(now): now})
    pipe.expire(key, window + 1)
    results = pipe.execute()

    count = results[1]
    if count >= DEEPSEEK_RATE_LIMIT:
        oldest = r.zrange(key, 0, 0, withscores=True)
        if oldest:
            wait_time = window - (now - oldest[0][1])
            if wait_time > 0:
                time.sleep(wait_time)


def _extract_json_object(content: str) -> dict:
    """Extract and parse a JSON object from raw model output."""
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


def _to_float(value, default=None):
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _has_substantial_evidence(evidence_pack: EvidencePack) -> bool:
    if not evidence_pack or not evidence_pack.blocks:
        return False
    text_len = 0
    signal_blocks = 0
    for eb in evidence_pack.blocks:
        if eb.kind in ("text", "ocr", "vlm"):
            txt = (eb.content or "").strip()
            if txt:
                signal_blocks += 1
                text_len += len(txt)
    return signal_blocks >= 2 or text_len >= 120


def _normalize_result(parsed: dict, evidence_pack: EvidencePack, dimension: dict) -> ScoreResult:
    """Normalize model JSON into safe ScoreResult."""
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

    # Reduce false NEED_MORE_EVIDENCE when evidence is clearly present.
    if status == "NEED_MORE_EVIDENCE" and _has_substantial_evidence(evidence_pack):
        status = "SCORED"
        if score is None:
            score = 0.0
        if not comment:
            comment = "存在可用证据，但证据强度偏弱，按保守策略评分。"
        else:
            comment += "（检测到可用证据，按保守策略评分）"

    if status == "SCORED" and score is None:
        score = 0.0
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


def score_dimension(
    evidence_pack: EvidencePack,
    dimension: dict,  # {id, name, description, max_score, weight}
    custom_prompt: str = None,
) -> tuple[ScoreResult, dict]:
    """Score one rubric dimension using DeepSeek. Returns (ScoreResult, trace_info)."""
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

    prompt = f"""你是一个严格的实验报告评分助手。请根据以下评分维度和证据材料进行评分。
{custom_section}
## 评分维度
- 名称: {dimension["name"]}
- 描述: {dimension.get("description", "")}
- 满分: {dimension["max_score"]}

## 证据材料
{evidence_text}

## 规则
1. 只要存在部分相关证据，必须给出保守分数并返回 status=SCORED。
2. 只有在所有证据都与该维度完全无关时，才可返回 status=NEED_MORE_EVIDENCE。
3. score 必须在 0 到 {dimension["max_score"]} 之间。
4. evidence_ids 只能填写上面已出现的证据ID。

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

    trace_info = {"model_used": DEEPSEEK_MODEL, "input_tokens": 0, "output_tokens": 0}
    start = time.time()

    for attempt in range(MAX_SCHEMA_RETRIES):
        try:
            resp = httpx.post(
                f"{DEEPSEEK_BASE_URL}/chat/completions",
                json={
                    "model": DEEPSEEK_MODEL,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0.1,
                    "max_tokens": 500,
                },
                headers={
                    "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                    "Content-Type": "application/json",
                },
                timeout=60.0,
            )
            resp.raise_for_status()
            data = resp.json()

            usage = data.get("usage", {})
            trace_info["input_tokens"] = usage.get("prompt_tokens", 0)
            trace_info["output_tokens"] = usage.get("completion_tokens", 0)

            content = data["choices"][0]["message"]["content"]
            parsed = _extract_json_object(content)
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
                            score=0.0,
                            max_score=dimension["max_score"],
                            comment=f"模型响应格式异常，已按保守策略评分: {str(e)[:120]}",
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
