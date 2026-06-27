---
status: foundation
created: 2026-06-28
scope: render-module
truth_level: current
owner: platform
---

# OpenCue Runtime Foundation

## Summary

OpenCue is modeled as a disabled-by-default **ExecutionEnvironment** (not a RenderProvider).
The foundation is complete for future integration but requires no real OpenCue cluster for current operation.

## Architecture

OpenCue is an **ExecutionEnvironment**, not a RenderProvider:
- It receives execution jobs from the platform
- It dispatches render tasks to workers
- It reports status back to the platform
- It does NOT replace FFmpeg, Remotion, or other RenderProviders
- Those providers would run ON OpenCue workers

## Current State

### Classes

| Class | File | Purpose |
|-------|------|---------|
| `OpenCueProperties` | `domain/environment/OpenCueProperties.java` | Configuration with safe defaults |
| `OpenCueExecutionEnvironment` | `infrastructure/environment/OpenCueExecutionEnvironment.java` | ExecutionEnvironment implementation (stub) |
| `OpenCueEnvironmentCompiler` | `infrastructure/environment/OpenCueEnvironmentCompiler.java` | EnvironmentCompiler: spec → job |
| `OpenCueJobSpec` | `domain/environment/OpenCueJobSpec.java` | Platform model for job submission |
| `OpenCueJobSpecValidator` | `domain/environment/OpenCueJobSpecValidator.java` | Fail-closed job spec validation |

### Conditional Bean Gating

Both `OpenCueExecutionEnvironment` and `OpenCueEnvironmentCompiler` are gated by:
```java
@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true", matchIfMissing = false)
```

When `opencue.enabled=false` (default), neither bean is loaded into the Spring context.

### Safe Defaults

| Property | Default | Effect |
|----------|---------|--------|
| `opencue.enabled` | `false` | Beans not loaded |
| `opencue.stub-mode-enabled` | `true` | Stub submit returns fake execution ID |
| `opencue.production-submit-enabled` | `false` | Real submit rejected |
| `opencue.allow-network-submit` | `false` | Network submit disabled |
| `opencue.server` | `localhost` | Safe default host |
| `opencue.grpc-port` | `8443` | Standard port |

### Submit Path Safety

1. If `enabled=false`: submit throws `IllegalStateException`
2. If `enabled=true` AND `stubModeEnabled=true`: returns fake `oc-{timestamp}` ID
3. If `enabled=true` AND `stubModeEnabled=false` AND `productionSubmitEnabled=false`: throws `IllegalStateException`
4. If `enabled=true` AND `productionSubmitEnabled=true`: logs and returns fake ID (Phase 1 — real submit deferred)

### Status Mapping

| OpenCue State | Platform Status |
|---------------|-----------------|
| `pending` | SUBMITTED |
| `queued` | QUEUED |
| `running` | RUNNING |
| `succeeded` | COMPLETED |
| `dead` | FAILED |
| `killed` | CANCELLED |
| `dependent` | QUEUED |
| null/blank/unknown | FAILED |

### Architecture Boundaries

- Never accesses repositories
- Never modifies ProductRuntime
- Never performs planning
- Never calculates pricing, billing, quota, or metering
- Environment reports state — platform owns lifecycle
- No raw command, env, worker host, or secrets exposed in public API

## Tests

| Test | File | Coverage |
|------|------|----------|
| `OpenCuePropertiesTest` | domain/environment/ | Property defaults, fail-closed flags |
| `OpenCueJobSpecValidatorTest` | domain/environment/ | Job spec validation, injection patterns |
| `OpenCueEnvironmentTest` | infrastructure/environment/ | Lifecycle mapping, stub submit, disabled behavior, compiler determinism |

## Missing for Real Submit

| Requirement | Status |
|-------------|--------|
| OpenCue Docker service | ❌ Not in docker-compose.dev.yml |
| Real REST/gRPC client | ❌ Stub only |
| Frame scheduling | ❌ Not implemented |
| Worker management | ❌ Not implemented |

## Next Steps

1. Add OpenCue Docker service to dev compose
2. Implement real REST/gRPC client
3. Add frame scheduling
4. Integrate with existing render provider dispatch

## Related Documents

- `docs/review/opencue-local-submit-feasibility.md` — Feasibility assessment
- `docs/review/opencue-environment-phase1.md` — Phase 1 implementation notes
- `docs/review/opencue-architecture-validation.md` — Architecture validation
- `docs/render/capability-matrix.md` — Provider capability matrix
