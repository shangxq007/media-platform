# API Strategy: REST, OpenAPI, GraphQL, and MCP

> **Project**: Media Platform
> **Last Updated**: 2026-05-16

## Overview

The media platform uses a multi-protocol API strategy, with each protocol serving a specific purpose:

| Protocol | Purpose | Audience |
|----------|---------|----------|
| **REST** | External systems, file operations, webhooks, MCP | External developers, AI tools, system integrations |
| **GraphQL** | Frontend query aggregation | Vue.js frontend |
| **OpenAPI** | API documentation | All API consumers |
| **MCP** | AI tool integration | AI assistants, LLMs |

## REST for External Systems

### When to Use REST

REST is the default protocol for all backend operations:

1. **External system integrations**: Third-party developers consuming the API
2. **File upload/download**: Multipart requests for video, subtitle, and font files
3. **Webhooks**: Incoming webhooks from payment providers, monitoring services
4. **Payment callbacks**: Stripe, PayPal asynchronous payment notifications
5. **MCP tool endpoints**: AI tool invocations
6. **CRUD operations**: All create, update, delete operations
7. **Health checks**: Kubernetes readiness/liveness probes

### REST API Structure

The REST API follows a resource-based URL convention:

```
/api/v1/
  /me                      — Current user profile
  /me/navigation           — User navigation
  /me/routes               — Route visibility decisions
  /projects                — Project CRUD
  /projects/{id}/export    — Export panel data
  /render/jobs             — Render job management
  /render/presets          — Available export presets
  /render/export/validate  — Export validation
  /prompts/templates       — Prompt template CRUD
  /prompts/templates/{id}  — Template detail
  /entitlements/me/capabilities — User capabilities
  /billing/tenants/{id}/budget  — Budget status
  /billing/cost/estimate        — Cost estimation
  /admin/dashboard              — Admin dashboard data
  /admin/navigation/routes      — Route management
  /admin/graphql/queries        — Persisted query management (future)
  /remote-worker/workers         — Worker management
  /remote-worker/jobs            — Job distribution
```

### REST Conventions

- **Versioning**: URL-based (`/api/v1/`)
- **Authentication**: JWT session or API key
- **Content-Type**: `application/json` (default), `multipart/form-data` (file uploads)
- **Error format**: Structured error with `errorCode`, `message`, `traceId`
- **Pagination**: Cursor-based with `limit` and `cursor` parameters
- **Idempotency**: POST requests support `Idempotency-Key` header

## GraphQL for Frontend Aggregation

### When to Use GraphQL

GraphQL is used exclusively for frontend data loading:

1. **Page-level data loading**: Each page loads all its data in one query
2. **Aggregation queries**: Combining data from multiple backend modules
3. **Nested data**: Fetching related entities in a single request
4. **Reducing over-fetching**: Frontend requests only the fields it needs

### GraphQL Boundaries

GraphQL is **not** used for:
- Mutations (all writes go through REST)
- File uploads/downloads
- Webhook processing
- External system integration
- Health checks

### GraphQL Endpoint

```
POST /graphql
```

- **Authentication**: Session cookie (same as REST)
- **Content-Type**: `application/json`
- **Response**: Standard GraphQL JSON response
- **Error handling**: GraphQL errors with extensions (`errorCode`, `traceId`, `details`)

### Fallback Strategy

The frontend always implements REST fallback for GraphQL queries:

```typescript
const { data } = useGraphQLQuery({
  query: ME_OVERVIEW,
  fallbackFn: () => fetch('/api/v1/me/overview').then(r => r.json()),
})
```

This ensures the application remains functional if the GraphQL endpoint is unavailable.

## OpenAPI for API Documentation

### OpenAPI Configuration

The project uses SpringDoc OpenAPI for API documentation:

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
  show-actuator: false
