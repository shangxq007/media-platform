# Blueprint ↔ Reality Mapping Report

**Date:** 2026-06-22  
**Method:** Every blueprint claim validated against actual code: `settings.gradle.kts`, `build.gradle.kts`, `package-info.java`, `ModularityTest.java`, `ProductionSafetyValidator.java`, `frontend/package.json`, `V1__init_full_schema.sql`, `docker-compose.yml`, `.github/workflows/ci.yml`, `k8s/`, `gitops/`, per-module file counts  
**Blueprint source:** `docs/architecture/blueprint/` (10 files), `docs/roadmap/` (5 files)

---

## Part 1: Executive Summary

### Blueprint Alignment Score

**5 / 10**

The blueprints describe a well-architected target system. Approximately 60% of the described capabilities have been implemented, but the blueprints themselves are stale in critical ways: wrong module count (32 vs 35), wrong Flyway state (17 vs 1), and wrong frontend framework (ADR-009 says Vue). The blueprints were written during a "target architecture" phase and have not been updated to reflect the significant implementation progress since.

### Areas Closest to Blueprint

| Area | Alignment | Evidence |
|------|-----------|----------|
| Security & Identity | 9/10 | JWT, OIDC, API keys, RBAC, tenant isolation — all implemented |
| Payment | 8/10 | Real Stripe + Hyperswitch HTTP clients, webhook verification |
| Billing | 8/10 | Full subscription lifecycle, usage metering, credit wallet, reconciliation |
| Render Pipeline | 7/10 | 7+ providers (FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, JavaCV, Natron), state machine, incremental render |
| Outbox Events | 8/10 | jOOQ-backed outbox with idempotency, exponential backoff, dead letter |
| Policy & Feature Flags | 8/10 | OpenFeature integration, JDBC persistence, percentage rollout |
| Audit & Compliance | 8/10 | jOOQ audit records, burst detection, anomaly detection |

### Areas Most Deviated from Blueprint

| Area | Alignment | Evidence |
|------|-----------|----------|
| Quota Persistence | 1/10 | Blueprint describes DB-backed tracking; code is 4 ConcurrentHashMaps |
| Automation/Plugin Marketplace | 1/10 | Blueprint describes marketplace, versioning, sandbox; none implemented |
| Scheduler | 2/10 | Blueprint describes cron engine; code is in-memory job registry |
| Cloud Resource | 2/10 | Blueprint describes S3/GCS provisioning; code is in-memory catalog |
| Observability | 3/10 | Blueprint describes distributed tracing, log aggregation, alerting; code has basic metrics only |
| Multi-Provider Render Orchestration | 4/10 | Blueprint describes cost optimization, auto-scaling; code has basic provider selection |
| Social Publishing | 3/10 | Blueprint describes platform adapters; code has stubs |

---

## Part 2: Blueprint Inventory

| # | Blueprint | Lines | Purpose | Area | Last Modified | Status |
|---|-----------|-------|---------|------|---------------|--------|
| 1 | `system-blueprint.md` | 273 | Target system architecture overview | All | 2026-06-18 | **Partially Implemented** — module count, Flyway stale |
| 2 | `platform-composition-blueprint.md` | 287 | Capability composition model | All | 2026-06-18 | **Partially Implemented** — contracts exist, runtime doesn't |
| 3 | `reference-architecture-map.md` | 300 | External reference project mapping | All | 2026-06-18 | **Reference** — not actionable |
| 4 | `capability-opening-blueprint.md` | 863 | 6-level capability opening model | Extension/Plugin | 2026-06-18 | **Partially Implemented** — Level 0-1 only |
| 5 | `module-blueprint-render.md` | 140 | Render module target architecture | Render | 2026-06-18 | **Partially Implemented** — providers exist, orchestration basic |
| 6 | `module-blueprint-security-identity.md` | 143 | Security/identity target architecture | Security | 2026-06-18 | **Mostly Implemented** — JWT, OIDC, RBAC done |
| 7 | `module-blueprint-observability.md` | 128 | Observability target architecture | Observability | 2026-06-18 | **Partially Implemented** — metrics only |
| 8 | `module-blueprint-ai-provider.md` | 177 | AI provider target architecture | AI | 2026-06-18 | **Partially Implemented** — gateway exists, providers stub |
| 9 | `module-blueprint-artifact-storage.md` | 121 | Artifact storage target architecture | Storage | 2026-06-18 | **Partially Implemented** — S3 + local, no CDN |
| 10 | `module-blueprint-automation-plugin.md` | 179 | Automation/plugin target architecture | Extension | 2026-06-18 | **Not Implemented** — all gap=Critical |

### Roadmaps (supplementary)

| # | Roadmap | Lines | Purpose | Status |
|---|---------|-------|---------|--------|
| 1 | `render-pipeline-roadmap.md` | 99 | Render pipeline phases | Phase 1 in progress |
| 2 | `ai-provider-ecosystem-roadmap.md` | 698 | AI provider ecosystem | Stub only |
| 3 | `technical-debt-roadmap.md` | 264 | Technical debt tracking | P0 mostly resolved |
| 4 | `capability-opening-roadmap.md` | 611 | Capability opening phases | Phase 0 done |
| 5 | `automation-plugin-platform-roadmap.md` | 375 | Automation/plugin phases | Phase 0 only |

---

## Part 3: Platform Composition Validation

### system-blueprint.md Claims vs Code

