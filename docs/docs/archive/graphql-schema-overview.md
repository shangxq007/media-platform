# GraphQL Schema Overview

> **Module**: `federation-query-module`
> **Schema Location**: `src/main/resources/graphql/`
> **Last Updated**: 2026-05-16

## Schema Files

The GraphQL schema is organized into 10 files by domain. All types use the `graphqls` extension and follow the SDL (Schema Definition Language) format.

### File Inventory

| File | Queries | Types | Description |
|------|---------|-------|-------------|
| `common.graphqls` | — | 8 + 1 scalar | Shared types used across all schemas |
| `me.graphqls` | 1 | 6 | User overview, tenant, workspace, navigation, billing |
| `render.graphqls` | 1 | 6 | Export panel state, timeline, options, workers |
| `admin-dashboard.graphqls` | 1 | 5 | Admin dashboard stats, health, billing, feedback |
| `prompt.graphqls` | 1 | 3 | Prompt template detail, versions, executions |
| `navigation.graphqls` | 1 | 2 | Navigation profile and menu groups |
| `entitlement.graphqls` | 2 | 0 (uses common) | Capability and decision queries |
| `billing.graphqls` | 2 | 3 | Billing summary and usage records with pagination |
| `extension.graphqls` | 1 | 3 | Extension info with routing rules and resource limits |
| `monitoring.graphqls` | 1 | 3 | Monitoring status, feedback, problematic data |

## Query Reference

### meOverview

Returns the current user's overview including tenant, workspace, capabilities, navigation, and billing.

```graphql
type Query {
    meOverview: MeOverview!
}

type MeOverview {
    id: String!
    displayName: String!
    currentTenant: TenantInfo
    currentWorkspace: WorkspaceInfo
    capabilities: [Capability!]!
    navigation: [NavigationRoute!]!
    billing: BillingSummary
}
```

### exportPanelState

Returns the complete export panel state for a project.

```graphql
type Query {
    exportPanelState(projectId: String!): ExportPanelState!
}

type ExportPanelState {
    project: ProjectInfo!
    timelineSummary: TimelineSummary!
    exportOptions: [ExportOption!]!
    workers: [WorkerStatus!]!
    validation: ExportValidation
}
```

### adminDashboard

Returns admin dashboard data. Requires `ADMIN` or `DASHBOARD_ADMIN` role.

```graphql
type Query {
    adminDashboard(range: String = "7d"): AdminDashboard!
}

type AdminDashboard {
    renderStats: RenderStats!
    providerHealth: [ProviderHealth!]!
    billingSummary: AdminBillingSummary!
    feedbackSummary: FeedbackSummary!
    extensionSummary: ExtensionSummary!
}
```

### promptTemplateDetail

Returns detailed information about a prompt template.

```graphql
type Query {
    promptTemplateDetail(id: String!): PromptTemplateDetail!
}

type PromptTemplateDetail {
    id: String!
    name: String!
    status: String!
    currentVersion: String
    tags: [String!]
    versions: [PromptVersion!]!
    executions(limit: Int = 20): [PromptExecution!]!
}
```

### navigationProfile

Returns the navigation profile for the current user.

```graphql
type Query {
    navigationProfile: NavigationProfile!
}

type NavigationProfile {
    routes: [NavigationRoute!]!
    menuGroups: [MenuGroup!]!
}
```

### myCapabilities

Returns the current user's capabilities.

```graphql
type Query {
    myCapabilities: [Capability!]!
}
```

### entitlementDecision

Evaluates an entitlement decision for a feature.

```graphql
type Query {
    entitlementDecision(featureKey: String!): Decision!
}
```

### billingSummary

Returns the current user's billing summary.

```graphql
type Query {
    billingSummary: BillingSummary!
}
```

### usageRecords

Returns paginated usage records.

```graphql
type Query {
    usageRecords(limit: Int = 20, cursor: String): UsageRecordConnection!
}
```

### extensionOverview

Returns all extensions.

```graphql
type Query {
    extensionOverview: [ExtensionInfo!]!
}
```

### monitoringFeedbackOverview

Returns monitoring and feedback data.

```graphql
type Query {
    monitoringFeedbackOverview(range: String = "7d"): MonitoringFeedbackOverview!
}
```

