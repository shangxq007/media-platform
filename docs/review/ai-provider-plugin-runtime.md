---
status: implementation-report
created: 2026-06-25
scope: render-module + extension-module
truth_level: current
owner: platform
---

# Platform Extension Sprint 041 — AI Provider Plugin Runtime

## Plugin Architecture Audit

| Component | Exists? | Reused? |
|-----------|---------|---------|
| `ProviderExtensionSPI` | ✅ | Whisper implements it |
| `ExtensionRegistryService` | ✅ | AI registers through it (future) |
| `ExtensionContext` | ✅ | Passed to execute() |
| `ExtensionResult` | ✅ | Returned from execute() |
| `ExtensionTrustLevel` | ✅ | FULLY_TRUSTED for Whisper |
| `ExtensionResourceLimits` | ✅ | 300s timeout, 2GB memory |
| PF4J `PluginManager` | ✅ Configured, dormant | Available for JAR plugins |

## New Components (3)

| Component | Role |
|-----------|------|
| `AiProviderDescriptor` | Immutable metadata: providerId, displayName, version, vendor, capabilities, mediaTypes, languages, models, executionBackend, priority |
| `WhisperProviderExtension` | Implements `ProviderExtensionSPI` — declares ASR/TRANSCRIBE/LANGUAGE_DETECTION, JSON input/output schemas, resource limits |
| `AiProviderPluginRegistry` | Resolves providers by capability (e.g., "ASR" → Whisper), auto-discovers via Spring injection |

## Modified Components

| Component | Change |
|-----------|--------|
| `RealAsrTaskHandler` | +`AiProviderPluginRegistry` dependency; resolves provider by capability before transcription |

## Runtime Flow

```
PlatformTask → TaskHandler → AiProviderPluginRegistry.resolveByCapability("ASR")
    → WhisperProviderExtension (ProviderExtensionSPI) → provider descriptor
    → WhisperAsrProvider.transcribe() → ExecutionBackend → ProcessBuilder → whisper CLI
    → AsrResult → Transcript → AssetSemanticMetadata → AssetEnrichedEvent
```

**TaskHandler knows only Plugins. Plugin knows only AI. ExecutionBackend knows only execution.**

## Architecture Validation

| Layer | Knows | Does NOT Know |
|-------|-------|---------------|
| `Platform Task` | Task Capability | Provider type |
| `TaskHandler` | Plugin Registry | Execution details |
| `AiProviderPluginRegistry` | Provider descriptors | Execution |
| `WhisperProviderExtension` | `ProviderExtensionSPI` contract | TaskHandler logic |
| `ExecutionBackend` | Process args, timeout | AI semantics |
| `WhisperAsrProvider` | Whisper CLI, JSON parsing | Plugin registration |

## Workbench Integration

`AiProviderDescriptor` fields available for workbench:
- `displayName` — "Whisper ASR"
- `version` — "1.0"
- `capabilities` — ["ASR", "TRANSCRIBE", "LANGUAGE_DETECTION"]
- `supportedLanguages` — ["en"]
- `supportedModels` — ["base"]

## Observable

```
AI Provider registered: Whisper ASR (v1.0) capabilities=[ASR,TRANSCRIBE] media=[VIDEO,AUDIO] languages=[en]
RealAsrTaskHandler: selected provider=Whisper ASR v1.0 for asset=a1
```

## Future Plugin Types

| Type | SPI | Example |
|------|-----|---------|
| AI Provider | `ProviderExtensionSPI` | Whisper, Tesseract OCR, YOLO Vision |
| Media Provider | `ProviderExtensionSPI` | BMF, FFmpeg |
| Storage Provider | New SPI (future) | S3, OSS, GCS |
| Workflow Provider | `WorkflowStepExtensionSPI` | Temporal steps |
| Marketplace Provider | New SPI (future) | Payment, Install |

## Tests

Compilation passes. Existing RealAsrTaskHandler tests and marketplace tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `execute()` on WhisperProviderExtension is stub | Returns `NOT_IMPLEMENTED` — transcription goes through `WhisperAsrProvider` directly |
| Plugin not registered in ExtensionRegistryService | Registered via `AiProviderPluginRegistry` (Spring-injected) — not yet in the platform registry |
| No PF4J JAR loading | PF4J configured but dormant |

## Deferred Items

| Item | Sprint |
|------|--------|
| Self-register in ExtensionRegistryService | Sprint 042 |
| OCR Plugin | Sprint 042 |
| Vision Plugin | Sprint 043 |