| Blueprint Claim | Code Reality | Result |
|----------------|-------------|--------|
| "32 Gradle modules (30 business + platform-app + shared-kernel)" | 35 subprojects in `settings.gradle.kts` | **WRONG** — off by 3 |
| "Java 25 toolchain" | `JavaLanguageVersion.of(25)` | **CORRECT** |
| "Spring Boot 4.0.4" | `build.gradle.kts` line 8 | **CORRECT** |
| "Spring Modulith 2.0.4" | `spring-modulith-api:2.0.4` | **CORRECT** |
| "PostgreSQL 16 (production)" | `postgres:16-alpine` in docker-compose | **CORRECT** |
| "Temporal 1.33.0" | `temporal-spring-boot-starter:1.33.0` | **CORRECT** |
| "LiteFlow 2.15.3.2" | `liteflow-spring-boot-starter:2.15.3.2` | **CORRECT** |
| "React 19 + TypeScript 5.7 + Vite 6" | `package.json`: react ^19.0.0, typescript ~5.7.2, vite ^6.0.7 | **CORRECT** |
| "PF4J 3.15.0" | `pf4j:3.15.0` in platform-app build | **CORRECT** |
| "Flyway (17 versions)" | 1 file: `V1__init_full_schema.sql` (133 tables, 2339 lines) | **WRONG** — 1 not 17 |
| "H2" mentioned in 02-backend-architecture | Not used in production; only in test profile context | **MISLEADING** |
| "shared-kernel is the only OPEN module" | `Type.OPEN` in shared-kernel package-info | **CORRECT** |
| "REST APIs use /api/v1/* prefix" | Controllers use `/api/v1/` | **CORRECT** |
| "3 API replicas" (deployment) | K8s HPA: 1-5 replicas for api | **PARTIALLY** — HPA range, not fixed 3 |
| "PostgreSQL 16 primary + replica" | Single postgres in docker-compose, no replica config | **NOT IMPLEMENTED** |

### platform-composition-blueprint.md Claims vs Code

| Blueprint Claim | Code Reality | Result |
|----------------|-------------|--------|
| "SystemAction interfaces exist in shared-kernel" | `shared-kernel` has capability contracts | **CORRECT** |
| "ExtensionPoint interfaces exist" | Shared kernel has extension point types | **CORRECT** |
| "InMemory registries" | Extension registry uses in-memory storage | **CORRECT** |
| "Runtime/event bus/hook/marketplace NOT implemented" | No runtime, no event bus, no marketplace code | **CORRECT** (accurately self-assessed) |
| "Temporal integration NOT implemented" | Temporal is implemented in workflow-module (20 files) | **WRONG** — blueprint says not implemented, but it is |
| "LiteFlow integration NOT implemented" | LiteFlow nodes exist in render-module (7 files) | **WRONG** — blueprint says not implemented, but it is |

---

## Part 4: Module Blueprint Validation

### Module-by-Module Reality

| Module | Blueprint File | Prod Files | Test Files | Blueprint Claim | Reality | Implemented % |
|--------|---------------|------------|------------|-----------------|---------|---------------|
| render | `module-blueprint-render.md` | 600 | 138 | Multi-provider, scheduling, cost opt | 7+ providers, state machine, incremental render, cache | **70%** |
| identity | `module-blueprint-security-identity.md` | 99 | 34 | JWT, OAuth2, RBAC, workspace | All implemented + API keys, OIDC JIT | **90%** |
| observability | `module-blueprint-observability.md` | 9 | 3 | Metrics, tracing, logs, alerting | Basic metrics + health checks only | **25%** |
| ai | `module-blueprint-ai-provider.md` | 33 | 8 | Multi-provider, cost tracking, BYOK | Gateway + stub provider + model routing | **35%** |
| artifact-storage | `module-blueprint-artifact-storage.md` | 19 | 6 | S3/GCS/local, lifecycle, CDN | S3 + local, basic lifecycle | **50%** |
| automation-plugin | `module-blueprint-automation-plugin.md` | 55 | 19 | Marketplace, sandbox, lifecycle | Extension execution + sandbox + CLI tools | **30%** |
| billing | (no dedicated blueprint) | 86 | 15 | — | Full subscription, usage, credit wallet, reconciliation | **85%** |
| payment | (no dedicated blueprint) | 30 | 4 | — | Real Stripe + Hyperswitch, webhooks | **80%** |
| quota | (no dedicated blueprint) | 10 | 1 | — | In-memory only, no persistence | **15%** |
| commerce | (no dedicated blueprint) | 28 | 4 | — | Full checkout flow, catalog, cart | **75%** |
| entitlement | (no dedicated blueprint) | 61 | 17 | — | Grants, bundles, quotas, feature flags | **80%** |
| workflow | (no dedicated blueprint) | 22 | 6 | — | Temporal workflows + local fallback | **70%** |
| notification | (no dedicated blueprint) | 44 | 14 | — | Multi-channel (Novu, email, SMS, webhook) | **70%** |
| storage | (no dedicated blueprint) | 19 | 6 | — | S3 SDK, presign, local fallback | **70%** |
| delivery | (no dedicated blueprint) | 36 | 4 | — | 6 protocol adapters, policy-based | **75%** |
| policy | (no dedicated blueprint) | 39 | 20 | — | ABAC, OpenFeature, feature flags | **80%** |
| audit | (no dedicated blueprint) | 40 | 15 | — | jOOQ audit, alerts, anomaly detection | **80%** |
| outbox | (no dedicated blueprint) | 6 | 3 | — | jOOQ outbox, idempotency, backoff | **80%** |
| prompt | (no dedicated blueprint) | 23 | 4 | — | Versioning, risk analysis, secret detection | **70%** |
| extension | (no dedicated blueprint) | 55 | 19 | — | Extension lifecycle, sandbox, CLI | **65%** |
| scheduler | (no dedicated blueprint) | 7 | 1 | — | In-memory job registry, no cron | **20%** |
| cloud-resource | (no dedicated blueprint) | 9 | 1 | — | In-memory catalog, stub provider | **15%** |
| federation | (no dedicated blueprint) | 101 | 43 | — | Stub core, full GraphQL + NLQ | **50%** |
| social-publish | (no dedicated blueprint) | 29 | 1 | — | Service layer, stub adapters | **30%** |
| sandbox | (no dedicated blueprint) | 15 | 2 | — | Script engine exec, external worker | **60%** |
| shared-kernel | (no dedicated blueprint) | 127 | 20 | — | Tenant isolation, SPIs, events, capabilities | **90%** |
| platform-app | (no dedicated blueprint) | 101 | 47 | — | Web facade, security, production safety | **85%** |
| spring-ai-adapter | (no dedicated blueprint) | 2 | 0 | — | Spring AI OpenAI + LiteLLM (compilation error) | **40%** |

