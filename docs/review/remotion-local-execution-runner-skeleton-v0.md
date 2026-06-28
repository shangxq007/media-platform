# Remotion Local Execution Runner Skeleton v0

## Overview

Internal skeleton for a future Remotion local execution runner. Disabled by default. Never executes external processes.

## Domain Model

### RemotionLocalExecutionRunner

- Accepts `RemotionLocalExecutionRequest`
- Runs `RemotionExecutionPolicyEvaluator` preflight
- Maps preflight status to execution status
- Returns `RemotionLocalExecutionResult`
- Never starts a process
- Never calls ProcessToolRunner, StorageRuntime, or ProductRuntime

### RemotionLocalExecutionRequest

- `documentGenerationResult` — ProviderExecutionDocumentGenerationResult
- `runtimeAvailability` — RemotionRuntimeAvailability
- `providerReadiness` — RemotionProviderReadiness
- `executionPolicy` — RemotionExecutionPolicy
- `sandboxPolicy` — RemotionSandboxPolicy
- `commandPlan` — RemotionExecutionCommandPlan (null if not yet created)
- `safeMetadata` — safe metadata only

### RemotionLocalExecutionResult

- `status` — NOT_IMPLEMENTED, BLOCKED_BY_*, REJECTED_*, FAILED_CLOSED
- `preflightStatus` — source preflight status
- `executed` — always false
- `readyToExecute` — always false
- `outputProductId` — always null
- `outputPathRef` — always null
- `safeMessage`, `violations`, `safeMetadata`

### RemotionLocalExecutionStatus

NOT_IMPLEMENTED, BLOCKED_BY_PREFLIGHT, BLOCKED_BY_POLICY, BLOCKED_BY_RUNTIME, BLOCKED_BY_SANDBOX, BLOCKED_BY_UNSAFE_COMMAND, REJECTED_UNSUPPORTED_DOCUMENT, FAILED_CLOSED

## Preflight Mapping

| Preflight Status | Execution Status |
|-----------------|-----------------|
| BLOCKED_BY_POLICY | BLOCKED_BY_POLICY |
| BLOCKED_BY_RUNTIME | BLOCKED_BY_RUNTIME |
| BLOCKED_BY_SANDBOX | BLOCKED_BY_SANDBOX |
| BLOCKED_BY_UNSUPPORTED_DOCUMENT | REJECTED_UNSUPPORTED_DOCUMENT |
| BLOCKED_BY_UNSAFE_COMMAND | BLOCKED_BY_UNSAFE_COMMAND |
| READY_BUT_EXECUTION_DISABLED | NOT_IMPLEMENTED |
| NOT_IMPLEMENTED | NOT_IMPLEMENTED |

## Safety Rules

- executed=false always
- readyToExecute=false always
- outputProductId=null always
- outputPathRef=null always
- No ProcessToolRunner dependency
- No StorageRuntime dependency
- No ProductRuntime dependency
- No Node/npm/npx/remotion execution
- No raw commands in result
- No local paths in result
- No storage internals in result
- Not wired into LocalExecutionPlanRunner
- Not wired into default PLAN_BASED render

## Architecture Rules

- FFmpeg remains only executable provider
- Remotion remains POC
- Runner is internal only
- Not integrated into production execution path
- No public API exposure
