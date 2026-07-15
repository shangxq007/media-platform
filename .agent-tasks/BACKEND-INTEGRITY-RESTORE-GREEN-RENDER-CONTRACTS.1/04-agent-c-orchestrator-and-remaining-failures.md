# Agent C — Orchestrator + Remaining Failures Investigation

**Branch:** `fix/pre-v5-readiness-recovery` @ `7a05cdd`
**Scope:** 10 failing classes, 14 individual test failures

## Executive Summary

- **RenderJobRepositoryTest** and **RenderJobStateMachineErrorModelTest** are **already GREEN** (2 classes, ~15 tests restored)
- **8 classes with 11 failures remain** (down from 14)
- Root causes fall into **3 distinct categories**, each with a clear fix strategy

---

## Category 1: RenderOrchestratorServiceCharacterizationTest (4 failures)

### Root Cause

The test constructs `RenderJobExecutionService` with an **un-stubbed mock** of `RenderJobClaimService`:

```java
// Line 127 in test setUp():
mock(RenderJobClaimService.class), mock(RenderJobFailureService.class)
```

In the production `RenderJobExecutionService.execute()` method (line 186):

```java
boolean claimed = claimService.claimForSelection(jobId);
if (!claimed) {
    log.info("Render job {} already claimed by another request", jobId);
    return jobId;   // ← early return, job stays QUEUED
}
```

Since Mockito defaults `boolean` return to `false`, **every execution path short-circuits** before reaching the provider render call. The job remains `QUEUED` instead of being executed to `COMPLETED`.

### Failing Tests

| Test | Line | Error |
|------|------|-------|
| `submitRenderJobCreatesJobAndRoutesProvider` | 197 | `expected: <COMPLETED> but was: <QUEUED>` |
| `executeExistingRenderJobSucceeds` | 249 | `expected: <COMPLETED> but was: <QUEUED>` |
| `executeExistingRenderJobHandlesProviderFailure` | 269 | `Expected IllegalStateException but nothing was thrown` |
| `submitRenderJobUsesSnapshotPayload` | 328 | `WantedButNotInvoked: zero interactions with this mock` |

### Fix

In the `@BeforeEach setUp()` method, add stubs for the claim and failure services:

```java
RenderJobClaimService claimService = mock(RenderJobClaimService.class);
RenderJobFailureService failureService = mock(RenderJobFailureService.class);
when(claimService.claimForSelection(anyString())).thenReturn(true);
// failureService.recordDurableFailure is void, no stub needed
```

Then pass these stubbed mocks to the `RenderJobExecutionService` constructor. The `failureService` field also needs to be captured so its void method doesn't NPE (Mockito void methods are no-ops by default, so that's fine).

**Files to modify:**
- `render-module/src/test/java/com/example/platform/render/app/RenderOrchestratorServiceCharacterizationTest.java`

---

## Category 2: TimelineTextOverlay Constructor Validation (4 failures across 4 classes)

### Root Cause

The `TimelineTextOverlay` record constructor (lines 32-45) now has **eager validation** that rejects invalid values at construction time:

```java
public TimelineTextOverlay {
    // ...
    if (duration <= 0) {
        throw new IllegalArgumentException("duration must be positive");
    }
    if (startTime < 0) {
        throw new IllegalArgumentException("startTime must be non-negative");
    }
}
```

Four tests attempt to construct `TimelineTextOverlay` with invalid values (negative duration or negative start time), expecting the object to be created and then validation to catch the error downstream. Instead, the **constructor throws immediately** before the test can proceed.

### Failing Tests

| Class | Test | Constructor Arg | Error |
|-------|------|----------------|-------|
| `TimelineEffectApiProductizationTest` | `textOverlayWithNegativeDurationRejected` | `duration = -1.0` | `IllegalArgumentException` at construction (line 180) |
| `TimelineSpecTest` | `shouldRejectTextOverlayWithInvalidDuration` | `duration = -1.0` | `IllegalArgumentException` at construction (line 136) |
| `FFmpegLibassBasicRenderPlannerTest` | `Caption overlay requires valid time range` | `startTime = -1.0` | `IllegalArgumentException` at construction (line 370) |
| `BasicTimelineEditingModelTest` | `Invalid caption time range rejected` | `startTime = -1.0` | `IllegalArgumentException` at construction (line 240) |

### Fix Strategy (two options)

**Option A — Update tests to assert on constructor (recommended):**
Each test should wrap the constructor call in `assertThrows(IllegalArgumentException.class, ...)`:

```java
// TimelineEffectApiProductizationTest — textOverlayWithNegativeDurationRejected
@Test
void textOverlayWithNegativeDurationRejected() {
    assertThrows(IllegalArgumentException.class, () ->
        new TimelineTextOverlay("ov-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 0.0, -1.0, null));
}
```

Same pattern for the other 3 tests.

**Option B — Add a static factory that defers validation:**
Create a `TimelineTextOverlay.unvalidated(...)` factory for tests. This is more invasive and not recommended.

### Files to modify
- `render-module/src/test/java/com/example/platform/render/infrastructure/subtitle/TimelineEffectApiProductizationTest.java`
- `render-module/src/test/java/com/example/platform/render/domain/timeline/TimelineSpecTest.java`
- `render-module/src/test/java/com/example/platform/render/domain/timeline/render/plan/FFmpegLibassBasicRenderPlannerTest.java`
- `render-module/src/test/java/com/example/platform/render/domain/timeline/editing/BasicTimelineEditingModelTest.java`

---

## Category 3: CompileDomainBoundaryTest (1 failure)

### Root Cause

The test `Execution plan groups steps by type correctly` asserts:

