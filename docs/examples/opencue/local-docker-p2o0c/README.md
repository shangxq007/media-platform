# P2O.0c — OpenCue Image Selection and Runtime Readiness

Example directory for P2O.0c local Docker OpenCue runtime readiness validation.

## Status

- PostgreSQL: CONFIRMED (postgres:16-alpine)
- Cuebot: CONFIRMED (opencue/cuebot:1.19.1)
- RQD: CONFIRMED base (opencue/rqd:1.19.1), NO ffmpeg
- RQD smoke image: LOCAL BUILD (extends opencue/rqd:1.19.1 with ffmpeg)

## Quick Start

```bash
# 1. Build local RQD smoke image
docker build -f docs/examples/opencue/local-docker-p2o0c/rqd-smoke.Dockerfile \
  -t opencue-rqd-smoke:local .

# 2. Prepare shared path
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh

# 3. Validate compose config
bash docs/examples/opencue/local-docker-p2o0c/scripts/validate-compose-config.sh

# 4. Start runtime
bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh

# 5. Check health
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh

# 6. Check RQD shared path
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-shared-path.sh

# 7. Check RQD ffmpeg
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-rqd-ffmpeg.sh

# 8. Collect logs
bash docs/examples/opencue/local-docker-p2o0c/scripts/collect-runtime-logs.sh

# 9. Stop
bash docs/examples/opencue/local-docker-p2o0c/scripts/stop-runtime.sh
```

## Safety

- Example-only, operator-run, not production
- Not pushed to any registry
- No secrets in compose file
- No user input command execution
- No raw FFmpeg filtergraph

## Related Docs

- Review: `docs/review/opencue-image-selection-runtime-readiness-v0.md`
- Runbook: `docs/operations/opencue-local-docker-runtime-readiness-runbook.md`
