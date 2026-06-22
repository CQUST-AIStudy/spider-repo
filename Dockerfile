# PTA Spider API 镜像
# 基于 python:3.11-slim，内置 Google Chrome + 匹配版本的 chromedriver，
# 使 selenium 在容器内可直接以 headless 方式登录 PTA。

FROM python:3.11-slim

# ===== 1. 系统依赖与 Google Chrome =====
# 先装基础工具与 Chrome 运行所需的共享库（headless Chrome 必需）
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
        wget \
        gnupg \
        curl \
        unzip \
        libnss3 \
        libatk1.0-0 \
        libatk-bridge2.0-0 \
        libcups2 \
        libxkbcommon0 \
        libxcomposite1 \
        libxdamage1 \
        libxrandr2 \
        libgbm1 \
        libpango-1.0-0 \
        libcairo2 \
        libasound2 \
        libxshmfence1 \
        libdrm2 \
        libxfixes3 \
        libgl1 \
        libglib2.0-0 \
        fonts-liberation \
        fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

# 安装 Google Chrome 稳定版
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
        | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
        > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends google-chrome-stable \
    && rm -rf /var/lib/apt/lists/*

# ===== 2. 下载与 Chrome 版本匹配的 chromedriver =====
# 解析 Chrome 主版本，从 chrome-for-testing 的 known-good 列表里取对应 chromedriver-linux64 下载地址，
# 解压后放到 /usr/bin/chromedriver，保证运行期无需联网下载。
RUN set -eux; \
    CHROME_FULL_VER=$(google-chrome --version | awk '{print $3}'); \
    CHROME_MAJOR=$(echo "$CHROME_FULL_VER" | cut -d. -f1); \
    echo "Chrome version: $CHROME_FULL_VER (major=$CHROME_MAJOR)"; \
    JSON_URL="https://googlechromelabs.github.io/chrome-for-testing/known-good-versions-with-downloads.json"; \
    MATCH=$(curl -s "$JSON_URL" \
        | python3 -c "import sys,json; d=json.load(sys.stdin); vs=[v for v in d['versions'] if v['version'].split('.')[0]=='$CHROME_MAJOR']; v=vs[-1] if vs else None; print(v['downloads']['chromedriver'][0]['url'] if v else '')"); \
    if [ -z "$MATCH" ]; then echo "No matching chromedriver found"; exit 1; fi; \
    echo "chromedriver url: $MATCH"; \
    curl -sL "$MATCH" -o /tmp/chromedriver.zip; \
    unzip -o /tmp/chromedriver.zip -d /tmp/chromedriver; \
    cp /tmp/chromedriver/chromedriver-linux64/chromedriver /usr/bin/chromedriver; \
    chmod +x /usr/bin/chromedriver; \
    rm -rf /tmp/chromedriver /tmp/chromedriver.zip; \
    chromedriver --version

# ===== 3. Python 依赖 =====
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# ===== 4. 应用代码 =====
COPY *.py ./
COPY tools ./tools

# ===== 5. 运行时环境默认值 =====
ENV PYTHONUNBUFFERED=1 \
    PYTHONIOENCODING=utf-8 \
    PTA_HEADLESS=true \
    PTA_KEEP_BROWSER_OPEN_ON_FAILURE=false \
    PTA_CHROME_BINARY=/usr/bin/google-chrome \
    PTA_CHROMEDRIVER_PATH=/usr/bin/chromedriver \
    PTA_RUNTIME_DIR=/app/runtime \
    PTA_CRAWL_DIR=/app/output \
    PTA_BROWSER_HOME=/app/runtime/browser \
    SE_CACHE_PATH=/app/runtime/.selenium \
    SPIDER_PORT=8100

# 运行时目录由 volume 挂载，这里仅创建兜底
RUN mkdir -p /app/runtime /app/output

EXPOSE 8100

CMD ["python", "spider_api.py"]
