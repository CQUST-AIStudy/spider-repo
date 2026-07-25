ARG PYTHON_BASE_IMAGE=python:3.11-slim-bookworm
FROM ${PYTHON_BASE_IMAGE}

ARG DEBIAN_MIRROR=mirrors.aliyun.com
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8 \
    PYTHONPATH=/app/src \
    SPIDER_PORT=8100 \
    PTA_HEADLESS=true \
    PTA_KEEP_BROWSER_OPEN_ON_FAILURE=false \
    PTA_RUNTIME_DIR=/app/runtime \
    PTA_CRAWL_DIR=/app/output \
    PTA_BROWSER_HOME=/app/runtime/browser \
    SE_CACHE_PATH=/app/runtime/.selenium \
    PTA_CHROME_BINARY=/usr/bin/chromium \
    PTA_CHROMEDRIVER_PATH=/usr/bin/chromedriver \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_DEFAULT_TIMEOUT=60

WORKDIR /app

RUN find /etc/apt -type f \( -name '*.list' -o -name '*.sources' \) \
        -exec sed -i -E "s#https?://deb.debian.org#http://${DEBIAN_MIRROR}#g" {} + \
    && apt-get -o Acquire::Retries=3 update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        unzip \
        fonts-liberation \
        fonts-noto-cjk \
        chromium \
        chromium-driver \
        libasound2 \
        libatk-bridge2.0-0 \
        libatk1.0-0 \
        libcups2 \
        libdbus-1-3 \
        libdrm2 \
        libgbm1 \
        libgtk-3-0 \
        libnss3 \
        libxcomposite1 \
        libxdamage1 \
        libxfixes3 \
        libxkbcommon0 \
        libxrandr2 \
        xdg-utils \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN python -m pip install \
        --no-cache-dir \
        --index-url "${PIP_INDEX_URL}" \
        --timeout 60 \
        --retries 3 \
        -r requirements.txt

COPY src ./src
COPY scripts ./scripts
COPY README.md STARTUP.md .env.example pyproject.toml spider.py spider_api.py ./

RUN useradd --create-home --shell /usr/sbin/nologin spider \
    && mkdir -p /app/runtime /app/output \
    && chown -R spider:spider /app

USER spider

EXPOSE 8100

CMD ["python", "-m", "pta_spider.spider_api"]
