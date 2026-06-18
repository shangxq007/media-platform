---
status: roadmap
last_verified: 2026-06-18
scope: all
truth_level: target
owner: platform
---

# Technical Debt Roadmap

> **Last verified:** 2026-06-18
> **Source:** [Current Known Gaps](../architecture/current/current-known-gaps.md) · [Release Candidate Readiness](../review/release-candidate-readiness-2026-06-17.md) · [Modulith Debt Register](../modulith-debt-register.md)

---

## Priority Classification

### P0 — Production Blockers (Immediate)

| Issue | Status | Fix Direction | ETA |
|-------|--------|---------------|-----|
| `ProductionSafetyValidator` `NoUniqueBeanDefinitionException` | 🔴 Open | Fix `ObjectProvider<?>` wildcard bean wiring | Sprint +1 |
| OAuth2 configuration required | 🔴 Open | Configure Authentik OIDC provider | Sprint +1 |
| JWT secret must be set | 🔴 Open | Document `APP_JWT_SECRET` env var requirement | Sprint +1 |
| Payment provider configuration | 🔴 Open | Configure Stripe/Hyperswitch production keys | Sprint +2 |

### P1 — High Impact (Next Quarter)

| Issue | Status | Fix Direction | ETA |
|-------|--------|---------------|-----|
| `StorageKeyPolicy` path traversal | �� Open | URL-decode normalization before path check | Q3 2026 |
| `SafeDownloadUrlValidator` SSRF kill-switch | 🟡 Open | Remove global mutable, per-request config | Q3 2026 |
| `BillingUsageDataLoader` thread-safety | 🟡 Open | Proper async context propagation | Q3 2026 |
| `TenantGuard` silent fallback | 🟡 Open | Throw explicit exception on missing tenant | Q3 2026 |
| jOOQ codegen not configured | 🟡 Open | Add codegen plugin + generated constants | Q3 2026 |
| `RenderJobRepository` extraction | 🟡 Open | Extract from 52 inline jOOQ refs | Q3 2026 |

### P2 — Medium Impact (Roadmap)

| Issue | Status | Fix Direction | ETA |
|-------|--------|---------------|-----|
| `PrometheusMeterRegistry` tag mismatch | 🟢 Warning | Align meter tag registration with Spring Boot auto-config | Q3 2026 |
| API parameter binding issues | 🟢 Known | Investigate `@RequestParam` name reflection (Java 21+ compiler flag) | Q3 2026 |
| Duplicate exception handlers | 🟢 Known | Consolidate global `@RestControllerAdvice` classes | Q3 2026 |
| Hardcoded business rules | 🟢 Known | Move to database-backed configuration | Q4 2026 |
| In-memory ConcurrentHashMap storage | 🟢 Known | Migrate to persistent storage (PostgreSQL) | Q4 2026 |
| SSRF TOCTOU race (no per-request DNS pinning) | 🟢 Known | Implement per-request DNS resolution | Q4 2026 |

### P3 — Low Impact (Backlog)

| Issue | Status | Fix Direction | ETA |
|-------|--------|---------------|-----|
| Dead Vue entry point (`frontend/src/main.ts`) | 🔵 Cleanup | Remove unused Vue/Pinia imports | Backlog |
| 3 TypeScript typecheck errors | 🔵 Cleanup | Add missing npm deps (`graphql-request`, `oidc-client-ts`) | Backlog |
| Pre-existing `RenderNatronEffectsIT` failure | 🔵 Known | Skip in CI or install Natron binary | Backlog |
| Actuator `/info` endpoint empty | 🔵 Known | Configure build info + git info | Backlog |

---

## Current Known Issues from Readiness Validation

### ProductionSafetyValidator NoUniqueBeanDefinitionException

**Severity:** Critical (blocks `prod,safe-mode` full startup)

**Root Cause:**
The `ProductionSafetyValidator` uses `ObjectProvider<?>` wildcard type for dependency injection. When multiple beans match the wildcard in `prod` profile (especially with OAuth2 and security components), Spring cannot determine a unique bean, resulting in `NoUniqueBeanDefinitionException`.

