---
status: current
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Current Known Gaps

> **Last validated:** 2026-06-18
> **Source:** [Platform Fact Gathering Report](../platform-fact-gathering-report.md) + [Release Candidate Readiness](../../review/release-candidate-readiness-2026-06-17.md)

## 1. Security Gaps

### P1 — Open

| Gap | File | Impact | Fix Direction |
|-----|------|--------|---------------|
| `StorageKeyPolicy` path traversal (substring check) | `StorageKeyPolicy.java:55,81` | Potential path traversal bypass via URL-encoded `..` | Add URL-decode normalization before path check |
| `SafeDownloadUrlValidator` SSRF kill-switch (global mutable) | `SafeDownloadUrlValidator.java:23,30-32` | SSRF risk if `skipDnsResolution` toggled at runtime | Remove global mutable, use per-request config |
| `BillingUsageDataLoader` thread-safety | `BillingUsageDataLoader.java:35-55` | `TenantContext` swap across async threads on shared ForkJoinPool | Use proper context propagation for async |
| `TenantGuard` silent fallback | `TenantGuard.java:38-52` | Silent pass-through when `TenantContext` is null | Throw explicit exception on missing tenant |

### P2 — Open

| Gap | Impact |
|-----|--------|
| SSRF TOCTOU race (no per-request DNS pinning) | DNS rebinding attack vector |
| Tenant ID in public URL paths | Leaked in logs/CDN |
| Font security scanners are skeleton-only | No real font validation |
| K8s secrets with placeholder values | Secrets not properly managed |

---

## 2. Persistence Gaps

### No jOOQ Codegen

| Metric | Current | Target |
|--------|---------|--------|
| jOOQ codegen configured | No (plugin declared, not applied) | Yes — type-safe column references |
| Column references | String-based `field("tenant_id")` | Generated constants |
| Generated directories | 0 | `build/generated-src/jooq/` |

### Tables Without Repositories

| Table | Inline jOOQ Refs | Affected Services | Priority |
|-------|-----------------|-------------------|----------|
| `render_job` | 52 | RenderJobService, RenderOrchestratorService + 10 more | **P1** |
| `outbox_events` | 38 | OutboxEventService | P2 |
| `delivery_destination` | 18 | DeliveryController, DeliveryAdminController | P2 |
| `delivery_job` | 16 | DeliveryJobService | P2 |
| `audit_records` | 16 | AuditService | P2 |
| `notification_*` (5 tables) | 5–8 each | Notification services | P2 |
| `config_entry` | 4 | ConfigService | P3 |
| `effect_pack` | 8 | EffectPackCatalogService | P3 |
| `timeline_snapshot` | 8 | TimelineSnapshotService | P3 |

**Total inline jOOQ calls:** 2,310

### In-Memory Storage (Should Be Persistent)

| Service | Storage | Risk |
|---------|---------|------|
| `CheckoutOrchestrator` | 4 ConcurrentHashMaps | Lost on restart |
| `CommerceCartService` | ConcurrentHashMap carts | Lost on restart |
| `PromptTemplateService` | 5 ConcurrentHashMaps | Lost on restart |
| `ReportExecutionService` | ConcurrentHashMap | Lost on restart |
| `QueryHistoryService` | ConcurrentHashMap | Lost on restart |
| `ExtensionAuditService` | ConcurrentHashMap | Lost on restart |
| `QueryCatalogService` | ConcurrentHashMap | Lost on restart |
| `ThirdPartyProviderHealthService` | 3 ConcurrentHashMaps | Lost on restart |
| `ExtensionRegistryService` | 6 ConcurrentHashMaps | Lost on restart |

---

## 3. Hardcoded Business Rules

| Finding | File | Risk |
|---------|------|------|
| Hardcoded product catalog (prices, SKUs) | `CommerceCatalogService.java` | HIGH |
| Hardcoded order values | `PurchaseOrderCreatedEvent.java` | HIGH |
| Hardcoded quota limits (10000, 1000, 100000) | `EntitlementService.java` | HIGH |
| Hardcoded fallback entitlement snapshot | `EntitlementService.java` | HIGH |
| Hardcoded tier→provider/preset/resolution policy | `ProviderAccessPolicy`, `ExportCapabilityPolicy`, `EntitlementPolicy` | HIGH |
| Hardcoded outbox event→class routing (6 types) | `OutboxEventDispatcher.java` | HIGH |

**Fix direction:** Move to database-backed configuration or external config files.

---

## 4. Test Coverage Gaps

### Zero-Test Modules

| Module | Main Files | Test Files |
|--------|-----------|-----------|
| config-module | 4 | 0 |
| frontend | N/A | 1 |

### Low Coverage (< 20%)

| Module | Coverage | Notes |
|--------|----------|-------|
| social-publish-module | 3% | 1 test file |
| compatibility-migration-module | 5% | 1 test file |
| payment-module | 10% | 3 test files |
| cloud-resource-module | 11% | 1 test file |
| quota-billing-module | 13% | 1 test file |
| remote-render-worker | 13% | 1 test file |
| scheduler-module | 14% | 1 test file |
| sandbox-worker | 14% | 1 test file |

### Missing Test Infrastructure

