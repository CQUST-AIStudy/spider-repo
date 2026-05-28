# 安全漏洞审计报告

**项目**: AI_Ds 智能教学辅助平台
**扫描日期**: 2026-05-28
**扫描范围**: 全栈四层 (Vue Frontend / Spring Boot Backend / Python Worker / Infrastructure)
**扫描轮次**: 2 轮 (R1 广度扫描 + R2 深度验证)

---

## 概览

| 指标 | 数量 |
|------|------|
| R1 基础发现 | 48 个 |
| R2 攻击链 | 8 条 (4 CRITICAL, 4 HIGH) |
| R2 代码路径验证 | 4 条 (全部 confirmed exploitable) |
| R2 跨层交互 | 4 个 (3 CRITICAL, 1 HIGH) |

### 严重程度分布

| 级别 | R1 | R2 攻击链 | R2 跨层 |
|------|-----|-----------|---------|
| CRITICAL | 4 | 4 | 3 |
| HIGH | 16 | 4 | 1 |
| MEDIUM | 24 | - | - |
| LOW | 4 | - | - |

---

## CRITICAL -- 立即修复 (系统级风险)

### C-1: Git 历史泄露全部密钥 (已确认可利用)

**攻击链**: Git Secret Leak -> Full System Takeover

**泄露的凭证**:
- `local.env.ps1`: JWT secret, OpenAI key, DashScope key, DeepL key, DB password, admin password
- `deploy/local/.env.local`: 同上 + MySQL root password
- `grading_worker/local_settings.py`: DashScope API key
- `application.yml:160`: JWT secret fallback 值

**影响**: 完全系统接管 -- 伪造 JWT 冒充任意用户(含 admin)、直接访问 MySQL/MinIO/Redis、滥用 AI API 产生费用、篡改成绩和学生数据

**修复**:
1. **立即轮换所有泄露的密钥** (DashScope, DeepSeek, DeepL, JWT)
2. `git rm --cached` 移除 git 追踪
3. 用 `git filter-repo` 或 BFG 清除历史
4. 重新设置所有数据库密码和 MinIO 凭证

**文件**: `local.env.ps1`, `deploy/local/.env.local`, `grading_worker/local_settings.py`, `AI_Ds/src/main/resources/application.yml:160`

---

### C-2: JWT 默认密钥可伪造任意 Token (已确认可利用)

**攻击链**: Hardcoded JWT Secret + No Token Expiry Enforcement

**漏洞**: `application.yml:160` 的 JWT secret 有硬编码 fallback `local_dev_only_jwt_secret_change_me_1234567890`，当 `JWT_SECRET` 环境变量未设置时使用。攻击者可用此密钥伪造 ADMIN 角色的 JWT token。

**调用链**:
```
application.yml:160 (secret fallback)
-> JwtService.java:21 (HMAC key from secret)
-> JwtService.java:27-35 (issue token with claims: uid, role, username)
-> JwtAuthFilter.java:33-34 (validate with same secret)
-> SecurityConfig.java:82 (/api/admin/** requires ADMIN role)
```

**修复**: 移除 fallback，要求必须设置 `JWT_SECRET` 环境变量，未设置时启动失败

---

### C-3: LeetCode 代码执行无沙箱 -- 容器 root 权限 RCE (已确认可利用)

**攻击链**: Unsandboxed Code Execution + Root Container -> Infrastructure Pivot

**漏洞**: `LeetCodeCodeExecutionEngine.java:44` 直接用 ProcessBuilder 执行用户代码，无 seccomp/AppArmor/Docker/nsjail 隔离。容器以 root 运行 (backend.Dockerfile 无 USER 指令)。

**攻击路径**:
```
POST /api/leetcode/run (任意学生)
-> LeetCodeCodeExecutionEngine.java:54 (写入临时文件)
-> ProcessBuilder.command("python", ...) (直接执行)
-> 6 秒内可: 读取 application.yml (密钥)、访问 MySQL/Redis/MinIO、横向移动
```

