---
status: placement-decision
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: confirmed
owner: platform
reviewers: principal-architect
---

# OTIO + XMP + Asset Registry — Phase 1 Placement Decision

## Executive Summary

**Decision:** Implement Phase 1 Core asset metadata and registry capabilities inside the existing `render-module` and pre-deployment V1 database baseline. Do not create standalone modules for XMP, Asset Registry, OpenAssetIO, OpenLineage, or Knowledge Graph at this time.

**Rationale:** The existing `render-module` already contains the `Asset` domain record, `AssetRepository`, `OpenTimelineioAdapter`, `TimelinePlatformMetadata`, and inline asset registry generation. All Phase 1 additions fit within these existing boundaries without creating cross-module coupling or violating Spring Modulith constraints.

**Timing:** Phase 1 implementation can begin after this placement decision is reviewed. No code or Flyway changes have been made yet.

---

## Inspection Findings

### Existing Assets and Their Limitations

| Existing Artifact | Location | What It Provides | What Is Missing |
|-------------------|----------|-----------------|-----------------|
| `asset` table | V1:1974 | 9 fields: id, tenant_id, project_id, storage_key, media_type, filename, size_bytes, checksum, duration_ms, width, height, created_at | Version, owner, entity_ref, updated_at, governance fields (classification, license, rights, retention, security, PII, AI-generated, review) |
| `artifact` table | V1:385 | 10 fields: id, render_job_id, project_id, storage_uri, format, resolution, duration, status, tombstoned_at, created_at | size_bytes, checksum (present in `Artifact.java` record but missing in V1 DDL) |
| `artifact_node` table | V1:2003 | DAG node with parent_artifact_ids as text field | Structured lineage records (workflow_id, run_id, operator, tool, parameters_hash) |
| `artifact_relation` table | V1:402 | Source-target relationships | Only binary relations; no lineage metadata |
| `media_asset_metadata` table | V1:538 | 20+ media probe fields (codec, fps, resolution, etc.) | Governance metadata; this table is probe data only |
| `storage_object` table | V1:97 | Blob storage registration with lifecycle_policy | Asset-level governance (storage has lifecycle, not classification/license) |
| `project_import_metadata` table | V1:2203 | Import/export timeline JSON + asset mapping | XMP or governance metadata |
| `asset_library` table | V1:2286 | Workspace-level library grouping | Per-asset governance or lineage |

### Missing Entirely

| Capability | Status |
|------------|--------|
| XMP metadata schema classes | No Java records, no namespace definitions |
| JSON-LD context / serialization | No code or configuration |
| OpenAssetIO entity reference resolution | No SDK, no adapter interface |
| OpenLineage event emission | No SDK, no event types |
| Knowledge Graph (Neo4j / RDF) | No deployment, no connector |
| Asset governance (classification, license, retention) | No domain model, no table columns |
| Asset version history | No version table or version field |
| Structured lineage records | Only `parent_artifact_ids` text field in `artifact_node` |

### Module Capability Check

| Module | Relevant? | What It Has | Gap for Phase 1 |
|--------|-----------|-------------|-----------------|
| `render-module` | **Yes — primary** | `Asset`, `AssetRepository`, `OpenTimelineioAdapter`, `TimelinePlatformMetadata`, `InternalTimelineWriter.buildAssetRegistry()` | Needs: governance fields, lineage table, XMP schema records, JSON-LD export |
| `policy-governance-module` | **No** | Feature flags, rule evaluation (`PolicyEvaluationService`) | No asset governance models; this is a policy engine, not an asset governance store |
| `artifact-catalog-module` | **No** | Artifact lifecycle, GC, relations | Artifact != asset; this module handles render outputs, not source assets |
| `storage-module` | **No** | Blob storage abstraction, S3/LocalFS | Storage layer, not asset identity layer |
| `identity-access-module` | **No** | Tenants, users, permissions | No asset-specific governance |
| `commerce-module` | **No** | Products, pricing, subscriptions | No asset metadata |
| `platform-app` | **Host only** | REST controllers, Flyway migrations | Schema extension goes here |

