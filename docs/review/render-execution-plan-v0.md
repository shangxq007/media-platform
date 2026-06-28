# Render Execution Plan v0

## Overview

The Render Execution Plan is the deterministic planning structure that maps a ProviderBindingPlan into a sequence of execution steps. It sits at the end of the compile pipeline, after binding and document draft planning.

```text
LogicalCapabilityGraph (provider-neutral)
    ↓ ProviderBindingCompiler
ProviderBindingPlan (provider-bound)
    ↓ ProviderExecutionDocumentDraftCompiler
List<ProviderExecutionDocumentDraft> (document plans)
    ↓ RenderExecutionPlanCompiler
RenderExecutionPlan (step plan)
    ↓ RenderPlanPolicyGuard
RenderPlanPolicyResult (validation verdict)
```

## Domain Model

### RenderExecutionPlan

Top-level plan with deterministic ID, steps, and policy:

- `planId` — deterministic SHA-256 from binding plan ID + policy mode
- `bindingPlanId` — source binding plan reference
- `timelineId` — source timeline reference
- `policy` — execution policy used
- `environmentTarget` — LOCAL, OPENCUE, FUTURE_EXTERNAL
- `steps` — ordered execution steps
- `executionReady` — always false in v0
- `failureReasons` — plan-level failure reasons

### RenderExecutionStep

Individual planning step:

- `stepId` — deterministic SHA-256 from plan ID + step type + node ID
- `type` — MATERIALIZE_INPUT, PREPARE_PROVIDER_DOCUMENT, EXECUTE_PROVIDER, VERIFY_OUTPUT, REGISTER_OUTPUT, LINK_PRODUCT_DEPENDENCY, FINALIZE_RENDER
- `status` — PENDING, READY, SKIPPED, FAILED, BLOCKED
- `nodeId` — source capability node ID
- `artifactNodeType` — INPUT_MEDIA, TRIMMED_MEDIA, etc.
- `providerName` — bound provider name (null for non-provider steps)
- `providerRef` — BoundProviderRef (null for non-provider steps)
- `documentDraft` — ProviderExecutionDocumentDraft (null for non-document steps)
- `dependencies` — step IDs this step depends on
- `executionReady` — false for all v0 steps
- `executionEnvironmentTarget` — LOCAL for FFmpeg
- `label` — human-readable label
- `metadata` — immutable metadata (no raw commands, no storage internals)

### ExecutionPolicy

Policy configuration for plan evaluation:

- `mode` — PRODUCTION, MANUAL, EXPERIMENT, DRY_RUN
- `allowManualProviders` — whether POC/OPTIONAL are allowed
- `allowExperimentalProviders` — whether HOLD/SPIKE are allowed
- `allowOpenCueSubmit` — whether OpenCue submission is enabled
- `allowProviderExecution` — whether provider execution is allowed (false in v0)
- `maxStepCount` — maximum steps allowed (0 = unlimited)

### ExecutionEnvironmentTarget

- `LOCAL` — default for FFmpeg baseline
- `OPENCUE` — reserved, not submitted in v0
- `FUTURE_EXTERNAL` — reserved only

## Step Sequence

For each bound node:

1. **MATERIALIZE_INPUT** (INPUT_MEDIA only) — plan to materialize input from storage
2. **PREPARE_PROVIDER_DOCUMENT** — plan to prepare provider execution document
3. **EXECUTE_PROVIDER** — placeholder (executionReady=false)

For FINAL_RENDER nodes:

4. **VERIFY_OUTPUT** — plan to verify output artifact
5. **REGISTER_OUTPUT** — plan to register output in storage/product
6. **LINK_PRODUCT_DEPENDENCY** — plan to link product dependency edge

Plan-level:

7. **FINALIZE_RENDER** — finalize render job (depends on all output steps)

## Dependency Chain

```
MATERIALIZE_INPUT
    ↓
PREPARE_PROVIDER_DOCUMENT
    ↓
EXECUTE_PROVIDER
    ↓
VERIFY_OUTPUT (FINAL_RENDER only)
    ↓
REGISTER_OUTPUT
    ↓
LINK_PRODUCT_DEPENDENCY
    ↓
FINALIZE_RENDER
```

## Policy Guard

The RenderPlanPolicyGuard validates plans against 14 safety constraints:

1. No unbound required capability node may become executable
2. No non-production provider in PRODUCTION mode
3. No provider with autoDispatch=false in automatic mode
4. No missing tool provider
5. No OpenFX capability without host
6. No OpenCue target unless enabled
7. No raw command in steps
8. No process environment in steps
9. No local materialized path in public surface
10. No storage internals (bucket/key/rootPath/etc) in public surface
11. Final output must have verification and registration steps
12. Plan must be acyclic
13. Step IDs must be deterministic
14. Dependency graph must be valid

### Policy Guard Statuses

| Status | Description |
|--------|-------------|
| VALID_FOR_DRY_RUN | Plan passed all checks (dry-run only in v0) |
| NOT_EXECUTABLE | Plan has policy violations |
| FAILED_CLOSED | Plan rejected by safety constraints |

## v0 Constraints

- All steps have `executionReady=false`
- No provider execution occurs
- No command generation
- No StorageRuntime mutation
- No ProductRuntime mutation
- No OpenCue submit
- Plans are VALID_FOR_DRY_RUN at best
- FFmpeg remains the only PRODUCTION baseline provider

## Failure Reasons

| Reason | Description |
|--------|-------------|
| UNBOUND_CAPABILITY_NODE | Required capability node is unbound |
| MISSING_DOCUMENT_DRAFT | Provider execution document draft missing |
| NON_PRODUCTION_PROVIDER_IN_PRODUCTION_MODE | Non-production provider in PRODUCTION mode |
| PROVIDER_TOOL_UNAVAILABLE | Provider tool binary not available |
| OPENCUE_NOT_ENABLED | OpenCue submit requested but not enabled |
| OPENFX_REQUIRES_HOST | OpenFX capability without host |
| STEP_COUNT_EXCEEDED | Plan exceeds maximum step count |
| CYCLIC_DEPENDENCY | Plan contains a cycle |
| MISSING_FINAL_OUTPUT | Missing final output step |
| POLICY_VIOLATION | Policy guard rejected the plan |

## Internal Only

The execution plan, policy guard, and all related types are internal only. They are not exposed in public APIs. No provider names, storage paths, commands, or environment details leak into public surfaces.

## Determinism

All IDs are deterministic SHA-256 based:
- Plan ID: SHA-256 of binding plan ID + policy mode
- Step IDs: SHA-256 of plan ID + step type + node ID
- No random UUIDs anywhere in the pipeline