**影响**: 完全容器接管 -> 所有密钥泄露 -> 数据库/存储/缓存全部可访问 -> 可横向到其他容器

**修复**:
1. 使用 Docker 容器或 nsjail 沙箱执行用户代码
2. 容器添加非 root USER
3. 限制网络访问 (禁止访问内部服务)
4. 设置 cgroup 资源限制 (CPU/内存)

---

### C-4: 存储型 XSS + localStorage Token 窃取 (已确认可利用)

**攻击链**: Stored XSS + localStorage Token Theft -> Admin Impersonation

**漏洞位置** (4 处未消毒的 v-html):
| 文件 | 行号 | 问题 |
|------|------|------|
| `ResultBlock.vue` | 25, 39 | markdown-it `html: true` + v-html，无 DOMPurify |
| `SubmissionDetail.vue` | 183 | `formatTestResults` regex 转 HTML，无消毒 |
| `KnowledgeBase.vue` | 206 | `renderMarkdown` regex 转 HTML，无消毒 |
| `GeneratePPT.vue` | 114 | `formatSlideContent` regex 转 HTML，无消毒 |

**攻击路径**:
```
注入恶意 HTML/JS 到 AI 内容或提交数据
-> ResultBlock.vue v-html 渲染执行
-> localStorage.getItem('token') / localStorage.getItem('tap_token')
-> 外发到攻击者服务器
-> 用窃取的 JWT 访问 /api/admin/** 端点
```

**修复**:
1. 所有使用 v-html 的地方必须用 DOMPurify 消毒
2. ResultBlock.vue 的 markdown-it 设置 `html: false`
3. 考虑将 token 存储从 localStorage 迁移到 httpOnly cookie

---

## HIGH -- 尽快修复

### H-1: PTA 回调端点无需认证 (已确认可利用)

`SecurityConfig.java:92` -- `PUT /api/classes/*/pta-sync/callback` 设为 `permitAll()`，无 HMAC 验证。攻击者可枚举 classId 触发任意班级的学生导入。

### H-2: 明文密码比较回退

`LoginController.java:220-227` -- 非 bcrypt 密码用 `rawPassword.equals(storedPassword)` 比较。遗留用户的密码在数据库中以明文存储。

### H-3: 全局异常处理泄露内部信息

`RestExceptionHandler.java:27` -- `e.getMessage()` 直接返回给客户端，暴露数据库表名、文件路径、类名等。

### H-4: 前端硬编码 dummy token

`api/index.js:274` -- 登录后设置 token 为字面量 `'legacy_session'`，存储在 localStorage 中。

### H-5: Redis 无认证运行

`docker-compose.local.yml` -- Redis 无 `--requirepass`，端口映射到 0.0.0.0。

### H-6: 默认数据库密码

- `config.py:15` -- MySQL 默认密码 `'123456'`
- `config.py:35-36` -- MinIO 默认凭证 `'minioadmin'/'minioadmin'`
- `docker-compose.local.yml:12` -- MySQL root 默认 `'taproot'`

### H-7: Worker /rerank 端点无认证

`main.py:111` -- FastAPI /rerank 端点无 auth、无 rate limit，可被滥用为 DoS 向量。

### H-8: CORS 过于宽松

`SecurityConfig.java:28` -- 默认允许 `http://localhost:*`，可被本地恶意页面利用进行跨域请求。

---

## MEDIUM -- 计划修复

