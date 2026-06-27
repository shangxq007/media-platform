---
status: current
last_verified: 2026-06-22
scope: preview
truth_level: implemented
owner: platform
---

# Current System State

> **Last validated:** 2026-06-22 (Source of Truth Validation)

## 1. Validated Startup Profiles

### dev-postgres,preview

| Metric | Value | Status |
|--------|-------|--------|
| Startup Time | ~30 seconds | ✅ |
| Flyway Migration | All applied | ✅ |
| Recurring Errors | 0 | ✅ |
| Endpoints Accessible | All smoke-tested | ✅ |

**Active configuration:**
- Security: permit-all (no auth required)
- Database: PostgreSQL via Docker
- Disabled modules: AI, workflow, scheduler, worker, payment, commerce, sandbox, cloud-resource, social-publish
- Outbox dispatcher: disabled
- Scheduling: disabled

### prod,safe-mode

| Metric | Value | Status |
|--------|-------|--------|
| Startup Time | 19.108 seconds | ✅ |
| Security Bean Wiring | Fixed | ✅ |
| Flyway Migration | All applied | ✅ |
| Post-startup stability | Known issue | ⚠️ |

**Active configuration:**
- Security: JWT auth (OAuth2 disabled)
- Feature flags: safe-mode=true, all experimental features off
- Render providers: FFmpeg + Libass only
- Pipeline DAG: enabled (parallel-external disabled)
- Production checks: enabled

### test

| Metric | Value | Status |
|--------|-------|--------|
| Test Suite | `:platform-app:test` | ✅ PASS |
| Tasks Executed | 75 | ✅ |

---

## 2. Database Status

| Attribute | Value |
|-----------|-------|
| Engine | PostgreSQL 16 (Docker: `postgres:16-alpine`) |
| Flyway Migrations | 1 version (V1 - consolidated baseline, 2339 lines) |
| Tables | 133 |
| Schema Location | `classpath:db/migration` |
| Test Schema | PostgreSQL via Testcontainers |
| H2 Support | ❌ Not supported |
| jOOQ Codegen | Not configured — all manual DSL |

### Key Tables

| Domain | Tables |
|--------|--------|
| Core | `render_job`, `notification_event`, `config_item` |
| Identity | `tenant`, `project`, `user`, `api_key`, `workspace` |
| Commerce | `commerce_product`, `checkout_session`, `purchase_order`, `payment_attempt` |
| Billing | `billing_invoice`, `subscription_contract`, `quota_usage` |
| Entitlement | `entitlement_grant`, `entitlement_override`, `feature_definition` |
| Operations | `outbox_events`, `audit_records`, `schedules` |
| Content | `artifact`, `timeline_snapshot`, `timeline_revision`, `prompt_template` |

### Schema Health

- `outbox_events` schema aligned with dispatcher leasing (`locked_at`, `locked_by`, `max_retries`)
- Indexes: ~40 indexes defined (V6 migration)
- Constraints: FK relationships validated

---

## 3. Security Status

### P0 Issues (Closed)

| Issue | Resolution |
|-------|-----------|
| NotificationController tenant isolation | Fixed — tenantId scoping added |
| X-User-Id header trust | Fixed — removed untrusted header usage |
| Duplicate exception handlers | Fixed — consolidated handler chains |
| MDC/logback fields | Fixed — traceId + requestId populated |

### P1 Issues (Open)

| Issue | Impact | Owner |
|-------|--------|-------|
| `StorageKeyPolicy` path traversal (substring check) | Potential path traversal bypass | security |
| `SafeDownloadUrlValidator` SSRF kill-switch (global mutable) | SSRF risk if toggled | security |
| `BillingUsageDataLoader` thread-safety (TenantContext swap) | Data leak across tenants | billing |
| `TenantGuard` silent fallback | Silent pass-through on null context | platform |

### Security Configuration Matrix

| Profile | Auth | OAuth2 | JWT | API Key | Dev Endpoints |
|---------|------|--------|-----|---------|---------------|
| `dev-postgres` | None | Off | Off | Off | Enabled |
| `preview` | None | Off | Off | Off | Disabled |
| `safe-mode` | JWT | Off | On | Off | Disabled |
| `prod` | Full | On | On | Configurable | Disabled |

---

## 4. Known Issues

### Critical (Blocks Production)

| Issue | Impact | Workaround |
|-------|--------|------------|
| `ProductionSafetyValidator` `NoUniqueBeanDefinitionException` | `prod,safe-mode` fails after startup | Use `prod` profile with OAuth2 configured |

### Medium

| Issue | Impact | Status |
|-------|--------|--------|
| `PrometheusMeterRegistry` tag mismatch | Warning log on startup | Non-blocking |
| Some API endpoints return 404 | Parameter name reflection issue | Non-blocking |
| Actuator `/info` endpoint empty | Not configured | Non-blocking |

### Low

| Issue | Impact |
|-------|--------|
| Dead Vue entry point (`frontend/src/main.ts`) | Cleanup debt |
| 3 TypeScript typecheck errors | Missing npm deps (`graphql-request`, `oidc-client-ts`) |
| Pre-existing `RenderNatronEffectsIT` failure | Requires Natron binary not installed |

---

## 5. Disabled/Isolated Modules

| Module | Mechanism | Profile |
|--------|-----------|---------|
| Spring AI | Isolated in `ai-module`, not in `platform-app` runtime path | All |
| GraphQL | Auto-config excluded | All |
| Outbox Dispatcher | `app.outbox.dispatcher-enabled: false` | preview |
| Scheduling | `spring.task.scheduling.enabled: false` | preview |
| Vault | `app.secrets.vault.enabled: false` | All (default) |
| Temporal | `TEMPORAL_ENABLED=false` | dev/test |

---

## 6. Endpoint Smoke Test Results (dev-postgres,preview)

| Endpoint | Status | Result |
|----------|--------|--------|
| `/actuator/health` | 200 | `{"status":"UP"}` |
| `/actuator/health/readiness` | 200 | `{"status":"UP"}` |
| `/v3/api-docs` | 200 | OpenAPI spec |
| `/swagger-ui/index.html` | 200 | Swagger UI |
| `/api/v1/render/jobs` | 200 | Accessible |
| `/api/v1/artifact/catalog/overview` | 200 | Accessible |

---

## References

- [Release Candidate Readiness Checklist](../../review/release-candidate-readiness-2026-06-17.md)
- [Platform Fact Gathering Report](../platform-fact-gathering-report.md)
- [Backend-first Stabilization Plan](../backend-first-stabilization-plan.md)
- [External Channel Extension Model](../blueprint/external-channel-extension-model.md) — Reserved extension points for external input/output channels
- [Storage Runtime Foundation](../../review/storage-runtime-foundation.md) — StorageReference locator semantics, R10B output key strategy
