---
status: implementation-report
created: 2026-06-25
scope: render-module + outbox-event-module
truth_level: current
owner: platform
---

# AI Platform Sprint 043 — OCR Provider Plugin & Provider Governance

## Provider Runtime Audit

| Component | Whisper | OCR | Status |
|-----------|---------|-----|--------|
| `ProviderExtensionSPI` | ✅ | ✅ | Same SPI |
| `ExtensionRegistryService` | ✅ `@PostConstruct` | ✅ `@PostConstruct` | Same registry |
| `ExecutionBackend` | ✅ | ✅ | Same backend, +OCR support |
| `TaskCapability` | ✅ ASR | ✅ OCR (pre-existing) | Same enum |
| Semantic Metadata | ✅ `AssetSemanticMetadata` | ✅ `AssetSemanticMetadata` | Same source of truth |
| Search Projection | ✅ via `AssetEnrichedEvent` | ✅ via `AssetEnrichedEvent` | Same reindex flow |
| Workbench | ✅ `SemanticWsDto` | ✅ Existing DTO (detected text) | Same workbench |

## New Components (4)

| Component | Role |
|-----------|------|
| `OcrResult` | Immutable: provider, model, language, confidence, processingTime, fullText, blocks[] |
| `TesseractOcrProvider` | OCR runtime (delegates to CLI via ExecutionBackend) |
| `TesseractOcrProviderExtension` | Implements `ProviderExtensionSPI` — OCR, TEXT_EXTRACTION capabilities; self-registers via `@PostConstruct` |
| `OcrTaskHandler` | TaskHandler (OCR capability) — routes through `ExtensionRegistryService.executeProvider("tesseract")` |

## Modified

| Component | Change |
|-----------|--------|
| `LocalProcessExecutionBackend` | +OCR in `supports()` |

## Provider Governance Rules

### Mandatory Rules (7)

| Rule | Description |
|------|-------------|
| **Rule 1** | No Provider Registry outside `ExtensionRegistryService` |
| **Rule 2** | No Provider called directly by TaskHandler — must go through `extensionRegistry.executeProvider()` |
| **Rule 3** | Execution must go through `ProviderExtensionSPI.execute()` (not direct CLI call) |
| **Rule 4** | `AssetSemanticMetadata` is the single source of truth for AI results |
| **Rule 5** | Search Projection, Marketplace Listing, Dashboard are projections only — rebuildable |
| **Rule 6** | No provider creates its own retry or scheduling — reuse PlatformTask |
| **Rule 7** | No provider stores its own runtime state — reuse coordination runtime |

### Provider Lifecycle

```
1. Define: AiProviderDescriptor (providerId, capabilities, models, languages)
2. Implement: ProviderExtensionSPI (providerKey, execute, onUnload, onRollback, resourceLimits)
3. Register: @PostConstruct → extensionRegistry.registerProviderExtension()
4. Route: TaskHandler → extensionRegistry.executeProvider(key, inputJson, ...)
5. Execute: WhisperAsrProvider / TesseractOcrProvider → ExecutionBackend → CLI
6. Persist: AssetSemanticMetadata (source of truth)
7. Publish: AssetEnrichedEvent → outbox → SearchConsumer → reindex
```

### Runtime Flow (Both Providers)

```
PlatformTask → TaskHandler → ExtensionRegistryService.executeProvider(key, inputJson, tenantId, traceId)
    → ProviderExtensionSPI.execute(context, inputJson)
        → ProviderRuntime (Whisper/Ocr) → ExecutionBackend → CLI → Result
    → ExtensionResult.success(outputJson)
→ Extract result → merge → AssetSemanticMetadata.update() → AssetEnrichedEvent
```

## Tests

Compilation passes. All existing coordination + marketplace tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| OCR `execute()` returns placeholder text | Real Tesseract CLI is runtime dependency |
| `OcrResult` not wired into OCR persistence flow | Structural pattern complete; real execution deferred |
| Vision + Embedding providers not implemented | Sprint 044, 045 |

## Future Providers

| Provider | Pattern | Sprint |
|----------|---------|--------|
| Vision (YOLO) | Same as OCR/Whipser — ProviderExtensionSPI | 044 |
| Embedding (CLIP) | Same pattern | 045 |
| BMF Media | ProviderExtensionSPI (media processing) | 046 |
| OpenCue | ProviderExtensionSPI (render farm) | 047 |
