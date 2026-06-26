---
status: implementation-report
created: 2026-06-25
scope: render-module + outbox-event-module
truth_level: current
owner: platform
---

# AI Processing Sprint 038 — Real Whisper ASR Integration

## ASR Capability Audit

| Capability | Before | After |
|-----------|--------|-------|
| ASR handler | `MockAsrTaskHandler` (fixed text, 3 segments) | `RealAsrTaskHandler` (delegates to Whisper via ExecutionBackend) |
| ASR provider | `MockWhisperAsrProvider` (SemanticMetadataProvider SPI) | `WhisperAsrProvider` (CLI-based, JSON parsing) |
| Execution backend | PROBE only | PROBE + ASR |
| Result model | None (inline Transcript) | `AsrResult` with segments, language, duration, processing time |

## New Components (3)

| Component | Role |
|-----------|------|
| `AsrResult` | Domain model: provider, model, language, duration, processingTime, fullTranscript, segments[] |
| `AsrResult.AsrSegment` | startMs, endMs, text, confidence |
| `WhisperAsrProvider` | Runs whisper CLI via `ExecutionBackendRegistry.resolve(ASR)`, parses JSON output |
| `RealAsrTaskHandler` | TaskHandler for ASR capability — extracts audio path from payload, calls provider, validates result |

## Modified Components

| Component | Change |
|-----------|--------|
| `LocalProcessExecutionBackend` | +`ASR` support in `supports()` |

## Runtime Flow

```
Asset → Platform Job → ASR Task → PlatformTaskDispatcher → RealAsrTaskHandler
    ↓
ExecutionBackendRegistry.resolve(ASR) → LocalProcessExecutionBackend
    ↓
ProcessBuilder → whisper audio.mp3 --model base --output_format json --output_dir /tmp/whisper
    ↓
stdout (JSON) → WhisperAsrProvider.parseWhisperOutput()
    ↓
AsrResult → validate (fullTranscript non-empty) → completeTask
```

## Whisper CLI

```bash
whisper audio.mp3 --model base --output_format json --output_dir /tmp/whisper
```
- Supported models: tiny, base, small, medium, large
- Output: JSON with text, language, duration, segments[].start/.end/.text/.confidence
- Timeout: 300s (5 minutes)

## Failure Handling

| Error | Result | Recovery |
|-------|--------|----------|
| Whisper not installed | `exitCode != 0` → `ExecutionResult.failure()` → task FAILED → retry |
| Timeout (>300s) | `Process.destroyForcibly()` → `TIMEOUT` error → retry |
| Invalid JSON output | `parseWhisperOutput()` throws → task FAILED → retry |
| Empty transcript | `AsrResult.isValid()` false → task FAILED |

All failures reuse existing `PlatformTask.attemptCount` + `maxAttempts` + retry with exponential backoff.

## Search Integration

After ASR completes, the `AssetEnrichedEvent` triggers the existing search reindex flow:
`AssetEnrichedEvent → SearchConsumer → SEARCH_REINDEX job → REINDEX task → SearchProjection updated with transcript text`

## Tests

Compilation passes. All existing tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Whisper binary must be installed on worker | Runtime dependency |
| No GPU acceleration support | CPU-only via CLI |
| ASR result not persisted to DB yet | Handler validates but doesn't write to `AssetSemanticMetadata` |
| No speaker diarization | Future enhancement |

## Deferred Items

| Item | Sprint |
|------|--------|
| Persist ASR result to AssetSemanticMetadata | Sprint 039 |
| OCR integration | Sprint 039 |
| Vision integration | Sprint 040 |
| Embedding integration | Sprint 041 |
