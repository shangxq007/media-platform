---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Product Runtime Blueprint

> **Refines:** [Artifact Runtime](artifact-runtime.md) (A4)
> **Linked ADR:** [ADR-010](adr/ADR-010-product-runtime-postgresql-modeling.md)

## 1. Core Definitions

| Term | Definition | Example |
|------|-----------|---------|
| **Asset** | User-owned logical resource | Interview.mov |
| **Product** | Any build result consumed/produced by another Producer | Transcript.json, Preview.mp4, TimelineEditPlan, SearchProjection |
| **Artifact** | File-backed Product with StorageReference | proxy.mp4, thumbnail.jpg, subtitle.srt |
| **Producer** | Anything consuming/ producing Products | ASR Provider, Execution Planner, Creative Planner |
| **Product Graph** | Producer + Product dependency graph | Transcript → Embedding → Search Projection |

## 2. Why Product, Not Just Artifact

Not every system-generated result is a file:
- ASR transcript → JSON in AssetSemanticMetadata
- TimelineEditPlan → structured plan (not a file)
- Search Projection → database row (not a file)

"Artifact" implies file. "Product" is the broader abstraction. Artifact = Product with representationKind=MEDIA_FILE.

## 3. Product Model

```
Product {
    productId: String
    tenantId: String
    projectId: String
    ownerAssetId: String (optional)
    productType: String
    representationKind: MEDIA_FILE | JSON_DOCUMENT | VECTOR_REFERENCE
                     | TIMELINE_PLAN | TIMELINE_REVISION | SEARCH_INDEX
                     | MARKETPLACE_PACKAGE | GRAPH | EXTERNAL_REFERENCE
    producerType: String
    producerId: String
    sourceTimelineRevisionId: String (optional)
    status: CREATED | PROCESSING | READY | FAILED | SUPERSEDED | ARCHIVED
    storageUri: String (optional — only for file-backed)
    checksum: String (optional)
    mimeType: String (optional)
    sizeBytes: long (optional)
    contentHash: String
    metadataJson: String
    version: int
    createdAt: Instant
    updatedAt: Instant
}
```

## 4. Producer Model

| Producer | Input Products | Output Products |
|----------|---------------|-----------------|
| ASR Provider | Asset (audio) | Transcript |
| OCR Provider | Asset (image) | OCR Result |
| Vision Provider | Asset (video) | Vision Metadata |
| Embedding Provider | Transcript | Embedding Reference |
| Creative Planner | Transcript, Vision, OCR | TimelineEditPlan |
| TimelineMutationService | TimelineEditPlan | TimelineRevision |
| Execution Planner | ArtifactGraph | ExecutionPlan |
| BMF Provider | Media Artifact | Transcode/Filter Artifact |
| FFmpeg Provider | Media Artifact | Proxy/Thumbnail Artifact |
| Marketplace Builder | Preview, Thumbnail, Package | Marketplace Listing |
| Search Reindexer | Transcript, Vision, OCR | Search Projection |

## 5. Product Graph

```
Raw Media (Asset)
  → Transcript Product → Embedding Product
  → Vision Metadata Product
  → OCR Product
  → Thumbnail Artifact
  → Preview Artifact
    → Final Render Artifact
      → Marketplace Package Artifact
        → Marketplace Listing Product
```

Product Graph supports: dependency tracking, lineage, rebuild, downstream invalidation, audit.

## 6. PostgreSQL Modeling Decision

**Rejected: Table inheritance** (FK complexity, uniqueness constraints, ORM migration issues).

**Adopted: Single product table + product_dependency table + metadata_json jsonb.**

```sql
product (product_id, tenant_id, project_id, owner_asset_id, product_type,
         representation_kind, producer_type, producer_id, source_timeline_revision_id,
         status, storage_uri, checksum, mime_type, size_bytes, content_hash,
         metadata_json jsonb, version, created_at, updated_at)

product_dependency (product_id, depends_on_product_id, dependency_type, created_at)
```

Phase 1: Single table. Phase 2: Specialized detail tables (transcript_product_detail, etc.). Phase 3: Declarative partitioning if scale demands.

## 7. Relationship to Existing Tables

Existing tables (asset, asset_semantic_metadata, search_projection, marketplace_listing) remain valid. Product Runtime:
- Phase 1: Keep current tables. Add Product Runtime as cross-cutting registry.
- Phase 2: Register generated outputs as Products.
- Phase 3: Move projection lineage to Product Graph.

## 8. Execution Planner

Planner consumes Product Graph (not PlatformTask directly). Resolves: Product requirements → Producer candidates → Provider → ExecutionBackend → ExecutionPlan.

## 9. Storage Runtime (Future A5)

Product only references storage. Storage Runtime owns: object storage, presigned URL, worker cache, range read, upload, download, checksum verification, access strategy.
