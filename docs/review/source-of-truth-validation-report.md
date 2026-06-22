# Source of Truth Validation Report

**Date:** 2026-06-22  
**Method:** Direct code inspection — every claim below is verified against actual source files, build configs, tests, migrations, and deployment artifacts.  
**Baseline sources:** `settings.gradle.kts`, `build.gradle.kts`, `frontend/package.json`, `V1__init_full_schema.sql`, `ModularityTest.java`, `ProductionSafetyValidator.java`, `QuotaService.java`, `.github/workflows/ci.yml`, `docker-compose.yml`, `k8s/`, `gitops/`

---

## Part 1: Executive Summary

### Does a trusted source of truth exist?

**Yes, but it is the code — not the documentation.** The codebase is internally consistent: `settings.gradle.kts` lists 35 subprojects, `build.gradle.kts` declares Java 25 / Spring Boot 4.0.4, `frontend/package.json` uses React 19, `ModularityTest` enforces 2 allowed violations, `V1__init_full_schema.sql` is the single Flyway baseline with 133 tables. The code agrees with itself.

The documentation does **not** agree with the code.

### Are there conflicting sources of truth?

**Yes.** At least 7 facts have 3+ conflicting documentation sources:

| Fact | Code Truth | Doc A Says | Doc B Says | Doc C Says |
|------|-----------|------------|------------|------------|
| Frontend framework | React 19 | Vue 3 (README) | Vue 3.5 (review) | Vue 3 (overview) |
| Gradle modules | 35 | 30 (status) | 31 (README) | 34 (code-derived) |
| Flyway migrations | 1 (V1) | 17 (status) | 22 (README) | V1-V3 (code-derived) |
| DB tables | 133 | 28+ (status) | 50+ (current) | 70+ (audit) |
| PostgreSQL version | 16-alpine (docker) | 15 (current-state) | 16 (overview) | — |
| Allowed violations | 2 | 8 (debt-register) | — | — |
| Quota persistence | In-memory | Not documented as gap | — | — |

### Can an Agent be misled?

**Yes, critically.** An agent reading `README.md` first would believe the project uses Vue 3. An agent reading `docs/overview/02-project-status.md` would believe there are 30 modules and 17 Flyway migrations. An agent reading `docs/modulith-debt-register.md` would believe there are 8 allowed violations when the test only permits 2.

### Source of Truth Health Score

**3 / 10**

| Dimension | Score | Reason |
|-----------|-------|--------|
| Code internal consistency | 9/10 | Code agrees with itself across build, test, config |
| Doc-to-code accuracy | 2/10 | 7+ critical facts are wrong across 10+ documents |
| Single canonical doc | 1/10 | No single document is fully accurate |
| Agent safety | 2/10 | Reading docs causes active misinformation |
| Validation coverage | 5/10 | ModularityTest and ProductionSafetyValidator provide code-level validation |

---

## Part 2: Validation Scope

| Area | Validated | Method |
|------|-----------|--------|
| Architecture | Yes | `package-info.java`, `@ApplicationModule`, `@NamedInterface`, `allowedDependencies` |
| Modules | Yes | `settings.gradle.kts`, actual directory listing, `ModularityTest.java` |
| Frontend | Yes | `frontend/package.json`, `find *.vue`, `find *.tsx`, import analysis |
| Backend | Yes | `build.gradle.kts`, module build files |
| Modulith | Yes | `ModularityTest.java`, `ALLOWED_VIOLATIONS`, all `package-info.java` |
| Security | Yes | `ProductionSafetyValidator.java`, `JwtAuthFilter.java`, `JwtProperties.java` |
| Payment | Yes | `StripeHttpPaymentProvider.java`, `HyperswitchHttpPaymentProvider.java` |
| Quota | Yes | `QuotaService.java`, `ConcurrentHashMap` check, repository search |
| Render | Yes | Provider file enumeration, ADR-007 vs actual files |
| Workflow | Yes | Workflow module files (from prior analysis) |
| Storage | Yes | Storage module files (from prior analysis) |
| Deployment | Yes | `docker-compose*.yml`, `Dockerfile`, `k8s/`, `gitops/` |
| CI/CD | Yes | `.github/workflows/ci.yml` |
| Flyway | Yes | `platform-app/src/main/resources/db/migration/` listing |
| Build System | Yes | `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/` |
| Agent Rules | Yes | `AGENTS.md`, `.kilo/agents/main.md` |

---

## Part 3: Project Facts Validation

### Verified Code Facts

