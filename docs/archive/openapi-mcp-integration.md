# OpenAPI / MCP Integration with Authentication and Security

## Overview

This document describes the OpenAPI documentation, authentication, and security infrastructure for the media-platform API.

## OpenAPI Documentation

### Access

| Endpoint | Description |
|----------|-------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI (interactive API docs) |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON specification |
| `http://localhost:8080/v3/api-docs.yaml` | OpenAPI YAML specification |
| `http://localhost:8080/v3/api-docs/public-v1` | Public API v1 group |
| `http://localhost:8080/v3/api-docs/render` | Render Pipeline group |
| `http://localhost:8080/v3/api-docs/prompt` | Prompt Engineering group |
| `http://localhost:8080/v3/api-docs/cost` | Cost & Entitlement group |
| `http://localhost:8080/v3/api-docs/monitoring` | Monitoring & Feedback group |
| `http://localhost:8080/v3/api-docs/worker` | Remote Worker group |

### API Groups

| Group | Path Prefix | Description |
|-------|-------------|-------------|
| `public-v1` | `/api/v1/**` | All public APIs |
| `render` | `/api/v1/render/**`, `/api/v1/artifacts/**` | Render Pipeline |
| `prompt` | `/api/v1/prompts/**` | Prompt Engineering |
| `cost` | `/api/v1/billing/**`, `/api/v1/entitlements/**` | Cost & Entitlement |
| `monitoring` | `/api/v1/audit/**`, `/api/v1/feedback/**` | Monitoring & Feedback |
| `worker` | `/api/v1/remote-worker/**` | Remote Worker |
| `internal` | `/api/v1/internal/**` | Internal APIs |
| `actuator` | `/actuator/**` | Spring Boot Actuator |

### Versioning

- Current version: `v1`
- Version is specified in the path: `/api/v1/...`
- OpenAPI spec includes version metadata
- Future v2 APIs will be available at `/api/v2/...`

## Authentication

### API Key Authentication

For service-to-service communication:

```bash
curl -H "X-API-Key: your-api-key" \
  http://localhost:8080/api/v1/render/jobs
```

**Configuration:**
```yaml
app:
  identity:
    api-key-auth-enabled: true
    api-keys:
      service-a: "tenant-1"
      service-b: "tenant-2"
```

### JWT / OAuth2 (Future)

JWT authentication is planned for external system integration. The OpenAPI spec already includes the `bearerAuth` security scheme.

```bash
# Future usage
curl -H "Authorization: Bearer <jwt-token>" \
  http://localhost:8080/api/v1/render/jobs
```

### Scopes (Future)

| Scope | Access |
|-------|--------|
| `read` | Read-only access to all resources |
| `write` | Read + create/update resources |
| `admin` | Full access including delete |
| `render:read` | Read render jobs and artifacts |
| `render:write` | Submit and manage render jobs |
| `prompt:read` | Read prompt templates |
| `prompt:write` | Create and manage prompt templates |
| `cost:read` | Read cost and entitlement data |
| `cost:write` | Modify budgets and entitlements |

## Security Policies

### Rate Limiting

```yaml
app:
  identity:
    rate-limit-enabled: true
    rate-limit-requests-per-minute: 100
```

- Returns `429 Too Many Requests` with `SECURITY-429-001` error code
- Includes `Retry-After: 60` header
- Whitelisted IPs bypass rate limiting

### IP Whitelisting

```yaml
app:
  identity:
    ip-whitelist:
      - "10.0.0.0/8"
      - "172.16.0.0/12"
```

- Non-whitelisted IPs receive `403 Forbidden` with `SECURITY-403-001`
- Applied before rate limiting

### CORS

```yaml
app:
  identity:
    allowed-origins:
      - "https://app.yourdomain.com"
      - "https://staging.yourdomain.com"
```

- Configurable allowed origins
- Development default: `http://localhost:3000`, `http://localhost:8080`

### HTTPS/TLS

- Production deployment must use TLS 1.3
- HSTS header recommended
- API endpoints redirect HTTP to HTTPS

### Sensitive Data Desensitization

The following data is never returned in API responses or stored in logs:

| Data Type | Handling |
|-----------|----------|
| API Keys | Redacted in responses, hashed in storage |
| Passwords | Never stored or logged |
| JWT Tokens | Only stored client-side |
| Prompt sensitive variables | Redacted in execution records |
| S3/DB credentials | Environment variables only |
| PII (email, name) | Available only to admin scope |

### Security Headers

All API responses include:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
```

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `SECURITY-429-001` | 429 | Rate limit exceeded |
| `SECURITY-401-001` | 401 | API key authentication required |
| `SECURITY-403-001` | 403 | IP not in whitelist |
| `COMMON-401-001` | 401 | Authentication required |
| `COMMON-403-001` | 403 | Insufficient permission |

## Audit Logging

All authentication and security events are logged:

| Event | Audit Category | Details |
|-------|---------------|---------|
| API key validation | SECURITY | IP, timestamp, result |
| Rate limit triggered | SECURITY | IP, request count |
| IP blocked | SECURITY | IP, reason |
| Auth failure | SECURITY | IP, error code |
| Token issued | AUTH | User, tenant, scopes |

## MCP Integration

The OpenAPI specification can be used with MCP (Model Context Protocol) tools:

1. Export the OpenAPI spec: `GET /v3/api-docs`
2. Import into MCP tool configuration
3. Configure authentication (API Key or JWT)
4. MCP tools can now call platform APIs

### Example MCP Configuration

```json
{
  "mcpServers": {
    "media-platform": {
      "command": "npx",
      "args": ["-y", "@your/mcp-client"],
      "env": {
        "OPENAPI_SPEC_URL": "https://api.media-platform.example.com/v3/api-docs",
        "API_KEY": "${MP_API_KEY}"
      }
    }
  }
}
```

## SDK Generation

The OpenAPI spec can be used to generate client SDKs:

```bash
# Java
openapi-generator-cli generate -i openapi.json -g java -o sdk/java

# JavaScript/TypeScript
openapi-generator-cli generate -i openapi.json -g typescript-fetch -o sdk/js

# Python
openapi-generator-cli generate -i openapi.json -g python -o sdk/python
```
