# P2L.1 — BasicRenderPlan-to-Local-Runner Bridge

## 1. Purpose

P2L.1 bridges the FFmpegLibassBasicRenderPlan to the existing controlled local runner/smoke boundary, proving that a platform-generated basic render plan can drive controlled local FFmpeg/ffprobe execution.

## 2. Why This Bridge Comes After P2L.0

P2L.0 established the local smoke execution boundary with:
- Fixed binary allowlist (ffmpeg, ffprobe)
- No shell invocation
- ProcessBuilder-based execution
- ffprobe validation
- Stable output root
- Execution disabled by default

P2L.1 consumes the output of P2R.3 (FFmpegLibassBasicRenderPlanner) and maps it through the P2L.0 execution boundary.

## 3. Current Planning Chain

```
TimelineSpec
  → BasicTimelineValidator
  → FFmpegLibassBasicRenderPlanner
  → FFmpegLibassBasicRenderPlan
  → [P2L.1 bridge]
  → LocalRenderExecutionRequest
  → Controlled FFmpeg execution
  → ffprobe validation
  → LocalRenderExecutionResult
```

## 4. What P2L.1 Implements

- `LocalRenderExecutionId` — typed ID for bridge executions
- `LocalRenderExecutionStatus` — extended status enum (adds UNSUPPORTED)
- `LocalRenderExecutionRequest` — request with plan reference and output profile
- `LocalRenderExecutionResult` — result with plan ID and execution details
- `LocalRenderExecutionReport` — aggregated report
- `BasicRenderPlanLocalExecutionAdapter` — maps plan to local request
- `BasicRenderPlanLocalRunner` — orchestrates bridge execution
- Extended `LocalRenderSmokeIssueCode` with P2L.1 bridge codes
- Extended `LocalFfmpegSmokeCommandBuilder` with plan-driven command building
- Unit tests (no FFmpeg required)
- Integration test (FFmpeg required, disabled by default)

## 5. What P2L.1 Does Not Implement

- Full arbitrary timeline rendering
- Real media source materialization
- Effect/transition/caption/watermark step execution
- Audio track processing
- Clip sequence assembly
- RenderExecutionPlan integration
- OpenCue integration
- ProductRuntime/StorageRuntime integration
- ProviderBindingRegistry integration
- Public API
- Artifact DAG
- Remotion execution

## 6. BasicRenderPlan to Local Execution Bridge

The bridge flow:

1. Accept `FFmpegLibassBasicRenderPlan`
2. Validate plan status (reject BLOCKED/INVALID/UNSUPPORTED)
3. Scan stages/steps for supported subset
4. Extract output profile (width, height, fps, codec, container)
5. Create `LocalRenderExecutionRequest` with synthetic testsrc input
6. Build controlled FFmpeg command via `LocalFfmpegSmokeCommandBuilder`
7. Execute via `LocalProcessRunner`
8. Validate output via `LocalFfprobeValidator`
9. Return `LocalRenderExecutionResult`

## 7. Supported BasicRenderPlan Subset

| Stage | Step | Support |
|-------|------|---------|
| VALIDATE_TIMELINE | VALIDATE_TIMELINE | Supported (handled by planner) |
| PREPARE_INPUTS | DECLARE_OUTPUT_PROFILE | Supported (extracts resolution/codec) |
| PLAN_OUTPUT_ENCODING | ENCODE_OUTPUT | Supported (maps to FFmpeg encoder) |
| PLAN_OUTPUT_VERIFICATION | VERIFY_OUTPUT | Supported (triggers ffprobe) |
| (any) | DECLARE_SAFE_METADATA | Supported (informational) |

## 8. Unsupported Stages/Steps Policy

Unsupported stages and steps are:
- Reported as WARNING issues (not errors)
- Listed in `unsupportedSteps` field of the result
- Do NOT block execution
- Allow the supported subset to proceed

This is intentionally conservative. Future P2L.x versions will expand the supported subset.

## 9. Local Command Safety Model

Reuses P2L.0 safety model:
- Fixed binary allowlist: ffmpeg, ffprobe
- No shell invocation (sh -c, bash -c forbidden)
- No user-provided command string
- Arguments built as `List<String>`
- Timeout required (default 20s)
- Working directory controlled
- Output directory controlled
- stdout/stderr captured to result
- Exit code captured

## 10. Local Execution Policy

Reuses `LocalRenderSmokePolicy`:
- `allowExecution` — disabled by default
- `timeoutSeconds` — 20s default
- `outputRoot` — configurable via system property
- `allowOverwrite` — true by default
- `allowedBinaries` — {ffmpeg, ffprobe}
- `strictMode` — when true, fails if binaries absent

## 11. Stable Output Root

When enabled with `-Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke`, output is written to:

```
/tmp/media-platform-local-smoke/local-plan-smoke-001-basic-render-plan-testsrc-h264-mp4/
  output.mp4
  local-render-execution-report.txt
```

## 12. Unit Test Strategy

Unit tests (no FFmpeg required):
- Adapter rejects blocked/unsupported/invalid plans
- Adapter rejects plans without output profile
- Adapter maps output profile to request dimensions
- Adapter reports unsupported steps as warnings
- Adapter reports synthetic input requirement
- Runner with disabled policy returns SKIPPED
- Runner with blocked plan returns BLOCKED
- Runner report contains plan ID
- Execution ID generation is unique
- Execution status enum contains all required values
- Smoke issue code enum contains all bridge codes
- Command builder maps codec correctly
- Stable output directory path is deterministic

## 13. Optional Integration Test Command

```bash
./gradlew :render-module:test \
  --tests "*BasicRenderPlanLocalRunnerIntegrationTest" \
  -Dmedia.platform.localSmoke.enabled=true \
  -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
```

## 14. Relationship to Scenario Runner

P2X.0 InternalScenarioRunner validates internal planning flow only. P2L.1 is a separate execution bridge — it takes a plan and executes it locally. They are independent validation mechanisms.

## 15. Relationship to Provider Binding DSL

P2B.0 defines the Provider Capability Binding DSL design. P2L.1 does not consume or reference the DSL. Future integration may allow the DSL to inform local execution eligibility.

## 16. Relationship to Future RenderExecutionPlan

P2L.1 does not implement RenderExecutionPlan integration. The bridge consumes FFmpegLibassBasicRenderPlan directly. Future work may bridge through RenderExecutionPlan for richer execution semantics.

## 17. Relationship to Future Local Runner

P2L.1 is the first step toward a full Local Runner. It proves that plan-driven local execution is viable. Future iterations will:
- Expand supported step types
- Support real media source materialization
- Support effects, transitions, captions, watermarks
- Support multi-clip assembly

## 18. Relationship to Future OpenCue

P2L.1 does not integrate with OpenCue. OpenCue is an ExecutionEnvironment, not a Provider. Future P2O.x work will bridge render plans to OpenCue for distributed execution.

## 19. Relationship to ProductRuntime / StorageRuntime

P2L.1 does not call ProductRuntime or StorageRuntime. Output is local-only temporary files.

## 20. Artifact DAG Boundary

P2L.1 does not reference or require Artifact DAG. Artifact DAG is indefinitely deferred.

## 21. Remotion Boundary

P2L.1 does not execute Remotion. Remotion remains non-executable.

## 22. Follow-up Tasks

- P2L.2 — Expand supported step types (effects, transitions, captions)
- P2L.3 — Real media source materialization for local execution
- P2O.0 — OpenCue PVE Testbed Smoke Harness
- Future: RenderExecutionPlan integration
- Future: Full Local Runner with arbitrary timeline rendering
