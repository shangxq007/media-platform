# GraphQL Query Aggregation Layer

> **Module**: `federation-query-module`
> **Status**: Stage 1 — Query Aggregation Layer (Current)
> **Last Updated**: 2026-05-16

## Overview

The `federation-query-module` serves as a **GraphQL query aggregation layer** that unifies data from multiple backend modules into a single, cohesive GraphQL API. It is designed to serve the frontend with all the data it needs in a single request, avoiding the N+1 problem through DataLoader batching.

### Design Principles

1. **Read-only aggregation**: The GraphQL layer is exclusively for queries. Mutations remain in REST endpoints.
2. **Single request per page**: Each page loads all its data via one GraphQL query.
3. **Tenant isolation**: All queries are scoped to the current tenant via `GraphQLRequestContext`.
4. **Fallback to REST**: If GraphQL is unavailable, the frontend falls back to individual REST calls.
5. **Error resilience**: Errors are mapped to structured GraphQL errors with error codes, trace IDs, and localized messages.

## Schema Organization

The schema is split across 10 `.graphqls` files, organized by domain:

| Schema File | Domain | Key Types |
|-------------|--------|-----------|
| `common.graphqls` | Shared types | `PageInfo`, `ErrorInfo`, `Money`, `DateTime`, `Decision`, `Capability`, `StatusBadge`, `MetricCard` |
| `me.graphqls` | User overview | `MeOverview`, `TenantInfo`, `WorkspaceInfo`, `NavigationRoute`, `BillingSummary` |
| `render.graphqls` | Export panel | `ExportPanelState`, `ProjectInfo`, `TimelineSummary`, `ExportOption`, `WorkerStatus`, `ExportValidation` |
| `admin-dashboard.graphqls` | Admin dashboard | `AdminDashboard`, `RenderStats`, `ProviderHealth`, `AdminBillingSummary`, `ExtensionSummary` |
| `prompt.graphqls` | Prompt management | `PromptTemplateDetail`, `PromptVersion`, `PromptExecution` |
| `navigation.graphqls` | Navigation | `NavigationProfile`, `MenuGroup` |
| `entitlement.graphqls` | Entitlement | `Capability`, `Decision` (queries: `myCapabilities`, `entitlementDecision`) |
| `billing.graphqls` | Billing | `BillingSummary`, `UsageRecord`, `UsageRecordConnection` |
| `extension.graphqls` | Extensions | `ExtensionInfo`, `RouteRule`, `ResourceLimits` |
| `monitoring.graphqls` | Monitoring | `MonitoringFeedbackOverview`, `MonitoringStatus`, `FeedbackSummary`, `ProblematicDataSummary` |

### Common Types

The `common.graphqls` file defines shared types used across all schemas:

```graphql
type Money {
    amount: Float!
    currency: String!
}

type DateTime {
    iso: String!
    epochMillis: Long!
}

type Capability {
    featureKey: String!
    allowed: Boolean!
    reasonCode: String
    quotaRemaining: Float
    expiresAt: DateTime
    source: String
}

type Decision {
    allowed: Boolean!
    reasonCode: String
    message: String
    recommendedAlternative: String
    requiresReview: Boolean!
    quotaRemaining: Float
    upgradeOptions: [String!]
}

scalar Map
```

## Resolver Architecture

All resolvers are in `com.example.platform.federation.graphql.resolver` and use Spring for GraphQL's `@QueryMapping` annotation.

### Resolver Classes

| Resolver | Query | Description |
|----------|-------|-------------|
| `MeOverviewGraphQLResolver` | `meOverview` | Aggregates user info, tenant, workspace, capabilities, navigation, billing |
| `ExportPanelGraphQLResolver` | `exportPanelState` | Aggregates project, timeline, export options, workers, validation |
| `AdminDashboardGraphQLResolver` | `adminDashboard` | Aggregates render stats, provider health, billing, feedback, extensions |
| `PromptGraphQLResolver` | `promptTemplateDetail` | Aggregates template, versions, executions (limited to 20) |
| `ExtensionGraphQLResolver` | `extensionOverview` | Lists all extensions with trust levels and health status |

### Data Flow

```
HTTP Request
  → GraphQLContextFactory (interceptor: extracts headers → GraphQLRequestContext)
    → Instrumentation (depth/complexity limits)
      → Resolver (uses GraphQLRequestContext for tenant/user scoping)
        → Service calls (entitlement, billing, render, prompt, etc.)
          → DTOs (record types mapped to GraphQL schema)
            → GraphQL Response
```

### Context Propagation

The `GraphQLRequestContext` record carries request-scoped data:

```java
public record GraphQLRequestContext(
    String tenantId,
    String workspaceId,
    String userId,
    List<String> roles,
    List<String> permissions,
    String requestSource,
    String authType,
    String traceId,
    String requestId,
    String ip,
    String userAgent
) {}
```

