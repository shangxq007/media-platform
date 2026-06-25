---
status: implementation-report
created: 2026-06-25
scope: render-module + V1 baseline
truth_level: current
owner: platform
---

# Product Ecosystem Sprint 025 — Marketplace Consumer Foundation

## Marketplace Architecture Audit

### Before

No marketplace infrastructure existed. Asset publish was a status change only.

### After

```
AssetPublishedEvent → MarketplaceConsumer → MARKTEPLACE_PREPARE job
    → VALIDATE task + PACKAGE task → barrier → listing draft ready
```

## Schema (1 new table)

| Table | Key Columns | Constraints |
|-------|------------|------------|
| `marketplace_listing` | id PK, asset_id UNIQUE, tenant_id, project_id, listing_type, title, summary, description, preview_url, cover_url, version, status, review_id, created_at, updated_at | `uq_ml_asset` UNIQUE(asset_id), `fk_ml_asset` FK→asset |

**Indexes:** `ix_ml_status`, `ix_ml_type`

## Domain Models (3 new)

| Model | Purpose |
|-------|---------|
| `MarketplaceListing` | Listing record: asset_id, title, status (DRAFT→PUBLISHED→ARCHIVED) |
| `MarketplaceListingStatus` | DRAFT, READY, PUBLISHED, ARCHIVED |
| `MarketplaceListingType` | MEDIA, TIMELINE_TEMPLATE, EFFECT, PLUGIN, STYLE, WORKFLOW, AI_MODEL |

## New Components (5)

| Component | Role |
|-----------|------|
| `MarketplaceListingRepository` | CRUD: upsert (ON CONFLICT), findByAssetId, updateStatus, listByStatus |
| `MarketplaceConsumer` | `@EventListener` on AssetPublishedEvent → creates MARKTEPLACE_PREPARE job with VALIDATE (bit 0) + PACKAGE (bit 1) tasks |
| `MarketplaceValidateTaskHandler` | Mock handler: passes validation (real validation deferred) |
| `MarketplacePackageTaskHandler` | Mock handler: passes packaging (real packaging deferred) |
| `MarketplaceListingBuilder` | Builds listing draft from Asset Registry + SearchProjection |

## Runtime Flow

```
AssetPublishedEvent (outbox → dispatcher)
    ↓
MarketplaceConsumer.onAssetPublished()
    ↓
coordinator.createJob(MARKETPLACE_PREPARE, ASSET, assetId)
    ↓
coordinator.createTask(VALIDATE, bit 0)
coordinator.createTask(PACKAGE, bit 1)
    ↓
PlatformTaskDispatcher → leases VALIDATE → MarketplaceValidateTaskHandler → completeTask
PlatformTaskDispatcher → leases PACKAGE → MarketplacePackageTaskHandler → completeTask
    ↓
Barrier: completedMask (0b11) == requiredMask (0b11) → job COMPLETED
```

## Design Principle

**Marketplace listing is a projection, not source of truth.** Asset Registry + Semantic Metadata are authoritative. Listing is rebuildable from source data at any time.

## Eligibility Matrix

| Asset State | Marketplace Eligible? |
|------------|----------------------|
| DRAFT | No |
| IN_REVIEW | No |
| APPROVED | Yes (triggers prepare) |
| PUBLISHED | Yes (listing created/updated) |
| REJECTED | No |
| ARCHIVED | No (listing archived) |

## Tests (3 tests, all passing)

| Test | Scenario |
|------|----------|
| `MarketplaceConsumerTest` | AssetPublishedEvent → createJob(MARKETPLACE_PREPARE) + 2 tasks |
| `MarketplaceTaskHandlerTest.validateHandler` | VALIDATE capability reported + execute() succeeds |
| `MarketplaceTaskHandlerTest.packageHandler` | PACKAGE capability reported + execute() succeeds |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Mock handlers only | VALIDATE + PACKAGE are mock — no real validation/packaging logic |
| No listing draft persistence in handlers | ListingBuilder exists but not called in task handlers yet |
| No marketplace search | Listing query by status only |
| No payment/install/download | Deferred to P3+ |
| No rating/reviews | Deferred to P3+ |
| No revenue sharing | Deferred to P4+ |
| No recommendation engine | Deferred to P4+ |

## Deferred Items

| Item | Sprint |
|------|--------|
| Real VALIDATE handler (governance check, quality review) | Sprint 026 |
| Real PACKAGE handler (bundle, version, upload) | Sprint 026 |
| Marketplace listing API (CRUD endpoints) | Sprint 026 |
| Marketplace search & discovery | Sprint 027 |
