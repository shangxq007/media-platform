# Frontend GraphQL Client Usage

> **Module**: `frontend`
> **Client Location**: `src/api/graphqlClient.ts`
> **Composables**: `src/composables/useGraphQLQuery.ts`
> **Last Updated**: 2026-05-16

## Architecture Overview

The frontend uses a layered approach for GraphQL data fetching:

```
Vue Components
  → useGraphQLQuery composable
    → graphqlRequest function
      → graphql-request library (GraphQLClient)
        → POST /graphql
```

When GraphQL fails, the composable automatically falls back to a REST `fallbackFn`.

## GraphQL Client

### Configuration

```typescript
// src/api/graphqlClient.ts
import { GraphQLClient } from 'graphql-request'

const endpoint = import.meta.env.VITE_GRAPHQL_ENDPOINT || '/graphql'

export const graphqlClient = new GraphQLClient(endpoint, {
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',
})
```

The endpoint is configurable via the `VITE_GRAPHQL_ENDPOINT` environment variable. Defaults to `/graphql` (same origin).

### Request Function

```typescript
export async function graphqlRequest<T>(
  query: string,
  variables?: Record<string, unknown>
): Promise<T>
```

On success, returns the typed response. On error, parses the GraphQL error response and throws a `GraphQLError`.

### Error Handling

```typescript
export class GraphQLError extends Error {
  constructor(
    message: string,
    public readonly errorCode?: string,
    public readonly traceId?: string,
    public readonly details?: unknown
  ) {
    super(message)
    this.name = 'GraphQLError'
  }
}
```

The client extracts `errorCode`, `traceId`, and `details` from the GraphQL error extensions. Non-GraphQL errors (network failures, etc.) are re-thrown as-is.

## useGraphQLQuery Composable

### API

```typescript
export function useGraphQLQuery<T>(options: {
  query: string
  variables?: Record<string, unknown>
  fallbackFn?: () => Promise<T>
  immediate?: boolean
  transform?: (data: unknown) => T
}): {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<GraphQLError | Error | null>
  errorCode: Ref<string | undefined>
  traceId: Ref<string | undefined>
  refetch: () => Promise<T | null>
}
```

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `query` | `string` | required | GraphQL query string |
| `variables` | `Record<string, unknown>` | `undefined` | Query variables |
| `fallbackFn` | `() => Promise<T>` | `undefined` | REST fallback when GraphQL fails |
| `immediate` | `boolean` | `true` | Execute on mount |
| `transform` | `(data) => T` | `undefined` | Transform raw response |

### Return Values

| Value | Type | Description |
|-------|------|-------------|
| `data` | `Ref<T \| null>` | Query result data |
| `loading` | `Ref<boolean>` | Loading state |
| `error` | `Ref<GraphQLError \| Error \| null>` | Error if failed |
| `errorCode` | `Ref<string \| undefined>` | GraphQL error code |
| `traceId` | `Ref<string \| undefined>` | Request trace ID |
| `refetch` | `() => Promise<T \| null>` | Re-execute the query |

### Fallback Behavior

When GraphQL fails and `fallbackFn` is provided:
1. GraphQL request is attempted first
2. On failure, `fallbackFn` is called
3. If fallback succeeds, its result is used as `data`
4. If fallback fails, the fallback error is set
5. When fallback succeeds, `error` is cleared (no error shown)

When GraphQL fails and no `fallbackFn` is provided:
1. The GraphQL error is set in `error`
2. `errorCode` and `traceId` are extracted from the `GraphQLError`

### useGraphQLQueryWithVars

A variant that watches a reactive variables ref and re-executes when variables change:

```typescript
export function useGraphQLQueryWithVars<T>(
  query: string,
  variables: Ref<Record<string, unknown>>,
  fallbackFn?: () => Promise<T>
): GraphQLQueryResult<T>
```

## Query Definitions

All GraphQL queries are defined as `.graphql` files in `src/graphql/queries/` and imported using `?raw`:

```typescript
import ME_OVERVIEW from '@/graphql/queries/meOverview.graphql?raw'
import EXPORT_PANEL_STATE from '@/graphql/queries/exportPanelState.graphql?raw'
import PROMPT_TEMPLATE_DETAIL from '@/graphql/queries/promptTemplateDetail.graphql?raw'
import ADMIN_DASHBOARD from '@/graphql/queries/adminDashboard.graphql?raw'
```

### Available Queries

| Query File | Query Name | Variables | Used By |
|------------|------------|-----------|---------|
| `meOverview.graphql` | `MeOverview` | None | NavigationMenu, MeOverview page |
| `exportPanelState.graphql` | `ExportPanelState` | `projectId: String!` | ExportPanel component |
| `promptTemplateDetail.graphql` | `PromptTemplateDetail` | `id: String!` | PromptManagementPage |
| `adminDashboard.graphql` | `AdminDashboard` | `range: String` | AdminDashboard page |

