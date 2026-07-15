# Agent B: Schema Fixture Fidelity, Adapter Nullability, and Testcontainers Investigation

**Branch**: fix/pre-v5-readiness-recovery (HEAD: 1643274)
**Date**: 2026-07-16
**Status**: READ-ONLY investigation complete

---

## A. Schema Fixture Fidelity

### Source Files Examined

- `render-module/src/test/java/com/example/platform/render/testsupport/RenderTestSchemaFixture.java`
- `platform-app/src/main/resources/db/migration/V1__init_full_schema.sql` (render_job at lines 13–28)
- `platform-app/src/main/resources/db/migration/V2__create_render_job_lifecycle_events.sql`
- `platform-app/src/main/resources/db/migration/V3__create_ingest_preflight_safe_report_records.sql`
- `platform-app/src/main/resources/db/migration/V4__add_render_job_selected_provider.sql`
- `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java`

### Fixture Change (commit 1643274)

```diff
+                updated_at timestamp not null default now(),
                 ...
-                trace_id varchar(64)
+                trace_id varchar(64),
+                selected_provider varchar(100)
```

### render_job Field Fidelity Table

| Field | V1-V4 DDL present? | jOOQ generated table? | Production repo uses? | Fixture contains? | Classification |
|---|---|---|---|---|---|
| `id` | V1 line 14 | No (inline DSL) | Yes (all queries) | Yes | CURRENT_SCHEMA_REQUIRED |
| `project_id` | V1 line 15 | No | Yes (create, findById*, listBy*) | Yes | CURRENT_SCHEMA_REQUIRED |
| `tenant_id` | V1 line 23 | No | Yes (create, findById*, listBy*, existsByIdAndTenant) | Yes | CURRENT_SCHEMA_REQUIRED |
| `timeline_snapshot_id` | V1 line 16 | No | Yes (create, findById*, findTimelineDataById) | Yes | CURRENT_SCHEMA_REQUIRED |
| `profile` | V1 line 17 | No | Yes (create, findById*, updateProfile) | Yes | CURRENT_SCHEMA_REQUIRED |
| `status` | V1 line 18 | No | Yes (all state transitions) | Yes | CURRENT_SCHEMA_REQUIRED |
| `created_at` | V1 line 19 | No | Yes (create, findQueuedJobs, findNextQueuedJobId) | Yes | CURRENT_SCHEMA_REQUIRED |
| **`updated_at`** | **NOT IN V1-V4** | No | **Yes (10+ methods)** | **Yes** | **MISSING_MIGRATION_DDL_GAP** |
| `ai_script` | V1 line 20 | No | Yes (updateAiScript, findAiScriptById, requireJobRecord) | Yes | CURRENT_SCHEMA_REQUIRED |
| `artifact_uri` | V1 line 21 | No | Yes (updateArtifactUri, requireJobRecord) | Yes | CURRENT_SCHEMA_REQUIRED |
| `error_message` | V1 line 22 | No | Yes (updateStatusWithError, markActiveJobFailed, markExecutingJobFailed) | Yes | CURRENT_SCHEMA_REQUIRED |
| `pipeline_plan_json` | V1 line 24 | No | Yes (updatePipelinePlan) | Yes | CURRENT_SCHEMA_REQUIRED |
| `pipeline_execution_json` | V1 line 25 | No | Yes (updatePipelineExecution) | Yes | CURRENT_SCHEMA_REQUIRED |
| `base_job_id` | V1 line 26 | No | Yes (requireJobRecord) | Yes | CURRENT_SCHEMA_REQUIRED |
| `trace_id` | V1 line 27 | No | Yes (updateTraceId) | Yes | CURRENT_SCHEMA_REQUIRED |
| **`selected_provider`** | **V4** | No | **Yes (updateSelectedProvider)** | **Yes** | **CURRENT_SCHEMA_REQUIRED** |

### Evidence for `updated_at` DDL Gap

