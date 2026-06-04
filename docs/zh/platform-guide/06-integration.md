# 06 · 集成矩阵

> [← 分卷索引](README.md) | 上一卷：[05-前端实现](05-frontend.md) | 下一卷：[07-配置与 Profiles](07-configuration.md)

---

## 已集成（有代码 + 文档）

| 集成对象 | 集成方式 | Profile / 开关 | 专题文档 |
|----------|----------|----------------|----------|
| LiteLLM | Spring AI `base-url` | `litellm` | [ai-gateway-architecture.md](../ai-gateway-architecture.md) |
| OpenAI 兼容 | 同上或直连 | `ai` | ai-gateway-architecture |
| Stub AI | `StubChatProvider` | 默认 stub routing | [faq.md](../faq.md) |
| Cloudflare R2 | S3 + `compatibility=r2` | `r2` | [vault-and-rustfs-setup.md](../vault-and-rustfs-setup.md) §3.7 |
| RustFS / MinIO | S3 端点 | `STORAGE_S3_*` | vault-and-rustfs-setup |
| HashiCorp Vault | HTTP / AppRole | `vault` | [secrets-management.md](../secrets-management.md) |
| Temporal | gRPC 7233 | `temporal` | vault-and-rustfs-setup §4 |
| 远程 / GPU 渲染 | `remote-render-worker` | 预设 `gpu_*` | [architecture.md](../architecture.md) |
| GraphQL 联邦 | `federation-query-module` | 默认 `graphql.enabled=false` | — |
| Sentry / OpenReplay | SDK | 环境变量 | [monitoring-feedback.md](../monitoring-feedback.md) |
| MCP 媒体工具 | `McpMediaToolsController` | — | platform-app |
| **Authentik（OIDC）** | Resource Server + SPA PKCE | `APP_SECURITY_ENABLED` + issuer-uri | [authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md) |

---

## 推荐接入（需适配）

| 厂商 / 能力 | 建议路径 | 说明 |
|-------------|----------|------|
| NVIDIA NIM | LiteLLM → NIM OpenAI 端点 | 与 OpenAI 路径相同 |
| OpenRouter / Gemini | LiteLLM model 名 | 改 `app.ai.routing.*.model` |
| Replicate | HTTP / Temporal Activity | 异步 Prediction |
| Workers AI | 自定义 `ChatProvider` | 非 OpenAI 流时走 SPI |
| Stripe / Hyperswitch | `payment-module` | 当前 Noop |
| 其他 IdP（Keycloak 等） | 非默认 | — | 默认 Authentik；替换时保持 Resource Server 模型 |

---

## 插件扩展

- **PF4J：** `extension-module`
- **SPI：** Provider / Prompt / Workflow
- **ToolRegistry：** 外部二进制（如 Natron POC）

[dynamic-extension.md](../dynamic-extension.md)、`platform/docs/extension-plugins.md`

---

## REST 契约摘要（租户 · 增量 + AI）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `.../timeline/preview-internal` | 编辑器 JSON → Internal 1.0 |
| POST | `.../timeline/ai-edit` | AI 改时间线 |
| POST | `.../timeline/ai-proposals/{id}/adopt\|reject` | 人工确认 |
| POST | `.../render-jobs/incremental/plan` | 增量计划 |
| POST | `.../render-jobs/incremental/submit` | 提交渲染 |
| GET | `.../render-jobs/{id}/timeline` | 读取作业时间线 |

完整 schema：运行后 `/swagger-ui.html`、`/v3/api-docs`。

---

## 错误码

- 业务：`PlatformException` + `error-codes.json`（中英）。
- 导航：`NAV-404-HIDDEN`、`NAV-403-TIER` 等（见前端 [05-前端实现](05-frontend.md)）。