---

## Part 5: Capability Matrix

| Capability | Blueprint Target | Reality | Status |
|-----------|-----------------|---------|--------|
| **Identity** | JWT + OAuth2 + API keys + RBAC | ✅ All implemented, OIDC JIT provisioning | **Implemented** |
| **RBAC** | Role/permission model | ✅ Roles, permissions, assignments, workspace groups | **Implemented** |
| **Payment** | Stripe + Hyperswitch + webhooks | ✅ Real HTTP clients, webhook signature verification | **Implemented** |
| **Billing** | Subscriptions + usage + credit wallet | ✅ Full lifecycle: plans, contracts, metering, rating, reconciliation | **Implemented** |
| **Quota** | DB-backed quota tracking, profiles, enforcement | ⚠️ Domain model exists, in-memory only (4 ConcurrentHashMaps) | **Planned** |
| **Render** | Multi-provider orchestration, cost optimization | ⚠️ 7+ providers exist, basic selection policy, no cost optimization | **Partial** |
| **Asset** | S3/GCS/local, lifecycle, CDN | ⚠️ S3 + local, basic lifecycle, no CDN | **Partial** |
| **Workflow** | Temporal durable workflows | ✅ Temporal integration + local fallback, 20 files | **Implemented** |
| **Storage** | S3-compatible blob storage | ✅ AWS SDK v2, presigned URLs, local fallback | **Implemented** |
| **Delivery** | Multi-protocol content delivery | ✅ 6 adapters (S3Mirror, SFTP, SMB, WebDAV, HTTPS PUT) | **Implemented** |
| **Audit** | Audit trail, compliance, anomaly detection | ✅ jOOQ-backed, burst detection, anomaly detection | **Implemented** |
| **AI** | Multi-provider gateway, model routing, cost tracking | ⚠️ Gateway + stub provider + model routing; no cost tracking | **Partial** |
| **GraphQL** | Optional GraphQL API | ✅ Full schema, resolvers, DataLoaders, complexity limiting | **Implemented** |
| **MCP** | MCP endpoints for AI tools | ✅ `/api/v1/mcp/*` endpoints with API key auth | **Implemented** |
| **Observability** | Metrics, tracing, logs, alerting | ⚠️ Prometheus metrics + basic health; no tracing, no log aggregation | **Partial** |
| **Scheduler** | Cron engine, job scheduling | ⚠️ In-memory job registry only, no cron engine | **Planned** |
| **Cloud Resource** | S3/GCS/Azure provisioning | ⚠️ In-memory catalog, stub provider | **Planned** |
| **Frontend** | React 19 SPA with timeline editor | ✅ React 19, Remotion, Zustand, dnd-kit, timeline editor | **Implemented** |
| **Feature Flags** | OpenFeature + Unleash | ✅ OpenFeature integration, JDBC persistence, percentage rollout | **Implemented** |
| **Notifications** | Multi-channel delivery | ✅ Novu, email, SMS, webhook providers | **Implemented** |
| **Plugin System** | PF4J-based extensions | ⚠️ PF4J dependency exists (2 files), extension-module has 55 files | **Partial** |
| **Sandbox Runtime** | Sandboxed script execution | ✅ Groovy/JS/Python via ScriptEngine, external worker | **Implemented** |
| **Secrets Management** | HashiCorp Vault + env fallback | ✅ Vault KV v2 integration, env fallback | **Implemented** |
| **Outbox Pattern** | Transactional outbox | ✅ jOOQ-backed, idempotency, exponential backoff | **Implemented** |
| **Project Export/Import** | ZIP packaging with assets | ✅ Full export/import with scrubbing, asset remapping | **Implemented** |
| **Collaboration** | Shared resources | ⚠️ Basic sharing exists, limited functionality | **Partial** |

---

## Part 6: Render Blueprint Validation

### module-blueprint-render.md vs Code

| Blueprint Claim | Code Reality | Status |
|----------------|-------------|--------|
| "FFmpeg/JavaCV single provider" | FFmpeg (5 files), JavaCV (4 files), plus GStreamer (2), MLT (2), Remotion (14), GPAC (5), OFX (1), Natron (11) | **Blueprint is stale** — 7+ providers now exist |
| "Simple FIFO scheduling" | `RenderWorkerQueueService`, `PipelineDagExecutorService` exist | **Partial** — DAG execution exists but no advanced scheduling |
| "Basic job lifecycle" | `RenderJobStateMachine` with 10+ states (QUEUED→EXECUTING→COMPLETED + FAILED/FALLBACKING/RETRYING/CANCELLED/REJECTED) | **More advanced than blueprint claims** |
| "No cost optimization" | `CostEstimationService` with per-provider profiles exists in billing-module | **Partially implemented** — cost estimation exists but not integrated into provider selection |
| "No auto-scaling" | K8s HPA: render-worker 1-10 replicas | **Partially implemented** — HPA exists but not application-level auto-scaling |
| "No real-time progress" | No WebSocket/SSE for progress | **NOT implemented** |

### Provider Status (Code Reality)

