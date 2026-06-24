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

云服务器没有图形界面，容器默认使用 headless Chrome。推荐在服务器上直接构建镜像，Dockerfile 会安装 Linux 版 Google Chrome，并下载与 Chrome 主版本匹配的 ChromeDriver。

```bash
cd /opt/pta-spider
cp .env.example .env
vim .env
docker compose up -d --build
docker compose logs -f pta-spider
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
- 让云服务器能够访问 Debian 官方 apt 源，这样构建时可以安装 Chromium 和匹配驱动。
- 如果服务器网络访问 Debian 源失败，优先配置服务器出网、代理或可用镜像源，再重新 `docker compose build`。
- `docker-compose.yml` 已把 `./runtime` 挂载到容器内 `/app/runtime`，cookie、手动 cookie 和 Selenium 运行缓存会保留，容器重建后不会丢。
- 如果 PTA 自动登录遇到滑块验证码失败，在前端或接口写入手动 cookie：`POST /cookie/update`，请求体传浏览器导出的 cookie JSON 数组。
