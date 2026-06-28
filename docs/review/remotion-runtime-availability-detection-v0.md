# Remotion Runtime Availability Detection v0

## Overview

Detects whether the local/runtime environment has tools needed for future Remotion execution. Diagnostic only — does NOT execute Remotion.

## Model

### RemotionRuntimeAvailability

- `nodeAvailable`, `nodeVersion`
- `npmAvailable`, `npmVersion`
- `npxAvailable`, `npxVersion`
- `remotionCliAvailable` — always false (not probed)
- `documentGenerationReady` — true (P1R.0-P1R.3 complete)
- `executionReady` — always false
- `disabledByPolicy` — always true
- `toolStatuses` — per-tool status list
- `issues` — diagnostic issues

### RemotionRuntimeToolStatus

Per-tool: toolName, status (AVAILABLE/MISSING/NOT_CHECKED/CHECK_FAILED), version, issue

### RemotionProviderReadiness

Combines runtime + policy:
- `documentGenerationReady` = true
- `runtimeToolsAvailable` = depends on environment
- `executionReady` = false (always)
- `productionEligible` = false (always)
- `autoDispatch` = false (always)
- `providerStatus` = "POC"
- `blockedReasons` — execution disabled by policy, POC status, etc.

## Safe Probes

| Command | Safe? | Notes |
|---------|-------|-------|
| `node --version` | ✅ | Version only, no network |
| `npm --version` | ✅ | Version only, no network |
| `npx --version` | ✅ | Version only, no network |
| `remotion --version` | ❌ | Not probed — may download |
| `npx remotion` | ❌ | May download packages |
| `npm install` | ❌ | Downloads packages |
| `npx remotion render` | ❌ | Executes rendering |

## Integration

`RenderToolCapabilityInventory` now includes `npx` detection alongside existing `node`, `npm`, `ffmpeg`, etc.

`RemotionRuntimeProbe` builds `RemotionRuntimeAvailability` from inventory results.

`RemotionProviderReadiness.from()` combines availability with policy.

## Safety Rules

- executionReady=false always
- disabledByPolicy=true always
- productionEligible=false always
- autoDispatch=false always
- No Remotion render execution
- No npm/npx package download
- No network access
- No environment variables exposed
- No local paths in public surfaces
- Runtime availability does not affect fingerprint or dedup

## Architecture Rules

- FFmpeg remains only executable provider
- Remotion remains POC
- Detection is diagnostic only
- Public API unchanged
