---
status: design-review
created: 2026-06-29
scope: provider-binding-dsl
truth_level: design
owner: platform
---

# P2B.0 — Provider Capability Binding DSL Design Review

## 1. Purpose

Document the design of the Provider Capability Binding DSL — a declarative YAML/JSON Schema DSL for describing provider capability bindings.

## 2. Scope

- OpenCue terminology cleanup (Part A)
- Provider Capability Binding DSL design (Part B)
- Example binding files
- Documentation updates

## 3. OpenCue Terminology Cleanup Summary

Fixed stale OpenCue terminology in two files:

| File | Line | Before | After |
|------|------|--------|-------|
| extension-decision-guide.md | 177 | "OpenCue as distributed execution backend → implement `ExecutionBackend`" | "OpenCue is an ExecutionEnvironment, not a Provider or ExecutionBackend" |
| provider-governance.md | 93 | "OpenCue \| ProviderExtensionSPI (render farm)" | "OpenCue \| ExecutionEnvironment — not a Provider; not ProviderExtensionSPI" |

Remaining references to ProviderExtensionSPI and ExecutionBackend in those files are for non-OpenCue concepts (Whisper, OCR, BMF) and are correct.

## 4. Current Implemented Planning Chain

```
BasicTimeline
  → BasicTimelineValidator
  → VisualCapabilityContract
  → FFmpegBaselineEffectPlanner
  → FFmpegBaselineTransitionPlanner
  → FFmpegLibassBasicRenderPlanner
  → InternalScenarioRunner
```

This chain is validated by P2X.0 (10 scenarios, 39 tests).

## 5. Why Provider Binding DSL Is Needed Now

The planning chain validates correctness but does not declare which providers support which capabilities. Today this mapping is implicit in Java code. The DSL makes it explicit, declarative, version-controlled, and schema-validatable. This is the foundation for future ProviderBindingRegistry (P2B.2) and ProviderBindingPlan.

## 6. YAML/JSON Schema First Decision

YAML is preferred: human-readable, version-control friendly, no parser generator required, JSON Schema can validate structure. Consistent with existing platform configuration patterns.

## 7. Why ANTLR/JavaCC Are Not Adopted Now

ANTLR and JavaCC are parser-generator options for future textual DSL only. They add complexity and dependencies. YAML/JSON Schema is sufficient for the current declarative binding model. If a more expressive textual DSL is needed later, ANTLR or JavaCC could parse it.

## 8. DSL Model Summary

The DSL schema has:
- `schemaVersion` — version identifier
- `kind` — ProviderBinding or ExecutionEnvironmentNote
- `metadata` — provider identity, type, status, execution/auto-dispatch flags
- `capabilities[]` — list of capability bindings with status, consistency, fallback, productionAllowed, autoDispatchAllowed, parameter schema

## 9. Provider Binding Examples Summary

| File | Provider | Status | executionAllowed | autoDispatchAllowed |
|------|----------|--------|-----------------|-------------------|
| ffmpeg.provider-binding.yaml | FFmpeg | PRODUCTION_BASELINE | true | true |
| libass.provider-binding.yaml | libass | PRODUCTION_BASELINE | true | true |
| remotion.provider-binding.yaml | Remotion | POC_CANDIDATE | false | false |
| opencue.execution-environment-note.yaml | OpenCue | N/A (not a provider) | N/A | N/A |

## 10. Safety Model Summary

- DSL is declarative, not executable
- DSL does not contain commands, scripts, or filtergraphs
- DSL does not expose provider internals
- DSL does not define OpenCue jobs
- DSL is future work, not runtime-integrated

## 11. Fail-Closed Validation Model

| Rule | Behavior |
|------|----------|
| Unknown provider | Reject |
| Unknown capability | Reject |
| Missing schemaVersion | Reject |
| Missing provider id | Reject |
| Missing capability id | Reject |
| FORBIDDEN capability | Reject |
| RESTRICTED capability | Manual review / internal only |
| POC capability | Internal only by default |
| autoDispatchAllowed=true without productionAllowed | Reject |
| productionAllowed=true for POC/SPIKE/FUTURE/RESTRICTED/FORBIDDEN | Reject |
| executionAllowed=false | No runtime execution selection |

## 12. Relationship to P2R.0 Visual Capability Contract

P2R.0 defines visual capabilities. The DSL references capability IDs and adds provider-specific binding metadata. The DSL does not redefine capabilities.

## 13. Relationship to P2R.1/P2R.2/P2R.3

P2R.1 (effect planner), P2R.2 (transition planner), and P2R.3 (render planner) use capability IDs. The DSL declares which providers support these capabilities. Future integration will allow planners to verify provider eligibility.

## 14. Relationship to P2X.0 Scenario Runner

P2X.0 validates planning correctness. Future P2B.3 will add capability discovery to the scenario runner.

## 15. Relationship to Future ProviderBindingRegistry

P2B.2 will implement ProviderBindingRegistry that loads DSL files, validates them, and provides lookup by provider/capability.

## 16. Relationship to Future OpenCue

OpenCue is an ExecutionEnvironment. It does not have a provider binding. It has an ExecutionEnvironmentNote. OpenCue integration (P2O.0-P2O.4) consumes RenderExecutionPlan output.

## 17. Relationship to Templates/Workflows

Future templates may reference provider bindings. The DSL does not define templates.

## 18. Relationship to Artifact DAG

The DSL does not reference Artifact DAG. Artifact DAG is indefinitely deferred (P2A.2).

## 19. What Was Intentionally Not Implemented

- Runtime ProviderBindingRegistry
- Runtime ProviderBindingPlan integration
- RenderExecutionPlan integration
- OpenCue adapter
- ANTLR/JavaCC parser generators
- YAML parsing runtime
- Public API endpoints
- Database persistence
- Full Java model

## 20. Follow-up Roadmap

| ID | Task | Status |
|----|------|--------|
| P2B.0 | Provider Capability Binding DSL Design | Complete |
| P2L.0 | Local Explicit Render Smoke Harness | Current |
| P2B.1 | FFmpeg/libass Binding Fixtures | Future |
| P2B.2 | Binding Validator / Registry | Future |
| P2B.3 | Scenario Runner Capability Discovery | Future |
| P2O.0 | OpenCue PVE Testbed Smoke Harness | Future |
