"""DeepSeek LLM scorer for rubric dimensions."""
import json
import time
import httpx
import redis
from typing import Optional
from config import (DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL,
                    DEEPSEEK_RATE_LIMIT, REDIS_HOST, REDIS_PORT)
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
        # Wait until oldest entry expires
        oldest = r.zrange(key, 0, 0, withscores=True)
        if oldest:
            wait_time = window - (now - oldest[0][1])
            if wait_time > 0:
                time.sleep(wait_time)


def score_dimension(
    evidence_pack: EvidencePack,
    dimension: dict,  # {id, name, description, max_score, weight}
    custom_prompt: str = None,
) -> tuple[ScoreResult, dict]:
    """Score a single rubric dimension using DeepSeek. Returns (ScoreResult, trace_info)."""
    _rate_limit_wait()

    # Build prompt
    evidence_text = "\n\n".join([
        f"[证据 {eb.evidence_id}] (类型: {eb.kind}, 页码: {eb.page}, 置信度: {eb.confidence or 'N/A'})\n{eb.content}"
        for eb in evidence_pack.blocks
    ])

    # Include teacher's custom prompt if provided
    custom_section = ""
    if custom_prompt and custom_prompt.strip():
        custom_section = f"\n## 教师自定义评分要求\n{custom_prompt.strip()}\n"

    prompt = f"""你是一个严格的实验报告评分助手。请根据以下评分维度和证据材料进行评分。
{custom_section}
## 评分维度
- 名称: {dimension['name']}
- 描述: {dimension.get('description', '')}
- 满分: {dimension['max_score']}

## 证据材料
{evidence_text}

## 输出要求
请以严格的JSON格式输出，不要包含任何其他文字：
{{
  "dimension_id": {dimension['id']},
  "score": <0到{dimension['max_score']}之间的数字>,
  "max_score": {dimension['max_score']},
  "comment": "<评分理由，说明扣分点>",
  "evidence_ids": ["<引用的证据ID列表>"],
  "status": "SCORED"
}}

如果证据不足以做出判断，请输出：
{{
  "dimension_id": {dimension['id']},
  "score": null,
  "max_score": {dimension['max_score']},
  "comment": "证据不足，无法评分",
  "evidence_ids": [],
  "status": "NEED_MORE_EVIDENCE"
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
                headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                         "Content-Type": "application/json"},
                timeout=60.0,
            )
            resp.raise_for_status()
            data = resp.json()

            usage = data.get("usage", {})
            trace_info["input_tokens"] = usage.get("prompt_tokens", 0)
            trace_info["output_tokens"] = usage.get("completion_tokens", 0)

            content = data["choices"][0]["message"]["content"]
            # Extract JSON from response (handle markdown code blocks)
            content = content.strip()
            if content.startswith("```"):
                content = content.split("\n", 1)[1] if "\n" in content else content[3:]
                content = content.rsplit("```", 1)[0]
            content = content.strip()

            parsed = json.loads(content)
            result = ScoreResult(
                dimension_id=parsed.get("dimension_id", dimension["id"]),
                score=parsed.get("score"),
                max_score=parsed.get("max_score", dimension["max_score"]),
                comment=parsed.get("comment", ""),
                evidence_ids=parsed.get("evidence_ids", []),
                status=parsed.get("status", "SCORED"),
            )
            trace_info["duration_ms"] = int((time.time() - start) * 1000)
            return result, trace_info

        except (json.JSONDecodeError, KeyError) as e:
            if attempt == MAX_SCHEMA_RETRIES - 1:
                trace_info["duration_ms"] = int((time.time() - start) * 1000)
                return ScoreResult(
                    dimension_id=dimension["id"],
                    score=None,
                    max_score=dimension["max_score"],
                    comment=f"评分失败: JSON解析错误 ({str(e)})",
                    status="NEED_MORE_EVIDENCE",
                ), trace_info
            continue

        except Exception as e:
            trace_info["duration_ms"] = int((time.time() - start) * 1000)
            raise