```

### API Groups

The OpenAPI documentation is organized into groups:

| Group | Path | Description |
|-------|------|-------------|
| User API | `/api/v1/me/**` | User profile, navigation, capabilities |
| Project API | `/api/v1/projects/**` | Project CRUD |
| Render API | `/api/v1/render/**` | Render jobs, presets, validation |
| Prompt API | `/api/v1/prompts/**` | Prompt templates, executions |
| Billing API | `/api/v1/billing/**` | Budget, cost estimation |
| Entitlement API | `/api/v1/entitlements/**` | Capability checks |
| Admin API | `/api/v1/admin/**` | Admin dashboard, route management |
| Worker API | `/api/v1/remote-worker/**` | Worker and job management |

### Security Schemes

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
    apiKeyAuth:
      type: apiKey
      in: header
      name: X-API-Key
```

### API Versioning

OpenAPI documents the current API version. Breaking changes require a new version:
- URL-based versioning: `/api/v1/` → `/api/v2/`
- Deprecation notices in OpenAPI annotations
- Sunset headers for deprecated endpoints

## MCP for AI Tool Integration

### MCP Protocol

The Model Context Protocol (MCP) provides a standardized interface for AI tools to interact with the platform:

```
AI Assistant (Claude, GPT, etc.)
  → MCP Client
    → MCP Server (HTTP or stdio)
      → REST API endpoints
```

### MCP Tool Design

Each MCP tool maps to a REST endpoint:

| MCP Tool | REST Endpoint | Purpose |
|----------|---------------|---------|
| `get_user_profile` | `GET /api/v1/me` | Get current user info |
| `list_projects` | `GET /api/v1/projects` | List user projects |
| `get_project` | `GET /api/v1/projects/{id}` | Get project details |
| `submit_render_job` | `POST /api/v1/render/jobs` | Submit a render job |
| `get_render_job` | `GET /api/v1/render/jobs/{id}` | Get render job status |
| `list_prompt_templates` | `GET /api/v1/prompts/templates` | List prompt templates |
| `get_capabilities` | `GET /api/v1/entitlements/me/capabilities` | Get user capabilities |
| `validate_export` | `POST /api/v1/render/export/validate` | Validate export settings |

### MCP Server Implementation

```java
@Component
public class PlatformMcpServer {

    @Tool(description = "Get the current user's profile")
    public UserProfile getUserProfile() {
        return userRepository.findCurrent();
    }

    @Tool(description = "Submit a render job for a project")
    public RenderJobResponse submitRenderJob(
            @ToolParam(description = "Project ID") String projectId,
            @ToolParam(description = "Export preset") String preset) {
        return renderJobService.create(projectId, preset);
    }
}
```

### MCP Security

- MCP tools use the same authentication as REST APIs
- Tool descriptions are sanitized to avoid prompt injection
- Rate limiting applies to MCP endpoints
- Audit events are recorded for all MCP tool invocations

## Protocol Boundaries Summary

| Concern | REST | GraphQL | OpenAPI | MCP |
|---------|------|---------|---------|-----|
| Frontend queries | Fallback | ✅ Primary | — | — |
| External systems | ✅ Primary | — | Docs | — |
| File operations | ✅ Only | ❌ | Docs | — |
| Webhooks | ✅ Only | ❌ | Docs | — |
| AI tools | ✅ Backend | ❌ | — | ✅ Frontend |
| Documentation | — | Schema | ✅ Primary | Tool descriptions |
| Mutations | ✅ Only | ❌ (Stage 4+) | Docs | Via REST |
| Health checks | ✅ Only | ❌ | — | — |

## Request Flow

```
External Client
  → REST API (with API key or JWT)
    → Module services

Vue.js Frontend
  → GraphQL (with session cookie)
    → federation-query-module
      → Module services
  ↓ (fallback)
  → REST API (with session cookie)
    → Module services

AI Assistant
  → MCP (with API key)
    → REST API
      → Module services

Developer
  → OpenAPI docs (Swagger UI)
    → Reads API documentation
```

## Error Code Strategy

All protocols use the same error code system:

| Code | Description | REST | GraphQL | MCP |
|------|-------------|------|---------|-----|
| `COMMON-400-001` | Invalid request | ✅ | ✅ | ✅ |
| `COMMON-401-001` | Auth required | ✅ | ✅ | ✅ |
| `COMMON-403-001` | Insufficient permission | ✅ | ✅ | ✅ |
| `COMMON-404-001` | Not found | ✅ | ✅ | ✅ |
| `COMMON-500-001` | Internal error | ✅ | ✅ | ✅ |
| `COMMON-502-001` | Integration error | ✅ | ✅ | ✅ |
| `SECURITY-429-001` | Rate limit exceeded | ✅ | ✅ | ✅ |

## Rate Limiting

| Protocol | Limit | Window | Scope |
|----------|-------|--------|-------|
| REST | 100 req/min | 60 seconds | Per IP / API key |
| GraphQL | 100 req/min | 60 seconds | Per session |
| MCP | 60 req/min | 60 seconds | Per API key |

GraphQL and REST share the same rate limiter since they use the same session.

## Monitoring

All API requests are monitored with:
- Request count by endpoint and protocol
- Error rate by error code
- Latency percentiles (p50, p95, p99)
- GraphQL query complexity distribution
- REST fallback rate (GraphQL → REST)
