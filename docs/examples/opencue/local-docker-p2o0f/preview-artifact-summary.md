# Preview Artifact Summary — P2O.0f

Date: 2026-06-30

## Preview Root

```
build/opencue-shared/media-platform-smoke/preview/p2o0f/
```

## Expected Artifacts Per Scenario

### multiframe-10/

| File | Source | Description |
|------|--------|-------------|
| frame-1.txt ... frame-10.txt | RQD frame execution | Probe output per frame |
| opencue-job-summary.txt | DB query | Job status and timing |
| frame-status.txt | DB query | Per-frame status and exit codes |
| submit-response.json | grpcurl | Submission response |

### multiframe-20-chunk5/

| File | Source | Description |
|------|--------|-------------|
| chunk-1-5.txt ... chunk-16-20.txt | RQD chunk execution | Probe output per chunk |
| opencue-job-summary.txt | DB query | Job status and timing |
| frame-status.txt | DB query | Per-frame status |

### multilayer-2/

| File | Source | Description |
|------|--------|-------------|
| preprocess-output.txt | PREPROCESS layer | Preprocess probe output |
| render-output.txt | RENDER layer | Render probe output |
| opencue-job-summary.txt | DB query | Job status and timing |
| frame-status.txt | DB query | Per-frame status |

### multilayer-3/

| File | Source | Description |
|------|--------|-------------|
| preprocess.txt | PREPROCESS layer | Preprocess probe output |
| render.txt | RENDER layer | Render probe output |
| postprocess.txt | POSTPROCESS layer | Postprocess probe output |
| opencue-job-summary.txt | DB query | Job status and timing |
| frame-status.txt | DB query | Per-frame status |

### dependency/

| File | Source | Description |
|------|--------|-------------|
| data-gen.txt | data-gen layer | Data generation output |
| process.txt | process layer | Processing output (depends on data-gen) |
| opencue-job-summary.txt | DB query | Job status and timing |
| frame-status.txt | DB query | Per-frame status |

### failure-exit1/

| File | Source | Description |
|------|--------|-------------|
| fail-marker.txt | fail-layer | Written before exit 1 |
| opencue-job-summary.txt | DB query | Job status (expected DEAD) |
| frame-status.txt | DB query | Frame status (expected DEAD, exit=1) |

### mixed-failure/

| File | Source | Description |
|------|--------|-------------|
| success.txt | success-layer | Success probe output |
| fail-marker.txt | fail-layer | Written before exit 1 |
| opencue-job-summary.txt | DB query | Job status (expected DEAD) |
| frame-status.txt | DB query | Layer-level status |

## Aggregate Files

| File | Description |
|------|-------------|
| scenario-results.txt | Pass/fail summary for all 7 scenarios |

## Artifact Collection Method

1. RQD writes output files to Docker volume mount: `/mnt/opencue-shared/media-platform-smoke/preview/p2o0f/`
2. `copy-preview-artifacts.sh` copies files from container to host via `docker exec cat`
3. DB summaries collected via `psql` through `docker exec postgres`
4. Frame status collected via `collect-frame-status.sh`
