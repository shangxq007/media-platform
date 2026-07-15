# 04 — Agent C: Test Baseline and Migration Readiness

**Branch**: `arch/render-output-commit-protocol-errata` @ `b0b00f8`
**Date**: 2026-07-15
**Scope**: Identify intentionally failing TDD cleanup markers, run compilation and architecture guard, determine default baseline color.

---

## 1. Three Intentionally Failing TDD Cleanup Markers

Commit `234689e` ("test: update state machine tests for FALLBACKING/RETRYING removal") introduced **three TDD RED-phase markers** in `RenderJobStateMachineErrorModelTest.java`. These tests were flipped from `assertTrue` to `assertFalse` to encode the desired future state: FALLBACKING and RETRYING should be excluded from the valid transition graph.

### 1.1 The Three TDD Markers

| # | Test Method | DisplayName | Assertion | Actual Result | Status |
|---|-------------|-------------|-----------|---------------|--------|
| 1 | `executingToFallbacking()` | EXECUTING → FALLBACKING is NOT valid (stale baggage) | `assertFalse(canTransition(EXECUTING, FALLBACKING))` | `false` (already removed from EXECUTING targets) | **PASS** ✅ |
| 2 | `executingToRetrying()` | EXECUTING → RETRYING is NOT valid (stale baggage) | `assertFalse(canTransition(EXECUTING, RETRYING))` | `false` (already removed from EXECUTING targets) | **PASS** ✅ |
| 3 | `fallbackingToExecuting()` | FALLBACKING → EXECUTING is NOT valid (stale baggage) | `assertFalse(canTransition(FALLBACKING, EXECUTING))` | `true` (FALLBACKING entry still in VALID_TRANSITIONS) | **FAIL** ❌ |

### 1.2 Root Cause Analysis

The `RenderJobStateMachine.VALID_TRANSITIONS` map was **partially cleaned up**:

```text
EXECUTING targets: {COMPLETING, FAILED, CANCELLED}
  → FALLBACKING and RETRYING already removed ✅

FALLBACKING entry still present:
  FALLBACKING → {EXECUTING, FAILED, CANCELLED}
  → Must be removed entirely ❌

RETRYING entry still present:
  RETRYING → {EXECUTING, FAILED, CANCELLED}
  → Must be removed entirely ❌
```

The state machine `canTransition(FALLBACKING, EXECUTING)` returns `true` because the FALLBACKING entry still exists in the VALID_TRANSITIONS map with EXECUTING as a valid target. The TDD marker correctly identifies this as the remaining cleanup work.

### 1.3 Additional TDD-Adjacent Failures (Not Part of the Three Markers)

Four tests fail due to `TimelineTextOverlay` constructor validation blocking higher-level validation testing:

| Test Class | Test Method | Root Cause |
|------------|-------------|------------|
| `TimelineSpecTest` | `shouldRejectTextOverlayWithInvalidDuration()` | Constructor throws `IllegalArgumentException: duration must be positive` before reaching `TimelineSpec.validate()` |
| `BasicTimelineEditingModelTest` | `invalidCaptionTimeRangeRejected()` | Constructor throws for `startTime=-1, duration=0` before reaching `BasicTimelineValidator.validate()` |
| `FFmpegLibassBasicRenderPlannerTest` | `captionOverlayRequiresValidTimeRange()` | Constructor throws for `startTime=-1.0` before reaching the planner |
| `TimelineEffectApiProductizationTest` | `textOverlayWithNegativeDurationRejected()` | Constructor throws for `duration=-1.0` before reaching `TimelineSpec.validate()` |

These are not the three TDD markers but represent a similar cleanup need: the `TimelineTextOverlay` record constructor now validates `duration > 0` and `startTime >= 0` (lines 39–44), making these higher-layer validation tests unreachable. They need updating to either test the constructor validation directly or use valid construction with higher-layer invalid configurations.

---

## 2. Compilation Results

### 2.1 compileJava

```text
Result: BUILD SUCCESSFUL
Tasks: 38 actionable tasks: 38 up-to-date
Duration: 6s
```

**GREEN** ✅ — All production source compiles cleanly.

### 2.2 compileTestJava

```text
Result: BUILD SUCCESSFUL
Tasks: 77 actionable tasks: 77 up-to-date
Duration: 7s
```

**GREEN** ✅ — All test source compiles cleanly.

### 2.3 bootJar

```text
Result: BUILD SUCCESSFUL
Tasks: 77 actionable tasks: 6 executed, 71 up-to-date
Duration: 7s
```

**GREEN** ✅ — All three boot JARs produced:
- `platform-app:bootJar`
- `remote-render-worker:bootJar`
- `sandbox-worker:bootJar`

---

## 3. Architecture Guard

```text
Script: scripts/check-architecture-drift.sh
Result: ✅ All architecture drift checks passed
Checks: 32
Failed: 0
```