**Impact:**
- `prod,safe-mode` profile fails after startup completes
- Production deployment requires workaround (use `prod` with OAuth2 fully configured)

**Fix Approach:**
1. Replace `ObjectProvider<?>` with specific typed parameters
2. Or use `@Qualifier` annotations to disambiguate
3. Or add `@ConditionalOnSingleCandidate` to validator configuration

**References:**
- [Security Preview Safe Mode Runbook](../operations/security-preview-safe-mode-runbook.md)
- [Release Candidate Readiness](../review/release-candidate-readiness-2026-06-17.md)

---

### PrometheusMeterRegistry Tag Mismatch

**Severity:** Low (non-blocking warning)

**Root Cause:**
Spring Boot's auto-configured `PrometheusMeterRegistry` and custom meter registrations have inconsistent tag structures. This produces warning logs on startup but doesn't affect functionality.

**Impact:**
- Warning log on startup: `PrometheusMeterRegistry tag mismatch`
- No functional impact

**Fix Approach:**
1. Align custom meter tags with Spring Boot's auto-configured registry
2. Or disable auto-config for Prometheus and use custom registry exclusively
3. Review `MeterRegistryCustomizer` beans for tag consistency

**References:**
- [Current System State](../architecture/current/current-system-state.md)

---

### API Parameter Binding Issues

**Severity:** Low (some endpoints return 404)

**Root Cause:**
Some API endpoints return 404 due to parameter name reflection issues. This is typically caused by:
- Missing `-parameters` compiler flag in Java 21+
- `@RequestParam` names not preserved in bytecode
- Spring MVC unable to match request parameters to method parameters

**Impact:**
- Some endpoints return 404 instead of expected responses
- Non-blocking (workaround: use `@RequestParam("name")` explicit names)

**Fix Approach:**
1. Add `-parameters` flag to Java compiler options in `build.gradle.kts`:
   ```kotlin
   tasks.withType<JavaCompile> {
       options.compilerArgs.add("-parameters")
   }
   ```
2. Or explicitly name all `@RequestParam` annotations
3. Verify with `javap -v` that parameter names are preserved

**References:**
- [Current Known Gaps](../architecture/current/current-known-gaps.md)

---

## Security Debt Summary

### P1 — Open (Must Fix Before Production)

| Gap | File | Impact | Fix Direction |
|-----|------|--------|---------------|
| `StorageKeyPolicy` path traversal (substring check) | `StorageKeyPolicy.java:55,81` | Potential path traversal bypass via URL-encoded `..` | Add URL-decode normalization before path check |
| `SafeDownloadUrlValidator` SSRF kill-switch (global mutable) | `SafeDownloadUrlValidator.java:23,30-32` | SSRF risk if `skipDnsResolution` toggled at runtime | Remove global mutable, use per-request config |
| `BillingUsageDataLoader` thread-safety | `BillingUsageDataLoader.java:35-55` | `TenantContext` swap across async threads on shared ForkJoinPool | Use proper context propagation for async |
| `TenantGuard` silent fallback | `TenantGuard.java:38-52` | Silent pass-through when `TenantContext` is null | Throw explicit exception on missing tenant |

### P2 — Open (Should Fix)

| Gap | Impact |
|-----|--------|
| SSRF TOCTOU race (no per-request DNS pinning) | DNS rebinding attack vector |
| Tenant ID in public URL paths | Leaked in logs/CDN |
| Font security scanners are skeleton-only | No real font validation |
| K8s secrets with placeholder values | Secrets not properly managed |

---

## Persistence Debt Summary

### No jOOQ Codegen

| Metric | Current | Target |
|--------|---------|--------|
| jOOQ codegen configured | No (plugin declared, not applied) | Yes — type-safe column references |
| Column references | String-based `field("tenant_id")` | Generated constants |
| Inline jOOQ calls | 2,310 | 0 (all via repositories) |

### In-Memory Storage (Should Be Persistent)

