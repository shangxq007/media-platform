# P2O.0b — Local Docker Cuebot/RQD Runtime Smoke

## 1. Purpose

P2O.0b validates the first real local Docker OpenCue runtime smoke: Cuebot + RQD worker + shared path + worker-side smoke script execution. It moves from P2O.0a dry-run scripts to actual OpenCue runtime scheduling on a single local Docker host.

## 2. Why Cuebot/RQD Runtime Smoke Follows P2O.0a

P2O.0a validated the shared-path execution model with dry-run scripts. P2O.0b validates actual OpenCue runtime scheduling: Cuebot dispatches jobs, RQD workers execute them, shared path carries input/output. This proves the runtime loop works before PVE.

## 3. Current Shared-Path Baseline

P2O.0a established:
- Host shared root: `build/opencue-shared/media-platform-smoke`
- Container shared root: `/mnt/opencue-shared/media-platform-smoke`
- Directory layout: `jobs/smoke-001/{input,work,output,logs}`
- Three smoke levels (shared path probe, FFmpeg probe, local runner equivalent)
- Operator-run dry-run scripts
- Docker Compose example with placeholder images

## 4. What P2O.0b Implements

- Runtime smoke submission scripts/templates
- Docker Compose example for Cuebot/RQD runtime (P2O.0b-specific)
- Runtime smoke runbook
- Image status documentation
- Runtime smoke validation scripts
- OpenCue log collection scripts
- Documentation updates

## 5. What P2O.0b Does Not Implement

- Production OpenCue adapter
- Cuebot API client in application runtime
- RenderExecutionPlan integration
- Cross-service-provider execution
- Object storage materialization
- StorageRuntime / ProductRuntime integration
- Public REST controllers / API endpoints
- Database tables / Flyway migrations
- RenderJob / Product creation
- ProviderBindingRegistry runtime integration
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

OpenCue remains modeled as:
- OpenCue = ExecutionEnvironment (not Provider, not ExecutionBackend)
- OpenCue does not own visual capability semantics
- OpenCue does not replace ProviderBindingPlan
- OpenCue does not require Artifact DAG for initial smoke
- OpenCue scheduling happens after platform-owned planning

## 7. Local Docker Runtime Topology

Single-machine Docker runtime model:

```
Local host
  ├── Docker network (opencue-net)
  ├── PostgreSQL container (postgres:16-alpine)
  ├── Cuebot container (operator-provided image)
  ├── RQD worker container (operator-provided image with FFmpeg)
  └── shared path mounted into Cuebot and RQD:
        host: ./build/opencue-shared/media-platform-smoke
        container: /mnt/opencue-shared/media-platform-smoke
```

## 8. Shared Path Model

| Location | Host | Container |
|----------|------|-----------|
| Root | `build/opencue-shared/media-platform-smoke` | `/mnt/opencue-shared/media-platform-smoke` |
| Input | `jobs/smoke-001/input/` | same |
| Work | `jobs/smoke-001/work/` | same |
| Output | `jobs/smoke-001/output/` | same |
| Logs | `jobs/smoke-001/logs/` | same |

## 9. Required Services

| Service | Required | Status | Notes |
|---------|----------|--------|-------|
| PostgreSQL | Yes | CONFIRMED | postgres:16-alpine |
| Cuebot | Yes | PLACEHOLDER | Operator must provide image |
| RQD worker | Yes | PLACEHOLDER | Operator must provide image with FFmpeg |
| FFmpeg on worker | Yes | PLACEHOLDER | Must be in RQD image |
| ffprobe on worker | Yes | PLACEHOLDER | Must be in RQD image |

## 10. OpenCue Image Status

**Status: PLACEHOLDER_ONLY / OPERATOR_REQUIRED**

No confirmed OpenCue Docker images found in repository. The P2O.0a compose uses `<opencue-cuebot-image>` and `<opencue-rqd-image-with-ffmpeg>` placeholders.

Operator requirements:

