# 媒体平台指南（分卷索引）

> **适用版本:** `platform` `0.2.0-SNAPSHOT`、前端 `0.1.0`  
> **最后更新:** 2026-05-21

原《综合指南》已拆为分卷，按角色选读；细节专题文档见 [11-专题文档索引](11-doc-index.md)。

---

## 分卷目录

| 卷 | 文档 | 适合读者 |
|----|------|----------|
| 架构卷 | [01-架构原则](01-architecture.md) | 架构师、技术负责人 |
| 依赖卷 | [02-技术栈与依赖](02-dependencies.md) | 全员 onboarding |
| 结构卷 | [03-仓库与模块](03-codebase.md) | 后端 / 全栈 |
| 实现卷 | [04-核心实现](04-implementation.md) | 后端、渲染、AI |
| 前端卷 | [05-前端实现](05-frontend.md) | 前端 |
| 集成卷 | [06-集成矩阵](06-integration.md) | 平台集成、运维 |
| 配置卷 | [07-配置与 Profiles](07-configuration.md) | 开发、SRE |
| 部署卷 | [08-部署与数据](08-deployment.md) | SRE、发布 |
| 运维卷 | [09-安全与可观测](09-security-ops.md) | 安全、SRE |
| 演进卷 | [10-路线图](10-roadmap.md) | 产品、规划 |
| 索引 | [11-专题文档索引](11-doc-index.md) | 查阅既有专题 doc |

---

## 快速跳转

| 你想… | 去读 |
|-------|------|
| 5 分钟理解系统形态 | [01-架构原则](01-architecture.md) |
| 本地跑通 | [08-部署与数据](08-deployment.md) §本地开发 + [faq.md](../faq.md) |
| 接 LiteLLM / R2 / Vault | [06-集成矩阵](06-integration.md) + [07-配置与 Profiles](07-configuration.md) |
| 接 Authentik 登录 / OIDC | [authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md) + [08-部署](08-deployment.md) |
| 增量渲染 / AI 改时间线 | [04-核心实现](04-implementation.md) + 专题 [incremental-rendering.md](../incremental-rendering.md) |
| 生产上线清单 | [08-部署与数据](08-deployment.md) + [deployment.md](../deployment.md) |
| 为什么 `/forbidden` | [05-前端实现](05-frontend.md) §导航 + [faq.md](../faq.md) |

---

## 与旧版 `architecture.md` 的关系

- [architecture.md](../architecture.md)：简图、数据流、Provider 路由（保持轻量）。
- 本分卷 **01** 讲原则与取舍；实现细节在 **04–05**；部署在 **08**。

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-21 | 由 `platform-guide.md` 拆分为分卷 |
| 2026-05-21 | 增补 Authentik OIDC Resource Server 专题交叉引用 |
