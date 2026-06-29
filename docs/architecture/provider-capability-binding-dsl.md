---
status: design
created: 2026-06-29
scope: provider-binding
truth_level: design
owner: platform
---

# Provider Capability Binding DSL

## 1. Purpose

Define a declarative YAML/JSON Schema DSL for describing provider capability bindings — how providers declare support for visual capabilities without exposing execution internals.

## 2. Why This DSL Exists

The platform has a validated planning chain:

```
BasicTimeline → BasicTimelineValidator → VisualCapabilityContract → FFmpegBaselineEffectPlanner → FFmpegBaselineTransitionPlanner → FFmpegLibassBasicRenderPlanner → InternalScenarioRunner
```

This chain validates that timelines, effects, transitions, and render plans are correct. The next step is mapping visual capabilities to providers. Today this mapping is implicit in Java code. The DSL makes it explicit, declarative, version-controlled, and schema-validatable.

## 3. What This DSL Is Not

- **Not an execution language.** The DSL does not generate commands, scripts, or filtergraphs.
- **Not a scripting language.** The DSL does not contain logic, control flow, or imperative instructions.
- **Not a runtime integration.** The DSL is design-only. Runtime binding is deferred to P2B.2.
- **Not a public API.** The DSL is internal configuration, not exposed to users.
- **Not a provider internals exposure.** The DSL does not contain shell commands, raw FFmpeg filtergraphs, Remotion component code, Blender scripts, Natron graphs, OFX plugin graphs, or OpenCue job definitions.

## 4. Relationship to Timeline

```
Timeline semantic capability (e.g., SCALE, CROSSFADE)
  ↓
Visual Capability Contract (P2R.0)
  ↓
Provider Capability Binding DSL (P2B.0) ← this document
  ↓
future ProviderBindingRegistry (P2B.2)
  ↓
future ProviderBindingPlan
  ↓
future RenderExecutionPlan
  ↓
future Local Runner / OpenCue ExecutionEnvironment
```

The DSL sits between the Visual Capability Contract and the future ProviderBindingRegistry. It declares which providers support which capabilities.

## 5. Relationship to Visual Capability Contract

P2R.0 defines visual capabilities as platform-owned concepts with status, consistency, fallback, and safety level. The DSL references these capability IDs and adds provider-specific binding metadata (productionAllowed, autoDispatchAllowed, parameter schema constraints).

The DSL does not redefine capabilities. It binds providers to existing capability definitions.

## 6. Relationship to ProviderBindingPlan

The future ProviderBindingPlan (P2B.2) will consume DSL files at runtime to resolve which provider handles a given capability for a given timeline. The DSL is the declaration; the ProviderBindingPlan is the runtime resolution.

## 7. Relationship to RenderExecutionPlan

The future RenderExecutionPlan consumes the ProviderBindingPlan output. The DSL does not reference RenderExecutionPlan IDs or execution details.

## 8. Relationship to FFmpeg/libass Baseline

FFmpeg and libass are the current production baseline. Their DSL bindings declare PRODUCTION_BASELINE status with productionAllowed: true and autoDispatchAllowed: true for supported capabilities.

## 9. Relationship to Remotion

Remotion is non-executable. Its DSL binding declares executionAllowed: false, autoDispatchAllowed: false, and POC/SPIKE status. Capabilities are future/POC only.

## 10. Relationship to OpenCue

OpenCue is an ExecutionEnvironment, not a Provider. OpenCue does not have a provider binding file. OpenCue has an ExecutionEnvironmentNote that clarifies it is not a provider, does not own visual capability semantics, and does not replace ProviderBindingPlan.

## 11. Relationship to Templates and Workflows

Future template definitions may reference provider bindings to declare which providers are eligible for template execution. The DSL does not define templates.

## 12. Relationship to Scenario Runner

The InternalScenarioRunner validates planning correctness. Future P2B.3 will add capability discovery to the scenario runner, allowing scenarios to verify that required capabilities have eligible providers.

## 13. Relationship to Future Parallel Segment/Layer Rendering

Parallel rendering will use ProviderBindingPlan to determine which provider handles each segment/layer. The DSL declares provider capabilities; the execution model decides parallelism.

## 14. Relationship to Artifact DAG Indefinite Deferral

The DSL does not reference Artifact DAG. Artifact DAG is indefinitely deferred (P2A.2). Provider binding does not require Artifact DAG.

## 15. YAML/JSON Schema First Decision

