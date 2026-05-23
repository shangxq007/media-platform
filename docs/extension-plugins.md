# 插件扩展点策略

平台使用 **PF4J**（`extension-module`）加载 `app.extensions.plugins-dir` 目录下的 JAR。核心业务保留在 Gradle 模块内；**易变、供应商相关、依赖重型原生库** 的能力优先做成插件。

## 适合做成插件

| 扩展点 | 说明 | 内置回退 |
|--------|------|----------|
| `RenderProvider` | FFmpeg、GStreamer、GPAC、MLT、JavaCV、OFX | `noop` / `mock`（test） |
| `PaymentProvider` | Stripe、Hyperswitch 等 | `Noop` 提供商 |
| `NotificationDeliveryProvider` | Novu、邮件、短信、Webhook | 日志 Noop |
| `AiChatProvider` | OpenAI、Anthropic、本地模型 | `StubChatProvider` |
| `SocialPlatformPublisher` | YouTube、TikTok、Instagram | Stub 适配器 |
| `StorageBackend` | S3、GCS、MinIO | 目录/catalog 模式 |
| `CloudResourceProvisioner` | 云资源开通钩子 | 空操作 |
| `ExtensionTool` | 用户脚本、沙箱工具（已支持 PF4J） | — |

## 保留在核心模块

- 身份、租户/工作空间 RBAC、Flyway Schema
- 权益决策、配额计量、Commerce 编排
- GraphQL 聚合、NLQ 元数据（库表存储）
- Feature Flag 引擎（DB 持久化 + 可选 Unleash 远程）
- Outbox、审计、调度

## 插件目录

默认：`./plugins`（可通过 `app.extensions.plugins-dir` 配置）。

实现类使用 PF4J `@Extension` 注解，并暴露与核心 `spi` 包兼容的接口。详见 `extension-module/.../spi/PlatformPluginPoints.java`。
