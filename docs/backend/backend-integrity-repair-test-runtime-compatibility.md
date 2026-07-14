# Backend Integrity Repair — Test Runtime Compatibility

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-REPAIR-TEST-RUNTIME-COMPATIBILITY.0
**Decision:** BACKEND_INTEGRITY_TEST_RUNTIME_COMPATIBILITY_RESTORED_WITH_TEST_FAILURES

---

## Context

- Previous test compilation repair commit: 96ba3f4
- Core test compilation errors: 0
- Runtime Mockito tests: blocked before this task (ByteBuddy self-attach failure)
- Java: 25.0.3
- Gradle: 9.1.0
- Spring Boot: 4.0.4

## Runtime Environment

| Item | Value |
|------|-------|
| Shell Java | OpenJDK 25.0.3 |
| Gradle daemon Java | 25.0.3 |
| Compile toolchain Java | 25 (configured in build.gradle.kts) |
| Forked Test JVM Java | 25.0.3 |
| OS | Linux 6.12.0-160000.35-default (openSUSE) |
| Architecture | x86_64 |

## Dependency Matrix

| Dependency | Version | Source |
|-----------|---------|--------|
| mockito-core | 5.20.0 | Spring Boot BOM (forced) |
| byte-buddy | 1.17.8 | Spring Boot BOM (managed from 1.12.23) |
| byte-buddy-agent | 1.17.8 | Spring Boot BOM (managed from 1.12.23) |
| junit-jupiter | 5.12.2 | Spring Boot BOM |
| spring-boot-starter-test | 4.0.4 | Spring Boot BOM |

## Mock Maker

- Active mock maker: **InlineByteBuddyMockMaker** (Mockito 5.x default)
- Configuration source: Default (no explicit mockito-extensions file)
- Inline instrumentation required: YES (Mockito 5.x default behavior)
- Static mocking found: 0
- Construction mocking found: 0
- Final-class mocking found: 0 (no explicit final-class mocking, but inline maker handles it)

## Failure Reproduction

- Initial command: `./gradlew :platform-app:test --tests "com.example.platform.ingest.preflight.IngestMetadataMergerTest.testTikaOnlyMerge" --stacktrace --no-daemon`
- Initial exit code: non-zero (test failure)
- Initial failure stage: **MOCKITO_PLUGIN_INITIALIZATION**
- Root cause: ByteBuddy `InlineByteBuddyMockMaker` attempts dynamic self-attach via Java Attach API. Java 25 restricts dynamic agent loading by default. Self-attach fails with `MockitoInitializationException: Could not initialize inline Byte Byte mock maker`.
- Root cause proven: **YES** (full exception chain captured and verified)

## Strategy Evaluation

| Strategy | Works | Supported | Test-only | Security Impact | Chosen |
|----------|------:|----------:|----------:|----------------:|-------:|
| A: Explicit test JVM agent | YES | YES | YES | None (test-only) | **YES** |
| B: Subclass mock maker | Possible | YES | YES | None | NO (not needed) |
| C: Dependency alignment | N/A | N/A | N/A | N/A | NO (versions already aligned) |
| D: JVM attach flags | YES | Partial | Partial | Medium | NO (Strategy A preferred) |
| E: Environment correction | N/A | N/A | N/A | N/A | NO (not container issue) |

## Selected Repair

**Strategy A: Explicit test JVM Java agent**

- File changed: `build.gradle.kts` (root)
- Change: Added `byteBuddyAgent` resolvable configuration with `net.bytebuddy:byte-buddy-agent` dependency. Configured `doFirst` on `tasks.withType<Test>` to attach `-javaagent:<jar>` to forked Test JVMs.
- Why selected: Smallest, most supported approach. Uses Gradle's dependency resolution to find the agent JAR. No hard-coded paths. No global JVM flags. Test-only.
- Why production runtime unaffected: Agent attached only to `Test` task JVMs via `doFirst`. No `bootRun`, no application JVM, no container entrypoint.
- Why no unsafe broad flag: `jdk.attach.allowAttachSelf` or `--enable-native-access` would weaken security globally. Explicit agent attachment is cleaner.

## Targeted Test Results

