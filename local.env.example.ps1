# Copy this file to `local.env.ps1` and replace placeholders with real local values.
# `start-all.ps1` will load `local.env.ps1` automatically if it exists.

$env:AI_PROVIDER = "openai"
$env:OPENAI_API_KEY = ""
$env:OPENAI_BASE_URL = "https://api.deepseek.com/v1"
$env:OPENAI_MODEL = "deepseek-chat"

$env:DEEPL_API_KEY = ""
$env:DASHSCOPE_API_KEY = ""
$env:VOLCANO_API_KEY = ""
$env:ARK_API_KEY = ""

# Optional migration flag: keep false unless you explicitly need the old standalone tap-backend.
# $env:START_LEGACY_TAP_BACKEND = "true"

# Optional TAP development seed users.
# $env:TAP_DEV_SEED_USERS_ENABLED = "true"
# $env:TAP_DEV_ADMIN_PASSWORD = "change-me"
# $env:TAP_DEV_TEACHER_PASSWORD = "change-me"

# Optional local service credentials
# $env:DB_USERNAME = "root"
# $env:DB_PASSWORD = "123456"
# $env:DB_PASS = "123456"
# $env:JWT_SECRET = "replace-with-a-long-random-secret"
# $env:MINIO_ACCESS_KEY = "minioadmin"
# $env:MINIO_SECRET_KEY = "minioadmin"
