# FFmpeg Baseline Effect Plan v0 (P2R.1)

## 1. Purpose

Pure, side-effect-free FFmpeg Baseline Effect Plan that maps semantic timeline effect references to bounded internal FFmpeg baseline effect operations, with typed parameter validation and safety boundaries.

## 2. Relationship to Basic Timeline Editing

P2R.1 consumes P2TLE.0 types:
- `TimelineSpec` — source timeline
- `TimelineClipEffect` — effect references with `effectKey` and parameters
- `TimelineTextOverlay` — caption overlays
- `TimelineTrack` / `TimelineClip` — structural context

P2R.1 does not call `BasicTimelineEditor.apply()`.

## 3. Relationship to Visual Capability Contract

P2R.1 uses P2R.0 vocabulary:
- `EffectCapabilityProfile` — resolves effect key to `VisualCapabilityDefinition`
- `VisualCapabilityPolicy` — checks forbidden/restricted/POC status
- `VisualConsistencyLevel` / `VisualFallbackBehavior` — referenced by capability definitions

## 4. Relationship to FFmpeg/libass Baseline

P2R.1 produces internal operation vocabulary only. No raw FFmpeg filtergraphs, no shell commands, no `filter_complex` exposure. FFmpeg/libass remains the production baseline provider (not called by P2R.1).

## 5. Effect Planning Scope

### Supported Baseline Effects

| Effect | Operation Type | Category |
|--------|---------------|----------|
| SCALE | SCALE | TRANSFORM |
| CROP | CROP | TRANSFORM |
| FIT | FIT | TRANSFORM |
| FILL | FILL | TRANSFORM |
| CONTAIN | CONTAIN | TRANSFORM |
| ROTATE | ROTATE | TRANSFORM |
| OPACITY | OPACITY | EFFECT |
| FADE_IN | FADE_IN | EFFECT |
| FADE_OUT | FADE_OUT | EFFECT |
| TEXT_OVERLAY | TEXT_OVERLAY | OVERLAY |
| IMAGE_OVERLAY | IMAGE_OVERLAY | OVERLAY |
| CAPTION_OVERLAY | CAPTION_OVERLAY | CAPTION |
| WATERMARK_OVERLAY | WATERMARK_OVERLAY | WATERMARK |

### POC / Unsupported Effects

| Effect | Status | Policy |
|--------|--------|--------|
| BLUR | POC | Unsupported by default |
| COLOR_ADJUST | POC | Unsupported by default |
| BRIGHTNESS | POC | Unsupported by default |
| CONTRAST | POC | Unsupported by default |
| SATURATION | POC | Unsupported by default |
| VOLUME_ADJUST | POC | Unsupported by default |
| AUDIO_FADE_IN | POC | Unsupported by default |
| AUDIO_FADE_OUT | POC | Unsupported by default |
| PICTURE_IN_PICTURE | POC | Unsupported by default |
| BACKGROUND_BLUR | POC | Unsupported by default |

### Forbidden Effects

| Effect | Status |
|--------|--------|
| ARBITRARY_FFMPEG_FILTERGRAPH | BLOCKED |
| ARBITRARY_SHADER | BLOCKED |
| ARBITRARY_SCRIPT_EFFECT | BLOCKED |
| ARBITRARY_OFX_PLUGIN | BLOCKED |
| NATRON_NODE_GRAPH | BLOCKED |
| BLENDER_COMPOSITOR_GRAPH | BLOCKED |
| REMOTION_COMPONENT_EXECUTION | BLOCKED |
| USER_DEFINED_RENDER_DAG | BLOCKED |
| PLUGIN_INSERTED_RENDER_NODE | BLOCKED |
| PROVIDER_SPECIFIC_RAW_COMMAND | BLOCKED |

## 6. Planning Request/Result Model

```
FFmpegBaselineEffectPlanningRequest(id, timeline, policy, safeMetadata)
  → FFmpegBaselineEffectPlanner.plan()
  → FFmpegBaselineEffectPlanningResult(status, plan, issues, safeMetadata)
```

## 7. Effect Operation Model

```
FFmpegBaselineEffectOperation(id, type, target, parameters, source, safeMetadata)
```

- `target` references semantic timeline entities (clipId, trackId, etc.)
- `parameters` are typed (`FFmpegBaselineEffectParameterType`)
- `source` indicates origin (BASIC_TIMELINE_EFFECT_REF, VISUAL_CAPABILITY_RESOLVED, etc.)

## 8. Parameter Validation Model

| Effect | Required Parameters | Validation |
|--------|-------------------|------------|
| SCALE | width, height | > 0 |
| CROP | x, y, width, height | x/y >= 0, width/height > 0 |
| FIT/FILL/CONTAIN | targetWidth, targetHeight | > 0 |
| ROTATE | degrees | numeric |
| OPACITY | opacity | 0..1 |
| FADE_IN/FADE_OUT | durationMs | > 0 |
| TEXT_OVERLAY | text | non-blank |
| IMAGE_OVERLAY | imageRef | non-blank |
| CAPTION_OVERLAY | text (from overlay) | non-blank |
| WATERMARK_OVERLAY | watermarkRef/target | present |

## 9. Policy Model

```
FFmpegBaselineEffectPolicy(allowPocEffects, allowRestrictedEffects, allowWarnings, failOnUnsupported, failOnMissingTarget)
```

- `conservative()` — default: no POC, no restricted, warnings allowed, fail on unsupported/missing
- `permissive()` — POC allowed, no restricted, warnings allowed, no fail on unsupported/missing

## 10. Deterministic Ordering

Operations ordered by:
1. Track order in timeline
2. Clip order within track
3. Effect order within clip

## 11. Safety Boundaries

- No raw FFmpeg filtergraph exposure
- No shell command generation
- No provider-specific parameters
- No storage/internal path exposure
- No Artifact DAG usage
- No Remotion execution
- No global optimization

## 12-21. Follow-up

- P2R.2: FFmpeg Baseline Transition Plan
- P2R.3: FFmpeg/libass Basic Timeline Render Plan
- Future: RenderExecutionPlan integration, OpenCue integration
