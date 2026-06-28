# Remotion Execution Policy and Sandbox Design v0

## Overview

Defines the internal Remotion execution policy and sandbox contract before any real execution is introduced. All models are data-only — no execution.

## Design Note: Remotion-Specific, Not Provider-Neutral

All preflight, status, policy, sandbox, and evaluator classes are **Remotion-specific**. No generic `ProviderExecutionPreflightStatus`, `ProviderExecutionPolicy` SPI, or provider-neutral preflight abstraction is introduced.

**Rationale:** There is currently only one non-FFmpeg provider (Remotion). Abstracting prematurely would lock in API shapes before a second provider validates the pattern.

**Future path:** When a second non-FFmpeg provider is introduced, evaluate extracting a shared provider-neutral preflight status/result model. Until then, keep all execution policy models provider-specific.

## Execution Policy

### RemotionExecutionPolicy

| Field | Default | Notes |
|-------|---------|-------|
| executionEnabled | false | Execution disabled in v0 |
| productionAllowed | false | Never in production |
| autoDispatchAllowed | false | Never auto-dispatched |
| manualModeAllowed | false (default), true (design-only) | Manual mode not yet wired to runner |
| experimentModeAllowed | false (default), true (design-only) | Experiment mode not yet wired to runner |
| publicSelectionAllowed | false | No public API selection |
| userSuppliedComponentAllowed | false | No user React components |
| userSuppliedJavaScriptAllowed | false | No user JS |
| networkAllowed | false | No network access |
| packageInstallAllowed | false | No npm install |
| npxPackageDownloadAllowed | false | No npx download |
| auditRequired | true | Audit events required |
| correlationRequired | true | Correlation context required |
| timeoutRequired | true | Timeout required |
| resourceLimitsRequired | true | Resource limits required |

### Factory Methods

- `disabledDefault()` — everything disabled, executionEnabled=false
- `manualExperimentDesignOnly()` — manual/experiment flags true, executionEnabled=false
- `futureLocalPocDisabledByDefault()` — same as design-only, executionEnabled=false

## Sandbox Policy

### RemotionSandboxPolicy

All constraints locked down by default:

| Constraint | Default |
|-----------|---------|
| managedWorkingDirectoryRequired | true |
| managedOutputDirectoryRequired | true |
| storageMaterializedInputsRequired | true |
| prohibitRawStorageInternals | true |
| prohibitSignedUrls | true |
| prohibitArbitraryUserPaths | true |
| prohibitEnvironmentLeakage | true |
| prohibitInheritedSecrets | true |
| prohibitNetwork | true |
| prohibitPackageInstall | true |
| prohibitUserUploadedProject | true |
| prohibitDynamicImportsFromUserContent | true |
| cleanupRequired | true |
| quarantineOnFailure | true |
| auditBeforeExecutionRequired | true |
| auditAfterExecutionRequired | true |

## Command Plan

### RemotionExecutionCommandPlan

Structured, non-executing command model:

- `commandKind` — "render", "preview"
- `executableRef` — trusted internal reference (e.g., "internal://remotion")
- `arguments` — structured list, not shell-joined
- `workingDirectoryRef` — managed reference
- `inputPropsRef` — managed props file reference
- `outputRef` — managed output file reference
- `timeoutSeconds` — positive timeout
- `networkPolicy` — DENIED (default), INTERNAL_ONLY, ALLOWED
- `trustedTemplateRef` — trusted internal template
- `compositionId` — from allowlist

### Allowed Future Command Shape

```
executableRef: internal://remotion
arguments: ["--props", "<managed-props-path>"]
workingDirectoryRef: <managed-work-dir>
inputPropsRef: <managed-props-file>
outputRef: <managed-output-file>
timeoutSeconds: 300
networkPolicy: DENIED
```

### Forbidden Command Shapes

- Shell string commands
- `npx remotion render`
- `npm install`
- `npm exec`
- `pnpm dlx` / `yarn dlx`
- Arbitrary `node` script
- Signed URL arguments
- Bucket/objectKey arguments
- Secret-looking arguments
- User-controlled working directory
- User-controlled component path
- User-controlled npm package

## Preflight Evaluator

### RemotionExecutionPolicyEvaluator

Evaluates preflight conditions. Returns `RemotionExecutionPreflightResult`.

### Preflight Statuses

| Status | Description |
|--------|-------------|
| BLOCKED_BY_POLICY | Execution not enabled |
| BLOCKED_BY_RUNTIME | Runtime tools not ready |
| BLOCKED_BY_SANDBOX | Sandbox constraints violated |
| BLOCKED_BY_UNSUPPORTED_DOCUMENT | Document not supported |
| BLOCKED_BY_UNSAFE_COMMAND | Command plan unsafe |
| READY_BUT_EXECUTION_DISABLED | All checks pass, execution still disabled |
| NOT_IMPLEMENTED | Execution not implemented (v0 default) |

### v0 Behavior

All evaluations return NOT_IMPLEMENTED or BLOCKED_BY_POLICY. `readyToExecute` is always false.

## Audit/Correlation Contract (Future)

Required fields for future execution:
- renderCorrelationId
- renderRequestFingerprint
- providerBindingPlanId
- renderExecutionPlanId
- documentId
- providerName=remotion
- before/after execution events

## Architecture Rules

- Execution policy is internal only
- Sandbox policy is internal only
- Command plan is internal only
- Preflight result is internal only
- No public API exposure
- FFmpeg remains only executable provider
- Remotion remains POC
- ExecutionReady=false always in v0

## Future P1R.6 Prerequisites

1. ✅ Execution policy model
2. ✅ Sandbox policy model
3. ✅ Command plan model
4. ✅ Preflight evaluator
5. RemotionLocalExecutionRunner (future)
6. Actual sandbox process execution (future)
7. Production eligibility review (future)