| Provider | Files | Status | ADR-007 Decision |
|----------|-------|--------|-----------------|
| FFmpeg | 5 | **Active** — real command builder | Core provider |
| GStreamer | 2 | **Active** | Not mentioned |
| MLT | 2 | **Active** | Not mentioned |
| Remotion | 14 | **Active** — CLI-based | Not mentioned |
| GPAC | 5 | **Active** — packaging | Not mentioned |
| OFX | 1 | **Exists** — Java2D simulation | ADR-007: deprecated |
| JavaCV | 4 | **Exists** — media probe + transcode | ADR-007: deprecated |
| Natron | 11 | **Exists** — POC scripts | ADR-007: hold/deprecated |

**Reality:** ADR-007 says OFX/JavaCV/Natron should be removed from provider registry. 16 files still exist.

---

## Part 7: Quota Blueprint Validation

### No Dedicated Quota Blueprint Exists

There is no `module-blueprint-quota.md`. The quota system is described across:
- `docs/quota-policy.md` — describes DB-backed QuotaPolicy, QuotaProfile, QuotaBucket
- `docs/billing-access/05-cost-control.md` — describes quota enforcement
- `system-blueprint.md` — mentions quota in persistence strategy

### What Blueprint Describes vs Code Reality

| Blueprint Claim | Code Reality | Status |
|----------------|-------------|--------|
| QuotaPolicy with DB persistence | `QuotaPolicy` record exists but stored in `ConcurrentHashMap` | **OVERPROMISES** |
| QuotaBucket with tracking | `QuotaBucket` record exists in `ConcurrentHashMap` | **OVERPROMISES** |
| QuotaProfile with tier-based limits | No QuotaProfile class found in quota module | **NOT IMPLEMENTED** |
| QuotaDecisionService | No QuotaDecisionService in quota module (exists in entitlement-module) | **WRONG MODULE** |
| Runtime enforcement | `QuotaService` checks in-memory buckets | **IN-MEMORY ONLY** |
| Billing integration | `QuotaBucketSummary` added for render integration (issue-003b) | **PARTIAL** |

### Critical Gap

`docs/quota-policy.md` line 10 says: "Source: `entitlement-module/.../domain/QuotaPolicy.java`" — but QuotaPolicy is in `quota-billing-module`. The quota doc references the wrong module.

**Quota persistence is the single largest gap between blueprint and reality.** The blueprint describes a production-grade quota system. The code is an in-memory prototype.

---

## Part 8: Billing & Payment Validation

### Payment Module (30 files)

| Blueprint/Doc Claim | Code Reality | Status |
|---------------------|-------------|--------|
| Stripe integration | `StripeHttpPaymentProvider` — real HttpClient to Stripe API | **Implemented** |
| Hyperswitch integration | `HyperswitchHttpPaymentProvider` — real HttpClient | **Implemented** |
| Webhook verification | HMAC-SHA256 signature verification | **Implemented** |
| Webhook idempotency | `ProviderWebhookEventRepository` (jOOQ) | **Implemented** |
| Noop providers | `NoopStripePaymentProvider`, `NoopHyperswitchPaymentProvider` | **Implemented** (dev/testing) |
| Payment attempt tracking | `PaymentAttemptRepository` (jOOQ) | **Implemented** |

### Billing Module (86 files)

| Blueprint/Doc Claim | Code Reality | Status |
|---------------------|-------------|--------|
| Subscription plans | `SubscriptionBillingService` — full CRUD | **Implemented** |
| Subscription contracts | Lifecycle management (BASE/ADD_ON roles) | **Implemented** |
| Usage metering | `UsageMeteringService` with idempotency | **Implemented** |
| Rating engine | `RatingEngine` — flat + tiered pricing | **Implemented** |
| Credit wallet | `CreditWalletService` — credit/debit/reserve/finalize/release | **Implemented** |
| Billing cycles | `BillingCycleService` — aggregate→rate→charge→settle | **Implemented** |
| Pricing rules | `PricingRuleService` — custom per-tenant, discount policies | **Implemented** |
| Cost estimation | `CostEstimationService` — per-provider profiles | **Implemented** |
| Budget guard | `BudgetGuardService` — soft/hard limits, auto-throttle | **Implemented** |
| Reconciliation | `ReconciliationService` — third-party invoice import | **Implemented** |
| Kill Bill integration | `NoopKillBillBillingEngine` — stub only | **NOT implemented** |

### Commerce Module (28 files)

| Blueprint/Doc Claim | Code Reality | Status |
|---------------------|-------------|--------|
| Product catalog | `CommerceCatalogService` — 9 hardcoded products | **Implemented** |
| Cart management | `CommerceCartService` — CRUD, line merging | **Implemented** |
| Checkout flow | `CheckoutOrchestrator` (412 lines) — session→payment→order→fulfillment | **Implemented** |
| Revenue tracking | DB aggregation via `PurchaseOrderRepository` | **Implemented** |
| Medusa.js integration | `NoopMedusaCatalogAdapter` — stub | **NOT implemented** |

---

## Part 9: Frontend Blueprint Validation

### Blueprint Claims vs Code

| Source | Claim | Code Reality | Status |
|--------|-------|-------------|--------|
| `system-blueprint.md` | "React 19 + TypeScript 5.7 + Vite 6 + TanStack Router/Query" | `package.json`: react ^19.0.0, typescript ~5.7.2, vite ^6.0.7 | **CORRECT** |
| ADR-009 (`07-architecture-decisions.md`) | "Vue 3 + Vite + Pinia + Apollo Client" | React 19, Zustand, axios, graphql-request | **WRONG** |
| `04-frontend-architecture.md` | "React 19, Zustand, TanStack" | Matches code | **CORRECT** |

### Frontend Actual Stack (from `package.json`)

