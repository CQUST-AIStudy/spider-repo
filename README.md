# PTA Spider Service

这是 PTA 数据爬取与同步服务。服务本体是 FastAPI，爬取登录由 Selenium + Google Chrome 完成，登录成功后主要通过 PTA API 抓取数据并同步到数据库。

## 目录结构

```text
.
├── src/pta_spider/              # 正式业务代码
│   ├── spider.py                # PTA 爬虫核心
│   ├── spider_api.py            # FastAPI 服务入口
│   ├── sync_to_db.py            # 旧库同步逻辑
│   └── sync_to_unified_db.py    # 统一库同步逻辑
├── scripts/                     # 运维/一次性脚本
│   ├── capture_pta_cookie.py
│   ├── crawl_examinee_submissions.py
│   ├── maintenance/
│   └── debug/
├── tests/                       # 测试脚本
├── runtime/                     # cookie、日志、Selenium 缓存，本地生成，不提交
├── output/                      # 爬取结果，本地生成，不提交
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── spider.py                    # 兼容旧命令的包装入口
└── spider_api.py                # 兼容旧命令的包装入口
```

## 本地启动

```powershell
cd H:\CQUST_AI\spider-repo
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

编辑 `.env`，至少填写：

```env
PTA_USERNAME=你的PTA账号
PTA_PASSWORD=你的PTA密码
PTA_GROUP_ID=PTA用户组ID
# 或者 PTA_GROUP_NAME=PTA用户组精确名称

DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=ptadatabase
DB_USERNAME=root
DB_PASSWORD=数据库密码
JAVA_BACKEND_URL=http://127.0.0.1:8081
SPIDER_PORT=8100
```

启动 API：

```powershell
.\.venv\Scripts\python.exe spider_api.py
```

健康检查：

```powershell
Invoke-WebRequest http://127.0.0.1:8100/health
```

触发爬取：

```http
POST http://127.0.0.1:8100/crawl
Content-Type: application/json

{
  "group_id": "2028307022170722304",
  "class_id": 123,
  "mode": "full",
  "force": false
}
```

也可以使用用户组名称：

```json
{
  "group_name": "计科25数据结构",
  "class_id": 123,
  "mode": "full"
}
```

## Docker 部署

Dockerfile 已经把 Google Chrome Stable 和匹配的 ChromeDriver 放进镜像里：

- Google Chrome 安装到 `/usr/bin/google-chrome`
- ChromeDriver 安装到 `/usr/bin/chromedriver`
- 容器默认设置：
  - `PTA_CHROME_BINARY=/usr/bin/google-chrome`
  - `PTA_CHROMEDRIVER_PATH=/usr/bin/chromedriver`
  - `PTA_HEADLESS=true`

构建并启动：

```bash
cd /opt/pta-spider
cp .env.example .env
vim .env
docker compose up -d --build
```

查看日志：

```bash
docker compose logs -f pta-spider
```

健康检查：

```bash
curl http://127.0.0.1:8100/health
```

停止服务：

```bash
docker compose down
```

## 云服务器上的 Chrome 登录问题

云服务器没有图形界面，所以容器默认使用 headless Chrome。一般建议这样处理：

1. 优先用本地或服务器上已有有效 cookie。服务会把 cookie 存在 `runtime/`，`docker-compose.yml` 已把 `./runtime` 挂载到容器内 `/app/runtime`，容器重建后 cookie 不会丢。
2. 如果自动登录遇到滑块验证码失败，在前端或接口写入手动 cookie。接口是 `POST /cookie/update`，请求体里传浏览器导出的 cookie JSON 数组。
3. 不建议把你本机的 Chrome 程序复制进容器。Dockerfile 会在构建时安装 Linux 版 Google Chrome，并下载匹配 ChromeDriver；Windows 版 Chrome 不能在 Linux 云服务器容器里运行。
4. 如果云服务器网络无法访问 Google 下载源，可以先在能联网的机器构建镜像，再 `docker save` 导出镜像，上传服务器后 `docker load`。

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
PTA_CHROME_BINARY=/usr/bin/google-chrome
PTA_CHROMEDRIVER_PATH=/usr/bin/chromedriver
JAVA_BACKEND_URL=http://backend:8081
SPIDER_PORT=8100
SPIDER_CORS_ALLOW_ORIGINS=*
DB_HOST=mysql
DB_PORT=3306
DB_NAME=ptadatabase
DB_USERNAME=root
DB_PASSWORD=
```

## 注意事项

- `.env`、`runtime/`、`output/` 都不应提交到 Git。
- 云服务器部署时，`DB_HOST` 和 `JAVA_BACKEND_URL` 不要写 `127.0.0.1`，除非数据库和后端就在同一个容器里。Docker Compose 内部服务互访通常写服务名，例如 `mysql`、`backend`。
- PTA 登录有验证码和风控，首次部署建议先跑通 cookie 导入，再做定时爬取。
