---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app + V1 baseline
truth_level: current
owner: platform
---

# Asset Ecosystem Sprint 008 — Probe + ASR Provider Spike

## Provider SPI Audit

### Before Sprint 008

The SPI (`SemanticMetadataProvider`) had no `capability()` method. The registry could only resolve by full `SemanticMetadataRequest` match — no capability-based lookup.

### After Sprint 008

- Added `capability()` method (`SemanticCapability` enum: PROBE, ASR, OCR, VISION, EMBEDDING)
- Added `resolveByCapability()` and `findFirst()` methods to `SemanticMetadataProviderRegistry`
- Added `SemanticMetadataResult.additionalData` map for auxiliary provider output (probe metadata)
- SPI now supports ordered pipeline: PROBE → ASR → (future: OCR, VISION, EMBEDDING)

## Implemented Components

| Component | File | Role |
|-----------|------|------|
| `ProbeMetadata` | `domain/asset/semantic/ProbeMetadata.java` | Probe domain record (format, duration, fps, codec, resolution, audio) |
| `SemanticCapability` | `domain/asset/semantic/SemanticCapability.java` | Enum: PROBE, ASR, OCR, VISION, EMBEDDING |
| `FfprobeMetadataProvider` | `infrastructure/asset/provider/FfprobeMetadataProvider.java` | FFprobe CLI probe — extracts video/audio/container metadata via JSON or text fallback parsing |
| `MockWhisperAsrProvider` | `infrastructure/asset/provider/MockWhisperAsrProvider.java` | Mock ASR — returns fixed transcript with 3 speaker segments (95% confidence, en language) |
| `AssetEnrichmentService` | `app/asset/AssetEnrichmentService.java` | Orchestrates all registered providers: PROBE → ASR → merge → persist |
| `AssetEnrichmentController` | `web/assets/AssetEnrichmentController.java` | REST API: `POST /enrich`, `GET /enrichment-status`, `GET /providers` |

## Extended Components

| Component | Change |
|-----------|--------|
| `SemanticMetadataProvider` | +`capability()` method |
| `SemanticMetadataProviderRegistry` | +`resolveByCapability()`, `findFirst()` methods |
| `SemanticMetadataResult` | +`additionalData` map field, overloaded `success()` factory |
| `AssetJsonLdExporter` | +`buildProjectionWithProbe()` method |

## Enrichment Status Lifecycle

```
PENDING ────→ IN_PROGRESS ────→ COMPLETE
                 │
                 └──→ FAILED
```

`AssetEnrichmentService` manages status transitions:
1. `enrich()` called → status set to `IN_PROGRESS`
2. All providers complete → status set to `COMPLETE`
3. Provider failure → status stays `IN_PROGRESS` (partial enrichment)

## REST API (3 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `POST /api/v1/projects/{projectId}/assets/{assetId}/enrich` | POST | Trigger enrichment pipeline (PROBE → ASR) |
| `GET /api/v1/projects/{projectId}/assets/{assetId}/enrichment-status` | GET | Get enrichment status and provider list |
| `GET /api/v1/projects/{projectId}/assets/{assetId}/providers` | GET | List registered providers with capabilities |

## Tests (12 new tests, all passing)

| Test Class | Tests | Scenarios |
|-----------|-------|-----------|
| `FfprobeMetadataProviderTest` | 3 | Supports storageUri, capability=PROBE, providerName |
| `MockWhisperAsrProviderTest` | 5 | Supports VIDEO/AUDIO, rejects IMAGE, 3 segments, confidence, capability=ASR |
| `AssetEnrichmentServiceTest` | 4 | Register+resolve ASR, full enrichment with mock, empty pipeline, status transitions |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No real Whisper integration | Mock provider only |
| No real FFprobe available in test | Unit tests test provider structure; integration test needs ffprobe binary |
| Synchronous enrichment only | No async queue, no Temporal, no progress tracking |
| No OCR provider | Deferred — P2 |
| No Vision provider | Deferred — P2 |
| No Embedding provider | Deferred — P3 |
| No ElasticSearch | Deferred — P3 |
| No Vector DB | Deferred — P3 |
| Probe stores in `additionalData` map | Not persisted to main semantic metadata until Phase 2 |

## Architecture

```
Current Pipeline:
  Asset → Probe (FFprobe) → ASR (MockWhisper) → AssetSemanticMetadata

Future Pipeline:
  Asset → Probe → ASR → OCR → Vision → Embedding → Search
```

## Validation

- [x] No new module
- [x] No V2 migration
- [x] No Whisper/Tesseract/CLIP runtime
- [x] No ElasticSearch/Vector DB/Neo4j
- [x] No Spring AI runtime
- [x] No H2
- [x] All 19 tests passing (7 Sprint 007 + 12 Sprint 008)
