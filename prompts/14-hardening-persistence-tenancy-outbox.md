# Prompt 14: Hardening, Persistence, Tenancy, Outbox, and Runtime Readiness

## Purpose
Improve the already working end-to-end chain from demo-quality to maintainable, persistent, tenant-safe, and deployment-ready.

## Preconditions
- Prompt 13 has been executed.
- `./gradlew clean test` and `./gradlew :platform-app:bootJar` were successful.
- End-to-end RenderFlowIntegrationTest exists and passes.

## Global Goal
Do not expand random new features. Strengthen boundaries, persistence, tenant isolation, outbox reliability, state machine correctness, jOOQ workflow, Temporal readiness, real render tool readiness, and observability.

## Required Reports
Update:
- `docs/roo-execution-log.md`
- `docs/roo-gap-report.md`
- `docs/roo-final-report.md`
- `docs/deployment-resource-requirements.md`
- `docs/architecture-decisions.md`

---

## Phase T1: Modulith Boundary Refactoring

Subtask: `Modulith Boundary Refactoring Architect`

Tasks:
1. List all `@ApplicationModule(type = Type.OPEN)`.
2. List all `allowedDependencies`.
3. Decide which are justified and which are temporary bypasses.
4. Reduce inappropriate openness using module API packages, ports, and events.
5. Avoid render-module depending on internal AI/storage/audit implementations.
6. Document decisions in:
   - `docs/module-boundaries.md`
   - `docs/architecture-decisions.md`

Acceptance:
- `ModularityTest` passes.
- `Type.OPEN` count is reduced or each retained case is justified.
- `./gradlew test` passes.

---

## Phase T2: Persistence Hardening

Subtask: `Persistence Hardening Engineer`

Inventory whether these are persistent:
- tenant
- user
- service account
- api key
- project
- render job
- render job status history
- artifact metadata
- notification event
- notification delivery
- audit record
- outbox event
- quota usage
- prompt execution log

Tasks:
1. Update `docs/roo-gap-report.md` with memory-backed state.
2. Persist critical P0/P1 chain state:
   - RenderJob
   - Project
   - API Key hash/fingerprint
   - Artifact metadata
   - Notification delivery
   - AuditRecord
   - Quota usage
3. Add/fix Flyway migrations.
4. Add repository tests.
5. Document restart semantics in `docs/database-schema.md`.
6. Update deployment requirements for PostgreSQL schemas/tables/volumes.

Acceptance:
- Core state survives restart semantics.
- Remaining memory state is explicitly non-critical/mock.
- `./gradlew test` passes.

---

## Phase T3: Tenant Isolation and Security

Subtask: `Tenant Isolation and Security Engineer`

Check and fix:
- tenant A cannot query tenant B project
- tenant A cannot query tenant B render job
- tenant A cannot query tenant B artifact
- tenant A cannot query tenant B notification
- tenant A cannot query tenant B audit record
- API key is bound to tenant
- revoked API key is invalid immediately
- permission checks are not controller-only

Tasks:
1. Introduce/refine TenantContext / RequestContext.
2. Parse API key and tenant context at API edge.
3. Enforce tenantId in services and repository queries.
4. Use 404 or 403 consistently without leaking cross-tenant existence.
5. Add tests for cross-tenant isolation and auth failures.
6. Create/update `docs/security-and-tenancy.md`.

Acceptance:
- Tenant isolation tests pass.
- Critical queries include tenant constraints.
- ProblemDetail is used for auth failures.

---

## Phase T4: Outbox Reliability

Subtask: `Outbox Reliability Engineer`

Implement or improve:
- statuses: PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER
- idempotencyKey
- retryCount
- maxRetries
- nextAttemptAt
- lastErrorCode
- lastErrorMessage
- lockedAt
- lockedBy
- processOnce
- processBatch
- due-event retry
- max-retry dead letter
- handler idempotency

Handlers:
- render.job.completed -> notification
- render.job.failed -> notification
- artifact.created -> optional audit/log

Internal APIs:
- process once
- list failed
- retry event
- move dead letter

Tests:
- success -> PROCESSED
- failure -> retryCount increment
- max retries -> DEAD_LETTER
- duplicate idempotency key does not duplicate side effects
- restart pending event can process

Docs:
- `docs/outbox-reliability.md`

---

## Phase T5: Render State Machine and Compensation

Subtask: `Render State Machine Engineer`

Tasks:
1. Create/refine `RenderJobStateMachine`.
2. Route all status mutations through it.
3. Add status history model/table:
   - jobId
   - fromStatus
   - toStatus
   - reason
   - errorCode
   - occurredAt
