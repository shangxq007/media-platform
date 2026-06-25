---
status: implementation-report
created: 2026-06-25
scope: render-module + platform-app
truth_level: current
owner: platform
---

# Product Ecosystem Sprint 028 — Marketplace Search Productization

## Marketplace Search Audit

### Before (Sprint 027)
- `search()` returned `List<MarketplaceListing>` — no total count, no pagination
- No `listingType` filter on search
- `findByAssetId(assetId)` was global — no tenant scoping
- Controller exposed repository model directly — no stable DTO
- Discovery API had inconsistent response

### After (Sprint 028)
- `search()` returns `SearchResult(total, offset, limit, results)` — proper pagination
- `listingType` filter added to search query and controller
- `findByAssetId(assetId, tenantId)` enforces tenant isolation
- Stable DTOs: `MarketplaceSearchResponse`, `MarketplaceListingDto`, `MarketplaceDiscoveryResponse`
- Search latency logging with structured info

## Pagination

```
GET /marketplace/search?q=cinematic&offset=0&limit=20

Response:
{
  "total": 42,
  "offset": 0,
  "limit": 20,
  "results": [...]
}
```

Repository computes `COUNT(*)` for total, then `SELECT ... LIMIT ? OFFSET ?`.

## Listing Type Filter

```
GET /marketplace/search?listingType=MEDIA
GET /marketplace/search?listingType=TIMELINE_TEMPLATE

Supported values: MEDIA, TIMELINE_TEMPLATE, EFFECT, PLUGIN, STYLE, WORKFLOW, AI_MODEL
```

SQL: `AND listing_type = ?` added to WHERE clause.

## Tenant Isolation

| Method | Before | After |
|--------|--------|-------|
| `findByAssetId` | `(assetId)` — global | `(assetId, tenantId)` — scoped |
| `search` | `tenantId` optional | `tenantId` optional (null = all tenants for admin) |
| GET by asset | No tenant check | `?tenantId=` query param |

## DTO Model

| DTO | Fields |
|-----|--------|
| `MarketplaceSearchResponse` | total, offset, limit, results (List<MarketplaceListingDto>) |
| `MarketplaceListingDto` | id, assetId, tenantId, projectId, listingType, title, summary, description, previewUrl, coverUrl, status, version, reviewId, createdAt, updatedAt |
| `MarketplaceDiscoveryResponse` | recent (List), popular (List), featured (List) |

## Multi-language FTS Decision

| Phase | Config | Rationale |
|-------|--------|-----------|
| Phase 1 (current) | `'english'` | Default for MVP. Most marketplace content is English. |
| Phase 2 | `'simple'` | Add language-agnostic FTS for non-English content discovery. |
| Phase 3 | Per-tenant config | Allow each tenant to set their `default_text_search_config`. Stores in tenant settings. |

## Observability

```
Marketplace search: query='cinematic' listingType=null status=PUBLISHED tenant=t1 total=42 returned=20 offset=0 latency=12ms
```

## Tests (10 total, all passing)

| Test | Scenario |
|------|----------|
| `MarketplaceConsumerTest` | Event → createJob |
| `MarketplaceTaskHandlerTest` (2) | VALIDATE + PACKAGE capability |
| `MarketplaceValidationTest` (2) | Asset existence check |
| `MarketplacePackageTest` (2) | Builder → upsert |
| `MarketplaceControllerTest` (3) | Search pagination, invalid transition, draft→ready |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No `listingType` on discovery | Discovery returns all types. Add type-based tabs in future UI. |
| Popular/featured still mock | Same as recent. Analytics-based popularity deferred. |
| FTS English-only | Phase 1 decision. Multi-language in Phase 2. |
| No sort parameter | Always `rank` desc (FTS) or `updated_at` desc (filter). |

## Deferred Items

| Item |
|------|
| Popularity score based on installs/views |
| Listing type tabs on discovery |
| Sort by price/rating/newest |
| Multi-language FTS (Phase 2) |