---

## Decision

### Primary Decision

**Implement Phase 1 Core inside `render-module` and existing V1 baseline.**

| Aspect | Decision |
|--------|----------|
| Code placement | `render-module/src/main/java/.../render/domain/xmp/`, `.../domain/asset/`, `.../infrastructure/jsonld/`, `.../app/assetregistry/` |
| Schema placement | Extend existing `asset` table in `V1__init_full_schema.sql` + add `asset_version` and `asset_lineage` tables |
| New module? | No — no standalone `asset-registry-module`, `xmp-metadata-module`, or `openlineage-event-module` |
| Flyway approach | Modify V1 baseline directly (project is pre-deployment, V1 states "resettable") |
| V2 migration? | Not yet — V2 migrations should only be used after first real deployment with shared database |

### Why render-module

1. `Asset.java` (domain record) and `AssetRepository.java` (jOOQ persistence) already live in `render-module`
2. `OpenTimelineioAdapter.java` already reads/writes OTIO metadata and is the injection point for `bluepulse.*` keys
3. `TimelinePlatformMetadata.java` already defines well-known metadata key constants
4. `InternalTimelineWriter.buildAssetRegistry()` already generates inline asset registries from timeline data
5. Adding governance fields to the `asset` table keeps authoritative identity in one place
6. No cross-module coupling is introduced — everything stays within `render-module`

### Why Not a Separate Module

1. Asset identity and governance are core attributes of assets, not a separate bounded context
2. The `asset` table already exists — splitting governance into a separate module would create cross-module JOINs or event-based synchronization
3. Spring Modulith guidance: "Do not collapse module boundaries for short-term convenience" — but also: "Prefer explicit domain vocabulary over generic names." Governance fields on the asset table are explicit domain vocabulary, not boundary collapse.
4. A standalone `asset-registry-module` would need to import the existing `Asset` record and `AssetRepository`, creating bidirectional coupling with `render-module`
5. Phase 2 can extract into a separate module if architecture demands it — Phase 1 should not pre-optimize

---

## Phase 1 Scope

### In Scope

| Area | Details |
|------|---------|
| **Asset identity** | Add `version`, `owner_id`, `entity_ref`, `updated_at` to `asset` table + `Asset` record |
| **Asset versioning** | New `asset_version` table + `AssetVersion` record for version history |
| **Asset governance** | Add `classification`, `license`, `rights_holder`, `retention_policy`, `security_level`, `pii`, `ai_generated`, `requires_review`, `approved_by` to `asset` table |
| **Asset lineage** | New `asset_lineage` table + `AssetLineage` record with structured lineage fields |
| **XMP schema** | 8 new Java records in `render-module/.../domain/xmp/`: `XmpAssetMetadata`, `XmpVfxMetadata`, `XmpAiMetadata`, `XmpLineageMetadata`, `XmpGovernanceMetadata`, `XmpMlMetadata`, `XmpNamespace`, `XmpMetadataPacket` |
| **OTIO metadata** | New `bluepulse.*` key constants in `TimelinePlatformMetadata`, injection points in `OpenTimelineioAdapter` for timeline/clip/effect/marker metadata |
| **JSON-LD** | `JsonLdContext`, `JsonLdAssetSerializer`, `JsonLdExportService` in `render-module/.../infrastructure/jsonld/` |
| **Asset registry service** | `AssetRegistryService` in `render-module/.../app/assetregistry/` for UUID assignment, version tracking, lineage recording |

### Explicitly Not in Scope (Deferred to Phase 2+)

