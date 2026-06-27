---
status: design
created: 2026-06-28
scope: render-module
truth_level: design
owner: platform
---

# Timeline Compile Contract v0

## Purpose

Define the deterministic compile-ready subset of the TimelineRevision / Timeline model for v0 of the compile pipeline:

```text
TimelineRevision
→ NormalizedTimeline v0
→ ArtifactDependencyGraph v0
→ LogicalCapabilityGraph v0
→ ProviderExecutionDocument model reservation
```

## Source Model

The compile pipeline takes a `TimelineSpec` (parsed from TimelineRevision snapshot JSON) as input.

### TimelineSpec Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Compile-participating |
| `name` | String | Metadata-only |
| `description` | String | Metadata-only |
| `tracks` | List\<TimelineTrack\> | Compile-participating |
| `textOverlays` | List\<TimelineTextOverlay\> | Compile-participating (caption placeholder) |
| `outputSpec` | TimelineOutputSpec | Compile-participating |
| `totalDuration` | double | Metadata-only (computed) |
| `metadata` | Map\<String, String\> | Metadata-only |

### TimelineTrack Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Compile-participating |
| `name` | String | Metadata-only |
| `type` | TrackType (VIDEO, AUDIO, SUBTITLE) | Compile-participating |
| `layer` | int | Compile-participating (ordering) |
| `clips` | List\<TimelineClip\> | Compile-participating |
| `muted` | boolean | Compile-participating |
| `locked` | boolean | Metadata-only |

### TimelineClip Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Compile-participating |
| `assetRef` | TimelineAssetRef | Compile-participating |
| `timelineStart` | double | Compile-participating |
| `assetInPoint` | double | Compile-participating |
| `assetOutPoint` | double | Compile-participating |
| `clipDuration` | double | Compile-participating |
| `effects` | List\<TimelineClipEffect\> | Compile-participating (v0: fail-closed for unsupported) |

### TimelineAssetRef Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `assetId` | String | Compile-participating |
| `storageUri` | String | Compile-participating |
| `format` | String | Compile-participating |
| `duration` | long | Compile-participating |
| `width` | int | Compile-participating |
| `height` | int | Compile-participating |
| `metadata` | Map\<String, String\> | Metadata-only |

### TimelineOutputSpec Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `format` | String | Compile-participating |
| `resolution` | String | Compile-participating |
| `frameRate` | double | Compile-participating |
| `videoCodec` | String | Compile-participating |
| `videoBitrate` | int | Compile-participating |
| `audioSpec` | TimelineAudioSpec | Compile-participating |
| `pixelFormat` | String | Compile-participating |

### TimelineTextOverlay Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Compile-participating |
| `text` | String | Compile-participating |
| `fontFamily` | String | Compile-participating |
| `fontSize` | int | Compile-participating |
| `color` | String | Compile-participating |
| `positionX` | String | Compile-participating |
| `positionY` | String | Compile-participating |
| `startTime` | double | Compile-participating |
| `duration` | double | Compile-participating |
| `backgroundColor` | String | Compile-participating |

### TimelineClipEffect Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Compile-participating |
| `effectKey` | String | Compile-participating (v0: unsupported → fail-closed) |
| `packId` | String | Future extension |
| `packVersion` | String | Future extension |
| `providerPreference` | List\<String\> | Future extension (provider binding) |
| `parameters` | Map\<String, Object\> | Compile-participating (v0: unsupported → fail-closed) |

### TimelineTransition Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| `id` | String | Future extension |
| `effectKey` | String | Future extension |
| `durationSeconds` | double | Future extension |
| `inClipId` | String | Future extension |
| `outClipId` | String | Future extension |

**Note:** TimelineTransition is defined but not currently used in TimelineClip. Clips carry effects, not transitions. Transitions are a future extension.

### TimelineMarker Fields

| Field | Type | Compile Classification |
|-------|------|----------------------|
| (various) | — | Ignored-for-v0 |

## v0 Compile Contract Rules

### Time Unit

- All times are in **seconds** (double).
- `timelineStart`, `assetInPoint`, `assetOutPoint`, `clipDuration`, `startTime`, `duration` are all seconds.
- `totalDuration` is computed as `max(track.totalDuration())` across all tracks.

### Track Ordering Rule

- Tracks are ordered by `layer` field (ascending: lower layer = behind).
- For same-layer tracks, preserve original list order (deterministic).
- Track type priority for normalization: VIDEO > AUDIO > SUBTITLE.

### Clip Ordering Rule

- Clips within a track are ordered by `timelineStart` (ascending).
- For same-start clips, preserve original list order (deterministic).

### Asset Reference Rule

- Each clip references exactly one asset via `TimelineAssetRef`.
- `assetId` is the canonical asset identifier.
- `storageUri` is the storage locator (e.g., `asset://...` or `s3://...`).
- `format`, `width`, `height`, `duration` provide media metadata.

