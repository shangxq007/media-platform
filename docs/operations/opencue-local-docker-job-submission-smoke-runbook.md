# OpenCue Local Docker Job Submission Smoke Runbook

## 1. Scope

Operator-run runbook for P2L.0d local Docker OpenCue job submission smoke validation.

## 2. Prerequisites

- Docker and Docker Compose installed
- P2O.0c images built (opencue-rqd-smoke:local)
- Repository cloned

## 3. Start Local OpenCue Runtime

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh
docker compose -f docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml \
  -f docs/examples/opencue/local-docker-p2o0d/docker-compose.override.if-needed.yml up -d
```

## 4. Confirm Cuebot/RQD Readiness

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-shared-path.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-ffmpeg.sh
```

## 5. Prepare Job Submission Smoke

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/prepare-job-submission-smoke.sh
```

## 6. Submit Smoke Level 0

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-0.sh
```

## 7. Submit Smoke Level 1

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-1.sh
```

## 8. Submit Smoke Level 2

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-2.sh
```

## 9. Wait for Jobs

Container exec is synchronous. No wait needed.

## 10. Validate Shared-Path Outputs

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/validate-job-submission-output.sh
```

## 11. Copy Preview Artifacts

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/copy-preview-artifacts.sh
```

## 12. Preview Output Locally

```bash
# View smoke level 2 output
ffprobe build/opencue-shared/media-platform-smoke/preview/p2o0d/smoke-level-2/output.mp4

# Read report
cat build/opencue-shared/media-platform-smoke/preview/p2o0d/smoke-level-2/local-render-execution-report.txt
```

## 13. Collect Logs

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/collect-job-logs.sh
```

## 14. Troubleshooting

- Container not running: `docker compose -f ... ps`
- Shared path not accessible: check bind mount in override file
- FFmpeg not found: rebuild opencue-rqd-smoke:local image

## 15. Cleanup

```bash
bash docs/examples/opencue/local-docker-p2o0d/scripts/cleanup-job-submission-smoke.sh
docker compose -f docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml \
  -f docs/examples/opencue/local-docker-p2o0d/docker-compose.override.if-needed.yml down -v
```

## 16. What Not to Do

- Do not use in production
- Do not submit jobs via raw shell commands
- Do not expose FFmpeg filtergraphs
- Do not call StorageRuntime/ProductRuntime

## 17. Transition to PVE

For PVE, replace bind mount path in compose override with PVE shared path.
