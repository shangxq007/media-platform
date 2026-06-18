---
status: current
last_verified: 2026-06-18
scope: all
truth_level: implemented
owner: platform
---

# Persistence & Restart Semantics

> **Generated**: 2026-05-08T08:47Z
> **Updated**: 2026-06-18
> **Scope**: Documents which business state survives an application restart and which is ephemeral.
> **Note**: PostgreSQL only. H2 is no longer supported.

---

## States That Survive Restart (Persistent)

After Phase T2, the following data is stored in the database (PostgreSQL) and **will survive** an application restart:

| Data | Table | Repository | Module |
|------|-------|------------|--------|
| **Tenant** | `tenant` | `TenantRepository` | `identity-access-module` |
| **Project** | `project` | `ProjectRepository` | `identity-access-module` |
| **User** | `"user"` | `UserRepository` | `identity-access-module` |
| **API Key** (hash + fingerprint only) | `api_key` | `ApiKeyRepository` | `identity-access-module` |
| **RenderJob** | `render_job` | `RenderJobService` (jOOQ) | `render-module` |
| **Artifact metadata** | `artifact` | `ArtifactRepository` | `storage-module` |
| **Notification delivery record** | `notification_record` | `NotificationDeliveryRepository` | `notification-module` |
| **Quota usage** | `quota_usage` | `QuotaUsageRepository` | `render-module` |
| **Audit record** | `audit_records` | `AuditService` (jOOQ) | `audit-compliance-module` |
| **Outbox event** | `outbox_events` | `OutboxEventService` (jOOQ) | `outbox-event-module` |
| **Notification event** | `notification_event` | `NotificationEventHandler` (jOOQ) | `notification-module` |
| **Notification delivery** | `notification_delivery` | `NotificationEventHandler` (jOOQ) | `notification-module` |

### Key Properties

- **API Keys**: Only the SHA-256 hash and fingerprint are stored. Plaintext keys are returned once at creation time and never persisted.
- **Artifact binary data**: Stored on local filesystem (via `LocalFsBlobStorage`). Only metadata (storage URI, format, resolution, duration) is persisted in the database.
- **Quota usage**: Accumulated usage counters are persisted and will continue from the last value after restart.

---

## States That Do NOT Survive Restart (Ephemeral / Mock)

The following are intentionally in-memory only and will be lost on restart. This is acceptable because they are mock/test artifacts:

| Data | Location | Reason |
|------|----------|--------|
| **Mock notification in-memory list** | `MockNotificationProvider.clear()` is now a no-op | Delivery records are persisted to `notification_record`; the in-memory list was redundant |
| **Config cache** | `ConfigService` | Config items are persisted in `config_item` table; any in-memory caching is rebuildable |
| **MDC trace context** | `RequestContextFilter` / `TraceCorrelationFilter` | Request-scoped logging context, rebuilt per-request |

---

## States Still In-Memory (Known Gaps — Not Part of T2)

These remain in-memory but are **not** part of the T2 persistence scope. They are documented in `roo-gap-report.md` for future phases:

| Data | Location | Priority |
|------|----------|----------|
| **Checkout sessions** | `CheckoutOrchestrator` | P1-11 |
| **Billing projections** | `BillingProjectionService` | P1-11 |
| **Entitlement snapshots** | `EntitlementService` | P1-11 |
| **Feature flag state** | OpenFeature `InMemoryProvider` | P2 |

---

## Restart Behavior Summary

### Normal Restart (Graceful Shutdown → Start)
1. **Flyway** runs migrations on startup, ensuring schema is up to date.
2. All persistent state is loaded from the database on demand.
3. API keys are available immediately (hashed, no bootstrap needed).
4. Quota usage continues from the last persisted value.
5. Artifact metadata is available for lookup.
6. Audit and outbox history is preserved.

### Cold Start (Fresh Database)
1. **Flyway** creates all tables from V1–V8 migrations.
2. No tenants, projects, users, or API keys exist.
3. The `IdentityProperties` bootstrap keys are loaded into the `api_key` table on first access.
4. All quota usage starts at 0.

### Test Profile (`@ActiveProfiles("test")`)
1. H2 in-memory database is used.
2. Flyway migrations run on test context startup.
3. Data is isolated per test (H2 `DB_CLOSE_DELAY=-1` keeps data for the duration of the JVM).
4. The `DataBootstrap` runner is excluded via `@Profile("!test")`.
