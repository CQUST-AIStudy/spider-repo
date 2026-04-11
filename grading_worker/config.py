"""Configuration for the grading worker."""
import os

# Redis
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_URL = f"redis://{REDIS_HOST}:{REDIS_PORT}/0"

# MySQL
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "ptadatabase")
DB_USER = os.getenv("DB_USER", "root")
DB_PASS = os.getenv("DB_PASS", "123456")
DATABASE_URL = f"mysql+pymysql://{DB_USER}:{DB_PASS}@{DB_HOST}:{DB_PORT}/{DB_NAME}?charset=utf8mb4"

# MinIO / S3
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "tap-files")
MINIO_SECURE = os.getenv("MINIO_SECURE", "false").lower() == "true"

# DeepSeek API
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
DEEPSEEK_RATE_LIMIT = int(os.getenv("DEEPSEEK_RATE_LIMIT", "30"))  # requests per minute

# DashScope Embedding
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
DASHSCOPE_EMBEDDING_MODEL = "text-embedding-v3"
DASHSCOPE_EMBEDDING_DIM = 1024
DASHSCOPE_EMBEDDING_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"

# Qwen / DashScope compatible chat
DASHSCOPE_COMPAT_BASE_URL = os.getenv("DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
QWEN_TEXT_MODEL = os.getenv("QWEN_TEXT_MODEL", "qwen-plus-latest")
QWEN_VLM_MODEL = os.getenv("QWEN_VLM_MODEL", "qwen-vl-max-latest")
QWEN_RATE_LIMIT = int(os.getenv("QWEN_RATE_LIMIT", "20"))

GRADING_AI_PROVIDER = os.getenv(
    "GRADING_AI_PROVIDER",
    "qwen" if DASHSCOPE_API_KEY else ("deepseek" if DEEPSEEK_API_KEY else "mock"),
).strip().lower()
if GRADING_AI_PROVIDER == "qwen":
    GRADING_API_KEY = DASHSCOPE_API_KEY
    GRADING_BASE_URL = DASHSCOPE_COMPAT_BASE_URL
    GRADING_MODEL = os.getenv("GRADING_MODEL", QWEN_TEXT_MODEL)
    GRADING_RATE_LIMIT = QWEN_RATE_LIMIT
else:
    GRADING_API_KEY = DEEPSEEK_API_KEY
    GRADING_BASE_URL = DEEPSEEK_BASE_URL
    GRADING_MODEL = os.getenv("GRADING_MODEL", DEEPSEEK_MODEL)
    GRADING_RATE_LIMIT = DEEPSEEK_RATE_LIMIT

# VLM API
VLM_API_URL = os.getenv("VLM_API_URL", f"{DASHSCOPE_COMPAT_BASE_URL}/chat/completions" if DASHSCOPE_API_KEY else "")
VLM_API_KEY = os.getenv("VLM_API_KEY", DASHSCOPE_API_KEY)
VLM_MODEL = os.getenv("VLM_MODEL", QWEN_VLM_MODEL)
VLM_RATE_LIMIT = int(os.getenv("VLM_RATE_LIMIT", "8"))  # requests per minute
OCR_STRATEGY = os.getenv(
    "OCR_STRATEGY",
    "qwen_first" if DASHSCOPE_API_KEY else "ocr_first",
).strip().lower()

# Celery
CELERY_CONCURRENCY = int(os.getenv("CELERY_CONCURRENCY", "3" if os.name == "nt" else "6"))
CELERY_POOL = os.getenv("CELERY_POOL", "threads" if os.name == "nt" else "prefork")

# Fallback scoring concurrency inside each submission.
# Primary path uses one batch AI call per submission, so keep fallback nested concurrency conservative.
DIMENSION_SCORE_CONCURRENCY = int(os.getenv("DIMENSION_SCORE_CONCURRENCY", "1"))

# Queue keys
TASK_QUEUE_KEY = "grading:tasks"
RESULT_CHANNEL = "grading:results"

# Milvus
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))
MILVUS_COLLECTION = "course_chunks"

# RAG Queue
RAG_TASK_QUEUE_KEY = "rag:tasks"

try:
    from local_settings import *  # noqa: F401,F403
except Exception:
    pass