Cuebot image required:
- Image: AcademySoftwareFoundation/opencue or operator-built
- Required ports: 8443 (gRPC)
- Required environment: CUE_DB_HOST, CUE_DB_PORT, CUE_DB_NAME, CUE_DB_USER, CUE_DB_PASS
- PostgreSQL connection: must connect to postgres service
- Shared mount: /mnt/opencue-shared/media-platform-smoke

RQD image required:
- Image: AcademySoftwareFoundation/opencue-rqd or operator-built
- Must contain: ffmpeg binary
- Must contain: ffprobe binary
- Must mount: /mnt/opencue-shared/media-platform-smoke
- Must connect to: Cuebot via CUEBOT_HOSTNAME:8443

## 11. Runtime Smoke Level 0 — Shared Path Probe

Proves RQD worker can execute a job and write to shared path.
Worker runs smoke-level-0-shared-path-probe.sh.
Expected output: `jobs/smoke-001/logs/shared-path-probe.txt`

## 12. Runtime Smoke Level 1 — FFmpeg Probe

Proves RQD worker can run FFmpeg and ffprobe through OpenCue job execution.
Worker runs smoke-level-1-ffmpeg-probe.sh.
Expected output: `jobs/smoke-001/output/output.mp4`, `jobs/smoke-001/logs/ffprobe-output.txt`

## 13. Runtime Smoke Level 2 — Local Runner Equivalent

Proves RQD worker can execute the P2L.3-equivalent output contract through OpenCue.
Worker runs smoke-level-2-local-runner-equivalent.sh.
Expected output: input, work, output, logs matching P2L.3 contract.

## 14. Docker Compose Strategy

P2O.0b-specific compose at `docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml`.
Placeholder images — operator must replace with confirmed OpenCue images.
Not verified until images are confirmed and execution succeeds.

## 15. Job Submission Strategy

Two layers:
- Layer 1: Shell scripts that run inside RQD container (dry run, no Cuebot needed)
- Layer 2: OpenCue submission templates (Cuebot/RQD required, operator-provided commands)

If submission command is unknown, scripts fail fast with clear message and TODO.

## 16. Output and Log Layout

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
  logs/opencue-job.log
  logs/cuebot.log
  logs/rqd.log
```

## 17. Safety Model

- Operator-run testbed commands only
- Not public APIs, not user-provided commands
- Not generated from Provider Binding DSL or templates
- No raw shell command from user
- No raw FFmpeg filtergraph from user
- No plugin-inserted execution node
- No user-submitted Render DAG

## 18. Cross-Service-Provider Decision

P2O.0b does not support cross-service-provider execution.
Uses single local Docker host shared path only.
Future cross-provider execution should use object storage + worker local materialization + StorageRuntime registration.

## 19. Relationship to BasicRenderPlan

P2O.0b does not consume BasicRenderPlan for real rendering. Future OpenCue adapter will consume RenderExecutionPlan output.

## 20. Relationship to Local Runner

P2L.3 validates local execution. P2O.0b validates runtime-scheduled execution via OpenCue. Both produce compatible output contracts.

## 21. Relationship to Provider Binding DSL

P2O.0b does not consume Provider Binding DSL.

## 22. Relationship to Future RenderExecutionPlan

Future OpenCue adapter will consume RenderExecutionPlan output for distributed execution.

## 23. Relationship to Future Production OpenCue Adapter

P2O.0b is preparation for future production adapter. Actual platform integration deferred.

## 24. Relationship to Future PVE Smoke

P2O.0b validates locally. PVE smoke will use same scripts with different mount point.

## 25. Relationship to ProductRuntime / StorageRuntime

P2O.0b does not call ProductRuntime or StorageRuntime.

## 26. Artifact DAG Boundary

P2O.0b does not reference Artifact DAG. Artifact DAG is indefinitely deferred.

## 27. Remotion Boundary

P2O.0b does not execute Remotion.

## 28. Follow-up Tasks

- P2O.1: OpenCue ExecutionEnvironment integration
- P2O.2: OpenCue provider integration
- Future: Cross-provider execution with object storage
- Future: Production OpenCue adapter
