# P2O.0b — Local Docker Cuebot/RQD Runtime Smoke

## Purpose

Local Docker runtime smoke for OpenCue Cuebot/RQD job execution.
Validates that Cuebot can submit jobs to RQD workers via shared path.

**This is not production. This is not a public API. This is operator-run testbed only.**

## Mode A: Host-Side Dry Run (No Cuebot Required)

Runs P2O.0a scripts directly on host. No Docker Compose needed.

```bash
cd <repo-root>

# 1. Prepare shared path
bash docs/examples/opencue/local-docker-p2o0b/scripts/prepare-runtime-smoke.sh

# 2. Run P2O.0a smoke scripts (host-side, no OpenCue)
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-0-shared-path-probe.sh
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-1-ffmpeg-probe.sh
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-2-local-runner-equivalent.sh

# 3. Validate
bash docs/examples/opencue/local-docker-p2o0b/scripts/validate-runtime-smoke-output.sh
```

## Mode B: Docker Cuebot/RQD Runtime (Requires OpenCue Images)

Requires operator-provided OpenCue Docker images.

```bash
# 1. Edit docker-compose.opencue-runtime-smoke.yml — replace placeholder images
# 2. Start services
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml up -d

# 3. Verify services are running
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml ps

# 4. Submit smoke jobs (inside RQD container or via Cuebot submission)
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-0.sh
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-1.sh
bash docs/examples/opencue/local-docker-p2o0b/scripts/submit-smoke-level-2.sh

# 5. Validate outputs
bash docs/examples/opencue/local-docker-p2o0b/scripts/validate-runtime-smoke-output.sh

# 6. Collect logs
bash docs/examples/opencue/local-docker-p2o0b/scripts/collect-opencue-logs.sh

# 7. Inspect output on host
ls -la build/opencue-shared/media-platform-smoke/jobs/smoke-001/output/

# 8. Cleanup
docker compose -f docs/examples/opencue/local-docker-p2o0b/docker-compose.opencue-runtime-smoke.yml down -v
```

## Shared Path Model

| Location | Host | Container |
|----------|------|-----------|
| Root | `build/opencue-shared/media-platform-smoke` | `/mnt/opencue-shared/media-platform-smoke` |
| Input | `jobs/smoke-001/input/` | same |
| Work | `jobs/smoke-001/work/` | same |
| Output | `jobs/smoke-001/output/` | same |
| Logs | `jobs/smoke-001/logs/` | same |

## Image Requirements

| Image | Required | Status |
|-------|----------|--------|
| postgres:16-alpine | Yes | Confirmed |
| OpenCue Cuebot | Yes | Operator must provide |
| OpenCue RQD + FFmpeg | Yes | Operator must provide |

## Safety

- Not a public API
- Not production runtime
- Operator-run manual testbed commands only
- No user-provided commands or filtergraphs
- No StorageRuntime/ProductRuntime integration
- No cross-service-provider execution

## Related Docs

- `docs/review/local-docker-cuebot-rqd-runtime-smoke-v0.md`
- `docs/operations/opencue-local-docker-runtime-smoke-runbook.md`
- `docs/examples/opencue/local-docker-p2o0a/` (P2O.0a baseline)
