# Remotion Local Execution Runner Audit v0

## Overview

Adds safe audit/correlation events for RemotionLocalExecutionRunner precheck outcomes. Runner remains disabled by default — never executes.

## Audit Event Types

| Event Type | When Emitted |
|-----------|-------------|
| PROVIDER_LOCAL_EXECUTION_PRECHECK_REJECTED | Any blocked/rejected outcome |
| PROVIDER_LOCAL_EXECUTION_NOT_IMPLEMENTED | READY_BUT_EXECUTION_DISABLED or NOT_IMPLEMENTED |
| PROVIDER_LOCAL_EXECUTION_FAILED_CLOSED | Null request or unexpected failure |

## Safe Audit Payload

**Included:**
- providerName=remotion
- documentId, documentType, draftId (from generation result)
- generationReady=false
- renderCorrelationId (if provided)
- renderRequestFingerprint (if provided)
- timelineRevisionId, providerBindingPlanId, renderExecutionPlanId (if in correlation)
- violation count

**Excluded:**
- Serialized RemotionInputProps
- Serialized document JSON
- Command arguments / raw command
- Local materialized paths
- Bucket/objectKey/rootPath/relativePath/signedUrl
- Process environment
- Secrets
- Full exception stack traces

## Correlation Support

`RemotionLocalExecutionRequest` now accepts optional `RenderCorrelationContext`:
- renderCorrelationId, renderRequestFingerprint
- timelineRevisionId, providerBindingPlanId, renderExecutionPlanId
- Null-safe — runner works without correlation context

## Runner Behavior (unchanged)

- executed=false always
- readyToExecute=false always
- No external process started
- No StorageRuntime/ProductRuntime calls
- Audit failure does not break runner result

## Architecture Rules

- Runner remains internal only
- Not wired into default PLAN_BASED execution
- Not wired into LocalExecutionPlanRunner
- FFmpeg remains only executable provider
- Remotion remains POC
