---
status: implementation-report
created: 2026-06-28
scope: render-module
truth_level: current
owner: platform
---

# Timeline DAG Foundation Report (N4+)

## Summary

Established the deterministic compile foundation from TimelineRevision to provider-neutral graph planning.

**Compile Pipeline v0:**
```text
TimelineRevision
→ NormalizedTimeline v0
→ ArtifactDependencyGraph v0
→ LogicalCapabilityGraph v0
→ ProviderExecutionDocument model reservation
```

## Stage 1: Timeline Compile Contract v0

**Status:** ✅ Complete

**Document:** `docs/review/timeline-compile-contract-v0.md`

Defines:
- Compile-participating vs metadata-only vs ignored-for-v0 fields
- Time unit (seconds), track ordering, clip ordering rules
- Asset reference rule, single-primary-input behavior
- Subtitle/caption behavior, unsupported effect/transition handling
- Output profile normalization with deterministic defaults
- Validation/fail-closed rules, determinism requirements

## Stage 2: NormalizedTimeline v0

**Status:** ✅ Complete

**Domain models (7 files):**
- `NormalizedTimeline` — deterministic internal compile input
- `NormalizedTrack` — ordered tracks (VIDEO > AUDIO > SUBTITLE)
- `NormalizedClip` — clips ordered by timelineStart
- `NormalizedAssetRef` — source asset reference
- `NormalizedOutputProfile` — deterministic output spec with defaults
- `NormalizedCaptionLayer` — text overlay representation
- `TimelineCompileException` — fail-closed exception with error codes

**Service:**
- `TimelineNormalizationService` — NormalizedTimeline ← TimelineSpec, deterministic, provider-neutral

**Tests:** 14 tests in `TimelineNormalizationServiceTest`

## Stage 3: ArtifactDependencyGraph v0

**Status:** ✅ Complete

**Domain models (5 files):**
- `ArtifactDependencyGraph` — provider-neutral DAG with nodes and edges
- `ArtifactNode` — artifact node with type, source asset, requirements
- `ArtifactNodeType` — INPUT_MEDIA, TRIMMED_MEDIA, SUBTITLE_OVERLAY, AUDIO_MIX, FINAL_ENCODE, FINAL_RENDER
- `ArtifactEdge` — directed dependency edge
- `ArtifactEdgeType` — DERIVES_FROM, REQUIRES_INPUT, ENCODES_TO, PRODUCES
- `ArtifactRequirement` — capability requirements with fidelity and fallback

**Service:**
- `ArtifactGraphCompiler` — NormalizedTimeline → ArtifactDependencyGraph, deterministic, acyclic

**Tests:** 10 tests in `ArtifactGraphCompilerTest`

## Stage 4: LogicalCapabilityGraph v0

**Status:** ✅ Complete

**Domain models (3 files):**
- `LogicalCapabilityGraph` — provider-neutral capability graph
- `LogicalCapabilityNode` — capability node with requirement
- `LogicalCapabilityEdge` — capability edge (mirrors artifact graph)

**Service:**
- `CapabilityGraphCompiler` — ArtifactDependencyGraph → LogicalCapabilityGraph

**Capability mapping:**
| Artifact Node | Capability Requirements |
|---------------|------------------------|
| INPUT_MEDIA | MEDIA_INPUT |
| TRIMMED_MEDIA | VIDEO_DECODE, VIDEO_TRIM |
| SUBTITLE_OVERLAY | SUBTITLE_BURN_IN, FONT_RESOLUTION |
| AUDIO_MIX | AUDIO_DECODE, AUDIO_MIX |
| FINAL_ENCODE | VIDEO_ENCODE, AUDIO_ENCODE, CONTAINER_MUX |
| FINAL_RENDER | MEDIA_FILE_OUTPUT |

**Tests:** 7 tests in `CapabilityGraphCompilerTest`

## Stage 5: ProviderExecutionDocument Model Reservation

**Status:** ✅ Complete

**Document:** `docs/review/provider-execution-document-model.md`

Reserved concepts:
- `ProviderExecutionDocument` — internal-only provider-specific execution document
- `ProviderExecutionDocumentType` — CLI_COMMAND, XML_PROJECT, JSON_PROPS, etc.
- `ProviderExecutionDocumentMetadata` — metadata linking to capability/artifact graphs

## Stage 6: Golden Fixture Tests

**Status:** ✅ Complete

**Tests:** 10 golden fixtures in `TimelineCompileGoldenFixtureTest`

| Fixture | Description |
|---------|-------------|
| Single video clip | Full pipeline: normalize → artifact graph → capability graph |
| Single clip + output profile | Output spec normalization |
| Single clip + caption | SUBTITLE_OVERLAY node in graph |
| Two sequential clips | Multiple TRIMMED_MEDIA nodes |
| Multiple tracks | AUDIO_MIX node for audio tracks |
| Unsupported effect | Fail-closed behavior |
| Missing source asset | Fail-closed at normalization |
| Stable graph IDs | Deterministic across repeated compile |
| No provider binding | No provider names in output |

## Stage 7: Documentation

**Status:** ✅ Complete

