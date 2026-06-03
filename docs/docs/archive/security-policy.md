# Security Policy

> **Purpose:** Security policy for the media-platform API.  
> **Last Updated:** 2026-05-14  
> **Classification:** INTERNAL

---

## Authentication

### Internal Services
- **Method:** API Key via `X-API-Key` header
- **Scope:** Service-to-service communication
- **Rotation:** Keys should be rotated every 90 days
- **Storage:** Keys stored in environment variables or secrets manager

### External Systems / MCP
- **Method:** OAuth2 / JWT (planned)
- **Scopes:** `read`, `write`, `admin`, `render:read`, `render:write`, `prompt:read`, `prompt:write`
- **Token expiry:** 1 hour (access), 30 days (refresh)
- **Storage:** Tokens stored client-side only

### User Authentication (Not Implemented)
- **Status:** 🔴 CRITICAL - No user authentication layer
- **Risk:** Anyone can access any tenant's data
- **Mitigation:** API Key auth provides basic service-to-service security

---

## Transport Security

### HTTPS/TLS
- **Policy:** All production traffic must use TLS 1.3
- **HSTS:** `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- **Certificate:** Valid CA-signed certificate required
- **Redirect:** HTTP → HTTPS automatic redirect

### CORS
- **Policy:** Restrict to known origins
- **Development:** `http://localhost:3000`, `http://localhost:8080`
- **Production:** Configure via `app.identity.allowed-origins`
- **Credentials:** Allowed for authenticated requests

---

## Rate Limiting

- **Default:** 100 requests per minute per IP
- **Response:** `429 Too Many Requests` with `Retry-After: 60`
- **Error code:** `SECURITY-429-001`
- **Whitelist:** Configured IPs bypass rate limiting
- **Configuration:** `app.identity.rate-limit-enabled`, `app.identity.rate-limit-requests-per-minute`

---

## IP Whitelisting

- **Policy:** Optional IP whitelist for API access
- **Configuration:** `app.identity.ip-whitelist`
- **Response:** `403 Forbidden` for non-whitelisted IPs
- **Error code:** `SECURITY-403-001`
- **Bypass:** Not recommended in production

---

## Data Protection

### Sensitive Data Classification

| Data Type | Classification | Handling |
|-----------|---------------|----------|
| API Keys | CRITICAL | Hashed in storage, never logged |
| Passwords | CRITICAL | Never stored or logged |
| JWT Tokens | HIGH | Client-side only, 1-hour expiry |
| Prompt sensitive variables | HIGH | Redacted in records and logs |
| S3/DB credentials | CRITICAL | Environment variables only |
| PII (email, name) | MEDIUM | Available to admin scope only |
| Render artifacts | LOW | Accessible to tenant members |
| Audit logs | MEDIUM | Immutable, 1-year retention |

### Desensitization Rules

**API Responses:**
- API keys: Never returned in responses
- Passwords: Never stored or logged
- Prompt sensitive variables: Redacted with `[REDACTED]`
- Internal IDs: Masked in error messages

**Logs:**
- Request bodies with sensitive fields → redacted
- Stack traces with API keys → redacted
- Prompt variables marked sensitive → redacted

**Monitoring (Sentry/OpenReplay):**
- Request headers: `authorization`, `cookie`, `x-api-key` → `[REDACTED]`
- Request body: API keys, passwords, tokens → `[REDACTED]`
- Input fields: Passwords, API keys → masked

---

## Input Validation

### File Uploads
- **Subtitle files:** SRT, VTT, ASS only
- **Font files:** TTF, OTF, WOFF only
- **Effect packs:** JSON schema validation
- **Size limits:** Configurable per file type
- **Path traversal:** Sanitized

### Extension Module
- **Command whitelist:** Only approved commands
- **No Runtime.exec:** Blocked
- **No sh -c:** Blocked
- **Sandbox:** All extensions run in sandbox
- **Resource limits:** CPU, memory, disk quotas

---

## Audit Logging

### Events Logged

| Event | Category | Retention |
|-------|----------|-----------|
| Authentication success | SECURITY | 1 year |
| Authentication failure | SECURITY | 1 year |
| Rate limit triggered | SECURITY | 90 days |
| IP blocked | SECURITY | 90 days |
| Template creation | PROMPT | 1 year |
| Template modification | PROMPT | 1 year |
| Risk analysis | PROMPT | 1 year |
| Prompt execution | PROMPT | 1 year |
| Render job submission | RENDER | 1 year |
| Render job completion | RENDER | 1 year |
| Cost operation | COST | 3 years |
| Anomaly detected | USAGE | 1 year |
| Reconciliation run | RECON | 3 years |

### Audit Record Fields

```json
{
  "actorType": "user|service|system",
  "actorId": "user-123",
  "action": "CREATE",
  "resourceType": "render_job",
  "resourceId": "job-456",
  "tenantId": "tenant-1",
  "category": "RENDER",
  "payload": { "..." : "..." },
  "timestamp": "2026-05-14T12:00:00Z",
  "ipAddress": "10.0.0.1",
  "userAgent": "Mozilla/5.0..."
}
```

---

## Security Headers

All API responses include:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

---

## Vulnerability Management

### Known Limitations

| Issue | Severity | Mitigation | Status |
|-------|----------|------------|--------|
| No user authentication | CRITICAL | API Key auth for services | Open |
| No tenant data isolation | CRITICAL | TenantContext filtering | Open |
| No CSRF protection | MEDIUM | CORS restrictions | Open |
| No request size limits | MEDIUM | Proxy-level limits | Open |
| No SQL injection prevention | LOW | jOOQ parameterized queries | Mitigated |
| No XSS prevention | LOW | Vue.js auto-escaping | Mitigated |

### Security Testing

- Run `./gradlew test` for unit/integration tests
- Run security scan: `dependencyCheckAnalyze` (if configured)
- Penetration testing required before production

---

## Incident Response

### Severity Levels

| Level | Description | Response Time |
|-------|-------------|---------------|
| P0 | Data breach, active attack | Immediate |
| P1 | Authentication bypass | 1 hour |
| P2 | Rate limit evasion | 4 hours |
| P3 | Suspicious activity | 24 hours |

### Response Procedures

1. **Detect:** Sentry alert or anomaly detection
2. **Assess:** Determine scope and impact
3. **Contain:** Block affected IPs/keys
4. **Investigate:** Review audit logs
5. **Remediate:** Fix vulnerability
6. **Notify:** Inform affected tenants
7. **Document:** Update security policy
