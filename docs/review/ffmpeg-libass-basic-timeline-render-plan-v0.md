# FFmpeg/libass Basic Timeline Render Plan v0 (P2R.3)

## 1. Purpose

Pure, side-effect-free FFmpeg/libass Basic Timeline Render Plan that composes BasicTimeline validation, FFmpeg baseline effect planning, FFmpeg baseline transition planning, caption/watermark overlay semantics, and output profile validation into a deterministic internal render plan.

## 2. Relationship to Current Project Goal

Supports:
- Complete basic timeline editing
- Complete basic rendering foundation
- Complete architecture boundary construction
- Prepare for OpenCue deployment testing

Foundation for:
- P2X.0 — API Scenario Runner and E2E Validation Harness
- P2O.0 — OpenCue PVE Testbed Smoke Harness
- Future RenderExecutionPlan integration
- Future Local Runner integration

## 3. Relationship to Basic Timeline Editing

Consumes P2TLE.0 types:
- `TimelineSpec` / `BasicTimeline` — source timeline
- `TimelineTrack`, `TimelineClip` — structural context
- `TimelineTextOverlay` — caption overlays
- `TimelineClipEffect` — effect references
- `TimelineOutputSpec` — output profile
- `BasicTimelineValidator` — timeline validation

Does not call `BasicTimelineEditor.apply()`.

## 4. Relationship to Visual Capability Contract

Uses P2R.0 vocabulary indirectly through P2R.1/P2R.2 planners:
- `EffectCapabilityProfile` — resolves effect keys to capability definitions
- `TransitionCapabilityProfile` — resolves transition keys to capability definitions
- `VisualCapabilityPolicy` — checks forbidden/restricted/POC status

## 5. Relationship to P2R.1 Effect Plan

Delegates to `FFmpegBaselineEffectPlanner.plan()` for effect planning. Consumes `FFmpegBaselineEffectPlan` result. Effect operations become `APPLY_EFFECT_OPERATION` render steps.

## 6. Relationship to P2R.2 Transition Plan

Delegates to `FFmpegBaselineTransitionPlanner.plan()` for transition planning. Consumes `FFmpegBaselineTransitionPlan` result. Transition operations become `APPLY_TRANSITION_OPERATION` render steps.

## 7. Relationship to FFmpeg/libass Baseline

FFmpeg/libass is the current production baseline. P2R.3 produces internal plan vocabulary only. No raw FFmpeg commands, no shell commands, no filter_complex exposure. FFmpeg/libass is not called by P2R.3.

## 8. Render Planning Scope

First version is conservative and full-render only:

| Stage | Type | Description |
|-------|------|-------------|
| 1 | VALIDATE_TIMELINE | Validate timeline structure |
| 2 | PREPARE_INPUTS | Declare output profile |
| 3 | PLAN_CLIP_SEQUENCE | Declare input clips in timeline order |
| 4 | PLAN_EFFECTS | Include effect operations from P2R.1 |
| 5 | PLAN_TRANSITIONS | Include transition operations from P2R.2 |
| 6 | PLAN_CAPTION_OVERLAYS | Apply caption overlay steps |
| 7 | PLAN_WATERMARK_OVERLAYS | Apply watermark overlay steps |
| 8 | PLAN_FINAL_ASSEMBLY | Assemble clip sequences |
| 9 | PLAN_OUTPUT_ENCODING | Encode output |
| 10 | PLAN_OUTPUT_VERIFICATION | Verify output |

## 9. Plan Model

```
FFmpegLibassBasicRenderPlan(id, status, stages, summary, issues, safeMetadata)
```

Status: READY, VALID_WITH_WARNINGS, INVALID, BLOCKED, UNSUPPORTED, FAILED

## 10. Stage Model

```
FFmpegLibassBasicRenderStage(id, type, status, steps, safeMetadata)
```

Stage types: VALIDATE_TIMELINE, PREPARE_INPUTS, PLAN_CLIP_SEQUENCE, PLAN_EFFECTS, PLAN_TRANSITIONS, PLAN_CAPTION_OVERLAYS, PLAN_WATERMARK_OVERLAYS, PLAN_AUDIO, PLAN_METADATA, PLAN_FINAL_ASSEMBLY, PLAN_OUTPUT_ENCODING, PLAN_OUTPUT_VERIFICATION

