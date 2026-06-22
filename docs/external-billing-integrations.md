> **Status:** Archived (2026-06-22)
> **Reason:** Superseded by `docs/billing-access/` series.
> **Superseded By:** `docs/billing-access/07-billing-models.md`
> **Do not use as current reference.**

---

# External Commerce / Billing / Payment Integrations

> Doc index: [docs/README.md](./README.md).

## Positioning

These integrations are **not** the core domain model of this platform.
They are optional external components that can be connected beneath the internal modules:

- `commerce-module`
- `payment-module`
- `billing-module`
- `entitlement-module`
- `quota-billing-module`

The internal model should remain stable even if any external integration is replaced.

```text
canonical product / order / subscription / entitlement
        ↓
provider adapters
        ↓
Kill Bill / Hyperswitch / Medusa / Stripe / Apple / Google / others
```

---

## 1. Kill Bill

### What it is for
Kill Bill is the strongest reference if the platform later needs a **full billing core**:
- recurring subscriptions
- invoices
- usage billing
- account-level billing lifecycle
- payment plugin ecosystem
- more complex subscription state changes

### Recommended role in this project
Use Kill Bill as an **optional billing engine**, not as the source of truth for authorization.

Recommended layering:

```text
commerce-module
  -> billing-module
      -> killbill-adapter
  -> entitlement-module
```

Kill Bill should primarily help with:
- invoice lifecycle
- subscription contracts
- payment retries and billing state
- plugin-based payment handling

The platform should still keep its own:
- canonical product catalog
- entitlement snapshot
- quota counters
- access decisions
- tenant/user/project ownership model

### Integration approach
- Add a `killbill-adapter` inside `billing-module`
- Use webhook/event synchronization or polling-based projection into local billing tables
- Persist provider object mappings locally
- Treat Kill Bill as an external billing system projection target/source

Suggested mapping examples:
- internal `commerce_product.code` -> Kill Bill plan / product mapping
- internal `subscription_contract` -> Kill Bill subscription reference
- internal invoice projection <- Kill Bill invoice state

### Attention points
- Do not let the rest of the platform depend directly on Kill Bill object shapes
- Do not use Kill Bill as the entitlement engine
- Be careful with local state projection, retries, and reconciliation jobs
- Keep external IDs and internal IDs clearly separated

### When to introduce it
Introduce Kill Bill when:
- subscription states become complicated
- invoice logic becomes heavy
- multi-plan transitions/proration/manual billing adjustments are no longer simple

---

## 2. Hyperswitch

### What it is for
Hyperswitch is best understood as a **payment orchestration layer**:
- multiple payment processors
- routing / fallback
- unified payment abstraction
- future regional payment diversity

### Recommended role in this project
Use Hyperswitch under `payment-module`, not above the commerce model.

Recommended layering:

```text
commerce-module
  -> payment-module
      -> hyperswitch-adapter
          -> payment providers
  -> billing-module
  -> entitlement-module
```

Hyperswitch is most useful for:
- web/direct payment expansion beyond Stripe
- future routing across processors
- regional payment strategy
- payment provider failover

### Integration approach
- Add `hyperswitch-adapter` inside `payment-module`
- Keep a provider-neutral `PaymentProvider` SPI in the platform
- Map Hyperswitch payment intents / attempts into local payment projections
- Use local webhook ingest + reconciliation

### Attention points
- Do not move product/catalog logic into Hyperswitch
- Do not let Hyperswitch decide entitlement grants
- In-app store purchases (Apple/Google) should still remain separate adapters
- Avoid leaking Hyperswitch terminology into public domain models

### When to introduce it
Introduce Hyperswitch when:
- more direct pay providers are required
- routing/fallback becomes important
- region-specific checkout logic starts to multiply

---

## 3. Medusa

### What it is for
Medusa is best treated as a **headless commerce layer**:
- catalog
- order/cart/checkout style commerce flows
- promotions/discounts
- storefront-oriented product operations

### Recommended role in this project
Use Medusa only if the platform later needs a stronger **commerce / merchandising** capability.

Recommended layering:

```text
commerce-module
  -> medusa-adapter
payment-module
billing-module
entitlement-module
```

Medusa is helpful if the platform later needs:
- richer checkout/product management
- storefront-like commerce operations
- discount/coupon/promotion complexity
- external storefront or partner sales surfaces

### Integration approach
- Add `medusa-adapter` under `commerce-module`
- Keep internal canonical products as the stable product model
- Map Medusa product / variant / price data into local catalog tables or projections
- Do not treat Medusa catalog IDs as permanent internal product identity

### Attention points
- Medusa is not a replacement for entitlement logic
- Medusa is not a replacement for platform-specific billing and quota logic
- Avoid coupling internal plan/feature definitions directly to Medusa variant structures
- Keep order ownership and tenant ownership explicit in local tables

### When to introduce it
Introduce Medusa when:
- checkout/catalog/promo logic becomes product-level heavy
- the platform starts behaving like a real commerce surface
- internal admin-only catalog management is no longer enough

---

## Combined recommendation

### Suggested evolution path
1. **Current stage**
   - keep internal `commerce-module`, `payment-module`, `billing-module`, `entitlement-module`
   - keep adapters simple
2. **Billing complexity rises**
   - consider Kill Bill under `billing-module`
3. **Payment provider/routing complexity rises**
   - consider Hyperswitch under `payment-module`
4. **Catalog/checkout/promo complexity rises**
   - consider Medusa under `commerce-module`

### Stable internal rule
No external platform should directly become:
- the product catalog source of truth
- the entitlement source of truth
- the quota source of truth
- the public API model

The platform should always own:
- canonical product definitions
- internal purchase/order projections
- billing projections used by platform logic
- entitlement snapshots
- quota enforcement

