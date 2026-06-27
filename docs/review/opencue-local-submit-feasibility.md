# OpenCue Local Submit Feasibility Assessment

> **Status:** feasibility-notes-only
> **Created:** 2026-06-28
> **Scope:** assessment of OpenCue readiness for local submit smoke

## Summary

OpenCue infrastructure is **not ready** for a local submit smoke test. The existing implementation is a Phase 1 stub with no real client, no Docker Compose service, and no frame scheduling.

## Existing Infrastructure

### OpenCueExecutionEnvironment

- File: `render-module/src/main/java/.../infrastructure/environment/OpenCueExecutionEnvironment.java`
- Status: **Stub implementation**
- `@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true", matchIfMissing = false)`
- **Disabled by default** (`opencue.enabled=false`)
- Submit/cancel/status are stub implementations
- No real REST/gRPC client
- No frame scheduling, no workers

### OpenCueProperties

- File: `render-module/src/main/java/.../domain/environment/OpenCueProperties.java`
- Configuration prefix: `opencue`
- Safe defaults: `enabled=false`, `stubModeEnabled=true`, `productionSubmitEnabled=false`
- `allowNetworkSubmit=false` by default

### OpenCueJobSpecValidator

- File: `render-module/src/main/java/.../domain/environment/OpenCueJobSpecValidator.java`
- Validates job specs (layer count, command count, environment variables, tags)
- Pure validation logic, no external dependencies

### OpenCueEnvironmentCompiler

- File: `render-module/src/main/java/.../infrastructure/environment/OpenCueEnvironmentCompiler.java`
- Compiles execution specs to OpenCue job specs
- No external dependencies

### Tests

- `OpenCuePropertiesTest` — validates property defaults
- `OpenCueJobSpecValidatorTest` — validates job spec validation
- `OpenCueEnvironmentTest` — validates environment compiler

### Documentation

- `docs/review/opencue-environment-phase1.md` — Phase 1 implementation notes
- `docs/review/opencue-architecture-validation.md` — Architecture validation

## Missing for Local Submit Smoke

| Requirement | Status |
|-------------|--------|
| OpenCue Docker service | ❌ Not in docker-compose.dev.yml |
| OpenCue REST/gRPC client | ❌ Stub only |
| Real submit/cancel/status | ❌ Stub only |
| Frame scheduling | ❌ Not implemented |
| Worker management | ❌ Not implemented |
| Local OpenCue server | ❌ Not available |

## Feasibility Conclusion

**Not feasible** for local submit smoke in this task. Reasons:

1. No OpenCue Docker service in dev compose
2. No real client implementation (stub only)
3. No frame scheduling infrastructure
4. OpenCue is an ExecutionEnvironment, not a RenderProvider — it would dispatch to render providers, not replace them
5. Adding a real OpenCue integration would require significant new infrastructure

## Recommendation

OpenCue integration should be deferred to a dedicated task that:
1. Adds OpenCue Docker service to dev compose
2. Implements real REST/gRPC client
3. Adds frame scheduling
4. Integrates with existing render provider dispatch

## Architecture Clarification

OpenCue is an **ExecutionEnvironment**, not a RenderProvider. It would:
- Receive execution jobs from the platform
- Dispatch render tasks to workers
- Report status back to the platform

It does NOT replace FFmpeg, Remotion, or other RenderProviders. Those providers would run ON OpenCue workers.
