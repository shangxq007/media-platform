---
status: current
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Current Startup Profiles

> **Last validated:** 2026-06-18

## Profile Matrix

| Profile | Purpose | Database | Security | Key Features |
|---------|---------|----------|----------|--------------|
| `dev` | Local development | PostgreSQL (Docker) | Permit-all | All features on |
| `dev-postgres` | Local with PostgreSQL | PostgreSQL (Docker) | Permit-all | Flyway migrations, real DB |
| `preview` | Manual QA / smoke testing | PostgreSQL | Permit-all | Most modules disabled, minimal footprint |
| `safe-mode` | Conservative production | PostgreSQL | JWT only | Only FFmpeg + Libass, all experimental off |
| `prod` | Full production | PostgreSQL (env vars) | OAuth2 + JWT | All stable features, strict checks |
| `temporal` | Temporal workflow mode | PostgreSQL | Profile-dependent | Temporal orchestration enabled |
| `r2` | Cloudflare R2 storage | Profile-dependent | Profile-dependent | S3-compatible R2 backend |
| `ai` | AI features | Profile-dependent | Profile-dependent | Spring AI + LLM providers |
| `vault` | HashiCorp Vault secrets | Profile-dependent | Profile-dependent | KV v2 secret management |
| `oidc` | OAuth2/OIDC auth | Profile-dependent | Profile-dependent | Authentik OIDC integration |

---

## Profile Details

### dev-postgres,preview (Validated ✅)

**Activation:**
```bash
SPRING_PROFILES_ACTIVE=dev-postgres,preview
```

**What's active:**
- PostgreSQL via Docker Compose
- Flyway migrations auto-applied
- Security: permit-all (no auth)
- OpenAPI / Swagger UI
- Actuator health endpoints

**What's disabled:**
- AI, workflow, scheduler, worker, payment, commerce, sandbox, cloud-resource, social-publish
- Outbox dispatcher
- Scheduling
- Vault secrets
- Provider fallback and auto-discovery

**Startup time:** ~30 seconds

**Validation results:**
| Endpoint | Status |
|----------|--------|
| `/actuator/health` | 200 ✅ |
| `/actuator/health/readiness` | 200 ✅ |
| `/v3/api-docs` | 200 ✅ |
| `/swagger-ui/index.html` | 200 ✅ |
| `/api/v1/render/jobs` | 200 ✅ |
| `/api/v1/artifact/catalog/overview` | 200 ✅ |

---

### prod,safe-mode (Validated ✅ with known issue)

**Activation:**
```bash
SPRING_PROFILES_ACTIVE=prod,safe-mode
```

**What's active:**
- PostgreSQL (requires `SPRING_DATASOURCE_*` env vars)
- Flyway migrations
- Security: JWT auth (OAuth2 disabled)
- FFmpeg + Libass render providers only
- Pipeline DAG (no parallel external)
- Production safety checks enabled

**What's disabled:**
- All experimental render providers (Remotion, Natron, Blender, VapourSynth, Shotstack, JavaCV, OFX, GStreamer, GPAC, MLT, Bento4, Shaka, Skia)
- All experimental features (remotion, multi-provider, advanced-effects, intelligence, timeline-advanced)
- Sandbox (execution-mode: disabled, allow-in-process-eval: false)

**Startup time:** 19.108 seconds

**Known issue:**
- `ProductionSafetyValidator` throws `NoUniqueBeanDefinitionException` after startup completes
- This is a post-startup issue, not a startup failure

---

### prod (Not fully validated ⚠️)

**Activation:**
```bash
SPRING_PROFILES_ACTIVE=prod
```

**Required environment variables:**
| Variable | Purpose | Required |
|----------|---------|----------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | Yes |
| `SPRING_DATASOURCE_USERNAME` | DB username | Yes |
| `SPRING_DATASOURCE_PASSWORD` | DB password | Yes |
| `APP_JWT_SECRET` | JWT signing key | Yes |
| `APP_SECURITY_OAUTH2_ENABLED` | Enable OAuth2 | Default: true |
| `APP_CORS_ALLOWED_ORIGIN` | CORS origin | Default: `https://localhost:5173` |
| `APP_FEATURES_UNLEASH_ENABLED` | Feature flags | Default: false |
| `PLATFORM_PAYMENT_STRIPE_ENABLED` | Stripe payments | Default: true |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook | If Stripe enabled |

**What's active:**
- Full security (OAuth2 + JWT)
- H2 console disabled
- Production checks enabled
- Modulith violation budget: 0
- Outbox drain on shutdown (batch: 100)

**What's required for production:**
- OAuth2 provider configuration (Authentik)
- JWT secret must be set
- Payment provider configuration
- Fix for `ProductionSafetyValidator`

---

## Profile Combination Guide

| Use Case | Profile Combination |
|----------|-------------------|
| Local dev (no Docker) | `dev` |
| Local dev (with PostgreSQL) | `dev-postgres` |
| Manual QA / demo | `dev-postgres,preview` |
| Integration testing | `dev-postgres` + Testcontainers |
| Conservative staging | `prod,safe-mode` |
| Full staging | `prod,r2` |
| Production | `prod,r2,temporal` + env vars |
| Production with AI | `prod,r2,temporal,ai` + env vars |
| Production with Vault | `prod,r2,temporal,vault` + env vars |

---

## Profile Activation Order

Profiles are loaded in order. Later profiles override earlier ones:

```
application.yml (base)
  → application-{profile}.yml (profile-specific)
    → environment variables (highest priority)
    → mounted file: /etc/media-platform/application.yaml (optional)
```

---

## References

- [Release Candidate Readiness Checklist](../../review/release-candidate-readiness-2026-06-17.md)
- [Deployment Architecture](../08-deployment-architecture.md)
