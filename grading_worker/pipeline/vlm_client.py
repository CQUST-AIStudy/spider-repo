"""VLM client with hash-based caching via Redis."""
import hashlib
import json
import base64
import re
import time
import redis
import httpx
from config import VLM_API_URL, VLM_API_KEY, VLM_MODEL, REDIS_HOST, REDIS_PORT
from models.pipeline_models import VlmResult

_redis_client = None
MAX_HTTP_RETRIES = 4
HTTP_RETRY_BASE_DELAY = 1.2
RETRYABLE_STATUS_CODES = {408, 409, 425, 429, 500, 502, 503, 504}


def _get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0, decode_responses=True)
    return _redis_client


def compute_image_hash(image_bytes: bytes) -> str:
    """Compute SHA256 hash of image bytes for cache key."""
    return hashlib.sha256(image_bytes).hexdigest()


def _normalize_content_to_text(content) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                text = item.get("text") or item.get("content") or item.get("value")
                if text:
                    parts.append(str(text))
        return "\n".join(parts)
    return str(content)


def _extract_json_from_text(raw: str):
    text = (raw or "").strip()
    if not text:
        return None

    if text.startswith("```"):
        first_lf = text.find("\n")
        last_fence = text.rfind("```")
        if first_lf >= 0 and last_fence > first_lf:
            text = text[first_lf + 1:last_fence].strip()

    # Try full parse first.
    try:
        return json.loads(text)
    except Exception:
        pass

    # Extract the outermost JSON object if wrapped with extra text.
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        candidate = text[start:end + 1]
        try:
            return json.loads(candidate)
        except Exception:
            pass

    return None


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


def call_vlm(image_bytes: bytes, task: str = "describe") -> VlmResult:
    """Call VLM API for multimodal extraction/understanding, with Redis caching."""
    img_hash = compute_image_hash(image_bytes)
    cache_key = f"vlm:cache:{task}:{img_hash}"

    # Check cache
    r = _get_redis()
    cached = r.get(cache_key)
    if cached:
        try:
            return VlmResult(description_json=json.loads(cached), cached=True)
        except Exception:
            pass

    # No VLM API configured — return empty
    if not VLM_API_URL:
        return VlmResult(description_json={"note": "VLM not configured"}, cached=False)

    try:
        b64_image = base64.b64encode(image_bytes).decode("utf-8")
        if task == "extract_text":
            prompt = (
                "Read this screenshot or scanned page carefully and return strict JSON only. "
                "Schema: {\"recognized_text\":\"...\",\"summary\":\"...\",\"confidence\":0.0}. "
                "recognized_text should contain the main visible text content in Chinese or original language. "
                "If the image is not text-heavy, still summarize the useful content."
            )
        else:
            prompt = (
                "Describe this image as short strict JSON only. "
                "Schema: {\"image_type\":\"diagram|plot|screenshot|other\",\"recognized_text\":\"...\","
                "\"summary\":\"...\",\"confidence\":0.0}. "
                "For diagrams or plots, summarize the key relationship or trend. "
                "If visible text exists, include it in recognized_text."
            )

        payload = {
            "model": VLM_MODEL,
            "messages": [
                {"role": "system", "content": "Return valid JSON only."},
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_image}"}},
                        {"type": "text", "text": prompt}
                    ]
                }
            ],
            "max_tokens": 500,
            "response_format": {"type": "json_object"},
        }

        headers = {"Authorization": f"Bearer {VLM_API_KEY}", "Content-Type": "application/json"}
        last_error = None
        for attempt in range(MAX_HTTP_RETRIES):
            try:
                resp = httpx.post(VLM_API_URL, json=payload, headers=headers, timeout=30.0)
                resp.raise_for_status()
                break
            except httpx.HTTPError as exc:
                last_error = exc
                if attempt == MAX_HTTP_RETRIES - 1 or not _is_retryable_http_error(exc):
                    raise
                time.sleep(HTTP_RETRY_BASE_DELAY * (attempt + 1))
        else:
            raise last_error

        data = resp.json()
        message = data.get("choices", [{}])[0].get("message", {}) or {}
        content = _normalize_content_to_text(message.get("content", "{}"))

        desc = _extract_json_from_text(content)
        if desc is None:
            plain = re.sub(r"\s+", " ", (content or "")).strip()
            if plain:
                # Keep useful plain text instead of treating it as full failure.
                desc = {"summary": plain[:2000], "recognized_text": plain[:2000], "confidence": 0.62}
            else:
                desc = {"raw": content}

        # Cache result (TTL 7 days)
        r.setex(cache_key, 604800, json.dumps(desc))
        return VlmResult(description_json=desc, cached=False)

    except Exception as e:
        return VlmResult(description_json={"error": str(e)}, cached=False)
