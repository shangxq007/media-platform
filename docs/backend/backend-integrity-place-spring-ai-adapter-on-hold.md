# Backend Integrity — Place spring-ai-adapter on HOLD

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-PLACE-SPRING-AI-ADAPTER-ON-HOLD.0
**Decision:** SPRING_AI_ADAPTER_HOLD_FORMALIZED_WITH_EXISTING_TEST_FAILURES

---

## Architecture Constraint

Spring AI runtime is NOT_APPROVED_FOR_MAINLINE.

spring-ai-adapter is retained only as a HOLD module.

The module's source presence is not approval for integration or repair.

## Triage Basis

- Triage commit: d37f499
- Approval evidence: NONE
- platform-app dependency: NO
- runtimeClasspath inclusion: NO
- bootJar inclusion: NO
- container inclusion: NO

## Previous Build Problem

- Module included in default Gradle graph (37 modules)
- Three compile errors (TenantLitellmKeyService cross-module reference)
- Manual `-x :spring-ai-adapter:*` exclusions required for every build command

## Selected HOLD Mechanism

**Strategy: CONDITIONAL_SETTINGS_INCLUSION**

- `settings.gradle.kts`: `spring-ai-adapter` removed from default `include()` block
- Added conditional inclusion gated by `includeHoldModules` Gradle property
- Default: `false` (module excluded)
- Opt-in: `./gradlew -PincludeHoldModules=true <task>`

Why reversible: Module source retained. Single property controls inclusion. No source deletion, no task suppression.

Why no silent skip: Module is simply not configured — no `enabled = false`, no `onlyIf`, no `ignoreFailures`.

## Default Project Graph

| Metric | Value |
|--------|-------|
| Default active modules | 34 |
| HOLD modules | 1 (spring-ai-adapter) |
| Total source-retained | 35 |

## HOLD Opt-in Validation

```
$ ./gradlew -PincludeHoldModules=true projects
+--- Project ':spring-ai-adapter'  ← PRESENT

$ ./gradlew -PincludeHoldModules=true :spring-ai-adapter:compileJava
BUILD FAILED — 3 errors (TenantLitellmKeyService cross-module reference)
```

HOLD_MODULE_EXPLICIT_COMPILE: EXPECTED_FAILURE_KNOWN_DESIGN_DEBT

## Default Mainline Validation

| Validation | Command | Result |
|-----------|---------|--------|
| Production compile | `./gradlew compileJava` | PASSED |
| Test compile | `./gradlew compileTestJava` | PASSED |
| Assembly | `./gradlew assemble` | PASSED |
| Core tests | `./gradlew :outbox-event-module:test :platform-app:test` | EXECUTED_WITH_FAILURES |
| platform-app runtimeClasspath | dependency inspection | spring-ai-adapter ABSENT |
| bootJar inspection | `jar tf` | spring-ai-adapter ABSENT |
| Container path | N/A (no Dockerfile) | NOT_VERIFIED |

No `-x` exclusions used.

## Test Truth

- outbox-event-module: 49 total, 45 passed, 4 failed
- platform-app: 375 total, 347 passed, 6 failed, 22 skipped
- render-module: 2756 total (previously verified)
- Mockito bootstrap failures: 0
- All failures are pre-existing assertion/context issues

## Runtime and Packaging Isolation

- platform-app compile dependency on adapter: NO
- platform-app runtime dependency on adapter: NO
- adapter on platform-app runtimeClasspath: NO
- adapter included in bootJar: NO
- adapter included in container: NOT_VERIFIED (no Dockerfile)
- Spring components runtime reachable: NO (not on classpath)

## CI and Developer Workflow

Canonical commands no longer need exclusions:

```bash
./gradlew compileJava
./gradlew compileTestJava
./gradlew assemble
./gradlew test
```

HOLD diagnostic workflow:

```bash
./gradlew -PincludeHoldModules=true projects
./gradlew -PincludeHoldModules=true :spring-ai-adapter:compileJava
```

## Architecture Guard

Added 3 new checks to `scripts/check-architecture-drift.sh`:

1. spring-ai-adapter HOLD mechanism present in settings.gradle.kts
2. platform-app does not depend on spring-ai-adapter
3. Spring AI mainline approval remains NOT_FOUND

Check count: 27 → 30

## Known HOLD Debt

- TenantLitellmKeyService cross-module reference (exists in platform-app, unreachable from adapter)
- Three compilation errors in TenantAwareLitellmChatProvider.java
- Zero adapter tests
- Inactive configuration (application-litellm.yml profile)
- No mainline approval

## No-Repair Declaration

TenantLitellmKeyService was not copied, moved, implemented, or stubbed.

spring-ai-adapter production source was not repaired.

No Spring AI runtime dependency or behavior was introduced.

## Current Platform Truth

Backend capability expansion remains paused.

Frontend feature development remains frozen.

Dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

Spring ApplicationContext, runtime Bean registration, MVC mappings, FFmpeg provider registration, and RenderJob lifecycle remain unverified.

## Recommended Next Step

Proceed to BACKEND-INTEGRITY-RUNTIME-CONTEXT-VALIDATION.0.

Validate only the active core application graph.

Do not opt into spring-ai-adapter during runtime validation.
