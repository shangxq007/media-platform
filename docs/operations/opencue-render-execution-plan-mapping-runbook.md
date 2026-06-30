# Runbook: RenderExecutionPlan-to-CJSL Mapping

## 1. Scope

Design runbook for mapping platform RenderExecutionPlan concepts to OpenCue CJSL structures.
Not a production adapter. Not live execution.

## 2. Prerequisites

- Understanding of RenderExecutionPlan domain model
- Understanding of CJSL XML format (cjsl-1.15.dtd)
- Understanding of OpenCue job/layer/frame model
- Understanding of shared-path vs object-storage strategies

## 3. Inputs Required from RenderExecutionPlan

| Input | Description |
|-------|-------------|
| planId | Deterministic plan identifier |
| steps | Ordered execution steps |
| environmentTarget | LOCAL, OPENCUE, FUTURE_EXTERNAL |
| policy | Execution policy (mode, flags) |
| timelineId | Source timeline reference |

## 4. Required ExecutionEnvironment Metadata

| Metadata | Description |
|----------|-------------|
| facility | OpenCue facility name |
| show | Show name (must exist in Cuebot DB) |
| shot | Shot identifier |
| user | Username |
| storageStrategy | SHARED_PATH or OBJECT_STORAGE |

## 5. Required Storage Strategy Metadata

### Shared Path

| Metadata | Description |
|----------|-------------|
| inputSharedPath | Path to input on shared filesystem |
| outputSharedPathPattern | Pattern for output paths (includes CUE_FRAME) |

### Object Storage

| Metadata | Description |
|----------|-------------|
| manifestId | Materialization manifest identifier |
| inputObjectKeys | Object keys for inputs |
| outputObjectKeyPattern | Pattern for output object keys |
| scratchRoot | Worker local scratch root |

## 6. Mapping to OpenCueJobSpecDraft

### Step 1: Group Steps into Stages

```java
List<List<RenderExecutionStep>> stages = topologicalStages(plan.steps());
```

### Step 2: For Each Stage, Create Job Spec Draft

```java
OpenCueJobSpecDraft draft = OpenCueJobSpecDraft.builder()
    .draftId(generateDraftId(plan.planId(), stageIndex))
    .planId(plan.planId())
    .environmentTarget(plan.environmentTarget())
    .storageStrategy(selectStorageStrategy(env, policy))
    .facility(resolveFacility(env))
    .show(resolveShow(env))
    .shot(plan.planId())
    .user("platform-worker")
    .jobName(generateJobName(plan.planId(), stageIndex))
    .layers(mapLayers(stage))
    .build();
```

### Step 3: Map Each Step to a Layer

```java
OpenCueLayerSpecDraft layer = OpenCueLayerSpecDraft.builder()
    .name(step.nodeId() + "-" + step.type().name().toLowerCase())
    .type("Render")
    .commandSpec(buildCommandSpec(step, storageStrategy))
    .frameRange(extractFrameRange(step))
    .chunkSize(extractChunkSize(step))
    .resources(extractResources(step))
    .build();
```

## 7. Mapping to CJSL

```java
String generateCjsl(OpenCueJobSpecDraft draft) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<!DOCTYPE spec SYSTEM \"http://localhost:8080/spcue/dtd/cjsl-1.15.dtd\">\n");
    xml.append("<spec>\n");
    xml.append("  <facility>").append(escapeXml(draft.facility())).append("</facility>\n");
    xml.append("  <show>").append(escapeXml(draft.show())).append("</show>\n");
    xml.append("  <shot>").append(escapeXml(draft.shot())).append("</shot>\n");
    xml.append("  <user>").append(escapeXml(draft.user())).append("</user>\n");
    xml.append("  <job name=\"").append(escapeXml(draft.jobName())).append("\">\n");
    xml.append("    <paused>").append(draft.paused()).append("</paused>\n");
    xml.append("    <layers>\n");
    for (OpenCueLayerSpecDraft layer : draft.layers()) {
        xml.append(generateLayerCjsl(layer));
    }
    xml.append("    </layers>\n");
    xml.append("  </job>\n");
    xml.append("</spec>\n");
    return xml.toString();
}
```

