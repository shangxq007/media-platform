# Media Platform

Media Build Platform — low-level reusable media capabilities for AI video production, rendering orchestration, and media asset management.

**Status:** Platform Kernel Baseline 1.0 is FROZEN. See [Platform Constitution](docs/architecture/platform-constitution-v1.md).

## Architecture Overview

```
Applications → Workflow → Creative Planning → Timeline → Capability Runtime → Kernel → Infrastructure
```

- 7 Stable Public SPIs (Producer, BackendCompiler, ExecutionEnvironment, ExecutionBackend, StorageProvider, AccessGovernance, Metering)
- 10 Kernel Invariants enforced
- 4 Architecture Validations passed (OpenCue, Storage, Whisper, Remotion)

## Documentation Entry Point

**[Documentation Index](docs/documentation/documentation-index.md)** — Single entry point for all documentation.

**New contributors start here:** [Reading Guide](docs/handoff/reading-guide.md)

## Quick Start

```bash
docker compose up -d db
./gradlew :platform-app:bootRun   # 默认 spring.profiles.active=dev（本地免 JWT）
cd frontend && npm install && npm run dev
```

生产环境请使用 **`SPRING_PROFILES_ACTIVE=prod`**（勿带 `dev`），配置 PostgreSQL 与 OIDC。  
启动门禁见 [docs/production-safety.md](docs/production-safety.md)。

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| 语言 | Java | 25 |
| 框架 | Spring Boot | 4.0.4 |
| 模块化 | Spring Modulith | 2.0.4 |
| 工作流 | Temporal + LiteFlow | 1.33.0 / 2.15.3.2 |
| 数据库 | PostgreSQL | 16 |
| ORM | jOOQ | 3.19.18 |
| 前端 | React 19 + Vite 6 + TypeScript 5.7 | — |
| 前端状态 | Zustand + TanStack Query + TanStack Router | — |
| 视频合成 | Remotion 4 | — |
| 特性开关 | OpenFeature | — |

## 模块结构

35 个 Gradle 子模块，入口为 `platform-app`。详见 `docs/modules/`。

## 数据库（Flyway）

Schema 统一在：

`platform-app/src/main/resources/db/migration/`

| 脚本 | 内容 |
|------|------|
| V1 | 完整 baseline（133 张表，2339 行 DDL） |

当前 **1** 个中央 Flyway 脚本（V1 合并 baseline）。模块内 Bootstrap 仅作 JDBC→内存 **hydrate**，不替代 DDL。  
新增迁移脚本见 [docs/operations/flyway-baseline-runbook.md](docs/operations/flyway-baseline-runbook.md)。  
Modulith 违规预算见 [docs/modulith-debt-register.md](docs/modulith-debt-register.md)。

## 核心能力

- **渲染管线** — 7+ 提供商（FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, Natron），状态机，增量渲染，缓存
- **计费与支付** — 订阅生命周期，用量计费，信用钱包，Stripe + Hyperswitch 真实集成
- **权限与身份** — JWT + OIDC + API Key，RBAC，多租户隔离
- **工作流编排** — Temporal 持久化工作流 + 本地回退
- **内容交付** — 6 协议适配器（S3Mirror, SFTP, SMB, WebDAV, HTTPS PUT）
- **特性开关** — OpenFeature + JDBC 持久化，百分比灰度
- **审计合规** — jOOQ 审计记录，异常检测，安全告警

## 插件扩展

见 [docs/extension-plugins.md](docs/extension-plugins.md)。

## 文档

- 文档中心：[docs/README.md](docs/README.md)
- 中文平台指南：`docs/zh/platform-guide/README.md`
- 项目情报报告：`docs/review/project-intelligence-report.md`
- 已知限制：`docs/review/known-limitations.md`
- Project Export/Import：`docs/media-rendering/project-export.md`

## 测试

```bash
./gradlew test
./gradlew :platform-app:test --tests "com.example.platform.FlywaySchemaIntegrationTest"  # 需 Docker
./gradlew :platform-app:test --tests "com.example.platform.ModularityTest"               # 模块边界验证
cd frontend && npx vitest run
```
