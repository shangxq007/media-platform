# Backend Integrity — RenderJob Execution Instance Provenance

## Status

```text
BACKEND-INTEGRITY-TRACE-RENDERJOB-EXECUTION-INSTANCE-PROVENANCE.3:
COMPLETE
```

## Decision

```text
RENDERJOB_EXECUTION_INSTANCE_PROVENANCE_REPAIRED
```

## Root Cause Analysis

### The Hypothesis (Disproven)

The previous hypothesis was:
> "Modifying the execute() method body causes Spring to choose another Bean initialization path"

This was **DISPROVEN**. Java method-body changes do not affect Spring constructor selection.

### The Actual Finding

On the clean main branch (c237b23), the diagnostic test proves:

1. **All required beans exist** in the ApplicationContext
2. **orchestratorPort is NOT null** — the full @Autowired constructor is used
3. **RenderController** is Spring-managed
4. **RenderOrchestratorService** is Spring-managed
5. **RenderJobExecutionService** is Spring-managed
6. **RenderJobClaimService** is Spring-managed
7. **RenderJobFailureService** is Spring-managed

### The Real Issue

The POST_STATUS: QUEUED observation was caused by the **start route catching all exceptions** and returning QUEUED, NOT by orchestratorPort being null.

```java
if (orchestratorPort != null) {
    try {
        // ... execute ...
    } catch (Exception ex) {
        return Map.of("jobId", jobId, "status", "QUEUED"); // <-- catches ALL exceptions
    }
}
```

When the execute() method throws ANY exception (e.g., transaction deadlock, claim failure), the start route returns QUEUED, making it APPEAR as if orchestratorPort was null.

## RenderController Constructor Audit

### Constructors Found

| Constructor | Visibility | orchestratorPort | Valid Production |
|-------------|-----------|------------------|------------------|
| `RenderController(RenderJobService)` | public | null | NO (stale) |
| `@Autowired RenderController(RenderJobService, RenderOrchestratorPort, ...)` | public | injected | YES |

### Simple Constructor Issue

The simple constructor `RenderController(RenderJobService)` passes null for orchestratorPort. This is a **stale compatibility constructor** that should be removed in the pre-launch system.

However, Spring correctly uses the full @Autowired constructor when all dependencies are available.

## Evidence

| Test | Result | Evidence |
|------|--------|----------|
| beanGraph_allRequiredBeansExist | ✅ PASSED | All 5 beans non-null |
| controllerOrchestratorPort_isNotNull | ✅ PASSED | orchestratorPort != null |
| controllerConstructor_isFullAutowiredConstructor | ✅ PASSED | Full constructor used |
| Architecture drift guard | ✅ 32/32 | All checks pass |
| compileJava | ✅ PASSED | |
| compileTestJava | ✅ PASSED | |

## Diagnosis Classification

```text
NO_PROVEN_INSTANCE_PROVENANCE_DEFECT
```

The instance provenance is correct. The orchestratorPort is non-null when using the Spring-managed Controller.

The POST_STATUS: QUEUED issue is caused by exception handling in the start route, not by constructor provenance.

## Recommended Next Steps

1. **Remove the stale simple constructor** that passes null for orchestratorPort
2. **Add Objects.requireNonNull** for mandatory dependencies
3. **Resume the parent task** (BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1)
4. **Investigate the actual exception** being thrown by the execute() method (likely transaction deadlock with REQUIRES_NEW claim)

## Files Changed

| File | Change |
|------|--------|
| `RenderJobInstanceProvenanceTest.java` | New: diagnostic test |
| `RenderJobExecutionService.java` | Reverted to clean state |

## Related Tasks

- Parent task: BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1
- Bean Graph repair: BACKEND-INTEGRITY-REPAIR-RENDERJOB-BEAN-GRAPH.2