| Category | Technology | Version |
|----------|-----------|---------|
| UI Framework | React | ^19.0.0 |
| State Management | Zustand | ^5.0.0 |
| Server State | TanStack Query | ^5.60.0 |
| Routing | TanStack Router | ^1.90.0 |
| Validation | Zod | ^4.3.6 |
| Video Composition | Remotion | ^4.0.0 |
| HTTP Client | axios | ^1.7.9 |
| GraphQL | graphql-request | ^7.4.0 |
| CSS | Tailwind CSS | ^3.4.17 |
| Build | Vite | ^6.0.7 |
| Testing | Vitest + Testing Library | ^3.0.0 |
| Auth | oidc-client-ts | ^3.5.0 |
| Monitoring | @sentry/react | ^10.53.1 |
| Drag & Drop | @dnd-kit | ^6.3.1 |

### Frontend Code Scale

| Metric | Count |
|--------|-------|
| `.tsx` files | 34 |
| `.ts` files | 101 |
| `.vue` files | **0** |
| Vue imports | **0** |

---

## Part 10: AI Roadmap Validation

### ai-provider-ecosystem-roadmap.md vs Code

| Roadmap Claim | Code Reality | Status |
|---------------|-------------|--------|
| "StubChatProvider active" | `StubChatProvider` in ai-module with failure simulation | **CORRECT** |
| "OpenAiChatProvider placeholder" | `SpringAiOpenAiChatProvider` in spring-ai-adapter | **Implemented** (not just placeholder) |
| "Simple ModelRouter" | `ConfigurableModelRouter` with RoutePlan/RouteTarget | **More advanced than claimed** |
| "Multi-provider routing" | `AiGatewayService` with capability-based routing + fallback chains | **Implemented** |
| "Cost tracking" | No cost tracking in ai-module | **NOT implemented** |
| "BYOK (Bring Your Own Key)" | `TenantLitellmKeyService` in platform-app (but broken — compilation error) | **Broken** |
| "Prompt marketplace" | Not implemented | **NOT implemented** |
| Spring AI runtime | `spring-ai-adapter` exists but has compilation error | **Broken** |

### spring-ai-adapter Status

| Attribute | Value |
|-----------|-------|
| Files | 2 |
| Compilation | **FAILS** — `TenantLitellmKeyService` not found |
| Runtime | Not enabled by default |
| Providers | OpenAI-compatible + LiteLLM |

**Note:** Spring AI runtime is intentionally not in active runtime per project rules. The compilation error is a known issue.

---

## Part 11: Deployment Blueprint Validation

### system-blueprint.md Deployment Section vs Code

| Blueprint Claim | Code Reality | Status |
|----------------|-------------|--------|
| "3-stage Docker build" | `Dockerfile`: Node 22 → Gradle 9.1/JDK 25 → Eclipse Temurin 25 JRE | **CORRECT** |
| "node:22-alpine" (frontend) | Dockerfile stage 1 | **CORRECT** |
| "gradle:9.1-jdk25-noble" (backend) | Dockerfile stage 2 | **CORRECT** |
| "eclipse-temurin:25-jre-jammy" (runtime) | Dockerfile stage 3 | **CORRECT** |
| "PostgreSQL 16 + app" (local) | `docker-compose.yml`: postgres:16-alpine + app | **CORRECT** |
| "3 API replicas" | K8s HPA: 1-5 replicas | **PARTIAL** — HPA range, not fixed |
| "PostgreSQL primary + replica" | Single postgres instance | **NOT implemented** |
| "Temporal Server" | Not in docker-compose; requires external Temporal | **External dependency** |
| "S3-compatible object storage" | Config exists for S3/R2; no local S3 in compose | **External dependency** |
| "Sentry + OpenReplay + Prometheus + Grafana" | Sentry configured, Prometheus endpoint exists; no Grafana dashboards in repo | **Partial** |

### CI/CD Validation

| Blueprint Claim | Code Reality | Status |
|----------------|-------------|--------|
| "Git Push → CI Pipeline → Tests → Build → Docker → Registry → GitOps" | `.github/workflows/ci.yml`: backend test → frontend test → build images → push to GHCR → update GitOps → create staging PR | **CORRECT** |
| "GHCR registry" | `ghcr.io/${{ github.repository_owner }}` | **CORRECT** |
| "3 images" | platform-api, platform-render-worker, platform-sandbox-worker | **CORRECT** |
| "ArgoCD" | `gitops/argocd/application-staging.yaml` + `application-production.yaml` | **CORRECT** |
| "Staging auto-sync" | ArgoCD staging has auto-sync | **CORRECT** |
| "Production manual" | ArgoCD production has no auto-sync | **CORRECT** |

### K8s Validation

| Attribute | Code Reality |
|-----------|-------------|
| Base files | 21 (deployments, services, ingress, HPA, network policies, PVC, secrets) |
| Overlays | staging, production (Kustomize) |
| Deployments | api, render-worker, sandbox-worker, egress-proxy |
| Security | runAsNonRoot, readOnlyRootFilesystem, seccompProfile, capabilities.drop: ALL |
| Egress control | NetworkPolicies route through egress-proxy |
| HPA | api: 1-5 replicas (70% CPU), render-worker: 1-10 replicas (80% CPU) |

---

## Part 12: Blueprint Drift Analysis

### Critical Drift

| # | Blueprint | Claim | Reality | Impact |
|---|-----------|-------|---------|--------|
| 1 | `system-blueprint.md` | "Flyway (17 versions)" | 1 file (V1, 133 tables) | Misleads about migration state |
| 2 | `system-blueprint.md` | "32 Gradle modules" | 35 subprojects | Misleads about project scope |
| 3 | ADR-009 | "Vue 3 + Pinia + Apollo" | React 19 + Zustand + TanStack | Permanent wrong record |
| 4 | `module-blueprint-render.md` | "FFmpeg/JavaCV single provider" | 7+ providers | Understates implementation |
| 5 | `platform-composition-blueprint.md` | "Temporal/LiteFlow NOT implemented" | Both implemented (20 + 7 files) | Understates implementation |

