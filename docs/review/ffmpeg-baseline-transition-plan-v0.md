# FFmpeg Baseline Transition Plan v0 (P2R.2)

## 1. Purpose

Pure, side-effect-free FFmpeg Baseline Transition Plan that maps semantic timeline transition references to bounded internal FFmpeg baseline transition operations, with typed parameter validation, clip relationship validation, deterministic ordering, conservative policy, and safety boundaries.

## 2. Relationship to Basic Timeline Editing

Consumes P2TLE.0 types:
- `TimelineSpec` — source timeline
- `TimelineClipTransition` / `TimelineClipEffect` — transition references with `effectKey`, `fromClipId`, `toClipId`, `durationMs`
- `TimelineTrack`, `TimelineClip` — structural context for adjacency validation
- `TimelineAssetRef`, `TimelineOutputSpec` — timeline metadata

Does not call `BasicTimelineEditor.apply()`.

## 3. Relationship to Visual Capability Contract

Uses P2R.0 vocabulary:
- `TransitionCapabilityProfile` — resolves transition key to `VisualCapabilityDefinition`
- `VisualCapabilityPolicy` — checks forbidden/restricted/POC status
- `VisualConsistencyLevel` / `VisualFallbackBehavior` — referenced by capability definitions

## 4. Relationship to FFmpeg/libass Baseline

Produces internal operation vocabulary only. No raw FFmpeg xfade strings, no filter_complex, no shell commands. FFmpeg/libass remains the production baseline provider (not called by P2R.2).

## 5. Relationship to P2R.1 Effect Plan

Mirrors P2R.1 style: pure records, typed IDs, status/result/issue vocabulary, conservative policy, deterministic planner, safeMetadata only, no execution, no raw provider exposure. Shares `FFmpegBaselineEffectParameterType` vocabulary where applicable.

## 6. Transition Planning Scope

### Supported Baseline Transitions

| Transition | Operation Type | Status |
|-----------|---------------|--------|
| CUT | CUT | PRODUCTION |
| FADE | FADE | PRODUCTION |
| CROSSFADE | CROSSFADE | PRODUCTION |
| DISSOLVE | DISSOLVE | PRODUCTION |

### POC / Unsupported Transitions

| Transition | Status | Policy |
|-----------|--------|--------|
| SLIDE | POC | Unsupported by default |
| WIPE | POC | Unsupported by default |
| PUSH | POC | Unsupported by default |
| ZOOM | POC | Unsupported by default |

### Forbidden Transitions

| Transition | Status |
|-----------|--------|
| THREE_D_TRANSITION | FUTURE / RESTRICTED |
| SHADER_TRANSITION | FORBIDDEN |
| ARBITRARY_TRANSITION_PLUGIN | FORBIDDEN |
| USER_DEFINED_TRANSITION_GRAPH | FORBIDDEN |
| PROVIDER_SPECIFIC_TRANSITION_GRAPH | FORBIDDEN |
| ARBITRARY_FFMPEG_FILTERGRAPH | FORBIDDEN |
| ARBITRARY_SHADER | FORBIDDEN |
| ARBITRARY_SCRIPT_EFFECT | FORBIDDEN |
| REMOTION_COMPONENT_EXECUTION | FORBIDDEN |
| USER_DEFINED_RENDER_DAG | FORBIDDEN |
| PLUGIN_INSERTED_RENDER_NODE | FORBIDDEN |
| PROVIDER_SPECIFIC_RAW_COMMAND | FORBIDDEN |

## 7. Planning Request/Result Model

```
FFmpegBaselineTransitionPlanningRequest(id, timeline, policy, safeMetadata)
  → FFmpegBaselineTransitionPlanner.plan()
  → FFmpegBaselineTransitionPlanningResult(status, plan, issues, safeMetadata)
```

## 8. Transition Operation Model

```
FFmpegBaselineTransitionOperation(id, type, target, parameters, source, safeMetadata)
```

- `target` references semantic timeline entities (fromClipId, toClipId, trackId, timelineId, transitionId)
- `parameters` are typed (`FFmpegBaselineTransitionParameterType`)
- `source` indicates origin (BASIC_TIMELINE_TRANSITION_REF, VISUAL_CAPABILITY_RESOLVED, etc.)

## 9. Parameter Validation Model

| Transition | Required Parameters | Validation |
|-----------|-------------------|------------|
| CUT | durationMs (may be 0) | >= 0 if policy allows |
| FADE | durationMs | > 0 |
| CROSSFADE | durationMs | > 0 |
| DISSOLVE | durationMs | > 0 |
| SLIDE/WIPE/PUSH/ZOOM | durationMs | > 0 (POC) |

## 10. Clip Relationship / Adjacency Policy

- `fromClipId` and `toClipId` are required
- `fromClipId != toClipId`
- Adjacent clips: `fromClip.timelineStart + fromClip.clipDuration == toClip.timelineStart`
- Non-adjacent clips produce a warning (policy-dependent)
- Default transition for adjacent clips without explicit transition: CUT

## 11. Policy Model

```
FFmpegBaselineTransitionPolicy(
    allowPocTransitions, allowRestrictedTransitions, allowWarnings,
    failOnUnsupported, failOnMissingClip, failOnNonAdjacentClips,
    allowCutWithZeroDuration
)
```

- `conservative()` — default: no POC, no restricted, warnings allowed, fail on unsupported/missing/non-adjacent, CUT zero duration allowed
- `permissive()` — POC allowed, no restricted, warnings allowed, no fail on unsupported/missing/non-adjacent

## 12. Deterministic Ordering

Operations ordered by:
1. Track order in timeline
2. fromClip timelineStart
3. toClip timelineStart
4. Transition durationMs
5. Operation type enum order
6. Transition id lexicographic

## 13. Safety Boundaries

- No raw FFmpeg xfade/filter_complex exposure
- No shell command generation
- No provider-specific parameters
- No storage/internal path exposure
- No Artifact DAG usage
- No Remotion execution
- No global optimization

## 14. Relationship to Future Basic Timeline Render Plan

```
BasicTimeline
  → BasicTimelineValidator
  → FFmpegBaselineEffectPlanner
  → FFmpegBaselineTransitionPlanner
  → FFmpeg/libass Basic Timeline Render Plan
  → RenderExecutionPlan
```

P2R.2 only implements the transition planning portion.

## 15-22. Follow-up

- P2R.3: FFmpeg/libass Basic Timeline Render Plan
- Future: RenderExecutionPlan integration, OpenCue integration
