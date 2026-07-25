# 启动说明

## 本地启动

```powershell
cd H:\CQUST_AI\spider-repo
.\.venv\Scripts\python.exe spider_api.py
```

服务地址：

```text
http://127.0.0.1:8100
```

健康检查：

```text
http://127.0.0.1:8100/health
```

## 云服务器 Docker 启动

云服务器没有图形界面，容器默认使用 headless Chrome。推荐在服务器上直接构建镜像，Dockerfile 会从 Debian 镜像安装 Chromium 和匹配的 ChromeDriver，并使用国内 Debian APT 与 PyPI 镜像加速构建。

```bash
cd /opt/pta-spider
cp .env.example .env
vim .env
docker compose up -d --build
docker compose logs -f pta-spider
```

默认构建参数如下，可在 `.env` 中覆盖：

```env
PYTHON_BASE_IMAGE=python:3.11-slim-bookworm
DEBIAN_MIRROR=mirrors.aliyun.com
PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
```

若 Docker Hub 不可访问，可将 `PYTHON_BASE_IMAGE` 改为可用的国内基础镜像代理，例如 `docker.m.daocloud.io/library/python:3.11-slim-bookworm`。

需要确认镜像源实际生效时，可执行：

```bash
docker compose build --no-cache --progress=plain pta-spider
```

检查 Chrome 和驱动：

```bash
docker compose exec pta-spider chromium --version
docker compose exec pta-spider chromedriver --version
```

健康检查：

```bash
curl http://127.0.0.1:8100/health
```

## 长期运行建议

- 不要把本机 Windows Chrome 复制进容器；Windows 程序不能在 Linux 容器里运行。
- 让云服务器能够访问 `.env` 中配置的 Debian APT、PyPI 和 Docker 基础镜像源，这样构建时可以安装 Chromium 和匹配驱动。
- 如果服务器网络访问镜像源失败，切换 `DEBIAN_MIRROR`、`PIP_INDEX_URL` 或 `PYTHON_BASE_IMAGE` 后重新 `docker compose build`。
- `docker-compose.yml` 已把 `./runtime` 挂载到容器内 `/app/runtime`，cookie、手动 cookie 和 Selenium 运行缓存会保留，容器重建后不会丢。
- 如果 PTA 自动登录遇到滑块验证码失败，在前端或接口写入手动 cookie：`POST /cookie/update`，请求体传浏览器导出的 cookie JSON 数组。
- 用户组答卷导出遇到 COS `404` 时会重新创建导出任务并重试；默认重试仍失败会记录警告并继续同步成绩单、提交记录和得分代码。若必须要求答题卡完整，可设置 `PTA_GROUP_ANSWER_EXPORT_REQUIRED=true`。

## 更新容器并验收

代码更新后只替换爬虫容器即可，以下命令不会删除已挂载的 `runtime` 和 `output` 数据：

```bash
docker compose build pta-spider
docker compose up -d --force-recreate pta-spider
docker compose logs -f pta-spider
```

确认健康检查正常后，在教师端选择对应班级并开启“强制同步”执行一次任务。完成后任务应显示“完成（部分数据缺失）”或“完成”；若答题卡导出仍失败，警告中会明确说明，成绩单、提交记录和得分代码仍应继续入库。