### High Drift

| # | Blueprint | Claim | Reality | Impact |
|---|-----------|-------|---------|--------|
| 6 | `module-blueprint-security-identity.md` | "OAuth2 disabled in preview" | OAuth2 fully implemented with Authentik | Understates implementation |
| 7 | `module-blueprint-observability.md` | "Prometheus metrics + basic health" | Also: Sentry, OTLP, structured logging, circuit breaker | Understates implementation |
| 8 | `docs/quota-policy.md` | DB-backed quota tracking | In-memory only (4 ConcurrentHashMaps) | Overstates implementation |
| 9 | `render-pipeline-roadmap.md` | "MVP: FFmpeg/JavaCV" | 7+ providers, state machine, incremental render | Understates implementation |
| 10 | `system-blueprint.md` | "PostgreSQL primary + replica" | Single instance | Overstates deployment |

### Medium Drift

| # | Blueprint | Claim | Reality | Impact |
|---|-----------|-------|---------|--------|
| 11 | `capability-opening-blueprint.md` | "Level 0-1 only" | Extension-module has 55 files with sandbox, CLI, audit | Understates implementation |
| 12 | `module-blueprint-automation-plugin.md` | "All gap=Critical" | Extension-module actually has significant implementation | Understates implementation |
| 13 | `ai-provider-ecosystem-roadmap.md` | "Simple ModelRouter" | `ConfigurableModelRouter` with RoutePlan/RouteTarget | Understates implementation |
| 14 | `docs/architecture/README.md` | "85% function complete" | Needs validation — quota gap alone may lower this | Potentially overstated |

### Low Drift

| # | Blueprint | Claim | Reality | Impact |
|---|-----------|-------|---------|--------|
| 15 | `reference-architecture-map.md` | External reference mapping | Reference document, not actionable | No impact |
| 16 | Various blueprints | "PF4J 3.15.0" | Only 2 files reference PF4J | Adoption lower than implied |

---

## Part 13: Blueprint Value Assessment

### KEEP (Active, Useful, Mostly Accurate)

| Blueprint | Reason |
|-----------|--------|
| `system-blueprint.md` | Fix module count and Flyway; otherwise accurate target architecture |
| `platform-composition-blueprint.md` | Fix Temporal/LiteFlow status; composition model is useful |
| `module-blueprint-security-identity.md` | Mostly implemented, useful reference |
| `module-blueprint-render.md` | Fix provider count; render architecture is useful |
| `reference-architecture-map.md` | External reference mapping — keep as research document |

### UPDATE (Needs Fixes to Be Useful)

| Blueprint | Fix Required |
|-----------|-------------|
| `system-blueprint.md` | 32→35 modules, 17→1 Flyway, add billing/commerce/entitlement details |
| `platform-composition-blueprint.md` | Mark Temporal + LiteFlow as implemented |
| `module-blueprint-render.md` | Update from "single provider" to "7+ providers" |
| `module-blueprint-observability.md` | Add Sentry, OTLP, structured logging, circuit breaker |
| `module-blueprint-ai-provider.md` | Update from "placeholder" to "gateway + routing implemented" |
| `capability-opening-blueprint.md` | Update current status to reflect extension-module progress |

### ARCHIVE (Superseded or Low Value)

| Blueprint | Reason |
|-----------|--------|
| `module-blueprint-automation-plugin.md` | Describes marketplace/sandbox that won't be built for 6+ months |
| `module-blueprint-artifact-storage.md` | S3 + local already implemented; CDN is future |
| `docs/roadmap/automation-plugin-platform-roadmap.md` | Phase 0 only, deferred to Q3-Q4 2026 |
| `docs/roadmap/capability-opening-roadmap.md` | Phase 0 done, Phase 1+ deferred |
| `docs/roadmap/ai-provider-ecosystem-roadmap.md` | 698 lines for a future ecosystem; current stub is sufficient |

### DELETE

**None.** Archive is sufficient.

---

## Part 14: Roadmap Reconstruction

### Original Vision (from Blueprints)

The blueprints describe a platform that:
1. Renders video with 7+ providers, auto-scaling workers, cost optimization
2. Manages quota with DB-backed profiles, tier-based limits, billing integration
3. Supports a plugin marketplace with sandboxed execution
4. Provides full observability with distributed tracing and alerting
5. Enables capability opening at 6 levels (internal → marketplace)
6. Integrates AI with multi-provider routing, cost tracking, BYOK

### Current Reality

| Capability | Implementation Level |
|-----------|---------------------|
| Render pipeline | **70%** — 7+ providers, state machine, incremental render, cache |
| Billing & payment | **85%** — full subscription, usage, credit wallet, reconciliation |
| Identity & security | **90%** — JWT, OIDC, RBAC, API keys, tenant isolation |
| Workflow orchestration | **70%** — Temporal + local fallback |
| Content delivery | **75%** — 6 protocol adapters |
| Feature flags & policy | **80%** — OpenFeature, ABAC |
| Audit & compliance | **80%** — jOOQ audit, anomaly detection |
| Notifications | **70%** — multi-channel (Novu, email, SMS, webhook) |
| Quota | **15%** — in-memory only |
| Observability | **25%** — basic metrics only |
| Plugin/automation | **30%** — extension execution only |
| Scheduler | **20%** — in-memory only |
| Cloud resource | **15%** — in-memory only |
| AI integration | **35%** — gateway + stub |

### Likely Next Phase (3-6 months)

| Phase | Focus | Rationale |
|-------|-------|-----------|
| **Month 1-2** | Quota persistence + scheduler | Last major stubs blocking production |
| **Month 2-3** | Observability (tracing + dashboards) | Production monitoring gap |
| **Month 3-4** | Render cost optimization + auto-scaling | Revenue optimization |
| **Month 4-5** | AI provider ecosystem (multi-provider) | Feature expansion |
| **Month 5-6** | Plugin marketplace (Phase 1) | Platform extensibility |