YAML is the preferred format for provider binding declarations because:
- Human-readable and version-control friendly
- No parser generator required
- JSON Schema can validate structure
- Consistent with existing platform configuration patterns

## 16. ANTLR / JavaCC Future-Only Position

ANTLR and JavaCC are parser-generator options for future textual DSL only. They are not adopted now. If a more expressive textual DSL is needed in the future (beyond YAML/JSON Schema), ANTLR or JavaCC could parse it. This is not current work.

## 17. DSL Schema Concept

A provider binding file is a YAML document with the following top-level structure:

```yaml
schemaVersion: "1.0"
kind: ProviderBinding
metadata:
  providerId: string
  providerType: string
  providerStatus: string
  executionAllowed: boolean
  autoDispatchAllowed: boolean
  notes: string
capabilities:
  - capabilityId: string
    category: string
    status: string
    consistency: string
    fallback: string
    productionAllowed: boolean
    autoDispatchAllowed: boolean
    parameterSchema: object (optional)
    safetyConstraints: list (optional)
    notes: string (optional)
```

## 18. Provider Binding File Layout

```
docs/examples/provider-bindings/
  ffmpeg.provider-binding.yaml
  libass.provider-binding.yaml
  remotion.provider-binding.yaml
  opencue.execution-environment-note.yaml
```

## 19. Capability Binding Model

Each capability binding declares:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| capabilityId | string | yes | Visual capability ID from P2R.0 |
| category | enum | yes | EFFECT, TRANSITION, OVERLAY |
| status | enum | yes | PRODUCTION, BASELINE_CANDIDATE, POC, SPIKE, FUTURE, RESTRICTED, FORBIDDEN, DEPRECATED |
| consistency | enum | yes | EXACT, APPROX, PROVIDER_SPECIFIC, UNSUPPORTED, FORBIDDEN, UNKNOWN |
| fallback | enum | yes | NO_FALLBACK, CUT, FADE_OUT_IN, DISABLE_EFFECT, REJECT_REQUEST, MANUAL_REVIEW_REQUIRED, PROVIDER_SPECIFIC_ONLY |
| productionAllowed | boolean | yes | Whether this capability can be used in production |
| autoDispatchAllowed | boolean | yes | Whether automatic dispatch is allowed (requires productionAllowed=true) |
| parameterSchema | object | no | JSON Schema for capability parameters |
| safetyConstraints | list | no | Additional safety constraints |
| notes | string | no | Human-readable notes |

## 20. Parameter Schema Model

Parameter schema uses JSON Schema format to declare allowed parameters for a capability. This constrains what parameters a provider accepts without exposing implementation details.

```yaml
parameterSchema:
  type: object
  properties:
    width:
      type: integer
      minimum: 1
      maximum: 7680
    height:
      type: integer
      minimum: 1
      maximum: 4320
  required: [width, height]
```

## 21. Status / Consistency / Fallback Vocabulary

### Provider Lifecycle Status

| Status | Meaning |
|--------|---------|
| PRODUCTION_BASELINE | Current production baseline |
| INTERNAL_FOUNDATION | Internal implementation foundation |
| POC_CANDIDATE | Proof-of-concept candidate |
| SPIKE_CANDIDATE | Technical spike candidate |
| FUTURE_EXTENSION | Planned future extension |
| NOT_ADOPTED | Evaluated and not adopted |
| DEFERRED | Deferred to later phase |
| FORBIDDEN_FOR_CURRENT_PATH | Explicitly excluded |

### Capability Status (aligned with P2R.0)

| Status | Meaning |
|--------|---------|
| PRODUCTION | Production-ready |
| BASELINE_CANDIDATE | Candidate for baseline promotion |
| POC | Proof-of-concept |
| SPIKE | Technical spike |
| FUTURE | Planned future capability |
| RESTRICTED | Restricted use (internal/manual review) |
| FORBIDDEN | Explicitly forbidden |
| DEPRECATED | Deprecated, will be removed |

### Consistency Level (aligned with P2R.0)

| Level | Meaning |
|-------|---------|
| EXACT | Exact match across providers |
| APPROX | Approximate match |
| PROVIDER_SPECIFIC | Provider-specific behavior |
| UNSUPPORTED | Not supported |
| FORBIDDEN | Explicitly forbidden |
| UNKNOWN | Unknown consistency |

### Fallback Behavior (aligned with P2R.0)

| Behavior | Meaning |
|----------|---------|
| NO_FALLBACK | No fallback available |
| CUT | Hard cut between clips |
| FADE_OUT_IN | Fade out then fade in |
| DISABLE_EFFECT | Disable the effect |
| REJECT_REQUEST | Reject the entire request |
| MANUAL_REVIEW_REQUIRED | Requires manual review |
| PROVIDER_SPECIFIC_ONLY | Only available with specific provider |

