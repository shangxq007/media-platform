---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F5 — Producer Runtime Integration

## Producer Migration Matrix

| Producer | producerId | supportedOutputTypes | TaskHandler? | Producer? |
|----------|-----------|---------------------|-------------|-----------|
| Whisper | `"whisper-asr"` | TRANSCRIPT | ✅ ASR | ✅ |
| OCR | `"tesseract-ocr"` | OCR | ✅ OCR | ✅ |
| Vision | `"vision-default"` | VISION | ✅ VISION | ✅ |
| Embedding | `"embedding-default"` | EMBEDDING | ✅ EMBEDDING | ✅ |

## Modified Files (4)

| File | Change |
|------|--------|
| `RealAsrTaskHandler.java` | +`implements Producer`; producerId="whisper-asr", outputs=[TRANSCRIPT] |
| `OcrTaskHandler.java` | +`implements Producer`; producerId="tesseract-ocr", outputs=[OCR] |
| `VisionTaskHandler.java` | +`implements Producer`; producerId="vision-default", outputs=[VISION] |
| `EmbeddingTaskHandler.java` | +`implements Producer`; producerId="embedding-default", outputs=[EMBEDDING] |

## Unified Execution Flow

All producers can now be invoked through:
```
ProducerRuntimeService.execute(producerId, context) → Producer SPI → ProducerResult
```

Producers are auto-discovered by `ProducerRuntimeService` via Spring `List<Producer>` injection.

## Producer Rules (Validated)

Every producer:
- Receives Product IDs via `ProducerContext` (future integration)
- Returns `ProducerResult`
- Registers as both `TaskHandler` (coordination) and `Producer` (execution)

## Tests

Compilation passes. All existing tests unaffected. No business behavior changed.

## Deferred Items
- Wire ProducerContext with actual Product IDs (from ProductRuntime)
- Wire ProducerResult with actual produced products
- Full Product Runtime + Storage Runtime orchestration in execute() path