| Item | Status |
|------|--------|
| E2E tests (Playwright/Cypress) | Not present — shell script only |
| Frontend test coverage | 1 test file total |
| Integration test for full render flow | Shell script smoke test only |

---

## 5. Observability Gaps

### MDC Fields

| Field | In Logback Config | Populated by Filter | Status |
|-------|-------------------|--------------------|----|
| `traceId` | Yes | Yes (`PlatformTraceCorrelationFilter`) | ✅ |
| `requestId` | Yes | Yes | ✅ |
| `tenantId` | Yes | **No** | ⚠️ Gap |
| `projectId` | Yes | **No** | ⚠️ Gap |
| `jobId` | **No** | No | ❌ Missing |
| `workflowId` | **No** | No | ❌ Missing |
| `eventId` | **No** | No | ❌ Missing |
| `errorCode` | **No** | No | ❌ Missing |

### Error Model

| Area | Gap |
|------|-----|
| Duplicate exception handlers | 2 global `@RestControllerAdvice` classes with undefined execution order |
| Scattered RuntimeExceptions | ~30 raw `throw new RuntimeException/IllegalStateException` without structured error codes |
| commerce-module exceptions | Throws raw exceptions instead of `PlatformException` |

---

## 6. Module Structure Gaps

| Gap | Module | Impact |
|-----|--------|--------|
| Missing `@ApplicationModule` | `social-publish-module` | Not enforced by Modulith boundary checks |
| Missing `package-info.java` | `social-publish-module` | Spring Modulith cannot detect module boundaries |
| God Object | `RenderOrchestratorService` (29 deps, 682 lines) | High coupling, hard to test |
| Highest fan-in | `federation-query-module` (12 deps) | Aggregation layer, tight coupling |

---

## 7. Frontend Gaps

| Gap | Impact |
|-----|--------|
| Near-zero test coverage (1 test file) | No regression protection |
| 3 TypeScript typecheck errors | Missing npm deps (`graphql-request`, `oidc-client-ts`) |
| Dead Vue entry point | `frontend/src/main.ts` still imports Vue/Pinia |
| Thin editor shell | No real editing workflow (no multi-track, no drag-drop) |
| No E2E tests | Manual testing only |

---

## 8. Documentation Gaps

| Missing Doc | Status |
|-------------|--------|
| `font-subtitle-rendering-stack.md` | No unified cross-stack pipeline doc |
| `backend-architecture-review-triage.md` | Not created |
| `same-image-vs-multi-image-worker-deployment.md` | Not created |
| fontTools/HarfBuzz/FreeType/Pango/Skia registration | Not in any doc |

---

## 9. Production Blockers

| Blocker | Severity | Fix Required |
|---------|----------|--------------|
| `ProductionSafetyValidator` `NoUniqueBeanDefinitionException` | Critical | Fix bean wiring |
| OAuth2 configuration required | Critical | Configure Authentik OIDC |
| JWT secret must be set | Critical | Set `APP_JWT_SECRET` env var |
| Payment provider configuration | Critical | Configure Stripe/Hyperswitch |
| jOOQ codegen not configured | High | Add codegen plugin |
| `RenderJobRepository` extraction | High | Extract from 52 inline refs |

---

## References

- [Platform Fact Gathering Report](../platform-fact-gathering-report.md)
- [Backend-first Stabilization Plan](../backend-first-stabilization-plan.md)
- [Release Candidate Readiness](../../review/release-candidate-readiness-2026-06-17.md)
- [Production Readiness Checklist](../../production-readiness.md)
- [Capability Opening Blueprint](../blueprint/capability-opening-blueprint.md)
- [Capability Opening Roadmap](../../roadmap/capability-opening-roadmap.md)

---

## 10. Capability Opening Gaps

> **Note:** The capability opening model is blueprint only. See [Capability Opening Blueprint](../blueprint/capability-opening-blueprint.md) for target architecture.

| Gap | Status | Notes |
|-----|--------|-------|
| Capability opening model | Blueprint only | Not implemented |
| Contract skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/` |
| Registry skeleton | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/registry/` |
| Event contracts | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/event/` |
| Hook contracts | ✅ Implemented | `shared-kernel/src/main/java/com/example/platform/shared/capability/hook/` |
| Formal SystemAction registry | ⚠️ Skeleton only | Registry exists, runtime execution not implemented |
| Automation flow engine | ❌ Not implemented | No workflow execution engine |
| ExtensionPoint SPI | ⚠️ Skeleton only | Registry exists, runtime execution not implemented |
| ExtensionProvider registry | ⚠️ Skeleton only | Registry exists, provider invocation not implemented |
| Event bus | ❌ Not implemented | Contracts only |
| Hook runtime | ❌ Not implemented | Contracts only |
| Connector marketplace | ❌ Not implemented | No marketplace infrastructure |
| Plugin marketplace | ❌ Not implemented | No marketplace infrastructure |
| Sandbox runtime | ❌ Not implemented | Stub only |
| BYOK/custom AI provider | ❌ Roadmap | Not in platform-app runtime |
| Plugin security sandbox | ❌ Not implemented | No Wasm/container isolation |
| Automation workflows | ❌ Not implemented | Config-only workflows not implemented |
