# Basic Timeline Editing Model and Validation v0 (P2TLE.0)

## 1. Purpose

Establish a basic, API/Agent-editable Timeline Editing Model and Validation layer. Enable creation, modification, validation, and inspection of a basic timeline through domain/application objects, without requiring a formal frontend.

## 2. Relationship to Current Project Goal

Supports:
- Complete basic timeline editing
- Complete basic rendering foundation
- Complete architecture boundary construction
- Prepare for OpenCue deployment testing

Foundation for:
- P2R.1 — FFmpeg Baseline Effect Plan
- P2R.2 — FFmpeg Baseline Transition Plan
- P2R.3 — FFmpeg/libass Basic Timeline Render Plan
- P2X.0 — API Scenario Runner and E2E Validation Harness
- P2O.0 — OpenCue PVE Testbed Smoke Harness

## 3. Package Placement

```
render-module/src/main/java/.../domain/timeline/editing/
render-module/src/test/java/.../domain/timeline/editing/
```

Separate from existing `timeline/` (canonical model), `timeline/diff/` (diff/patch), `timeline/version/` (branch/commit), and `timeline/compile/` (render compilation).

## 4. Timeline Model

Reuses existing `TimelineSpec` as the canonical timeline representation. No parallel model created.

```
TimelineSpec
├── id (String)
├── name (String)
├── description (String)
├── tracks (List<TimelineTrack>)
├── textOverlays (List<TimelineTextOverlay>)
├── outputSpec (TimelineOutputSpec)
├── totalDuration (double)
└── metadata (Map<String, String>)
```

## 5. Track Model

Reuses existing `TimelineTrack`:

```
TimelineTrack
├── id (String)
├── name (String)
├── type (TrackType: VIDEO, AUDIO, SUBTITLE)
├── layer (int)
├── clips (List<TimelineClip>)
├── muted (boolean)
└── locked (boolean)
```

## 6. Clip Model

Reuses existing `TimelineClip`:

```
TimelineClip
├── id (String)
├── assetRef (TimelineAssetRef)
├── timelineStart (double)
├── assetInPoint (double)
├── assetOutPoint (double)
├── clipDuration (double)
└── effects (List<TimelineClipEffect>)
```

## 7. Caption Model

Reuses existing `TimelineTextOverlay`:

```
TimelineTextOverlay
├── id (String)
├── text (String)
├── fontFamily (String)
├── fontSize (int)
├── color (String)
├── positionX, positionY (String)
├── startTime (double)
├── duration (double)
└── backgroundColor (String)
```

## 8. Watermark Model

Watermarks are stored as metadata annotations in the current model:

```
watermark.{id}.kind = TEXT | IMAGE
watermark.{id}.text = ...
watermark.{id}.imageRef = ...
watermark.{id}.placement = ...
watermark.{id}.opacity = ...
```

## 9. Effect Reference Model

Uses `TimelineClipEffect` with `effectKey` referencing visual capability IDs from P2R.0.

```
TimelineClipEffect
├── id (String)
├── effectKey (String — visual capability ID)
├── parameters (Map<String, Object>)
└── ...
```

## 10. Transition Reference Model

Uses `TimelineTransition` with `effectKey` referencing transition capability IDs from P2R.0.

Transitions stored as metadata annotations:
```
transition.{id}.from = fromClipId
transition.{id}.to = toClipId
transition.{id}.durationMs = ...
transition.{id}.capability = visualCapabilityId
```

## 11. Output Profile Model

Reuses existing `TimelineOutputSpec`:

```
TimelineOutputSpec
├── format (String — allowlist: mp4, mov, webm)
├── resolution (String — e.g., "1920x1080")
├── frameRate (double)
├── videoCodec (String — allowlist: h264, h265, hevc, vp8, vp9)
├── videoBitrate (int)
├── audioSpec (TimelineAudioSpec)
└── pixelFormat (String)
```

## 12. Edit Operation Model

```
TimelineEditOperationType (21 values):
  CREATE_TIMELINE, UPDATE_OUTPUT_PROFILE,
  ADD_TRACK, REMOVE_TRACK, REORDER_TRACK,
  ADD_CLIP, UPDATE_CLIP, REMOVE_CLIP,
  ADD_CAPTION, UPDATE_CAPTION, REMOVE_CAPTION,
  ADD_WATERMARK, UPDATE_WATERMARK, REMOVE_WATERMARK,
  ADD_EFFECT, UPDATE_EFFECT, REMOVE_EFFECT,
  ADD_TRANSITION, UPDATE_TRANSITION, REMOVE_TRANSITION,
  VALIDATE_TIMELINE

TimelineEditOperation — single semantic operation
TimelineEditRequest — ordered list of operations
TimelineEditResult — result with status, updated timeline, issues
TimelineEditResultStatus — APPLIED, VALIDATION_FAILED, NO_OP, INVALID_OPERATION, BLOCKED, FAILED
```

