# 插件扩展点策略

平台保留 **PF4J** 扩展契约，但 Phase 0 生产分发仅允许构建时嵌入、由平台控制提取的插件包。`app.extensions.plugins-dir` 和外部 `--plugins-dir` 不构成生产加载权限；任意外部目录均被拒绝。核心业务保留在 Gradle 模块内。

## 适合做成插件

| 扩展点 | 说明 | 内置回退 |
|--------|------|----------|
| `RenderProvider` | FFmpeg、GStreamer、GPAC、MLT、JavaCV、OFX | `noop` / `mock`（test） |
| `PaymentProvider` | Stripe、Hyperswitch 等 | 未配置时返回 typed unavailable |
| `NotificationDeliveryProvider` | Novu、邮件、短信、Webhook | 未配置时失败关闭 |
| `AiChatProvider` | OpenAI、Anthropic、本地模型 | `UnavailableChatProvider`（fail-closed） |
| `SocialPlatformPublisher` | YouTube、TikTok、Instagram | 未配置时返回 typed unsupported |
| `StorageBackend` | S3、GCS、MinIO | 目录/catalog 模式 |
| `CloudResourceProvisioner` | 云资源开通钩子 | 空操作 |
| `ExtensionTool` | 用户脚本、沙箱工具（已支持 PF4J） | — |

## 保留在核心模块

- 身份、租户/工作空间 RBAC、Flyway Schema
- 权益决策、配额计量、Commerce 编排
- GraphQL 聚合、NLQ 元数据（库表存储）
- Feature Flag 引擎（DB 持久化 + 可选 Unleash 远程）
- Outbox、审计、调度

## 插件分发

当前仅发布 `media-platform-all-in-one.jar` 的内部 `embedded-plugins/` 包。构建会校验生产者 JAR 与嵌入字节的 SHA-256 一致性；启动器把内部包提取到受控临时目录，关闭 host 后删除。外部、可写或未列入平台构建的目录均不能加载或启动插件。

实现类使用 PF4J `@Extension` 注解，并暴露与核心 `spi` 包兼容的接口。详见 `extension-module/.../spi/PlatformPluginPoints.java`。