## Page Integration

### NavigationMenu (MeOverview)

Loads navigation routes from GraphQL `meOverview`:

```typescript
const { data, loading, error, errorCode } = useGraphQLQuery<MeOverviewData>({
  query: ME_OVERVIEW,
})
```

The navigation menu uses the `navigation` field from `MeOverview` to render routes filtered by `visible` and `enabled`.

### ExportPanel (ExportPanelState)

Loads export panel state from GraphQL with REST fallback:

```typescript
const { data, refetch } = useGraphQLQuery<ExportPanelData>({
  query: EXPORT_PANEL_STATE,
  variables: { projectId },
  fallbackFn: fetchExportPanelREST,
  immediate: false,
})
```

The component displays:
- Timeline summary from GraphQL data
- Export options with allowed/restricted status
- Worker status (local + remote with GPU info)
- Validation violations and recommendations
- REST fallback notice when GraphQL is unavailable

### PromptManagementPage (PromptTemplateDetail)

Loads prompt template detail from GraphQL with REST fallback:

```typescript
const { data, loading, error, errorCode, refetch } = useGraphQLQuery<PromptTemplateDetailData>({
  query: PROMPT_TEMPLATE_DETAIL,
  variables: { id: currentTemplateId },
  immediate: false,
})
```

The page shows:
- Template name, status, current version
- Version history
- Recent executions (limited to 20)
- REST fallback notice when GraphQL is unavailable

### AdminDashboard (AdminDashboard)

Loads admin dashboard data from GraphQL with REST fallback:

```typescript
const { data, loading, error, errorCode, refetch } = useGraphQLQuery<AdminDashboardData>({
  query: ADMIN_DASHBOARD,
  variables: { range: range.value },
  fallbackFn: fetchAdminDashboardREST,
  immediate: false,
})
```

The dashboard shows:
- Render stats (submitted, completed, failed)
- Provider health with latency and error rates
- Billing summary with usage, revenue, credits
- Feedback summary with issue counts
- Extension summary with install/enabled counts
- Error display with GraphQL error codes for access denied

## Error Handling

### ErrorState Component

The `ErrorState.vue` component displays GraphQL errors with:

```html
<ErrorState
  title="Access Denied"
  description="You do not have permission"
  :errorCode="errorCode"
  :diagnosticId="traceId"
  showRetry
  @retry="refetch"
/>
```

The component renders:
- Error title and description
- Error code in monospace font (e.g., `GRAPHQL-403-001`)
- Diagnostic ID (trace ID) with copy button
- Retry button (triggers refetch)
- Copy error button (copies full error details)
- Dismiss button

### Fallback Detection

Pages track whether REST fallback was used:

```typescript
const restFallback = ref(false)

async function fetchExportPanelREST(): Promise<ExportPanelData> {
  restFallback.value = true
  // ... REST calls
}
```

When `restFallback` is true, a blue notice bar is shown:
```html
<div v-if="restFallback">
  Using REST fallback — GraphQL endpoint unavailable
</div>
```

## Error Code Display

GraphQL error codes are displayed in the UI using the `ErrorState` component. The error code format is:

```
{MODULE}-{STATUS}-{SEQUENCE}
```

Examples:
- `GRAPHQL-403-001` — Access denied
- `ENTITLEMENT-403-001` — Feature not allowed
- `PROMPT-404-001` — Template not found
- `COMMON-500-001` — Internal error
- `SECURITY-429-001` — Rate limit exceeded

## TypeScript Types

Frontend TypeScript types mirror the GraphQL schema:

```typescript
interface MeOverviewData {
  id: string
  displayName: string
  currentTenant: { id: string; name: string; tier: string } | null
  currentWorkspace: { id: string; name: string; role: string } | null
  capabilities: Array<{ featureKey: string; allowed: boolean; reasonCode: string | null }>
  navigation: Array<{ routeKey: string; path: string; title: string; visible: boolean; enabled: boolean }>
  billing: { currentPlan: string; creditBalance: { amount: number; currency: string } } | null
}

interface ExportPanelData {
  project: { id: string; name: string }
  timelineSummary: { durationSeconds: number; tracks: number; clips: number; subtitles: number; effects: number }
  exportOptions: Array<{ preset: string; allowed: boolean; reasonCode: string | null }>
  workers: Array<{ id: string; status: string; gpuAvailable: boolean; providerKeys: string[] }>
  validation: { allowed: boolean; violations: string[]; recommendations: string[] }
}
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_GRAPHQL_ENDPOINT` | `/graphql` | GraphQL API endpoint URL |