## 22. Safety and Fail-Closed Rules

The DSL enforces fail-closed validation:

1. **Unknown provider → reject.** If providerId is not recognized, the binding is invalid.
2. **Unknown capability → reject.** If capabilityId is not in the Visual Capability Contract, the binding is invalid.
3. **Missing schemaVersion → reject.** Every binding must declare a schema version.
4. **Missing provider id → reject.** Every binding must declare a provider identity.
5. **Missing capability id → reject.** Every capability binding must declare an identity.
6. **FORBIDDEN capability → reject.** Capabilities with FORBIDDEN status cannot be bound.
7. **RESTRICTED capability → manual review / internal only.** RESTRICTED capabilities require explicit review.
8. **POC capability → internal only by default.** POC capabilities are not production-eligible.
9. **autoDispatchAllowed cannot be true unless productionAllowed is true.** Auto-dispatch requires production eligibility.
10. **productionAllowed cannot be true for POC/SPIKE/FUTURE/RESTRICTED/FORBIDDEN.** Only PRODUCTION and BASELINE_CANDIDATE can be production-allowed.
11. **executionAllowed=false means no runtime execution selection.** The provider cannot be selected for execution.

## 23. Forbidden Fields

The DSL must not contain the following fields. These represent execution internals, provider internals, or security-sensitive paths:

| Field | Reason |
|-------|--------|
| command | Shell command execution |
| shellCommand | Shell command execution |
| rawCommand | Raw provider command |
| filtergraph | FFmpeg filtergraph |
| filter_complex | FFmpeg filter_complex |
| script | Arbitrary script execution |
| nodeScript | Node.js script |
| pythonScript | Python script |
| remotionComponent | Remotion component code |
| blenderPython | Blender Python script |
| natronGraph | Natron node graph |
| ofxGraph | OFX plugin graph |
| openCueJob | OpenCue job definition |
| openCueLayer | OpenCue layer definition |
| openCueFrame | OpenCue frame definition |
| renderExecutionPlanId | Execution plan reference |
| artifactGraphId | Artifact DAG reference |
| bucket | Storage bucket |
| objectKey | Storage object key |
| signedUrl | Signed URL |
| materializedPath | Materialized file path |
| storageReferenceId | Storage reference |
| productId | Product identifier |

## 24. Example Bindings

See `docs/examples/provider-bindings/` for complete examples:

- `ffmpeg.provider-binding.yaml` — FFmpeg production baseline
- `libass.provider-binding.yaml` — libass production baseline
- `remotion.provider-binding.yaml` — Remotion non-executable POC
- `opencue.execution-environment-note.yaml` — OpenCue is not a provider

## 25. Validation Strategy

### Current (P2B.0)
- Design-only, no runtime validation
- YAML examples are manually reviewed for safety

### Future (P2B.2)
- JSON Schema validation for structure
- Java validator for semantic rules (fail-closed, status consistency)
- Forbidden field scanner
- Integration with InternalScenarioRunner for capability discovery

## 26. Future Registry Integration

P2B.2 will implement ProviderBindingRegistry that:
- Loads DSL files from classpath or configuration directory
- Validates against JSON Schema and semantic rules
- Provides lookup by provider ID and capability ID
- Integrates with ProviderBindingPlan resolution

## 27. Future OpenCue Integration

OpenCue integration (P2O.0-P2O.4) will:
- Consume RenderExecutionPlan output
- Dispatch jobs to OpenCue ExecutionEnvironment
- Not require provider binding (OpenCue is not a provider)
- Not require Artifact DAG

## 28. What Is Intentionally Not Implemented

- Runtime ProviderBindingRegistry (P2B.2)
- Runtime ProviderBindingPlan integration (P2B.2)
- RenderExecutionPlan integration
- OpenCue adapter (P2O.0-P2O.4)
- ANTLR/JavaCC parser generators
- YAML parsing runtime
- Public API endpoints
- Database persistence
- Full Java model (deferred to P2B.2)

## 29. Follow-up Tasks

- P2B.1 — FFmpeg/libass Binding Fixtures (populate and validate example bindings)
- P2B.2 — Binding Validator / Registry (runtime integration)
- P2B.3 — Scenario Runner Capability Discovery (integrate with P2X.0)
- P2O.0 — OpenCue PVE Testbed Smoke Harness
