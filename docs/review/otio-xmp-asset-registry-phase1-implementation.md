---
status: implementation-report
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: current
owner: platform
supersedes: otio-xmp-asset-registry-placement-decision.md (placement decision)
---

# OTIO + XMP + Asset Registry — Phase 1 Implementation Report (Sprint 001)

## Implemented Scope

Phase 1 Core minimum viable asset metadata and registry closed loop:

```
Existing asset table
  → asset identity/version/governance fields
  → render-module asset registry domain/service
  → OTIO metadata references
  → XMP sidecar records
  → JSON-LD export
  → lightweight structured lineage fields
```

## Modified Schema

### `asset` table (V1__init_full_schema.sql)

**Columns added (8 new):**
- `asset_version VARCHAR(64)` — version string (e.g., "v7")
- `owner_id VARCHAR(128)` — owner user/org reference
- `entity_ref TEXT` — OpenAssetIO entity reference placeholder
- `classification VARCHAR(64)` — public/internal/confidential/restricted
- `license VARCHAR(128)` — license type
- `retention_policy VARCHAR(128)` — retention policy
- `security_level VARCHAR(64)` — L1/L2/L3/L4
- `contains_pii BOOLEAN NOT NULL DEFAULT FALSE`
- `ai_generated BOOLEAN NOT NULL DEFAULT FALSE`
- `updated_at TIMESTAMP`

**New indexes:**
- `ix_asset_classification` on `classification`
- `ix_asset_ai_generated` on `ai_generated`

### `artifact_node` table (V1__init_full_schema.sql)

**Columns added (6 new):**
- `workflow_id VARCHAR(128)` — workflow that produced this artifact
- `run_id VARCHAR(128)` — specific run identifier
- `operator_id VARCHAR(128)` — operator (user or system)
- `parameters_hash VARCHAR(128)` — hash of processing parameters
- `source_asset_id VARCHAR(128)` — primary source asset
- `derived_from_asset_ids TEXT` — upstream asset IDs

**No columns removed.** Existing `parent_artifact_ids` preserved.

## New Domain Records

### Asset Domain (`render-module/.../domain/asset/`)

| Record | Fields | Purpose |
|--------|--------|---------|
| `AssetIdentity` | assetId, assetVersion, entityRef, xmpUri | Platform asset identity (does not resolve storage) |
| `AssetGovernanceMetadata` | classification, license, retentionPolicy, securityLevel, containsPii, aiGenerated | Governance metadata block |
| `AssetLineageMetadata` | sourceAssetId, derivedFromAssetIds, workflowId, runId, operatorId, parametersHash | Lightweight lineage (no OpenLineage SDK) |
| `AssetRegistryRecord` | assetId, assetVersion, assetType, ownerId, projectId, entityRef, xmpUri, storageUri, checksum, governance, createdAt, updatedAt | Phase 1 registry read model |

### XMP Sidecar Records (`render-module/.../domain/xmp/`)

| Record | Fields | Purpose |
|--------|--------|---------|
| `XmpAssetMetadata` | assetId, assetType, assetVersion, ownerId, projectId, checksum, storageUri, entityRef, createdAt, updatedAt | Asset identity metadata |
| `XmpAiMetadata` | model, modelVersion, prompt, negativePrompt, seed, sampler, guidanceScale, taskType, confidence, reviewStatus | AI generation metadata |
| `XmpLineageMetadata` | sourceAsset, derivedFrom, processingStep, workflowId, runId, operatorId, parametersHash, timestamp | Processing lineage metadata |
| `XmpGovernanceMetadata` | classification, license, rightsHolder, usageRights, retentionPolicy, securityLevel, containsPii, aiGenerated, requiresReview, approvedBy | Governance metadata |
| `XmpSidecar` | schemaVersion, asset, ai, lineage, governance | Top-level sidecar container |

## Modified Existing Files

| File | Change |
|------|--------|
| `Asset.java` | Added 10 fields (assetVersion through updatedAt) |
| `AssetRepository.java` | Updated `register()` to insert new columns; updated `mapAsset()` for new columns |
| `OpenTimelineioAdapter.java` | Added bluepulse.* metadata injection for timeline root, clips, effects, markers |
| `TimelinePlatformMetadata.java` | Added 11 `BLUEPULSE_*` constant keys |
| `AssetServiceTest.java` | Updated constructor calls for new Asset record shape |
| `AssetControllerTest.java` | Updated constructor calls for new Asset record shape |

## New Services

| Service | Location | Purpose |
|---------|----------|---------|
| `AssetRegistryService` | `render-module/.../app/asset/` | Register assets, resolve identity, attach governance, build OTIO refs, build JSON-LD projection |
| `AssetJsonLdExporter` | `render-module/.../app/asset/` | Lightweight JSON-LD export (no JSON-LD library dependency) |

## OTIO Metadata Support

