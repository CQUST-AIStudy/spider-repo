"""VLM client with hash-based caching via Redis."""
import hashlib
import json
import base64
import redis
import httpx
from config import VLM_API_URL, VLM_API_KEY, REDIS_HOST, REDIS_PORT
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


def call_vlm(image_bytes: bytes) -> VlmResult:
    """Call VLM API for diagram/plot description, with Redis caching."""
    img_hash = compute_image_hash(image_bytes)
    cache_key = f"vlm:cache:{img_hash}"

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
        payload = {
            "model": "vlm",
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_image}"}},
                        {"type": "text", "text": (
                            "Describe this image as a short structured JSON. "
                            "For diagrams: {\"type\":\"diagram\",\"nodes\":[...],\"edges\":[...]}. "
                            "For plots: {\"type\":\"plot\",\"x_label\":\"...\",\"y_label\":\"...\",\"trend\":\"...\"}. "
                            "Keep it under 100 tokens."
                        )}
                    ]
                }
            ],
            "max_tokens": 150
        }

        headers = {"Authorization": f"Bearer {VLM_API_KEY}", "Content-Type": "application/json"}
        resp = httpx.post(VLM_API_URL, json=payload, headers=headers, timeout=30.0)
        resp.raise_for_status()

        data = resp.json()
        content = data.get("choices", [{}])[0].get("message", {}).get("content", "{}")

        try:
            desc = json.loads(content)
        except json.JSONDecodeError:
            desc = {"raw": content}

        # Cache result (TTL 7 days)
        r.setex(cache_key, 604800, json.dumps(desc))
        return VlmResult(description_json=desc, cached=False)

    except Exception as e:
        return VlmResult(description_json={"error": str(e)}, cached=False)