```java
assertEquals(0, plan.finalOutputSteps().size());  // line 173
```

`RenderExecutionPlan.finalOutputSteps()` filters by `RenderExecutionStep::isFinalOutput`, which checks:

```java
public boolean isFinalOutput() {
    return artifactNodeType == ArtifactNodeType.FINAL_RENDER;
}
```

The test creates 5 steps. Two of them (`prepDoc` at s2 and `executeProvider` at s3) have `ArtifactNodeType.FINAL_RENDER` as their artifact node type. Therefore `finalOutputSteps()` returns **2**, not **0** as the test expects.

The test was written expecting `finalOutputSteps()` to filter by step type (e.g., `REGISTER_OUTPUT` or `VERIFY_OUTPUT`), but the implementation filters by **artifact node type**.

### Fix

Update the assertion to expect `2` instead of `0`:

```java
assertEquals(2, plan.finalOutputSteps().size());  // prepDoc + executeProvider both have FINAL_RENDER
```

Alternatively, if the semantic intent is different, update the test's `registerOutput` and `finalize` steps to use `FINAL_RENDER` artifact node type and adjust counts accordingly.

**Files to modify:**
- `render-module/src/test/java/com/example/platform/render/domain/timeline/compile/CompileDomainBoundaryTest.java`

---

## Category 4: StorageRuntimeServiceBoundaryTest (1 failure)

### Root Cause

The test `exists() delegates to repository` stubs `repo.exists()` but asserts on `service.find()`:

```java
@Test
void existsDelegates() {
    when(repo.exists("stor-1")).thenReturn(true);
    assertTrue(service.find("stor-1").isPresent());  // ← calls findById, not exists
}
```

`StorageRuntimeService.find()` calls `repo.findById("stor-1")`, which is **not stubbed** and returns `Optional.empty()`. The `repo.exists()` stub is irrelevant because `find()` doesn't use it.

### Fix

Stub `findById` instead of `exists`:

```java
@Test
void existsDelegates() {
    StorageReference ref = localRef("stor-1", "/data", "file.mp4");
    when(repo.findById("stor-1")).thenReturn(Optional.of(ref));
    assertTrue(service.find("stor-1").isPresent());
}
```

**Files to modify:**
- `render-module/src/test/java/com/example/platform/render/app/storage/StorageRuntimeServiceBoundaryTest.java`

---

## Category 5: TimelineRevisionRealRenderSmokeTest (1 failure)

### Root Cause

`NullPointerException` at line 161 — the test constructs `TimelineRevisionRenderService` with `null` for `internalTimelineAdapter`:

```java
TimelineRevisionRenderService renderService = new TimelineRevisionRenderService(
    new StubTimelineRevisionService(revisionRepo),
    snapshotService, mapper, parser,
    null,                                              // ← internalTimelineAdapter is null
    new RenderInputMaterializationService(...), ...);
```

At runtime, `TimelineRevisionRenderService.render()` line 135 calls:

```java
TimelineSpec spec = internalTimelineAdapter.toSpec(timelineJson).orElse(null);
```

NPE because `internalTimelineAdapter` is null.

### Fix

Create a real or stub `InternalTimelineAdapter` for the test. `InternalTimelineAdapter` depends on `InternalTimelineWriter` and `TimelineExtensionsReader`:

```java
TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
InternalTimelineAdapter internalTimelineAdapter = new InternalTimelineAdapter(writer, parser);
```

Then pass `internalTimelineAdapter` instead of `null` in the constructor call.

**Files to modify:**
- `render-module/src/test/java/com/example/platform/render/app/timeline/TimelineRevisionRealRenderSmokeTest.java`

---

## Already Green (2 classes)

| Class | Status |
|-------|--------|
| `RenderJobRepositoryTest` | ✅ GREEN (15 tests, 0 failures) |
| `RenderJobStateMachineErrorModelTest` | ✅ GREEN (26 tests, 0 failures) |

These were likely fixed by parallel agent work or are stable in the current tree.

---

## Summary of Remaining Failures

| # | Class | Failure Count | Root Cause Category |
|---|-------|:---:|---|
| 1 | `RenderOrchestratorServiceCharacterizationTest` | 4 | Un-stubbed `claimForSelection` mock |
| 2 | `TimelineEffectApiProductizationTest` | 1 | Constructor validation (negative duration) |
| 3 | `TimelineSpecTest` | 1 | Constructor validation (negative duration) |
| 4 | `FFmpegLibassBasicRenderPlannerTest` | 1 | Constructor validation (negative startTime) |
| 5 | `BasicTimelineEditingModelTest` | 1 | Constructor validation (negative startTime) |
| 6 | `CompileDomainBoundaryTest` | 1 | `finalOutputSteps()` filters by artifactNodeType |
| 7 | `StorageRuntimeServiceBoundaryTest` | 1 | Test stubs `exists()` but asserts `find()` |
| 8 | `TimelineRevisionRealRenderSmokeTest` | 1 | Null `internalTimelineAdapter` |

**Total remaining failures: 11** (down from 14 in scope)

---

## Fix Priority

1. **Category 2 (TimelineTextOverlay constructor)** — 4 failures across 4 classes, each a one-line `assertThrows` fix. Highest bang-for-buck.
2. **Category 1 (Orchestrator claim mock)** — 4 failures in 1 class. Add `when(claimService.claimForSelection(anyString())).thenReturn(true)` to setUp.
3. **Category 3 (CompileDomainBoundary)** — 1 assertion count fix.
4. **Category 4 (StorageRuntime)** — 1 stub fix.
5. **Category 5 (TimelineRevision smoke)** — 1 constructor arg fix, but depends on `InternalTimelineAdapter` availability.
