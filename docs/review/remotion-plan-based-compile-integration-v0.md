# Remotion Plan-Based Compile Integration v0

## Overview

Integrated `ProviderExecutionDocumentGenerationService` into the plan-based compile orchestration in `PlanBasedTimelineRevisionRenderService`. Document generation runs after `ProviderExecutionDocumentDraftCompiler` produces drafts, as a diagnostic/planning step.

## Integration Point

```text
PlanBasedTimelineRevisionRenderService.doRender()
  → draftCompiler.compile(bindingPlan)
  → docGenerationService.generate(drafts, timeline)  // NEW
  → emit audit events for generated/rejected docs
  → planCompiler.compile(bindingPlan, drafts, policy)
```

## Behavior

- Default FFmpeg path: produces FFMPEG_COMMAND_PLAN drafts → skipped by generation service
- Remotion draft (explicit test/manual): generates RemotionInputProps → validation → serialized JSON
- Non-Remotion drafts: skipped safely
- Audit events emitted: PROVIDER_EXECUTION_DOCUMENT_GENERATED, PROVIDER_EXECUTION_DOCUMENT_REJECTED

## What Changed

- `PlanBasedTimelineRevisionRenderService`: added `docGenerationService` field (auto-initialized), generation call after drafts, audit events
- `RenderAuditEventType`: added PROVIDER_EXECUTION_DOCUMENT_GENERATED, PROVIDER_EXECUTION_DOCUMENT_REJECTED

## What Did Not Change

- Default FFmpeg execution path unchanged
- Public API unchanged
- Remotion not executable
- Remotion not production eligible
- RenderPlanPolicyGuard still rejects Remotion
- LocalExecutionPlanRunner still FFmpeg-only

## Audit Events

| Event | When | Fields |
|-------|------|--------|
| PROVIDER_EXECUTION_DOCUMENT_GENERATED | Document generated | providerName, documentType, generationReady |
| PROVIDER_EXECUTION_DOCUMENT_REJECTED | Document rejected | providerName, documentType, issues |

Events use safe correlation fields only. No serialized JSON in events.

## Safety

- generationReady=false for all v0 documents
- No Remotion execution
- No Node/npm/npx
- No public API exposure
- No storage internals in events