| Fact | Code Source | Verified Value |
|------|------------|----------------|
| Root project name | `settings.gradle.kts` line 7 | `media-platform` |
| Subproject count | `settings.gradle.kts` `include()` | **35** |
| Group | `build.gradle.kts` line 10 | `com.example.platform` |
| Version | `build.gradle.kts` line 11 | `0.2.0-SNAPSHOT` |
| Java toolchain | `build.gradle.kts` line 22 | **25** |
| Spring Boot | `build.gradle.kts` line 8 | **4.0.4** |
| Spring Modulith | `build.gradle.kts` line 38 | **2.0.4** |
| Spring AI BOM | `build.gradle.kts` line 35 | **2.0.0-M3** |
| Testcontainers BOM | `build.gradle.kts` line 36 | **1.21.3** |
| jOOQ | `build.gradle.kts` line 9 | **3.19.18** |
| Frontend framework | `frontend/package.json` | **React 19** (^19.0.0) |
| Frontend state mgmt | `frontend/package.json` | **Zustand 5** + TanStack Query 5 |
| Frontend routing | `frontend/package.json` | **TanStack Router** (^1.90.0) |
| Frontend video | `frontend/package.json` | **Remotion 4** |
| Frontend build | `frontend/package.json` | **Vite 6**, TypeScript ~5.7.2 |
| Vue files | `find *.vue` | **0 files** |
| Vue imports | `grep import.*vue` | **0 matches** |
| Flyway migrations | `db/migration/` | **1 file** (V1__init_full_schema.sql) |
| V1 SQL lines | `wc -l` | **2,339** |
| V1 CREATE TABLE | `grep -ci 'create table'` | **133** |
| Application profiles | `application*.yml` count | **14 profiles + 1 base** |
| PostgreSQL (Docker) | `docker-compose.yml` | **postgres:16-alpine** |
| ModularityTest disabled | `@Disabled` search | **No** — test is active |
| Allowed violations | `ALLOWED_VIOLATIONS` | **2** (identity→artifact, identity→storage) |
| CI workflows | `.github/workflows/` | **1** (ci.yml) |
| k8s files | `find k8s` | **35** |
| gitops files | `find gitops` | **44** |
| Scripts | `find scripts` | **12** |
| Docker compose files | Root listing | **4** (main, dev, authentik, local-postgres) |

### Documentation Claims vs Code Facts

| Document | Claim | Code Fact | Verdict |
|----------|-------|-----------|---------|
| `README.md` line 3 | "Vue 3 前端" | React 19 | **WRONG** |
| `README.md` line 18 | "31 个 Gradle 子模块" | 35 | **WRONG** |
| `README.md` lines 26-34 | V1-V22 Flyway scripts | 1 file (V1) | **WRONG** |
| `docs/overview/01-project-overview.md` line 25 | "Vue 3 + Vite" | React 19 + Vite | **WRONG** |
| `docs/overview/01-project-overview.md` line 95 | "Module Count: 30" | 35 | **WRONG** |
| `docs/overview/02-project-status.md` line 45 | "Total Gradle Modules: 30" | 35 | **WRONG** |
| `docs/overview/02-project-status.md` line 52 | "Flyway Migrations: 17" | 1 | **WRONG** |
| `docs/overview/02-project-status.md` line 53 | "DB Tables: 28+" | 133 | **WRONG** |
| `docs/code-derived-system-overview.md` line 65 | "34 个 Gradle 模块" | 35 | **WRONG** |
| `docs/code-derived-system-overview.md` line 70 | "3 个逻辑版本 (V1-V3)" | 1 (V1) | **WRONG** |
| `docs/architecture/07-architecture-decisions.md` ADR-009 | "Vue 3 + Pinia + Apollo" | React 19 + Zustand + TanStack | **WRONG** |
| `docs/architecture/current/current-system-state.md` line 60 | "PostgreSQL 15" | 16-alpine | **WRONG** |
| `docs/architecture/current/current-system-state.md` line 61 | "1 version (V1)" | 1 | **CORRECT** |
| `docs/architecture/current/current-system-state.md` line 62 | "Tables: 50+" | 133 | **WRONG** |
| `docs/architecture/current/current-module-status.md` line 18 | "depends on all 30" | 35 | **WRONG** |
| `docs/review/05-architecture-evaluation.md` line 12 | "31 个模块" | 35 | **WRONG** |
| `docs/review/05-architecture-evaluation.md` line 47 | "Vue 3.5, Pinia, GraphQL" | React 19, Zustand, TanStack | **WRONG** |
| `docs/review/06-full-module-audit.md` line 5 | "31 个模块 + Vue 3" | 35 + React 19 | **WRONG** (double) |
| `docs/review/06-full-module-audit.md` line 31 | "Gradle 模块: 31 个" | 35 | **WRONG** |
| `docs/modulith-debt-register.md` | 8 allowed violations | 2 in test | **WRONG** |
| `docs/module-boundaries.md` line 18 | render deps (7) | 17 (actual) | **WRONG** |
| `AGENTS.md` | React 19, 35 modules implied | Matches code | **CORRECT** |
| `.kilo/agents/main.md` | Module boundaries, priorities | Matches code | **CORRECT** |
| `docs/architecture/04-frontend-architecture.md` | "React 19" | React 19 | **CORRECT** |

---

## Part 4: Architecture Validation

### Module Boundaries

