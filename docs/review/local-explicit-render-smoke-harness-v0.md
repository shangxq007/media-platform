# P2L.0 — Local Explicit Render Smoke Harness

## 1. Purpose

P2L.0 introduces a local-only explicit render smoke harness that proves a platform-owned, controlled render smoke can:
1. Produce a deterministic small test video locally.
2. Validate the output with ffprobe.
3. Write a deterministic local smoke report.
4. Keep all execution behind a local smoke boundary.

## 2. Why Local Smoke Comes Before OpenCue Smoke

Local smoke validates the FFmpeg/ffprobe execution boundary without requiring any infrastructure (no PostgreSQL, no Redis, no OpenCue, no Docker, no cloud). This establishes the baseline execution model before scaling to distributed execution environments.

## 3. Current Planning Chain

```
BasicTimeline → BasicTimelineValidator → VisualCapabilityContract
  → FFmpegBaselineEffectPlanner → FFmpegBaselineTransitionPlanner
  → FFmpegLibassBasicRenderPlanner → InternalScenarioRunner
```

P2L.0 does not consume this chain for real rendering. P2L.0 proves local controlled FFmpeg/ffprobe execution boundary only.

## 4. What P2L.0 Implements

- Local render smoke domain types (id, name, status, request, result, report, issue, policy)
- Controlled FFmpeg command builder (List<String> args, never shell strings)
- Local process runner (ProcessBuilder, timeout, stdout/stderr capture)
- ffprobe output validator (dimensions, duration, codec, format)
- Local render smoke harness (orchestrates the full smoke flow)
- Unit tests (no FFmpeg required)
- Integration test (gated by system property)

## 5. What P2L.0 Does Not Implement

- Public REST controllers / API endpoints
- Database tables / Flyway migrations / repositories
- RenderJob / Product creation
- ProductRuntime / StorageRuntime calls
- RenderExecutionPlan integration
- ProviderBindingRegistry integration
- OpenCue integration
- Artifact DAG
- Remotion execution
- Incremental / partial render
- Cache reuse

## 6. Local FFmpeg/ffprobe Execution Boundary

FFmpeg and ffprobe are executed only inside the local smoke harness package. Commands are built as `List<String>` argument lists, never as shell strings. The binary is always `ffmpeg` or `ffprobe` from the fixed allowlist.

## 7. Command Safety Model

| Constraint | Enforcement |
|-----------|-------------|
| Fixed binary allowlist | `ffmpeg`, `ffprobe` only |
| No shell invocation | `sh -c`, `bash -c` rejected |
| No user-provided command | All args platform-owned |
| Arguments as List<String> | Never shell command string |
| Timeout required | Default 20 seconds |
| Output directory controlled | Under `/tmp/media-platform-local-smoke` |
| Environment sanitized | Inherited minimally |

## 8. Local Smoke Policy

Default policy: **execution disabled**. Must be explicitly enabled via:
```
-Dmedia.platform.localSmoke.enabled=true
```

Optional strict mode (fails if binaries absent):
```
-Dmedia.platform.localSmoke.strict=true
```

## 9. Test Strategy

**Unit tests** (no FFmpeg required):
- Domain type validation (id, name, status, issue codes)
- Policy defaults and allowlist
- Command builder produces List<String>, no shell invocation
- ffprobe parser handles sample output

**Integration test** (requires FFmpeg + explicit enable):
```
./gradlew :render-module:test --tests "*LocalRenderSmokeHarnessIntegrationTest" -Dmedia.platform.localSmoke.enabled=true
```

## 10. Optional Integration Smoke Command

```bash
./gradlew :render-module:test --tests "*LocalRenderSmokeHarnessIntegrationTest" -Dmedia.platform.localSmoke.enabled=true
```

## 11. Relationship to FFmpeg/libass Basic Render Plan

P2L.0 does not consume FFmpegLibassBasicRenderPlan. Future P2L.1 may bridge BasicRenderPlan to local runner.

## 12. Relationship to Scenario Runner

P2L.0 does not modify InternalScenarioRunner. Both are independent validation mechanisms.

## 13. Relationship to Provider Binding DSL

P2L.0 does not consume Provider Binding DSL. Provider binding is future work.

## 14. Relationship to Future RenderExecutionPlan

Future P2L.x may consume RenderExecutionPlan for structured local execution.

## 15. Relationship to Future Local Runner

P2L.0 establishes the local execution boundary. Future Local Runner will extend this to structured plan execution.

## 16. Relationship to Future OpenCue

Future P2O.0 may run equivalent smoke through OpenCue ExecutionEnvironment.

## 17. Relationship to ProductRuntime / StorageRuntime

P2L.0 does not call ProductRuntime or StorageRuntime. Smoke output is local-only temporary files.

## 18. Artifact DAG Boundary

P2L.0 does not reference Artifact DAG. Artifact DAG is indefinitely deferred.

## 19. Remotion Boundary

P2L.0 does not execute Remotion. Remotion remains non-executable.

## 20. Follow-up Tasks

- P2L.1 — Bridge BasicRenderPlan to local runner (implemented)
- P2L.2 — Expand supported step types for local execution (implemented)
- P2L.3 — Real media source materialization for local execution (implemented)
- P2O.0a — Local Docker OpenCue Shared-Path Smoke (complete)
- P2O.0b — OpenCue Cuebot/RQD Job Submission (future)
