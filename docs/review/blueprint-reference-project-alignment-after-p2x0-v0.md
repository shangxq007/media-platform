# P2REF.0 — Blueprint and Reference Project Alignment after P2X.0

## 1. Purpose

This document records the blueprint and reference project alignment pass performed after P2X.0 (API Scenario Runner and E2E Validation Harness). It clarifies what is implemented, what is intentionally not implemented, and how reference projects should be interpreted.

## 2. Why This Alignment Pass Was Needed

After P2X.0 completed the internal planning chain validation, the project is about to move into Provider Binding DSL design and OpenCue execution testing. Before that happens, the architecture blueprint and reference project documents need updating so future agents do not follow stale assumptions about:

- The current planning chain vs the old compile pipeline
- Provider Binding DSL position (future, not current)
- OpenCue position (ExecutionEnvironment, not Provider)
- Remotion status (non-executable, POC only)
- Artifact DAG status (indefinitely deferred)
- Reference project classifications

## 3. Current Implemented Chain

```text
BasicTimeline / TimelineSpec
  → BasicTimelineValidator (P2TLE.0)
  → VisualCapabilityContract (P2R.0)
  → FFmpegBaselineEffectPlanner (P2R.1)
  → FFmpegBaselineTransitionPlanner (P2R.2)
  → FFmpegLibassBasicRenderPlanner (P2R.3)
  → InternalScenarioRunner (P2X.0)
```

This chain is fully implemented, pure, side-effect-free, and validated by 10 internal scenarios (39 tests total).

## 4. Current Intentionally Missing Execution Pieces

```text
InternalScenarioRunner
  → future RenderExecutionPlan bridge
  → future Local Runner smoke
  → future OpenCue ExecutionEnvironment smoke
  → future Product registration
```

These are intentionally not implemented. The planning chain validates planning correctness; execution is the next phase.

## 5. Reference Project Status Matrix

### Media/Render Technologies

| Technology | Status | Notes |
|-----------|--------|-------|
| FFmpeg | PRODUCTION_BASELINE | Current production baseline for basic full explicit rendering |
| libass | PRODUCTION_BASELINE | Current production baseline for subtitle/caption overlay semantics |
| OpenCue | FUTURE_EXTENSION | Future ExecutionEnvironment for scheduling. Not a Provider. |
| OTIO / OpenTimelineIO | INTERNAL_FOUNDATION | Reference/exchange model. Import/export implemented. |
| Remotion | POC_CANDIDATE | Non-executable. POC/dry-run only. Not production. |
| MLT | POC_CANDIDATE | POC candidate. Not current execution path. |
| GStreamer | POC_CANDIDATE | POC candidate. Not current execution path. |
| BMF | SPIKE_CANDIDATE | Spike candidate for GPU/AI media pipeline. |
| GPAC | POC_CANDIDATE | POC candidate for packaging. |
| Blender | SPIKE_CANDIDATE | Spike candidate for 3D/VFX. |
| Natron | SPIKE_CANDIDATE | Spike candidate for compositing. |
| OFX | NOT_ADOPTED | Deprecated. Capability model only. |
| Shotstack | FUTURE_EXTENSION | Cloud render spike/future reference. |
| JavaCV | NOT_ADOPTED | Not evaluated. Requires separate dependency/license review. |
| OpenCV | NOT_ADOPTED | Not evaluated. Requires separate dependency/license review. |
| Aegisub | NOT_ADOPTED | Not evaluated. Requires separate dependency/license review. |
| VapourSynth / AviSynth+ | NOT_ADOPTED | Not evaluated. Requires separate dependency/license review. |

### Editing/Versioning Tools

| Technology | Status | Notes |
|-----------|--------|-------|
| vedit / pyvedit | NOT_ADOPTED | Not adopted as production dependency. Reference/benchmark only. |

### DSL/Parser Technologies

| Technology | Status | Notes |
|-----------|--------|-------|
| ANTLR | FUTURE_EXTENSION | Parser-generator option for future textual DSL only. Not current dependency. |
| JavaCC | FUTURE_EXTENSION | Parser-generator option for future textual DSL only. Not current dependency. |
| YAML / JSON Schema declarative DSL | FUTURE_EXTENSION | Preferred first step for Provider Binding DSL / template / scenario declarations. |

## 6. FFmpeg/libass Baseline Position

FFmpeg is the current production baseline for basic full explicit rendering. libass is the current production baseline for subtitle/caption overlay semantics when used through controlled FFmpeg/libass planning. No other provider is currently executable. No arbitrary FFmpeg filtergraph exposure. No shell command DSL.

## 7. OpenCue Execution Environment Position

OpenCue is the next execution-environment validation target after planning/scenario validation. OpenCue is ExecutionEnvironment, not Provider. OpenCue does not decide visual capability semantics. OpenCue does not replace ProviderBindingPlan. OpenCue does not require Artifact DAG for initial smoke.

