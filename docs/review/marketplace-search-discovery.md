> Historical pre-owner implementation record. EP15/EP29C replaces the Render/in-memory Marketplace and generic Asset publication paths described here. Current contracts, routes, event ownership and migration limits: [Marketplace owner contracts](../marketplace/marketplace-foundation.md). Artifact-owned metadata and Timeline-owned review contracts remain distinct.

---
status: implementation-report
created: 2026-06-25
scope: render-module + platform-app + V1 baseline
truth_level: current
owner: platform
---

# Product Ecosystem Sprint 027 — Marketplace Search & Discovery Foundation

## Marketplace Audit

### Before

- `marketplace_listing` had no search capability — only status filter
- No FTS index on listing content
- No builder-generated search text
- No discovery API

### After

- `search_text` + `search_vector tsvector` with GIN index on `marketplace_listing`
- `MarketplaceListingRepository.search()` using `plainto_tsquery @@ search_vector` + `ts_rank`
- `MarketplaceListingBuilder` generates search text from title + summary, mock preview/cover URLs
- `MarketplaceController` extended with `GET /search` + `GET /discovery`
- Tenant/project isolation in search queries

## Schema Changes

| Column | Type | Purpose |
|--------|------|---------|
| `search_text` | TEXT | Concatenated title + summary for FTS indexing |
| `search_vector` | TSVECTOR | GIN-indexed full-text search vector |
| `ix_ml_fts` | GIN INDEX | Fast FTS lookup |

## New Components

| Component | Method | Purpose |
|-----------|--------|---------|
| `MarketplaceListingRepository.search()` | FTS search | `plainto_tsquery('english', ?) @@ search_vector` + `ts_rank` |
| `MarketplaceListingBuilder` | Enhanced | Generates search_text, preview_url, cover_url |
| `MarketplaceController.search()` | API | `GET /marketplace/search?q=&status=&tenantId=&projectId=` |
| `MarketplaceController.discovery()` | API | `GET /marketplace/discovery` — recent + popular |

## Search Architecture

```
User Search: "cinematic intro"
    ↓
GET /marketplace/search?q=cinematic+intro
    ↓
MarketplaceListingRepository.search(query, status, tenantId, projectId, limit)
    ↓
SELECT *, ts_rank(search_vector, plainto_tsquery('english', 'cinematic intro')) as rank
FROM marketplace_listing
WHERE search_vector @@ plainto_tsquery('english', 'cinematic intro')
  AND status = 'PUBLISHED'
  AND tenant_id = 't1'
ORDER BY rank DESC LIMIT 20
    ↓
ListingResponse[{id, title, summary, previewUrl, coverUrl, ...}]
```

## Discovery API

```
GET /marketplace/discovery
  → recent: latest PUBLISHED listings (by updated_at desc)
  → popular: same as recent (mock — real popularity deferred)
  → featured: empty (mock — curation deferred)
```

## Tenant Isolation

| Before (Sprint 026) | After (Sprint 027) |
|---------------------|-------------------|
| Global listing query | `tenant_id` + `project_id` filter in search |
| `findByAssetId` across all tenants | Still global (listing IDs must be unique) |

## Observable

```
Marketplace search: query='cinematic' results=3
Marketplace discovery: recent=10
```

## Tests

All 8 existing marketplace tests pass (Consumer, TaskHandler, Validation, Package, Controller).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Popular/featured sections are mock | Uses same query as recent. Real popularity + curation deferred. |
| No pagination on search | `limit` only. Full offset-based pagination deferred. |
| No listing type filter on search | Only `status` + `tenantId` + `projectId`. Add `listingType` in next sprint. |
| FTS English-only | `to_tsvector('english', ...)` hardcoded. Multi-language deferred. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Pagination (offset-based) | Sprint 028 |
| listingType filter on search | Sprint 028 |
| Popularity score / analytics | P3 |
| Real cover/preview generation | P3 |
| Curation / featured management | P3 |
