---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Environment SPI

## SPI Hierarchy

```
ExecutionEnvironment (SPI)
    ├── environmentId()
    ├── environmentType()
    ├── supports(List<String> capabilities)
    ├── submit(EnvironmentExecutionSpec) → executionId
    ├── cancel(executionId) → boolean
    └── status(executionId) → String

EnvironmentCompiler (SPI)
    ├── environmentType()
    ├── supports(String environmentType) → boolean
    └── compile(BackendExecutionSpec) → EnvironmentExecutionSpec

EnvironmentRuntimeService
    ├── resolve(String environmentType) → Optional<ExecutionEnvironment>
    ├── resolveCompiler(String environmentType) → Optional<EnvironmentCompiler>
    ├── listEnvironments() → List<String>
    └── listCompilers() → List<String>
```

## Execution Flow

```
BackendExecutionSpec → EnvironmentCompiler.compile() → EnvironmentExecutionSpec
    → EnvironmentRuntimeService.resolve(environmentType) → ExecutionEnvironment
    → environment.submit(spec) → executionId
    → environment.status(executionId) → completion/failure
```

## Future Implementations

| Environment | Compiler | Phase |
|------------|----------|-------|
| Local Process | LocalCompiler (maps to direct backend execution) | V1 |
| OpenCue | OpenCueCompiler → OpenCueEnvironment | Phase 1 |
| Kubernetes | K8sCompiler → K8sEnvironment | Phase 2 |
| Ray | RayCompiler → RayEnvironment | Phase 3 |

## Backend vs Environment (Reinforced)

| Backend | Environment |
|---------|------------|
| WHAT processes media | WHERE/HOW execution happens |
| BMF, FFmpeg, Remotion | Local, OpenCue, K8s, Ray |
| BackendCompiler produces BackendExecutionSpec | EnvironmentCompiler produces EnvironmentExecutionSpec |
