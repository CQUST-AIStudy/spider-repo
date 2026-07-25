# PTA Spider

PTA 数据爬取与同步服务。服务本体是 FastAPI，登录由 Selenium + Google Chrome 完成；登录成功后主要通过 PTA API 抓取用户组授权的实验、提交记录、成绩单和代码，并同步到数据库或 Java 后端。

## 项目结构

```text
.
├── src/pta_spider/
│   ├── spider.py              # PTA 登录、用户组实验解析、数据抓取
│   ├── spider_api.py          # FastAPI 服务与同步任务队列
│   ├── sync_to_db.py          # 旧库同步
│   └── sync_to_unified_db.py  # 统一库同步
├── scripts/
│   ├── capture_pta_cookie.py
│   ├── crawl_examinee_submissions.py
│   ├── debug/
│   └── maintenance/
├── tests/
├── runtime/                   # cookie、日志、Selenium 缓存，本地生成，不提交
├── output/                    # 爬取产物，本地生成，不提交
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

## 同步策略

- 教师端按教学班配置 PTA 用户组名或用户组 ID。
- 爬虫不再按 PTA 关键词模糊搜索实验列表，而是读取用户组授权的实验链接，再按班级绑定的实验 ID/名称做精确过滤。
- 未截止实验默认 24 小时同步一次。
- 已截止实验如果数据库已有数据，默认跳过；如果缺数据，会继续补抓。
- 教师强制同步会跳过后端和爬虫的冷却限制。

## 本地运行

```powershell
cd H:\CQUST_AI\spider-repo
python -m pip install -r requirements.txt
python spider_api.py
```

健康检查：

```bash
curl http://127.0.0.1:8100/health
```

触发爬取：

```bash
curl -X POST http://127.0.0.1:8100/crawl \
  -H "Content-Type: application/json" \
  -d '{
    "group_name": "计科23数据结构",
    "class_id": 123,
    "mode": "full",
    "force": true
  }'
```

兼容旧测试字段：

```json
{
  "keyword": "计科23数据结构",
  "classId": 123,
  "mode": "FULL"
}
```

## Docker 部署

Dockerfile 会在镜像构建阶段从 Debian 官方源安装 Linux 版 Chromium 与版本匹配的 ChromeDriver，避免依赖 Google 下载源：

- Chromium: `/usr/bin/chromium`
- ChromeDriver: `/usr/bin/chromedriver`
- 默认 headless: `PTA_HEADLESS=true`

云服务器上直接构建并启动：

```bash
cd /opt/pta-spider
cp .env.example .env
vim .env
docker compose up -d --build
docker compose logs -f pta-spider
```

检查浏览器版本：

```bash
docker compose exec pta-spider chromium --version
docker compose exec pta-spider chromedriver --version
```

健康检查：

```bash
curl http://127.0.0.1:8100/health
```

停止服务：

```bash
docker compose down
```

## 云服务器 Chrome 与 Cookie

云服务器通常没有图形界面，所以容器默认使用 headless Chrome。长期爬取建议这样处理：

1. 让云服务器能够访问 Debian 官方 apt 源，直接在服务器上 `docker compose up -d --build` 构建镜像。
2. 不要把本机 Windows Chrome 复制进容器；Windows 版 Chrome 不能在 Linux 云服务器容器里运行。
3. cookie 会保存在 `runtime/`，`docker-compose.yml` 已把宿主机 `./runtime` 挂载到容器内 `/app/runtime`，容器重建后 cookie 不会丢。
4. 如果自动登录遇到滑块验证码失败，在前端或接口写入手动 cookie。接口是 `POST /cookie/update`，请求体传浏览器导出的 cookie JSON 数组。
5. 如果服务器无法访问 Debian 官方 apt 源，优先配置服务器出网、代理或可用镜像源，再重新构建镜像。

## 常用环境变量

```env
PTA_USERNAME=
PTA_PASSWORD=
PTA_GROUP_ID=
PTA_GROUP_NAME=
PTA_HEADLESS=true
PTA_FORCE_SELENIUM_LOGIN=false
PTA_RUNTIME_DIR=/app/runtime
PTA_CRAWL_DIR=/app/output
PTA_CHROME_BINARY=/usr/bin/chromium
PTA_CHROMEDRIVER_PATH=/usr/bin/chromedriver
JAVA_BACKEND_URL=http://host.docker.internal:8081
SPIDER_PORT=8100
SPIDER_CORS_ALLOW_ORIGINS=*
COOLDOWN_SUBMISSIONS=86400
COOLDOWN_EXPORTS=86400
# 爬取吞吐（激进默认；遇 429 可自行下调）
PTA_API_RATE_LIMIT_PER_MINUTE=60
PTA_API_RATE_LIMIT_MIN=10
PTA_DETAIL_MAX_WORKERS=12
PTA_PROBLEM_SET_MAX_WORKERS=3
PTA_EXPORT_POLL_INTERVAL_SECONDS=1.0
PTA_EXPORT_CREATE_DELAY_SECONDS=0.5
PTA_EXPORT_BETWEEN_DELAY_SECONDS=0.5
PTA_EXPORT_PARALLEL=true
PTA_EXPORT_RETRY_ROUNDS=2
PTA_EXPORT_RETRY_DELAY_SECONDS=20
PTA_GROUP_ANSWER_EXPORT_REQUIRED=false
DB_HOST=host.docker.internal
DB_PORT=3306
DB_NAME=ptadatabase
DB_USERNAME=root
DB_PASSWORD=
```

## 爬取速度说明

默认已开启较激进的吞吐配置：

- **自适应 API 限流**：稳态目标 60 次/分钟；遇到 429 自动降半到 `PTA_API_RATE_LIMIT_MIN`，连续成功后再缓慢回升。
- **题集并行**：同一任务内最多 `PTA_PROBLEM_SET_MAX_WORKERS` 个题目集并行处理内容/提交/导出。
- **导出并行**：同题集内成绩单与得分代码可并行（`PTA_EXPORT_PARALLEL=true`），轮询间隔可配置。

若频繁 429/403 或触发 PTA 风控，建议回退示例：

```env
PTA_API_RATE_LIMIT_PER_MINUTE=20
PTA_PROBLEM_SET_MAX_WORKERS=1
PTA_EXPORT_PARALLEL=false
PTA_EXPORT_POLL_INTERVAL_SECONDS=3
PTA_EXPORT_CREATE_DELAY_SECONDS=3
```

用户组答卷导出说明：

- 用户组答卷下载使用独立的 PTA 导出任务和临时 COS URL；下载遇到 404、429 或 5xx 时会重新创建导出任务并重试。
- 默认 `PTA_GROUP_ANSWER_EXPORT_REQUIRED=false`。重试仍失败时，服务会记录警告并继续同步成绩单、提交记录和得分代码；答题卡相关证据可能缺失。
- 如果业务要求答题卡必须完整导出，可设置 `PTA_GROUP_ANSWER_EXPORT_REQUIRED=true`，此时重试失败会使整个同步任务失败。

## 注意事项

- `.env`、`runtime/`、`output/` 都不应提交到 Git。
- 云服务器部署时，`DB_HOST` 和 `JAVA_BACKEND_URL` 不要写 `127.0.0.1`，除非数据库和后端就在同一个容器里。单服务 compose 访问宿主机可用 `host.docker.internal`；统一根目录 compose 内部服务互访使用服务名，例如 `backend`。
- PTA 登录有验证码和风控，首次部署建议先跑通 cookie 导入，再做定时爬取。
- 任务队列仍为单 worker（不同教学班任务串行），避免同账号多任务抢限流。