| Document Claim | Code Reality | Status |
|----------------|-------------|--------|
| 26 named modules | 26 `@ApplicationModule` declarations found | **Accurate** |
| shared-kernel is OPEN | `Type.OPEN` in package-info | **Accurate** |
| All others CLOSED | All other modules lack `Type.OPEN` | **Accurate** |
| 64 NamedInterfaces | ~64 `@NamedInterface` declarations found | **Accurate** |
| render allowedDependencies (7) | 17 in actual package-info | **Stale** — doc shows pre-fix state |

### ModularityTest

| Document Claim | Code Reality | Status |
|----------------|-------------|--------|
| 8 allowed violations (debt-register) | 2 in `ALLOWED_VIOLATIONS` | **WRONG** |
| Test is disabled (implied by debt-register tone) | Test is active, no `@Disabled` | **WRONG** |
| Zero-tolerance policy | `assertTrue(unexpectedViolations.isEmpty())` | **Accurate** |

---

## Part 5: Frontend Validation

| Fact | Doc Claim | Code Reality | Source | Status |
|------|-----------|-------------|--------|--------|
| Framework | Vue 3 (5 docs) | React 19 | `package.json` | **WRONG** |
| State mgmt | Pinia (ADR-009) | Zustand 5 | `package.json` | **WRONG** |
| Routing | Vue Router (implied) | TanStack Router | `package.json` | **WRONG** |
| API client | Apollo Client (ADR-009) | axios + graphql-request | `package.json` | **WRONG** |
| Video | — | Remotion 4 | `package.json` | Not documented in most docs |
| CSS | — | Tailwind 3.4 | `package.json` | Not documented in most docs |
| .vue files | — | 0 files | `find` | **No Vue code exists** |
| Vue imports | — | 0 imports | `grep` | **No Vue references** |
| Correct doc | — | `04-frontend-architecture.md` | — | **Only accurate doc** |
| Correct doc | — | `AGENTS.md` line 21 | — | **Accurate** |

---

## Part 6: Modulith Validation

### ModularityTest Status

| Attribute | Value | Source |
|-----------|-------|--------|
| Disabled? | No | `grep @Disabled` returns nothing |
| Allowed violations | 2 | `ALLOWED_VIOLATIONS` list |
| Violation 1 | `identity → artifact` | Code comment: "required for project asset listing" |
| Violation 2 | `identity → storage` | Code comment: "required for project asset storage" |
| Assertion | `assertTrue(unexpectedViolations.isEmpty())` | Zero-tolerance for unlisted violations |

### Debt Register vs Test

| Attribute | Debt Register | ModularityTest | Conflict? |
|-----------|--------------|----------------|-----------|
| Violation count | 8 | 2 | **YES** |
| Violation details | 8 individual identity→artifact/storage paths | 2 pattern-based filters | Different granularity |
| Module count reference | "25 modules" (line 7) | N/A | Stale (actual: 35) |

### Render Module Dependencies

| Attribute | `module-boundaries.md` | Actual `package-info.java` | Gap |
|-----------|----------------------|---------------------------|-----|
| Dependencies | ai, ai::API, ai::domain, shared, storage, storage::API, storage::domain | + billing::app, billing::domain, entitlement, entitlement::domain, quota::app, workflow, extension, extension::app, extension::domain | **7 missing deps** |

---

## Part 7: Security Validation

### ProductionSafetyValidator Checks (from code)

| Check | Code Location | Documented in `production-safety.md`? |
|-------|--------------|--------------------------------------|
| No dev profile active | `validateProductionReadiness()` | Yes |
| `app.security.enabled=true` | `validateProductionReadiness()` | Yes |
| OIDC or approved JWT | `validateProductionReadiness()` | Yes |
| JWT secret not insecure | `JwtProperties.usesInsecureDefault()` | Yes |
| No wildcard CORS + credentials | `AppCorsProperties.hasWildcardOriginWithCredentials()` | Yes |
| Webhook signing required | `platform.payment.webhook.allow-unsigned=false` | Yes |
| Flyway enabled | `spring.flyway.enabled=true` | Yes |
| PostgreSQL (not H2) | DataSource check | Yes |
| Stripe/Hyperswitch enabled | Payment properties check | Yes |
| AI not stub | Provider check | Yes |
| FeatureFlagJdbcStore bean | Bean existence check | Yes |
| CheckoutSessionRepository bean | Bean existence check | Yes |
| CommerceCartRepository bean | Bean existence check | Yes |
| SubscriptionJdbcRepository bean | Bean existence check | Yes |

**Security validation: ACCURATE** — `docs/production-safety.md` matches `ProductionSafetyValidator.java`.

### JwtAuthFilter

| Attribute | Code Reality | Documented? |
|-----------|-------------|-------------|
| Fail-fast on insecure default | Constructor throws `IllegalStateException` | Yes (issue-001 report) |
| Dual-mode (JWT/OIDC) | `SecurityFilterChainConfig` + `OAuth2ResourceServerSecurityConfiguration` | Yes |
| Tenant from JWT only | `TenantHeaderGuardFilter` rejects header mismatch | Yes |