This context is:
1. Created by `GraphQLContextFactory` from HTTP headers
2. Stored in the GraphQL execution context via `configureExecutionInput`
3. Injected into resolvers via Spring for GraphQL's method parameter resolution
4. Used by the `GraphQLAuditInterceptor` for audit logging

## Security Model

### Authorization

- **Admin Dashboard**: Requires `ADMIN` or `DASHBOARD_ADMIN` role. Enforced in the resolver.
- **Export Panel**: Tenant isolation enforced by checking `project.tenantId` against `context.tenantId()`.
- **Prompt Detail**: Tenant scoping via `context.tenantId()`.
- **Me Overview**: Returns anonymous data if no user is authenticated.

### Input Validation

- **Query depth limit**: Default 10 levels, configurable via `platform.graphql.query.max-depth`
- **Query complexity limit**: Default 200, configurable via `platform.graphql.query.max-complexity`
- **Page size limit**: Default 20, max 100, configurable via `platform.graphql.query.max-page-size`

### Sensitive Data Redaction

The `GraphQLAuditInterceptor` automatically redacts sensitive variables before logging:
- Fields containing `password`, `secret`, `token`, or `apikey` (case-insensitive) are replaced with `[REDACTED]`

## Error Handling

### Error Mapping

`GraphQLExceptionMapper` maps exceptions to `GraphQLError` with extensions:

| Exception Type | Error Code | HTTP Status |
|----------------|------------|-------------|
| `PlatformException` | From `errorCode.code()` | From `errorCode.status()` |
| `IllegalArgumentException` | `COMMON-400-001` | 400 |
| `SecurityException` | `COMMON-403-001` | 403 |
| Any other exception | `COMMON-500-001` | 500 |

### Error Extensions

Every GraphQL error includes these extensions:
- `errorCode`: Structured error code (e.g., `ENTITLEMENT-403-001`)
- `reasonCode`: Present for `ConfigurableErrorCode` instances
- `traceId`: Request trace ID for debugging
- `details`: Optional additional context (from `PlatformException.getDetails()`)

### Error Codes

The error codes follow the pattern: `{MODULE}-{STATUS}-{SEQUENCE}`

Examples:
- `ENTITLEMENT-403-001` — Feature not allowed
- `PROMPT-404-001` — Template not found
- `GRAPHQL-403-001` — Access denied (admin dashboard)
- `COMMON-500-001` — Internal server error
- `SECURITY-429-001` — Rate limit exceeded

## Query Limits

All limits are configurable via `application.yml`:

```yaml
platform:
  graphql:
    query:
      max-depth: 10
      max-complexity: 200
      max-page-size: 100
      default-page-size: 20
```

### Depth Limiting

`GraphQLDepthLimiter` extends `MaxQueryDepthInstrumentation`. Queries exceeding the max depth are rejected before execution.

### Complexity Limiting

`GraphQLQueryComplexityLimiter` extends `MaxQueryComplexityInstrumentation`. Queries exceeding the max complexity are rejected before execution.

### Page Size Validation

`GraphQLPageSizeValidator` clamps requested page sizes to `[1, maxPageSize]`. Values below 1 return `defaultPageSize`.

## DataLoader Usage

DataLoaders are defined in `com.example.platform.federation.graphql.dataloader` for batch loading:

| DataLoader | Key | Value | Purpose |
|------------|-----|-------|---------|
| `UserDataLoader` | `Set<String>` (user IDs) | `Map<String, Object>` | Batch load user data |
| `TenantDataLoader` | `Set<String>` (tenant IDs) | Tenant info | Batch load tenant data |
| `WorkspaceDataLoader` | `Set<String>` (workspace IDs) | Workspace info | Batch load workspace data |
| `BillingUsageDataLoader` | `Set<String>` (tenant IDs) | Usage records | Batch load billing usage |
| `EntitlementGrantDataLoader` | `Set<String>` (feature keys) | Entitlement decisions | Batch load entitlements |
| `PromptTemplateVersionDataLoader` | `Set<String>` (template IDs) | Versions | Batch load prompt versions |
| `PromptExecutionDataLoader` | `Set<String>` (template IDs) | Executions | Batch load executions |
| `RenderJobArtifactDataLoader` | `Set<String>` (job IDs) | Artifacts | Batch load render artifacts |
| `ProviderHealthDataLoader` | `Set<String>` (provider keys) | Health status | Batch load provider health |

### DataLoader Pattern

All DataLoaders implement `MappedBatchLoader<K, V>`:
1. Keys are collected during query execution
2. At the end of the field resolution, all collected keys are batch-loaded
3. Results are cached per-request to avoid duplicate loads
4. Tenant isolation is maintained by scoping batch loads to the current tenant

## Audit

The `GraphQLAuditInterceptor` instruments every query execution:
1. Records operation name, query hash, redacted variables
2. Captures tenant ID, user ID, trace ID from `GraphQLRequestContext`
3. Logs duration, result status (SUCCESS/PARTIAL/ERROR), error code
4. Records audit event via `AuditPort`
5. Never fails the query — audit failures are logged as warnings
