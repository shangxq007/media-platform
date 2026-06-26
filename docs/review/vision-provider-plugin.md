---
status: implementation-report
created: 2026-06-25
scope: render-module + outbox-event-module
truth_level: current
owner: platform
---

# AI Platform Sprint 044 — Vision Provider Plugin

## Capability Audit

| Semantic Field | Before | After |
|---------------|--------|-------|
| `scenes` | ✅ Exists | ✅ Populated by Vision |
| `objects` | ✅ Exists | ✅ Populated by Vision |
| `brands` | ✅ Exists | ✅ Populated by Vision |
| `people` | ✅ Exists | Future |
| Vision status | ❌ | ✅ Capability shown in workbench |

## New Components (3)

| Component | Role |
|-----------|------|
| `VisionResult` | Immutable: provider, model, processingTime, frames[], objects[], scenes[], brands[], people[] |
| `VisionProviderExtension` | `ProviderExtensionSPI` — VISION, OBJECT_DETECTION, SCENE_DETECTION, BRAND_DETECTION, PEOPLE_DETECTION; self-registers via `@PostConstruct`; `execute()` returns structured JSON |
| `VisionTaskHandler` | TaskHandler (VISION capability) — routes through `extensionRegistry.executeProvider("vision-default", ...)`; parses objects/scenes/brands; persists to AssetSemanticMetadata |

## Modified (1)

`LocalProcessExecutionBackend` — +VISION capability

## Runtime Flow (Same as Whisper/OCR)

```
PlatformTask → VisionTaskHandler → ExtensionRegistryService.executeProvider("vision-default", ...)
    → VisionProviderExtension.execute(context, inputJson) → ExtensionResult.success(outputJson)
        → objects[{label, confidence, timeMs}], scenes[{label, startMs, endMs, confidence}], brands[{brandName, confidence}]
    → VisionTaskHandler: parse → AssetSemanticMetadata.update(objects, scenes, brands)
    → eventPublisher.publish(AssetEnrichedEvent) → SearchConsumer → reindex
```

## Provider Governance Validation

| Rule | Satisfied? |
|------|-----------|
| No Provider Registry outside ExtensionRegistryService | ✅ |
| No direct Provider call by TaskHandler | ✅ Routes through `extensionRegistry.executeProvider()` |
| Execution through `ProviderExtensionSPI.execute()` | ✅ |
| Single source of truth: AssetSemanticMetadata | ✅ Objects/scenes/brands persisted there |
| No provider retry/scheduling | ✅ Reuses PlatformTask |
| No provider runtime state | ✅ No state storage |

## Multi-modal Foundation

Three providers (Whisper, OCR, Vision) all follow identical pattern:
- `ProviderExtensionSPI` implementation
- `@PostConstruct` self-registration
- `ExtensionRegistryService.executeProvider()` routing
- `AssetSemanticMetadata` persistence
- `AssetEnrichedEvent` → Search reindex

**No architecture changes needed for Vision.**

## Tests

Compilation passes. All existing tests unaffected.

## Deferred Items

| Item | Sprint |
|------|--------|
| Embedding Provider | 045 |
| BMF Provider | 046 |
| OpenCue Provider | 047 |
