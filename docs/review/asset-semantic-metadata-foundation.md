---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app + V1 baseline
truth_level: current
owner: platform
---

# Asset Ecosystem Sprint 007 — Semantic Metadata Foundation

## Gap Analysis

### Existing Asset Models (Before)

| Model | Semantic Capability | Status |
|-------|-------------------|--------|
| `Asset` | identity, storage, mediaType, checksum, governance | ✅ |
| `AssetIdentity` | assetId, assetVersion, entityRef, xmpUri | ✅ |
| `AssetGovernanceMetadata` | classification, license, retention, security, PII, AI flag | ✅ |
| `AssetLineageMetadata` | sourceAssetId, derivedFrom, workflowId, runId | ✅ |
| `AssetRegistryRecord` | aggregated read-side view | ✅ |
| **Semantic Metadata** | transcripts, OCR, scenes, objects, people, brands, embeddings | ❌ |

### Gap Summary

**No unified model for AI enrichment results.** Transcripts, OCR text, scene detection, object recognition, face detection, brand detection, and embeddings would be stored as ad-hoc JSON if any provider were integrated. This sprint establishes the canonical model before provider integration begins.

## Implemented Domain Models (8 new records)

| Record | File | Purpose |
|--------|------|---------|
| `AssetSemanticMetadata` | `domain/asset/semantic/AssetSemanticMetadata.java` | Top-level aggregation: transcripts, OCR, scenes, objects, people, brands, embeddings + enrichment status |
| `Transcript` | `domain/asset/semantic/Transcript.java` | ASR result: provider, language, confidence, text, segments |
| `TranscriptSegment` | `domain/asset/semantic/TranscriptSegment.java` | Timed segment: startMs, endMs, speaker, text |
| `DetectedText` | `domain/asset/semantic/DetectedText.java` | OCR result: text, confidence, startMs, endMs |
| `Scene` | `domain/asset/semantic/Scene.java` | Scene detection: label, startMs, endMs, confidence |
| `DetectedObject` | `domain/asset/semantic/DetectedObject.java` | Object recognition: label, confidence, startMs, endMs |
| `DetectedPerson` | `domain/asset/semantic/DetectedPerson.java` | Face detection: name, confidence, startMs, endMs |
| `DetectedBrand` | `domain/asset/semantic/DetectedBrand.java` | Logo detection: brandName, confidence, startMs, endMs |
| `EmbeddingReference` | `domain/asset/semantic/EmbeddingReference.java` | Vector reference: embeddingId, provider, dimension, storageUri |

## Database Changes

### New table: `asset_semantic_metadata`

| Column | Type | Purpose |
|--------|------|---------|
| `asset_id` | VARCHAR(64) PK | Foreign key to asset |
| `asset_version` | VARCHAR(64) | Version when metadata was created |
| `status` | VARCHAR(32) | PENDING/IN_PROGRESS/COMPLETE/FAILED |
| `language` | VARCHAR(16) | Detected language |
| `semantic_json` | TEXT | Full AssetSemanticMetadata as JSON (Phase 1 aggregation) |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last update time |

## Provider SPI

| Interface | File | Purpose |
|-----------|------|---------|
| `SemanticMetadataProvider` | `domain/asset/semantic/SemanticMetadataProvider.java` | SPI interface: `supports()`, `analyze()`, `providerName()` |
| `SemanticMetadataRequest` | `domain/asset/semantic/SemanticMetadataRequest.java` | Input: assetId, version, type, storageUri, language |
| `SemanticMetadataResult` | `domain/asset/semantic/SemanticMetadataResult.java` | Output: semanticMetadata + success/failure |

## Registry & Service

| Component | File | Purpose |
|-----------|------|---------|
| `SemanticMetadataProviderRegistry` | `app/asset/SemanticMetadataProviderRegistry.java` | Register/resolve/list providers |
| `AssetSemanticMetadataService` | `app/asset/AssetSemanticMetadataService.java` | create, get, update, delete, attachProviderResult |
| `AssetSemanticMetadataRepository` | `infrastructure/asset/AssetSemanticMetadataRepository.java` | jOOQ CRUD with upsert |

## REST API

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/projects/{projectId}/assets/{assetId}/semantic-metadata` | GET | Get semantic metadata |
| `POST /api/v1/projects/{projectId}/assets/{assetId}/semantic-metadata` | POST | Create (initializes empty, no provider called) |
| `DELETE /api/v1/projects/{projectId}/assets/{assetId}/semantic-metadata` | DELETE | Delete semantic metadata |

## JSON-LD Extension

Added `buildProjectionWithSemantic()` to `AssetJsonLdExporter` — includes:
- Transcripts (provider, language, confidence, text)
- Scenes (label, time range, confidence)
- Objects (label, confidence)
- Enrichment status

New context namespace: `semantic: https://bluepulse.ai/xmp/semantic/1.0/`

## Review Integration

**Design note (no implementation):** Asset semantic metadata review will reuse `TimelineReview` with `entityRef = ASSET:{assetId}`. Reviewers can check transcript accuracy, object detection quality, governance compliance before publishing to marketplace.

## Tests (2 test files, 7 tests)

| Test Class | Tests | Scenarios |
|-----------|-------|-----------|
| `SemanticMetadataProviderRegistryTest` | 3 | Register, resolve matching, empty when no provider |
| `AssetSemanticMetadataTest` | 4 | Empty defaults, transcript creation, scene creation, embedding reference |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No Whisper provider | Not implemented — SPI defined, no implementation |
| No Tesseract provider | Not implemented |
| No Vision provider | Not implemented |
| No Embedding provider | Not implemented |
| No ElasticSearch | Not planned for Phase 1 |
| No Vector DB | Not planned for Phase 1 |
| semantic_json is single JSON column | Phase 1 aggregation — normalize in Phase 2 |
| No batch enrichment API | Single-asset only |
| No async enrichment | Synchronous API only |
| Review integration | Design note only — no implementation |

## Deferred Items

| Item | Phase |
|------|-------|
| Whisper ASR provider | P1 |
| Tesseract OCR provider | P2 |
| Vision provider (scene/object/face/brand) | P2 |
| Embedding provider (CLIP/OpenAI) | P3 |
| ElasticSearch integration | P3 |
| Vector DB integration | P3 |
| Asset search API | P2 |
| Marketplace | P4 |

## Validation

- [x] No new module
- [x] No V2 migration
- [x] No Whisper/Tesseract/CLIP integration
- [x] No ElasticSearch/Vector DB/Neo4j
- [x] No Spring AI runtime
- [x] No H2
- [x] ProductionSafetyValidator unchanged
- [x] All tests passing
