# Review: RenderExecutionPlan-to-CJSL Mapping v0

## 1. Purpose

P2O.0g designs the mapping from platform `RenderExecutionPlan` concepts to OpenCue CJSL job/layer/frame structures after P2O.0f validated multi-frame, chunking, multi-layer, and failure visibility behavior.

## 2. Why Mapping Follows P2O.0f

P2O.0f validated:
- CJSL XML with DTD works
- Multi-frame (10, 20 frames) works
- Chunking works
- Multi-layer (2, 3 layers) works
- Failure visibility works (DEAD frames, exit codes)
- CUE_FRAME env var available

P2O.0f also discovered:
- CJSL dependency syntax blocked by DTD
- Facility must match RQD facility
- Job name transformed by Cuebot

P2O.0g converts these findings into a platform-side mapping design.

## 3. Current OpenCue Validation Baseline

| Capability | Status |
|-----------|--------|
| Cuebot gRPC submission | PASS |
| CJSL XML format | DISCOVERED |
| Multi-frame jobs | PASS |
| Chunking | PASS |
| Multi-layer jobs | PASS |
| Failure visibility | PASS |
| Dependency syntax | BLOCKED / NOT_SUPPORTED_BY_CURRENT_CJSL_DTD |

## 4. What P2O.0g Implements

- RenderExecutionPlan to OpenCue job/layer/frame mapping design
- CJSL generation boundary definition
- Multi-frame mapping rules
- Chunking policy
- Multi-layer mapping rules
- Dependency handling without CJSL depends (platform-side stage splitting)
- Failure visibility mapping
- Retry/relaunch boundary
- Storage strategy: shared path vs object storage
- Safe command construction rules
- Facility/allocation selection rules
- Job naming and traceability rules
- Preview artifact and output registration boundary
- Future production adapter boundary

## 5. What P2O.0g Does Not Implement

- Production OpenCue adapter
- Live Cuebot submission
- StorageRuntime integration
- ProductRuntime integration
- Public API endpoints
- Cross-service-provider execution
- Artifact DAG
- Remotion execution

## 6. OpenCue as ExecutionEnvironment

```
OpenCue = ExecutionEnvironment
OpenCue != Provider
OpenCue != ExecutionBackend
OpenCue does not own visual capability semantics
OpenCue does not replace ProviderBindingPlan
OpenCue does not require Artifact DAG
```

OpenCue receives execution jobs from the platform, dispatches render tasks to workers, and reports status back. Providers (FFmpeg, etc.) run ON OpenCue workers.

## 7. RenderExecutionPlan to Job Mapping

### One Plan → One or More Jobs

A single `RenderExecutionPlan` may map to one or more OpenCue jobs:
- If dependencies cannot be expressed in CJSL → split into multiple jobs/stages
- If all steps are independent → single job with multiple layers

### Job Identity

Each OpenCue job has:
- `facility` — must match RQD facility
- `show` — show name (must exist in Cuebot DB)
- `shot` — shot identifier
- `user` — username
- `jobName` — unique, traceable to plan

### Mapping Rule

```
RenderExecutionPlan.planId → job name prefix
RenderExecutionStep.nodeId → layer name suffix
ExecutionEnvironmentTarget → facility selection
```

## 8. Logical Node to Layer Mapping

A homogeneous repeated operation maps to a layer:

| Layer Property | Source |
|---------------|--------|
| name | Step nodeId + step type |
| type | Always "Render" for execution layers |
| command | From ProviderExecutionDocumentDraft |
| range | Frame range from step metadata |
| chunk | Chunk size from step metadata |
| cores | Resource hint from step metadata |
| memory | Resource hint from step metadata |

## 9. Repeated Work to Frame Mapping

Repeated independent work maps to frames:
- `CUE_FRAME` is used for frame identity
- Frame output paths must include frame id
- Chunk size determines frames per dispatch

## 10. Chunking Policy

| Rule | Value |
|------|-------|
| Default chunk size | 1 |
| Chunk allowed | Only when frames are independent |
| Larger chunks | Require explicit bounded policy |
| CUE_FRAME | Starting frame of chunk |

## 11. Multi-Layer Policy

Multiple layers within a single job:
- Each layer has unique name
- Layers execute in parallel unless dependencies exist
- Cuebot handles intra-job layer ordering

## 12. Dependency Handling Without CJSL Depends

Because current CJSL dependency syntax is blocked by DTD, platform-side orchestration manages dependencies:

```
Stage A (Job A):
  Submit → Wait → Validate outputs

Stage B (Job B):
  Submit → Wait → Validate outputs

Stage C (Job C):
  Submit → Wait → Validate outputs
```