| Area | Reason |
|------|--------|
| OpenLineage SDK or event emission | Phase 1 stores lineage records; event streaming is Phase 2 |
| OpenAssetIO SDK or entity resolution | Phase 1 stores `entity_ref` strings; resolution is Phase 2 |
| Neo4j / RDF graph database | Phase 1 provides JSON-LD export; graph DB is Phase 2+ |
| Full XMP file I/O engine | Phase 1 provides schema records only; file embedding is Phase 2+ |
| Standalone `asset-registry-module` | Phase 1 fits in `render-module`; module extraction is Phase 2+ |
| Policy-governance-module rewrite | Asset governance fields are on `asset` table, not a policy engine |
| DAM/MAM search integration | Phase 2+ consideration |
| Cross-cloud asset federation | Phase 3 (long-term) |

---

## Implementation Readiness

### Prerequisites

- [x] V1 baseline inspection complete — all 42 tables audited against design document
- [x] Existing asset/artifact/storage/media domain models inspected
- [x] Module boundary analysis complete — no new module needed
- [x] Placement decision documented in `otio-render-platform-blueprint.md` §18
- [x] Reference architecture map updated with XMP/OpenAssetIO/OpenLineage/KG learnings
- [x] docs/README.md updated with new document references

### Can Phase 1 Core Start?

**Yes.** All prerequisite inspection and documentation is complete. No code changes have been made. The placement decision is recorded in three documents. Phase 1 Core implementation can begin after review of this placement decision.

### Files Likely Affected During Implementation

| File | Change |
|------|--------|
| `platform-app/src/main/resources/db/migration/V1__init_full_schema.sql` | Add 10 columns to `asset` + 2 new tables |
| `render-module/src/main/java/.../domain/asset/Asset.java` | Add 10 fields |
| `render-module/src/main/java/.../domain/asset/AssetVersion.java` | New record |
| `render-module/src/main/java/.../domain/asset/AssetLineage.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpAssetMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpVfxMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpAiMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpLineageMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpGovernanceMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpMlMetadata.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpNamespace.java` | New record |
| `render-module/src/main/java/.../domain/xmp/XmpMetadataPacket.java` | New record |
| `render-module/src/main/java/.../domain/timeline/TimelinePlatformMetadata.java` | Add `bluepulse.*` keys |
| `render-module/src/main/java/.../domain/timeline/OpenTimelineioAdapter.java` | Inject metadata keys into timeline/clip/effect/marker |
| `render-module/src/main/java/.../infrastructure/asset/AssetRepository.java` | Update jOOQ queries for new columns |
| `render-module/src/main/java/.../infrastructure/jsonld/JsonLdContext.java` | New class |
| `render-module/src/main/java/.../infrastructure/jsonld/JsonLdAssetSerializer.java` | New class |
| `render-module/src/main/java/.../infrastructure/jsonld/JsonLdExportService.java` | New class |
| `render-module/src/main/java/.../app/assetregistry/AssetRegistryService.java` | New service |

### Review Gates Before Code Changes

- [ ] This placement decision reviewed and approved
- [ ] V1 baseline column names/types confirmed against existing jOOQ patterns in `AssetRepository`
- [ ] `ModularityTest` confirmed passing before any changes (`./gradlew :platform-app:test --tests '*ModularityTest*'`)
- [ ] Local development database can be dropped and recreated (verify Flyway baseline resettability)
- [ ] Existing tests pass before changes (`./gradlew :render-module:test`)

---

## Remaining Questions

| # | Question | Status |
|---|----------|--------|
| 1 | Should `display_name` be added as a separate column or derived from `filename`? | Recommendation: add as separate column — `filename` is the storage artifact name; `display_name` is the user-facing label |
| 2 | Should `usage_rights` be a text list column or a separate join table? | Recommendation: start with a simple `usage_rights TEXT[]` array column; normalize into a join table in Phase 2 if needed |
| 3 | Should XMP sidecar files be stored alongside media in object storage? | Deferred to Phase 2 — Phase 1 only generates JSON-LD on demand |
| 4 | Where should the `AssetRegistryController` live — `render-module` or `platform-app`? | Recommendation: `platform-app` (thin REST controller); `AssetRegistryService` in `render-module` (application service) |
