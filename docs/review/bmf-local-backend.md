---
status: implementation-report
created: 2026-06-25
scope: outbox-event-module + render-module
truth_level: current
owner: platform
---

# Sprint 046 — BMF Local ExecutionBackend Foundation

## Architecture Validation

### ADR-006 Implementation
BMF integrates as `BmfExecutionBackend` (ExecutionBackend SPI). No new runtime. No new registry. BMF operators are internal to BMF — NOT platform concepts.

## New Components (2)

| Component | Role |
|-----------|------|
| `BmfGraphDefinition` | Minimal graph model: graphType, parameters, inputs[], outputs[]. Factory methods: `transcode()`, `thumbnail()` |
| `BmfExecutionBackend` | Implements `ExecutionBackend` — backendId="bmf", supports MEDIA_PIPELINE, TRANSCODE, FRAME_EXTRACTION, FILTER, THUMBNAIL |

## Modified (1)

| Component | Change |
|-----------|--------|
| `TaskCapability` | +5 entries: MEDIA_PIPELINE, TRANSCODE, FRAME_EXTRACTION, FILTER, THUMBNAIL |

## Execution Flow

```
PlatformTask → TaskHandler → ExtensionRegistryService → ProviderExtensionSPI
    → ExecutionBackendRegistry.resolve(MEDIA_PIPELINE) → BmfExecutionBackend
    → BMF Graph (via CLI/subprocess or native SDK)
    → ExecutionResult → Artifact
```

## ExecutionBackend Registry

`BmfExecutionBackend` is auto-discovered by `ExecutionBackendRegistry` via Spring `@Component` — same pattern as `LocalProcessExecutionBackend`. No special registration.

## Graph Definition Model

```java
BmfGraphDefinition {
    graphType: "TRANSCODE" | "FRAME_EXTRACTION" | "THUMBNAIL" | "MEDIA_PIPELINE"
    parameters: {format: "mp4", width: 1920, height: 1080}
    inputs: [{storageUri, mediaType, checksum, sizeBytes}]
    outputs: [{label, mediaType, outputKey}]
}
```

Payload passes through `ExecutionRequest.payload` map. No separate execution request type needed.

## Phase 1: CLI Placeholder

Current implementation uses simulated execution (50ms sleep). Phase 2 replaces with native BMF SDK graph execution.

## OpenCue Compatibility

`OpenCueExecutionBackend` replaces `BmfExecutionBackend` via same `ExecutionBackend` SPI:
- Same `supports()` capabilities
- Same `execute(ExecutionRequest) → ExecutionResult`
- Providers unchanged. TaskHandler unchanged. Coordination unchanged.

## Tests

Compilation passes. All existing coordination tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| CLI/subprocess placeholder | Phase 2: native BMF SDK integration |
| No GPU acceleration | Phase 3: GPU operator routing |
| No graph optimizer | Phase 4: graph optimization |
| No distributed execution | OpenCue (Sprint 048) |
