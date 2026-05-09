# Prompt 13: Functional Implementation Round

## Purpose
Move from P0 skeleton to the first working end-to-end business flow.

## Preconditions
- Project skeleton and P0 platform capabilities are complete.
- `.roo/rules/` exists and must be followed.
- Do not focus on empty interfaces or placeholder directories.

## Total Goal
Implement this usable chain:

```text
Create tenant/user/API key
  -> create project
  -> submit AI video render job
  -> check permission and quota
  -> create RenderJob
  -> write Outbox event
  -> execute simplified workflow/orchestrator
  -> call Mock AI Provider
  -> call Mock Render Provider
  -> save artifact metadata/placeholder with LocalFs Storage
  -> update RenderJob status
  -> write AuditRecord
  -> create NotificationEvent
  -> deliver through Mock Notification Provider
  -> query job, artifacts, events, audit records through APIs
```

## Global Constraints
- Do not connect real AI, real payment, real cloud, real email/SMS/push.
- Use mock/local/in-memory only where appropriate, but keep SPI boundaries ready.
- Do not break module boundaries.
- Do not push or deploy.
- Do not store API keys in plaintext.

## Required Reports
Continuously update:
- `docs/roo-execution-log.md`
- `docs/roo-gap-report.md`
- `docs/roo-final-report.md`
- `docs/deployment-resource-requirements.md`

---

## Phase F1: Functional Gap Audit

Subtask: `Functional Gap Auditor`

Check:
- platform-app
- shared-kernel
- identity-access-module
- quota-billing-module
- render-module
- workflow-module
- ai-module
- prompt-module
- storage-module
- artifact-catalog-module
- notification-module
- outbox-event-module
- audit-compliance-module
- datasource-module
- scheduler-module
- docs
- migrations
- OpenAPI/controllers
- ProblemDetail
- tests

Output new sections in `docs/roo-gap-report.md`:
- Functional Implementation Gap
- End-to-End Render Flow Gap
- API Gap
- Persistence Gap
- Test Gap

Do not perform large code changes.

---

## Phase F2: Identity, Tenant, API Key, Permission Loop

Subtask: `Identity Feature Implementer`

Implement:
- Tenant create/query
- User create/query
- ServiceAccount create/query
- API Key create/hash/fingerprint/revoke
- Permission/Role/AccessDecision minimal model
- PermissionChecker validating tenant, API key validity, permission

HTTP API:
- `POST /api/v1/tenants`
- `GET /api/v1/tenants/{tenantId}`
- `POST /api/v1/tenants/{tenantId}/users`
- `POST /api/v1/tenants/{tenantId}/service-accounts`
- `POST /api/v1/tenants/{tenantId}/api-keys`
- `POST /api/v1/api-keys/{fingerprint}/revoke`

Audit events:
- tenant.created
- user.created
- service_account.created
- api_key.created
- api_key.revoked

Tests:
- API key hash/verify
- permission checker
- controller tests
- repository tests if supported
- `./gradlew test`

---

## Phase F3: Project and RenderJob Domain

Subtask: `Render Domain Implementer`

Implement `Project`:
- projectId
- tenantId
- name
- status
- createdAt
- updatedAt

Implement `RenderJob`:
- jobId
- tenantId
- projectId
- promptId/promptText
- status
- priority
- input parameters
- output artifact references
- failure reason
- createdAt/updatedAt/startedAt/completedAt

Statuses:
- CREATED
- VALIDATING
- QUEUED
- RUNNING
- AI_GENERATING
- RENDERING
- STORING
- COMPLETED
- FAILED
- CANCELLED

Implement RenderJobService with create/get/list/status mutation/cancel/fail methods and transition validation.

HTTP API:
- `POST /api/v1/tenants/{tenantId}/projects`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}`
- `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs`

Outbox events:
- project.created
- render.job.created
- render.job.status_changed
- render.job.completed
- render.job.failed

Tests:
- status machine tests
- API tests
- outbox write tests
- audit write tests
- `./gradlew test`

---

## Phase F4: Prompt and Mock AI Provider

Subtask: `Prompt and AI Feature Implementer`

Implement:
- PromptTemplate
- PromptVersion
- PromptVariable
- PromptRenderRequest
- PromptRenderResult
- PromptExecutionLog
- PromptTemplateService

AI:
- AiProvider SPI
- AiRequest/AiResponse
- AiGenerationType
- AiProviderError
- AiProviderRegistry
- MockAiProvider with provider key `mock-ai`
- deterministic mock script output
- success/failure simulation without network access

Events:
- ai.generation.completed
- ai.generation.failed

HTTP API:
- `POST /api/v1/prompt-templates`
- `POST /api/v1/prompt-templates/{templateId}/versions`
- `POST /api/v1/prompt-templates/{templateId}/render`
- `POST /api/v1/ai/mock/generate-script` for dev/internal only

Tests:
- template rendering
- missing variables
- mock AI success/failure
- execution log
- `./gradlew test`

---

## Phase F5: LocalFs Storage and Artifact Catalog

Subtask: `Storage and Artifact Feature Implementer`

Storage:
- StorageProvider SPI
- LocalFsStorageProvider
- StorageObject
- StorageWriteRequest
- StorageReadRequest
- StorageLocation
- object key policy
- path traversal protection

Artifact catalog:
- Artifact
- ArtifactType
- ArtifactStatus
- ArtifactRelation
- ArtifactProvenance
- ArtifactService

Types:
- VIDEO_PLACEHOLDER
- SCRIPT
- THUMBNAIL_PLACEHOLDER
- SUBTITLE_PLACEHOLDER
- METADATA_JSON

HTTP API:
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/artifacts`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/artifacts/{artifactId}`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/artifacts`

