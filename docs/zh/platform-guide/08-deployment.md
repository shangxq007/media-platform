# 08 · 部署与数据

> [← 分卷索引](README.md) | 上一卷：[07-配置与 Profiles](07-configuration.md) | 下一卷：[09-安全与可观测](09-security-ops.md)

---

## 本地开发（三终端）

```bash
# 终端 1（可选：dev 默认 H2 可不启）
cd platform && docker compose up -d db

# 终端 2
cd platform && ./gradlew :platform-app:bootRun

# 终端 3
cd platform/frontend && npm install && npm run dev
```

| 访问 | URL |
|------|-----|
| 前端 Vite | http://localhost:3000 或 3001 |
| 后端 | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |

请求头：`X-Tenant-ID: tenant-1`（前端自动）。

---

## Docker Compose（简化）

`platform/docker-compose.yml`：

- `db`：Postgres 16
- `app`：`SPRING_PROFILES_ACTIVE=prod` + JDBC 指向 db

```bash
cd platform && docker compose up --build
```

生产完整拓扑还需 **RustFS/Vault/Temporal/LiteLLM/Authentik** 等，见 [deployment.md](../deployment.md) 检查清单。

### 身份与登录（Authentik + OIDC）

生产采用 **OAuth2 Resource Server**：浏览器经 Authentik（Authorization Code + PKCE）获取 Access Token，API 用 **JWKS** 验签；MCP 仍用 API Key。

| 项 | 文档 |
|----|------|
| 自托管 Authentik、OIDC Application、Claim、环境变量 | [authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md) |
| Resource Server vs BFF 选型依据 | 同上 §1 |
| 部署勾选项 | [deployment.md](../deployment.md) §Authentik |

---

## 前端嵌入单包

```bash
cd platform/frontend && npm run build
cd platform && ./gradlew :platform-app:bootJar
```

静态资源在 `platform-app/src/main/resources/static/`，单端口 **8080** 同时提供 API + SPA。

---

## 数据迁移（Flyway）

| 环境 | 行为 |
|------|------|
| dev | 通常 `flyway.enabled=false`，H2 内存 |
| prod | Postgres + `flyway.enabled=true` |

脚本目录：`platform-app/src/main/resources/db/migration/`（V1 基线 + Vn 增量）。

可选 jOOQ：`platform/docs/jooq-workflow.md`。

---

## 回滚

| 对象 | 文档 |
|------|------|
| 应用 jar / 镜像 | [deployment.md](../deployment.md) §后端回滚 |
| 前端静态 | deployment §前端回滚 |
| Flyway | deployment §数据库（谨慎） |

---

## 发布前质量门禁

```bash
./gradlew clean test
cd platform/frontend && npm run build && npm run test
docker compose -f platform/docker-compose.yml config
```

对照 [deployment.md](../deployment.md) 检查清单逐项勾选。
