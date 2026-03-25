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

# VLM API
VLM_API_URL = os.getenv("VLM_API_URL", "")
VLM_API_KEY = os.getenv("VLM_API_KEY", "")
VLM_RATE_LIMIT = int(os.getenv("VLM_RATE_LIMIT", "5"))  # requests per minute

# Celery
CELERY_CONCURRENCY = int(os.getenv("CELERY_CONCURRENCY", "6"))
CELERY_POOL = os.getenv("CELERY_POOL", "threads" if os.name == "nt" else "prefork")

# Fallback scoring concurrency inside each submission.
# Primary path uses one batch AI call per submission, so keep fallback nested concurrency conservative.
DIMENSION_SCORE_CONCURRENCY = int(os.getenv("DIMENSION_SCORE_CONCURRENCY", "1"))

# Queue keys
TASK_QUEUE_KEY = "grading:tasks"
RESULT_CHANNEL = "grading:results"

# DashScope Embedding
DASHSCOPE_API_KEY = os.getenv("DASHSCOPE_API_KEY", "")
DASHSCOPE_EMBEDDING_MODEL = "text-embedding-v3"
DASHSCOPE_EMBEDDING_DIM = 1024
DASHSCOPE_EMBEDDING_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"

# Milvus
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))
MILVUS_COLLECTION = "course_chunks"

# RAG Queue
RAG_TASK_QUEUE_KEY = "rag:tasks"