Tests:
- local fs write/read
- path traversal protection
- artifact create/list
- render job artifact association
- `./gradlew test`

---

## Phase F6: Workflow / Activity Execution Loop

Subtask: `Render Workflow Implementer`

Implement local executable flow, using Temporal test infrastructure if available; otherwise use LocalWorkflowExecutor while preserving Temporal interfaces.

Workflow steps:
- validate request
- check quota
- generate AI script
- create script artifact
- mock render video placeholder
- create video artifact
- mark completed
- emit notification event

Activities:
- ValidateRenderRequestActivity
- CheckQuotaActivity
- GenerateScriptActivity
- StoreArtifactActivity
- MockRenderActivity
- CompleteRenderJobActivity
- FailRenderJobActivity

HTTP API:
- `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start`
- `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execute-local`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/execution`

Tests:
- successful local workflow
- AI failure -> FAILED
- storage failure -> FAILED
- cancelled job does not execute
- events emitted
- audit written
- `./gradlew test`

---

## Phase F7: Quota and Entitlement

Subtask: `Quota and Entitlement Feature Implementer`

Defaults:
- every tenant may use `render_video`
- every tenant has 10 render jobs/day by default
- every job costs 1 usage
- excess returns `QUOTA_EXCEEDED` ProblemDetail

Implement:
- Entitlement / FeatureAccess / EntitlementDecision / Explanation
- QuotaBucket / UsageRecord / QuotaPolicy / QuotaDecision
- CheckQuotaActivity integration

HTTP API:
- `GET /api/v1/tenants/{tenantId}/entitlements`
- `GET /api/v1/tenants/{tenantId}/quota`
- `GET /api/v1/tenants/{tenantId}/usage`
- `POST /api/v1/tenants/{tenantId}/quota/reset` dev/internal only

Tests:
- entitlement allow/deny
- quota allow/exceeded
- workflow quota exceeded
- `./gradlew test`

---

## Phase F8: Notification Loop

Subtask: `Notification Feature Implementer`

Implement:
- NotificationEvent
- NotificationTemplate
- NotificationChannel
- NotificationRecipient
- NotificationDelivery
- NotificationDeliveryStatus
- NotificationProvider SPI
- MockNotificationProvider
- NotificationService create/route/send/retry/query

Outbox integration:
- render.job.completed -> notification
- render.job.failed -> notification
- notification.event.published

HTTP API:
- `GET /api/v1/tenants/{tenantId}/notifications`
- `GET /api/v1/tenants/{tenantId}/notifications/{notificationId}`
- `GET /api/v1/tenants/{tenantId}/notifications/{notificationId}/deliveries`
- `POST /api/v1/tenants/{tenantId}/notifications/{notificationId}/retry`

Tests:
- render completed creates notification
- mock send success
- mock failure and retry
- delivery query
- `./gradlew test`

---

## Phase F9: Outbox Processor and Scheduler Compensation

Subtask: `Outbox Processor and Scheduler Implementer`

Implement:
- OutboxProcessor
- fetch pending events
- dispatch by eventType
- mark processed/failed
- retry/backoff
- idempotency protection
- EventHandler registry
- scheduler jobs for outbox scan, notification retry, stale render compensation

Internal/dev API:
- `POST /api/v1/internal/outbox/process-once`
- `GET /api/v1/internal/outbox/events`
- `POST /api/v1/internal/scheduler/run/{jobKey}`

Tests:
- pending event processed
- handler failure retries
- idempotency
- scheduler runs outbox once
- `./gradlew test`

---

## Phase F10: End-to-End API Smoke and Local Demo

Subtask: `End-to-End Smoke Implementer`

Create:
- `scripts/smoke/e2e-render-flow.sh`
- `docs/runbook-e2e-render-flow.md`

Smoke flow:
- health check
- create tenant
- create user/service account
- create api key
- create project
- create render job
- execute local workflow
- query job
- query artifacts
- query notifications
- query audit records if available
- query outbox if available

Tests:
- `./gradlew test`
- `./gradlew :platform-app:bootJar`
- optional bootRun + smoke

---

## Phase F11: Functional Quality Gate

Subtask: `Functional Release Gatekeeper`

Run:
- `git status`
- `./gradlew clean test`
- `./gradlew :platform-app:bootJar`
- `docker compose config` if present
- infra validation script if present

Check:
- e2e render flow works
- status machine tested
- API key not plaintext
- LocalFs path traversal test
- quota exceeded test
- outbox processing/retry
- mock notification query
- audit records
- ProblemDetail
- OpenAPI
- deployment requirements updated

Update `docs/roo-final-report.md`.

## Acceptance Criteria
- First usable flow is implemented and tested.
- Mock/local providers are replaceable.
- Future deployment resources are documented.
