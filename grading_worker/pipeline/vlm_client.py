"""VLM client with hash-based caching via Redis."""
import hashlib
import json
import base64
import redis
import httpx
from config import VLM_API_URL, VLM_API_KEY, VLM_MODEL, REDIS_HOST, REDIS_PORT
from models.pipeline_models import VlmResult

_redis_client = None


def _get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=0, decode_responses=True)
    return _redis_client


def compute_image_hash(image_bytes: bytes) -> str:
    """Compute SHA256 hash of image bytes for cache key."""
    return hashlib.sha256(image_bytes).hexdigest()


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
            "max_tokens": 500
        }

        headers = {"Authorization": f"Bearer {VLM_API_KEY}", "Content-Type": "application/json"}
        resp = httpx.post(VLM_API_URL, json=payload, headers=headers, timeout=30.0)
        resp.raise_for_status()

        data = resp.json()
        content = data.get("choices", [{}])[0].get("message", {}).get("content", "{}")

        try:
            stripped = (content or "").strip()
            if stripped.startswith("```"):
                first_lf = stripped.find("\n")
                last_fence = stripped.rfind("```")
                if first_lf >= 0 and last_fence > first_lf:
                    stripped = stripped[first_lf + 1:last_fence].strip()
            start = stripped.find("{")
            end = stripped.rfind("}")
            if start >= 0 and end > start:
                stripped = stripped[start:end + 1]
            desc = json.loads(stripped)
        except json.JSONDecodeError:
            desc = {"raw": content}

        # Cache result (TTL 7 days)
        r.setex(cache_key, 604800, json.dumps(desc))
        return VlmResult(description_json=desc, cached=False)

    except Exception as e:
        return VlmResult(description_json={"error": str(e)}, cached=False)