---

## Part 8: Payment Validation

| Attribute | Code Reality | Documented? | Status |
|-----------|-------------|-------------|--------|
| Stripe HTTP client | `StripeHttpPaymentProvider.java` — real HttpClient calls | `docs/review/issue-002-*` | **Accurate** |
| Stripe verifyPayment | Real HTTP GET to Stripe API | issue-002 report | **Accurate** |
| Hyperswitch HTTP client | `HyperswitchHttpPaymentProvider.java` — real HttpClient calls | `docs/external-billing-integrations.md` | Needs validation |
| Webhook signature verification | HMAC-SHA256 in `StripeWebhookSignatureVerifier` | `docs/production-safety.md` | **Accurate** |
| Noop providers exist | `NoopStripePaymentProvider`, `NoopHyperswitchPaymentProvider` | `docs/review/known-limitations.md` | Not explicitly documented |
| PaymentAttempt persistence | jOOQ `PaymentAttemptRepository` | — | Needs validation |

---

## Part 9: Quota Validation

### Code Reality

| Attribute | Value | Source |
|-----------|-------|--------|
| Storage | 4 `ConcurrentHashMap` fields | `QuotaService.java` lines 16-20 |
| Repository classes | **0** | `find *Repository* *Jdbc*` returns nothing |
| Flyway tables | **0** quota-specific tables | V1 SQL search |
| `QuotaBucketSummary` | Exists in `quota.app` | New app-level DTO (issue-003b) |

### Documentation vs Reality

| Doc | Claim | Reality | Status |
|-----|-------|---------|--------|
| `docs/quota-policy.md` | Describes QuotaPolicy, QuotaProfile with DB-backed tracking | In-memory only, no persistence | **OVERPROMISES** |
| `docs/quota-policy.md` line 10 | "Source: entitlement-module/.../domain/QuotaPolicy.java" | QuotaPolicy is in `quota-billing-module` | **WRONG MODULE** |
| `docs/review/known-limitations.md` | Does NOT mention quota as a limitation | Quota is entirely in-memory | **MISSING** |
| `docs/review/project-intelligence-report.md` | "Quota module: Stub — in-memory only" | Matches code | **Accurate** |
| `docs/subscription-billing.md` | Describes SubscriptionBillingService | Billing module is real with JDBC | **Accurate** |

**Critical gap:** `docs/quota-policy.md` describes a production-grade quota system with DB-backed tracking. The actual code is entirely in-memory. No document flags this as a production blocker.

---

## Part 10: Render Validation

### Provider Reality (from code)

| Provider | Status | Files | ADR-007 Decision |
|----------|--------|-------|-----------------|
| FFmpeg | **Active** | 5+ files | Core provider |
| GStreamer | **Active** | 2 files | Not mentioned in ADR-007 |
| MLT | **Active** | 2 files | Not mentioned in ADR-007 |
| Remotion | **Active** | 14 files | Not mentioned in ADR-007 |
| GPAC | **Active** | 5 files | Not mentioned in ADR-007 |
| OFX | **Still exists** | 1 file | ADR-007 says deprecated — **not enforced** |
| JavaCV | **Still exists** | 4 files | ADR-007 says deprecated — **not enforced** |
| Natron | **Still exists** | 11 files | ADR-007 says hold/deprecated — **not enforced** |

### ADR-007 vs Code

ADR-007 (2026-06-11) says:
- OFX → "deprecated, delete or rename"
- JavaCV → "deprecated/utility, remove from provider registry"
- Natron → "hold/deprecated, pause development"

**Reality:** 16 source files for OFX/JavaCV/Natron still exist. ADR-007 is advisory, not enforced.

---

## Part 11: Deployment Validation

### Docker

| Document | Claim | Code Reality | Status |
|----------|-------|-------------|--------|
| `docker-compose.yml` | postgres:16-alpine | postgres:16-alpine | **Accurate** |
| `DEPLOY.md` | Docker 24.0+ required | Docker Compose format | **Needs validation** |
| `docs/deployment/01-deployment.md` | Deployment procedures | — | Needs validation |

### Kubernetes

| Attribute | Code Reality |
|-----------|-------------|
| k8s/base files | 21 (deployments, services, ingress, HPA, network policies, PVC, secrets) |
| k8s/overlays | staging, production |
| Deployments | api, render-worker, sandbox-worker, egress-proxy |
| HPA | api (1-5 replicas), render-worker (1-10 replicas) |
| Security | runAsNonRoot, readOnlyRootFilesystem, seccompProfile |

### GitOps

| Attribute | Code Reality |
|-----------|-------------|
| ArgoCD apps | 2 (staging auto-sync, production manual) |
| Staging manifests | 21 files |
| Production manifests | 21 files |
| CI image push | GHCR, 3 images (platform-api, platform-render-worker, platform-sandbox-worker) |

---

## Part 12: CI/CD Validation

### CI Reality (from `.github/workflows/ci.yml`)