---

## Part 15: Canonical Architecture Set

### Current Architecture (What Exists)

```
Canonical:
  AGENTS.md                          — Project overview, priorities, rules
  .kilo/agents/main.md               — Module boundaries, development rules
  docs/architecture/current/current-system-state.md  — System state
  docs/production-safety.md          — Production safety checks
  ModularityTest.java                — Module boundary enforcement
  ProductionSafetyValidator.java     — Production safety enforcement
```

### Future Blueprint (Target State)

```
Canonical (after updates):
  docs/architecture/blueprint/system-blueprint.md    — Fix module count, Flyway
  docs/architecture/blueprint/platform-composition-blueprint.md  — Fix Temporal/LiteFlow
  docs/architecture/blueprint/module-blueprint-render.md         — Fix provider count

Supporting:
  docs/roadmap/render-pipeline-roadmap.md            — Render phases
  docs/roadmap/technical-debt-roadmap.md             — Tech debt tracking
```

### Historical Decisions

```
Canonical:
  docs/architecture/07-architecture-decisions.md     — Fix ADR-009 Vue→React
  docs/render/adr/ADR-001 through ADR-007            — Render decisions

Archive:
  docs/architecture-decisions.md                     — Duplicate of 07
  docs/roadmap/capability-opening-roadmap.md         — Deferred
  docs/roadmap/automation-plugin-platform-roadmap.md — Deferred
  docs/roadmap/ai-provider-ecosystem-roadmap.md      — Deferred
```

---

## Part 16: Documentation Cleanup Inputs

### KEEP

```
docs/architecture/blueprint/system-blueprint.md      (fix first)
docs/architecture/blueprint/platform-composition-blueprint.md  (fix first)
docs/architecture/blueprint/reference-architecture-map.md
docs/architecture/blueprint/module-blueprint-security-identity.md
docs/architecture/blueprint/module-blueprint-render.md  (fix first)
docs/roadmap/render-pipeline-roadmap.md
docs/roadmap/technical-debt-roadmap.md
docs/architecture/01-system-architecture.md through 08-deployment-architecture.md
docs/architecture/current/*.md (4 files)
docs/render/adr/*.md (7 files)
```

### UPDATE

```
docs/architecture/blueprint/system-blueprint.md           — 32→35, 17→1
docs/architecture/blueprint/platform-composition-blueprint.md — Temporal/LiteFlow status
docs/architecture/blueprint/module-blueprint-render.md    — single→7+ providers
docs/architecture/blueprint/module-blueprint-observability.md — add Sentry/OTLP
docs/architecture/blueprint/module-blueprint-ai-provider.md — gateway implemented
docs/architecture/blueprint/capability-opening-blueprint.md — Level 0-1 progress
docs/architecture/07-architecture-decisions.md            — ADR-009 Vue→React
docs/quota-policy.md                                      — add in-memory disclaimer
```

### ARCHIVE

```
docs/architecture/blueprint/module-blueprint-automation-plugin.md
docs/architecture/blueprint/module-blueprint-artifact-storage.md
docs/roadmap/automation-plugin-platform-roadmap.md
docs/roadmap/capability-opening-roadmap.md
docs/roadmap/ai-provider-ecosystem-roadmap.md
```

### DELETE

**None.**

---

## Part 17: Multi-Agent Impact

### Which Blueprints Must Be Loaded?

| Blueprint | When to Load | Why |
|-----------|-------------|-----|
| `system-blueprint.md` (after fix) | Planner agent boot | Target architecture overview |
| `module-blueprint-security-identity.md` | Security-related tasks | Mostly accurate |
| `module-blueprint-render.md` (after fix) | Render tasks | Provider architecture |
| `platform-composition-blueprint.md` (after fix) | Architecture decisions | Composition model |

### Which Should NOT Be Auto-Loaded?

| Blueprint | Why |
|-----------|-----|
| `capability-opening-blueprint.md` | 863 lines, describes future capability model — wastes context |
| `reference-architecture-map.md` | 300 lines of external references — not actionable |
| `module-blueprint-automation-plugin.md` | Describes unimplemented marketplace — misleads |
| `module-blueprint-artifact-storage.md` | S3 already implemented — stale gaps |
| `docs/roadmap/ai-provider-ecosystem-roadmap.md` | 698 lines for future ecosystem |
| `docs/roadmap/capability-opening-roadmap.md` | 611 lines for deferred phases |
| `docs/roadmap/automation-plugin-platform-roadmap.md` | 375 lines for deferred phases |

### Agent Loading Strategy

```
Planner Agent:
  Always: AGENTS.md, .kilo/agents/main.md
  By Task: system-blueprint.md (after fix), technical-debt-roadmap.md
  Never: capability-opening-blueprint.md, reference-architecture-map.md

Coder Agent:
  Always: AGENTS.md, .kilo/agents/main.md
  By Module: module-blueprint-render.md (render tasks), module-blueprint-security-identity.md (security tasks)
  Never: automation-plugin blueprint, capability-opening blueprint

Tester Agent:
  Always: AGENTS.md, .kilo/agents/main.md
  By Task: docs/review/known-limitations.md, docs/production-safety.md
  Never: Any blueprint (test from code, not blueprints)

Reviewer Agent:
  Always: AGENTS.md, .kilo/agents/main.md, docs/modulith-debt-register.md
  By Task: module-blueprint-*.md (to verify implementation matches target)
  Never: reference-architecture-map.md
```

---

## Part 18: Executive Recommendation

### Which blueprints have completed their mission?

| Blueprint | Reason |
|-----------|--------|
| `module-blueprint-security-identity.md` | 90% implemented — blueprint served its purpose |
| `module-blueprint-artifact-storage.md` | S3 + local implemented — CDN is future, blueprint not needed now |

