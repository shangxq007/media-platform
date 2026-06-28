# Remotion MANUAL/EXPERIMENT Draft Production v0

## Overview

Enables Remotion execution document draft production in controlled MANUAL/EXPERIMENT provider binding modes. The full pipeline from binding to generation is now wired end-to-end.

## Binding Behavior

| Mode | Remotion Selected? | FFmpeg Selected? |
|------|-------------------|-----------------|
| PRODUCTION | ❌ No | ✅ Yes |
| MANUAL | ✅ Yes (if capabilities match) | ✅ Yes (preferred) |
| EXPERIMENT | ✅ Yes (if capabilities match) | ✅ Yes (preferred) |

## End-to-End Flow

```text
MANUAL/EXPERIMENT ProviderBindingCompiler
  → Remotion candidate (POC, autoDispatch=false)
  → Remotion binding node (for matching capabilities)
  → ProviderExecutionDocumentDraftCompiler
  → REMOTION_INPUT_PROPS_DOCUMENT draft (generationReady=false)
  → ProviderExecutionDocumentGenerationService
  → RemotionProviderExecutionDocumentGenerator
  → RemotionInputProps → validate → serialize
  → ProviderExecutionDocumentGenerationResult (generationReady=false)
  → audit event: PROVIDER_EXECUTION_DOCUMENT_GENERATED
```

## What Changed

No production code changes. The existing `ProviderBindingCompiler` already supports MANUAL mode with POC candidates. The existing `ProviderExecutionDocumentDraftCompiler` already maps "remotion" → REMOTION_INPUT_PROPS_DOCUMENT. The integration was already wired in P1R.2.

This task verifies and tests the full end-to-end flow.

## Safety Rules

- Remotion remains POC (not productionDispatchEligible)
- Remotion autoDispatch=false
- Remotion only selected in MANUAL/EXPERIMENT mode
- Remotion never selected in PRODUCTION mode
- generationReady=false for all Remotion documents
- RenderPlanPolicyGuard rejects Remotion execution
- LocalExecutionPlanRunner does not execute Remotion
- No Node/npm/npx in new code
- No public API exposure

## Known Limitations

- Default FFmpeg path does not produce Remotion drafts
- Remotion drafts only produced in explicit MANUAL/EXPERIMENT scenarios
- No Remotion CLI availability check
- No Remotion execution
