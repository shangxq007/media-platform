# Provider Binding Compile v0

## Overview

Provider binding is the compile stage that maps provider-neutral capability graph nodes to specific providers. It sits between the LogicalCapabilityGraph (from N4+) and execution document drafts.

```
LogicalCapabilityGraph (provider-neutral)
    ↓ ProviderBindingCompiler
ProviderBindingPlan (provider-bound)
    ↓ ProviderExecutionDocumentDraftCompiler
List<ProviderExecutionDocumentDraft> (document plans)
```

## Domain Model

### ProviderBindingPlan

Top-level plan derived from a LogicalCapabilityGraph. Contains:

- `planId` — deterministic SHA-256 ID from capability graph ID
- `capabilityGraphId` — source capability graph reference
- `timelineId` — source timeline reference
- `nodes` — list of ProviderBindingNode with binding decisions
- `edges` — topology edges (same as capability graph)
- `bindingMode` — PRODUCTION, MANUAL, EXPERIMENT
- `allBound` — true if all nodes have a bound provider
- `hasFailures` — true if any node failed binding

### ProviderBindingNode

Maps a single capability graph node to its binding decision:

- `nodeId` — matches capability graph node ID
- `artifactNodeType` — INPUT_MEDIA, TRIMMED_MEDIA, etc.
- `requiredCapabilities` — list of required capability codes
- `decision` — ProviderBindingDecision with selected provider, candidates, and failure info

### ProviderBindingDecision

Decision record for a single node:

- `status` — BOUND, UNBOUND, UNSUPPORTED, TOOL_UNAVAILABLE, NOT_PRODUCTION_ELIGIBLE, etc.
- `selectedProvider` — BoundProviderRef if bound (null if failed)
- `candidates` — all candidate providers considered (for traceability)
- `failureReason` — explicit reason enum (null if bound)
- `explanation` — human-readable explanation

### BoundProviderRef

Reference to a selected provider:

- `providerName` — "ffmpeg", "mlt", etc.
- `providerStatus` — PRODUCTION, POC, etc.
- `providerType` — RENDER, PACKAGING, etc.
- `priority` — P0, P1, P2, P3
- `autoDispatch` — whether provider supports auto-dispatch
- `toolAvailable` — whether tool binary is locally available
- `toolVersion` — detected version string
- `score` — binding score (lower = preferred)

## Binding Rules

### Mode-Based Eligibility

| Mode | PRODUCTION | POC | OPTIONAL | HOLD | SPIKE | STUB | SKELETON | DEPRECATED | MOCK |
|------|-----------|-----|----------|------|-------|------|----------|------------|------|
| PRODUCTION | ✅ (if autoDispatch) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MANUAL | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| EXPERIMENT | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### Scoring

Lower score = preferred. Factors:

1. **Status score**: PRODUCTION=0, OPTIONAL=100, POC=200, HOLD=300, SPIKE=400
2. **Priority score**: P0=0, P1=10, P2=20, P3=30, default=40
3. **Tool unavailability penalty**: +1000

### Capability Matching

A provider matches a capability node if:
1. Provider's `enabledCapabilities` contains ALL required capabilities
2. Provider's `notFor` does NOT contain any required capability
3. Provider is eligible for the binding mode

## Execution Document Drafts

Each bound node produces a ProviderExecutionDocumentDraft:

| Provider | Document Type |
|----------|--------------|
| ffmpeg | FFMPEG_COMMAND_PLAN |
| mlt | MLT_PROJECT_DOCUMENT |
| remotion | REMOTION_INPUT_PROPS_DOCUMENT |
| blender | BLENDER_SCENE_SPEC |
| natron | NATRON_PROJECT_SPEC |
| gpac/MP4Box | PACKAGING_PLAN |
| gstreamer | GSTREAMER_PIPELINE_SPEC |
| openfx | OPENFX_EFFECT_DESCRIPTOR |
| other | UNKNOWN |

v0 drafts are planning artifacts only — `generationReady` is always false.

## Failure Reasons

| Reason | Status | Description |
|--------|--------|-------------|
| REQUIRED_CAPABILITY_MISSING | UNSUPPORTED | No provider declares the required capability |
| PROVIDER_NOT_PRODUCTION_ELIGIBLE | NOT_PRODUCTION_ELIGIBLE | Matching providers exist but none are production-eligible |
| TOOL_UNAVAILABLE | TOOL_UNAVAILABLE | Provider exists but tool binary not found |
| PROVIDER_DISABLED | DISABLED | Provider is disabled by configuration |
| MULTIPLE_PROVIDERS_AMBIGUOUS | AMBIGUOUS | Multiple providers match equally |

## Internal Only

This compile stage is internal only. Binding decisions, provider names, scores, and failure reasons are not exposed in public render APIs.

## Determinism

All binding decisions are deterministic for the same inputs:
- Plan ID: SHA-256 of capability graph ID + "provider-binding-plan"
- Draft IDs: SHA-256 of node ID + provider name + document type
- No random UUIDs anywhere in the pipeline

## Downstream

The ProviderBindingPlan feeds into:
- `RenderExecutionPlanCompiler` → `RenderExecutionPlan` (step planning)
- `RenderPlanPolicyGuard` → `RenderPlanPolicyResult` (validation)
- See `docs/review/render-execution-plan-v0.md`
