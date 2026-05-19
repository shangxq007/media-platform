# Natural Language Query (NLQ) Assistant

## Overview

The NLQ Assistant enables users to query platform analytics data using natural language questions. The system converts questions to SQL, validates safety, enforces scope isolation, executes read-only queries, and returns results with optional chart suggestions.

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌───────────────────┐
│   Frontend   │────▶│  NLQ Controller  │────▶│  SQL Generation   │
│  (Vue.js)    │     │  (REST API)      │     │  Service          │
└─────────────┘     └──────────────────┘     └───────────────────┘
                              │                        │
                              ▼                        ▼
                    ┌──────────────────┐     ┌───────────────────┐
                    │  Query Execution │◀────│  SQL Safety       │
                    │  Service         │     │  Validator        │
                    └──────────────────┘     └───────────────────┘
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              ┌──────────┐ ┌────────┐ ┌──────────────┐
              │ Result   │ │ Chart  │ │ Query        │
              │ Redaction│ │ Suggest│ │ Audit        │
              │ Service  │ │ Service│ │ Service      │
              └──────────┘ └────────┘ └──────────────┘
```

## Module Structure

### Domain Models (`federation-query-module/.../nlq/domain/`)

| Model | Purpose |
|-------|---------|
| `QueryDataset` | Registered dataset with access control metadata |
| `QueryDatasetField` | Field metadata including redaction strategy |
| `SqlDraft` | AI/deterministic generated SQL with intent and assumptions |
| `SqlSafetyResult` | Safety validation outcome with violations |
| `QueryCostEstimate` | Cost/risk estimate for a query |
| `QueryResult` | Execution result with rows, columns, summary, chart suggestions |
| `QueryHistoryRecord` | Stored query history entry |
| `ReportDefinition` | Saved report with widgets and schedule |
| `ReportExecution` | Report execution record |
| `ChartSuggestion` | Suggested chart type for result data |

### Application Services (`federation-query-module/.../nlq/app/`)

| Service | Responsibility |
|---------|---------------|
| `QueryCatalogService` | In-memory dataset registry with role/permission filtering |
| `SqlGenerationService` | AI-powered + deterministic SQL generation from natural language |
| `SqlSafetyValidator` | Validates SQL is read-only, has LIMIT, no injection, etc. |
| `SqlScopeInjector` | Injects tenant/workspace/user scope conditions |
| `SqlCostEstimator` | Estimates query cost/risk from SQL structure |
| `QueryExecutionService` | Executes read-only SQL via JdbcTemplate |
| `ResultRedactionService` | Redacts PII fields (email, phone, IP, user_id) |
| `ResultSummarizer` | AI-powered or rule-based result summarization |
| `ChartSuggestionService` | Suggests chart types based on result structure |
| `QueryHistoryService` | In-memory query history storage |
| `QueryAuditService` | Writes audit events for all NLQ operations |
| `ReportDefinitionService` | CRUD for saved reports |
| `ReportExecutionService` | Executes saved reports with scope enforcement |

## REST API Endpoints

### NLQ Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/analytics/nlq/preview` | Generate SQL preview from natural language |
| `POST` | `/api/v1/analytics/nlq/execute` | Execute a SQL query |
| `POST` | `/api/v1/analytics/nlq/explain` | Explain the generated SQL |
| `POST` | `/api/v1/analytics/nlq/chart-suggestions` | Get chart suggestions for result structure |
| `GET` | `/api/v1/analytics/nlq/datasets` | List accessible datasets |
| `GET` | `/api/v1/analytics/nlq/datasets/{datasetKey}` | Get dataset details |

### Report Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/analytics/reports` | Create a new report |
| `GET` | `/api/v1/analytics/reports` | List reports |
| `GET` | `/api/v1/analytics/reports/{reportId}` | Get report details |
| `PUT` | `/api/v1/analytics/reports/{reportId}` | Update a report |
| `POST` | `/api/v1/analytics/reports/{reportId}/execute` | Execute a report |
| `POST` | `/api/v1/analytics/reports/{reportId}/archive` | Archive a report |

## Safety Rules

All generated SQL is validated against these rules:

