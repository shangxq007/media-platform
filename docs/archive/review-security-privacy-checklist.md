# Security and Privacy Review Checklist

> **Purpose:** Verify security and privacy measures before production.  
> **Reviewer:** _______________  
> **Date:** _______________  
> **Classification:** CONFIDENTIAL

---

## Key Management

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | No API keys in source code | ⬜ | |
| 2 | No passwords in source code | ⬜ | |
| 3 | No private keys in source code | ⬜ | |
| 4 | No secrets in config files | ⬜ | |
| 5 | .env in .gitignore | ⬜ | |
| 6 | .env.local in .gitignore | ⬜ | |
| 7 | .env.example uses placeholder values | ⬜ | |
| 8 | Sentry DSN via env var only | ⬜ | |
| 9 | OpenReplay key via env var only | ⬜ | |
| 10 | Database credentials via env var | ⬜ | |
| 11 | Redis credentials via env var | ⬜ | |
| 12 | S3/MinIO credentials via env var | ⬜ | |

## Secret Redaction

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | API keys redacted in Sentry events | ⬜ | |
| 2 | Passwords redacted in Sentry events | ⬜ | |
| 3 | Tokens redacted in Sentry events | ⬜ | |
| 4 | Sensitive headers redacted | ⬜ | |
| 5 | Request body sanitized | ⬜ | |
| 6 | Stack trace vars sanitized | ⬜ | |
| 7 | OpenReplay text sanitized | ⬜ | |
| 8 | OpenReplay input sanitized | ⬜ | |
| 9 | OpenReplay network data sanitized | ⬜ | |

## Prompt Sensitive Variable Handling

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sensitive variables marked in schema | ⬜ | |
| 2 | Sensitive variables redacted in render output | ⬜ | |
| 3 | Sensitive variables not stored in execution records | ⬜ | |
| 4 | Sensitive variables redacted in audit logs | ⬜ | |
| 5 | Hash stored instead of plaintext | ⬜ | |
| 6 | Secret scanner detects API keys in prompts | ⬜ | |
| 7 | Secret scanner detects passwords in prompts | ⬜ | |
| 8 | PROMPT-422-002 on secret detection | ⬜ | |

## File Upload Security

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Subtitle file type validation | ⬜ | |
| 2 | Font file type validation | ⬜ | |
| 3 | Effect pack file validation | ⬜ | |
| 4 | File size limits | ⬜ | |
| 5 | No executable file upload | ⬜ | |
| 6 | Path traversal prevention | ⬜ | |

## Extension Module Security

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Command whitelist enforced | ⬜ | |
| 2 | No Runtime.exec / sh -c | ⬜ | |
| 3 | No arbitrary script execution | ⬜ | |
| 4 | Sandbox execution environment | ⬜ | |
| 5 | Extension permissions checked | ⬜ | |

## Remote Worker Security

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Worker authentication | ⬜ | Stub |
| 2 | Worker TLS communication | ⬜ | Stub |
| 3 | Worker job isolation | ⬜ | |
| 4 | Worker resource limits | ⬜ | |
| 5 | Worker deregistration on failure | ⬜ | |

## Infrastructure Credentials

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | S3/MinIO credentials via env var | ⬜ | |
| 2 | Database credentials via env var | ⬜ | |
| 3 | Redis credentials via env var | ⬜ | |
| 4 | Temporal credentials via env var | ⬜ | |
| 5 | No credentials in Docker images | ⬜ | |

## Sentry/OpenReplay Data Handling

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sentry PII scrubbing enabled | ⬜ | |
| 2 | OpenReplay privacy mode for inputs | ⬜ | |
| 3 | Session replay excludes passwords | ⬜ | |
| 4 | IP address handling compliant | ⬜ | |
| 5 | Data retention policy defined | ⬜ | Future |

## User Data

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | User data deletion capability | ⬜ | Future |
| 2 | Data retention policy | ⬜ | Future |
| 3 | GDPR/CCPA compliance | ⬜ | Future |
| 4 | Data export capability | ⬜ | Future |
| 5 | Audit log for data access | ⬜ | |

## Audit Coverage

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | All API operations audited | ⬜ | |
| 2 | Audit linked to tenant | ⬜ | |
| 3 | Audit linked to user | ⬜ | |
| 4 | Audit immutable | ⬜ | |
| 5 | Audit searchable by category | ⬜ | |
| 6 | Prompt operations audited | ⬜ | |
| 7 | Cost operations audited | ⬜ | |
| 8 | Anomaly events audited | ⬜ | |

## Authentication/Authorization

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Authentication layer | ⬜ | NOT IMPLEMENTED |
| 2 | Role-based access control | ⬜ | NOT IMPLEMENTED |
| 3 | API key authentication | ⬜ | Stub |
| 4 | Tenant isolation | ⬜ | Partial |
| 5 | Session management | ⬜ | NOT IMPLEMENTED |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| Key Management | ___/12 | 12 | |
| Secret Redaction | ___/9 | 9 | |
| Prompt Sensitive Vars | ___/8 | 8 | |
| File Upload Security | ___/6 | 6 | |
| Extension Security | ___/5 | 5 | |
| Remote Worker Security | ___/5 | 5 | |
| Infrastructure Credentials | ___/5 | 5 | |
| Sentry/OpenReplay Data | ___/5 | 5 | |
| User Data | ___/5 | 5 | |
| Audit Coverage | ___/8 | 8 | |
| Auth/Authz | ___/5 | 5 | |
| **Total** | ___/73 | **73** | |

**Reviewer Signature:** _______________  
**Date:** _______________