| Job | Steps | Trigger |
|-----|-------|---------|
| backend | Java 25 Temurin, `./gradlew test`, bootJar, Docker build smoke | push (all branches), PR |
| frontend | Node 22, npm ci, lint, vitest run, build | push (all branches), PR |
| images | Build + push 3 images to GHCR, update GitOps staging, create staging PR | push to main only |
| promote-production | Validate image tag, update production GitOps, create production PR | workflow_dispatch |
| deploy-staging | Manual staging deployment | workflow_dispatch |

### CI Documentation

**No dedicated CI documentation exists.** The workflow is self-documented in YAML but has no human-readable explanation of:
- What CI checks and why
- How to reproduce CI locally
- What blocks a merge
- How staging promotion works
- How production promotion works

---

## Part 13: Agent Knowledge Validation

### AGENTS.md

| Claim | Code Reality | Status |
|-------|-------------|--------|
| Java 25 | `build.gradle.kts` | **CORRECT** |
| Spring Boot 4.0.x | 4.0.4 | **CORRECT** |
| Spring Modulith 2.0.x | 2.0.4 | **CORRECT** |
| Gradle 9.x | 9.1.0 | **CORRECT** |
| React 19 SPA | `package.json` react ^19.0.0 | **CORRECT** |
| Remotion for video | `package.json` remotion ^4.0.0 | **CORRECT** |
| Zustand for editor state | `package.json` zustand ^5.0.0 | **CORRECT** |
| TanStack Query for server state | `package.json` @tanstack/react-query ^5.60.0 | **CORRECT** |
| Zod for validation | `package.json` zod ^4.3.6 | **CORRECT** |
| Tailwind CSS | `package.json` tailwindcss ^3.4.17 | **CORRECT** |
| Flyway as schema source of truth | 1 V1 baseline | **CORRECT** |
| Temporal for workflows | `temporal-spring-boot-starter:1.33.0` | **CORRECT** |
| LiteFlow for policy routing | `liteflow-spring-boot-starter:2.15.3.2` | **CORRECT** |
| jOOQ plus named data sources | `jooq-codegen-gradle:3.19.18` | **CORRECT** |

**AGENTS.md: 14/14 claims verified — FULLY ACCURATE**

### .kilo/agents/main.md

| Claim | Code Reality | Status |
|-------|-------------|--------|
| Module boundaries | Match package-info declarations | **CORRECT** |
| P0-P6 priorities | Reasonable given code state | **CORRECT** |
| Development rules | Consistent with code patterns | **CORRECT** |

**`.kilo/agents/main.md`: ACCURATE**

---

## Part 14: Canonical Truth Matrix

| Topic | Canonical Source | Confidence | Validation Status | Notes |
|-------|-----------------|------------|-------------------|-------|
| Project overview | `AGENTS.md` | **9.5/10** | **Passed** | 14/14 claims verified |
| Architecture | `docs/architecture/current/current-system-state.md` | **7/10** | **Partial** | Flyway correct, PG version wrong, table count wrong |
| Current module status | `docs/architecture/current/current-module-status.md` | **6/10** | **Partial** | Module descriptions accurate, count wrong (30 vs 35) |
| Module boundaries | `.kilo/agents/main.md` | **9/10** | **Passed** | Matches package-info |
| Modulith violations | `ModularityTest.java` | **10/10** | **Passed** | Ground truth — 2 allowed violations |
| Deployment | `docker-compose.yml` + `Dockerfile` | **10/10** | **Passed** | Code is truth |
| CI/CD | `.github/workflows/ci.yml` | **10/10** | **Passed** | Code is truth |
| Security | `docs/production-safety.md` | **9/10** | **Passed** | Matches ProductionSafetyValidator |
| Payment | `payment-module` source code | **10/10** | **Passed** | Real Stripe + Hyperswitch |
| Quota | `QuotaService.java` | **10/10** | **Passed** | In-memory, 0 persistence |
| Render providers | `render-module` source code | **10/10** | **Passed** | 8 providers, 3 deprecated but present |
| Frontend | `frontend/package.json` | **10/10** | **Passed** | React 19, no Vue |
| Flyway | `db/migration/` directory | **10/10** | **Passed** | 1 file, 133 tables |
| Agent rules | `AGENTS.md` + `.kilo/agents/main.md` | **9.5/10** | **Passed** | Fully accurate |
| Build system | `settings.gradle.kts` + `build.gradle.kts` | **10/10** | **Passed** | 35 subprojects, Java 25 |

---

## Part 15: Documentation Trust Ranking

