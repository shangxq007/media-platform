# 09 · 安全与可观测

> [← 分卷索引](README.md) | 上一卷：[08-部署与数据](08-deployment.md) | 下一卷：[10-路线图](10-roadmap.md)

---

## 安全与多租户

| 能力 | 现状 | 生产目标 |
|------|------|----------|
| 用户登录 / SSO | dev 自签 token 或关安全 | **Authentik OIDC**（自托管） |
| API 认证 | dev 关闭；自签 JWT / API Key | **Resource Server** 验 Authentik JWT；MCP 用 API Key |
| 租户上下文 | `TenantContextFilter` + `X-Tenant-ID` | JWT `tenantId` claim，禁止裸头提权 |
| 业务授权 | `identity-access` + `entitlement` | 组/角色映射 + 套餐 tier |
| 密钥 | Vault；避免 DB 明文 | `SECRETS_INLINE_CREDENTIALS_ENABLED=false` |
| 导航授权 | 路由 visible/enabled/tier | 与 entitlement 一致 |
| 时间线安全 | Schema 禁止降级 security / 滥用 cache | 见 Internal Timeline §16 |

### 身份架构（已选型）

- **方案：** OAuth2 **纯 Resource Server**（非 BFF 换票）。  
- **依据：** 多客户端（Web + MCP/API Key + 未来移动端）、无状态 API、与现有 JWT 过滤器演进一致；BFF 的 Cookie/会话优势不足以抵消 CSRF 与会话运维成本。  
- **部署与 Claim 映射：** [authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md)  
- **检查清单：** [deployment.md](../deployment.md) §Authentik  

分层示意见 [architecture.md](../architecture.md) §安全架构。

---

## 可观测性

| 能力 | 端点 / 工具 |
|------|-------------|
| 健康 | `/actuator/health` |
| 指标 | `/actuator/metrics`、`prometheus` |
| 链路 | OTLP（`management.otlp`，可选） |
| 日志 | `traceId`、`tenantId`、`projectId` |
| 前端 | Sentry、OpenReplay（可选） |
| 工作流 | Temporal UI（启用 temporal 时） |

专题：[monitoring-feedback.md](../monitoring-feedback.md)。

---

## 问题数据与审计

- 自动检测、隔离、修复 — [problematic-data.md](../problematic-data.md)
- Outbox 投递监控 — `outbox-event-module`、`platform/docs/outbox-reliability.md`

---

## 运维排错提示

| 现象 | 方向 |
|------|------|
| `/forbidden` + `NAV-404-HIDDEN` | [05-前端实现](05-frontend.md) §导航 |
| 8080 起不来 | 查日志：Flyway、GraphQL、循环依赖、Provider 环境校验 |
| AI 无响应 | Profile `litellm` / `OPENAI_API_KEY` / Stub |
| 增量未复用缓存 | `RENDER_CACHE_*`、`STORAGE_S3_ENABLED` |
