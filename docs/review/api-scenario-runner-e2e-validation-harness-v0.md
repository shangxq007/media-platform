# P2X.0 — API Scenario Runner and E2E Validation Harness

## 1. Purpose

P2X.0 provides a repeatable internal scenario runner that validates the current core planning flow from timeline editing through visual capability validation, FFmpeg baseline effect planning, FFmpeg baseline transition planning, and FFmpeg/libass basic timeline render planning.

The scenario runner is pure and side-effect-free. It does not execute FFmpeg, does not call OpenCue, does not create RenderJob or Product, does not call StorageRuntime or ProductRuntime, does not expose public APIs, and does not use Artifact DAG.

## 2. Relationship to Current Project Goal

P2X.0 proves that an internal Agent or future API layer can drive the planning flow end-to-end using deterministic scenario definitions. It validates that all planning stages compose correctly and that safety boundaries are enforced.

## 3. Relationship to Basic Timeline Editing

P2TLE.0 introduced `BasicTimelineEditor` and `BasicTimelineValidator`. P2X.0 scenarios use these to build and validate timelines before planning. The scenario runner calls `BasicTimelineValidator.validate()` as part of timeline preparation.

## 4. Relationship to Visual Capability Contract

P2R.0 introduced `VisualCapabilityPolicy` and capability profiles. P2X.0 safety boundary scenarios verify that forbidden effects and transitions are correctly blocked by the policy layer.

## 5. Relationship to P2R.1 Effect Plan

P2R.1 introduced `FFmpegBaselineEffectPlanner`. P2X.0 scenarios 004 and 007 exercise effect planning directly. Scenario 004 validates that SCALE, CROP, and OPACITY produce expected operations. Scenario 007 validates that arbitrary filtergraph effects are blocked.

## 6. Relationship to P2R.2 Transition Plan

P2R.2 introduced `FFmpegBaselineTransitionPlanner`. P2X.0 scenarios 005 and 008 exercise transition planning directly. Scenario 005 validates that CROSSFADE produces expected operations. Scenario 008 validates that user-defined transition graphs are blocked.

## 7. Relationship to P2R.3 Basic Timeline Render Plan

P2R.3 introduced `FFmpegLibassBasicRenderPlanner`. P2X.0 scenarios 002, 003, 006, 009, and 010 exercise the full render planner. Scenario 002 validates caption overlay planning. Scenario 003 validates watermark overlay planning. Scenario 006 validates full composition. Scenario 009 validates output profile validation. Scenario 010 validates the complete planning flow.

## 8. Scenario Model

A scenario is defined by `InternalScenarioDefinition` containing:

- `InternalScenarioId` — unique identifier
- `InternalScenarioName` — human-readable name
- `InternalScenarioCategory` — category enum
- `InternalScenarioStatus` — ACTIVE, DISABLED, DEPRECATED
- Input `TimelineSpec` or edit operations
- `InternalScenarioExpectedOutcome` — expected status, issue codes, plan properties
- Safety expectations

Scenario results are `InternalScenarioResult` containing:

- Scenario identity and category
- `InternalScenarioResultStatus` — PASS, PASS_WITH_WARNINGS, FAIL, BLOCKED, UNSUPPORTED, NOT_RUN
- `InternalScenarioExpectedOutcome` and `InternalScenarioActualOutcome`
- `List<InternalScenarioIssue>` with severity and code

## 9. Scenario Registry / Fixture Strategy

`InternalScenarioRegistry` provides all 10 required scenarios as static factory methods. Scenarios build timelines inline using the existing domain model (`TimelineSpec`, `TimelineTrack`, `TimelineClip`, `TimelineTextOverlay`, `TimelineClipEffect`, `TimelineOutputSpec`). No external JSON fixtures are used.

## 10. Scenario Runner Behavior

`InternalScenarioRunner.run()` executes a single scenario:

1. Validate definition status (skip if not ACTIVE)
2. Build timeline from input or edit operations
3. Validate timeline with `BasicTimelineValidator`
4. Run `FFmpegBaselineEffectPlanner` if category is EFFECT_PLANNING, BASIC_RENDER_PLANNING, SAFETY_BOUNDARY, or REGRESSION
5. Run `FFmpegBaselineTransitionPlanner` if category is TRANSITION_PLANNING, BASIC_RENDER_PLANNING, SAFETY_BOUNDARY, or REGRESSION
6. Run `FFmpegLibassBasicRenderPlanner` if category is BASIC_RENDER_PLANNING, SAFETY_BOUNDARY, OUTPUT_PROFILE, or REGRESSION
7. Compare actual outcome with expected outcome
8. Produce deterministic result

