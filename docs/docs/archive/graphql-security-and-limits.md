# GraphQL Security and Limits

> **Module**: `federation-query-module`
> **Last Updated**: 2026-05-16

## Security Model

### Authentication

Authentication is handled at the Spring Security layer before GraphQL execution. The `GraphQLContextFactory` interceptor extracts authentication data from HTTP headers:

| Header | Purpose |
|--------|---------|
| `X-Tenant-Id` | Tenant identifier |
| `X-User-Id` | User identifier |
| `X-Workspace-Id` | Workspace identifier |
| `X-User-Roles` | Comma-separated roles (e.g., `ADMIN,USER`) |
| `X-User-Permissions` | Comma-separated permissions |
| `X-Auth-Type` | Authentication type (e.g., `JWT_SESSION`, `API_KEY`) |
| `X-Trace-Id` | Distributed tracing ID |
| `X-Request-Id` | Request correlation ID |
| `X-Forwarded-For` | Client IP address |
| `User-Agent` | Client user agent |

If headers are missing, the factory falls back to `TenantContext` for tenant ID and generates random trace/request IDs.

### Authorization

Authorization is enforced at the resolver level:

| Query | Required Roles | Enforcement Point |
|-------|---------------|-------------------|
| `meOverview` | None (returns anonymous for unauthenticated) | `MeOverviewGraphQLResolver` |
| `exportPanelState` | Tenant membership | `ExportPanelGraphQLResolver` (checks `project.tenantId`) |
| `adminDashboard` | `ADMIN` or `DASHBOARD_ADMIN` | `AdminDashboardGraphQLResolver` (throws `IllegalArgumentException` if missing) |
| `promptTemplateDetail` | Tenant scoping | `PromptGraphQLResolver` (uses `context.tenantId()`) |
| `navigationProfile` | None | Returns routes filtered by capabilities |
| `myCapabilities` | None | Returns capabilities for current user |
| `entitlementDecision` | None | Evaluates against current user context |
| `billingSummary` | Tenant scoping | Returns billing for current tenant |
| `usageRecords` | Tenant scoping | Returns records for current tenant |
| `extensionOverview` | None | Returns all extensions |
| `monitoringFeedbackOverview` | None | Returns monitoring data for current context |

### Tenant Isolation

All resolvers scope their data to the current tenant via `GraphQLRequestContext.tenantId()`. The `ExportPanelGraphQLResolver` explicitly checks project ownership:

```java
if (tenantId != null && !tenantId.equals(project.tenantId())) {
    throw new IllegalArgumentException("Project not found for tenant");
}
```

### IP and User Agent Tracking

The `GraphQLRequestContext` captures `ip` and `userAgent` for audit purposes. These are included in audit events but not used for authorization decisions.

## Query Limits

### Depth Limiting

Prevents deeply nested queries that could cause performance issues.

| Property | Default | Description |
|----------|---------|-------------|
| `platform.graphql.query.max-depth` | 10 | Maximum query nesting depth |

Implementation: `GraphQLDepthLimiter` extends `MaxQueryDepthInstrumentation` from `graphql-java`. Queries exceeding the limit are rejected with a `MaxQueryDepthExceededException` before execution begins.

Example of a depth-3 query:
```graphql
query {
  meOverview {           # depth 1
    currentTenant {      # depth 2
      id                 # depth 3
    }
  }
}
```

### Complexity Limiting

Prevents queries that request too many fields.

| Property | Default | Description |
|----------|---------|-------------|
| `platform.graphql.query.max-complexity` | 200 | Maximum query complexity score |

Implementation: `GraphQLQueryComplexityLimiter` extends `MaxQueryComplexityInstrumentation`. Each field contributes 1 to the complexity score. Queries exceeding the limit are rejected with a `MaxQueryComplexityExceededException`.

### Page Size Limiting

Prevents requests for excessive amounts of data.