### Single-Primary-Input Behavior

- Current render path (R6.1) supports **single-primary-input only**.
- `TimelineInputProductResolver` resolves the first sourceAssetId to the primary input Product.
- Multiple input tracks are **not yet supported** in the compile pipeline.
- v0 compile should handle single-clip, single-track timelines deterministically.
- Multi-clip timelines should produce a valid graph but actual multi-input rendering is future work.

### Multi-Input Limitation

- v0 does **not** support multi-track compositing.
- v0 does **not** support multi-clip sequencing in a single render pass.
- Multi-input support is deferred to v1+ compile pipeline.

### Subtitle/Caption Behavior

- `TimelineTextOverlay` items are recognized as caption/subtitle placeholders.
- v0 maps text overlays to `SUBTITLE_OVERLAY` artifact nodes.
- Actual subtitle burn-in depends on provider (FFmpeg/libass for v0).
- `TimelineTrack` with type `SUBTITLE` is recognized but v0 does not fully compile subtitle tracks.

### Unsupported Transition Behavior

- `TimelineTransition` is defined but **not used** in current TimelineClip model.
- Transitions are a future extension field.
- v0 does not process transitions.
- If a clip carries unsupported effects, v0 fails closed with a validation error.

### Unsupported Effect Behavior

- `TimelineClipEffect` with non-empty `effects` list triggers fail-closed validation in v0.
- v0 does **not** compile clip effects.
- Effect compilation is deferred to v1+ compile pipeline.
- Unsupported effects produce a `TimelineCompileException` with clear error message.

### Output Profile Rule

- `TimelineOutputSpec` provides the output format, resolution, codec, and audio spec.
- v0 normalizes the output spec into a deterministic `NormalizedOutputProfile`.
- Defaults are filled deterministically if fields are missing:
  - format: `mp4`
  - resolution: `1920x1080`
  - frameRate: `30.0`
  - videoCodec: `h264`
  - videoBitrate: `8000`
  - audioSpec: AAC 48kHz stereo 128kbps
  - pixelFormat: `yuv420p`

### Validation / Fail-Closed Rules

- Missing required fields (id, tracks, outputSpec) → fail closed.
- Empty tracks list → fail closed.
- Clip with no assetRef → fail closed.
- Clip with invalid timing (out <= in, duration <= 0) → fail closed.
- Unsupported clip effects → fail closed (v0).
- Missing source asset → fail closed at materialization time, not compile time.

### Determinism Requirements

- Same TimelineRevision → same NormalizedTimeline (byte-stable JSON).
- Same NormalizedTimeline → same ArtifactDependencyGraph (stable node IDs, stable edges).
- Same ArtifactDependencyGraph → same LogicalCapabilityGraph (stable capability mappings).
- No random IDs in compile output. All IDs are derived from input content hashes or deterministic sequences.

## v0 Safe Subset Summary

| Concept | v0 Status | Notes |
|---------|-----------|-------|
| Single video clip | ✅ Supported | Primary use case |
| Single video clip + output profile | ✅ Supported | Output spec normalization |
| Single video clip + caption placeholder | ✅ Supported | TextOverlay → SUBTITLE_OVERLAY node |
| Two sequential clips | ⚠️ Graph only | v0 produces graph but single-primary-input render |
| Multiple tracks | ⚠️ Graph only | v0 produces graph but no multi-track compositing |
| Clip effects | ❌ Fail-closed | Unsupported in v0 |
| Transitions | ❌ Not applicable | Not used in current model |
| Multi-input render | ❌ Future | v1+ compile pipeline |

## Compile Pipeline Architecture

```text
TimelineRevision
  → load snapshot JSON
  → parse to TimelineSpec
  → TimelineNormalizationService.normalize()
    → NormalizedTimeline (deterministic, provider-neutral)
  → ArtifactGraphCompiler.compile()
    → ArtifactDependencyGraph (deterministic, provider-neutral, acyclic)
  → CapabilityGraphCompiler.compile()
    → LogicalCapabilityGraph (deterministic, provider-neutral)
  → [future: ProviderBindingPlan]
  → [future: ProviderExecutionDocument]
  → [future: RenderExecutionPlan]
```

## Non-Goals for v0

- No provider binding
- No tool command generation
- No OpenCue mapping
- No cache/incremental render
- No multi-input compositing
- No effect compilation
- No transition compilation
- No public API changes

## Related Documents

- `docs/architecture/blueprint/otio-render-platform-blueprint.md` — Full blueprint
- `docs/review/multi-provider-poc-integration-report.md` — Provider integration pattern
- `docs/review/storage-runtime-foundation.md` — StorageRuntime semantics
- `docs/render/capability-matrix.md` — Capability matrix