## 13. Validation Model

```
TimelineValidationIssue — typed issue with severity, code, field, message
TimelineValidationIssueSeverity — INFO, WARNING, ERROR, BLOCKING
TimelineValidationIssueCode — 28 codes covering structure, safety, and boundary violations
TimelineValidationStatus — VALID, VALID_WITH_WARNINGS, INVALID, BLOCKED
```

Validation rules:
- Timeline has id
- Output profile valid (format/codec allowlists)
- Track ids unique, type required
- Clip ids unique, timing valid, asset ref required
- Caption ids unique, time ranges valid, text required
- Effect references validated against forbidden patterns
- Transition clip references validated
- No provider/storage internals in metadata
- No arbitrary FFmpeg filtergraph
- No raw provider commands

## 14. BasicTimelineEditor Behavior

`BasicTimelineEditor.apply(timeline, request)` → `TimelineEditResult`

- Pure, side-effect-free
- Returns updated timeline if applied
- Returns validation failure if invalid
- Returns no-op when operation changes nothing
- Does not persist, render, create Product, call StorageRuntime/ProductRuntime/FFmpeg/Remotion/OpenCue
- Does not mutate input
- Deterministic result

Supported minimum operations:
- CREATE_TIMELINE, UPDATE_OUTPUT_PROFILE, ADD_TRACK, ADD_CLIP, ADD_CAPTION, ADD_WATERMARK, ADD_EFFECT, ADD_TRANSITION, VALIDATE_TIMELINE

Stub operations (future work):
- REMOVE_TRACK, REORDER_TRACK, UPDATE_CLIP, REMOVE_CLIP, UPDATE_CAPTION, REMOVE_CAPTION, UPDATE_WATERMARK, REMOVE_WATERMARK, UPDATE_EFFECT, REMOVE_EFFECT, UPDATE_TRANSITION, REMOVE_TRANSITION

## 15. Relationship to Visual Capability Contract

Effects and transitions reference visual capability IDs from P2R.0. Forbidden capabilities (ARBITRARY_FFMPEG_FILTERGRAPH, REMOTION_COMPONENT_EXECUTION, etc.) are rejected by the validator and editor.

## 16. Relationship to Timeline Diff/Patch/Git

P2TLE.0 editing produces updated `TimelineSpec` instances. These can later feed into:
- P2V.0 Timeline Diff (diff between before/after specs)
- P2V.2 TimelinePatch (apply patch to snapshot)
- P2V.5 Timeline Branch/Commit (record edit as commit)

P2TLE.0 does not call diff/patch/commit services.

## 17. Relationship to RenderExecutionPlan

P2TLE.0 produces validated timeline specs. Future render planning can consume these. P2TLE.0 does not call render compilation or execution.

## 18. Relationship to FFmpeg/libass Baseline

P2TLE.0 validates output format/codec against allowlists (mp4, h264, etc.) aligned with FFmpeg/libass baseline. P2TLE.0 does not call FFmpeg.

## 19. Relationship to OpenCue

No relationship. P2TLE.0 does not call OpenCue.

## 20. Artifact DAG Boundary

No Artifact DAG dependency. P2TLE.0 is independent of Artifact DAG (indefinitely deferred).

## 21. Provider/Storage/Product Safety Boundaries

- No providerName/providerType/backendName exposure
- No bucket/objectKey/signedUrl/materializedPath exposure
- No StorageRuntime calls
- No ProductRuntime calls
- No raw commands or arbitrary filtergraphs
- No Remotion component execution
- No user-submitted Render DAG

## 22. What Is Intentionally Not Implemented

- Full visual editor frontend
- Full render execution
- OpenCue integration
- Public API controllers
- Database persistence
- Timeline Git persistence
- Artifact DAG
- Incremental/partial render
- Cache reuse
- FFmpeg filtergraph generation
- Remotion execution
- Arbitrary plugin execution

## 23. Follow-up Tasks

- P2R.1: FFmpeg Baseline Effect Plan
- P2R.2: FFmpeg Baseline Transition Plan
- P2R.3: FFmpeg/libass Basic Timeline Render Plan
- P2X.0: API Scenario Runner and E2E Validation Harness
- P2O.0: OpenCue PVE Testbed Smoke Harness
- Implement remaining edit operations (remove, update, reorder)
- Watermark dedicated record type (currently metadata-based)
- Transition dedicated tracking (currently metadata-based)
