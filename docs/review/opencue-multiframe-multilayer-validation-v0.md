# Review: OpenCue Multi-Frame, Multi-Layer, Dependency, and Failure Visibility Validation v0

## 1. Purpose

P2O.0f validates local Docker OpenCue multi-frame, multi-layer, dependency, and failure visibility behavior after P2O.0e true Cuebot gRPC submission.

## 2. Why P2O.0f Follows P2O.0e

P2O.0e achieved true Cuebot gRPC job submission via grpcurl + CJSL XML spec.
All 3 smoke levels (0/1/2) passed with single-frame, single-layer jobs.
P2O.0e did NOT validate:
- Multi-frame jobs (only 1-frame jobs)
- Multi-layer jobs (only single RENDER layer)
- Frame/layer dependencies
- Failure visibility (only success paths)

## 3. P2O.0e Baseline

- gRPC API: REACHABLE
- CJSL XML parsing: WORKS
- Job submission: WORKS (LaunchSpecAndWait)
- RQD execution: WORKS
- Frame completion: WORKS (FINISHED)
- Show/Facility/Allocation: SEEDED
- Submission method: grpcurl + proto files from host

## 4. What P2O.0f Validates

### Scenario 1: Multi-Frame Job (10 frames, chunk=1)
- 10 frames dispatched to RQD
- All 10 frames complete
- Job reaches FINISHED/SUCCEEDED
- Each frame writes output file

### Scenario 2: Multi-Frame Job (20 frames, chunk=5)
- 4 chunks dispatched (frames 1-5, 6-10, 11-15, 16-20)
- All 4 chunks complete
- Job reaches FINISHED/SUCCEEDED

### Scenario 3: Multi-Layer Job (PREPROCESS + RENDER)
- PREPROCESS layer executes first
- RENDER layer executes after PREPROCESS (dependency)
- Both layers succeed
- Job reaches FINISHED/SUCCEEDED

### Scenario 4: Multi-Layer Job (PREPROCESS + RENDER + POSTPROCESS)
- Chain execution: PREPROCESS → RENDER → POSTPROCESS
- All 3 layers succeed
- Job reaches FINISHED/SUCCEEDED

### Scenario 5: Frame Dependency (inter-layer depends)
- `<depends>` element in CJSL honored
- Dependent layer waits for dependency

### Scenario 6: Failure Visibility (intentional exit 1)
- Frame marked DEAD
- Job does NOT reach FINISHED
- Failure visible in database

### Scenario 7: Mixed Success/Failure
- Success layer: SUCCEEDED
- Failure layer: DEAD
- Job reflects overall failure

## 5. What P2O.0f Does Not Implement

- Production OpenCue adapter
- RenderExecutionPlan integration
- ProductRuntime / StorageRuntime integration
- Public API endpoints
- App runtime dependencies
- Cross-service-provider execution
- Artifact DAG
- Remotion execution

## 6. CJSL Spec Design

Each scenario has a dedicated CJSL XML spec under `docs/examples/opencue/local-docker-p2o0f/specs/`.

Key CJSL elements used:
- `<job name="" shot="" show="" user="">` — job identity
- `<layer name="" type="">` — layer identity (RENDER, UTIL)
- `<cmd><arg>...</arg></cmd>` — shell command
- `<range>N-M</range>` — frame range
- `<chunk>N</chunk>` — frames per chunk
- `<depends><layer>...</layer></depends>` — inter-layer dependency
- `<memory>N</memory>` — memory hint (MB)
- `<cores>N</cores>` — core count hint

## 7. Submission Method

grpcurl from host with proto files (carried forward from P2O.0e):

```
grpcurl -plaintext \
  -import-path /tmp/opencue-protos \
  -proto job.proto \
  -d '{"spec": "<CJSL XML>"}' \
  localhost:8443 \
  job.JobInterface.LaunchSpecAndWait
```

## 8. Validation Method

For each scenario:
1. Submit CJSL spec via grpcurl
2. Poll job status from PostgreSQL (str_state)
3. Collect frame-level status (str_state, int_exit_status per frame)
4. Verify output files exist in shared path
5. Check dependency ordering (layer start times)

## 9. Safety Model

- Operator-run testbed commands only
- Not production OpenCue adapter
- Not public API
- No raw shell command from user
- No signed URLs or filesystem paths in outputs
- OpenCue = ExecutionEnvironment (not Provider/Backend)
- No secrets in generated files

## 10. Architecture Boundaries

- OpenCue remains ExecutionEnvironment, not Provider or ExecutionBackend
- OpenCue does not own visual capability semantics
- OpenCue does not replace ProviderBindingPlan
- No Artifact DAG (ADR-025)
- No Remotion execution
- No cross-service-provider execution

## 11. Relationship to Future Tasks

- P2O.1: PVE smoke validation can use P2O.0f infrastructure
- Production OpenCue adapter: separate task (needs connection pooling, retry, monitoring)
- RenderExecutionPlan integration: separate task (can use P2O.0f submission path)

## 12. Deliverables

| Deliverable | Location |
|------------|----------|
| CJSL specs (7) | `docs/examples/opencue/local-docker-p2o0f/specs/` |
| Scripts (12) | `docs/examples/opencue/local-docker-p2o0f/scripts/` |
| Preview artifacts | `build/opencue-shared/media-platform-smoke/preview/p2o0f/` |
| Review doc | `docs/review/opencue-multiframe-multilayer-validation-v0.md` |
| Runbook | `docs/operations/opencue-multiframe-multilayer-runbook.md` |