1. Must start with `SELECT` or `WITH`
2. No DDL statements (CREATE, DROP, ALTER, TRUNCATE)
3. No DML statements (INSERT, UPDATE, DELETE, MERGE)
4. No multi-statement queries (semicolon injection)
5. No `SELECT *` — explicit columns required
6. Must include a `LIMIT` clause
7. No `CROSS JOIN`
8. Time-series queries must include a time range filter
9. No sensitive field access (api_key, password, token, etc.)
10. Only registered datasets may be referenced

## Redaction Strategies

| Strategy | Fields | Example |
|----------|--------|---------|
| `email_mask` | email | `u***r@example.com` |
| `phone_mask` | phone, mobile | `****1234` |
| `user_id_hash` | user_id | `h_A1B2C3D4E5F6` |
| `ip_mask` | ip_address | `192.168.1.*` |
| `full_redact` | password, secret, token | `***REDACTED***` |
| `partial_redact` | name, address | `J***n` |

## Scope Isolation

Queries are automatically scoped to the user's tenant, workspace, and user ID:

- **Tenant scope**: `tenant_id = :tenant_id` (unless already present)
- **Workspace scope**: `workspace_id = :workspace_id` (unless already present)
- **User scope**: `created_by = :user_id` (non-admin users only)
- **Admin bypass**: Admins with `analytics.global.query` permission skip injection

## Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `NLQ-400-001` | 400 | NLQ feature disabled |
| `NLQ-400-002` | 400 | SQL failed safety validation |
| `NLQ-400-003` | 400 | SQL operation not allowed |
| `NLQ-400-004` | 400 | Missing scope conditions |
| `NLQ-400-005` | 400 | LIMIT clause required |
| `NLQ-400-006` | 400 | Query too complex |
| `NLQ-402-001` | 402 | Query cost exceeds threshold |
| `NLQ-403-001` | 403 | Dataset access denied |
| `NLQ-404-001` | 404 | Report not found |
| `NLQ-408-001` | 408 | Query timeout |
| `NLQ-410-001` | 410 | Preview expired |
| `NLQ-425-001` | 425 | Execution requires confirmation |
| `NLQ-503-001` | 503 | AI provider unavailable |

## Frontend Routes

### User-Side
- `/me/analytics` — Analytics Assistant (NLQ interface)
- `/me/reports` — My Reports (saved reports list)

### Admin-Side
- `/admin/analytics/datasets` — Dataset Catalog (manage datasets)
- `/admin/analytics/query-audit` — Query Audit Logs (view audit trail)

## Intent Classification

The SQL generator classifies questions into intents:

| Intent | Keywords | Default Chart |
|--------|----------|---------------|
| `AGGREGATION` | total, sum, count, average, how many | metric_card, pie_chart |
| `TREND` | trend, over time, daily, weekly, monthly | line_chart, area_chart |
| `COMPARISON` | compare, versus, difference | bar_chart, grouped_bar |
| `RANKING` | top, bottom, highest, lowest, rank | bar_chart, table |
| `DISTRIBUTION` | distribution, breakdown, by category | pie_chart, donut_chart |
| `DETAIL` | (default) | table |

## Chart Suggestions

Chart types are suggested based on result structure:

- **Time field + numeric** → line chart, area chart
- **Category + numeric** → bar chart
- **Two columns (category + numeric)** → pie chart
- **Single row with numeric** → metric card
- **Two numeric fields** → scatter plot
- **Category + multiple numeric** → stacked bar
- **Always available** → table

## Testing

### Backend Tests

- `SqlSafetyValidatorTest` — SQL safety validation rules
- `QueryExecutionReadOnlyTest` — Query execution with mocks
- `QueryTenantIsolationTest` — Scope injection and tenant isolation
- `NlqAuditTest` — Audit event recording

### Frontend Tests

- `AnalyticsAssistantPage.spec.ts` — Renders, buttons, disabled states
- `MyReportsPage.spec.ts` — Reports list, execute, archive
- `DatasetCatalogPage.spec.ts` — Dataset listing and detail view
- `QueryAuditPage.spec.ts` — Audit log display

## Configuration

The NLQ feature is controlled by the `nlq.enabled` feature flag (default: true).

## Dependencies

- `ai-module` — AI gateway for SQL generation and summarization
- `datasource-module` — Federated query gateway interface
- `shared-kernel` — Audit port, tenant context, error codes
- `policy-governance-module` — Feature flag service
- `JdbcTemplate` — Spring JDBC for query execution