## Type Reference

### Common Types

```
PageInfo           - Pagination info (hasNextPage, cursors, totalCount)
ErrorInfo          - Error details (errorCode, reasonCode, message, traceId, details)
Money              - Monetary amount (amount, currency)
DateTime           - Timestamp (iso, epochMillis)
Decision           - Access decision (allowed, reasonCode, message, etc.)
Capability         - Feature capability (featureKey, allowed, quotaRemaining, etc.)
StatusBadge        - Status display (value, display, color)
MetricCard         - Dashboard metric (key, label, value, unit, trend, status)
AuditSummary       - Audit info (total, lastActivityAt)
Map                - Generic key-value map (scalar)
```

### User Domain Types

```
MeOverview         - User overview (id, displayName, tenant, workspace, capabilities, navigation, billing)
TenantInfo         - Tenant (id, name, tier)
WorkspaceInfo      - Workspace (id, name, role)
NavigationRoute    - Navigation item (routeKey, path, title, icon, visible, enabled, children)
BillingSummary     - Billing (currentPlan, creditBalance, usageThisMonth)
UsageSummary       - Usage (renderMinutes, storageGb, apiCalls)
```

### Render Domain Types

```
ExportPanelState   - Export panel (project, timelineSummary, exportOptions, workers, validation)
ProjectInfo        - Project (id, name)
TimelineSummary    - Timeline (durationSeconds, tracks, clips, subtitles, effects)
ExportOption       - Export option (preset, allowed, reasonCode, estimatedCost, providerCandidates)
WorkerStatus       - Worker (id, status, gpuAvailable, providerKeys)
ExportValidation   - Validation (allowed, violations, recommendations)
```

### Admin Domain Types

```
AdminDashboard         - Dashboard (renderStats, providerHealth, billingSummary, feedbackSummary, extensionSummary)
RenderStats            - Render stats (submitted, completed, failed, avgDurationSeconds)
ProviderHealth         - Provider health (providerKey, status, latencyMs, errorRate)
AdminBillingSummary    - Billing (usageAmount, estimatedRevenue, creditBalanceTotal)
ExtensionSummary       - Extensions (installed, enabled, highRisk, sandboxJobsRunning)
FeedbackSummary        - Feedback (openIssues, criticalIssues, linkedRenderJobs, etc.)
```

### Prompt Domain Types

```
PromptTemplateDetail   - Template (id, name, status, currentVersion, tags, versions, executions)
PromptVersion          - Version (version, createdAt, createdBy, changelog)
PromptExecution        - Execution (executionId, status, riskLevel, costEstimate, startedAt, finishedAt)
```

### Navigation Domain Types

```
NavigationProfile      - Profile (routes, menuGroups)
MenuGroup              - Group (key, label, icon, order, routes)
```

### Billing Domain Types

```
UsageRecordConnection  - Paginated records (edges, pageInfo)
UsageRecordEdge        - Edge (cursor, node)
UsageRecord            - Record (id, meterKey, quantity, unit, recordedAt, ratedAmount)
```

### Extension Domain Types

```
ExtensionInfo          - Extension (extensionKey, runtimeType, trustLevel, enabled, version, healthStatus)
RouteRule              - Routing rule (scene, priority, enabled)
ResourceLimits         - Limits (timeoutMs, maxConcurrency, maxOutputBytes)
```

### Monitoring Domain Types

```
MonitoringFeedbackOverview  - Overview (monitoringStatus, feedbackSummary, problematicDataSummary)
MonitoringStatus           - Status (sentryEnabled, openReplayEnabled, lastErrorAt, lastFeedbackAt)
FeedbackSummary            - Feedback (openIssues, criticalIssues, linkedRenderJobs, etc.)
ProblematicDataSummary     - Data quality (total, requireReview, autoFixed, critical)
```

## Total Schema Statistics

| Metric | Count |
|--------|-------|
| Schema files | 10 |
| Query fields | 12 |
| Object types | 35+ |
| Scalar types | 1 (Map) |
| Enum types | 0 (represented as strings) |
| Interface types | 0 |
| Union types | 0 |
| Input types | 0 (queries only, no mutations) |
