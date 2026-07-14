# Backend Integrity Repair — Test Compilation

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-REPAIR-TEST-COMPILATION.0
**Decision:** BACKEND_INTEGRITY_TEST_COMPILATION_RESTORED_WITH_RUNTIME_TEST_FAILURES

---

## Repository Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | c8e7aaa |
| New commit | (pending) |
| Gradle version | 9.1.0 |
| Java version | 25.0.3 (OpenJDK) |
| Spring Boot version | 4.0.4 |
| Submodules | 37 |

---

## Audit Context

- Backend audit commit: c8e7aaa
- Audit reported: 13 test compilation errors, 4 affected test files
- **Actual discovered**: 116+ test compilation errors across 35+ test files in 3 modules
- The audit's "13 errors" only captured the first failing module (`platform-app`). The `render-module` had 101 additional errors and `outbox-event-module` had 3 additional errors, all hidden by Gradle's fail-fast behavior.
- Production compilation: PASSED (except pre-existing `spring-ai-adapter` issue)
- Runtime integrity: UNVERIFIED

## Error Inventory

### Module: outbox-event-module (3 errors, 2 files)

| # | File | Error | Root Cause |
|---|------|-------|------------|
| 1 | TaskDispatcherTest.java:6 | Missing semicolon on wildcard import | SYNTAX_ERROR |
| 2 | TaskHandlerRegistryTest.java:6 | Missing semicolon on wildcard import | SYNTAX_ERROR |
| 3 | TaskHandlerRegistryTest.java:20 | `init()` not public in TaskHandlerRegistry | ACCESS_MODIFIER_CHANGE |

### Module: platform-app (15 errors, 4 files)

| # | File | Error | Root Cause |
|---|------|-------|------------|
| 4-5 | RenderControllerTest.java:24,32 | `RenderController` class not found | MOVED_CLASS_OR_PACKAGE |
| 6-8 | IngestMetadataMergerTest.java:24,42,77 | `ObjectProvider` not functional interface | MOCKITO_API_MISMATCH |
| 9-11 | UploadReportOnlyPreflightHookTest.java:21,38 | `ObjectProvider` not functional interface | MOCKITO_API_MISMATCH |
| 12-14 | UploadReportOnlyPreflightHookTest.java:23,40,53 | Constructor requires 3 args, found 1 | STALE_CONSTRUCTOR_CALL |
| 15-16 | UploadReportOnlyPreflightHookIntegrationTest.java:44,59 | Constructor requires 3 args, found 2 | STALE_CONSTRUCTOR_CALL |
| 17 | UploadReportOnlyPreflightHookIntegrationTest.java:56 | `ObjectProvider` not functional interface | MOCKITO_API_MISMATCH |

### Module: render-module (101 errors, 31 files)

| Root Cause | Count |
|-----------|-------|
| TimelineAssetRef constructor (7→8 args, missing productId) | 28 |
| RenderOutputRegistrationService constructor (3→5 args, ObjectProvider) | 19 |
| StorageRuntimeService constructor (1→2 args, ObjectProvider) | 17 |
| TimelineRevisionRenderService constructor (added InternalTimelineAdapter) | 12 |
| TimelineRevisionService constructor (7→8 args) | 10 |
| StorageRuntimeService.exists() removed | 6 |
| S3ObjectMaterializer → ObjectProvider<S3ObjectMaterializer> | 3 |
| Optional<String> return type change | 3 |
| RenderArtifactQueryService constructor (2→3 args) | 3 |

## Root Cause Analysis

All errors fall into these categories:

1. **SYNTAX_ERROR**: Missing semicolons on wildcard imports (2 errors)
2. **ACCESS_MODIFIER_CHANGE**: `init()` method was package-private, test in different package (1 error)
3. **MOVED_CLASS_OR_PACKAGE**: `RenderController` moved from platform-app to render-module with completely different API (2 errors)
4. **MOCKITO_API_MISMATCH**: `ObjectProvider` used as functional interface via lambda — it is NOT a functional interface (6 errors)
5. **STALE_CONSTRUCTOR_CALL**: Production constructors gained new parameters (ObjectProvider wrappers, new fields) — tests not updated (105+ errors)

## Production Contract Decisions

1. **TaskHandlerRegistry.init()**: Made `public` — this is a `@PostConstruct` lifecycle method that cross-package tests legitimately need to invoke. Minimal, justified production change.

