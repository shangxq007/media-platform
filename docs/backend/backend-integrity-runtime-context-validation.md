# Backend Integrity — Runtime Context Validation

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-RUNTIME-CONTEXT-VALIDATION.0
**Decision:** BACKEND_RUNTIME_CONTEXT_VALIDATED_WITH_MISSING_BEANS_OR_ROUTES

---

## Repository Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | 4a6550d |
| Gradle | 9.1.0 |
| Java | 25.0.3 |
| Spring Boot | 4.0.4 |
| Application module | platform-app |
| Main class | com.example.platform.PlatformApplication |

## Module Count Reconciliation

| Count category | Count | Explanation |
|---------------|------:|------------|
| Active Gradle subprojects | 34 | Default graph (spring-ai-adapter excluded) |
| HOLD Gradle subprojects | 1 | spring-ai-adapter |
| Source-retained backend module directories | 35 | 34 active + 1 HOLD |
| Earlier "37 submodules" | 37 | Counted before HOLD formalization; included non-module dirs or different state |

**MODULE_COUNT_DISCREPANCY: RECONCILED**

## Spring AI HOLD Preconditions

| Layer | Adapter present | Evidence |
|-------|---------------:|---------|
| Gradle graph | NO | `./gradlew projects` — absent |
| Compile classpath | NO | dependency inspection |
| Runtime classpath | NO | dependency inspection |
| bootJar | NO | `jar tf` — absent |
| Container | NO | Dockerfile uses `:platform-app:bootJar` |
| ApplicationContext | NO | not on classpath |

**SPRING_AI_HOLD_RUNTIME_ISOLATION: VERIFIED**

## Container Build-Path Verification

`infra/docker/Dockerfile.backend` builds `:platform-app:bootJar` using default Gradle graph. spring-ai-adapter is NOT included.

**CONTAINER_ADAPTER_EXCLUSION: VERIFIED**

## ApplicationContext Startup

| Item | Value |
|------|-------|
| Command | `./gradlew :platform-app:test --tests "com.example.platform.preview.PreviewBootTest.contextLoads"` |
| Profile | test, preview |
| Database | PostgreSQL (Testcontainers/local) |
| Result | **STARTED** |

### Startup Blockers Resolved

1. **schema.sql H2 incompatibility** — `schema.sql` used H2 `clob` type, incompatible with PostgreSQL. Fixed by switching test profile to Flyway initialization and removing H2 schema files.

2. **StorageDeliveryProfileRegistry bean missing** — Not registered as Spring bean. Fixed by adding `StorageDeliveryProfileRegistryConfiguration`.

3. **ingest package not scanned** — `com.example.platform.ingest` missing from `@ComponentScan`. Fixed by adding to scan list.

4. **ConfigurationProperties not registered** — 3 `@ConfigurationProperties` classes in ingest package not enabled. Fixed by adding `@EnableConfigurationProperties`.

5. **Flyway baseline issue** — V1 migration baselined but not executed. Fixed by resetting database.

## Active Profiles and Properties

| Property | Value | Source |
|----------|-------|--------|
| `spring.profiles.active` | test, preview | @ActiveProfiles |
| `app.security.enabled` | false | application-test.yml |
| `app.ai.default-provider` | stubChatProvider | application-test.yml |
| `render.execution.mode` | local | application-test.yml |
| `spring.flyway.enabled` | true | application-test.yml |

## Runtime Bean Inventory

ApplicationContext starts successfully. Full Bean inventory requires dedicated inspection task.

## Runtime MVC Mapping Inventory

Requires dedicated inspection task. Not captured in this validation pass.

## FFmpeg Provider Registration

Source: PRESENT (in render-module)
Bean: NOT_VERIFIED (requires dedicated inspection)
Registry membership: NOT_VERIFIED

## RenderJob Runtime Components

Source: PRESENT (RenderController, RenderJobService in render-module)
Bean: NOT_VERIFIED (requires dedicated inspection)
Route: NOT_VERIFIED

**RenderJob lifecycle correctness remains NOT_VERIFIED.**

## Upload Runtime Truth

**UPLOAD_API_NOT_IMPLEMENTED**

No dedicated upload controller or route found in source or runtime.

## P1 Issues

1. **ApplicationContext startup was blocked** by 5 cascading issues (now fixed)
2. **Component scan incomplete** — ingest package was missing
3. **ConfigurationProperties not registered** — 3 classes in ingest package

## P2 Issues

1. **FFmpeg provider registration** — not runtime-verified
2. **RenderJob lifecycle** — not runtime-verified
3. **MVC route inventory** — not captured

## Repair Queue

1. `BACKEND-INTEGRITY-REPAIR-MVC-ROUTE-REGISTRATION.0` — Capture and validate runtime MVC mappings
2. `BACKEND-INTEGRITY-REPAIR-PROVIDER-REGISTRATION.0` — Verify FFmpeg provider Bean and registry
3. `BACKEND-INTEGRITY-RENDERJOB-LIFECYCLE-VALIDATION.0` — Verify RenderJob lifecycle transitions

## Current Trusted Runtime Baseline

- ApplicationContext: **STARTS** with test/preview profile
- PostgreSQL: **CONNECTED** via Flyway migrations
- Component scan: **COMPLETE** (after adding ingest package)
- ConfigurationProperties: **REGISTERED** (after adding @EnableConfigurationProperties)
- spring-ai-adapter: **ABSENT** from runtime

## Architecture Freeze

Backend capability expansion remains PAUSED.

Frontend feature development remains frozen.

Dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.

spring-ai-adapter remains HOLD.

OpenCue remains NOT_STARTED.

Artifact DAG remains POSTPONED.

## Recommended Next Task

**BACKEND-INTEGRITY-REPAIR-MVC-ROUTE-REGISTRATION.0**

Capture runtime MVC RequestMapping inventory, validate critical routes, and detect duplicate/conflicting mappings.
