# Validation Outputs — P2O.0f

Date: 2026-06-30

## Environment Status

| Component | Status |
|-----------|--------|
| Docker runtime | NOT RUNNING (operator must start) |
| grpcurl | NOT INSTALLED (operator must install to /tmp/grpcurl) |
| Proto files | NOT PRESENT (operator must download to /tmp/opencue-protos/) |
| Compose config | VALID |

## Prerequisites Not Met

The following must be satisfied before running scenarios:

1. Install grpcurl to /tmp/grpcurl
2. Download OpenCue proto files to /tmp/opencue-protos/
3. Build RQD smoke image: `docker build -f docs/examples/opencue/local-docker-p2o0c/rqd-smoke.Dockerfile -t opencue-rqd-smoke:local .`
4. Start runtime: `bash docs/examples/opencue/local-docker-p2o0c/scripts/start-runtime.sh`
5. Verify health: `bash docs/examples/opencue/local-docker-p2o0c/scripts/check-runtime-health.sh`

## Expected Scenario Results

### Scenario 1: Multi-Frame 10 (chunk=1)

Expected:
- 10 frames dispatched (frame 1 through 10)
- All 10 frames reach SUCCEEDED
- Job reaches SUCCEEDED
- 10 output files: frame-1.txt through frame-10.txt
- Each file contains frame number and timestamp

DB query to verify:
```sql
SELECT str_state, count(*) FROM frame
WHERE pk_job = (SELECT pk_job FROM job WHERE str_name='p2o0f-multiframe-10')
GROUP BY str_state;
```

### Scenario 2: Multi-Frame 20 Chunk=5

Expected:
- 4 chunks dispatched (frames 1, 6, 11, 16)
- All 4 chunks reach SUCCEEDED
- Job reaches SUCCEEDED
- 4 output files: chunk-1-5.txt, chunk-6-10.txt, chunk-11-15.txt, chunk-16-20.txt

### Scenario 3: Multi-Layer 2

Expected:
- PREPROCESS layer completes first
- RENDER layer completes after PREPROCESS
- 2 output files: preprocess-output.txt, render-output.txt
- RENDER dt_started > PREPROCESS dt_finished

DB query to verify ordering:
```sql
SELECT l.str_name, f.dt_started, f.dt_finished
FROM frame f JOIN layer l ON f.pk_layer = l.pk_layer
WHERE f.pk_job = (SELECT pk_job FROM job WHERE str_name='p2o0f-multilayer-2')
ORDER BY f.dt_started;
```

### Scenario 4: Multi-Layer 3

Expected:
- Chain: PREPROCESS → RENDER → POSTPROCESS
- 3 output files: preprocess.txt, render.txt, postprocess.txt
- Each layer starts after previous completes

### Scenario 5: Dependency

Expected:
- data-gen layer completes first
- process layer completes after data-gen
- 2 output files: data-gen.txt, process.txt
- process dt_started > data-gen dt_finished

### Scenario 6: Failure Exit 1

Expected:
- Frame reaches DEAD (exit code 1)
- Job reaches DEAD
- fail-marker.txt exists (written before exit 1)
- Job does NOT reach SUCCEEDED/FINISHED

DB query to verify:
```sql
SELECT str_state FROM job WHERE str_name='p2o0f-failure-exit1';
SELECT str_state, int_exit_status FROM frame
WHERE pk_job = (SELECT pk_job FROM job WHERE str_name='p2o0f-failure-exit1');
```

### Scenario 7: Mixed Failure

Expected:
- success-layer: SUCCEEDED, output success.txt
- fail-layer: DEAD (exit 1), fail-marker.txt exists
- Job reaches DEAD (not SUCCEEDED)
- success.txt exists, fail-marker.txt exists

DB query to verify:
```sql
SELECT l.str_name, f.str_state, f.int_exit_status
FROM frame f JOIN layer l ON f.pk_layer = l.pk_layer
WHERE f.pk_job = (SELECT pk_job FROM job WHERE str_name='p2o0f-mixed-failure')
ORDER BY l.str_name;
```

## Safety Check Expected Results

| Check | Expected |
|-------|----------|
| No signed URLs | PASS |
| No host filesystem paths | PASS |
| No provider/backend hints | PASS |
| No secrets | PASS |
| OpenCue terminology correct | PASS |

## Architecture Compliance

| Requirement | Status |
|------------|--------|
| OpenCue = ExecutionEnvironment | PASS |
| Not Provider or ExecutionBackend | PASS |
| No visual capability ownership | PASS |
| No ProviderBindingPlan replacement | PASS |
| No Artifact DAG | PASS (ADR-025) |
| No Remotion | PASS |
| No cross-service-provider | PASS |
| No production adapter | PASS |
| No RenderExecutionPlan integration | PASS |
| No app runtime dependencies | PASS |
