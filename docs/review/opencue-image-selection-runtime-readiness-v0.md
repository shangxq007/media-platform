# P2O.0c — OpenCue Image Selection and Runtime Readiness

## 1. Purpose

P2O.0c resolves the OpenCue image selection blocker and validates that a local Docker OpenCue runtime (PostgreSQL + Cuebot + RQD) can start, connect, and execute smoke commands.

## 2. Why Image Selection Follows P2O.0b

P2O.0b prepared the Docker Compose template with placeholder images. P2O.0c resolves the actual images and validates runtime readiness.

## 3. Current Blocker

P2O.0b status was: OpenCue Images Confirmed: NO / PLACEHOLDER_ONLY / OPERATOR_REQUIRED

## 4. What P2O.0c Implements

- Image discovery from Docker Hub (opencue organization)
- Local smoke RQD Dockerfile (extends opencue/rqd:1.19.1 with ffmpeg)
- Compose file with confirmed images and correct env vars
- Database schema initialization (V1-V30 migrations + seed data)
- Runtime startup validation
- RQD-Cuebot connectivity verification
- Shared path mount verification
- ffmpeg/ffprobe availability verification in RQD

## 5. What P2O.0c Does Not Implement

- OpenCue job submission
- Production OpenCue adapter
- RenderExecutionPlan integration
- Cross-service-provider execution
- StorageRuntime / ProductRuntime integration
- Public API
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

OpenCue = ExecutionEnvironment (not Provider, not ExecutionBackend)

## 7. Image Discovery

| Image | Source | Status |
|-------|--------|--------|
| postgres:16-alpine | Docker Hub | CONFIRMED |
| opencue/cuebot:1.19.1 | Docker Hub (opencue org) | CONFIRMED |
| opencue/rqd:1.19.1 | Docker Hub (opencue org) | CONFIRMED (no ffmpeg) |
| opencue-rqd-smoke:local | Local build | CONFIRMED |

## 8. Image Selection Decision

Use opencue/cuebot:1.19.1 and opencue/rqd:1.19.1 as base. Build local smoke RQD image with ffmpeg.

## 9. RQD ffmpeg/ffprobe Requirement

Official RQD image does NOT include ffmpeg. Built local smoke image extending opencue/rqd:1.19.1 with `apt-get install ffmpeg`.

## 10. Local Smoke Image Strategy

Dockerfile: `docs/examples/opencue/local-docker-p2o0c/rqd-smoke.Dockerfile`
- FROM opencue/rqd:1.19.1
- apt-get install ffmpeg
- Create rqd.yaml config pointing to cuebot:8443

## 11. Compose Readiness Model

Compose file: `docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml`
- PostgreSQL (postgres:16-alpine)
- Cuebot (opencue/cuebot:1.19.1) with CUEBOT_DB_URL env var
- RQD (opencue-rqd-smoke:local) with OPENCUE_RQD_CONFIG env var

## 12. Runtime Startup Validation

All three services start successfully. Cuebot connects to PostgreSQL. RQD connects to Cuebot via gRPC.

## 13. Shared Path Validation

RQD can read and write to /mnt/opencue-shared/media-platform-smoke (Docker named volume).

## 14. Worker ffmpeg/ffprobe Validation

RQD container has ffmpeg 5.1.9 and ffprobe 5.1.9 installed.

## 15. Logs and Diagnostics

Runtime logs collected to /tmp/p2o0c-runtime-logs/ (postgres.log, cuebot.log, rqd.log).

## 16. Safety Model

- Operator-run testbed commands only
- Not production, not pushed
- No raw shell command from user
- No raw FFmpeg filtergraph from user

## 17. Cross-Service-Provider Decision

P2O.0c uses single local Docker host shared path only.

## 18. Relationship to P2O.0d Job Submission

P2O.0c validates runtime readiness. P2O.0d will implement actual job submission.

## 19. Relationship to PVE

P2O.0c validates locally. PVE will use same images with different mount point.

## 20. Relationship to RenderExecutionPlan

P2O.0c does not implement RenderExecutionPlan integration.

## 21. Relationship to ProductRuntime / StorageRuntime

P2O.0c does not call ProductRuntime or StorageRuntime.

## 22. Artifact DAG Boundary

P2O.0c does not reference Artifact DAG.

## 23. Remotion Boundary

P2O.0c does not execute Remotion.

## 24. Follow-up Tasks

- P2O.0d: OpenCue job submission
- P2O.1: OpenCue ExecutionEnvironment integration
- Future: Production OpenCue adapter
