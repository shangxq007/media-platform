# Frontend API Usage

> **Purpose:** Guide for frontend developers on consuming the media-platform API.  
> **Last Updated:** 2026-05-14

---

## API Base URL

| Environment | URL |
|-------------|-----|
| Local | `http://localhost:8080/api/v1` |
| Staging | `https://staging.media-platform.example.com/api/v1` |
| Production | `https://api.media-platform.example.com/api/v1` |

---

## Authentication

### API Key (Service-to-Service)

```typescript
// api/index.ts - already configured
const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
    'X-API-Key': import.meta.env.VITE_API_KEY || ''
  }
});
```

### JWT (User Authentication - Future)

```typescript
// Future implementation
api.interceptors.request.use(config => {
  const token = localStorage.getItem('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### Tenant Header

All requests must include the tenant ID:

```typescript
// Already configured in api/index.ts interceptor
api.interceptors.request.use(config => {
  const tenant = localStorage.getItem('tenant_id') || 'tenant-1';
  config.headers['X-Tenant-ID'] = tenant;
  return config;
});
```

---

## Error Handling

All errors follow RFC 7807 ProblemDetail format:

```typescript
interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  code: string;  // e.g., "RENDER-500-001"
  timestamp?: string;
  details?: Record<string, unknown>;
}
```

### Error Code Lookup

```typescript
import { getErrorMessage } from '@/utils/i18n';

// Automatically translates error codes
const message = getErrorMessage('RENDER-500-001');
// English: "Render execution failed"
// Chinese: "渲染执行失败"
```

### Common Error Codes

| Code | Status | Description |
|------|--------|-------------|
| `COMMON-400-001` | 400 | Invalid request |
| `COMMON-401-001` | 401 | Authentication required |
| `COMMON-403-001` | 403 | Insufficient permission |
| `COMMON-404-001` | 404 | Resource not found |
| `COMMON-409-001` | 409 | Conflict |
| `COMMON-500-001` | 500 | Internal server error |
| `RENDER-500-001` | 500 | Render execution failed |
| `RENDER-503-001` | 503 | No render provider available |
| `COST-402-001` | 402 | Budget exceeded |
| `ENTITLEMENT-403-001` | 403 | Preset not allowed for tier |
| `PROMPT-403-001` | 403 | Blocked by safety policy |
| `PROMPT-404-001` | 404 | Template not found |
| `SECURITY-429-001` | 429 | Rate limit exceeded |

---

## API Clients

### Render API

```typescript
import { RenderAPI } from '@/api';

// Create render job
const job = await RenderAPI.createJob(projectId, {
  format: 'mp4',
  resolution: '1080p',
  profile: 'default_1080p',
  audioTrack: 'all',
  frameRate: 30,
  encoder: 'h264'
});

// Get job status
const status = await RenderAPI.getJob(job.id);

// List jobs
const jobs = await RenderAPI.listJobs();
```

### Entitlement API

```typescript
import { EntitlementAPI } from '@/api';

// Get current capabilities
const caps = await EntitlementAPI.getCapabilities();

// Validate export
const validation = await EntitlementAPI.validateExport('default_1080p', 'mp4');
// { allowed: true, estimatedCost: 0.0025, currency: 'USD' }
```

### Cost API

```typescript
import { CostAPI } from '@/api';

// Get budget status
const budget = await CostAPI.getBudgetStatus(tenantId);

// Estimate cost
const estimate = await CostAPI.estimateCost('javacv', 'default_1080p', 'mp4', 60);
// { estimatedCost: 0.0025, currency: 'USD' }
```

### Prompt API

```typescript
import { PromptAPI } from '@/api';

// List templates
const templates = await PromptAPI.listTemplates();

// Create template
const template = await PromptAPI.createTemplate({
  name: 'My Prompt',
  category: 'general'
});

// Render preview
const result = await PromptAPI.render(template.templateId, {
  variables: { name: 'World' },
  dryRun: true
});

// Analyze risk
const risk = await PromptAPI.analyzeRisk({
  content: 'Hello {{name}}',
  category: 'general'
});
```

### Feedback API

```typescript
import { submitOpenReplayFeedback } from '@/utils/openreplay';

// Submit user feedback
await submitOpenReplayFeedback({
  type: 'bug',
  title: 'Issue description',
  description: 'Detailed description...',
  severity: 'medium',
  renderJobId: 'job-123'  // optional
});
```

---

## OpenAPI Documentation

### Swagger UI

Access interactive API documentation at:

```
http://localhost:8080/swagger-ui.html
```

### OpenAPI Spec

Download the specification:

```bash
# JSON
curl http://localhost:8080/v3/api-docs > openapi.json

# YAML
curl http://localhost:8080/v3/api-docs.yaml > openapi.yaml

# Specific group
curl http://localhost:8080/v3/api-docs/render > render-api.json
```

---

## Rate Limiting

The API enforces rate limits:

- Default: 100 requests per minute per IP
- Returns `429 Too Many Requests` when exceeded
- Includes `Retry-After` header

```typescript
// Handle rate limiting
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 429) {
      const retryAfter = error.response.headers['retry-after'] || 60;
      console.warn(`Rate limited. Retry after ${retryAfter}s`);
    }
    return Promise.reject(error);
  }
);
```

---

## Security Best Practices

1. **Never store API keys in source code** - Use environment variables
2. **Use HTTPS in production** - Never send API keys over HTTP
3. **Rotate keys regularly** - Update keys in configuration
4. **Scope API keys** - Use minimum required permissions
5. **Monitor usage** - Check Sentry for unusual activity
6. **Handle errors gracefully** - Always check for error codes
7. **Sanitize user input** - Don't send secrets to the API
