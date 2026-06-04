# Runbook: End-to-End Render Flow

## Purpose

This runbook documents the first end-to-end business main chain (核心业务主链路) for the media platform. It covers the full flow from tenant creation through render job execution.

## Business Chain

```
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

## Prerequisites

- Java 25 (via asdf-vm or SDKMAN!)
- Gradle 9.1 (via wrapper)
- curl (for smoke script)
- Application running: `./gradlew :platform-app:bootRun`

## Quick Start

### 1. Start the Application

```bash
cd media-platform
./gradlew :platform-app:bootRun
```

Wait for the log line: `Started PlatformApplication in ... seconds`

### 2. Run the E2E Smoke Script

```bash
./scripts/smoke/e2e-render-flow.sh
```

Expected output: `E2E RENDER FLOW PASSED` with 13 checks all green.

### 3. Run Integration Tests

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` — all tests pass including `RenderFlowIntegrationTest`.

## API Walkthrough

### Step 1: Health Check

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected: `{"status":"UP"}`

### Step 2: Create Tenant

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "My Tenant"}' | jq .
```

Response:
```json
{
  "id": "ten_...",
  "name": "My Tenant",
  "status": "ACTIVE",
  "createdAt": "2026-05-08T..."
}
```

Save the `id` as `$TENANT_ID`.

### Step 3: Create Project

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants/$TENANT_ID/projects \
  -H "Content-Type: application/json" \
  -d '{"name": "My Project", "description": "Test project"}' | jq .
```

Save the `id` as `$PROJECT_ID`.

### Step 4: Create API Key

```bash
curl -s -X POST http://localhost:8080/api/v1/identity/tenants/$TENANT_ID/apikeys \
  -H "Content-Type: application/json" \
  -d '{"principal": "my-service"}' | jq .
```

Response includes `apiKey` (plaintext, only returned once) and `fingerprint`.

### Step 5: Create Render Job

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs \
  -H "Content-Type: application/json" \
  -d '{"projectId": "'$PROJECT_ID'", "timelineSnapshotId": "snap_001", "profile": "default_1080p"}' | jq .
```

Response:
```json
{
  "id": "rj_...",
  "projectId": "prj_...",
  "timelineSnapshotId": "snap_001",
  "profile": "default_1080p",
  "status": "QUEUED"
}
```

Save the `id` as `$JOB_ID`.

### Step 6: Query Render Job

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID | jq .
```

### Step 7: Execute Local Workflow

```bash
curl -s -X POST http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID/execute-local | jq .
```

This triggers the full orchestration chain:
1. Quota check
2. AI script generation (via Mock AI Provider)
3. Render (via Mock Render Provider)
4. Storage (via LocalFs Storage Provider)
5. Notification (via Mock Notification Provider)
6. Audit record

### Step 8: Query Execution Status

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/projects/$PROJECT_ID/render-jobs/$JOB_ID/execution | jq .
```

### Step 9: Query Quota

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/quota | jq .
```

### Step 10: Query Entitlements

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/entitlements | jq .
```

### Step 11: Query Notifications

```bash
curl -s http://localhost:8080/api/v1/tenants/$TENANT_ID/notifications | jq .
```

### Step 12: Query Audit Records

```bash
curl -s http://localhost:8080/api/v1/audit/compliance/overview | jq .
```

### Step 13: Query Outbox

```bash
curl -s http://localhost:8080/api/v1/outbox/overview | jq .
```

## Architecture Decisions

### Port Interfaces (ADR-002)

Cross-module service access uses port interfaces, not direct service references:

- `RenderOrchestratorPort` in `render.api.port` — used by `workflow-module`
- `NotificationEventPublisher` in `shared.notification` — cross-module SPI
- `AiGatewayPort` in `ai.api` — used by `render-module`

### Mock Providers

All external providers are mocked:

- `StubChatProvider` — deterministic mock AI script generation
- `MockRenderProvider` — simulates rendering with 200-1000ms delay
- `LocalFsStorageProvider` — filesystem-based storage in `./.data/storage`
- `MockNotificationProvider` — in-memory notification delivery

### Module Boundaries

- `render-module` depends on `ai-module`, `audit-compliance-module`, `storage-module` via `allowedDependencies`
- `workflow-module` depends on `render-module` via port interface (`RenderOrchestratorPort`)
- All modules use constructor injection (no `@Autowired`)

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` | App not running | Start with `./gradlew :platform-app:bootRun` |
| `404 Not Found` | Wrong API path | Check path matches `/api/v1/tenants/{tenantId}/...` |
| `401 Unauthorized` | API key auth enabled | Set `app.identity.api-key-auth-enabled=false` |
| `BUILD FAILED` | Module boundary violation | Check `allowedDependencies` in `package-info.java` |
| Test context load failure | Flyway timing | Ensure `@ActiveProfiles("test")` on test class |

## Related Documents

- `docs/roo-execution-log.md` — full execution history
- `docs/roo-gap-report.md` — gap analysis
- `docs/module-boundaries.md` — module dependency graph
- `docs/runbook-local.md` — local development runbook
