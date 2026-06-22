> **Status:** Archived (2026-06-22)
> **Reason:** Superseded by `docs/billing-access/` series.
> **Superseded By:** `docs/billing-access/07-billing-models.md`
> **Do not use as current reference.**

---

# Subscription Billing

> Doc index: [docs/README.md](./README.md).

## Overview

The subscription billing system manages recurring billing contracts, plan lifecycle, included quotas, overage handling, trials, and cancellations. The `SubscriptionBillingService` orchestrates the subscription lifecycle.

## Subscription Plans

### SubscriptionPlan

```java
public record SubscriptionPlan(
    String planId,
    String planKey,           // e.g., "pro_monthly", "team_annual"
    String name,
    String description,
    String billingInterval,   // "MONTHLY", "ANNUAL"
    long basePriceMinor,      // Price in minor units (cents)
    String currencyCode,
    Map<String, Long> includedQuota,  // {"render.minutes": 300, "gpu.minutes": 0}
    String status,            // ACTIVE, ARCHIVED
    Instant createdAt,
    Instant updatedAt
)
```

### Creating a Plan

```
POST /api/v1/admin/billing/plans
{
  "planKey": "pro_monthly",
  "name": "Pro Monthly",
  "description": "PRO tier with monthly billing",
  "billingInterval": "MONTHLY",
  "basePriceMinor": 2900,
  "currencyCode": "USD",
  "includedQuota": {
    "render.minutes": 300,
    "render.jobs.daily": 50,
    "storage.bytes": 10737418240,
    "render.gpu_minutes": 0
  }
}
```

### Listing Plans

```
GET /api/v1/admin/billing/plans
```

## Subscription Contracts

### SubscriptionContract

```java
public record SubscriptionContract(
    String contractId,
    String tenantId,
    String userId,
    String planKey,
    Instant periodStartAt,
    Instant periodEndAt,
    String lifecycleState,    // ACTIVE, PENDING_CANCELLATION, EXPIRED, CANCELLED
    long basePriceMinor,
    String currencyCode,
    Map<String, Long> includedQuota,
    Map<String, Long> includedQuotaUsed
)
```

## Lifecycle

```
CREATE -> ACTIVE -> [change plan] -> ACTIVE (new period)
                -> [cancel] -> PENDING_CANCELLATION -> EXPIRED (at period end)
                -> [period end without renewal] -> EXPIRED
```

### Creating a Subscription

```
POST /api/v1/billing/subscriptions
{
  "tenantId": "tenant-1",
  "userId": "user-123",
  "planKey": "pro_monthly",
  "periodDays": 30
}
```

### Getting Current Subscription

```
GET /api/v1/billing/subscriptions/current?tenantId={tenantId}&userId={userId}
```

## Included Quota and Oage Handling

Each plan includes a quota map (`includedQuota`). Usage is tracked against these limits.

### Overage Behavior

When included quota is exceeded:

1. The system checks the pricing model:
   - **SUBSCRIPTION**: Overage is blocked or charged at a per-unit rate
   - **HYBRID**: Overage is charged at the overage rate defined in the pricing rule
   - **USAGE_BASED**: All usage is charged per-unit

2. Overage charges are recorded in the billing ledger as `CHARGE` entries.

3. The user is notified with upgrade recommendations.

Example overage pricing:
```json
{
  "planKey": "pro_monthly",
  "overageRates": {
    "render.minutes": 30,    // $0.03 per minute overage
    "storage.bytes": 1048576 // $0.001 per MB overage
  }
}
```

## Plan Upgrade/Downgrade

```
POST /api/v1/billing/subscriptions/change-plan
{
  "contractId": "sub-abc",
  "newPlanKey": "team_monthly",
  "periodDays": 30
}
```

### Upgrade Behavior

- Immediate access to new tier features
- Prorated billing for the current period
- New quota limits apply immediately
- Remaining quota from the old plan is forfeited

### Downgrade Behavior

- New tier features remain accessible until the current period ends
- At period end, the new (lower) tier takes effect
- If current usage exceeds the new tier's limits, the user is warned

## Trial Periods

Free trial subscriptions can be created by setting `FREE_TRIAL` pricing model:

```
POST /api/v1/billing/subscriptions
{
  "tenantId": "tenant-1",
  "userId": "user-123",
  "planKey": "pro_trial",
  "periodDays": 14
}
```

Trial behavior:
- Full access to the plan's features for the trial period
- No charges during the trial
- At trial end, the subscription transitions to `EXPIRED`
- User must create a paid subscription to continue

## Cancellation

```
POST /api/v1/billing/subscriptions/cancel
{
  "contractId": "sub-abc"
}
```

Cancellation behavior:
- The contract transitions to `PENDING_CANCELLATION`
- The user retains access until `periodEndAt`
- At period end, the contract transitions to `EXPIRED`
- No automatic renewal

## Payment Provider Integration (STUB)

> **Production Blocker**: Payment provider integrations are currently stubs.

The `BillingEngine` SPI interface is available for integrating real payment providers:

```java
public interface BillingEngine {
    PaymentResult processPayment(PaymentRequest request);
    RefundResult processRefund(RefundRequest request);
    void handleWebhook(WebhookPayload payload);
}
```

The current implementation uses `NoopKillBillBillingEngine` which returns projected state only. Real payment processing, invoicing, and webhook handling are **NOT implemented**.

### Integration Points

- **Stripe**: For card payments and subscription management
- **Kill Bill**: For enterprise billing with complex invoicing
- **Hyperswitch**: For multi-processor routing and fallback

See [production-blockers.md](./production-blockers.md).

## Billing Cycle Processing

> **Production Blocker**: `SubscriptionBillingService.processBillingCycle()` logs but does not actually generate invoices or charge customers.

The billing cycle process should:
1. Iterate all `ACTIVE` contracts where `periodEndAt` is approaching
2. Calculate charges for the period (base fee + overage)
3. Generate invoices
4. Charge the customer via payment provider
5. Reset `includedQuotaUsed` for the new period
6. Emit billing events

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/billing/plans` | Create subscription plan |
| GET | `/api/v1/admin/billing/plans` | List all plans |
| POST | `/api/v1/billing/subscriptions` | Create subscription |
| GET | `/api/v1/billing/subscriptions/current` | Get current subscription |
| POST | `/api/v1/billing/subscriptions/change-plan` | Upgrade/downgrade plan |
| POST | `/api/v1/billing/subscriptions/cancel` | Cancel subscription |
| GET | `/api/v1/billing/subjects/{subjectId}` | Get billing state |

## Error Codes

| Code | Description |
|------|-------------|
| `SUBSCRIPTION-400-001` | Invalid subscription request |
| `SUBSCRIPTION-403-001` | Plan change not allowed |
| `SUBSCRIPTION-404-001` | Plan not found |
| `SUBSCRIPTION-404-002` | Contract not found |
| `SUBSCRIPTION-409-001` | Active subscription already exists |
| `SUBSCRIPTION-422-001` | Downgrade would exceed new limits |