2. **RenderController**: The test was completely stale — it tested a non-existent class in `platform-app`. Updated to test the real `RenderController` from `render-module` using its 1-arg constructor and legacy API.

3. **ObjectProvider pattern**: All tests that used `() -> instance` lambda for `ObjectProvider` were updated to use Mockito mocks with `mockProvider()` helper pattern.

## Files Changed

### Test files (46 files):
- `outbox-event-module`: 2 files (import fixes)
- `platform-app`: 4 files (RenderControllerTest rewrite + ObjectProvider fixes)
- `render-module`: 40 files (ObjectProvider mocks, constructor arg fixes, record field additions)

### Production files (1 file):
- `TaskHandlerRegistry.java`: `init()` changed from package-private to `public`

### Build files: NONE
### Frontend files: NONE (pre-existing static asset changes only)

## Test Compilation Evidence

### Before (reproduced):
```
Command: ./gradlew compileTestJava
Exit code: 0 (but build failed)
Errors: 116+ across 35+ files in 3 modules
```

### After:
```
Command: ./gradlew compileTestJava -x :spring-ai-adapter:compileJava -x :spring-ai-adapter:compileTestJava
Exit code: 0
Errors: 0
```

Note: `spring-ai-adapter:compileJava` has a pre-existing production error (`TenantLitellmKeyService` not found) that exists on the original commit c8e7aaa. This is unrelated to test compilation.

## Targeted Test Results

| Test Class | Compile | Runtime Result | Failure Reason |
|-----------|---------|---------------|----------------|
| TaskDispatcherTest | PASS | FAIL | Mockito ByteBuddy Java 25 self-attach |
| TaskHandlerRegistryTest | PASS | FAIL | Mockito ByteBuddy Java 25 self-attach |
| IngestMetadataMergerTest | PASS | FAIL | Mockito ByteBuddy Java 25 self-attach |
| TimelineSpecTest | PASS | FAIL | Mockito ByteBuddy Java 25 self-attach |

All runtime failures are caused by the same root cause: **Mockito/ByteBuddy cannot self-attach on Java 25**. This is a systemic environment issue, not a test code issue.

## Broad Test Results

- Test compilation: **PASSED** (0 errors)
- Runtime assertions: **BLOCKED** by Mockito/ByteBuddy Java 25 incompatibility
- Spring context: NOT_TESTED
- Database/environment: NOT_TESTED

## Test Authenticity

| Category | Count | Status |
|----------|-------|--------|
| UNIT (mocked) | ~35 | COMPILATION_RESTORED, runtime blocked by ByteBuddy |
| SMOKE (real objects) | ~10 | COMPILATION_RESTORED, runtime blocked by ByteBuddy |
| MVC_MOCK | 0 | N/A |
| DATABASE_INTEGRATION | 0 | N/A |

## No-Silencing Verification

- [x] No tests were excluded.
- [x] No broad @Disabled annotations were added.
- [x] No ignoreFailures setting was enabled.
- [x] No compiler suppression was added.
- [x] No test source sets were excluded.
- [x] No test files were deleted.

## Architecture and Safety

- [x] No new capability introduced.
- [x] No public DTO safety boundary changed.
- [x] No signed URL persistence introduced.
- [x] No storage internals exposed.
- [x] No Flyway baseline changed.
- [x] Architecture drift guard: 27/27 PASS (before and after).

## Upload Truth

The dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

This task did not create an upload Controller, route, service workflow, or frontend upload UI.

## Remaining Integrity Gaps

- Spring runtime context/Bean evidence remains incomplete.
- Runtime MVC route inventory remains incomplete.
- FFmpeg provider registration remains unverified.
- RenderJob lifecycle remains runtime-unverified.
- Backend upload API remains unimplemented.
- Mockito/ByteBuddy Java 25 compatibility needs resolution (environment-level issue).
- `spring-ai-adapter` production compilation needs `TenantLitellmKeyService` resolution.

## Recommended Next Step

Proceed to BACKEND-INTEGRITY-RUNTIME-CONTEXT-VALIDATION.0.

The next task must:
1. Resolve Mockito/ByteBuddy Java 25 compatibility (upgrade Mockito or add JVM args)
2. Establish Spring ApplicationContext, runtime Bean, condition/profile, and RequestMapping evidence
3. Address `spring-ai-adapter` missing `TenantLitellmKeyService`