## 8. Shared-Path Execution Mode

### Input Preparation

```
StorageRuntime.materialize(storageReferenceId) → sharedPath
```

### Command Construction

```
ffmpeg -i {sharedInputPath} ... {sharedOutputPath}/frame-${CUE_FRAME}.mp4
```

### Output Collection

```
Host reads {sharedOutputPath}/frame-*.mp4
```

## 9. Object-Storage Materialization Mode

### Manifest Preparation

Platform creates manifest with:
- Input object keys and checksums
- Output object key patterns
- Worker scratch paths

### Command Construction

```
media-worker-materialize-and-render --manifest {manifestPath}
```

### Worker Responsibilities

1. Download inputs from object storage to local scratch
2. Execute provider against local scratch paths
3. Upload outputs from local scratch to object storage
4. Write output manifest

### Platform Responsibilities

1. Create input manifest
2. Upload manifest to shared path or object storage
3. Submit CJSL job
4. Poll job status
5. Read output manifest
6. Register StorageReferences
7. Create Products

## 10. Dependency Stage Splitting

### Algorithm

```java
List<List<RenderExecutionStep>> topologicalStages(List<RenderExecutionStep> steps) {
    // Build dependency graph
    // Topological sort
    // Group steps with no inter-dependencies into same stage
    // Return list of stages
}
```

### Submission Flow

```
for each stage:
    draft = mapToJobSpecDraft(stage)
    cjsl = generateCjsl(draft)
    jobId = submitToCuebot(cjsl)
    status = pollJobStatus(jobId)
    if status == DEAD:
        collectFailureDetails(jobId)
        throw StageExecutionFailedException
    validateStageOutputs(stage)
```

## 11. Failure Status Interpretation

| Job Status | Frame Status | Interpretation |
|-----------|--------------|----------------|
| SUCCEEDED | All SUCCEEDED | Full success |
| DEAD | Some DEAD | Partial failure |
| DEAD | All DEAD | Full failure |
| RUNNING | Mixed | In progress |

## 12. Output Registration Handoff

After successful execution:
1. Collect output paths from shared path or output manifest
2. Register in StorageRuntime (creates StorageReference)
3. Create Product via ProductRuntime
4. Link ProductDependency edges (input → output)
5. Mark render job as completed

## 13. Validation Checklist

- [ ] Facility matches RQD facility
- [ ] Show exists in Cuebot DB
- [ ] Job name is unique and traceable
- [ ] Command does not contain user input
- [ ] Command does not expose signed URLs
- [ ] Command does not expose local host paths
- [ ] Output paths include CUE_FRAME for multi-frame jobs
- [ ] Chunk size is appropriate for frame independence
- [ ] Memory and core hints are reasonable
- [ ] Dependencies handled by stage splitting
- [ ] Failure status mapping is correct
- [ ] Output registration flow is defined

## 14. What Not to Do

- Do not assume CJSL can express dependencies
- Do not expose signed URLs in CJSL or public API
- Do not expose local host paths in CJSL or public API
- Do not use raw user input in commands
- Do not persist signed URLs as product identity
- Do not expose storage provider internals
- Do not implement production adapter in this task

## 15. Transition to P2O.1 PVE Smoke

P2O.1 can use:
- Shared-path strategy for single PVE node
- Same CJSL format and generation
- Same submission method (grpcurl)
- Same status polling approach

## 16. Transition to Production Adapter

Production adapter needs:
- gRPC client library (not grpcurl)
- Connection pooling and retry
- Asynchronous job submission
- Real-time status monitoring
- StorageRuntime integration
- ProductRuntime integration
- Error handling and retry policies
- Metrics and observability
