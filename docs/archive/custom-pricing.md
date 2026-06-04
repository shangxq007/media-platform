# Custom Pricing

> Doc index: [docs/README.md](./README.md).

## Overview

Custom pricing allows platform admins to override default pricing at the tenant level, workspace level, or for individual meters. This enables enterprise contracts, volume discounts, and promotional pricing.

## Custom Pricing Rules

### CustomPricingRule

```java
public record CustomPricingRule(
    String ruleId,
    String tenantId,
    String workspaceId,          // null for tenant-level overrides
    String meterKey,             // e.g., "render.minutes"
    Long overridePriceMinor,     // Override the default unit price
    Double discountPercent,      // Percentage discount (0-100)
    Instant effectiveFrom,
    Instant effectiveTo,
    String status,               // ACTIVE, EXPIRED, ARCHIVED
    Instant createdAt
)
```

### Creating Custom Pricing

```
POST /api/v1/admin/billing/custom-pricing
{
  "tenantId": "tenant-acme",
  "workspaceId": null,
  "meterKey": "render.minutes",
  "overridePriceMinor": 20,    // $0.02 per minute instead of default $0.05
  "discountPercent": null,
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "effectiveTo": "2026-12-31T23:59:59Z"
}
```

Workspace-level override (takes precedence over tenant-level):

```
POST /api/v1/admin/billing/custom-pricing
{
  "tenantId": "tenant-acme",
  "workspaceId": "ws-premium",
  "meterKey": "render.gpu_minutes",
  "overridePriceMinor": 50,
  "discountPercent": 10.0,
  "effectiveFrom": "2026-05-01T00:00:00Z",
  "effectiveTo": "2026-10-31T23:59:59Z"
}
```

## Pricing Rule Overrides

### PricingRule

Standard pricing rules define the default pricing:

```java
public record PricingRule(
    String ruleId,
    String ruleKey,
    String name,
    String description,
    PricingModel pricingModel,
    String meterKey,
    long unitPriceMinor,
    String currencyCode,
    List<PricingTier> tiers,
    String status,
    Instant effectiveFrom,
    Instant effectiveTo,
    Instant createdAt,
    Instant updatedAt
)
```

### Creating a Pricing Rule

```
POST /api/v1/admin/billing/pricing-rules
{
  "ruleKey": "render_minutes_standard",
  "name": "Render Minutes - Standard",
  "description": "Standard pricing for render minutes",
  "pricingModel": "USAGE_BASED",
  "meterKey": "render.minutes",
  "unitPriceMinor": 50,
  "currencyCode": "USD",
  "tiers": [
    { "upToQuantity": 100, "unitPriceMinor": 50, "flatFeeMinor": 0 },
    { "upToQuantity": 500, "unitPriceMinor": 30, "flatFeeMinor": 0 },
    { "upToQuantity": null, "unitPriceMinor": 10, "flatFeeMinor": 0 }
  ],
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "effectiveTo": "2026-12-31T23:59:59Z"
}
```

### Archiving a Pricing Rule

```
POST /api/v1/admin/billing/pricing-rules/{ruleKey}/archive
```

## Discount Policies

### DiscountPolicy

```java
public record DiscountPolicy(
    String policyId,
    String policyKey,
    String name,
    String description,
    String discountType,         // "PERCENTAGE" or "FIXED_AMOUNT"
    double discountValue,
    Map<String, Object> conditions,  // e.g., {"minQuantity": 1000, "tier": "ENTERPRISE"}
    String status,
    Instant effectiveFrom,
    Instant effectiveTo,
    Instant createdAt
)
```

### Creating a Discount Policy

```
POST /api/v1/admin/billing/discount-policies
{
  "policyKey": "enterprise_volume_discount",
  "name": "Enterprise Volume Discount",
  "description": "20% discount for enterprise customers with >1000 minutes/month",
  "discountType": "PERCENTAGE",
  "discountValue": 20.0,
  "conditions": {
    "minQuantity": 1000,
    "tier": "ENTERPRISE"
  },
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "effectiveTo": "2026-12-31T23:59:59Z"
}
```

### Listing Discount Policies

```
GET /api/v1/admin/billing/discount-policies
```

## Pricing Priority Chain

When determining the price for a usage record, the system checks in this order:

```
1. Workspace-level CustomPricingRule (if workspaceId matches)
2. Tenant-level CustomPricingRule (if tenantId matches)
3. DiscountPolicy (if conditions match)
4. PricingRule (standard pricing for the meter)
5. Default pricing (fallback)
```

## Pricing Preview API

The pricing preview allows admins to estimate costs before committing:

```
POST /api/v1/admin/billing/pricing-preview
{
  "tenantId": "tenant-acme",
  "meterKey": "render.minutes",
  "quantity": 500,
  "context": {
    "tier": "ENTERPRISE",
    "workspaceId": "ws-premium"
  }
}
```

Response:
```json
{
  "tenantId": "tenant-acme",
  "meterKey": "render.minutes",
  "quantity": 500,
  "estimatedAmountMinor": 11000,
  "currencyCode": "USD",
  "breakdown": {
    "tier1": { "quantity": 100, "rate": 20, "cost": 2000 },
    "tier2": { "quantity": 400, "rate": 20, "cost": 8000 },
    "discount": { "type": "PERCENTAGE", "value": 10, "saving": 1000 }
  }
}
```

## Quote API

Users can get price quotes before consuming:

```
POST /api/v1/billing/quote
{
  "tenantId": "tenant-1",
  "meterKey": "render.minutes",
  "quantity": 120,
  "unit": "minutes"
}
```

Response:
```json
{
  "tenantId": "tenant-1",
  "meterKey": "render.minutes",
  "quantity": 120,
  "unit": "minutes",
  "estimatedAmountMinor": 6000,
  "currencyCode": "USD",
  "pricingModel": "USAGE_BASED"
}
```

## Enterprise Contract Pricing

For enterprise contracts, the typical setup involves:

1. Create a custom pricing rule with the negotiated rate:
```json
{
  "tenantId": "tenant-acme-corp",
  "meterKey": "render.minutes",
  "overridePriceMinor": 15,
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "effectiveTo": "2027-01-01T00:00:00Z"
}
```

2. Create a discount policy for volume commitments:
```json
{
  "policyKey": "acme_annual_commitment",
  "discountType": "PERCENTAGE",
  "discountValue": 25.0,
  "conditions": { "annualCommitment": true, "minSpend": 5000000 }
}
```

3. Set up a subscription plan with included quota:
```json
{
  "planKey": "acme_enterprise",
  "basePriceMinor": 500000,
  "billingInterval": "MONTHLY",
  "includedQuota": { "render.minutes": 50000 }
}
```

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/billing/pricing-rules` | Create pricing rule |
| GET | `/api/v1/admin/billing/pricing-rules` | List pricing rules |
| POST | `/api/v1/admin/billing/pricing-rules/{key}/archive` | Archive rule |
| POST | `/api/v1/admin/billing/pricing-preview` | Preview pricing |
| POST | `/api/v1/admin/billing/custom-pricing` | Create custom pricing |
| POST | `/api/v1/admin/billing/discount-policies` | Create discount policy |
| GET | `/api/v1/admin/billing/discount-policies` | List discount policies |
| POST | `/api/v1/billing/quote` | Get price quote |

## Error Codes

| Code | Description |
|------|-------------|
| `PRICING-400-001` | Invalid pricing rule |
| `PRICING-404-001` | Pricing rule not found |
| `PRICING-409-001` | Pricing rule key already exists |
| `PRICING-422-001` | Invalid discount policy |
