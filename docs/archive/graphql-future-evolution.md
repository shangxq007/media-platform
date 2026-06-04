# GraphQL Future Evolution

> **Module**: `federation-query-module`
> **Current Stage**: Stage 1 — Query Aggregation Layer
> **Last Updated**: 2026-05-16

## Evolution Stages

The GraphQL layer is designed to evolve through 5 stages. Each stage builds on the previous one.

---

## Stage 1: Query Aggregation Layer (Current)

### Status: ✅ Implemented

The GraphQL layer serves as a read-only query aggregation layer for the frontend.

**Key characteristics:**
- Single GraphQL endpoint at `/graphql`
- 12 query fields across 10 schema files
- All resolvers use `@QueryMapping` (read-only)
- DataLoader for batch loading
- REST fallback in frontend
- Structured error codes with trace IDs
- Query depth, complexity, and page size limits

**Modules integrated:**
- `identity-access-module` — User, tenant, workspace data
- `entitlement-module` — Capability checks, decisions
- `billing-module` — Billing summary, usage records
- `render-module` — Export panel, worker status
- `prompt-module` — Template detail, versions, executions
- `extension-module` — Extension catalog
- `user-analytics-module` — Monitoring, feedback
- `audit-compliance-module` — Audit recording

**Limitations (by design):**
- No mutations
- No persisted queries
- No code generation
- No federation
- No subscription support

---

## Stage 2: Persisted Queries

### Status: 📋 Planned

Persisted queries (also called "trusted documents") improve security and performance by storing queries server-side and referencing them by ID.

### Benefits

1. **Security**: Only pre-registered queries can be executed. Clients cannot send arbitrary queries.
2. **Performance**: Queries are parsed and validated once at registration time, not on every request.
3. **Payload size**: Query ID is smaller than the full query text.
4. **CDN caching**: GET requests with query IDs are cacheable.

### Implementation Plan

#### Server-Side

