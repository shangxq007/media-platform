# 07 · 配置与 Spring Profiles

> [← 分卷索引](README.md) | 上一卷：[06-集成矩阵](06-integration.md) | 下一卷：[08-部署与数据](08-deployment.md)

---

## 配置文件位置

```
platform/platform-app/src/main/resources/
├── application.yml           # 默认（含 dev active）
├── application-dev.yml
├── application-prod.yml      # 若存在
├── application-ai.yml
├── application-litellm.yml
├── application-r2.yml
├── application-vault.yml
├── application-temporal.yml
└── application-*.yml
```

模块级配置也可能在各自 `*-module/src/main/resources/`。

---

## 常用 Profiles

| Profile | 用途 |
|---------|------|
| `dev` | H2、安全关闭、占位 OpenAI key、本地友好项 |
| `prod` | PostgreSQL、Flyway、安全开启 |
| `litellm` | AI → LiteLLM Proxy |
| `ai` | AI Bean 与 routing |
| `r2` | Cloudflare R2 S3 兼容 |
| `vault` | Vault 客户端 |
| `temporal` | Worker + `render.execution.mode=temporal` |
| `natron-worker` | Natron worker |

---

## 启动示例

```bash
# 最小本地
./gradlew :platform-app:bootRun

# 全栈集成（按需补环境变量）
SPRING_PROFILES_ACTIVE=dev,litellm,r2,vault,temporal \
  ./gradlew :platform-app:bootRun
```

`bootRun` 默认带 `dev`（见 `platform-app/build.gradle.kts`）。

---

## 关键环境变量（摘录）

| 变量 | 含义 |
|------|------|
| `SPRING_DATASOURCE_URL` | JDBC |
| `STORAGE_S3_ENABLED` | 远程对象存储 |
| `STORAGE_S3_COMPATIBILITY` | `generic` / `r2` |
| `STORAGE_S3_ENDPOINT` / `ACCESS_KEY` / `SECRET_KEY` | S3 连接 |
| `RENDER_CACHE_*` | 段缓存上传、清理、Webhook |
| `VAULT_ENABLED` / `VAULT_ADDR` / `VAULT_*` | Vault |
| `PLATFORM_ENV` | `dev` / `staging` / `prod`；驱动 Temporal namespace `media-platform-{env}` |
| `TEMPORAL_TARGET` / `TEMPORAL_NAMESPACE` | Temporal（显式 namespace 覆盖自动解析） |
| `TEMPORAL_FAIL_ON_MISSING_WORKER` | 生产 `true`：无 Worker 时启动失败 |
| `LITELLM_API_KEY` | LiteLLM |
| `OPENAI_API_KEY` | 直连或经 LiteLLM |
| `APP_JWT_SECRET` | 自签 JWT（迁移期 / dev；OIDC 全量后生产停用） |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Authentik issuer（Resource Server） |
| `APP_SECURITY_OAUTH2_AUDIENCE` | （可选）JWT `aud` 校验 |
| `VITE_OIDC_ISSUER` / `VITE_OIDC_CLIENT_ID` / `VITE_OIDC_REDIRECT_URI` | 前端 OIDC（见 `frontend/.env.oidc.example`） |
| `APP_SECURITY_OAUTH2_ENABLED` | 启用 OIDC Resource Server |
| `APP_SECURITY_OAUTH2_LEGACY_HMAC_JWT_ENABLED` | OIDC 模式下同时接受 dev 自签 JWT |
| `APP_SECURITY_OAUTH2_USER_ID_CLAIM` | 平台用户 ID claim（默认 `platform_user_id`，迁移 `user-1`） |

完整清单：[deployment.md](../deployment.md)、[vault-and-rustfs-setup.md](../vault-and-rustfs-setup.md)、[authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md)。

---

## AI 路由（`app.ai.routing`）

- 按 **capability** 映射 provider + model（如 `timeline-edit`）。
- 生产推荐仅配置 LiteLLM 端点，model 字符串与 LiteLLM `config.yaml` 的 `model_name` 一致。

见 [ai-gateway-architecture.md](../ai-gateway-architecture.md)。

---

## 渲染与缓存（`render.*`）

| 配置区 | 说明 |
|--------|------|
| `render.execution.mode` | `local` / `temporal` |
| `render.providers.*` | 各 Provider 开关 |
| `render.cache.*` | 远程缓存、清理、Webhook |
| `render.pipeline.dag` | 增量 DAG |

---

## 默认 dev 注意项

- `spring.flyway.enabled=false`（H2 内存）
- `app.security.enabled=false`
- `spring.graphql.enabled=false`（避免 GraphQL schema 启动问题）
- 部分 Provider（如 VapourSynth）在 `application-dev.yml` 中关闭