| ID | 漏洞 | 文件 |
|----|------|------|
| M-1 | JDBC URL 禁用 SSL (useSSL=false) | `application.yml:8` |
| M-2 | 512MB 上传限制可被 zip bomb 利用 | `application.yml:56` |
| M-3 | AES-GCM 加密密钥复用 JWT secret | `AesGcmTextEncryptor.java:24` |
| M-4 | Session 中存储完整 UserEntity 含密码哈希 | `LoginController.java:77` |
| M-5 | Actuator health 暴露详细系统信息 | `application.yml:119-120` |
| M-6 | ArXiv ID 未校验可能导致 SSRF | `ArxivFetchService.java:36` |
| M-7 | ZIP 解压无大小限制 (Zip Bomb) | `UploadFolderService.java:53` |
| M-8 | 容器以 root 运行 | `backend.Dockerfile:30` |
| M-9 | Nginx 缺少安全头 | `nginx.conf` |
| M-10 | mock 数据含测试凭证打包到生产 bundle | `mock/index.js:181` |
| M-11 | Dev profile 硬编码弱密码 fallback | `application-dev.yml:9` |
| M-12 | local_settings.py 通配符导入覆盖安全配置 | `config.py:108-111` |
| M-13 | VLM 客户端泄露原始异常详情 | `vlm_client.py:176` |
| M-14 | RabbitMQ 使用 guest/guest 默认凭证 | `docker-compose.local.yml:71-73` |
| M-15 | MinIO 使用 unpinned `latest` 镜像标签 | `docker-compose.prod.yml:38` |

---

## LOW -- 建议修复

| ID | 漏洞 | 文件 |
|----|------|------|
| L-1 | 生产构建默认开启 source maps | `vue.config.js` |
| L-2 | Redis 连接无密码无 TLS | `config.py:6-8` |
| L-3 | Scorer trace 日志存储原始 HTTP 错误 | `scorer.py:134` |
| L-4 | 所有种子用户共享同一密码哈希 | `user_schema.sql:14-18` |

---

## 攻击链总结 (R2)

### 4 条 CRITICAL 攻击链

1. **XSS -> Token 窃取 -> Admin 冒充** (HIGH likelihood)
   - 4 处未消毒的 v-html -> localStorage token 窃取 -> JWT 伪造 admin

2. **JWT 默认密钥 -> 完全认证绕过** (HIGH likelihood)
   - 硬编码 secret -> 伪造任意角色 token -> 访问所有端点

3. **LeetCode RCE -> 容器接管 -> 基础设施沦陷** (HIGH likelihood)
   - 无沙箱代码执行 -> root 权限 -> 读取所有密钥 -> 访问 MySQL/Redis/MinIO

4. **Git 密钥泄露 -> 完全系统接管** (HIGH likelihood)
   - .env.local + local_settings.py 提交到 git -> 所有 API key + JWT secret + DB 密码泄露

### 4 条 HIGH 攻击链

5. PTA 回调 + 明文密码 -> 数据篡改 + 凭证泄露
6. Actuator 暴露 + 异常泄露 -> 完整基础设施侦察
7. Zip Bomb + 512MB 上传 -> 磁盘耗尽 DoS
8. CORS 通配符 + XSS -> 跨域 Token 窃取

---

## 跨层交互总结 (R2)

| 交互 | 层 | 严重程度 |
|------|-----|----------|
| 前端 XSS -> 后端 Auth 绕过 | Frontend + Backend | CRITICAL |
| 后端 RCE -> 基础设施横向移动 | Backend + Infra | CRITICAL |
| Git 密钥泄露 -> 全栈沦陷 | All 4 Layers | CRITICAL |
| Worker 无认证 -> 成绩投毒 | Worker + Backend | HIGH |

---

## 修复优先级

### 紧急 (本周)
1. 轮换所有泄露的 API 密钥和 JWT secret
2. 清除 git 历史中的密钥
3. LeetCode 代码执行添加沙箱
4. 所有 v-html 添加 DOMPurify 消毒

### 高优 (两周内)
5. 移除 JWT secret fallback
6. PTA 回调添加认证
7. Redis 添加密码
8. 异常处理不返回 e.getMessage()
9. 容器添加非 root USER

### 计划 (一个月内)
10. Nginx 安全头
11. Actuator 限制访问
12. 上传大小限制
13. 其余 MEDIUM 修复
