# OpenCue Local Docker Smoke Runbook

## 1. Scope

Operator-run runbook for P2O.0a local Docker OpenCue shared-path smoke validation.
Not production. Not automated. Manual testbed commands only.

## 2. Prerequisites

- Docker v20+ installed
- Docker Compose v2+ installed
- FFmpeg and ffprobe available on host (for Mode A)
- Repository cloned

## 3. Required Local Tools

| Tool | Purpose |
|------|---------|
| docker | Container runtime |
| docker compose | Multi-container orchestration |
| ffmpeg | Test media generation |
| ffprobe | Output validation |
| bash | Script execution |

## 4. Required Docker Services

| Service | Image | Purpose |
|---------|-------|---------|
| PostgreSQL | postgres:16-alpine | OpenCue database |
| Cuebot | <opencue-cuebot-image> | Job scheduler |
| RQD | <opencue-rqd-image-with-ffmpeg> | Worker with FFmpeg |

## 5. Shared Path Setup

```bash
# Create shared directory structure
bash docs/examples/opencue/local-docker-p2o0a/scripts/prepare-shared-path.sh

# Or manually:
mkdir -p build/opencue-shared/media-platform-smoke/jobs/smoke-001/{input,work,output,logs}
```

## 6. Worker Requirements

Worker container must have:
- ffmpeg binary
- ffprobe binary
- Read/write access to /mnt/opencue-shared/media-platform-smoke

## 7. Smoke Directory Layout

```
build/opencue-shared/media-platform-smoke/
  jobs/
    smoke-001/
      input/        # input fixtures
      work/         # intermediate files (ASS subtitles, etc.)
      output/       # render output (output.mp4, reports)
      logs/         # execution logs
```

## 8. Smoke Level 0 Steps — Shared Path Probe

```bash
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-0-shared-path-probe.sh
# Expected: jobs/smoke-001/logs/shared-path-probe.txt created
```

## 9. Smoke Level 1 Steps — FFmpeg Probe

```bash
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-1-ffmpeg-probe.sh
# Expected: jobs/smoke-001/output/output.mp4 + ffprobe validation
```

## 10. Smoke Level 2 Steps — Local Runner Equivalent

```bash
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-2-local-runner-equivalent.sh
# Expected: input-fixture.mp4, caption-overlay-input.ass, output.mp4, report
```

## 11. Docker Compose Usage

```bash
# Edit images first
vim docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml

# Start
docker compose -f docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml up -d

# Run smoke inside rqd container or via host scripts

# Stop
docker compose -f docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml down -v
```

## 12. Expected Outputs

| Smoke Level | Expected Output |
|-------------|----------------|
| 0 | logs/shared-path-probe.txt |
| 1 | output/output.mp4, logs/ffprobe-output.txt |
| 2 | input/input-fixture.mp4, work/caption-overlay-input.ass, output/output.mp4, output/local-render-execution-report.txt |

## 13. Troubleshooting

| Issue | Solution |
|-------|----------|
| ffmpeg not found | Install ffmpeg or use container with ffmpeg |
| Permission denied on shared path | Check mount permissions, use `chmod -R 777 build/opencue-shared` |
| Docker Compose fails | Verify image names are replaced with real OpenCue images |
| PostgreSQL connection refused | Wait for healthcheck, verify port 15432 |

## 14. Cleanup

```bash
# Remove shared path
rm -rf build/opencue-shared/media-platform-smoke

# Stop Docker
docker compose -f docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml down -v
```

## 15. What Not to Do

- Do not use in production
- Do not expose ports to public network
- Do not store secrets in compose file
- Do not run untrusted scripts
- Do not use real media assets

## 16. Transition to PVE

For PVE, change shared root from:
- Host: `build/opencue-shared/media-platform-smoke`
- To: `/mnt/opencue-shared/media-platform-smoke` (actual host path)

Same directory layout. Same scripts. Different mount point.