| Property | Default | Description |
|----------|---------|-------------|
| `platform.graphql.query.max-page-size` | 100 | Maximum items per page |
| `platform.graphql.query.default-page-size` | 20 | Default items per page when not specified |

Implementation: `GraphQLPageSizeValidator` validates and clamps page sizes:
- `null` → `defaultPageSize` (20)
- `0` or negative → `defaultPageSize` (20)
- `> maxPageSize` → `maxPageSize` (100)
- Otherwise → requested value

### Prompt Execution Limit

The `PromptGraphQLResolver` hard-limits executions to 20 per query:
```java
.promptTemplateService.listExecutions(id).stream()
    .limit(20)
    .map(this::mapExecution)
    .collect(Collectors.toList());
```

## Sensitive Data Redaction

The `GraphQLAuditInterceptor` automatically redacts sensitive variables before recording audit events:

### Redacted Fields

Any variable key containing these strings (case-insensitive) is redacted:
- `password`
- `secret`
- `token`
- `apikey`

### Redaction Example

Input variables:
```json
{
  "username": "john",
  "password": "secret123",
  "authToken": "bearer-abc",
  "apiKey": "sk-12345"
}
```

Redacted audit payload:
```json
{
  "username": "john",
  "password": "[REDACTED]",
  "authToken": "[REDACTED]",
  "apiKey": "[REDACTED]"
}
```

## Error Handling Security

### Error Messages

Error messages are designed to avoid leaking sensitive information:
- Database errors are mapped to generic `COMMON-500-001` messages
- Stack traces are never exposed to clients
- Internal details are only in `traceId` for server-side correlation

### Error Code Structure

Error codes follow the pattern `{MODULE}-{STATUS}-{SEQUENCE}`:
- `COMMON-400-001` — Invalid request
- `COMMON-401-001` — Authentication required
- `COMMON-403-001` — Insufficient permission
- `COMMON-404-001` — Resource not found
- `COMMON-500-001` — Internal error
- `COMMON-502-001` — Integration error
- `SECURITY-429-001` — Rate limit exceeded

### Trace ID

Every GraphQL error includes a `traceId` that correlates to the server-side log. This allows debugging without exposing internal state.

## Audit

### Audit Events

The `GraphQLAuditInterceptor` records an audit event for every query:

| Field | Source |
|-------|--------|
| `actorType` | `"GRAPHQL"` |
| `action` | `"EXECUTE"` |
| `category` | `"GRAPHQL_OPERATION"` |
| `resourceType` | `"graphql_operation"` |
| `resourceId` | Operation name |
| `queryHash` | SHA-256 hash of the query (first 16 chars) |
| `variablesRedacted` | Variables with sensitive fields redacted |
| `tenantId` | From `GraphQLRequestContext` |
| `userId` | From `GraphQLRequestContext` |
| `traceId` | From `GraphQLRequestContext` |
| `durationMs` | Execution time |
| `resultStatus` | `SUCCESS`, `PARTIAL`, or `ERROR` |
| `errorCode` | Error code if failed |

### Audit Failures

Audit recording never fails the query. If the `AuditPort.record()` throws an exception, it is caught and logged as a warning.

## Configuration Reference

```yaml
platform:
  graphql:
    query:
      max-depth: 10          # Maximum query nesting depth
      max-complexity: 200    # Maximum query complexity score
      max-page-size: 100     # Maximum items per page
      default-page-size: 20  # Default items per page
```

## Security Checklist

- [x] Authentication via Spring Security
- [x] Role-based authorization at resolver level
- [x] Tenant isolation in all data access
- [x] Query depth limiting
- [x] Query complexity limiting
- [x] Page size limiting
- [x] Sensitive data redaction in audit logs
- [x] No stack traces in error responses
- [x] Trace ID for server-side debugging
- [x] Audit events for all operations
- [x] Graceful audit failure handling
- [x] Input validation via GraphQL type system
- [x] No mutations (read-only API)
