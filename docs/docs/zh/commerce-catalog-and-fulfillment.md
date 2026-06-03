# 商品目录与结账履约

> 索引：[docs/zh/README.md](README.md) · 路线图：[platform-guide/10-roadmap.md](platform-guide/10-roadmap.md)

## 设计原则

```text
Catalog 定义可售组合 → Billing 记录合同与计量 → Entitlement 合并后裁决访问
支付成功只驱动状态迁移，不直接当权限真相
```

## 模块边界

| 模块 | 职责 |
|------|------|
| `commerce-module` | 平台自有 SKU（`CanonicalProduct`）、结账会话、订单事件 |
| `billing-module` | 订阅合同（BASE / ADD_ON）、钱包充值、合并配额 |
| `entitlement-module` | tier、grant、工作区席位池 |
| `platform-app` | `PurchaseFulfillmentService` 实现 `PurchaseFulfillmentPort` |

## 商品行类型（`ProductLineType`）

| 类型 | 含义 | 履约动作 |
|------|------|----------|
| `BASE_SUBSCRIPTION` | 主套餐月订 | `setTier` + BASE 合同（替换旧 BASE） |
| `ADD_ON_SUBSCRIPTION` | 增值包月订 | ADD_ON 合同 + `EntitlementGrant` |
| `CREDIT_PACK` | 预付费加油包 | `CreditWalletService.credit` |
| `SEAT_PACK` | 席位/池扩容 | `WorkspaceEntitlementPool`（首期创建池） |

## 目录 API

```http
GET /api/v1/commerce/products
POST /api/v1/commerce/checkout-sessions
POST /api/v1/commerce/checkout-sessions/{sessionId}/confirm
```

确认结账时可选 body：`{ "userId": "user-1" }`。未传则使用创建会话时的 `userId`，再回退为 `{tenantId}-billing-owner`。

## 计费 API（多合同）

```http
GET /api/v1/billing/subscriptions/current?tenantId=&userId=
GET /api/v1/billing/subscriptions/active?tenantId=&userId=
GET /api/v1/billing/subscriptions/effective-quota?tenantId=&userId=
```

- **current**：仅 `BASE` 主订。
- **active**：所有未过期 ACTIVE 合同（含 add-on）。
- **effective-quota**：各合同 `includedQuota` 按 meter **相加**。

## 演进阶段

| 阶段 | 状态 | 内容 |
|------|------|------|
| P0 | 已落地 | 扩展目录、confirm 履约、钱包加油包 SKU |
| P1 | 已落地 | 固定 add-on（GPU/AI）、多合同并存、合并配额 API |
| P2 | 已落地 | 支付 webhook、席位池、`/billing/me/*`、Stripe HTTP（可配置）、计量周期、购物车 |
| P2+ | 部分落地 | Hyperswitch HTTP（Payment Link）、周期任务调度、发票 PDF |
| P3 | 部分 | 企业协议价（`CustomPricingRule` 已在 `previewPricing` 生效） |

## 支付 webhook → 履约（P2）

创建结账会话时（`platform-app` 装配了 `CheckoutPaymentPort`）会同步在支付模块登记 `checkoutSessionId` 绑定。

```http
POST /api/v1/webhooks/payments/stripe
Content-Type: application/json

{
  "type": "payment.succeeded",
  "checkoutSessionId": "chk_xxx",
  "tenantId": "tenant-1",
  "userId": "user-1"
}
```

Webhook 解析成功后调用 `PaymentSucceededPort` → `CheckoutOrchestrator.confirmCheckout` → `PurchaseFulfillmentService`。

开发环境也可直接：

```http
POST /api/v1/commerce/checkout-sessions/{sessionId}/confirm
```

## 购物车（P2）

```http
POST /api/v1/commerce/carts
POST /api/v1/commerce/carts/{cartId}/lines
POST /api/v1/commerce/carts/{cartId}/checkout-sessions
```

确认结账后按行逐项履约（主订 + add-on + 加油包可同单）。

## 计量账单周期（P2）

```http
POST /api/v1/billing/usage/record
POST /api/v1/billing/cycles/run?tenantId=&userId=
POST /api/v1/billing/cycles/process-due
```

`BillingCycleService`：汇总用量 → 扣减订阅 `includedQuota` → 对超量按 `PricingRule` + `CustomPricingRule` 计价 → 记账/扣钱包。

## Stripe（可选）

```yaml
platform.payment.stripe.enabled: true
STRIPE_SECRET_KEY: sk_test_...
```

启用后 `StripeHttpPaymentProvider` 替代 Noop，创建 Checkout Session 并回写 `checkout_session_id` 元数据。

## 购物车（P3）

```http
POST /api/v1/commerce/carts
POST /api/v1/commerce/carts/{cartId}/lines
POST /api/v1/commerce/carts/{cartId}/checkout-sessions
```

确认结账后按行逐项履约（主订 + add-on + 加油包可同单）。

## 计量与账单周期（P2+）

```http
POST /api/v1/billing/usage/record
POST /api/v1/billing/cycles/run?tenantId=&userId=
POST /api/v1/billing/cycles/process-due
```

`BillingCycleService`：汇总用量 → 扣减订阅包含量 → 对超量按 `PricingRule` + `CustomPricingRule` 计价 → 写 ledger / 尝试扣钱包。

## Stripe（可选生产）

```yaml
platform.payment.stripe.enabled: true
STRIPE_SECRET_KEY: sk_live_...
```

启用后 `StripeHttpPaymentProvider` 替代 Noop，创建 Checkout Session；未配置时仍为 Noop。

## Hyperswitch（可选）

Profile：`application-hyperswitch.yml`。商品码含 `hs` 时路由至 Hyperswitch（与 `PaymentGatewayService` 规则一致）。

```yaml
platform.payment.hyperswitch.enabled: true
HYPERSWITCH_API_KEY: snd_...
HYPERSWITCH_BASE_URL: https://sandbox.hyperswitch.io  # 生产: https://api.hyperswitch.io
HYPERSWITCH_PROFILE_ID: pro_...   # 可选
```

启用后 `HyperswitchHttpPaymentProvider` 调用 `POST /payments`（`payment_link: true`），回写 `checkout_session_id` / `tenant_id` 元数据；未启用时为 `NoopHyperswitchPaymentProvider`。

## 用户门户 API

```http
GET /api/v1/billing/me/plan
GET /api/v1/billing/me/subscriptions
GET /api/v1/billing/me/effective-quota
GET /api/v1/billing/me/credits
GET /api/v1/billing/me/history
GET /api/v1/billing/me/upgrades
```

## 联调示例

```bash
# 列出可售商品
curl -s http://localhost:8080/api/v1/commerce/products | jq .

# 创建 Pro 结账会话并确认（开发环境，无真实支付）
SESSION=$(curl -s -X POST http://localhost:8080/api/v1/commerce/checkout-sessions \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"tenant-1","productCode":"pro_monthly","userId":"user-1","successUrl":"https://example.com/ok"}' \
  | jq -r .checkoutSessionId)
curl -s -X POST "http://localhost:8080/api/v1/commerce/checkout-sessions/${SESSION}/confirm" \
  -H 'Content-Type: application/json' -d '{"userId":"user-1"}'

# 叠加 GPU add-on
# productCode: addon_gpu_monthly
```

## 相关代码

- `platform/commerce-module/.../CommerceCatalogService.java`
- `platform/commerce-module/.../CheckoutOrchestrator.java`
- `platform/platform-app/.../PurchaseFulfillmentService.java`
- `platform/billing-module/.../SubscriptionBillingService.java`