`InternalScenarioRunner.runAll()` runs all scenarios and produces an `InternalScenarioReport`.

## 11. Scenario Report Model

`InternalScenarioReport` contains:

- `reportId` — deterministic identifier
- `totalScenarios`, `passed`, `passedWithWarnings`, `failed`, `blocked`, `unsupported`, `notRun`
- `List<InternalScenarioResult>` ordered by scenario id
- `safeMetadata`

## 12. Required Scenarios

| ID | Name | Category |
|---|---|---|
| scenario-001 | Basic Timeline Create | TIMELINE_EDITING |
| scenario-002 | Caption Overlay Render Plan | BASIC_RENDER_PLANNING |
| scenario-003 | Watermark Overlay Render Plan | BASIC_RENDER_PLANNING |
| scenario-004 | Effect Plan Scale/Crop/Opacity | EFFECT_PLANNING |
| scenario-005 | Transition Plan Cut/Crossfade | TRANSITION_PLANNING |
| scenario-006 | Basic Render Plan Composition | BASIC_RENDER_PLANNING |
| scenario-007 | Invalid Effect Forbidden Filtergraph | SAFETY_BOUNDARY |
| scenario-008 | Invalid Transition User-Defined Graph | SAFETY_BOUNDARY |
| scenario-009 | Output Profile Validation | OUTPUT_PROFILE |
| scenario-010 | Full Basic Planning Flow | REGRESSION |

## 13. Safety Boundary Scenarios

- **scenario-007**: Validates that arbitrary filtergraph effects (CUSTOM_FILTERGRAPH) are blocked by `FFmpegBaselineEffectPlanner`.
- **scenario-008**: Validates that user-defined transition graphs (CUSTOM_GRAPH_TRANSITION) are blocked by `FFmpegBaselineTransitionPlanner`.
- **scenario-009**: Validates that unsupported output containers (avi) are blocked by `FFmpegLibassBasicRenderPlanner`.

## 14. What Is Intentionally Not Implemented

- FFmpeg execution
- OpenCue job submission
- Product creation
- StorageRuntime materialization
- ProductRuntime calls
- Public REST controllers
- Database tables / Flyway migrations
- Repository persistence
- Provider Binding DSL
- Artifact DAG
- Incremental/partial render
- Cache reuse
- Remotion execution
- JSON fixture loading (scenarios use inline Java builders)

## 15. Relationship to Future Product-facing API

P2X.0 is internal-only. A future Product-facing API would wrap `InternalScenarioRunner` behind a REST controller, add authentication, and persist results to a database. None of that is implemented here.

## 16. Relationship to Provider Binding DSL

P2X.0 does not use or reference Provider Binding. Provider binding is a future extension point. P2B.0 introduced the Provider Capability Binding DSL design (declarative, YAML/JSON Schema first, fail-closed). Future P2B.3 will add capability discovery to the scenario runner.

## 17. Relationship to Future Local Runner

P2X.0 does not implement a local execution runner. The scenario runner produces plans but does not execute them. A future Local Runner would consume the plans produced by P2X.0 scenarios.

## 18. Relationship to Future OpenCue

P2X.0 does not call OpenCue. OpenCue integration is a future extension point for actual render execution.

## 19. Artifact DAG Boundary

P2X.0 does not use Artifact DAG. Artifact DAG is indefinitely deferred and is an extension layer only. P2X.0 proves that the full planning flow works without Artifact DAG.

## 20. Follow-up Tasks

- Add optional Timeline Git scenarios (011-014) if needed
- Add public API controller wrapping the scenario runner (future)
- Add scenario result persistence (future)
- Add scenario result comparison across runs (future)

## 21. Relationship to P2L.0 Local Smoke

P2L.0 introduced a local-only explicit render smoke harness. It does not consume scenario runner output. Both are independent validation mechanisms — scenario runner validates planning correctness, local smoke validates FFmpeg/ffprobe execution boundary.

## 22. Relationship to P2L.1 BasicRenderPlan Bridge

P2L.1 introduced the first bridge from FFmpegLibassBasicRenderPlan to controlled local execution. It does not consume scenario runner output. The scenario runner validates planning correctness; P2L.1 validates that a plan can drive actual FFmpeg execution. Both are independent validation layers.