## 11. Step Model

```
FFmpegLibassBasicRenderStep(id, type, target, parameters, source, safeMetadata)
```

Step types: VALIDATE_TIMELINE, DECLARE_INPUT_CLIP, DECLARE_OUTPUT_PROFILE, APPLY_EFFECT_OPERATION, APPLY_TRANSITION_OPERATION, APPLY_CAPTION_OVERLAY, APPLY_WATERMARK_OVERLAY, ASSEMBLE_CLIP_SEQUENCE, ENCODE_OUTPUT, VERIFY_OUTPUT, DECLARE_AUDIO_TRACK, APPLY_AUDIO_OPERATION, DECLARE_SAFE_METADATA

## 12. Parameter Model

Typed parameters with `FFmpegLibassBasicRenderStepParameterType`: STRING, INTEGER, DECIMAL, BOOLEAN, DURATION_MS, PERCENT, PIXEL, RATIO, ENUM, COLOR, SAFE_REF.

All parameters are semantic only — no raw FFmpeg commands, no filtergraph strings, no provider-specific parameters.

## 13. Output Profile Validation

- MP4 container accepted
- H264/H265/HEVC/VP8/VP9 video codecs accepted
- AAC/MP3/Opus/Vorbis/FLAC audio codecs accepted
- Width/height/fps required and validated
- Unsupported container/video codec blocked
- Unsupported audio codec warned

## 14. Caption Overlay Planning

- Each `TimelineTextOverlay` produces an `APPLY_CAPTION_OVERLAY` step
- Requires valid text (non-blank)
- Requires valid time range (startTime >= 0, duration > 0)
- Parameters: captionId, startMs, endMs, textRef

## 15. Watermark Overlay Planning

- Watermarks stored in timeline metadata (current model limitation)
- Detected via `watermark.placement` or `watermark.opacity` metadata keys
- Opacity validated to 0..1 range
- Parameters: watermarkId, placement, opacity

## 16. Policy Model

```
FFmpegLibassBasicRenderPolicy(
    allowWarnings, allowPocEffects, allowPocTransitions,
    failOnTimelineWarnings, failOnEffectWarnings, failOnTransitionWarnings,
    failOnUnsupportedOutputProfile,
    requireCaptionOverlayValidation, requireWatermarkOverlayValidation
)
```

Default conservative policy:
- allowWarnings = true
- allowPocEffects = false
- allowPocTransitions = false
- failOnUnsupportedOutputProfile = true
- requireCaptionOverlayValidation = true
- requireWatermarkOverlayValidation = true

## 17. Deterministic Ordering

Stage ordering is fixed (10 stages in predefined order).

Step ordering within stages:
1. Timeline order
2. Track order
3. Clip timelineStart
4. Caption startTime
5. Watermark startMs if available
6. Operation type enum order
7. Entity id lexicographic

## 18. Safety Boundaries

- No raw FFmpeg commands
- No shell command generation
- No filter_complex exposure
- No provider-specific parameters
- No storage/internal path exposure
- No Artifact DAG usage
- No Remotion execution
- No OpenCue job/layer/frame IDs
- No global optimization
- No parallel segment rendering
- No incremental render
- No RenderExecutionPlan integration

## 19. Relationship to Future RenderExecutionPlan

```
BasicTimeline
  → FFmpeg/libass Basic Timeline Render Plan
  → RenderExecutionPlan
  → Local Runner or OpenCue ExecutionEnvironment
  → output verification
  → Product registration
```

P2R.3 only implements the Basic Timeline Render Plan.

## 20. Relationship to Future Local Runner

Future Local Runner will consume the render plan stages and steps. P2R.3 does not implement Local Runner.

## 21. Relationship to Future OpenCue

```
FFmpeg/libass Basic Timeline Render Plan
  → future RenderExecutionPlan
  → future OpenCue job/layer/frame mapping
```

