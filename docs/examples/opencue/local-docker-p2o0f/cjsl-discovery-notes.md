# CJSL Discovery Notes — P2O.0f

Date: 2026-06-30

## 1. CJSL Spec Format

CJSL (Cue Job Specification Language) is XML-based.
DTD: cjsl-1.15.dtd (from OpenCue source).

## 2. Root Element

```xml
<spec>
  <job name="..." shot="..." show="..." user="...">
    ...
  </job>
</spec>
```

## 3. Job Attributes

| Attribute | Required | Description |
|-----------|----------|-------------|
| name | Yes | Unique job name |
| shot | Yes | Shot identifier |
| show | Yes | Show name (must exist in Cuebot DB) |
| user | Yes | Username (must exist or auto-created) |

## 4. Layer Element

```xml
<layer name="..." type="RENDER|UTIL|PREPROCESS|POSTPROCESS">
  <cmd><arg>...</arg></cmd>
  <range>1-10</range>
  <chunk>1</chunk>
  <memory>100</memory>
  <cores>1</cores>
</layer>
```

### Layer Attributes

| Attribute | Required | Description |
|-----------|----------|-------------|
| name | Yes | Layer name (unique within job) |
| type | Yes | Layer type: RENDER, UTIL, PREPROCESS, POSTPROCESS |

### Layer Child Elements

| Element | Required | Description |
|---------|----------|-------------|
| cmd/arg | Yes | Shell command to execute |
| range | Yes | Frame range (e.g., "1-10", "1-1") |
| chunk | Yes | Frames per chunk |
| memory | No | Memory hint in MB |
| cores | No | Core count hint |
| depends | No | Inter-layer dependencies |

## 5. Frame Range and Chunking

- `range="1-10"` with `chunk="1"` → 10 frames, each frame gets CUE_FRAME=1..10
- `range="1-20"` with `chunk="5"` → 4 chunks, each chunk gets CUE_FRAME=1,6,11,16
- CUE_FRAME env var is set by RQD to the starting frame of the chunk

## 6. Dependencies

```xml
<layer name="render" type="RENDER">
  ...
  <depends>
    <layer>preprocess</layer>
  </depends>
</layer>
```

- `<depends>` contains one or more `<layer>` elements
- Referenced layer name must exist in the same job
- Dependent layer waits for all dependencies to complete before dispatch

## 7. Environment Variables

RQD sets these for each frame:
- `CUE_FRAME` — starting frame number of the chunk
- `CUE_IFRAME` — frame number (alias)

## 8. Facility/Allocation Requirements

- Job facility must match host allocation facility for dispatch
- Default: show="testing", facility="local", allocation="local.general"
- These must be seeded in Cuebot DB before submission

## 9. Submission via grpcurl

```
grpcurl -plaintext \
  -import-path /tmp/opencue-protos \
  -proto job.proto \
  -d '{"spec": "<CJSL XML>"}' \
  localhost:8443 \
  job.JobInterface.LaunchSpecAndWait
```

- Proto: job.proto
- Service: job.JobInterface
- Method: LaunchSpecAndWait
- Request: JobLaunchSpecAndWaitRequest { string spec = 1; }
- Response: JobLaunchSpecAndWaitResponse { JobSeq jobs = 1; }

## 10. Job Lifecycle States

| State | Description |
|-------|-------------|
| PENDING | Job submitted, waiting for dispatch |
| RUNNING | At least one frame is running |
| SUCCEEDED | All frames completed successfully |
| FINISHED | Alias for SUCCEEDED in some versions |
| DEAD | At least one frame failed, job terminated |
| SETUP_FAILED | Job setup phase failed |
| CHECKPOINTED | Job checkpointed (rare in smoke) |

## 11. Frame Lifecycle States

| State | Description |
|-------|-------------|
| PENDING | Frame waiting for dispatch |
| RUNNING | Frame executing on RQD |
| SUCCEEDED | Frame completed with exit 0 |
| DEAD | Frame completed with non-zero exit |

## 12. Layer Types

| Type | Purpose |
|------|---------|
| RENDER | Primary render layer |
| UTIL | Utility/helper layer |
| PREPROCESS | Pre-processing layer |
| POSTPROCESS | Post-processing layer |

## 13. Known CJSL Limitations

- No GPU resource specification in local Docker setup
- No service-based resource limits
- No frame retry configuration in basic spec
- No job priority specification
- No environment variable injection beyond CUE_FRAME
