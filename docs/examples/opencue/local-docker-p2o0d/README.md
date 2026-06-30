# P2O.0d — Local Docker OpenCue Job Submission Smoke

## Purpose

Validates actual local Docker OpenCue job submission and RQD execution.
Smoke outputs are saved under `build/opencue-shared/media-platform-smoke/preview/p2o0d/` for local preview.

## Status

**PARTIAL_JOB_SUBMISSION_READY**

No true OpenCue CLI (cueadmin/cuesubmit/cuecmd) or Python client available in containers.
Fallback: container exec via `docker exec opencue-rqd` to run smoke scripts on RQD worker.

## Quick Start

```bash
# 1. Start runtime (if not running)
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh

# 2. Prepare job submission smoke
bash docs/examples/opencue/local-docker-p2o0d/scripts/prepare-job-submission-smoke.sh

# 3. Submit smoke level 0
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-0.sh

# 4. Submit smoke level 1
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-1.sh

# 5. Submit smoke level 2
bash docs/examples/opencue/local-docker-p2o0d/scripts/submit-smoke-level-2.sh

# 6. Validate outputs
bash docs/examples/opencue/local-docker-p2o0d/scripts/validate-job-submission-output.sh

# 7. Copy preview artifacts
bash docs/examples/opencue/local-docker-p2o0d/scripts/copy-preview-artifacts.sh

# 8. Collect logs
bash docs/examples/opencue/local-docker-p2o0d/scripts/collect-job-logs.sh
```

## Preview Artifacts

After running, preview at:
```
build/opencue-shared/media-platform-smoke/preview/p2o0d/smoke-level-2/output.mp4
```

## Safety

- Operator-run testbed commands only
- Not production OpenCue adapter
- Not public API
- No raw shell command from user
- No raw FFmpeg filtergraph from user
