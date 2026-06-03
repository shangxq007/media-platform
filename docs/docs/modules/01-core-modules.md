# Core Infrastructure Modules

> **Last Updated:** 2026-05-18

## shared-kernel

**Status:** ✅ Implemented

The only `ApplicationModule.Type.OPEN` module. Provides shared types, events, and cross-module SPIs.

| Category | Types |
|----------|-------|
| Error codes | `CommonErrorCode`, `ErrorCode`, `ErrorCodeRegistry` |
| Value objects | `Ids` (UUID generation with prefix), `Jsons` (Jackson wrapper) |
| Log context | `TraceKeys` (traceId, requestId, tenantId, projectId) |
| Base exceptions | `PlatformException` (RuntimeException with ErrorCode + details) |
| Domain events | `RenderJobCreatedEvent`, `RenderJobStatusChangedEvent`, `ArtifactCreatedEvent`, `RenderJobCompletedEvent`, `RenderJobFailedEvent` |
| Cross-module SPI | `NotificationEventPublisher` |

**Dependencies:** None (root of dependency graph)

## platform-app

**Status:** ✅ Implemented

Spring Boot application entry point. Aggregates all 30 modules.

| Component | Purpose |
|-----------|---------|
| `PlatformApplication` | `@Modulith` + `@SpringBootApplication` root |
| `ModularityTest` | Verifies module boundary constraints |
| `GlobalExceptionHandler` | Global exception → ProblemDetail mapping |
| `GlobalSentryExceptionHandler` | Sentry exception capture |
| `RequestContextFilter` | Request ID generation, MDC population |
| `ApiKeyAuthFilter` | API key authentication, TenantContext |
| OpenAPI config | springdoc OpenAPI 3 configuration |
| Security config | CORS, rate limiting |

**Dependencies:** All 29 other modules (flat)

## config-module

**Status:** ✅ Implemented

Versioned configuration storage and retrieval.

| Feature | Status |
|---------|--------|
| CRUD operations | ✅ |
| Versioning | ✅ |
| ConfigController | ✅ |

**Dependencies:** None

## secrets-config-module

**Status:** ✅ Implemented

Secret reference management.

| Feature | Status |
|---------|--------|
| Secret reference CRUD | ✅ |
| Secret resolution | ✅ |

**Dependencies:** `shared-kernel`

## datasource-module

**Status:** ✅ Implemented

Named DataSource and jOOQ DSLContext registry for multi-datasource support.

| Feature | Status |
|---------|--------|
| Named DataSource registry | ✅ |
| Named DSLContext registry | ✅ |
| Federated query gateway interface | ✅ |

**Dependencies:** `shared-kernel`

## identity-access-module

**Status:** ✅ Implemented

Identity and access management.

| Feature | Status |
|---------|--------|
| Tenant management | ✅ |
| User management | ✅ |
| API key management | ✅ |
| Project management | ✅ |
| ApiKeyAuthFilter | ✅ |

**Dependencies:** `shared-kernel`

## scheduler-module

**Status:** ✅ Implemented

Job scheduling with cron and manual triggers.

| Feature | Status |
|---------|--------|
| Cron job scheduling | ✅ |
| Manual trigger | ✅ |
| Dead-letter support | ✅ |

**Dependencies:** None

## outbox-event-module

**Status:** ✅ Implemented

Transactional outbox pattern for reliable event publishing.

| Feature | Status |
|---------|--------|
| Outbox record storage | ✅ |
| Event dispatch with retry | ✅ |
| Idempotency support | ✅ |

**Dependencies:** `shared-kernel`
