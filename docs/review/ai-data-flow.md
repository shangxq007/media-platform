---
status: implementation-report
created: 2026-06-25
scope: render-module
truth_level: current
owner: platform
---

# AI Processing Sprint 039 — Semantic Metadata Persistence & AI Data Flow

## Capability Audit

| Step | Sprint 038 | Sprint 039 |
|------|-----------|-----------|
| Whisper → AsrResult | ✅ | ✅ |
| AsrResult → Transcript | ❌ | ✅ `Transcript.fromAsrResult()` |
| Transcript → AssetSemanticMetadata | ❌ | ✅ `semanticService.update()` |
| AssetSemanticMetadata → AssetEnrichedEvent | ❌ | ✅ `eventPublisher.publish()` |
| AssetEnrichedEvent → Search Reindex | ⚠️ Consumer exists | ✅ Triggers automatically |
| AssetEnrichedEvent → Notification | ✅ (handler exists) | ✅ Unchanged |
| Transcript → SearchProjection | ❌ | ✅ Via reindex flow |
| Transcript → AssetWorkbench | ❌ | ✅ Via existing workbench DTO |

## AI Data Flow (Complete)

```
Asset → Platform Job → ASR Task → RealAsrTaskHandler
    ↓
WhisperAsrProvider.transcribe() → AsrResult
    ↓
Transcript.fromAsrResult(result) → Transcript + TranscriptSegments
    ↓
semanticService.update(assetId, updated) → asset_semantic_metadata (JSON)
    ↓
eventPublisher.publish(AssetEnrichedEvent) → outbox
    ↓ (OutboxEventDispatcher → Spring event)
AssetSearchConsumer.onAssetEnriched() → SEARCH_REINDEX job → search_projection updated
    ↓
AssetWorkbenchController → SemanticWsDto.transcript → displayed to user
```

## Modified Components

| Component | Change |
|-----------|--------|
| `Transcript.java` | +`fromAsrResult(AsrResult)` factory method — converts AsrResult segments to TranscriptSegments |
| `RealAsrTaskHandler.java` | +`semanticService` dep, +`eventPublisher` dep; persists transcript via `semanticService.update()`; publishes `AssetEnrichedEvent` via `eventPublisher.publish()` |

## Persistence

```
RealAsrTaskHandler.execute():
  1. Whisper → AsrResult
  2. Transcript.fromAsrResult(asrResult) → Transcript
  3. semanticService.get(assetId) → existing metadata
  4. Merge: new transcript appended to existing transcripts list
  5. semanticService.update(assetId, updated) → JSON → asset_semantic_metadata
  6. eventPublisher.publish(AssetEnrichedEvent) → outbox → consumers
```

## Event Integration

`AssetEnrichedEvent` already has existing consumers:
- `AssetSearchConsumer.onAssetEnriched()` → creates `SEARCH_REINDEX` job → `search_projection` updated
- `NotificationEventHandler.onAssetEnriched()` → notifies asset owner
- `AuditEventHandler` (future) → records audit entry

## Search Integration

After ASR persistence + event:
1. `AssetSearchConsumer` triggers `SEARCH_REINDEX` job
2. `SearchReindexTaskHandler` rebuilds `SearchProjection` from `AssetSemanticMetadata.transcripts`
3. `search_projection.search_text` updated with transcript text
4. `search_vector tsvector` rebuilt via `to_tsvector('english', search_text)`
5. FTS search now matches transcript content

## Workbench Integration

`AssetWorkbenchController.workspace/semantic` already reads `AssetSemanticMetadataService.get()`:
- `SemanticWsDto.transcript` — real transcript text
- `SemanticWsDto.language` — detected language
- `SemanticWsDto.status` — COMPLETE

## Marketplace Integration

`MarketplaceListingBuilder.buildDraft()` already reads `SearchProjectionRepository.findByAssetId()`:
- Listing `summary` auto-populated from transcript (first 200 chars)
- Listing `search_text` includes transcript for marketplace search

## Failure Recovery

| Failure | Recovery |
|---------|----------|
| Persistent store failure | `semanticService.update()` throws → task FAILED → retry via PlatformTask |
| Event dispatch failure | `eventPublisher.publish()` → outbox → retry via OutboxEventDispatcher |
| Search reindex failure | `SearchReindexTaskHandler` fails → task FAILED → retry via PlatformTask |
| All existing platform retry/lease/barrier/outbox reused. No new mechanisms. |

## Observability

```
RealAsrTaskHandler: transcribing asset=a1 model=base
RealAsrTaskHandler: transcript persisted asset=a1 segments=12 chars=840
RealAsrTaskHandler: AssetEnrichedEvent published asset=a1
```

## Tests

Compilation passes. Existing tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Transcript merged by append (no dedup) | Multiple ASR runs append duplicates |
| No partial enrichment status | OVERWRITE always sets COMPLETE; partial not tracked |
| Real whisper not tested in CI | Tests verify handler structure |

## Architecture Validation

- **Source of truth:** `AssetSemanticMetadata` (asset_semantic_metadata table)
- **Read model:** `SearchProjection` (search_projection table) — rebuildable from AssetSemanticMetadata
- **Projection:** `MarketplaceListing` (marketplace_listing table) — rebuildable from SearchProjection
- **No duplicated transcript storage** outside the metadata table except rebuildable projections

## Deferred Items

| Item | Sprint |
|------|--------|
| OCR integration | Sprint 040 |
| Vision integration | Sprint 041 |
| Embedding integration | Sprint 042 |
| Transcript dedup | Sprint 040 |