### Which should be updated?

| Blueprint | Why |
|-----------|-----|
| `system-blueprint.md` | Fix 3 facts (module count, Flyway, PG replica) — otherwise useful |
| `module-blueprint-render.md` | Fix provider count — render is the core business domain |
| `platform-composition-blueprint.md` | Fix Temporal/LiteFlow status |
| ADR-009 | Fix Vue→React — permanent record |

### Which should continue guiding development?

| Blueprint | Why |
|-----------|-----|
| `module-blueprint-render.md` (after fix) | Render pipeline is the core business — cost optimization, auto-scaling remain valid targets |
| `module-blueprint-observability.md` | Observability gap is real — distributed tracing and alerting needed |
| `technical-debt-roadmap.md` | Tech debt tracking is active and useful |

### Next Steps for Documentation Governance

1. **Immediate (this week):** Fix 4 blueprint files (module count, Flyway, providers, Temporal/LiteFlow)
2. **Next week:** Fix ADR-009 Vue→React
3. **Week 3:** Archive 5 deferred roadmap/blueprint files
4. **Week 4:** Establish blueprint update cadence (monthly review)

---

## Summary

### Blueprint Alignment Score

**5 / 10** — Significant implementation progress beyond what blueprints describe, but blueprints are stale in critical facts.

### Top 10 Active Blueprints

| # | Blueprint | Alignment | Action |
|---|-----------|-----------|--------|
| 1 | `module-blueprint-security-identity.md` | 90% | Keep |
| 2 | `system-blueprint.md` | 70% | Fix module count + Flyway |
| 3 | `module-blueprint-render.md` | 65% | Fix provider count |
| 4 | `platform-composition-blueprint.md` | 60% | Fix Temporal/LiteFlow |
| 5 | `reference-architecture-map.md` | N/A | Keep (reference) |
| 6 | `module-blueprint-observability.md` | 40% | Update with Sentry/OTLP |
| 7 | `module-blueprint-ai-provider.md` | 35% | Update with gateway status |
| 8 | `capability-opening-blueprint.md` | 30% | Update with extension progress |
| 9 | `module-blueprint-artifact-storage.md` | 50% | Archive (S3 done) |
| 10 | `module-blueprint-automation-plugin.md` | 10% | Archive (deferred) |

### Top 10 Stale Blueprints

| # | Blueprint | Why Stale |
|---|-----------|-----------|
| 1 | `module-blueprint-automation-plugin.md` | Describes marketplace that won't exist for 6+ months |
| 2 | `docs/roadmap/automation-plugin-platform-roadmap.md` | 375 lines for deferred phases |
| 3 | `docs/roadmap/capability-opening-roadmap.md` | 611 lines for deferred phases |
| 4 | `docs/roadmap/ai-provider-ecosystem-roadmap.md` | 698 lines for future ecosystem |
| 5 | `module-blueprint-artifact-storage.md` | S3 already implemented |
| 6 | ADR-009 (in 07-architecture-decisions.md) | Vue 3 claim — wrong |
| 7 | `system-blueprint.md` Flyway section | 17 vs 1 |
| 8 | `system-blueprint.md` module count | 32 vs 35 |
| 9 | `module-blueprint-render.md` provider section | Single vs 7+ |
| 10 | `platform-composition-blueprint.md` runtime section | "Not implemented" vs implemented |

### Most Important Drifts

1. **Flyway 17 → 1** — `system-blueprint.md` claims 17 versions; code has 1 consolidated baseline
2. **Module count 32 → 35** — blueprint misses 3 modules
3. **Render single provider → 7+** — blueprint understates implementation by 6x
4. **Temporal/LiteFlow "not implemented" → implemented** — blueprint understates workflow capability
5. **Vue 3 → React 19** — ADR-009 permanent wrong record

### Blueprint → Update → Archive

**Update:** `system-blueprint.md`, `module-blueprint-render.md`, `platform-composition-blueprint.md`, `module-blueprint-observability.md`, `module-blueprint-ai-provider.md`, ADR-009

**Archive:** `module-blueprint-automation-plugin.md`, `module-blueprint-artifact-storage.md`, 3 deferred roadmaps

**Keep:** `reference-architecture-map.md`, `module-blueprint-security-identity.md`, `technical-debt-roadmap.md`, `render-pipeline-roadmap.md`

### Future Architecture Knowledge Base Structure

```
Tier 0 (Always Load):
  AGENTS.md
  .kilo/agents/main.md

Tier 1 (Current Architecture):
  docs/architecture/current/*.md (4 files)
  docs/production-safety.md
  docs/modulith-debt-register.md

Tier 2 (Target Architecture — after fixes):
  docs/architecture/blueprint/system-blueprint.md
  docs/architecture/blueprint/module-blueprint-render.md
  docs/architecture/blueprint/platform-composition-blueprint.md

Tier 3 (Decisions & Roadmap):
  docs/architecture/07-architecture-decisions.md (fix ADR-009)
  docs/render/adr/*.md (7 files)
  docs/roadmap/render-pipeline-roadmap.md
  docs/roadmap/technical-debt-roadmap.md

Tier 4 (Never Auto-Load):
  docs/architecture/blueprint/capability-opening-blueprint.md
  docs/architecture/blueprint/reference-architecture-map.md
  docs/architecture/blueprint/module-blueprint-automation-plugin.md
  docs/roadmap/automation-plugin-platform-roadmap.md
  docs/roadmap/capability-opening-roadmap.md
  docs/roadmap/ai-provider-ecosystem-roadmap.md
```

### Can Documentation Cleanup Begin?

**Yes.** The 6 blueprint updates are small text edits (2 hours). The 5 archives are git mv operations (30 minutes). No structural changes needed. Total effort: half a day.