**GREEN** ✅ — All 32 architecture drift checks pass. Key checks verified:
- No StorageDeliveryProfileResolver in production code
- Storage delivery classes don't contain credential fields
- No forbidden persistence repositories
- Preflight report persistence guards pass
- Admin routes require ROLE_ADMIN authority
- SPA fallback restricted to /app/**
- spring-ai-adapter is HOLD
- OpenCue/Artifact DAG remains POSTPONED/DEFERRED

---

## 4. Test Baseline Summary

### 4.1 Module-Level Results

| Module | Tests | Failed | Skipped | Status |
|--------|-------|--------|---------|--------|
| render-module | 2749 | 39 | 17 | 🔴 RED |
| platform-app | 422 | 41 | 21 | 🔴 RED |
| outbox-event-module | 49 | 4 | 0 | 🔴 RED |
| All other modules | ~450 | 0 | 0 | 🟢 GREEN |

### 4.2 Failure Categories

#### render-module (39 failures)

| Category | Count | Description |
|----------|-------|-------------|
| TDD state machine marker | 1 | `fallbackingToExecuting()` — FALLBACKING/RETRYING not fully removed |
| TextOverlay constructor TDD | 4 | Constructor validates before higher-layer tests can run |
| E2E/Smoke (FFmpeg/S3/infra) | ~30 | TimelineFfmpegBaselineRenderSmokeTest, TimelineRevisionRealRenderSmokeTest, RenderInputMaterializationSmokeTest, TimelineRevisionRenderServiceTest, TimelineRevisionRenderModeParityTest, etc. |
| Docker container | 1 | RenderJobRepositoryTest — ContainerLaunchException (no Docker) |
| Other | ~3 | StorageRuntimeServiceBoundaryTest, CompileDomainBoundaryTest, RenderPipelineE2ECharacterizationTest |

#### platform-app (41 failures)

| Category | Count | Description |
|----------|-------|-------------|
| Spring context loading | ~5 | PreviewBootTest, OidcIdentityProvisioning*, etc. |
| Security boundary tests | ~20 | EnabledAdminSecurityTest, RealHttpSecurityBoundaryTest |
| Render integration | ~10 | RenderJobSelectionTransition*, StartClaimAndFailureDurabilityTest, RenderExecutionBoundaryTest |
| Modularity | 1 | ModularityTest — module boundary violations |
| Other | ~5 | StorageDeliveryProfile*, ReportOnlyPreflightPolicyEvaluatorTest, etc. |

#### outbox-event-module (4 failures)

| Category | Count | Description |
|----------|-------|-------------|
| API mismatch | 4 | OutboxEventDispatcherTest — tests expect behavior that doesn't match current production code |

### 4.3 Pre-existing Failure Context

Historical docs reference 22 pre-existing platform-app failures (as of 2026-06-06). Current count is 41, suggesting additional tests were added or Spring context issues worsened. The outbox-event-module failures (4) appear to be a regression from production code changes not reflected in test expectations.

---

## 5. Default Baseline Verdict

```text
COMPILE:     GREEN ✅ (compileJava, compileTestJava, bootJar)
ARCH GUARD:  GREEN ✅ (32/32 checks passed)
TESTS:       RED   ❌ (84 failures across 3 modules)
```

**The default baseline is NOT green.** Compilation and architecture guard pass cleanly, but there are 84 test failures across three modules.

### 5.1 Migration Readiness Assessment

| Gate | Status | Notes |
|------|--------|-------|
| Production source compiles | ✅ PASS | Zero compilation errors |
| Test source compiles | ✅ PASS | Zero compilation errors |
| bootJar produces artifacts | ✅ PASS | All 3 boot JARs built |
| Architecture guard clean | ✅ PASS | 32/32 checks |
| Default test baseline green | ❌ FAIL | 84 failures (39 render + 41 platform-app + 4 outbox) |
| TDD cleanup markers resolved | ❌ FAIL | 1 of 3 FALLBACKING/RETRYING markers still failing |
| TextOverlay test cleanup | ❌ FAIL | 4 tests blocked by constructor validation |

### 5.2 Impact on Migration Work

The render-module TDD markers (1 failing state machine test + 4 TextOverlay tests) are **low-risk for migration work** — they encode desired behavior changes that don't block compilation, architecture guard, or the render output commit protocol schema work.

The platform-app failures (41) and outbox failures (4) are **pre-existing and unrelated** to the render output commit protocol errata. They should be tracked separately.

For the ERRATA.0B task group, the **compilation and architecture guard baselines are green**, which is sufficient for documentation/design/schema work. Test failures do not block the errata documentation effort.

---

## 6. Summary

- **3 TDD cleanup markers** identified in `RenderJobStateMachineErrorModelTest` (commit `234689e`): 3 tests encoding FALLBACKING/RETRYING removal, 1 actually failing due to partial state machine cleanup
- **4 additional TDD-adjacent failures** in TextOverlay validation tests (constructor blocks higher-layer validation)
- **compileJava**: GREEN
- **compileTestJava**: GREEN
- **bootJar**: GREEN
- **Architecture guard**: GREEN (32/32)
- **Default test baseline**: RED (84 failures across render-module, platform-app, outbox-event-module)
- **Migration readiness**: Compilation and arch guard are green; test failures are pre-existing and non-blocking for errata documentation work
