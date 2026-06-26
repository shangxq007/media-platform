---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Job Model

## Model Hierarchy

```
ExecutionJob (jobId, environmentId, backendId, priority, resourceRequirements, tasks[])
    └── ExecutionTask (taskId, backendSpec, dependencies[], retryPolicy)
         └── ExecutionCommand (commandType, executable, arguments, environment, workingDirectory)
```

## Environment Mapping

| Model | Local | OpenCue | Kubernetes | Ray |
|-------|-------|---------|-----------|-----|
| ExecutionJob | Single-thread submission | OpenCue Job | K8s Job | Ray Job |
| ExecutionTask | Single task | OpenCue Layer | K8s Pod | Ray Task |
| ExecutionCommand | Process (CLI) | Frame dispatch | Container command | Function |

## Execution Flow

```
BackendExecutionSpec → EnvironmentCompiler.compile() → ExecutionJob
    → ExecutionEnvironment.submit(job) → executionId
    → tasks executed by environment
    → status(executionId) → completion/failure
```

## Job Construction

EnvironmentCompiler owns Job construction:
- LocalEnvironmentCompiler → single-task Job
- OpenCueCompiler → multi-task Job with frame decomposition (future)
- K8sCompiler → multi-task Job with pod per task (future)
