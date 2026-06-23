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

## Docker 启动

```bash
docker compose up -d --build
docker compose logs -f pta-spider
```

Docker 镜像内已经包含 Linux 版 Google Chrome 和对应 ChromeDriver，不需要把本机 Windows Chrome 复制进去。

如果服务器无法联网构建镜像，可以在本地构建：

```bash
docker compose build
docker save pta-spider:latest -o pta-spider.tar
```

上传到服务器后：

```bash
docker load -i pta-spider.tar
docker compose up -d
```