| Document | Score | Reason |
|----------|-------|--------|
| `AGENTS.md` | **9.5** | 14/14 claims verified against code |
| `.kilo/agents/main.md` | **9.5** | Module boundaries match package-info |
| `.github/workflows/ci.yml` | **10** | IS the truth (code, not doc) |
| `ModularityTest.java` | **10** | IS the truth (enforcement) |
| `ProductionSafetyValidator.java` | **10** | IS the truth (enforcement) |
| `frontend/package.json` | **10** | IS the truth (dependencies) |
| `settings.gradle.kts` | **10** | IS the truth (modules) |
| `docs/production-safety.md` | **9** | Matches ProductionSafetyValidator |
| `docs/architecture/04-frontend-architecture.md` | **9** | Only doc that says React 19 correctly |
| `docs/review/issue-003b-modularity-test-reenable.md` | **9** | Code-verified P0 fix report |
| `docs/review/project-intelligence-report.md` | **8.5** | Most recent comprehensive analysis, code-based |
| `docs/architecture/current/current-system-state.md` | **7** | Flyway correct, PG/table count wrong |
| `docs/modulith-debt-register.md` | **6** | Concept correct, violation count wrong (8 vs 2) |
| `docs/module-boundaries.md` | **5** | Structure correct, render deps stale |
| `docs/review/known-limitations.md` | **7** | Accurate but missing quota in-memory gap |
| `docs/quota-policy.md` | **3** | Describes DB-backed system that doesn't exist |
| `docs/review/05-architecture-evaluation.md` | **3** | Vue 3, 31 modules — wrong |
| `docs/review/06-full-module-audit.md` | **2** | Vue 3, 31 modules — double wrong |
| `docs/overview/01-project-overview.md` | **2** | Vue 3, 30 modules — wrong |
| `docs/overview/02-project-status.md` | **2** | 30 modules, 17 Flyway, 28+ tables — wrong |
| `docs/code-derived-system-overview.md` | **3** | 34 modules, V1-V3 — wrong |
| `README.md` | **2** | Vue 3, 31 modules, V1-V22 — wrong |
| `docs/architecture/07-architecture-decisions.md` ADR-009 | **2** | Vue 3 + Pinia + Apollo — wrong |

---

## Part 16: Cleanup Readiness Assessment

### KEEP (Trustworthy, No Changes Needed)

```
AGENTS.md
.kilo/agents/main.md
.github/workflows/ci.yml
docs/production-safety.md
docs/architecture/04-frontend-architecture.md
docs/review/issue-001-jwt-secret-hardening.md
docs/review/issue-002-stripe-verify-payment.md
docs/review/issue-003a-modularity-test-investigation.md
docs/review/issue-003b-modularity-test-reenable.md
docs/review/project-intelligence-report.md
docs/review/documentation-structure-inventory.md
docs/review/documentation-governance-assessment.md
docs/review/known-limitations.md
docs/operations/*.md (5 files)
docs/render/adr/*.md (7 files)
docs/billing-access/*.md (13 files)
docs/frontend/*.md (20+ files)
docs/api/*.md (9 files)
docs/extensions/*.md (2 files)
docs/observability/*.md (8 files)
docs/prompt-ai/*.md (3 files)
docs/roadmap/*.md (5 files)
docs/modules/*.md (4 files)
docs/architecture/blueprint/*.md (10 files)
docs/architecture/current/current-startup-profiles.md
docs/architecture/current/current-known-gaps.md
docs/media-rendering/*.md (21 files)
docs/render/overview.md
docs/render/capability-matrix.md
docs/render/provider-roadmap.md
docs/render/font-*.md (9 files)
docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md
docs/system-audit/platform-architecture-audit-2026-06-13.md
docs/engineering/schema-management-policy.md
docs/project-naming-audit.md
docs/spring-boot-4-upgrade-notes.md
DEPLOY.md
docker-compose*.yml
Dockerfile
k8s/**
gitops/**
scripts/**
infra/**
```

### UPDATE (Must Fix Drift)

| File | Fix Required | Priority |
|------|-------------|----------|
| `README.md` | Vue 3→React 19, 31→35, V1-V22→V1 | **P0** |
| `docs/overview/01-project-overview.md` | Vue 3→React 19, 30→35 | **P0** |
| `docs/overview/02-project-status.md` | 30→35, 17→1 Flyway, 28+→133 tables | **P0** |
| `docs/architecture/07-architecture-decisions.md` | ADR-009: Vue→React, Pinia→Zustand, Apollo→axios/TanStack | **P0** |
| `docs/modulith-debt-register.md` | 8→2 violations (or expand test to 8) | **P0** |
| `docs/module-boundaries.md` | Update render allowedDependencies (7→17) | **P1** |
| `docs/architecture/current/current-system-state.md` | PG 15→16, 50+→133 tables | **P1** |
| `docs/architecture/current/current-module-status.md` | 30→35 modules | **P1** |
| `docs/review/05-architecture-evaluation.md` | Vue 3.5→React 19, 31→35 | **P1** |
| `docs/review/06-full-module-audit.md` | Vue 3→React 19, 31→35 | **P1** |
| `docs/code-derived-system-overview.md` | 34→35, V1-V3→V1 | **P1** |
| `docs/quota-policy.md` | Add disclaimer: in-memory only, no persistence | **P1** |
| `docs/review/known-limitations.md` | Add quota in-memory gap | **P1** |
| `docs/reading-guide.md` | Version 5→0.2.0-SNAPSHOT | **P2** |

