# Security & Tenancy

## Multi-Tenancy Isolation Strategy

This platform enforces **strict multi-tenant isolation** at every layer: authentication, service logic, and data access. A tenant can never access another tenant's data — cross-tenant requests return `404 Not Found` without revealing whether the resource exists.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      API Request                            │
│                   (Header: X-API-Key)                       │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   ApiKeyAuthFilter                          │
│  1. Extract X-API-Key header                                │
│  2. Validate key against api_key table (SHA-256 hash)       │
│  3. Resolve tenantId from ApiKeyRecord                      │
│  4. Set TenantContext (ThreadLocal)                         │
│  5. Set MDC tenantId for log tracing                        │
│  6. Clear TenantContext in finally block                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                              │
│  • assertTenantAccess(tenantId)                             │
│  • assertProjectBelongsToTenant(tenantId, projectId)        │
│  • All queries scoped by tenantId                           │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer                           │
│  • findByTenantId(tenantId)                                 │
│  • findByProjectId(tenantId, projectId)                     │
│  • All queries include tenant_id WHERE clause               │
└─────────────────────────────────────────────────────────────┘
```

## API Key Authentication Flow

### Authentication

1. Client sends request with `X-API-Key` header
2. `ApiKeyAuthFilter` intercepts protected endpoints (`/api/v1/identity`, `/api/v1/render`, `/api/v1/storage`, `/api/v1/audit`, `/api/v1/extensions`, `/api/v1/outbox`)
3. Key is hashed with SHA-256 and looked up in the `api_key` table
4. Revoked keys (`revoked_at IS NOT NULL`) are rejected
5. On success, the filter:
   - Sets `TenantContext` with the key's bound `tenantId`
   - Sets MDC `tenantId` for structured logging
   - Sets MDC `principal` for audit trails
6. On failure, returns `401 Unauthorized` with ProblemDetail body

### API Key → Tenant Binding

Every `ApiKeyRecord` contains a `tenantId` field. When an API key is created via `POST /api/v1/identity/tenants/{tenantId}/apikeys`, the key is permanently bound to that tenant. The `tenantIdOf(apiKey)` method resolves the tenant from the key.

### Error Responses

| Scenario | HTTP Status | Error Code | Detail |
|----------|-------------|------------|--------|
| Missing API key | 401 | `COMMON-401-001` | Missing API key |
| Invalid/revoked API key | 401 | `COMMON-401-001` | Invalid or revoked API key |
| Cross-tenant access | 404 | `COMMON-404-001` | Resource not found |
| Insufficient permission | 403 | `COMMON-403-001` | Insufficient permission |

## Cross-Tenant Access Protection

### Design Principle: 404, Never 403

Cross-tenant access attempts **always return `404 Not Found`**, never `403 Forbidden`. This prevents attackers from discovering that a resource exists in another tenant.

```java
// Service layer pattern
private void assertTenantAccess(String tenantId) {
    String currentTenant = TenantContext.get();
    if (currentTenant != null && !currentTenant.equals(tenantId)) {
        throw new IllegalArgumentException("Resource not found for tenant");
    }
}
```

The controller's `@ExceptionHandler(IllegalArgumentException.class)` maps this to a `404` ProblemDetail.

### Enforcement Points

| Service | Method | Protection |
|---------|--------|------------|
| `TenantProjectService` | `getProject(projectId)` | Verifies project.tenantId matches TenantContext |
| `TenantProjectService` | `createProject(tenantId, ...)` | Verifies path tenantId matches TenantContext |
| `TenantProjectService` | `listProjects(tenantId)` | Filters by TenantContext tenantId |
| `TenantProjectService` | `listApiKeys(tenantId)` | Filters API keys by tenantId |
| `RenderJobService` | `getById(jobId)` | Reads job's tenantId from DB, verifies match |
| `RenderJobService` | `list()` | Adds `WHERE tenant_id = ?` when TenantContext is set |
| `RenderOrchestratorService` | `submitRenderJob(request)` | Validates request.tenantId + project ownership |
| `RenderOrchestratorService` | `getArtifactsByJob(jobId)` | Reads job's tenantId from DB, verifies match |
| `StorageCatalogService` | `findArtifactsByProject(projectId)` | Verifies project.tenantId matches TenantContext |
| `AuditService` | `recent()`, `findByCategory()`, `findByResource()` | Adds tenant filter when TenantContext is set |

## TenantContext

`TenantContext` is a `ThreadLocal<String>` that holds the current request's tenant ID:

```java
// Set by ApiKeyAuthFilter after successful authentication
TenantContext.set(tenantId);

// Read by services to enforce isolation
String currentTenant = TenantContext.get();

// Cleared in finally block after request completes
TenantContext.clear();
```

### Thread Safety

- `TenantContext` is request-scoped via `ThreadLocal`
- The `finally` block in `ApiKeyAuthFilter` always clears it, preventing thread-pool leakage
- MDC (`TraceKeys.TENANT_ID`) is set/cleared alongside `TenantContext`

## Permission Model

### API Key Scopes

API keys are tenant-scoped. A key created for tenant A can only:
- Access tenant A's projects, users, and resources
- Create render jobs under tenant A's projects
- List tenant A's audit records

### Role-Based Access (Future)

The `User` entity has a `role` field (`ADMIN`, `MEMBER`, `VIEWER`) for future RBAC enforcement at the service layer.

## Database Schema

### Tenant-Scoped Tables

| Table | Tenant Column | Index |
|-------|--------------|-------|
| `project` | `tenant_id` | `ix_project_tenant_id` |
| `"user"` | `tenant_id` | `ix_user_tenant_id` |
| `api_key` | `tenant_id` | — |
| `render_job` | `tenant_id` | — |
| `artifact` | (via `project_id` → `project.tenant_id`) | `ix_artifact_project_id` |
| `audit_records` | (via `actor_id` filtering) | — |

## Testing

Multi-tenancy isolation is verified by `MultiTenancyIsolationTest`:

- Tenant A cannot query Tenant B's projects → `IllegalArgumentException` (404)
- Tenant A cannot create projects under Tenant B → `IllegalArgumentException` (404)
- Tenant A's project list only contains Tenant A's projects
- API keys are bound to their creating tenant
- `listApiKeys` only returns keys for the requesting tenant
- Revoked keys cannot authenticate
- Missing/invalid keys return null principal/tenantId
- Without `TenantContext` (auth disabled), all access is permitted
