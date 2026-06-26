---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Planner Blueprint

## 1. Responsibilities

| Owns | Does NOT Own |
|------|-------------|
| Artifact Dependency DAG | FFmpeg graph |
| Capability resolution | BMF graph |
| Provider selection | MLT graph |
| Backend selection | Remotion composition |
| Execution ordering | OpenCue job |
| Dependency graph topology | Media encoding/decoding |
| Parallel stage generation | GPU scheduling |
| Failure propagation | Distributed worker assignment |
| Retry boundary | Execution environment |

## 2. Execution Model

```
ExecutionRequest
    ↓
Artifact DAG (what needs to be produced)
    ↓
Execution Planner (how to organize production)
    ↓
Logical Execution Plan (ordered stages + steps)
    ↓
Backend Compiler (translate to backend-specific format)
    ↓
ExecutionBackend (execute)
```

## 3. Artifact DAG

### ArtifactNode
- id: String
- artifactType: PROBE | TRANSCRIPT | THUMBNAIL | TRANSCODE | FILTER | EMBEDDING
- inputReferences: List<StorageReference>
- outputType: VIDEO | AUDIO | IMAGE | TEXT | VECTOR
- capabilityRequirement: CapabilityRequirement
- metadata: Map

### ArtifactEdge
- sourceNodeId
- targetNodeId
- dependencyType: REQUIRED | OPTIONAL | CACHE_HIT

### CapabilityRequirement
- capability: TaskCapability
- providerHint: String (optional)
- backendHint: String (optional)
- parameters: Map

## 4. Logical Execution Plan

### ExecutionPlan
- planId
- artifactDagHash
- stages: List<ExecutionStage>
- totalSteps: int
- estimatedDuration: long

### ExecutionStage
- stageId
- stageNumber: int
- steps: List<ExecutionStep>
- dependencyType: PARALLEL | SEQUENTIAL
- onFailure: FAIL_STAGE | CONTINUE

### ExecutionStep
- stepId
- artifactNodeId
- capability: TaskCapability
- provider: String
- backend: String
- inputRefs: List<StorageReference>
- outputRefs: List<StorageReference>
- retryPolicy: RetryPolicy

### RetryPolicy
- maxAttempts: int (default 3)
- backoffMs: long (default 5000)
- onFailure: FAIL_STEP | RETRY | SKIP

## 5. Planner Algorithm

1. Load Artifact DAG
2. Topological sort → ordered node list
3. For each node:
   a. Resolve capability → TaskCapability
   b. Select provider → ExtensionRegistryService (by capability)
   c. Select backend → ExecutionBackendRegistry (by TaskCapability)
   d. Assign to stage (parallel-compatible nodes in same stage)
4. Group parallel nodes into ExecutionStage
5. Calculate estimated duration
6. Return ExecutionPlan

## 6. Compiler Layer

### BackendCompiler SPI

```java
interface BackendCompiler {
    String backendId();
    boolean supports(ExecutionStep step);
    Object compile(ExecutionStep step, List<StorageReference> inputs);
}
```

### Compiler Implementations (Future)
- `FfmpegCompiler` → FFmpeg filter graph
- `BmfCompiler` → BMF graph definition
- `MltCompiler` → MLT XML
- `RemotionCompiler` → Remotion composition
- `OpenCueJobCompiler` → OpenCue job spec

## 7. OpenCue Interaction

```
Execution Planner → BackendCompiler → OpenCueCompiler → OpenCueJob
    → OpenCue → Worker → ExecutionBackend → Result
```

OpenCue is a scheduler. Execution Planner owns the plan. BackendCompiler translates to OpenCue format.

## 8. Storage Model

### StorageReference
- storageUri
- checksum
- mediaType
- sizeBytes
- accessStrategy: LOCAL_CACHE | REMOTE_STREAM | RANGE_READ

No raw bytes in execution payload.

## 9. Future Evolution

- Phase 1: Artifact DAG model (Sprint 047)
- Phase 2: Execution Planner algorithm (Sprint 048)
- Phase 3: BackendCompiler SPI (Sprint 049)
- Phase 4: FFmpegCompiler (Sprint 050)
- Phase 5: BmfCompiler (Sprint 051)