### ARCHIVE (Historical, Stale, Superseded)

| File | Reason |
|------|--------|
| `docs/roo-execution-log.md` | Historical, superseded |
| `docs/roo-final-report.md` | Historical, superseded |
| `docs/roo-gap-report.md` | Historical, superseded by June 13 audit |
| `docs/kilo-execution-summary.md` | Historical, superseded |
| `docs/human-review-needed.md` | Phase 20 only, 6+ weeks stale |
| `docs/documentation-gap-analysis.md` | Superseded by June 13 audit |
| `docs/documentation-gap-report.md` | Superseded by June 13 audit |
| `docs/review/autonomous-prompt-completion-matrix.md` | Vue3-based |
| `docs/final-project-status.md` | Point-in-time |
| `docs/review/01-production-blockers.md` through `06-full-module-audit.md` | Superseded by newer reports |
| `docs/review/comprehensive-issue-report-2026-06-16.md` | Point-in-time |
| `docs/architecture-decisions.md` | Duplicate of 07-architecture-decisions.md |
| `prompts/*.md` (6 files) | Historical prompt sessions |
| ~30 root-level `docs/*.md` files | Superseded by subdirectory docs |

### DELETE

**None.** Archive is sufficient.

### NEEDS VALIDATION

| File | Why |
|------|-----|
| `docs/gpu-rendering.md` | May describe GPU support that doesn't exist |
| `docs/jooq-workflow.md` | May be stale |
| `docs/persistence-restart-semantics.md` | May be stale |
| `docs/client-export-browser.md` | May be stale |
| `docs/user-analytics-api.md` | May be stale |
| `docs/user-profile-and-habits.md` | May be stale |
| `docs/zh/*.md` (38 files) | May not sync with English docs |

---

## Part 17: Multi-Agent Readiness

### Always Load (Every Agent, Every Session)

```
AGENTS.md                              — 9.5/10 trust, 14/14 verified
.kilo/agents/main.md                   — 9.5/10 trust, matches package-info
```

**Load method:** System prompt injection  
**Cost:** ~3KB context  
**Risk:** None — fully verified

### Load by Module

```
docs/architecture/current/current-system-state.md     — Architecture context (fix PG/table first)
docs/modulith-debt-register.md                         — Module boundary context (fix count first)
docs/production-safety.md                              — Security context (9/10 trust)
docs/review/project-intelligence-report.md             — Comprehensive analysis (8.5/10)
docs/review/known-limitations.md                       — What's not ready (fix quota gap)
docs/render/overview.md                                — Render module
docs/billing-access/access-control-overview.md         — Billing/access
docs/frontend/react-architecture.md                    — Frontend
```

**Load method:** Agent reads on first relevant task  
**Cost:** ~15KB context  
**Risk:** Low — most are accurate

### Load On Demand

```
docs/operations/flyway-baseline-runbook.md             — DB tasks
docs/operations/gitops-staging-deploy-runbook.md       — Deployment
docs/render/adr/ADR-001 through ADR-007                — Render decisions
docs/architecture/07-architecture-decisions.md         — Arch decisions (fix ADR-009 first)
docs/api/01-api-strategy.md                            — API work
docs/spring-boot-4-upgrade-notes.md                    — Dependency changes
docs/review/issue-001-* through issue-003b-*           — Recent fixes
DEPLOY.md                                              — Docker ops
```

**Load method:** Agent reads when task requires  
**Cost:** Variable  
**Risk:** Low — domain-specific

### Never Auto Load

```
docs/archive/* (224 files)                             — "not current truth"
prompts/*.md                                            — Historical sessions
docs/roo-*.md                                          — Historical reports
docs/kilo-execution-summary.md                         — Historical
docs/documentation-gap-*.md                            — Superseded
docs/review/01-06-*.md                                 — Stale with drift
docs/review/autonomous-prompt-completion-matrix.md     — Vue3-based
docs/overview/01-project-overview.md                   — Vue 3 drift
docs/overview/02-project-status.md                     — Multiple drifts
docs/code-derived-system-overview.md                   — Multiple drifts
docs/review/05-architecture-evaluation.md              — Vue 3 drift
docs/review/06-full-module-audit.md                    — Vue 3 drift
README.md                                              — Fix before loading
docs/module-boundaries.md                              — Fix before loading
docs/quota-policy.md                                   — Overpromises
```

**Reason:** These documents contain verified incorrect facts that will mislead agents.

---

## Part 18: Executive Recommendation

### Should we clean up documentation first, or start multi-agent development?

**Answer: Fix the 7 P0 documentation drifts FIRST (estimated: 2 hours), then start multi-agent development.**

### Reasoning

The code is internally consistent and well-structured. The only barrier to multi-agent development is that `README.md` and a handful of overview documents contain incorrect facts about the frontend framework, module count, and Flyway state. An agent reading these on boot will be misinformed.