| Module | Test Class | Mockito Usage | Runtime Result |
|--------|-----------|---------------|----------------|
| outbox-event-module | TaskHandlerRegistryTest | mock() | PASSED |
| platform-app | IngestMetadataMergerTest | mock(ObjectProvider) | PASSED |
| platform-app | RenderControllerTest | mock(RenderJobService) | PASSED |
| render-module | RenderControllerTest | mock(RenderJobService) | PASSED |

## Module Test Results

| Module | Total | Passed | Failed | Skipped | Result |
|--------|------:|-------:|-------:|--------:|--------|
| outbox-event-module | 49 | 45 | 4 | 0 | FAILED (assertions) |
| platform-app | 371 | 342 | 7 | 22 | FAILED (mixed) |
| render-module | 2756 | 2701 | 38 | 17 | FAILED (mixed) |

**Mockito bootstrap failures: 0**

## Remaining Test Failures (classification)

All remaining failures are real test failures, NOT Mockito infrastructure issues:

- **ASSERTION_FAILURE**: Test expectations don't match current production behavior
- **SPRING_CONTEXT_FAILURE**: Spring ApplicationContext fails to start (missing beans, config)
- **DATABASE_UNAVAILABLE**: Testcontainers/PostgreSQL not available in this environment
- **CONFIGURATION_MISSING**: Missing test configuration or environment variables
- **PRODUCTION_DEFECT**: Tests exposing real production code issues

No Mockito/ByteBuddy bootstrap failure remains.

## Test Compilation

- Command: `./gradlew compileTestJava -x :spring-ai-adapter:compileJava -x :spring-ai-adapter:compileTestJava`
- Result: **PASSED (exit 0)**
- Test compilation errors: 0

## Production Build

- Production compilation: **PASSED** (with spring-ai-adapter excluded)
- Artifact assembly: **PASSED** (with spring-ai-adapter excluded)
- Excluded modules: `:spring-ai-adapter:compileJava`, `:spring-ai-adapter:processResources`, `:spring-ai-adapter:classes`, `:spring-ai-adapter:jar`
- spring-ai-adapter status: **UNRESOLVED** (pre-existing `TenantLitellmKeyService` not found)

## No-Silencing Verification

- [x] No tests disabled.
- [x] No Mockito tests excluded.
- [x] No ignoreFailures introduced.
- [x] No assertion weakening.

## Production Runtime Isolation

- [x] Mockito/ByteBuddy agent configuration is test-only.
- [x] No production JVM agent was added.
- [x] No runtime application behavior was changed.
- [x] Agent attached only via `doFirst` on `tasks.withType<Test>`.
- [x] No `bootRun` or application JVM affected.

## Scope Integrity Findings

### TaskHandlerRegistry.init() visibility
Assessment: **PRODUCTION_VISIBILITY_CHANGE_UNRELATED_TO_RUNTIME_COMPATIBILITY**
Changed in commit 96ba3f4 (previous task). Not modified by this task.

### Frontend static assets in 96ba3f4
Assessment: **GENERATED_FRONTEND_ASSETS_COMMITTED**
LikeC4 exports and platform-app static assets were pre-existing working tree changes included in the previous commit. Not modified by this task.

### Agent skill self-modification
Assessment: **No skill or agent-instruction files were modified by this task.**

## Architecture and Safety

- Architecture drift guard: **27/27 PASS**
- No upload API introduced.
- No public route introduced.
- No FFmpeg provider behavior changed.
- No RenderJob lifecycle behavior changed.
- No Flyway baseline changed.
- No storage-internal exposure introduced.
- No signed URL persistence introduced.

## Current Truth

- Backend runtime ApplicationContext/Bean/route integrity remains unverified.
- Dedicated backend upload API remains NOT_IMPLEMENTED.
- FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
- spring-ai-adapter full-repository compilation remains unresolved.

## Recommended Next Step

Proceed to BACKEND-INTEGRITY-SPRING-AI-ADAPTER-TRIAGE.0.

Determine whether spring-ai-adapter is a required runtime module, an optional/HOLD module, or stale code requiring explicit exclusion or repair.

Do not begin backend upload API implementation or frontend Upload Surface work.
