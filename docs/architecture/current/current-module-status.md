---
status: current
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Current Module Status

> **Last validated:** 2026-06-18

## Module Status Overview

| Module | Gradle Module | Modulith Type | Test Coverage | Status | Notes |
|--------|---------------|---------------|---------------|--------|-------|
| shared-kernel | `shared-kernel` | OPEN | — | ✅ Stable | Root dependency, no outgoing deps |
| platform-app | `platform-app` | — | 42% | ✅ Stable | Composition root, depends on all 30 |
| render-module | `render-module` | CLOSED | 26% | ✅ Stable | Largest module (453 main files), 9 active providers |
| workflow-module | `workflow-module` | CLOSED | — | ⚠️ Disabled (preview) | Temporal workflows, disabled in preview |
| ai-module | `ai-module` | CLOSED | — | ⚠️ Isolated | Not in platform-app runtime path |
| notification-module | `notification-module` | CLOSED | — | ✅ Stable | Multi-channel notifications |
| storage-module | `storage-module` | CLOSED | — | ✅ Stable | S3-compatible object storage |
| delivery-module | `delivery-module` | CLOSED | — | ⚠️ Disabled (preview) | SFTP/SMB/WebDAV delivery |
| prompt-module | `prompt-module` | CLOSED | — | ✅ Stable | Prompt template management |
| config-module | `config-module` | CLOSED | **0%** | ⚠️ No tests | Zero test files |
| cloud-resource-module | `cloud-resource-module` | CLOSED | 11% | ⚠️ Stub | Stub provider only |
| secrets-config-module | `secrets-config-module` | CLOSED | — | ⚠️ Disabled (preview) | Vault integration disabled by default |
| extension-module | `extension-module` | CLOSED | — | ✅ Stable | PF4J plugin framework |
| datasource-module | `datasource-module` | CLOSED | — | ✅ Stable | Multi-datasource routing |
| observability-module | `observability-module` | CLOSED | — | ✅ Stable | Sentry + OpenTelemetry |
| outbox-event-module | `outbox-event-module` | CLOSED | — | ⚠️ Disabled (preview) | Dispatcher disabled in preview |
| audit-compliance-module | `audit-compliance-module` | CLOSED | — | ✅ Stable | Audit logging |
| scheduler-module | `scheduler-module` | CLOSED | 14% | ⚠️ Disabled (preview) | In-memory only, scheduling disabled |
| identity-access-module | `identity-access-module` | CLOSED | 34% | ✅ Stable | IAM, authz, user management |
| quota-billing-module | `quota-billing-module` | CLOSED | 13% | ⚠️ Low coverage | Quota tracking |
| commerce-module | `commerce-module` | CLOSED | — | ⚠️ Disabled (preview) | Commerce/order management |
| payment-module | `payment-module` | CLOSED | 10% | ⚠️ Disabled (preview) | Stripe/Hyperswitch |
| billing-module | `billing-module` | CLOSED | — | ✅ Stable | Billing/invoicing |
| entitlement-module | `entitlement-module` | CLOSED | — | ✅ Stable | Feature entitlement/quota profiles |
| policy-governance-module | `policy-governance-module` | CLOSED | 51% | ✅ Stable | Policy rules, feature flags |
| artifact-catalog-module | `artifact-catalog-module` | CLOSED | — | ✅ Stable | Artifact/media catalog |
| sandbox-runtime-module | `sandbox-runtime-module` | CLOSED | — | ⚠️ Disabled (preview) | Isolated (no shared-kernel) |
| sandbox-worker | `sandbox-worker` | — | 14% | ⚠️ Disabled (preview) | Separate deployable |
| federation-query-module | `federation-query-module` | CLOSED | 40% | ⚠️ Disabled (preview) | GraphQL gateway, highest fan-in (12 deps) |
| user-analytics-module | `user-analytics-module` | CLOSED | — | ✅ Stable | User analytics |
| compatibility-migration-module | `compatibility-migration-module` | CLOSED | 5% | ⚠️ Low coverage | Migration helpers |
| remote-render-worker | `remote-render-worker` | — | 13% | ✅ Stable | Separate deployable |
| social-publish-module | `social-publish-module` | CLOSED | 3% | ⚠️ Missing package-info | No `@ApplicationModule` declaration |
| product-layer-module | `product-layer-module` | CLOSED | — | ✅ Stable | Product layer |
| spring-ai-adapter | `spring-ai-adapter` | — | — | ⚠️ Isolated | Not in platform-app |

---

## Status Summary

### By Status