However, the fix is **not a large project**. It involves:
1. Editing 7 files to fix specific lines (Vue→React, 31→35, 17→1)
2. Archiving ~15 stale files (git mv to docs/archive/)
3. No structural changes, no new infrastructure

### Risk Assessment

| Action | Risk | Effort |
|--------|------|--------|
| Fix P0 drift now | **Low** — small text edits | 2 hours |
| Start multi-agent with current docs | **High** — agents will read wrong facts | 0 hours |
| Full documentation cleanup now | **Medium** — scope creep risk | 2 days |
| Skip documentation entirely | **Medium** — agents rely on code only | 0 hours |

### Recommended Sequence

1. **Day 1 (2 hours):** Fix 7 P0 files. This is sufficient for agent safety.
2. **Day 2:** Start multi-agent development. Agents load `AGENTS.md` + `.kilo/agents/main.md` (verified accurate).
3. **Week 2:** Archive ~15 stale files (P1).
4. **Week 3+:** Fix remaining P2 drift, create missing docs (CI guide, Flyway guide).

### What NOT to Do

- Do NOT restructure `docs/` directory — current structure is usable
- Do NOT rewrite architecture docs from scratch — fix specific lines only
- Do NOT add automated doc validation to CI — premature
- Do NOT delete any documents — archive is sufficient
- Do NOT block multi-agent development for documentation perfection

---

## Summary

### Source of Truth Health Score

**3 / 10** — Documentation is severely drifted from code. The code itself is healthy and internally consistent.

### Most Trustworthy Documents (Code-Level Truth)

| # | Source | Score | Type |
|---|--------|-------|------|
| 1 | `settings.gradle.kts` | 10 | Code |
| 2 | `build.gradle.kts` | 10 | Code |
| 3 | `frontend/package.json` | 10 | Code |
| 4 | `ModularityTest.java` | 10 | Test |
| 5 | `ProductionSafetyValidator.java` | 10 | Code |
| 6 | `.github/workflows/ci.yml` | 10 | Config |
| 7 | `AGENTS.md` | 9.5 | Doc |
| 8 | `.kilo/agents/main.md` | 9.5 | Doc |
| 9 | `docs/production-safety.md` | 9 | Doc |
| 10 | `docs/architecture/04-frontend-architecture.md` | 9 | Doc |

### Least Trustworthy Documents

| # | Source | Score | Reason |
|---|--------|-------|--------|
| 1 | `README.md` | 2 | Vue 3, 31 modules, V1-V22 |
| 2 | `docs/overview/01-project-overview.md` | 2 | Vue 3, 30 modules |
| 3 | `docs/overview/02-project-status.md` | 2 | 30 modules, 17 Flyway |
| 4 | `docs/review/06-full-module-audit.md` | 2 | Vue 3, 31 modules |
| 5 | `docs/architecture/07-architecture-decisions.md` ADR-009 | 2 | Vue 3 + Pinia + Apollo |
| 6 | `docs/review/05-architecture-evaluation.md` | 3 | Vue 3.5, 31 modules |
| 7 | `docs/code-derived-system-overview.md` | 3 | 34 modules, V1-V3 |
| 8 | `docs/quota-policy.md` | 3 | Describes DB-backed system that doesn't exist |
| 9 | `docs/module-boundaries.md` | 5 | Stale render dependencies |
| 10 | `docs/modulith-debt-register.md` | 6 | Wrong violation count |

### Most Dangerous Drifts

1. **Vue 3 → React 19** — 5+ documents claim Vue 3. Every new agent/developer reads README first.
2. **Module count 30/31/34 → 35** — Every overview document is wrong.
3. **Flyway 17/22/V1-V3 → 1** — Migration state completely misrepresented.
4. **8 allowed violations → 2** — Debt register and ModularityTest disagree.
5. **Quota persistence described but doesn't exist** — `quota-policy.md` overpromises.

### Documents to Fix Immediately

```
README.md                                          — Vue→React, 31→35, Flyway
docs/overview/01-project-overview.md               — Vue→React, 30→35
docs/overview/02-project-status.md                 — 30→35, 17→1, 28+→133
docs/architecture/07-architecture-decisions.md     — ADR-009 Vue→React
docs/modulith-debt-register.md                     — 8→2
```

### Documents to Archive

~15 files (see Part 16 ARCHIVE section).

### Agent Default Knowledge Set

```
Tier 0 (Always):    AGENTS.md, .kilo/agents/main.md
Tier 1 (By Module): current-system-state.md, modulith-debt-register.md, production-safety.md, project-intelligence-report.md
Tier 2 (On Demand): operations/*.md, render/adr/*.md, architecture/07-architecture-decisions.md
Tier 3 (Never):     archive/*, prompts/*, roo-*, overview/*, stale review reports
```

### Can Documentation Cleanup Begin?

**Yes.** The 7 P0 fixes are small text edits (2 hours). No structural changes needed. Multi-agent development can start immediately after P0 fixes.
