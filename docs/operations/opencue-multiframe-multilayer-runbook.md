# Runbook: OpenCue Multi-Frame, Multi-Layer, Dependency, and Failure Visibility Validation

## 1. Scope

Operator-run validation of OpenCue multi-frame, multi-layer, dependency, and failure visibility.
Not production. Not automated. Not pushed.

## 2. Prerequisites

- Docker and Docker Compose
- grpcurl binary at /tmp/grpcurl
- Proto files at /tmp/opencue-protos/
- P2O.0c runtime ready
- P2O.0e submission validated

## 3. Start Local OpenCue Runtime

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/prepare-runtime-ready.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh
bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh
```

## 4. Verify Prerequisites

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/prepare-p2o0f.sh
```

## 5. Run All Scenarios

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/validate-all-scenarios.sh
```

## 6. Run Individual Scenarios

### Scenario 1: Multi-Frame 10

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-multiframe-10.sh
```

### Scenario 2: Multi-Frame 20 Chunk=5

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-multiframe-20-chunk5.sh
```

### Scenario 3: Multi-Layer 2

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-multilayer-2.sh
```

### Scenario 4: Multi-Layer 3

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-multilayer-3.sh
```

### Scenario 5: Dependency

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-dependency.sh
```

### Scenario 6: Failure Exit 1

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-failure-exit1.sh
```

### Scenario 7: Mixed Failure

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/submit-mixed-failure.sh
```

## 7. Collect Frame Status

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/collect-frame-status.sh <job_name> <output_file>
```

## 8. Copy Preview Artifacts

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/copy-preview-artifacts.sh
```

## 9. Safety Check

```bash
bash docs/examples/opencue/local-docker-p2o0f/scripts/safety-check.sh
```

## 10. Preview Artifact Layout

```
build/opencue-shared/media-platform-smoke/preview/p2o0f/
  scenario-results.txt
  multiframe-10/
    frame-1.txt ... frame-10.txt
    opencue-job-summary.txt
    frame-status.txt
    submit-response.json
  multiframe-20-chunk5/
    chunk-1-5.txt ... chunk-16-20.txt
    opencue-job-summary.txt
    frame-status.txt
  multilayer-2/
    preprocess-output.txt
    render-output.txt
    opencue-job-summary.txt
    frame-status.txt
  multilayer-3/
    preprocess.txt
    render.txt
    postprocess.txt
    opencue-job-summary.txt
    frame-status.txt
  dependency/
    data-gen.txt
    process.txt
    opencue-job-summary.txt
    frame-status.txt
  failure-exit1/
    fail-marker.txt
    opencue-job-summary.txt
    frame-status.txt
  mixed-failure/
    success.txt
    fail-marker.txt
    opencue-job-summary.txt
    frame-status.txt
```

## 11. Troubleshooting

### Job Not Dispatching

Check job facility matches host allocation:
```sql
SELECT j.str_name, f.str_name as facility
FROM job j JOIN facility f ON j.pk_facility = f.pk_facility;
```

### Frame DEAD

Check Cuebot logs:
```bash
docker compose -f docs/examples/opencue/local-docker-p2o0c/docker-compose.opencue-runtime-ready.yml logs cuebot | grep -i error
```

### Dependency Not Honored

Check layer start times in frame status:
```sql
SELECT l.str_name, f.int_dispatch_order, f.dt_started, f.dt_finished
FROM frame f JOIN layer l ON f.pk_layer = l.pk_layer
WHERE f.pk_job = (SELECT pk_job FROM job WHERE str_name='p2o0f-multilayer-2' LIMIT 1)
ORDER BY f.dt_started;
```

## 12. Cleanup

```bash
bash docs/examples/opencue/local-docker-p2o0c/scripts/stop-runtime.sh
```

## 13. What Not to Do

- Do not push Docker images
- Do not install packages on host
- Do not use production secrets
- Do not modify .env files
- Do not commit generated code
- Do not implement production adapter
- Do not add app runtime dependencies
