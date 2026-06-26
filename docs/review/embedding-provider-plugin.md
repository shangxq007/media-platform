---
status: implementation-report
created: 2026-06-25
scope: render-module + outbox-event-module
truth_level: current
owner: platform
---

# AI Platform Sprint 045 — Embedding Provider Plugin & Semantic Representation Foundation

## Capability Audit

`AssetSemanticMetadata` already had `embeddings: List<EmbeddingReference>` — no schema changes needed.

## New / Modified Components

| Component | Change |
|-----------|--------|
| `EmbeddingReference` | Extended: +model, +contentHash, +processingTimeMs, +createdAt; +`of()` factory |
| `EmbeddingProviderExtension` | New — `ProviderExtensionSPI` with TEXT_EMBEDDING, IMAGE_EMBEDDING, MULTIMODAL_EMBEDDING capabilities; self-registers via `@PostConstruct` |
| `EmbeddingTaskHandler` | New — TaskHandler (EMBEDDING capability); routes through `extensionRegistry.executeProvider("embedding-default")`; persists `EmbeddingReference` to AssetSemanticMetadata |
| `LocalProcessExecutionBackend` | +EMBEDDING capability |

## Semantic Representation Abstraction

```
EmbeddingReference {
    embeddingId    → stable identifier
    provider       → "embedding-default"
    model          → "default"
    dimension      → 768
    storageUri     → "vectors://default/emb_xxx"
    contentHash    → hash of source content
    processingTime → ms
    createdAt      → timestamp
}
```

**NEVER expose raw vectors (float[]) through the platform.** Only `EmbeddingReference` is stored. Future vector databases (pgvector, Milvus, Qdrant) are referenced by URI, not embedded in domain models.

## Four-Provider Runtime (Complete)

| Provider | Key | Capabilities | Semantic Fields |
|----------|-----|-------------|-----------------|
| Whisper | `"whisper"` | ASR, TRANSCRIBE | `transcripts` |
| Tesseract | `"tesseract"` | OCR, TEXT_EXTRACTION | `detectedTexts` |
| Vision | `"vision-default"` | VISION, OBJECT_DETECTION, SCENE_DETECTION | `scenes`, `objects`, `brands` |
| Embedding | `"embedding-default"` | TEXT_EMBEDDING, IMAGE_EMBEDDING | `embeddings` |

**All four share identical runtime pattern. Zero architecture changes between providers.**

## Runtime Flow

```
PlatformTask → EmbeddingTaskHandler → ExtensionRegistryService.executeProvider("embedding-default")
    → EmbeddingProviderExtension.execute() → ExtensionResult.success({embeddingId, dimension, storageUri})
    → EmbeddingReference.of(provider, model, dim, uri) → AssetSemanticMetadata.update(embeddings)
    → AssetEnrichedEvent → SearchConsumer → reindex
```

## Provider Governance: All 7 rules validated.

## Future: Vector Database Integration

`EmbeddingReference.storageUri` → `"vectors://default/emb_123"` or `"pgvector://table/row_id"` or `"milvus://collection/id"`

Platform never depends on implementation. Swap storage backends without changing domain models.

## Tests

Compilation passes. All existing tests unaffected.

## Deferred Items

| Item | Sprint |
|------|--------|
| BMF Provider | 046 |
| OpenCue Provider | 047 |
| Vector Store Adapter | 048 |
