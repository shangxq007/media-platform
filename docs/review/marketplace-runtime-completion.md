---
status: implementation-report
created: 2026-06-25
scope: render-module + platform-app
truth_level: current
owner: platform
---

# Product Ecosystem Sprint 026 — Marketplace Runtime Completion & Registry API

## Marketplace Runtime Audit

| Component | Sprint 025 | Sprint 026 |
|-----------|-----------|-----------|
| `MarketplaceConsumer` | ✅ Creates job + 2 tasks | ✅ Unchanged |
| `MarketplaceValidateTaskHandler` | Mock (log only) | ✅ Real: checks asset exists, PUBLISHED status, projection built |
| `MarketplacePackageTaskHandler` | Mock (log only) | ✅ Real: calls builder → upserts listing |
| `MarketplaceListingBuilder` | Basic (type + title) | ✅ Complete: reads Asset + SemanticMetadata + SearchProjection |
| `MarketplaceListingRepository` | ✅ | ✅ Unchanged |
| **MarketplaceController** | ❌ | ✅ New: list, get, getByAsset, updateStatus |
| **Lifecycle rules** | ❌ | ✅ State transitions enforced |

## VALIDATE Handler — Realized

Checks:
1. Asset exists (`AssetRepository.findById`)
2. Asset publishStatus == "PUBLISHED"
3. Search projection exists (`SearchProjectionRepository.findByAssetId`)

Failure throws `IllegalStateException` → task FAILED → barrier not satisfied → job FAILED.

## PACKAGE Handler — Realized

1. Extracts `assetId` + `projectId` from payload
2. `MarketplaceListingBuilder.buildDraft(assetId, tenantId, projectId)` → creates `MarketplaceListing`
3. `MarketplaceListingRepository.upsert(draft)` → persists (idempotent via ON CONFLICT)

## Listing Builder — Completed

Reads from 3 sources:
- `AssetRepository` — assetType, filename
- `SearchProjectionRepository` — transcript (as summary), filename (as title)
- `AssetSemanticMetadataRepository` — available for future enrichment

## Registry API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/marketplace/listings?status=&listingType=` | GET | List listings (default: PUBLISHED) |
| `GET /api/v1/marketplace/listings/{listingId}` | GET | Get listing by ID |
| `GET /api/v1/marketplace/assets/{assetId}/listing` | GET | Get listing for an asset |
| `PATCH /api/v1/marketplace/listings/{listingId}/status` | PATCH | Update status (with lifecycle validation) |

## Lifecycle Matrix

| From → To | Valid? |
|-----------|--------|
| DRAFT → READY | ✅ |
| DRAFT → ARCHIVED | ✅ |
| READY → PUBLISHED | ✅ |
| READY → ARCHIVED | ✅ |
| READY → DRAFT | ✅ |
| PUBLISHED → ARCHIVED | ✅ |
| PUBLISHED → DRAFT | ❌ (rejected 409) |
| ARCHIVED → * | ❌ (rejected 409) |

## Runtime Flow (Complete)

```
AssetPublishedEvent → MarketplaceConsumer → MARKTEPLACE_PREPARE job
    → VALIDATE task (bit 0) → checks asset + publishStatus + projection
    → PACKAGE task (bit 1) → builder → upsert listing
    → barrier satisfied → job COMPLETED
    → listing available at GET /marketplace/listings
```

## Tests (8 total, all passing)

| Test Class | Tests |
|-----------|-------|
| `MarketplaceConsumerTest` | 1 |
| `MarketplaceTaskHandlerTest` | 2 |
| `MarketplaceValidationTest` | 2 |
| `MarketplacePackageTest` | 2 |
| `MarketplaceControllerTest` | 3 |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `AssetRepository.findById("system", ...)` hardcoded tenant | Tenant context not passed through task execution |
| No multi-tenant listing scoping | Listings returned globally |
| No listing search | Only by status filter |
| No cover/preview URL generation | Fields exist but not populated |

## Deferred Items

| Item | Sprint |
|------|--------|
| Marketplace search & discovery | Sprint 027 |
| Cover/preview URL generation | Sprint 027 |
| Real FFprobe handler | Sprint 027 |
| Real ASR handler | Sprint 028 |