| Status | Count | Modules |
|--------|-------|---------|
| ✅ Stable | 16 | shared-kernel, platform-app, render-module, notification-module, storage-module, prompt-module, extension-module, datasource-module, observability-module, audit-compliance-module, identity-access-module, billing-module, entitlement-module, policy-governance-module, artifact-catalog-module, user-analytics-module, remote-render-worker, product-layer-module |
| ⚠️ Disabled (preview) | 9 | workflow-module, delivery-module, outbox-event-module, scheduler-module, commerce-module, payment-module, sandbox-runtime-module, sandbox-worker, federation-query-module |
| ⚠️ Isolated | 2 | ai-module, spring-ai-adapter |
| ⚠️ Low Coverage | 4 | quota-billing-module, config-module, compatibility-migration-module, cloud-resource-module |
| ⚠️ Structural Issue | 1 | social-publish-module (missing `@ApplicationModule`) |

### Test Coverage Distribution

| Coverage Range | Modules |
|---------------|---------|
| > 40% | policy-governance (51%), platform-app (42%), federation-query (40%) |
| 20–40% | identity-access (34%), render-module (26%) |
| 10–20% | scheduler (14%), sandbox-worker (14%), remote-render-worker (13%), quota-billing (13%), cloud-resource (11%) |
| < 10% | payment (10%), compatibility-migration (5%), social-publish (3%) |
| 0% | config-module |

---

## Modulith Boundary Status

| Check | Status | Details |
|-------|--------|---------|
| `ModularityTest` | ✅ PASS | `ApplicationModules.verify()` passes |
| Registered violations | 8 | All in `identity → artifact/storage` (ProjectImportService) |
| Unregistered violations | 0 | None |
| `package-info.java` | 29/30 | `social-publish-module` missing |

---

## Cross-Module Dependencies (Non-trivial)

| From | To | Type | Status |
|------|----|------|--------|
| render-module | ai-module | `api`, `domain` | ✅ Allowed |
| render-module | storage-module | `api`, `domain` | ✅ Allowed |
| workflow-module | policy-governance-module | `feature-flags` | ✅ Allowed |
| identity-access | entitlement, artifact-catalog, storage | Mixed | ⚠️ Registered debt (8 entries) |
| federation-query | 12 modules | Highest fan-in | ✅ Allowed (declared) |

---

## References

- [Module Architecture](../03-module-architecture.md)
- [Platform Fact Gathering Report](../platform-fact-gathering-report.md)
- [Backend-first Stabilization Plan](../backend-first-stabilization-plan.md)
- [Capability Opening Blueprint](../blueprint/capability-opening-blueprint.md)
- [Capability Opening Roadmap](../../roadmap/capability-opening-roadmap.md)

---

## Capability Opening Status

> **Note:** The capability opening model is blueprint only. See [Capability Opening Blueprint](../blueprint/capability-opening-blueprint.md) for target architecture.

| Component | Status | Notes |
|-----------|--------|-------|
| Capability opening model | Blueprint only | Not implemented |
| Contract skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/` |
| Registry skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/registry/` |
| Event contracts | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/event/` |
| Hook contracts | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/hook/` |
| Formal SystemAction registry | ⚠️ Skeleton only | Registry exists, runtime execution not implemented |
| Automation flow engine | ❌ Not implemented | No workflow execution engine |
| ExtensionPoint SPI | ⚠️ Skeleton only | Registry exists, runtime execution not implemented |
| ExtensionProvider registry | ⚠️ Skeleton only | Registry exists, provider invocation not implemented |
| EventType registry | ⚠️ Skeleton only | Registry exists, event bus not implemented |
| HookPoint registry | ⚠️ Skeleton only | Registry exists, hook runtime not implemented |
| Flow validation skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/validation/` |
| Flow validation rules | ✅ Implemented | Validates registry references, cycles, disconnected nodes |
| Built-in SystemAction catalog | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/action/` |
| Built-in action metadata | ✅ Implemented | 12 metadata-only actions (render, media, artifact, review, notification, webhook) |
| Execution skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/execution/` |
| Validating executor | ✅ Implemented | Validates requests, supports dry-run, returns NOT_IMPLEMENTED for real execution |
| AutomationFlow dry-run executor | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/flow/` |
| Dry-run capabilities | ✅ Implemented | Validates flows, produces traces, dry-runs ACTION nodes, marks non-runtime nodes |
| Execution trace model | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/trace/` |
| Dry-run trace mapper | ✅ Implemented | Converts dry-run results to explain-plan traces |
| Explain-plan concept | ✅ Implemented | Dry-run traces show what flow would do before execution |
| Event bus | ❌ Not implemented | Contracts only |
| Hook runtime | ❌ Not implemented | Contracts only |
| Connector marketplace | ❌ Not implemented | No marketplace infrastructure |
| Plugin marketplace | ❌ Not implemented | No marketplace infrastructure |
| Sandbox runtime | ❌ Not implemented | Stub only |
| BYOK/custom AI provider | ❌ Roadmap | Not in platform-app runtime |
| Plugin security sandbox | ❌ Not implemented | No Wasm/container isolation |
| Temporal integration | ❌ Not implemented | Future consideration for long-running workflows |
| LiteFlow integration | ❌ Not implemented | Future consideration for policy chains |
