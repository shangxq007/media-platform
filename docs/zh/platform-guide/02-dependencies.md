# 02 · 技术栈与依赖

> [← 分卷索引](README.md) | 上一卷：[01-架构原则](01-architecture.md) | 下一卷：[03-仓库与模块](03-codebase.md)

---

## 后端

| 组件 | 版本 / 说明 |
|------|-------------|
| Java | **25**（Gradle toolchain） |
| Spring Boot | **4.0.4** |
| Spring Modulith API | **2.0.4**（元数据 / 模块标注） |
| Spring AI BOM | **2.0.0-M3**（与 Boot 4 对齐，Milestone） |
| 持久化 | jOOQ + JDBC；**Flyway**（`platform-app/.../db/migration/`） |
| 本地默认库 | **H2** in-memory（`MODE=PostgreSQL`）；生产 **PostgreSQL 16+** |
| API 文档 | springdoc OpenAPI 3 → `/swagger-ui.html` |
| 安全 | Spring Security；dev profile 可关闭 JWT；`X-Tenant-ID` 租户头 |
| 工作流 | **Temporal**（可选 profile `temporal`）；**LiteFlow** 渲染 DAG |
| 媒体工具链 | FFmpeg、JavaCV、GPAC、MLT、GStreamer、OFX、Natron、Blender、Libass、Remotion 等（按 provider 启用） |

---

## 前端

| 组件 | 版本 / 说明 |
|------|-------------|
| React | **19** |
| Vite | **6** |
| TypeScript | **5.7** |
| 状态 | Zustand |
| 服务端状态 | TanStack Query |
| 路由 | TanStack Router + **可配置导航**（`NavigationDecisionService`） |
| HTTP | Axios → `/api/v1`（dev 代理到 `:8080`） |
| 视频合成 | Remotion 4 |
| 可选 | GraphQL（`federation-query-module`；默认 `spring.graphql.enabled=false`） |
| 构建输出 | `platform-app/src/main/resources/static/` |

---

## 基础设施（生产推荐）

| 服务 | 用途 | 专题文档 |
|------|------|----------|
| PostgreSQL | 主库、Flyway | [deployment.md](../deployment.md) |
| Redis | 缓存/限流（按环境） | deployment |
| **RustFS** 或 **S3/R2** | 渲染缓存、成片、预签名 | [vault-and-rustfs-setup.md](../vault-and-rustfs-setup.md) |
| **HashiCorp Vault** | 凭证、交付目的地密钥 | [secrets-management.md](../secrets-management.md) |
| **Temporal** | 长时渲染/交付工作流 | vault-and-rustfs-setup §4 |
| **LiteLLM** | 统一 LLM 网关（推荐） | [ai-gateway-architecture.md](../ai-gateway-architecture.md) |
| Sentry / OpenReplay | 错误与会话（可选） | [monitoring-feedback.md](../monitoring-feedback.md) |
| OTLP | Metrics/Tracing（可选） | `application.yml` `management.otlp` |

---

## 版本锁定位置

- 根构建：`platform/build.gradle.kts`（Boot `4.0.4`、Java 25）
- 前端：`platform/frontend/package.json`
- Docker 镜像：`platform/docker-compose.yml`（Postgres 16）

升级 Spring AI / Boot 前请跑全量 `./gradlew test` 与 `npm run build`。
