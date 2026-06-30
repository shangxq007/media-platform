# Dependency Stage Splitting Example

## Problem

Current CJSL (cjsl-1.15.dtd) does not support `<depends>` syntax.
P2O.0f confirmed: dependency elements fail DTD validation.

## Solution: Platform-Side Stage Splitting

Dependencies are managed by the platform orchestrator, not by CJSL.

### Example: RenderExecutionPlan with Dependency A → B

```
RenderExecutionPlan:
  Step A: Materialize input
  Step B: Execute FFmpeg (depends on A)
  Step C: Register output (depends on B)
```

### Mapping to OpenCue Jobs

```
Stage 1 (Job A):
  - Materialize input to shared path / object storage
  - Submit to Cuebot
  - Wait for SUCCEEDED
  - Validate output exists

Stage 2 (Job B):
  - Execute FFmpeg against materialized input
  - Submit to Cuebot
  - Wait for SUCCEEDED
  - Validate rendered output exists

Stage 3 (Job C):
  - Register output in StorageRuntime
  - Create ProductDependency edges
  - Finalize render
```

### Pseudocode

```java
public void executeWithDependencySplitting(RenderExecutionPlan plan) {
    // Topological sort of steps by dependency
    List<List<RenderExecutionStep>> stages = topologicalStages(plan.steps());

    for (List<RenderExecutionStep> stage : stages) {
        // All steps in a stage have no inter-dependencies
        // They can be submitted as one OpenCue job or in parallel

        OpenCueJobSpecDraft draft = mapToJobSpecDraft(stage, plan);
        CjslXml cjsl = generateCjsl(draft);

        // Submit stage
        String jobId = submitToCuebot(cjsl);

        // Wait for completion
        JobStatus status = pollJobStatus(jobId);

        if (status == DEAD) {
            // Handle failure
            collectFailureDetails(jobId);
            throw new StageExecutionFailedException(jobId);
        }

        // Validate outputs before next stage
        validateStageOutputs(stage);
    }
}
```

### Multi-Layer Within a Stage

When multiple steps have no dependencies on each other, they can be combined into a single OpenCue job with multiple layers:

```xml
<job name="render-abc123-combined">
  <layers>
    <layer name="preprocess" type="Render">
      <cmd><arg>...</arg></cmd>
      <range>1-1</range>
      <chunk>1</chunk>
    </layer>
    <layer name="render" type="Render">
      <cmd><arg>...</arg></cmd>
      <range>1-10</range>
      <chunk>1</chunk>
    </layer>
  </layers>
</job>
```

Cuebot handles intra-job layer ordering when dependencies exist in the DTD.
When they don't, the platform splits into separate jobs.

## Decision Matrix

| Scenario | Strategy |
|----------|----------|
| No dependencies | Single job, single or multi-layer |
| Linear chain A→B→C | 3 sequential jobs |
| Fan-out A→(B,C)→D | Job A, then parallel jobs B+C, then job D |
| Complex DAG | Topological sort into stages |

## Safety Rules

1. Never assume CJSL can express dependencies
2. Always validate stage outputs before submitting next stage
3. Never leak stage internals into public API
4. Never expose submission credentials
5. Never expose worker paths in public API

## Future Work

If CJSL dependency syntax becomes available:
- Platform can optionally use `<depends>` for intra-job dependencies
- Cross-job dependencies remain platform-side
- Hybrid approach: intra-job deps in CJSL, inter-job deps in platform
