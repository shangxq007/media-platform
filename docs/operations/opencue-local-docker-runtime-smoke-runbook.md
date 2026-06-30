# OpenCue Local Docker Runtime Smoke Runbook

## 1. Scope

Operator-run runbook for P2O.0b/P2O.0c local Docker Cuebot/RQD runtime smoke validation.
Not production. Not automated. Manual testbed commands only.

## 2. Prerequisites

- Docker v20+ installed
- Docker Compose v2+ installed
- FFmpeg and ffprobe available on host (for dry run fallback)
- Repository cloned
- OpenCue Docker images confirmed (P2O.0c confirmed: opencue/cuebot:1.19.1, opencue/rqd:1.19.1)

## 3. Required Local Tools

| Tool | Purpose |
|------|---------|
| docker | Container runtime |
| docker compose | Multi-container orchestration |
| ffmpeg | Test media generation (host dry run) |
| ffprobe | Output validation (host dry run) |
| bash | Script execution |

## 4. Required Docker Services

| Service | Image | Purpose |
|---------|-------|---------|
| PostgreSQL | postgres:16-alpine | OpenCue database |
| Cuebot | <opencue-cuebot-image> | Job scheduler |
| RQD | <opencue-rqd-image-with-ffmpeg> | Worker with FFmpeg |

## 5. Required OpenCue Images

**Status: OPERATOR_REQUIRED**

Operator must provide:
- Cuebot image: AcademySoftwareFoundation/opencue or custom build
- RQD image: AcademySoftwareFoundation/opencue-rqd with FFmpeg/ffprobe

## 6. Shared Path Setup

```bash
# Create shared directory structure
bash docs/examples/opencue/local-docker-p2o0b/scripts/prepare-runtime-smoke.sh

# Or manually:
mkdir -p build/opencue-shared/media-platform-smoke/jobs/smoke-001/{input,work,output,logs}
```

## 7. Worker Requirements

Worker container must have:
- ffmpeg binary
- ffprobe binary
- Read/write access to /mnt/opencue-shared/media-platform-smoke

## 8. Smoke Directory Layout

```
build/opencue-shared/media-platform-smoke/
  jobs/
    smoke-001/
      input/        # input fixtures
      work/         # intermediate files
      output/       # render output
      logs/         # execution logs
```

## 9. Start Local Runtime

```bash
# Edit compose file with confirmed images first
vim docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml

# Validate compose config
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml config

# Start services
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml up -d

# Verify services
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml ps
```

## 10. Submit Smoke Level 0

```bash
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-0.sh
# Expected: jobs/smoke-001/logs/shared-path-probe.txt
```

## 11. Submit Smoke Level 1

```bash
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-1.sh
# Expected: jobs/smoke-001/output/output.mp4 + logs/ffprobe-output.txt
```

## 12. Submit Smoke Level 2

```bash
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-2.sh
# Expected: input, work, output, logs matching P2L.3 contract
```

## 13. Validate Outputs

```bash
bash docs/examples/opencue/local-docker-p2o0b/scripts/validate-runtime-smoke-output.sh
```

## 14. Collect Logs

```bash
bash docs/examples/opencue/local-docker-p2o0b/scripts/collect-opencue-logs.sh
```

## 15. Troubleshooting

| Issue | Solution |
|-------|----------|
| Images not found | Verify image names in compose file |
| Cuebot won't start | Check PostgreSQL health, verify DB connection |
| RQD won't connect | Verify CUEBOT_HOSTNAME and port |
| Job not executing | Check RQD logs, verify worker registration |
| FFmpeg not found | Verify RQD image contains FFmpeg |
| Permission denied | Check mount permissions |

## 16. Cleanup

```bash
# Stop Docker
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml down -v

# Remove shared path (optional)
rm -rf build/opencue-shared/media-platform-smoke
```

## 17. What Not to Do

- Do not use in production
- Do not expose ports to public network
- Do not store secrets in compose file
- Do not run untrusted scripts
- Do not use real media assets

## 18. Transition to PVE

For PVE, change shared root from:
- Host: `build/opencue-shared/media-platform-smoke`
- To: `/mnt/opencue-shared/media-platform-smoke` (actual host path)

Same directory layout. Same scripts. Different mount point.
