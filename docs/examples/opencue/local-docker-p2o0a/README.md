# P2O.0a — Local Docker OpenCue Shared-Path Smoke

## Purpose

Local Docker smoke preparation for OpenCue shared-path execution model.
Validates worker container can access shared path, run FFmpeg, and produce output.

**This is not production. This is not a public API. This is operator-run testbed only.**

## Mode A: Local Shared-Path Dry Run (No Cuebot Required)

Runs directly on host with FFmpeg/ffprobe. No Docker Compose needed.

```bash
cd <repo-root>

# 1. Prepare shared path
bash docs/examples/opencue/local-docker-p2o0a/scripts/prepare-shared-path.sh

# 2. Smoke Level 0 — shared path probe
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-0-shared-path-probe.sh

# 3. Smoke Level 1 — FFmpeg probe
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-1-ffmpeg-probe.sh

# 4. Smoke Level 2 — local runner equivalent
bash docs/examples/opencue/local-docker-p2o0a/scripts/smoke-level-2-local-runner-equivalent.sh

# 5. Validate
bash docs/examples/opencue/local-docker-p2o0a/scripts/validate-smoke-output.sh
```

## Mode B: Local Docker OpenCue Smoke (Requires OpenCue Images)

Requires operator-provided OpenCue Docker images.

```bash
# 1. Edit docker-compose.opencue-smoke.yml — replace placeholder images
# 2. Start services
docker compose -f docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml up -d

# 3. Run smoke scripts inside rqd container (or via Cuebot job submission)
# 4. Inspect output on host: build/opencue-shared/media-platform-smoke/

# 5. Cleanup
docker compose -f docs/examples/opencue/local-docker-p2o0a/docker-compose.opencue-smoke.yml down -v
```

## Shared Path Model

| Location | Host | Container |
|----------|------|-----------|
| Root | `build/opencue-shared/media-platform-smoke` | `/mnt/opencue-shared/media-platform-smoke` |
| Input | `jobs/smoke-001/input/` | same |
| Work | `jobs/smoke-001/work/` | same |
| Output | `jobs/smoke-001/output/` | same |
| Logs | `jobs/smoke-001/logs/` | same |

## Safety

- Not a public API
- Not production runtime
- Operator-run manual testbed commands only
- No user-provided commands or filtergraphs
- No StorageRuntime/ProductRuntime integration
- No cross-service-provider execution

## Related Docs

- `docs/review/local-docker-opencue-shared-path-smoke-v0.md`
- `docs/operations/opencue-local-docker-smoke-runbook.md`
