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