P2R.3 does not call OpenCue. P2R.3 does not include OpenCue job/layer/frame IDs.

## 22. Relationship to Future Parallel Segment/Layer Rendering

Future only:
- Parallel segment rendering
- Layer rendering
- Intermediate products
- Multi-stage OpenCue execution
- Artifact DAG / cache / incremental render

Current P2R.3 is full explicit render planning only.

## 23. Artifact DAG Boundary

No Artifact DAG dependency. Artifact DAG is indefinitely deferred (P2A.2/ADR-025).

## 24. What is Intentionally Not Implemented

- FFmpeg execution
- libass execution
- Shell command generation
- Raw filtergraph exposure
- Local Runner
- OpenCue integration
- RenderExecutionPlan integration
- RenderJob/Product creation
- StorageRuntime/ProductRuntime calls
- Artifact DAG
- Incremental render
- Partial render
- Cache reuse
- Parallel segment/layer rendering
- Database tables / migrations / repositories
- Controllers / public APIs

## 25. Follow-up Tasks

- P2X.0: API Scenario Runner and E2E Validation Harness
- P2L.0: Local Explicit Render Smoke Harness
- P2O.0: OpenCue PVE Testbed Smoke Harness
- Future: RenderExecutionPlan integration
- Future: Local Runner integration
- Future: OpenCue job/layer/frame mapping

## 26. P2X.0 Status

P2X.0 introduced an internal API/Agent Scenario Runner and E2E Validation Harness. It validates the current core planning flow from timeline editing through visual capability validation, FFmpeg baseline effect planning, FFmpeg baseline transition planning, and FFmpeg/libass basic timeline render planning. It does not execute FFmpeg, does not call OpenCue, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not expose public APIs, and does not use Artifact DAG.

## 27. P2L.0 Status

P2L.0 introduced a local-only explicit render smoke harness. It allows controlled FFmpeg/ffprobe execution only inside a local smoke boundary with fixed binary allowlist, no shell invocation, no user-provided command, no raw user filtergraph, timeout enforcement, controlled output directory, and optional execution gated by an explicit system property. It does not implement public API, RenderExecutionPlan integration, OpenCue integration, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion execution, or Artifact DAG.

## 28. P2L.1 Status

P2L.1 introduced the first BasicRenderPlan-to-local-runner bridge. It consumes FFmpegLibassBasicRenderPlan and maps a conservative supported subset (DECLARE_OUTPUT_PROFILE, ENCODE_OUTPUT, VERIFY_OUTPUT) to controlled local FFmpeg/ffprobe execution through the P2L.0 boundary. Uses synthetic testsrc input. Unsupported steps are reported as warnings. Execution remains disabled by default. Does not implement full timeline rendering, RenderExecutionPlan integration, OpenCue integration, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion execution, or Artifact DAG.

## 29. P2L.2 Status

P2L.2 expands the BasicRenderPlan-to-local-runner bridge to support caption overlay. Recognizes APPLY_CAPTION_OVERLAY steps, extracts safe typed caption fields, generates a platform-owned ASS subtitle file, and burns it in via FFmpeg/libass. Caption text is sanitized (braces/backslashes removed, length bounded to 200 chars). No raw filtergraph, no raw ASS style, no external subtitle path, no font path. Caption overlay counts included in result/report. Execution remains disabled by default. Does not implement full caption rendering, full timeline rendering, RenderExecutionPlan integration, OpenCue integration, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion execution, or Artifact DAG.

## 30. P2L.3 Status

P2L.3 expands the local runner from synthetic testsrc input to controlled real media fixture input. Generates a deterministic input-fixture.mp4 under the controlled output root using FFmpeg testsrc. Validates input and output with ffprobe. Preserves caption overlay support on real media input. Controlled local fixture only — rejects arbitrary user paths, remote URLs, storage references. Input source metadata included in result/report. Execution remains disabled by default. Does not implement arbitrary user media ingestion, StorageRuntime materialization, ProductRuntime, RenderExecutionPlan, OpenCue, ProviderBindingRegistry, Remotion, or Artifact DAG.