### Stage Splitting Rules

1. Topological sort of steps by dependency
2. Steps with no inter-dependencies can be in same stage
3. Each stage maps to one OpenCue job
4. Platform validates outputs between stages

## 13. Failure Visibility Mapping

| OpenCue State | Platform Status | Action |
|---------------|-----------------|--------|
| SUCCEEDED/FINISHED | COMPLETED | Register outputs |
| DEAD | FAILED | Collect failure details |
| RUNNING | RUNNING | Continue polling |
| PENDING | SUBMITTED | Continue polling |

### Partial Success

When a job has both SUCCEEDED and DEAD frames:
- Register successful outputs
- Report failures with exit codes
- Preserve successful output metadata

## 14. Retry/Relaunch Boundary

### Current (P2O.0g)

- No retry mechanism implemented
- Failed frames remain DEAD
- Platform decides: retry whole job or accept partial

### Future Work

- Frame-level retry via Cuebot API
- Job-level retry with modified frame range
- Platform-side retry policy configuration

## 15. Facility/Allocation Selection

| Environment | Facility | Allocation |
|------------|----------|------------|
| Local Docker | local | local.general |
| PVE | local | local.general |
| Cloud | cloud | cloud.general |

Rule: Job facility must match RQD host allocation facility.

## 16. Job Naming and Traceability

Format: `{planId}-{stepType}-{nodeId}`

Constraints:
- Alphanumeric + hyphens + underscores
- Max ~64 characters
- Include stable trace IDs
- Avoid unsafe characters

## 17. Shared-Path Storage Strategy

Use for:
- Local Docker
- Single PVE node
- Same-LAN workers
- Controlled smoke testing

Model:
```
StorageRuntime prepares input under shared root
→ Worker reads input path
→ Worker writes output path
→ Host/platform reads output path
```

## 18. Object-Storage Materialization Strategy

Use for:
- Production
- Cross-host execution
- Cross-service-provider execution
- Cloud workers

Model:
```
StorageRuntime has input StorageReference
→ Worker receives materialization manifest
→ Worker downloads input to local scratch
→ Provider runs against local scratch paths
→ Worker uploads output to object storage
→ Platform registers StorageReference
→ ProductRuntime creates/updates Product
```

## 19. Worker Local Scratch Strategy

Worker local scratch is execution-local and disposable:
- Not Product identity
- Not StorageReference identity
- Must be cleaned after job completion where possible

## 20. Hybrid Strategy by ExecutionEnvironment

```
Shared path can remain an ExecutionEnvironment-local optimization.
Object storage is the portable production abstraction.
Platform can select materialization strategy per ExecutionEnvironment.
```

## 21. ProductRuntime Boundary

- ProductRuntime owns product lifecycle and dependency semantics
- Output registration goes through StorageRuntime → ProductRuntime
- ProductDependency edges created after successful execution
- P2O.0g does NOT implement ProductRuntime calls

## 22. StorageRuntime Boundary

- StorageRuntime owns input/output storage semantics
- Shared path is an execution materialization detail
- Object storage is production materialization strategy
- P2O.0g does NOT implement StorageRuntime calls

## 23. Public API Safety Boundary

Must NOT expose:
- Signed URLs
- Local filesystem paths
- Object keys
- Bucket names
- Storage provider internals
- Worker paths
- Raw commands
- Provider names
- Environment details

## 24. CJSL Generation Boundary

CJSL generation is an internal implementation detail:
- Generated from OpenCueJobSpecDraft
- Not user-provided
- Not stored as product identity
- Validated against DTD before submission

## 25. Production OpenCue Adapter Boundary

Future production adapter needs:
- Connection pooling
- Error handling
- Retry logic
- Job monitoring
- Frame status tracking
- StorageRuntime integration
- ProductRuntime integration

P2O.0g does NOT implement any of these.

## 26. Relationship to P2O.1 PVE Smoke

P2O.1 can use P2O.0g mapping design for PVE validation:
- Shared-path strategy for single PVE node
- Same CJSL format
- Same submission method

## 27. Relationship to Artifact DAG

Artifact DAG is indefinitely deferred (ADR-025).
P2O.0g does not require or implement Artifact DAG.

## 28. Remotion Boundary

Remotion is a separate execution path.
P2O.0g does not implement Remotion execution.

## 29. Follow-Up Tasks

- P2O.1: PVE smoke validation using shared-path strategy
- Production OpenCue adapter: separate task
- StorageRuntime integration: separate task
- ProductRuntime integration: separate task
