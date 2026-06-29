# 生产安全门禁

> **实现:** `com.example.platform.production.ProductionSafetyValidator`  
> **开关:** `platform.runtime.production-checks-enabled=true`（`application-prod.yml` 默认开启）

## 启动时强制项（prod）

| 检查 | 要求 |
|------|------|
| Profile | 不得激活 `dev` |
| 安全 | `app.security.enabled=true` |
| 认证 | OIDC 或已批准的 legacy JWT 路径 |
| 数据库 | `spring.datasource.url` 为 PostgreSQL（禁止 H2） |
| 迁移 | `spring.flyway.enabled=true` |
| 支付 | Stripe 或 Hyperswitch 至少一项 `enabled=true` |
| AI | `app.ai.default-provider` 不得为 stub |
| Feature Flag | 存在 `FeatureFlagJdbcStore` Bean（启动时从 DB 加载） |
| 商务结账 | 存在 `CheckoutSessionRepository` Bean（JDBC 权威） |
| 购物车 | 存在 `CommerceCartRepository` Bean |
| 订阅计费 | 存在 `SubscriptionJdbcRepository` Bean |

## 推荐环境变量

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://...
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...
export APP_SECURITY_OAUTH2_ENABLED=true
export APP_SECURITY_OAUTH2_ISSUER_URI=https://...
export PLATFORM_PAYMENT_STRIPE_ENABLED=true
export APP_AI_DEFAULT_PROVIDER=openAiChatProvider
```

## Readiness

- `PlatformReadinessHealthIndicator`：数据库连通 + Flyway 无 pending。
- prod 将 `platformReadiness` 纳入 `management.endpoint.health.group.readiness`。

## P2X.0 Scenario Runner Safety

P2X.0 introduced an internal API/Agent Scenario Runner. The runner is pure and side-effect-free:
- Does not execute FFmpeg
- Does not call OpenCue
- Does not create RenderJob or Product
- Does not call StorageRuntime or ProductRuntime
- Does not expose public APIs
- Does not use Artifact DAG
- Does not persist scenario results
- Does not expose filter_complex, raw commands, or provider internals

## P2B.0 Provider Capability Binding DSL Safety

P2B.0 introduced the Provider Capability Binding DSL design. The DSL is declarative and safe by default:
- DSL is not an execution language
- DSL is not a scripting language
- DSL does not generate raw commands
- DSL does not expose provider internals
- DSL does not define OpenCue jobs
- DSL is future work, not runtime-integrated
- DSL forbids: shell commands, filtergraphs, scripts, Remotion components, Blender scripts, Natron graphs, OpenCue job definitions, storage internals, ProductRuntime internals
- ANTLR/JavaCC remain future-only, not adopted now