- `docs/review/timeline-compile-contract-v0.md` — compile contract
- `docs/review/provider-execution-document-model.md` — execution document reservation
- `docs/review/timeline-dag-foundation-report.md` — this report
- `docs/architecture/current/current-system-state.md` — updated with N4+ section

## Stage 8: Regression and Safety

**Status:** ✅ Complete

All compile tests pass:
- `TimelineNormalizationServiceTest` — 14 tests
- `ArtifactGraphCompilerTest` — 10 tests
- `CapabilityGraphCompilerTest` — 7 tests
- `TimelineCompileGoldenFixtureTest` — 10 tests

## Files Created

| File | Type | Purpose |
|------|------|---------|
| `docs/review/timeline-compile-contract-v0.md` | Doc | Compile contract v0 |
| `docs/review/provider-execution-document-model.md` | Doc | Execution document reservation |
| `docs/review/timeline-dag-foundation-report.md` | Doc | This report |
| `domain/timeline/compile/NormalizedTimeline.java` | Model | Normalized timeline |
| `domain/timeline/compile/NormalizedTrack.java` | Model | Normalized track |
| `domain/timeline/compile/NormalizedClip.java` | Model | Normalized clip |
| `domain/timeline/compile/NormalizedAssetRef.java` | Model | Asset reference |
| `domain/timeline/compile/NormalizedOutputProfile.java` | Model | Output profile |
| `domain/timeline/compile/NormalizedCaptionLayer.java` | Model | Caption layer |
| `domain/timeline/compile/TimelineCompileException.java` | Model | Compile exception |
| `domain/timeline/compile/ArtifactDependencyGraph.java` | Model | Artifact DAG |
| `domain/timeline/compile/ArtifactNode.java` | Model | Artifact node |
| `domain/timeline/compile/ArtifactNodeType.java` | Enum | Node types |
| `domain/timeline/compile/ArtifactEdge.java` | Model | Artifact edge |
| `domain/timeline/compile/ArtifactEdgeType.java` | Enum | Edge types |
| `domain/timeline/compile/ArtifactRequirement.java` | Model | Capability requirement |
| `domain/timeline/compile/LogicalCapabilityGraph.java` | Model | Capability graph |
| `domain/timeline/compile/LogicalCapabilityNode.java` | Model | Capability node |
| `domain/timeline/compile/LogicalCapabilityEdge.java` | Model | Capability edge |
| `app/timeline/compile/TimelineNormalizationService.java` | Service | Timeline normalizer |
| `app/timeline/compile/ArtifactGraphCompiler.java` | Service | Artifact graph compiler |
| `app/timeline/compile/CapabilityGraphCompiler.java` | Service | Capability graph compiler |
| `test/.../TimelineNormalizationServiceTest.java` | Test | 14 tests |
| `test/.../ArtifactGraphCompilerTest.java` | Test | 10 tests |
| `test/.../CapabilityGraphCompilerTest.java` | Test | 7 tests |
| `test/.../TimelineCompileGoldenFixtureTest.java` | Test | 10 golden fixtures |

## Non-Goals Achieved

- ❌ No real provider rendering
- ❌ No real command generation
- ❌ No OpenCue submit
- ❌ No public API changes
- ❌ No DB migration
- ❌ No provider/backend/storage internals exposed

## Completed in N5+ (Provider Binding)

- ✅ ProviderBindingPlan v0 — internal binding model with mode-aware eligibility
- ✅ ProviderBindingCompiler — LogicalCapabilityGraph → ProviderBindingPlan
- ✅ ProviderExecutionDocumentDraft v0 — planning artifacts (generationReady=false)
- ✅ ProviderExecutionDocumentDraftCompiler — plan → draft list
- ✅ 28 tests (12 binding, 8 execution draft, 8 golden fixtures)

## Completed in N6+ (Execution Plan)

- ✅ RenderExecutionPlan v0 — deterministic step planning (executionReady=false)
- ✅ RenderExecutionPlanCompiler — ProviderBindingPlan → RenderExecutionPlan
- ✅ RenderPlanPolicyGuard v0 — 14 safety constraint checks
- ✅ ExecutionPolicy v0 — PRODUCTION/MANUAL/EXPERIMENT/DRY_RUN modes
- ✅ 31 tests (12 plan compiler, 12 policy guard, 7 golden fixtures)

## Completed in N7 (Local Execution Plan Runner)

- ✅ LocalExecutionPlanRunner v0 — FFmpeg baseline execution through plan
- ✅ RenderExecutionStepExecutor v0 — delegates to existing services per step type
- ✅ PlanBasedTimelineRevisionRenderService — bridges render API to plan-based path
- ✅ Real render smoke: READY Product + ProductDependency lineage
- ✅ Non-FFmpeg providers remain non-executable
- ✅ 13 tests (8 runner, 5 smoke)

## Follow-up Items

1. **LocalExecutionPlanRunner** — Actually execute plans locally (FFmpeg baseline)
2. **ProviderExecutionDocument generation** — Create real provider-specific execution documents
3. **OpenCue submit** — Submit execution plans to OpenCue cluster
4. **Multi-clip/multi-input support** — Extend v0 for multi-track compositing
5. **Effect compilation** — Support clip effects in compile pipeline
6. **Cache/incremental render** — Leverage artifact graph for cache invalidation