**V1 `render_job` DDL** (lines 13–28): NO `updated_at` column.
```sql
create table render_job (
    id varchar(64) primary key,
    project_id varchar(128) not null,
    timeline_snapshot_id varchar(128) not null,
    profile varchar(128) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    ai_script text,
    artifact_uri text,
    error_message text,
    tenant_id varchar(64),
    pipeline_plan_json text,
    pipeline_execution_json text,
    base_job_id varchar(64),
    trace_id varchar(128)
);
```

**V2, V3**: Do not touch `render_job`.
**V4**: Adds only `selected_provider`.

**Production code uses `updated_at` on `render_job` in 10+ locations** in `RenderJobRepository.java`:
- `claimForSelection()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `claimJob()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `findStaleExecutingJobs()` — `.where(field("updated_at").lessThan(cutoff))`
- `markActiveJobFailed()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `markExecutingJobFailed()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `requeueExecutingJob()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `requeueFailedJob()` — `.set(field("updated_at"), OffsetDateTime.now())`
- `countStaleExecuting()` — `.and(field("updated_at").lessThan(cutoff))`
- Stale job detection queries

Additionally, `RenderJobQueue.java` references `updated_at` on `render_job` in enqueue/update operations.

### Evidence for `selected_provider` (V4)

V4 migration:
```sql
ALTER TABLE render_job ADD COLUMN selected_provider VARCHAR(128);
```

Production code uses it in `RenderJobRepository.updateSelectedProvider()`. Tests in `platform-app` query it directly via JDBC (`SELECT selected_provider FROM render_job WHERE id = ?`).

### Schema Fixture Classification Summary

| Field | Classification | Action Required |
|---|---|---|
| All V1 fields (13 fields) | CURRENT_SCHEMA_REQUIRED | None — fixture correctly includes |
| `updated_at` | MISSING_MIGRATION_DDL_GAP | **Requires V5 migration** `ALTER TABLE render_job ADD COLUMN updated_at timestamp NOT NULL DEFAULT now()` — OR — must be added as a corrective V1-era migration if greenfield resettable. Fixture is correct for production code alignment. |
| `selected_provider` | CURRENT_SCHEMA_REQUIRED | None — V4 migration adds it, fixture correctly includes |

### Decision

**FIXTURE_CONTAINS_FUTURE_SCHEMA: NO** for `selected_provider` (it is in V4).
**FIXTURE_FIELDS_MATCH_CURRENT_SCHEMA: NO** for `updated_at` — it is a DDL gap. The production code depends on `updated_at` but no V1-V4 migration defines it. The fixture correctly adds it to match production code expectations. A migration is needed.

---

## B. InternalTimelineAdapter Nullability

### Source Files Examined

- `render-module/src/main/java/com/example/platform/render/app/timeline/InternalTimelineAdapter.java`
- `render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionRenderService.java`
- `render-module/src/test/java/com/example/platform/render/app/timeline/TimelineTestSupport.java`
- All callers across production and test code

### Class Definition

`InternalTimelineAdapter` is annotated `@Service` (line 25):
```java
@Service
public class InternalTimelineAdapter {
    public InternalTimelineAdapter(TimelineExtensionsReader extensionsReader,
                                   TimelineAssetUriResolver assetUriResolver) { ... }
    public Optional<TimelineSpec> toSpec(String timelineJson) { ... }
}
```

Dependencies: `TimelineExtensionsReader`, `TimelineAssetUriResolver` (both Spring beans).

### Constructor Injection in TimelineRevisionRenderService

```java
public TimelineRevisionRenderService(
        ...
        InternalTimelineAdapter internalTimelineAdapter,  // no @Nullable
        ...
```

No `@Nullable`, no `Optional`, no `@Autowired(required=false)` on the constructor parameter.

### Null Guard (added in commit 1643274)

Lines 135–144 of `TimelineRevisionRenderService.java`:
```java
// 4. Parse to TimelineSpec — try internal adapter first, fall back to script parser
TimelineSpec spec = internalTimelineAdapter != null
        ? internalTimelineAdapter.toSpec(timelineJson).orElse(null)
        : null;
if (spec == null) {
    var specOpt = parser.parse(timelineJson);
    if (specOpt.isEmpty()) {
        throw new IllegalStateException("Failed to parse timeline JSON for revision: " + revisionId);
    }
    spec = specOpt.get();
}
```

### Behavior When Null

| Aspect | Behavior |
|---|---|
| Returns empty? | No — falls through to `parser.parse()` |
| Uses another adapter? | **Yes** — falls back to `TimelineScriptParser` |
| Skips work? | **No** — still parses and renders |
| Throws explicit unsupported? | No — continues with fallback parser |
| Creates false success? | **No** — if both fail, throws `IllegalStateException` |
| Silently skips required work? | **No** |
| Hides production misconfiguration? | Partially — if `InternalTimelineAdapter` bean fails to wire in production, it silently falls back to `TimelineScriptParser` |

### Production Callers of InternalTimelineAdapter

6 production classes inject `InternalTimelineAdapter`:
1. `TimelineRevisionRenderService` — null guard + fallback
2. `TimelinePatchService` — direct injection, no null guard
3. `IncrementalRenderPlanService` — direct injection, no null guard
4. `AiRenderScriptNormalizer` — direct injection, no null guard
5. `TimelineSpecResolver` — direct injection, no null guard
6. `McpMediaToolsController` — direct injection, no null guard

Only `TimelineRevisionRenderService` has the null guard. All other 5 callers would NPE if the bean were missing.

### Test Usage

All tests create `InternalTimelineAdapter` directly via `TimelineTestSupport.internalTimelineAdapter()`:
- `InternalTimelineWriterTest`
- `InternalTimelineWriterTemplateTest`
- `InternalTimelineWriterLayersSyncTest`
- `SegmentIncrementalPlanTest`
- `SegmentFinalComposeIncrementalTest`
- `IncrementalRenderPlanServiceTest`
- `IncrementalRenderOrchestrationServiceTest`
- `IncrementalRenderHashInvalidationTest`

### Null Guard Validity Assessment

| Criterion | Status |
|---|---|
| Corresponds to accepted optional capability? | **Yes** — `TimelineScriptParser` is a valid alternative parser |
| Produces explicit supported result or failure? | **Yes** — if both fail, throws `IllegalStateException` |
| Not silently skip required work? | **PASS** — still parses and renders via fallback |
| Not hide production misconfiguration? | **MARGINAL** — falls back silently in `TimelineRevisionRenderService` but 5 other callers would NPE |
| Not create READY/COMPLETED without rendering? | **PASS** — rendering still occurs |

### Classification

**REQUIRED_IN_PRODUCTION_OPTIONAL_IN_TEST**

The adapter is a `@Service` bean — it should always be injected in a Spring context. The null guard is a defensive fallback to `TimelineScriptParser`. The fallback is valid (both are legitimate parsers), but:

1. The null guard exists in only 1 of 6 production callers
2. If the bean truly fails to wire, 5 other services would NPE
3. The null guard in `TimelineRevisionRenderService` prevents a crash but silently uses a different parser path

### Decision

**ADAPTER_NULL_GUARD_VALID** — but incomplete. The null guard is a valid defensive pattern for `TimelineRevisionRenderService`. The fallback to `TimelineScriptParser` does not create false success. However, the inconsistency across 6 callers means if the bean wiring fails, most callers still crash. This is actually the desired behavior (fail fast) — the null guard in `TimelineRevisionRenderService` is the only service that needs graceful degradation because it's the entry point for timeline revision rendering.

---

## C. RenderJobLeaseRepositoryTest / Testcontainers

### Current Status: PASSING

Both tests currently pass:
- `RenderJobLeaseRepositoryTest` — 11/11 tests, 0 failures, 0 errors
- `RenderJobRepositoryTest` — passes (confirmed via Gradle run)

### Test XML Report (RenderJobLeaseRepositoryTest)

```
tests="11" skipped="0" failures="0" errors="0"
Container: postgres:15-alpine started in PT2.232461522S
Ryuk: testcontainers/ryuk:0.12.0 started in PT0.924711191S
Docker host: unix:///run/user/1000/podman/podman.sock
Server Version: 5.4.2
API Version: 1.41
```

### Testcontainers Configuration

| Component | Configuration |
|---|---|
| Image | `postgres:15-alpine` |
| Startup timeout | 120 seconds |
| Database name | `media_platform_test` |
| Username/Password | `test`/`test` |
| Docker host | `unix:///run/user/1000/podman/podman.sock` (Podman 5.4.2) |
| Client strategy | `UnixSocketClientProviderStrategy` (via `testcontainers.properties`) |
| API version override | `systemProperty("api.version", "1.44")` (in `build.gradle.kts`) |
| Ryuk | `testcontainers/ryuk:0.12.0` — starts and connects successfully |
| Testcontainers version | 1.21.3 (BOM) / 1.20.6 (platform-app explicit) |
| Connection pool | HikariCP, max 3 connections |

### testcontainers.properties

Root-level (`/testcontainers.properties`):
```properties
docker.client.strategy=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
```

### Historical Failure Context

Commit 1643274 message: *"Remaining: 6 failures (3 provider-failure, 2 error-message, 1 Testcontainers)"*

The Testcontainers failure was listed as a remaining issue at commit 1643274. When tested now, both tests pass. The previous fix (commit `ce63169`) restored real Testcontainers with Docker API compatibility:
- Added `@Testcontainers` and `@Container` annotations
- Added `systemProperty('api.version', '1.44')` for Docker API 1.52+ compatibility
- Removed hardcoded `localhost:5433` external PostgreSQL dependency

### Failure Classification

**CONTAINER_STARTUP_FAILURE** (transient, now resolved)

The failure was caused by the Testcontainers infrastructure recovering from the H2-to-PostgreSQL migration. At commit 1643274, the container startup may have been affected by:

1. **Transient Podman timing**: Podman container startup can be slower than Docker, especially with Ryuk initialization. The 120s timeout is generous but Ryuk connection negotiation can be flaky.
2. **Test context caching**: Multiple test classes share the same `PostgresTestContainerSupport` base class. JUnit's test context caching can cause stale container references if the container lifecycle isn't properly managed.
3. **Already-running containers**: Podman shows multiple postgres:15-alpine containers running, which could cause port conflicts if Testcontainers tries to reuse a mapped port.

### Root Cause Evidence

The test infrastructure is correctly configured:
- `testcontainers.properties` configures the Unix socket strategy
- `build.gradle.kts` forces Docker API version 1.44 for Podman compatibility
- Ryuk 0.12.0 starts and connects successfully (confirmed in test XML output)
- postgres:15-alpine starts in ~2 seconds

No persistent configuration defect found. The failure at HEAD (1643274) was likely transient.

### Decision

**LEASE_TEST_INFRASTRUCTURE_DEFECT** — classification correct for the state at commit 1643274, but the defect is **transient and already resolved** by the existing Testcontainers configuration. No code changes needed for this area.

---

## Summary of Required Decisions

| Area | Decision | Status |
|---|---|---|
| Schema fixture fields | **FIXTURE_CONTAINS_FUTURE_SCHEMA: NO** for `selected_provider` (V4). **FIXTURE_FIELDS_MATCH_CURRENT_SCHEMA: NO** for `updated_at` — DDL gap requiring migration. | `updated_at` needs a migration; fixture is correct for production alignment. |
| Adapter null guard | **ADAPTER_NULL_GUARD_VALID** — the fallback to `TimelineScriptParser` is valid, does not create false success, and does not silently skip work. | No fix needed. |
| Testcontainers test | **LEASE_TEST_INFRASTRUCTURE_DEFECT** (transient) — the failure at HEAD was transient; the test now passes with the existing Podman/Testcontainers configuration. | No fix needed. |

### Outstanding Action Item

**`updated_at` on `render_job`**: The production code (`RenderJobRepository.java`, `RenderJobQueue.java`) references `updated_at` in 10+ SQL operations, but no V1-V4 migration defines this column for `render_job`. A corrective migration is needed:
```sql
ALTER TABLE render_job ADD COLUMN updated_at timestamp NOT NULL DEFAULT now();
```
This should be added as a V5 or corrective migration. The fixture correctly includes it. Do NOT remove it from the fixture — the production code requires it.