1. Create a query registry table:
```sql
CREATE TABLE graphql_persisted_query (
    query_id VARCHAR(64) PRIMARY KEY,
    query_text TEXT NOT NULL,
    operation_name VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

2. Create a `PersistedQueryRepository` for CRUD operations.

3. Add a `PersistedQueryLoader` that implements `graphql.execution.preparsed.PreparsedDocumentProvider`:
```java
@Component
public class PersistedQueryLoader implements PreparsedDocumentProvider {
    @Override
    public PreparsedDocumentEntry getDocument(
            ExecutionInput executionInput,
            Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
        String queryId = executionInput.getExtensions().get("queryId");
        if (queryId != null) {
            return loadFromRegistry(queryId);
        }
        return computeFunction.apply(executionInput);
    }
}
```

4. Add an admin REST endpoint for query registration:
```
POST   /api/v1/admin/graphql/queries     — Register a query
GET    /api/v1/admin/graphql/queries     — List registered queries
DELETE /api/v1/admin/graphql/queries/:id — Remove a query
```

#### Client-Side

1. Add query ID mapping:
```typescript
const QUERY_IDS = {
  MeOverview: 'me-overview-v1',
  ExportPanelState: 'export-panel-v1',
  AdminDashboard: 'admin-dashboard-v1',
  PromptTemplateDetail: 'prompt-detail-v1',
}
```

2. Modify `graphqlClient.ts` to send query IDs:
```typescript
export async function persistedRequest<T>(
  queryId: string,
  variables?: Record<string, unknown>
): Promise<T> {
  return graphqlClient.request<T>('', variables, {
    queryId: queryId,
  })
}
```

#### Migration Path

1. Deploy persisted query support with auto-registration
2. Frontend switches to persisted queries incrementally
3. Monitor for unknown query IDs (clients still on old version)
4. Eventually reject non-persisted queries via configuration

---

## Stage 3: Frontend GraphQL Codegen

### Status: 📋 Planned

Code generation from the GraphQL schema produces type-safe queries, mutations, and hooks.

### Benefits

1. **Type safety**: Queries are validated at compile time
2. **Auto-completion**: IDE support for GraphQL queries
3. **Refactoring**: Schema changes are caught at build time
4. **Reduced boilerplate**: No manual type definitions

### Implementation Plan

#### Tool Chain

Use [GraphQL Code Generator](https://the-guild.dev/graphql/codegen) with these plugins:

```yaml
# codegen.yml
schema: ../federation-query-module/src/main/resources/graphql/*.graphqls
documents: src/graphql/queries/*.graphql
generates:
  src/generated/graphql.ts:
    plugins:
      - typescript
      - typescript-operations
      - typescript-vue-apollo
    config:
      vueCompositionApiImportFrom: vue
      skipTypename: false
      enumsAsTypes: true
```

#### Generated Output

The codegen produces:
- TypeScript types for all GraphQL types
- Typed `useQuery` hooks for each query
- Typed `useMutation` hooks (future)
- Enum types as TypeScript unions

#### Example Generated Hook

```typescript
// Auto-generated
export function useMeOverviewQuery(
  options?: VueCompositionApi.UseQueryOptions<MeOverviewQuery, MeOverviewQueryVariables>
) {
  return useQuery<MeOverviewQuery, MeOverviewQueryVariables>(
    MeOverviewDocument,
    options
  )
}
```

#### Migration Path

1. Set up codegen in the build pipeline
2. Generate types for existing queries
3. Migrate components to use generated hooks incrementally
4. Add codegen to CI to catch schema drift

---

## Stage 4: Low-Risk Mutations (Preview/Validate)

### Status: 📋 Planned

Introduce read-only mutations that preview or validate operations without side effects.

### Design Principles

1. **No side effects**: Mutations only validate or preview
2. **Idempotent**: Safe to retry
3. **Explicit**: Named with `preview` or `validate` prefix

### Planned Mutations

```graphql
type Mutation {
    # Export preview — validates export settings without submitting
    previewExport(input: ExportPreviewInput!): ExportPreviewResult!

    # Prompt validation — validates prompt variables without executing
    validatePrompt(input: PromptValidateInput!): PromptValidationResult!

    # Entitlement check — evaluates entitlement without recording
    checkEntitlement(input: EntitlementCheckInput!): EntitlementDecision!

    # Billing estimate — estimates cost without charging
    estimateCost(input: CostEstimateInput!): CostEstimateResult!
}
```

### Implementation Plan

1. Add `Mutation` type to a new `mutations.graphqls` schema file
2. Create `MutationGraphQLResolver` with `@MutationMapping` methods
3. Each mutation delegates to existing read-only services
4. Add complexity limits specific to mutations (lower than queries)
5. Frontend uses mutations for form validation and preview

### Security

- Mutations are subject to the same depth/complexity limits
- Each mutation requires explicit authorization
- Audit events are recorded with `action: "PREVIEW"` or `action: "VALIDATE"`
- Rate limiting is stricter for mutations than queries

---

## Stage 5: Federation (If Backend Splits)

### Status: 📋 Planned (Conditional)

If the backend splits into multiple independently deployable services, GraphQL Federation provides a unified API gateway.

### When to Federate

Federation is appropriate when:
- Backend services are deployed independently
- Different teams own different domains
- Schema ownership needs to be decentralized
- Service boundaries align with domain boundaries

### Federation Architecture

```
Frontend
  → Apollo Federation Gateway (or @apollo/gateway)
    → User Service (me.graphqls, navigation.graphqls)
    → Render Service (render.graphqls)
    → Billing Service (billing.graphqls)
    → Prompt Service (prompt.graphqls)
    → Admin Service (admin-dashboard.graphqls)
    → Extension Service (extension.graphqls)
    → Monitoring Service (monitoring.graphqls)
```

### Implementation Option A: Apollo Federation

Uses `@apollo/subgraph` and `@apollo/gateway`:

```java
// Each service adds @key directives to shared entities
@Entity
@Key("id")
public class Tenant {
    @Id
    private String id;
    private String name;
    private String tier;
}
```

Gateway composes schemas from all subgraphs at startup or via polling.

### Implementation Option B: Spring for GraphQL Stitching

Spring for GraphQL supports schema stitching via `GraphQlSourceBuilderCustomizer`:

```java
@Configuration
public class FederationConfiguration {
    @Bean
    GraphQlSourceBuilderCustomizer stitchingCustomizer(
            @Value("${services.render}") String renderUrl,
            @Value("${services.billing}") String billingUrl) {
        return builder -> builder
                .schemaFactory((reg, wiring) -> {
                    // Fetch remote schemas and merge
                    return SchemaStitching.stitch(
                            reg.getSchema(),
                            fetchSchema(renderUrl),
                            fetchSchema(billingUrl)
                    );
                });
    }
}
```

### Federation Challenges

1. **Cross-service joins**: Entities owned by different services require `__resolveReference`
2. **Consistency**: Schema composition can fail if subgraphs are incompatible
3. **Performance**: Gateway adds latency; requires careful query planning
4. **Versioning**: Subgraph schemas must evolve compatibly
5. **Testing**: Integration testing across services is more complex

### Migration Path

1. Start with a single gateway that delegates to REST endpoints
2. Gradually convert REST endpoints to GraphQL subgraphs
3. Add `@key` directives for shared entities
4. Implement `__resolveReference` for cross-service entity resolution
5. Move from schema polling to schema registry (e.g., Apollo Studio)

---

## Technical Research

### Spring for GraphQL vs Netflix DGS

| Feature | Spring for GraphQL | Netflix DGS |
|---------|-------------------|-------------|
| Framework | Spring Boot | Spring Boot (with Netflix conventions) |
| Code-first | Yes (RuntimeWiring) | Yes (schema-first with codegen) |
| Schema-first | Yes | Yes (primary approach) |
| DataLoader | Manual registration | Built-in `@DgsDataLoader` |
| Federation | Via stitching | Native Apollo Federation support |
| Subscriptions | WebSocket | WebSocket + SSEE |
| Testing | `WebGraphQlTester` | `DgsQueryExecutor` |
| Community | Spring ecosystem | Netflix ecosystem |
| Documentation | Spring.io | Netflix GitHub |

**Current choice**: Spring for GraphQL — better integration with existing Spring Boot infrastructure and broader community support.

**Future consideration**: Netflix DGS if federation becomes a priority, as it has better native federation support.

### DataLoader Patterns

The current implementation uses `MappedBatchLoader` for all DataLoaders. Future enhancements:

1. **Caching**: Add request-level caching to avoid re-fetching the same entity
2. **Deduplication**: Automatically deduplicate keys within a batch
3. **Error handling**: Per-key error handling instead of failing the entire batch
4. **Metrics**: Track batch size, load time, cache hit rate

### Playground Security

GraphQL Playground/GraphiQL should be disabled in production:

```yaml
spring:
  graphql:
    graphiql:
      enabled: false
```

For development, restrict to localhost or VPN IPs only.

---

## Summary

| Stage | Name | Status | Key Feature |
|-------|------|--------|-------------|
| 1 | Query Aggregation Layer | ✅ Current | Read-only queries, DataLoader, REST fallback |
| 2 | Persisted Queries | 📋 Planned | Trusted documents, CDN caching, security |
| 3 | Frontend Codegen | 📋 Planned | Type-safe hooks, compile-time validation |
| 4 | Low-Risk Mutations | 📋 Planned | Preview/validate without side effects |
| 5 | Federation | 📋 Conditional | Multi-service schema composition |
