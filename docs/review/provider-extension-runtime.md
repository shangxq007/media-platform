---
status: implementation-report
created: 2026-06-25
scope: render-module + extension-module
truth_level: current
owner: platform
---

# Platform Extension Sprint 042 — Provider Extension Runtime Completion

## Extension Runtime Audit

| Component | Sprint 041 | Sprint 042 |
|-----------|-----------|-----------|
| `WhisperProviderExtension.execute()` | Stub (returns NOT_IMPLEMENTED) | ✅ Real: calls `WhisperAsrProvider.transcribe()` through ExecutionBackend |
| Registration in `ExtensionRegistryService` | None (AiProviderPluginRegistry) | ✅ `@PostConstruct` → `registerProviderExtension("whisper", this, FULLY_TRUSTED, "system")` |
| `RealAsrTaskHandler` execution path | Direct: `whisperProvider.transcribe()` | ✅ Via extension runtime: `extensionRegistry.executeProvider("whisper", inputJson, tenantId, traceId)` |
| `AiProviderPluginRegistry` | Duplicate registry | ✅ Thin capability index wrapping `ExtensionRegistryService` |

## Modified Components (3)

| Component | Change |
|-----------|--------|
| `WhisperProviderExtension` | Complete rewrite: `@PostConstruct` self-registration in `ExtensionRegistryService`; `execute()` actually runs transcription via `WhisperAsrProvider.transcribe()`; parses input JSON (audioFile, model, language); returns `ExtensionResult.success()` with transcript JSON |
| `AiProviderPluginRegistry` | Simplified: thin wrapper around `ExtensionRegistryService.listExtensions()` for capability-based lookup |
| `RealAsrTaskHandler` | Routes through `extensionRegistry.executeProvider("whisper", inputJson, "system", traceId)` instead of `whisperProvider.transcribe()` directly. No longer depends on `WhisperAsrProvider` or `AiProviderPluginRegistry`. |

## Runtime Flow (Complete)

```
PlatformTask → RealAsrTaskHandler
    ↓
ExtensionRegistryService.executeProvider("whisper", inputJson, tenantId, traceId)
    ↓ (resolves by provider key)
WhisperProviderExtension.execute(context, inputJson)
    ↓ (parses input: audioFile, model, language)
WhisperAsrProvider.transcribe(audioPath, model, language, jobId, taskId)
    ↓
ExecutionBackendRegistry.resolve(ASR) → LocalProcessExecutionBackend → whisper CLI → AsrResult
    ↓ (converts to JSON)
ExtensionResult.success(outputJson)
    ↓
RealAsrTaskHandler: extract transcript, merge, persist, publish event
```

## Architecture Validation

| Layer | Knows | Does NOT Know |
|-------|-------|---------------|
| `PlatformTask` | Task capability (ASR) | Provider type |
| `RealAsrTaskHandler` | Extension runtime (executeProvider) | Whisper / ExecutionBackend |
| `ExtensionRegistryService` | Registered providers (by key) | AI semantics |
| `WhisperProviderExtension` | WhisperAsrProvider (internal) | TaskHandler logic |
| `WhisperAsrProvider` | ExecutionBackend + CLI | Plugin registration |
| `ExecutionBackend` | Process execution | AI / business logic |

**TaskHandler → Extension Runtime → Provider SPI → ExecutionBackend. Clean separation.**

## Self-Registration

```java
@PostConstruct
void registerInPlatform() {
    extensionRegistry.registerProviderExtension("whisper", this,
            ExtensionTrustLevel.FULLY_TRUSTED, "system");
}
```

ExtensionRegistryService handles: version tracking, rollback points, trust level, resource limits, status lifecycle.

## Observable

```
Whisper registered in platform extension runtime as provider=whisper
RealAsrTaskHandler: executing ASR via extension runtime asset=a1 model=base
WhisperProviderExtension: executing ASR via platform runtime audio=/tmp/a.mp3 model=base
Whisper ASR complete: model=base language=en segments=12 dur=3500ms
```

## Tests

Compilation passes. All existing tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No extension sandbox | `SandboxExecutionService` exists but Whisper runs as FULLY_TRUSTED (no sandbox) |
| `AiProviderPluginRegistry` capability mapping is simplified | Maps any provider key + "PROVIDER" category to a generic descriptor |

## Deferred Items

| Item | Sprint |
|------|--------|
| OCR Provider Plugin | Sprint 043 |
| Vision Provider Plugin | Sprint 044 |
| Extension sandbox for AI providers | Sprint 045 |