Recommended future path:
- P2O.0 — OpenCue PVE Testbed Smoke Harness
- P2O.1 — OpenCue ExecutionEnvironment Model Alignment
- P2O.2 — OpenCue Job Submission Adapter
- P2O.3 — OpenCue Worker Result Collection
- P2O.4 — OpenCue Failure / Retry / Log Mapping

## 8. Remotion Non-Executable Boundary

Remotion remains non-executable. Remotion is POC/dry-run only. Remotion is not production. Remotion is not auto-dispatch. Remotion execution is not available. Remotion is a future spike candidate for caption/text rendering.

## 9. Provider Binding DSL Future Position

Provider Binding DSL is a future declarative configuration layer, not a runtime scripting language.

DSL can declare:
- capability id
- provider support
- status
- consistency
- fallback
- parameter schema
- productionAllowed
- autoDispatchAllowed

DSL must not declare:
- shell command
- FFmpeg filtergraph
- Remotion component execution
- Blender Python script
- Natron graph
- user-submitted Render DAG
- plugin-inserted execution node

Recommended future path:
- P2B.0 — Provider Capability Binding DSL Design
- P2B.1 — FFmpeg/libass Binding Fixtures
- P2B.2 — Binding Validator / Registry
- P2B.3 — Scenario Runner Capability Discovery

## 10. ANTLR / JavaCC Future-Only Parser-Generator Position

ANTLR and JavaCC are parser-generator options for future textual DSL only. They are not current dependencies. If a textual DSL is needed in the future (beyond YAML/JSON Schema declarative format), ANTLR or JavaCC could be used to parse it. This is not current work.

## 11. YAML / JSON Schema Declarative DSL Recommendation

YAML/JSON Schema is the preferred first step for Provider Binding DSL, template definitions, and scenario declarations. This is a declarative configuration approach, not a runtime scripting language. It should be implemented as part of P2B.0.

## 12. Template DSL Position

Template definitions use the existing domain model (TimelineSpec, TimelineTrack, TimelineClip, etc.). A future template DSL could use YAML/JSON Schema to declare reusable template structures. This is not current work.

## 13. Scenario DSL Position

Scenario definitions currently use Java builders (InternalScenarioRegistry). A future scenario DSL could use YAML/JSON Schema to declare scenario definitions externally. This is not current work.

## 14. Parallel Segment/Layer Rendering Future Note

The platform should eventually support bounded multi-stage rendering, including segment/layer parallel execution and final assembly. This should evolve from RenderExecutionPlan and OpenCue execution modeling, not from user-submitted DAGs or plugin-inserted execution nodes. Artifact DAG may be re-evaluated later for cache/lineage/incremental render only after measured production bottlenecks appear.

## 15. Artifact DAG Indefinite Deferral Boundary

Artifact DAG remains indefinitely deferred (P2A.2). Artifact DAG is an extension layer only. Artifact DAG is not a roadmap dependency. Artifact DAG is not required by render planning, Timeline Git, OpenCue, Product API, effects, transitions, or E2E validation. The new planning chain (P2TLE.0 → P2X.0) operates entirely without Artifact DAG. The compile pipeline (N4-N7.5) includes ArtifactDependencyGraph but this is an implementation detail, not a requirement.

## 16. Updated Roadmap

```text
Completed:
  P2TLE.0 — Basic Timeline Editing Model and Validation
  P2R.0 — Visual Capability Contract for Effects and Transitions
  P2R.1 — FFmpeg Baseline Effect Plan
  P2R.2 — FFmpeg Baseline Transition Plan
  P2R.3 — FFmpeg/libass Basic Timeline Render Plan
  P2X.0 — API Scenario Runner and E2E Validation Harness
  P2REF.0 — Blueprint and Reference Project Alignment after P2X.0

Next:
  P2B.0 — Provider Capability Binding DSL Design
  P2O.0 — OpenCue PVE Testbed Smoke Harness
  P2O.1 — OpenCue ExecutionEnvironment Model Alignment
  P2O.2 — OpenCue Job Submission Adapter

Later:
  Product-facing API Contract
  Local Runner integration
  ProductRuntime integration
  Provider Binding Registry integration
  OpenCue E2E explicit render
  Parallel segment/layer render
  Artifact DAG re-evaluation only if measured production bottleneck appears
```

## 17. What Was Intentionally Not Changed

- No new runtime functionality implemented
- No Provider Binding DSL implementation
- No OpenCue adapter implementation
- No FFmpeg execution
- No public API controllers
- No Artifact DAG revival
- No new external dependencies
- No ANTLR/JavaCC integration
- No parser generator integration

## 18. Follow-up Tasks

- P2B.0: Provider Capability Binding DSL Design (YAML/JSON Schema, declarative)
- P2O.0: OpenCue PVE Testbed Smoke Harness
- Future: Scenario DSL (YAML/JSON Schema external scenario definitions)
- Future: Template DSL (YAML/JSON Schema reusable template structures)
