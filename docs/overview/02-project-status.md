# Project Status & Statistics

> **Last Updated:** 2026-06-22
> **Last Validated Against Code:** 2026-06-22

## Module Status

| # | Module | Prod Files | Test Files | Status | Notes |
|---|--------|-----------|------------|--------|-------|
| 1 | `shared-kernel` | 127 | 20 | ✅ Stable | Root dependency, no outgoing deps |
| 2 | `platform-app` | 101 | 47 | ✅ Stable | Composition root, security, production safety |
| 3 | `render-module` | 600 | 138 | ✅ Stable | Largest module, 7+ providers, state machine, incremental render |
| 4 | `workflow-module` | 22 | 6 | ✅ Stable | Temporal workflows + local fallback |
| 5 | `ai-module` | 33 | 8 | ⚠️ Gateway | Gateway + model routing, stub provider |
| 6 | `notification-module` | 44 | 14 | ✅ Stable | Multi-channel (Novu, email, SMS, webhook) |
| 7 | `storage-module` | 19 | 6 | ✅ Stable | S3-compatible blob storage |
| 8 | `delivery-module` | 36 | 4 | ✅ Stable | 6 protocol adapters |
| 9 | `prompt-module` | 23 | 4 | ✅ Stable | Template CRUD, versioning, risk analysis |
| 10 | `config-module` | 4 | 0 | ⚠️ Stub | Minimal key-value, no tests |
| 11 | `cloud-resource-module` | 9 | 1 | ⚠️ Stub | In-memory catalog, stub provider |
| 12 | `secrets-config-module` | 24 | 3 | ✅ Stable | Vault KV v2 + env fallback |
| 13 | `extension-module` | 55 | 19 | ✅ Stable | Extension lifecycle, sandbox, CLI tools |
| 14 | `datasource-module` | 9 | 2 | ⚠️ Partial | Multi-datasource config, noop federation |
| 15 | `observability-module` | 9 | 3 | ⚠️ Partial | Circuit breaker/SLA, no tracing infra |
| 16 | `outbox-event-module` | 6 | 3 | ✅ Stable | jOOQ outbox, idempotency, exponential backoff |
| 17 | `audit-compliance-module` | 40 | 15 | ✅ Stable | Audit trail, anomaly detection |
| 18 | `scheduler-module` | 7 | 1 | ⚠️ Stub | In-memory job registry, no cron |
| 19 | `identity-access-module` | 99 | 34 | ✅ Stable | RBAC, API keys, project export/import |
| 20 | `quota-billing-module` | 10 | 1 | ⚠️ Stub | In-memory only, no persistence |
| 21 | `commerce-module` | 28 | 4 | ✅ Stable | Checkout flow, catalog, cart |
| 22 | `payment-module` | 30 | 4 | ✅ Stable | Real Stripe + Hyperswitch HTTP clients |
| 23 | `billing-module` | 86 | 15 | ✅ Stable | Subscriptions, usage, credit wallet, reconciliation |
| 24 | `entitlement-module` | 61 | 17 | ✅ Stable | Grants, bundles, quotas, feature flags |
| 25 | `policy-governance-module` | 39 | 20 | ✅ Stable | ABAC, OpenFeature, feature flags |
| 26 | `artifact-catalog-module` | 19 | 4 | ✅ Stable | Artifact lifecycle, GC, integrity scan |
| 27 | `sandbox-runtime-module` | 15 | 2 | ✅ Stable | Script engine exec, external worker |
| 28 | `sandbox-worker` | — | — | ✅ Stable | Separate deployable |
| 29 | `federation-query-module` | 101 | 43 | ⚠️ Mixed | Stub core, full GraphQL + NLQ |
| 30 | `user-analytics-module` | — | — | ✅ Stable | User analytics |
| 31 | `compatibility-migration-module` | — | — | ✅ Stable | Schema migration helpers |
| 32 | `remote-render-worker` | 7 | — | ✅ Stable | Separate render worker |
| 33 | `social-publish-module` | 29 | 1 | ⚠️ Partial | Service layer, stub adapters |
| 34 | `product-layer-module` | — | — | ✅ Stable | Product layer abstraction |
| 35 | `spring-ai-adapter` | 2 | 0 | ⚠️ Broken | Compilation error (TenantLitellmKeyService) |

## Statistics

| Metric | Value | Source |
|--------|-------|--------|
| Gradle Subprojects | 35 | `settings.gradle.kts` |
| Java Source Files | ~1,800+ | `find` count |
| Backend Test Files | ~500+ | `find` count |
| Flyway Migrations | 1 (V1 consolidated) | `db/migration/` |
| Database Tables | 133 | V1 `CREATE TABLE` count |
| V1 SQL Lines | 2,339 | `wc -l` |
| Frontend TSX Files | 34 | `find *.tsx` |
| Frontend TS Files | 101 | `find *.ts` |
| Application Profiles | 14 + base | `application*.yml` |
| K8s Manifests | 35 | `find k8s` |
| GitOps Files | 44 | `find gitops` |
| CI Workflows | 1 | `.github/workflows/` |
| Scripts | 12 | `find scripts` |

## Modularity Status

| Check | Status | Details |
|-------|--------|---------|
| `ModularityTest` | ✅ PASS | Re-enabled, zero-tolerance assertion |
| Allowed violations | 2 | `identity → artifact`, `identity → storage` |
| Unregistered violations | 0 | None |
| NamedInterfaces | 64 | Across all modules |

## Security Status

| Check | Status |
|-------|--------|
| ProductionSafetyValidator | ✅ Active — 14+ checks on startup |
| JWT fail-fast | ✅ Rejects insecure defaults in constructor |
| OIDC/JIT provisioning | ✅ Authentik integration with role mapping |
| Tenant isolation | ✅ JWT-only tenant resolution, header guard |

## Production Readiness

### Ready
- Render pipeline with 7+ providers
- Billing and subscription lifecycle
- Payment (Stripe + Hyperswitch)
- Identity (JWT + OIDC + RBAC)
- Content delivery (6 adapters)
- Audit and compliance
- Feature flags (OpenFeature + JDBC)

### Not Ready
- Quota persistence (in-memory only)
- Scheduler (in-memory only)
- Cloud resource provisioning (stub)
- Observability (no distributed tracing)
- Spring AI adapter (compilation error)

## References

- [Project Intelligence Report](../review/project-intelligence-report.md)
- [Source of Truth Validation Report](../review/source-of-truth-validation-report.md)
- [Known Limitations](../review/known-limitations.md)
