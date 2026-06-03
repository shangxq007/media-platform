# Secrets and Local Environment Governance

> **Last updated**: 2026-05-11
> **Scope**: Local development, Docker Compose, CI/CD, and production readiness

## Principles

1. **Never commit real secrets** — API keys, tokens, passwords, private keys, certificates.
2. **Use placeholder values** in `.env.example` and documentation.
3. **Local development** uses `.env` (gitignored) with development-only credentials.
4. **Production secrets** must be injected via a secrets manager (e.g., HashiCorp Vault, AWS Secrets Manager, Kubernetes Secrets).
5. **Rotate credentials** regularly and on any suspected compromise.

## File Classification

| File | Committed | Purpose |
|------|-----------|---------|
| `.env.example` | ✅ Yes | Template with placeholder values |
| `.env` | ❌ No | Local development credentials |
| `.env.local` | ❌ No | Machine-specific overrides |
| `application.yml` | ✅ Yes | Non-sensitive defaults only |
| `application-prod.yml` | ✅ Yes | References `${...}` env vars, no literals |
| `docker-compose.yml` | ✅ Yes | Uses `secret` for local dev only |
| `*.pem`, `*.key`, `*.p12` | ❌ No | Private keys and certificates |
| `*-credentials.json` | ❌ No | Cloud provider credentials |

## Local Development

### Database

```
POSTGRES_PASSWORD=change-me-in-local-env
```

The `docker-compose.yml` uses `secret` as the local development password. This is acceptable for local-only containers.

### Application

```bash
# Copy template
cp .env.example .env

# Edit with your local values
# DO NOT use production credentials
```

## Secret Scanning Rules

The following patterns are flagged as sensitive in metadata sanitization:

- `password`, `token`, `secret`
- `apikey`, `api_key`
- `auth`, `authorization`
- `credential`, `credentials`
- `ssn`, `creditcard`, `credit_card`

These keys are automatically stripped from user behavior event metadata by `BehaviorEventService.sanitizeMetadata()`.

## Production Checklist

- [ ] All secrets injected via environment variables or secrets manager
- [ ] No hardcoded credentials in source code
- [ ] No credentials in YAML/properties files
- [ ] Database passwords are strong and rotated
- [ ] API keys are scoped and rotatable
- [ ] TLS certificates are managed (e.g., cert-manager, ACM)
- [ ] Secret access is audited (who accessed what, when)
- [ ] `.env` files are in `.gitignore`

## Module Responsibilities

### `secrets-config-module`

- Provides a `SecretService` port for resolving secret references
- In development: resolves from environment variables
- In production: delegates to an external secrets manager via SPI
- Does NOT store secrets itself

### `user-analytics-module`

- `BehaviorEventService.sanitizeMetadata()` strips sensitive keys from event metadata
- Events never contain passwords, tokens, or API keys

### `identity-access-module`

- API keys are stored as fingerprints, not plaintext
- Tenant isolation enforced on all secret access

## Metadata Sanitization (Phase 20)

The `BehaviorEventService.sanitizeMetadata()` strips the following key patterns from event metadata:

**Network**: `ip`, `ip_address`, `user_ip`, `x-forwarded-for`, `x-real-ip`
**Browser**: `user-agent`, `useragent`
**Session**: `cookie`, `session_id`, `sessionid`
**Auth**: `password`, `passwd`, `pwd`, `token`, `secret`, `api_secret`, `apikey`, `api_key`, `auth`, `authorization`, `bearer`
**Credentials**: `credential`, `credentials`, `ssn`, `social_security`
**Payment**: `creditcard`, `credit_card`, `cvv`

## Gitignored Secret Files (Phase 20)

The following patterns are now in `.gitignore`:

```
*.pem, *.key, *.p12, *.pfx
*-credentials.json, *-service-account.json
secrets/, .ssh/
*.crt, *.csr
```

## Incident Response

If a secret is accidentally committed:

1. **Immediately rotate** the exposed credential.
2. **Remove from git history** using `git filter-branch` or BFG Repo-Cleaner.
3. **Audit access logs** for any unauthorized use.
4. **Document the incident** in `docs/human-review-needed.md`.