Bluepulse metadata keys added to `TimelinePlatformMetadata`:
- `bluepulse.project_id` — project identifier on timeline root
- `bluepulse.asset_registry_uri` — registry URI on timeline root
- `bluepulse.asset_id` — asset identifier on clip metadata
- `bluepulse.asset_version` — version on clip metadata
- `bluepulse.xmp_uri` — XMP sidecar URI on clip metadata
- `bluepulse.entity_ref` — OpenAssetIO entity ref on clip metadata
- `bluepulse.effect_family` — effect classification
- `bluepulse.provider_hint` — provider preference on effect metadata
- `bluepulse.capability_code` — capability code on effect metadata
- `bluepulse.semantic_type` — marker semantic type
- `bluepulse.review_status` — marker review status

Injection points:
- Timeline root metadata → `bluepulse.project_id`, `bluepulse.asset_registry_uri` from timeline metadata map
- Clip metadata → `bluepulse` sub-object in clip metadata node
- Effect metadata → `bluepulse.capability_code`, `bluepulse.provider_hint` from effect properties
- Marker metadata → `bluepulse.semantic_type`, `bluepulse.review_status` for review markers

## XMP Sidecar Support

XMP metadata is represented as Java domain records only — no XMP SDK, no file I/O engine. Sidecars are serializable to JSON for API/file export.

## JSON-LD Support

`AssetJsonLdExporter` produces JSON-LD with:
- `@context` mapping XMP namespaces (`bluepulse.ai/xmp/asset/1.0/`, etc.)
- `@id` and `@type` for asset entities
- Asset identity fields
- Governance fields
- Lineage references (derivedFrom, workflowId, runId)

No JSON-LD library dependency — uses Jackson `ObjectMapper`.

## Lineage Support

Structured lineage fields on `artifact_node` table:
- `workflow_id`, `run_id`, `operator_id`, `parameters_hash`
- `source_asset_id`, `derived_from_asset_ids`

Existing `parent_artifact_ids` field preserved. No OpenLineage SDK.

## Tests Run

**New tests (18 total, all passing):**
- `AssetIdentityTest` (2 tests)
- `AssetGovernanceMetadataTest` (2 tests)
- `XmpSidecarSerializationTest` (4 tests)
- `AssetJsonLdExporterTest` (3 tests)
- `AssetRegistryServiceTest` (3 tests)
- `OpenTimelineioAdapterMetadataTest` (4 tests)

**Existing tests (running, pre-existing):**
- `AssetServiceTest` — 9 tests passing (adapted to new Asset constructor)
- `AssetControllerTest` — 5 tests passing (adapted to new Asset constructor)

**Test commands:**
```bash
./gradlew :render-module:test --tests '*AssetIdentityTest' '*AssetGovernanceMetadataTest' '*XmpSidecarSerializationTest' '*AssetJsonLdExporterTest' '*AssetRegistryServiceTest' '*OpenTimelineioAdapterMetadataTest'
# Result: BUILD SUCCESSFUL (18 tests)
```

## Known Limitations

1. Asset metadata fields (classification, license, etc.) are stored in the `asset` table but the `AssetRepository.register()` method always inserts defaults (`v1`, `false`, `false`). The `AssetRegistryService` provides explicit registration with version/governance but requires a separate call path.
2. Bluepulse metadata on clips is an export-only feature — the OTIO import parser does not reconstruct `bluepulse.*` metadata back onto `TimelineAssetRef.metadata()`.
3. `artifact_node` lineage fields are schema-only in Phase 1 — no Java code populates them yet at runtime.
4. No REST API endpoint for asset governance CRUD — `AssetRegistryService` exists but has no controller.
5. Jooq `Record.get("contains_pii", Boolean.class)` may return null if column allows nulls despite `NOT NULL DEFAULT FALSE` constraint — handled via `Boolean.TRUE.equals()`.
6. `XmpAiMetadata` and `XmpLineageMetadata` are defined as records but not yet populated from any existing AI processing pipeline (ASR, vision, LLM).

## Deferred to Phase 2+

| Item | Reason |
|------|--------|
| OpenLineage SDK / event emission | Lineage stored at rest; event streaming is Phase 2 |
| OpenAssetIO SDK / entity resolution | `entity_ref` column seeded; resolution is Phase 2 |
| Neo4j / RDF graph database | JSON-LD export foundation laid; graph DB is Phase 2+ |
| Full XMP file I/O engine | Phase 1 provides schema records only |
| Standalone `asset-registry-module` | Phase 1 fits in `render-module` |
| Policy-governance-module rewrite | Asset governance fields on `asset` table |
| DAM/MAM search integration | Phase 2+ |
| AI metadata population pipeline | ASR/vision/LLM integration needed to populate XMP AI metadata |

## Validation Checklist

- [x] No new Gradle module created
- [x] No V2 migration file
- [x] V1 baseline updated (project is pre-deployment)
- [x] No OpenLineage SDK
- [x] No OpenAssetIO SDK
- [x] No Neo4j
- [x] No Spring AI runtime enabled
- [x] No H2
- [x] ProductionSafetyValidator unchanged
- [x] No policy-governance-module changes
- [x] 18 new tests, all passing
- [x] Existing AssetServiceTest and AssetControllerTest adapted and passing
