# P2O.0a — Local Docker OpenCue Shared-Path Smoke

## 1. Purpose

P2O.0a validates the OpenCue shared-path execution model locally with Docker before moving to PVE. It proves that a local Docker OpenCue/Cuebot/RQD testbed can share a path between submitter and worker, execute minimal worker-side commands, run FFmpeg smoke, and write output/logs/report to the shared path.

## 2. Why Local Docker Smoke Comes Before PVE

Local Docker smoke validates the shared-path execution model without requiring physical infrastructure (no PVE VMs, no LAN shared storage, no cloud). This establishes the baseline execution environment model before scaling to distributed execution.

## 3. Current Local Runner Baseline

```
P2L.0: Controlled local FFmpeg/ffprobe smoke
P2L.1: FFmpegLibassBasicRenderPlan → BasicRenderPlanLocalExecutionAdapter → BasicRenderPlanLocalRunner
P2L.2: Local caption overlay smoke through platform-generated ASS subtitle
P2L.3: Controlled real media source materialization → input-fixture.mp4 → caption overlay → output.mp4 → ffprobe validation
```

## 4. What P2O.0a Implements

- Local Docker shared-path smoke documentation
- Shared path layout model (host + container)
- Three smoke levels (shared path probe, FFmpeg probe, local runner equivalent)
- Docker Compose example (placeholder images)
- Operator-run shell scripts for each smoke level
- Validation script
- Runbook for manual operation

## 5. What P2O.0a Does Not Implement

- Production OpenCue adapter
- Cuebot API integration
- Cross-service-provider execution
- Cross-cloud shared filesystem
- Object storage materialization
- StorageRuntime / ProductRuntime integration
- Public REST controllers / API endpoints
- Database tables / Flyway migrations
- RenderJob / Product creation
- ProviderBindingRegistry runtime integration
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

OpenCue is modeled as:
- OpenCue = ExecutionEnvironment (not Provider, not ExecutionBackend)
- OpenCue does not own visual capability semantics
- OpenCue does not replace ProviderBindingPlan
- OpenCue does not require Artifact DAG for initial smoke
- OpenCue scheduling happens after platform-owned planning

## 7. Local Docker Shared Path Model

Single-machine Docker shared-path model:

```
Local host
  ├── Docker network
  ├── PostgreSQL for OpenCue
  ├── Cuebot container
  ├── RQD worker container
  ├── submitter/test-driver (host-side scripts)
  └── shared path mounted into all relevant containers:
        host: ./build/opencue-shared/media-platform-smoke
        container: /mnt/opencue-shared/media-platform-smoke
```

## 8. Local Docker Topology

| Service | Image | Ports | Volumes |
|---------|-------|-------|---------|
| PostgreSQL | postgres:16-alpine | 15432:5432 | opencue-postgres |
| Cuebot | <opencue-cuebot-image> | 8443:8443 | media-smoke |
| RQD | <opencue-rqd-image-with-ffmpeg> | — | media-smoke |

## 9. Required Services

| Service | Required | Notes |
|---------|----------|-------|
| PostgreSQL | Yes | OpenCue database |
| Cuebot | Yes | Job scheduler |
| RQD worker | Yes | Execution worker |
| FFmpeg on worker | Yes | For smoke generation |
| ffprobe on worker | Yes | For output validation |
| CueGUI | No | Optional |
| Dedicated submitter | No | Host scripts sufficient |

## 10. Smoke Level 0 — Shared Path Probe

Proves worker container can access shared path.
Writes hostname/date/whoami/pwd to shared path.

Output: `jobs/smoke-001/logs/shared-path-probe.txt`

## 11. Smoke Level 1 — FFmpeg Probe

Proves worker container can run ffmpeg and write MP4.
Generates testsrc MP4 and validates with ffprobe.

Output: `jobs/smoke-001/output/output.mp4`, `jobs/smoke-001/logs/ffprobe-output.txt`

## 12. Smoke Level 2 — Local Runner Equivalent

Proves worker can produce output matching P2L.3 output contract.
Generates input-fixture.mp4, caption overlay, output.mp4, and report.

Output: `jobs/smoke-001/output/local-render-execution-report.txt`

## 13. Docker Compose Strategy

Example-only compose file at `docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml`.
Placeholder images — operator must replace with actual OpenCue images.

## 14. Job Submission Strategy

Layer 1: Docker shared-path dry run scripts (no Cuebot needed)
Layer 2: OpenCue submission runbook (Cuebot/RQD required)

P2O.0a implements Layer 1 only. Layer 2 deferred to P2O.0b.

## 15. Output and Log Layout

```
jobs/smoke-001/
  input/input-fixture.mp4
  work/caption-overlay-input.ass
  output/output.mp4
  output/local-render-execution-report.txt
  logs/shared-path-probe.txt
  logs/ffprobe-input.txt
  logs/ffprobe-output.txt
  logs/ffmpeg.stdout.log
  logs/ffmpeg.stderr.log
```

## 16. Safety Model

- Operator-run manual testbed commands only
- Not public APIs, not user-provided commands
- Not generated from Provider Binding DSL or templates
- No raw shell command from user
- No raw FFmpeg filtergraph from user
- No plugin-inserted execution node
- No user-submitted Render DAG

## 17. Cross-Service-Provider Decision

P2O.0a does not support cross-service-provider execution.
Uses single local Docker host shared path only.
Future cross-provider execution should use object storage + worker local materialization + StorageRuntime registration.

## 18. Relationship to BasicRenderPlan

P2O.0a does not consume BasicRenderPlan for real rendering. Future OpenCue adapter will consume RenderExecutionPlan output.

## 19. Relationship to Local Runner

P2L.3 validates local execution. P2O.0a validates shared-path execution model. Both produce compatible output contracts.

## 20. Relationship to Provider Binding DSL

P2O.0a does not consume Provider Binding DSL.

## 21. Relationship to Future RenderExecutionPlan

Future OpenCue adapter will consume RenderExecutionPlan output for distributed execution.

## 22. Relationship to Future OpenCue Adapter

P2O.0a is preparation for future OpenCue adapter. Actual Cuebot/RQD submission deferred to P2O.0b.

## 23. Relationship to Future PVE Smoke

P2O.0a validates locally. PVE smoke will use same scripts with different mount point.

## 24. Relationship to ProductRuntime / StorageRuntime

P2O.0a does not call ProductRuntime or StorageRuntime.

## 25. Artifact DAG Boundary

P2O.0a does not reference Artifact DAG. Artifact DAG is indefinitely deferred.

## 26. Remotion Boundary

P2O.0a does not execute Remotion.

## 27. Follow-up Tasks

- P2O.0b: Actual Cuebot/RQD job submission ✅
- P2O.0c: OpenCue Image Selection and Runtime Readiness ✅
- P2O.1: OpenCue ExecutionEnvironment integration
- P2O.2: OpenCue provider integration
- Future: Cross-provider execution with object storage
