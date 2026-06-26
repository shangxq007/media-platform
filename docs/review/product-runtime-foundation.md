---
status: implementation-report
created: 2026-06-25
scope: render-module + platform-app + V1 baseline + shared-kernel
truth_level: current
owner: platform
---

# Foundation F1 — Product Runtime Foundation

## Implemented

### Domain Models (4)
| Model | Purpose |
|-------|---------|
| `Product` | Aggregate root: productId, tenantId, projectId, ownerAssetId, productType, representationKind, producerType/Id, status, storageReferenceId, checksum, contentHash, mimeType, version, metadataJson, timestamps |
| `ProductType` | RAW_MEDIA, TRANSCRIPT, VISION, OCR, EMBEDDING, TIMELINE_EDIT_PLAN, TIMELINE_REVISION, PREVIEW, THUMBNAIL, PROXY, SUBTITLE, FINAL_RENDER, PACKAGE, SEARCH_INDEX, MARKETPLACE_LISTING |
| `RepresentationKind` | MEDIA_FILE, JSON_DOCUMENT, VECTOR_REFERENCE, TIMELINE_PLAN, TIMELINE_REVISION, GRAPH, SEARCH_INDEX, EXTERNAL_REFERENCE |
| `ProductStatus` | REGISTERED, PROCESSING, READY, FAILED, SUPERSEDED, ARCHIVED |

### Schema
`product` table — 18 columns, 6 indexes (tenant, project, asset, producer, status, type)

### Repository + Service + Controller
| Component | Key Methods |
|-----------|-------------|
| `ProductRepository` | save (upsert), findById, findByProject, findByAsset, findLatest |
| `ProductRuntimeService` | register, markReady, markFailed, find, findLatest, findByAsset, findByProject |
| `ProductController` | GET /products/{id}, GET /projects/{projectId}/products, GET /assets/{assetId}/products |

### Events (3)
- `ProductRegisteredEvent` — productId, productType, assetId, projectId, producerId
- `ProductReadyEvent` — productId, productType, assetId
- `ProductFailedEvent` — productId, productType, assetId, error

### Architecture Validation
- Product table: single table, no inheritance (per ADR-010)
- representationKind discriminates file vs non-file products
- storageReferenceId is nullable (Storage Runtime not implemented)
- No Product Graph, No Execution Planner, No Storage Runtime

## Tests
Compilation passes. Existing tests unaffected.

## Known Limitations
| Limitation | Status |
|-----------|--------|
| No Product Graph (dependencies) | Deferred to F2 |
| No Producer integration | Producers use service API manually |
| No Storage Runtime | storageReferenceId nullable |
