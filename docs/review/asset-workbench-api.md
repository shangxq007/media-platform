---
status: implementation-report
created: 2026-06-25
scope: platform-app
truth_level: current
owner: platform
---

# Productization Sprint 033 — Asset Workbench API

## Asset Capability Audit

### Existing APIs (Reused)

| API | Source |
|-----|--------|
| Asset identity + governance + version | `AssetRegistryService` |
| Semantic metadata (transcripts, scenes, objects) | `AssetSemanticMetadataService` |
| Publish status | `AssetReviewService` |
| Marketplace listing | `MarketplaceListingRepository` |
| Search projection | `SearchProjectionRepository` |

### Workbench Gaps (Addressed)

| Gap | API |
|-----|-----|
| No unified asset view | `GET /assets/{assetId}/workspace` — all facets in one response |
| No semantic workspace | `GET /workspace/semantic` — transcript, scenes, objects |
| No governance workspace | `GET /workspace/governance` — classification, license, PII |
| No marketplace workspace | `GET /workspace/marketplace` — listing status, type, preview |
| No search workspace | `GET /workspace/search` — index status, projection size |

## New Controller: `AssetWorkbenchController` — 6 aggregation endpoints

| Endpoint | Purpose | DTO |
|----------|---------|-----|
| `GET /api/v1/assets/{assetId}/workspace` | Full asset workbench view | `AssetWorkbenchDto` |
| `GET /.../workspace/semantic` | Semantic metadata summary | `SemanticWsDto` |
| `GET /.../workspace/governance` | Governance metadata | `GovernanceWsDto` |
| `GET /.../workspace/marketplace` | Marketplace listing status | `MarketplaceWsDto` |
| `GET /.../workspace/search` | Search projection status | `SearchWsDto` |

## DTO Model (5 new)

| DTO | Fields |
|-----|--------|
| `AssetWorkbenchDto` | assetId, assetType, storageUri, checksum, timestamps, publishStatus, semanticStatus, marketplaceStatus, searchIndexed, classification, license, version |
| `SemanticWsDto` | transcript, sceneCount, objectCount, brandCount, peopleCount, language, status |
| `GovernanceWsDto` | classification, license, containsPii, retentionPolicy, securityLevel, ownerId, version |
| `MarketplaceWsDto` | listingId, status, listingType, previewUrl, coverUrl, updatedAt |
| `SearchWsDto` | indexed, searchTextSize |

## Observability

```
Asset workbench loaded: asset=a1 latency=3ms
```

## No Changes To

- Asset Registry, Semantic Metadata, Enrichment Runtime
- Marketplace Runtime, Search Runtime
- Database schema, Domain models, Events

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Enrichment provider summary not yet exposed | `AssetEnrichmentService` exists but no provider-listing API |
| No version history in workbench | Available via `GET /versions` (existing) |
| Marketplace workspace returns 404 if not listed | Returns empty response — frontend should handle gracefully |

## Deferred Items

| Item |
|------|
| Enrichment workspace provider summary |
| Asset timeline / history view |
| Asset relationship / lineage view |
| GraphQL read layer |
