# Git, Kanban, and Baseline State

## Git State

```
Branch: fix/pre-v5-readiness-recovery
HEAD: 1643274
origin/main: c237b23
Ahead: 11 commits
Behind: 0 commits
```

## Expected Recent Commits

```
1643274 (HEAD) fix: recover 33 render-module tests
7a05cdd fix: partial test baseline recovery
0526722 fix: partial pre-V5 readiness recovery
1acab6b docs: correct render output commit migration inputs
b0b00f8 docs: close render output commit protocol ambiguities
a539594 docs: define Render Output Commit Protocol architecture
```

## Kanban State

```
BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2: in_progress
ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1: blocked
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0: blocked
BACKEND-INTEGRITY-IMPLEMENT-RENDER-OUTPUT-COMMIT-PROTOCOL.1: blocked
```

## Environment

```
Java: 25.0.3 (OpenJDK)
Gradle: 9.1.0
Docker: Podman 5.4.2
FFmpeg: 7.0.2-static (johnvansickle.com)
  Path: /home/user/.local/bin/ffmpeg
  SHA256: e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99
  ffprobe: /home/user/.local/bin/ffprobe
  ffprobe SHA256: 4f231a1960d83e403d08f7971e271707bec278a9ae18e21b8b5b03186668450d
  libx264: YES
RAM: 125Gi total, 112Gi available
OS: Linux 6.12.0-160000.35-default (openSUSE)
```

## Compilation & Architecture Guard

```
compileJava: PASS
compileTestJava: PASS
Architecture guard: 32/32 PASS
```

## Render Module Baseline (1643274)

```
Total: 2749
Passed: 2726
Failed: 6
Skipped: 17
```

### Failing Tests

| # | Class | Method | Type | Message |
|---|-------|--------|------|---------|
| 1 | RenderOrchestratorServiceCharacterizationTest | executeExistingRenderJobHandlesProviderFailure | AssertionFailedError | expected: <FAILED> but was: <EXECUTING> |
| 2 | RenderPipelineE2ECharacterizationTest | scenarioI_jobStatusLifecycle | AssertionFailedError | expected: <true> but was: <false> |
| 3 | RenderPipelineE2ECharacterizationTest | scenarioK_providerFailureHandling | AssertionFailedError | expected: <FAILED> but was: <EXECUTING> |
| 4 | TimelineRevisionRenderServiceTest | R6.1: missing input Product fails closed | AssertionFailedError | Error must indicate resolution failure: expected true but was false |
| 5 | TimelineRevisionRenderServiceTest | R6.1: input Product not READY fails closed | AssertionFailedError | Error must indicate resolution failure: expected true but was false |
| 6 | RenderJobRepositoryTest | initializationError | ContainerLaunchException | Container startup failed for image postgres:15-alpine |

## Platform-App Baseline (1643274)

```
Total: 75
Passed: 54
Failed: 4
Skipped: 17
OOM: YES (Java heap space)
```

### Failing Tests

| # | Class | Method | Type | Message |
|---|-------|--------|------|---------|
| 1 | Gradle Test Executor 1 | failed to execute tests | TestSuiteExecutionException | OOM - Java heap space |
| 2 | ModularityTest | modularityViolationsWithinBudget | AssertionFailedError | 123 Modulith violations |
| 3 | MvcRouteInventoryTest | captureRouteInventory | IllegalStateException | Failed to load ApplicationContext |
| 4 | ProviderRegistrationValidationTest | initializationError | ContainerLaunchException | Container startup failed postgres:15-alpine |

## Full Suite Baseline

```
Status: RUNNING (background)
```

## Discrepancy Note

Task spec reported "6 render failures" — actual reproduction confirms exactly 6. Match verified.
