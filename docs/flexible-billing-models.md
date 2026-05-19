# Flexible Billing Models

> Doc index: [docs/README.md](./README.md).

## Overview

The `billing-module` supports seven pricing models and provides a complete billing pipeline: metering, rating, ledger, credit wallets, and subscription contracts. The system is designed to be extensible through the `BillingEngine` SPI.

## Supported Pricing Models

```java
public enum PricingModel {
    SUBSCRIPTION,    // Fixed recurring fee
    USAGE_BASED,     // Pay per unit consumed
    TIME_BASED,      // Pay per time unit (e.g., per minute)
    CUSTOM,          // Tenant/workspace-specific pricing
    HYBRID,          // Combination of subscription + usage
    CREDIT,          // Pre-paid credit-based
    FREE_TRIAL       // Free period with limited features
}
```

### Model Descriptions

| Model | Description | Example |
|-------|-------------|---------|
| SUBSCRIPTION | Fixed price per billing interval (monthly/yearly) | $29/month for PRO tier |
| USAGE_BASED | Pay per unit consumed | $0.05 per render minute |
| TIME_BASED | Pay per time unit | $0.10 per GPU minute |
| CUSTOM | Override pricing for specific tenant/workspace | Enterprise contract: $0.02/min |
| HYBRID | Base subscription + overage usage charges | $29/month + $0.03/min overage |
| CREDIT | Debit from pre-paid credit wallet | Top up $100, debit per use |
| FREE_TRIAL | Free access for a limited period | 14-day trial with PRO features |

## Billing Meters

`BillingMeter` defines what is being measured:

```java
public record BillingMeter(
    String meterKey,          // e.g., "render.minutes", "gpu.minutes"
    String name,
    String unit,              // e.g., "minutes", "bytes", "jobs"
    String aggregationType,   // SUM, MAX, COUNT
    String status             // ACTIVE, INACTIVE
)
```

Common meters:
- `render.minutes`: Total render minutes consumed
- `render.gpu_minutes`: GPU render minutes
- `render.jobs`: Number of render jobs
- `storage.bytes`: Storage consumed
- `prompt.executions`: AI prompt executions
- `api.calls`: API call count

## Usage Records

`UsageRecord` captures a single usage event:

```java
public record UsageRecord(
    String recordId,
    String tenantId,
    String workspaceId,
    String userId,
    String meterKey,
    double quantity,
    String unit,
    Instant recordedAt,
    String idempotencyKey
)
```

Recorded via `POST /api/v1/billing/usage/record`.

## Rating Engine

`RatingEngine` applies pricing rules to usage records:

```
UsageRecord + PricingRule -> RatedUsageRecord
```

```java
public record RatedUsageRecord(
    String ratedUsageId,
    String usageRecordId,
    String pricingRuleId,
    long ratedAmountMinor,    // Amount in minor currency units (cents)
    String currencyCode,
    Map<String, Object> ratingDetails,
    Instant createdAt
)
```

### Tiered Pricing

`PricingRule` supports tiered pricing via `PricingTier`:

```java
public record PricingTier(
    long upToQuantity,        // Upper bound of this tier
    long unitPriceMinor,      // Price per unit in this tier
    long flatFeeMinor         // Flat fee for this tier
)
```

Example: First 100 minutes at $0.05/min, next 400 at $0.03/min, remainder at $0.01/min.

## Billing Ledger

`BillingLedgerEntry` records all financial transactions:

```java
public record BillingLedgerEntry(
    String entryId,
    String tenantId,
    String workspaceId,
    String userId,
    String entryType,         // CHARGE, REFUND, ADJUSTMENT, CREDIT, DEBIT, DISCOUNT
    long amountMinor,
    String currencyCode,
    String referenceType,
    String referenceId,
    String description,
    Instant createdAt
)
```

Entry types:
- `CHARGE`: Usage-based charge
- `REFUND`: Refund issued
- `ADJUSTMENT`: Manual adjustment
- `CREDIT`: Credit added
- `DEBIT`: Debit from wallet
- `DISCOUNT`: Discount applied

## Credit Wallets

`CreditWallet` and `CreditTransaction` manage pre-paid balances. See [credit-wallet.md](./credit-wallet.md).

## Subscription Billing Lifecycle

```
Plan Created -> Subscription Created -> Active -> [Change Plan] -> Active
                                                    -> [Cancel] -> Pending Cancellation
                                                    -> Period End -> Expired
```

See [subscription-billing.md](./subscription-billing.md).

## Custom Pricing and Enterprise Contracts

`CustomPricingRule` and `DiscountPolicy` enable tenant-level and workspace-level pricing overrides.

See [custom-pricing.md](./custom-pricing.md).

## Discount Policies

```java
public record DiscountPolicy(
    String policyId,
    String policyKey,
    String name,
    String description,
    String discountType,       // PERCENTAGE, FIXED_AMOUNT
    double discountValue,
    Map<String, Object> conditions,
    String status,
    Instant effectiveFrom,
    Instant effectiveTo,
    Instant createdAt
)
```

## BillingState

The current billing state for a subject:

```java
public record BillingState(
    String subjectId,
    String contractState,
    Instant periodEndAt,
    String canonicalProductCode
)
```

Retrieved via `GET /api/v1/billing/subjects/{subjectId}`.

## BillingDecision

Result of a billing check:

```java
public record BillingDecision(
    String decisionId,
    String action,
    String tenantId,
    String userId,
    String pricingModel,
    long estimatedAmountMinor,
    String currencyCode,
    boolean useCredits,
    Map<String, Object> details,
    String status             // APPROVED, DENIED, PENDING
)
```

## API Reference

### Subscription Plans

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/billing/plans` | Create a plan |
| GET | `/api/v1/admin/billing/plans` | List all plans |

### Subscriptions

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/billing/subscriptions` | Create subscription |
| GET | `/api/v1/billing/subscriptions/current` | Get current subscription |
| POST | `/api/v1/billing/subscriptions/change-plan` | Change plan |
| POST | `/api/v1/billing/subscriptions/cancel` | Cancel subscription |

### Usage & Rating

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/billing/quote` | Get price quote |
| POST | `/api/v1/billing/usage/record` | Record usage |
| GET | `/api/v1/billing/usage` | List usage records |
| GET | `/api/v1/billing/ledger` | Get billing ledger |

### Pricing Rules

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/billing/pricing-rules` | Create pricing rule |
| GET | `/api/v1/admin/billing/pricing-rules` | List pricing rules |
| POST | `/api/v1/admin/billing/pricing-rules/{key}/archive` | Archive rule |
| POST | `/api/v1/admin/billing/pricing-preview` | Preview pricing |
| POST | `/api/v1/admin/billing/custom-pricing` | Create custom pricing |
| POST | `/api/v1/admin/billing/discount-policies` | Create discount policy |
| GET | `/api/v1/admin/billing/discount-policies` | List discount policies |

### Billing State

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/billing/subjects/{subjectId}` | Get billing state |

## Error Codes

| Code | Description |
|------|-------------|
| `BILLING-400-001` | Invalid billing request |
| `BILLING-403-001` | Insufficient credits |
| `BILLING-404-001` | Plan not found |
| `BILLING-404-002` | Subscription not found |
| `BILLING-409-001` | Subscription already exists |
| `BILLING-422-001` | Invalid pricing rule |