4. Enforce failure paths:
   - AI failure -> FAILED
   - storage failure -> FAILED
   - render provider failure -> FAILED
   - quota exceeded -> FAILED or REJECTED
   - cancelled cannot execute
5. Add compensation for stale RUNNING jobs.
6. Add API for status history, cancel, retry if supported.
7. Create `docs/render-state-machine.md`.

Tests:
- legal transitions
- illegal transitions
- completed cannot regress
- failed retry strategy
- cancelled cannot execute
- stale compensation

---

## Phase T6: jOOQ and Database Workflow

Subtask: `Jooq and Database Workflow Engineer`

Tasks:
1. Inspect jOOQ Gradle config.
2. Add minimal generation config if absent.
3. Generate from Flyway-migrated schema where possible.
4. Document commands for migrate/generate/test.
5. Decide H2 vs PostgreSQL/Testcontainers risk.
6. Create `docs/jooq-workflow.md`.
7. Do not rewrite all repositories.
8. Optionally convert one low-risk repository as example.

Acceptance:
- jOOQ workflow is documented.
- Generation task works or missing preconditions are documented.
- `./gradlew test` passes.

---

## Phase T7: Temporal Integration Preparation

Subtask: `Temporal Integration Preparation Engineer`

Tasks:
1. Audit workflow-module and RenderOrchestratorService.
2. Separate workflow orchestration, activities, and domain services.
3. Introduce `RenderExecutionPort`.
4. Implement/keep `LocalRenderExecutionAdapter`.
5. Add `TemporalRenderExecutionAdapter` placeholder.
6. Avoid workflow directly using repositories or concrete feature flag providers.
7. Create `docs/temporal-integration-plan.md`.
8. Update deployment requirements with Temporal Server, namespace, task queue, workers, retry policies, timeouts, dev compose, production notes.

Acceptance:
- Local e2e still works without Temporal.
- Temporal can be added later without rewriting business services.

---

## Phase T8: Render Provider Realization Preparation

Subtask: `Render Provider Realization Preparation Engineer`

Tasks:
1. Refine RenderProvider SPI:
   - render(request)
   - supports(capability)
   - validateEnvironment()
2. Keep MockRenderProvider.
3. Add ProcessRenderProvider placeholder/dev-only provider:
   - configured executable path
   - command whitelist
   - timeout
   - stdout/stderr capture
   - exit-code mapping
4. Do not use `ProcessBuilder` in business modules.
5. If probing ffmpeg/melt, make tests conditional.
6. Add OpenTimelineIO timeline model or placeholder.
7. Create `docs/render-provider-integration.md`.
8. Update deployment requirements for binaries, fonts, codecs, temp dirs, CPU/GPU, storage.

Acceptance:
- Default tests do not require ffmpeg/melt.
- Process execution boundaries are safe and documented.

---

## Phase T9: Observability Implementation Preparation

Subtask: `Observability Implementation Engineer`

Tasks:
1. Add/propagate fields:
   - traceId
   - requestId
   - tenantId
   - projectId
   - jobId
   - workflowId
   - eventId
   - errorCode
2. HTTP filter/interceptor creates/accepts requestId.
3. Put requestId in MDC and response header.
4. Ensure structured JSON logging config.
5. Add metrics:
   - render job created/completed/failed
   - outbox processed/failed
   - notification sent/failed
   - API request timer if Micrometer exists
6. Add OpenTelemetry dependency/config placeholder without requiring a collector.
7. Optionally add otel/grafana compose draft.
8. Create `docs/observability.md`.

Tests:
- requestId filter
- metrics registration smoke
- `./gradlew test`

---

## Phase T10: Third Iteration Quality Gate

Subtask: `Third Iteration Release Gatekeeper`

Run:
- `git status`
- `grep -R "@Autowired" . --include="*.java" --exclude-dir=build --exclude-dir=.gradle`
- list all `Type.OPEN`
- `./gradlew clean test`
- `./gradlew :platform-app:bootJar`
- `docker compose config` if present
- infra validation if present

Check:
- e2e render flow still passes
- tenant isolation tests pass
- API key not plaintext
- outbox retry/dead letter
- centralized render state machine
- LocalFs path traversal test
- persistence restart semantics
- jOOQ workflow
- Temporal boundary
- real render process boundary
- OTel path

Update final reports.

## Acceptance Criteria
- Project is stronger without losing the working flow.
- Remaining mock/local/memory implementations are documented.
- Next iteration is clear.
