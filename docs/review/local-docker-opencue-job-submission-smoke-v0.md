# P2L.0d — Local Docker OpenCue Job Submission Smoke

## 1. Purpose

P2L.0d validates actual local Docker OpenCue job submission and RQD execution after P2O.0c runtime readiness.

## 2. Why Job Submission Follows P2O.0c

P2O.0c resolved runtime readiness: PostgreSQL, Cuebot, RQD start; RQD registers with Cuebot; shared path accessible; ffmpeg/ffprobe available. P2L.0d proves actual job execution on the RQD worker.

## 3. Current Runtime-Ready Baseline

P2O.0c confirmed:
- PostgreSQL starts and is healthy
- Cuebot starts and connects to PostgreSQL
- RQD starts and registers with Cuebot via gRPC
- RQD can access shared path
- RQD has ffmpeg and ffprobe
- Docker compose repeatable start/stop

## 4. What P2L.0d Implements

- Job submission discovery notes
- Smoke scripts for level 0, 1, 2 via container exec (fallback)
- Preview artifact copy scripts
- Validation scripts
- Runbook and review doc
- Documentation updates

## 5. What P2L.0d Does Not Implement

- True OpenCue job submission (no CLI/Python client available)
- Production OpenCue adapter
- RenderExecutionPlan integration
- Cross-service-provider execution
- StorageRuntime / ProductRuntime integration
- Public API
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

OpenCue = ExecutionEnvironment, not Provider, not ExecutionBackend. OpenCue does not own visual capability semantics. OpenCue does not replace ProviderBindingPlan.

## 7. Job Submission Discovery

No true OpenCue submission mechanism available in containers:
- cueadmin: NOT FOUND
- cuesubmit: NOT FOUND
- cuecmd: NOT FOUND
- Python OpenCue client: NOT on PyPI, not in containers
- gRPC CLI: NOT installed

## 8. Job Submission Strategy

**PARTIAL_JOB_SUBMISSION_READY** — Container exec fallback via `docker exec opencue-rqd`.

This is NOT true OpenCue job submission. It validates:
- RQD container has ffmpeg/ffprobe
- RQD container can access shared path
- Smoke scripts execute successfully on worker

Does NOT validate:
- Cuebot job scheduling
- RQD job dispatch via Cuebot
- OpenCue job lifecycle

## 9. Runtime Smoke Level 0

Purpose: Prove RQD can write to shared path.
Worker-side: shared-path-probe.sh
Output: shared-path-probe.txt
Status: PASS

## 10. Runtime Smoke Level 1

Purpose: Prove RQD can execute FFmpeg.
Worker-side: ffmpeg probe
Output: output.mp4, ffprobe-output.txt
Status: PASS

## 11. Runtime Smoke Level 2

Purpose: Prove RQD can produce real media + caption overlay.
Worker-side: real media fixture + ASS subtitle render
Output: input-fixture.mp4, caption-overlay-input.ass, output.mp4, report
Status: PASS

## 12. Local Preview Artifact Model

All preview artifacts saved under:
```
build/opencue-shared/media-platform-smoke/preview/p2o0d/
```

## 13. Output and Log Layout

```
preview/p2o0d/
  smoke-level-0/
    shared-path-probe.txt
    opencue-job-summary.txt
    worker-log.txt
  smoke-level-1/
    output.mp4
    ffprobe-output.txt
    ffmpeg.stdout.log
    ffmpeg.stderr.log
    opencue-job-summary.txt
    worker-log.txt
  smoke-level-2/
    input-fixture.mp4
    caption-overlay-input.ass
    output.mp4
    local-render-execution-report.txt
    ffprobe-input.txt
    ffprobe-output.txt
    ffmpeg.stdout.log
    ffmpeg.stderr.log
    opencue-job-summary.txt
    worker-log.txt
```

## 14. Safety Model

- Operator-run testbed commands only
- Not production OpenCue adapter
- Not public API
- No raw shell command from user
- No raw FFmpeg filtergraph from user

## 15. Cross-Service-Provider Decision

P2L.0d does not support cross-service-provider execution. Uses single local Docker host shared path only.

## 16. Relationship to P2O.1 PVE Smoke

P2L.0d validates locally. PVE smoke will use same scripts with different mount point.

## 17. Relationship to RenderExecutionPlan

P2L.0d does not implement RenderExecutionPlan integration.

## 18. Relationship to Future Production OpenCue Adapter

P2L.0d is preparation for future production adapter.

## 19. Relationship to ProductRuntime / StorageRuntime

P2L.0d does not call ProductRuntime or StorageRuntime.

## 20. Artifact DAG Boundary

P2L.0d does not reference Artifact DAG. Artifact DAG is indefinitely deferred.

## 21. Remotion Boundary

P2L.0d does not execute Remotion.

## 22. Follow-up Tasks

- P2O.1: OpenCue ExecutionEnvironment integration
- P2O.2: OpenCue provider integration
- Future: True OpenCue job submission via Python client
- Future: Cross-provider execution with object storage
