# Security Policy

> **Last Updated:** 2026-05-18

## Authentication

**Current State:** API Key authentication via `ApiKeyAuthFilter`

| Header | Purpose |
|--------|---------|
| `X-API-Key` | API key for service-to-service auth |
| `X-Tenant-ID` | Tenant identification |
| `X-User-ID` | User identification |

**🔴 Production Blocker:** No Spring Security filter chain configured for production. JWT authentication not implemented.

## API Key Security

- API keys stored as SHA-256 hashes (never plaintext)
- Key fingerprint stored for identification
- Keys can be revoked
- Last usage tracked

## Tenant Isolation

**🔴 Production Blocker:** `TenantContext` exists but is not enforced at the data layer. All queries must be manually scoped to the current tenant.

## CORS

| Environment | Policy |
|-------------|--------|
| Development | `*` (all origins) |
| Production | Configurable whitelist |

## Rate Limiting

| Tier | Requests/min | Burst |
|------|-------------|-------|
| FREE | 30 | 10 |
| PRO | 120 | 30 |
| TEAM | 600 | 100 |
| ENTERPRISE | Unlimited | Unlimited |

## Process Execution Security

- ✅ No `Runtime.exec()` or `ProcessBuilder` in business code
- ✅ JavaCV uses JNI bindings directly
- ✅ Extension module uses allowlist-based CLI execution
- ✅ Path traversal protection
- ✅ Null byte injection prevention
- ⚠️ Apache Commons Exec still present in `extension-module`

## Data Protection

- Sensitive data redacted in logs
- Sensitive data redacted in Sentry/OpenReplay
- PII fields redacted in NLQ query results
- API keys never logged

## Security Checklist for Production

- [ ] Spring Security + JWT configured
- [ ] Tenant isolation enforced at data layer
- [ ] CORS whitelist configured
- [ ] Rate limiting per tenant
- [ ] CSRF protection enabled
- [ ] Admin endpoints protected
- [ ] API key rotation policy
- [ ] Security audit completed
