# 核心基础设施模块

> **最后更新：** 2026-05-18

## shared-kernel

**状态：** ✅ 已实现

唯一的 `ApplicationModule.Type.OPEN` 模块。提供共享类型、事件和跨模块 SPI。

| 类别 | 类型 |
|------|------|
| 错误码 | `CommonErrorCode`、`ErrorCode`、`ErrorCodeRegistry` |
| 值对象 | `Ids`（UUID 生成）、`Jsons`（Jackson 包装） |
| 日志上下文 | `TraceKeys`（traceId、requestId、tenantId、projectId） |
| 基础异常 | `PlatformException`（带 ErrorCode + details） |
| 领域事件 | `RenderJobCreatedEvent`、`RenderJobStatusChangedEvent`、`ArtifactCreatedEvent`、`RenderJobCompletedEvent`、`RenderJobFailedEvent` |
| 跨模块 SPI | `NotificationEventPublisher` |

**依赖：** 无（依赖图根节点）

## platform-app

**状态：** ✅ 已实现

Spring Boot 应用入口。聚合全部 30 个模块。

| 组件 | 用途 |
|------|------|
| `PlatformApplication` | `@Modulith` + `@SpringBootApplication` 根类 |
| `ModularityTest` | 验证模块边界约束 |
| `GlobalExceptionHandler` | 全局异常 → ProblemDetail 映射 |
| `GlobalSentryExceptionHandler` | Sentry 异常捕获 |
| `RequestContextFilter` | 请求 ID 生成、MDC 填充 |
| `ApiKeyAuthFilter` | API Key 认证、TenantContext |
| OpenAPI 配置 | springdoc OpenAPI 3 配置 |
| 安全配置 | CORS、限流 |

**依赖：** 全部 29 个其他模块（扁平）

## config-module

**状态：** ✅ 已实现

版本化配置存储与检索。

| 功能 | 状态 |
|------|------|
| CRUD 操作 | ✅ |
| 版本控制 | ✅ |
| ConfigController | ✅ |

**依赖：** 无

## secrets-config-module

**状态：** ✅ 已实现

密钥引用管理。

| 功能 | 状态 |
|------|------|
| 密钥引用 CRUD | ✅ |
| 密钥解析 | ✅ |

**依赖：** `shared-kernel`

## datasource-module

**状态：** ✅ 已实现

命名 DataSource 和 jOOQ DSLContext 注册表，支持多数据源。

| 功能 | 状态 |
|------|------|
| 命名 DataSource 注册表 | ✅ |
| 命名 DSLContext 注册表 | ✅ |
| 联邦查询网关接口 | ✅ |

**依赖：** `shared-kernel`

## identity-access-module

**状态：** ✅ 已实现

身份与访问管理。

| 功能 | 状态 |
|------|------|
| 租户管理 | ✅ |
| 用户管理 | ✅ |
| API Key 管理 | ✅ |
| 项目管理 | ✅ |
| ApiKeyAuthFilter | ✅ |

**依赖：** `shared-kernel`

## scheduler-module

**状态：** ✅ 已实现

作业调度，支持 Cron 和手动触发。

| 功能 | 状态 |
|------|------|
| Cron 作业调度 | ✅ |
| 手动触发 | ✅ |
| 死信支持 | ✅ |

**依赖：** 无

## outbox-event-module

**状态：** ✅ 已实现

事务性 Outbox 模式，用于可靠的事件发布。

| 功能 | 状态 |
|------|------|
| Outbox 记录存储 | ✅ |
| 带重试的事件分发 | ✅ |
| 幂等支持 | ✅ |

**依赖：** `shared-kernel`
