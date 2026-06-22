> **Status:** Archived (2026-06-22)
> **Reason:** Superseded by `docs/billing-access/` series (13 files covering entitlement, access, billing, reconciliation).
> **Superseded By:** `docs/billing-access/access-control-overview.md`
> **Do not use as current reference.**

---

# Commerce / Payment / Billing / Entitlement

> Doc index: [docs/README.md](./README.md).

## Purpose
This document explains how the four monetization modules fit together.

For internal layering (api / app / domain / infrastructure) and which third-party stacks are **actually** on the classpath vs roadmap-only, see [layering-and-open-source.md](./layering-and-open-source.md).

## Boundaries
- `commerce-module`: canonical catalog, checkout intent, product mapping, order semantics.
- `payment-module`: payment provider adapter, hosted checkout, webhook ingestion, payment verification.
- `billing-module`: recurring contract state, invoice projection, cycle lifecycle, proration/reconciliation entry point.
- `entitlement-module`: final access control, feature bundle, quota profile, overrides, grace period.

## Canonical flow
```text
checkout requested
  -> provider checkout created
  -> provider webhook/payment confirmation
  -> billing projection updated
  -> entitlement recalculated
  -> notifications/audit emitted
```

## Integration notes
### Kill Bill
Use under `billing-module`, not as the source of truth for entitlement.

### Hyperswitch
Use under `payment-module` for multi-processor routing, retries, and fallback.

### Medusa
Use under `commerce-module` if catalog, promotion, cart, or storefront workflows grow more complex.

## Important rules
1. Provider product IDs are never the canonical product key.
2. Payment success is not equal to entitlement grant.
3. Entitlement is the final platform truth for feature access.
4. Raw provider webhooks should be projected into internal events before other modules consume them.