| Service | Storage | Risk | Priority |
|---------|---------|------|----------|
| `CheckoutOrchestrator` | 4 ConcurrentHashMaps | Lost on restart | P1 |
| `CommerceCartService` | ConcurrentHashMap carts | Lost on restart | P1 |
| `PromptTemplateService` | 5 ConcurrentHashMaps | Lost on restart | P2 |
| `ReportExecutionService` | ConcurrentHashMap | Lost on restart | P2 |
| `QueryHistoryService` | ConcurrentHashMap | Lost on restart | P3 |
| `ExtensionAuditService` | ConcurrentHashMap | Lost on restart | P3 |

---

## Test Coverage Debt

### Zero-Test Modules

| Module | Main Files | Test Files | Priority |
|--------|-----------|-----------|----------|
| config-module | 4 | 0 | P2 |
| frontend | N/A | 1 | P2 |

### Low Coverage (< 20%)

| Module | Coverage | Notes |
|--------|----------|-------|
| social-publish-module | 3% | 1 test file |
| compatibility-migration-module | 5% | 1 test file |
| payment-module | 10% | 3 test files |
| cloud-resource-module | 11% | 1 test file |

---

## Modulith Debt

### Current Allowed Violations (2026-06-07)

| Source | Target | Dependency Path | Owner | Fix Deadline |
|--------|--------|-----------------|-------|--------------|
| identity | artifact (app) | ProjectImportService → ArtifactCatalogService | Backend Team | Staging |
| identity | storage (domain) | ProjectImportService → BlobStorage | Backend Team | Staging |
| identity | artifact (domain) | ProjectImportService → ArtifactStatus | Backend Team | Staging |
| identity | storage (domain) | ProjectImportService → StorageObjectRef | Backend Team | Staging |
| identity | storage (domain) | ProjectImportService → PutObjectCommand | Backend Team | Staging |
| identity | artifact (domain) | ProjectImportService → Artifact | Backend Team | Staging |
| identity | artifact (app) | ArtifactCatalogProjectAssetListingAdapter → ArtifactCatalogService | Backend Team | Staging |
| identity | artifact (domain) | ArtifactCatalogProjectAssetListingAdapter → Artifact | Backend Team | Staging |

**Fix Direction:**
- Short-term: Reverse dependencies via shared-kernel ports, or move to platform-app composition layer
- Long-term: Extract import/export adapters out of identity module
- Principle: Don't merge modules, don't expand allowlist

---

## Observability Debt

### MDC Fields

| Field | In Logback Config | Populated by Filter | Status |
|-------|-------------------|--------------------|----|
| `traceId` | Yes | Yes | ✅ |
| `requestId` | Yes | Yes | ✅ |
| `tenantId` | Yes | No | ⚠️ Gap |
| `projectId` | Yes | No | ⚠️ Gap |
| `jobId` | No | No | ❌ Missing |
| `workflowId` | No | No | ❌ Missing |
| `eventId` | No | No | ❌ Missing |
| `errorCode` | No | No | ❌ Missing |

### Error Model

| Area | Gap | Priority |
|------|-----|----------|
| Duplicate exception handlers | 2 global `@RestControllerAdvice` classes | P2 |
| Scattered RuntimeExceptions | ~30 raw `throw new RuntimeException` | P2 |
| commerce-module exceptions | Raw exceptions instead of `PlatformException` | P3 |

---

## Tracking & Governance

### Update Cadence

- **Sprint review:** Update P0/P1 status
- **Monthly:** Review P2/P3 backlog
- **Quarterly:** Re-prioritize based on production feedback

### Related Documentation

- [Current Known Gaps](../architecture/current/current-known-gaps.md)
- [Release Candidate Readiness](../review/release-candidate-readiness-2026-06-17.md)
- [Modulith Debt Register](../modulith-debt-register.md)
- [Production Safety](../production-safety.md)
- [Module Boundaries](../module-boundaries.md)

---

*This roadmap reflects technical debt as of 2026-06-18. Priorities are based on production readiness requirements and security impact.*
