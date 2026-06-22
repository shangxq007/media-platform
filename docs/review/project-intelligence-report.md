# Project Intelligence Report

**Date:** 2026-06-22  
**Author:** Principal Architect Analysis (code-fact-based)  
**Scope:** Full codebase analysis — 35 Gradle subprojects, 90 package-info files, ~1,800+ production Java files, ~500+ test files, React 19 frontend, K8s deployment manifests, GitOps pipeline

---

# Part 1: Executive Summary

## Project Name

**Media Platform** — AI Video Production & Rendering Orchestration Platform

## One Sentence Summary

A modular monolith platform for AI-assisted video production, multi-engine rendering, asset management, quota billing, and collaborative content delivery — built on Spring Modulith with a React 19 frontend and Temporal-orchestrated workflows.

## Current Maturity

**Beta → Production Candidate (transitional)**

Evidence:
- Core rendering pipeline has 600+ production files and 138 test files with real FFmpeg/GPAC/GStreamer/OFX integration — **production-grade**
- Payment module has real Stripe + Hyperswitch HTTP clients with webhook signature verification — **production-grade**
- Billing module has full subscription lifecycle, usage metering, credit wallets, reconciliation — **production-grade**
- Production safety validator prevents startup with insecure config — **production-hardening signal**
- ModularityTest enforces zero module boundary violations — **architectural discipline signal**
- Flyway V1 baseline (2,339-line schema) is locked — **schema stability signal**
- CI/CD pipeline builds, tests, and promotes via GitOps staging → production — **deployment readiness signal**
- Quota module is in-memory only (no persistence) — **NOT production-ready**
- Scheduler module is in-memory only — **NOT production-ready**
- Cloud-resource module is stub-only — **NOT production-ready**
- `spring-ai-adapter` has a compilation error (`TenantLitellmKeyService` not found) — **broken build signal**
- Version is `0.2.0-SNAPSHOT` — **pre-release signal**

## Development Stage

**Platform Build-out → Feature Expansion (transitional)**

Evidence:
- Foundation is solid: 26 named modules with enforced boundaries, shared kernel, SPI/adapter pattern, tenant isolation
- Core business domains (render, billing, commerce, payment, identity) are fully implemented
- Platform infrastructure (outbox, policy, audit, notification, delivery, entitlement) is operational
- Feature expansion is actively happening: timeline editor, AI integration, client export, incremental render, collaboration
- Not yet in "Scaling" phase: no Redis caching, no message broker, no connection pool tuning, no load testing infrastructure

---

# Part 2: Business & Product Analysis

## Core Product Goal

Enable content creators and media teams to produce video content at scale using AI-assisted editing, multi-engine rendering pipelines, and automated delivery — with enterprise-grade billing, quota management, and compliance.

## Primary Users

| User Type | Confidence | Evidence |
|-----------|-----------|----------|
| **Creator** | High | Timeline editor, subtitle pipeline, effect packs, render presets, Remotion preview in frontend |
| **Platform Operator** | High | Admin controllers, production safety validator, observability dashboard, deployment readiness endpoints |
| **Enterprise** | Medium | Multi-tenancy, workspace management, RBAC, API key auth, billing/subscription, quota profiles (basic/pro/team/enterprise) |
| **Developer** | Medium | MCP endpoints (`/api/v1/mcp/*`), GraphQL API, OpenAPI docs, API key authentication |
| **Agency** | Low Confidence | Project export/import, workspace groups, collaboration — could support agency workflows but no explicit agency features |

## Key Use Cases

| Use Case | Code Evidence |
|----------|--------------|
| **Submit a render job** | `RenderJobSubmissionService`, `RenderJobService`, `RenderController` (447 lines), state machine with 10+ states |
| **AI-assisted timeline editing** | `AiTimelineEditService`, `AiTimelineProposalService`, frontend `src/timeline/intelligence/` with suggestion engine, conflict resolver |
| **Multi-engine rendering** | 7+ render providers: FFmpeg, GPAC, GStreamer, OFX, MLT, Natron, Remotion — with provider selection policy |
| **Incremental render with caching** | `IncrementalRenderPlanService`, `RenderCachePresignService`, content-addressable hashing, S3-backed remote cache |
| **Subtitle pipeline** | SRT, WebVTT, ASS parsing and burn-in via libass; auto-captions service |
| **Payment and checkout** | Checkout flow: catalog → cart → checkout session → Stripe/Hyperswitch → order → fulfillment |
| **Subscription billing** | Plan CRUD, tiered pricing, usage metering, credit wallets, billing cycles, reconciliation |
| **Content delivery** | 6 delivery adapters (S3Mirror, SFTP, SMB, WebDAV, HTTPS PUT), policy-based auto-delivery on render completion |
| **Feature entitlement** | Entitlement grants with expiration, bundles, overrides, quota profiles, workspace-level pools |
| **Audit and compliance** | jOOQ-backed audit records, burst detection, anomaly detection, security alerts |
| **Project export/import** | ZIP packaging with manifest/assets/timeline, import preview, scrubbing, asset remapping |

## Revenue / Monetization Indicators

**High confidence — explicit monetization infrastructure exists:**

| Signal | Evidence |
|--------|----------|
| **Subscription tiers** | `CanonicalProduct` with 4 base subscriptions: basic, pro, team, enterprise |
| **Add-on products** | GPU add-on, AI add-on, credit packs, seat packs |
| **Usage-based billing** | `UsageMeteringService`, `RatingEngine` (flat + tiered pricing), `UsageBillingController` |
| **Credit system** | `CreditWalletService` with credit/debit/reserve/finalize/release lifecycle |
| **Checkout flow** | Full commerce pipeline: `CheckoutOrchestrator` (412 lines), cart, checkout session, payment provider integration |
| **Cost estimation** | `CostEstimationService` with per-provider cost profiles (javacv, ofx, gpac, mlt, gstreamer, remote) |
| **Budget enforcement** | `BudgetGuardService` with soft/hard limits, auto-throttle |
| **Pricing rules** | `PricingRuleService` with custom per-tenant pricing, discount policies |
| **Invoice reconciliation** | `ReconciliationService` for third-party invoice import and matching |

**Business model:** SaaS with tiered subscriptions + usage-based overage + credit packs. Classic PLG (product-led growth) with free → pro → team → enterprise progression.

---

# Part 3: Architecture Analysis

## Architecture Style

**Spring Modulith Modular Monolith with Hexagonal (Ports & Adapters) internal structure**

Evidence:
- `spring-modulith-starter-core:2.0.4` with `@ApplicationModule` on 26 modules
- `shared-kernel` is the only `Type.OPEN` module; all others are `Type.CLOSED`
- 64 `@NamedInterface` declarations controlling cross-module access
- 14 modules declare explicit `allowedDependencies`
- `ModularityTest` enforces zero boundary violations at test time
- Each module follows api/app/domain/infrastructure layering
- 64+ port/adapter implementations for cross-module SPI contracts
- 18 ADRs documenting architectural decisions
- Outbox pattern for cross-module event-driven communication
- Temporal for durable workflow orchestration, LiteFlow for local policy routing

## Module Inventory

### Tier 1: Core Business Modules (Fully Implemented)

| Module | Purpose | Responsibilities | Public Interfaces | Key Dependencies | Risk |
|--------|---------|-----------------|-------------------|-----------------|------|
| **render** | Video rendering engine | Job lifecycle, state machine, 7+ providers, timeline, subtitles, cache, farm | API, app, domain, infrastructure, timeline, 14 NamedInterfaces | ai, billing, quota, storage, workflow, extension, entitlement | **High** — largest module (600 files), most complex |
| **billing** | Subscription & usage billing | Plans, subscriptions, metering, rating, credit wallet, reconciliation | app, domain, infrastructure | shared, payment (via SPI) | **Medium** — comprehensive but stable |
| **commerce** | Product catalog & checkout | Products, cart, checkout sessions, orders, fulfillment | app, domain, infrastructure | payment, billing, entitlement, shared | **Medium** |
| **payment** | Payment processing | Stripe + Hyperswitch HTTP clients, webhooks, idempotency | app, commerce | shared, jOOQ | **Medium** — real payment integration |
| **identity-access** | Multi-tenancy & RBAC | Tenants, workspaces, users, roles, permissions, API keys, project export/import | app, domain, infrastructure | shared, entitlement | **Medium** |
| **workflow** | Workflow orchestration | Temporal workflows, local fallback, signal-based control | temporal | shared, render, delivery, policy | **Medium** |

### Tier 2: Platform Infrastructure (Fully Implemented)

| Module | Purpose | Key Dependencies | Risk |
|--------|---------|-----------------|------|
| **notification** | Multi-channel notifications | Novu, email, SMS, webhook | Low |
| **storage** | S3-compatible blob storage | AWS SDK v2, jOOQ | Low |
| **ai-module** | AI gateway & model routing | Micrometer, Spring config | Low |
| **extension** | Plugin system (PF4J) | Apache Commons Exec | Low |
| **outbox-event** | Outbox pattern | jOOQ, Micrometer | Low |
| **policy-governance** | ABAC & feature flags | OpenFeature, jOOQ | Low |
| **audit-compliance** | Audit trail & compliance | jOOQ, Spring Events | Low |
| **artifact-catalog** | Artifact lifecycle | jOOQ, scheduling | Low |
| **prompt** | Prompt template management | jOOQ, AuditPort | Low |
| **delivery** | Content delivery (6 protocols) | jOOQ, CredentialBundlePort | Low |
| **entitlement** | Feature entitlement & quotas | jOOQ, feature flags | Low |
| **sandbox-runtime** | Sandboxed script execution | javax.script, Commons Exec | Low |
| **secrets-config** | Secrets management | HashiCorp Vault | Low |
| **shared-kernel** | Cross-cutting concerns | Sentry, Jackson | **Critical** — everything depends on it |

### Tier 3: Partial / Stub Modules

| Module | Status | Gap |
|--------|--------|-----|
| **quota-billing** | **Stub** — in-memory only | No persistence, no Flyway tables, single test |
| **scheduler** | **Partial** — in-memory | No real cron engine (Quartz/Temporal integration missing) |
| **cloud-resource** | **Partial** — in-memory | No real cloud SDK integration |
| **observability** | **Partial** | Circuit breaker/SLA tracking exists, but no trace collection infrastructure |
| **social-publish** | **Partial** | Service layer exists, platform adapters likely stubs (1 test) |
| **config** | **Stub** | 34 lines of logic, no tests |
| **datasource** | **Partial** | Multi-datasource config exists, no dynamic routing |
| **federation-query** | **Mixed** | Stub federation core, but full GraphQL + NLQ stack (101 prod files, 43 tests) |

### Tier 4: Cross-Cutting / Integration

| Module | Purpose | Risk |
|--------|---------|------|
| **spring-ai-adapter** | Spring AI OpenAI/LiteLLM integration | **High** — currently broken (compilation error) |
| **remote-render-worker** | Standalone render worker process | Medium |
| **sandbox-worker** | Standalone sandbox worker process | Low |
| **product-layer-module** | Product layer abstraction | Low (empty) |
| **compatibility-migration** | Schema migration compatibility | Low |
| **user-analytics** | User analytics | Low |

## Dependency Graph

```
render
  -> ai (API, domain, video)
  -> billing (app, domain)
  -> quota (app)
  -> storage (API, domain)
  -> workflow
  -> extension (app, domain)
  -> entitlement (domain)
  -> shared

commerce
  -> payment (commerce)
  -> billing (app, domain)
  -> entitlement (app, domain)
  -> shared

billing
  -> shared
  -> payment (via SPI)

workflow
  -> policy (feature-flags)
  -> render (API)
  -> delivery (API)

identity
  -> entitlement (app, domain)
  -> shared

delivery
  -> secrets (API)
  -> storage (domain)
  -> shared

federation
  -> identity (app, domain, infrastructure)
  -> render (API, app, domain, infrastructure, timeline)
  -> extension (app, domain)
  -> billing (app, domain)
  -> entitlement (app, domain)
  -> prompt (app, domain)
  -> ai (API, domain)
  -> policy (feature-flags)

web (platform-app facade)
  -> render, identity, prompt, billing, entitlement, artifact, audit, storage,
     delivery, policy, commerce, app, security, workflow
  (45 allowed dependencies — the widest fan-out)
```

### Boundary Quality Assessment

**Good boundaries:**
- `payment` module: clean SPI via `PaymentProvider` interface, no domain leakage
- `billing` module: clean separation of app/domain/infrastructure, SPI adapters
- `outbox-event` module: self-contained with jOOQ persistence
- `shared-kernel`: OPEN but well-constrained (error codes, value objects, SPI ports only)

**Risky boundaries:**
- `render` module: 600 files, 14 NamedInterfaces, 17 allowed dependencies — **high coupling surface**
- `web` virtual module in platform-app: 45 allowed dependencies — **fan-out anti-pattern risk**
- `federation-query`: 21 allowed dependencies — **wide coupling surface**

**No cyclic dependencies detected** in the declared dependency graph.

## Modulith Health

| Aspect | Status | Evidence |
|--------|--------|----------|
| Module boundary enforcement | **Strong** | ModularityTest with zero-tolerance policy, 2 known allowed violations tracked |
| NamedInterface discipline | **Good** | 64 NamedInterfaces defined, consistent naming (API, app, domain, infrastructure) |
| Cross-module event decoupling | **Good** | Outbox pattern for render→audit, render→notification, application events for internal |
| Domain isolation | **Good** | `quota.domain` kept internal (no NamedInterface), `billing.domain` exposed only to declared dependents |
| Shared kernel discipline | **Good** | Only SPI ports, value objects, error codes, events — no business logic |
| Violation tracking | **Active** | `docs/modulith-debt-register.md` tracks 8 allowed violations with fix deadlines |

---

# Part 4: Technical Capability Matrix

| Capability | Status | Evidence |
|-----------|--------|----------|
| **Authentication (JWT)** | ✅ Implemented | `JwtAuthFilter`, JJWT HMAC-SHA256, fail-fast on insecure defaults |
| **Authentication (OIDC/OAuth2)** | ✅ Implemented | `OAuth2ResourceServerSecurityConfiguration`, Authentik integration, JIT user provisioning |
| **Authorization (RBAC)** | ✅ Implemented | `RoleService`, `PermissionService`, role/permission tables, admin audit |
| **Multi-tenancy** | ✅ Implemented | `TenantContext` (ThreadLocal), `TenantGuard`, JWT-only tenant resolution |
| **Payment (Stripe)** | ✅ Implemented | Real HTTP client, webhook signature verification, idempotency |
| **Payment (Hyperswitch)** | ✅ Implemented | Real HTTP client, webhook signature verification |
| **Subscription billing** | ✅ Implemented | Plans, contracts, lifecycle, billing cycles, ledger |
| **Usage metering** | ✅ Implemented | `UsageMeteringService`, `RatingEngine`, tiered pricing |
| **Credit wallet** | ✅ Implemented | Credit/debit/reserve/finalize/release lifecycle |
| **Quota management** | ⚠️ Partial | Domain model exists, but **in-memory only, no persistence** |
| **Entitlement** | ✅ Implemented | Grants, bundles, overrides, quota profiles, workspace pools |
| **Feature flags** | ✅ Implemented | OpenFeature + Unleash, JDBC persistence, percentage rollout |
| **Render pipeline** | ✅ Implemented | 7+ providers, state machine, incremental render, cache, farm |
| **AI integration** | ✅ Implemented | Gateway, model routing, fallback chains, Spring AI adapter |
| **Asset storage** | ✅ Implemented | S3-compatible with presigned URLs, local fallback |
| **Content delivery** | ✅ Implemented | 6 protocol adapters, policy-based auto-delivery |
| **Notification** | ✅ Implemented | Multi-channel (Novu, email, SMS, webhook), event-driven |
| **Workflow orchestration** | ✅ Implemented | Temporal durable workflows + local fallback |
| **Audit trail** | ✅ Implemented | jOOQ-backed, burst detection, anomaly detection |
| **Plugin system** | ✅ Implemented | PF4J-based with sandboxed execution |
| **Prompt management** | ✅ Implemented | Versioning, risk analysis, secret detection |
| **Secrets management** | ✅ Implemented | HashiCorp Vault + env fallback |
| **Project export/import** | ✅ Implemented | ZIP packaging, scrubbing, asset remapping |
| **GraphQL API** | ✅ Implemented | Full schema, resolvers, DataLoaders, complexity limiting |
| **OpenAPI docs** | ✅ Implemented | springdoc at `/v3/api-docs`, Swagger UI |
| **Rate limiting** | ✅ Implemented | IP-based sliding window |
| **Observability** | ⚠️ Partial | Circuit breaker/SLA tracking, no Grafana dashboards in repo |
| **Structured logging** | ✅ Implemented | JSON logback with MDC propagation |
| **Distributed tracing** | ⚠️ Partial | OTLP configured, trace correlation filter, but no sampling strategy |
| **Caching** | ⚠️ Partial | In-process ConcurrentHashMap only, no Redis/Memcached |
| **Message broker** | ❌ Missing | Outbox pattern uses DB polling, no Kafka/RabbitMQ/SQS |
| **Search** | ❌ Missing | No Elasticsearch/OpenSearch/Meilisearch integration |
| **CDN** | ❌ Missing | No CDN configuration or asset URL rewriting |
| **Internationalization** | ❌ Missing | No i18n framework (frontend has i18n utils but no translation files) |
| **Email service** | ⚠️ Stub | `EmailNotificationProvider` exists but likely stub (no SMTP config found) |
| **Social publishing** | ⚠️ Partial | Service layer exists, platform adapters likely stubs |

---

# Part 5: Roadmap Inference

## What Appears Complete

| Area | Evidence |
|------|----------|
| Render pipeline with 7+ backends | FFmpeg, GPAC, GStreamer, OFX, MLT, Natron, Remotion providers with real command construction |
| Billing & subscription system | Full lifecycle: plans, contracts, usage, rating, credit wallet, reconciliation |
| Commerce checkout flow | Catalog, cart, checkout, payment, order, fulfillment |
| Identity & access management | Multi-tenancy, RBAC, API keys, OIDC JIT provisioning |
| Content delivery | 6 protocol adapters, policy-based automation |
| Entitlement system | Grants, bundles, quotas, workspace pools |
| Feature flag system | OpenFeature + Unleash, JDBC persistence |
| Audit & compliance | Full audit trail with anomaly detection |
| CI/CD pipeline | GitHub Actions → GHCR → GitOps staging → production promotion |
| Frontend timeline editor | Undo/redo, drag-and-drop, intelligence panel, Remotion preview |

## What Appears In Progress

| Area | Evidence |
|------|----------|
| **AI timeline editing** | `AiTimelineEditService`, `AiTimelineProposalService` exist but AI provider is stub by default |
| **Incremental render** | `IncrementalRenderPlanService`, `SegmentTimelinePlanner`, cache reuse — functional but complex |
| **Spring AI integration** | `spring-ai-adapter` exists but has compilation error; LiteLLM tenant key management partially wired |
| **Render farm** | `RenderFarmWorkerRepository`, worker lease management, heartbeat — functional but needs production validation |
| **Client export** | `ClientExportService`, `ClientExportSession` — functional but limited format support |
| **Collaboration** | `SharedResourceService`, `CollaborationController` — basic sharing exists |
| **Social publishing** | Service layer with platform adapters — likely stubs |
| **Frontend** | Timeline editor is advanced; other pages (billing, settings, admin) appear early-stage |

## What Appears Planned But Not Implemented

| Area | Evidence |
|------|----------|
| **Quota persistence** | `QuotaService` is in-memory; `QuotaBucket` domain model exists but no Flyway tables, no JDBC repository |
| **Scheduler engine** | `ScheduleRegistryService` is in-memory; no Quartz/Temporal cron integration |
| **Cloud resource provisioning** | `CloudResourceCatalogService` is in-memory; no AWS/GCP/Azure SDK |
| **External message broker** | Outbox pattern uses DB polling; OpenTofu has queue placeholder (SQS/RabbitMQ/Kafka) |
| **Federation query** | `FederationQueryService` is explicitly a stub; GraphQL/NLQ layer is complete around it |
| **Kill Bill integration** | `NoopKillBillBillingEngine` exists — Kill Bill was considered but not implemented |
| **Medusa.js commerce** | `NoopMedusaCatalogAdapter` exists — Medusa.js was considered but not implemented |
| **Unleash feature flags** | Config exists but disabled by default; OpenFeature InMemoryProvider is the fallback |
| **Natron compositing** | POC Docker compose exists; `NatronRenderProvider` in render-module — research phase |
| **VFX engine (TuttleOFX)** | ADR mentions Tier-3 research; no implementation found |
| **Server-side NLE** | ADR describes 7-layer architecture (L1 FFmpeg → L7 packaging); partially implemented via DAG planner |

## Missing Capabilities

| Capability | Why Needed |
|-----------|-----------|
| **Redis/in-memory cache** | Feature flag hydration, prompt cache, session cache — currently all in-process ConcurrentHashMap |
| **Message broker** | Scale outbox beyond DB polling; enable real event streaming |
| **Search infrastructure** | Asset search, prompt search, audit log search — no full-text search |
| **CDN integration** | Asset delivery at scale; presigned URLs work but no edge caching |
| **Horizontal scaling validation** | K8s manifests exist but no load testing, no connection pool tuning |
| **Monitoring dashboards** | Prometheus metrics exported but no Grafana dashboards in repo |
| **End-to-end testing** | Smoke test scripts exist but no Cypress/Playwright E2E tests |
| **Data migration tooling** | Single V1 baseline; no multi-version migration testing |

---

# Part 6: AI & Automation Readiness

## Multi-Agent Development Readiness Score

**7 / 10**

### Strengths
- **Clean module boundaries**: 26 modules with enforced Modulith boundaries, ModularityTest with zero tolerance
- **Consistent layering**: Every module follows api/app/domain/infrastructure pattern
- **SPI/adapter pattern**: 64+ port interfaces enable independent development of providers
- **Shared kernel discipline**: Only value objects, error codes, SPI ports — no business logic leakage
- **Test infrastructure**: Testcontainers for PostgreSQL, per-module test suites, JaCoCo coverage
- **Well-documented ADRs**: 18 architecture decisions documented, reducing ambiguity

### Weaknesses
- **render-module is too large**: 600 files in a single module — difficult for one agent to own
- **web virtual module couples everything**: 45 allowed dependencies — platform-app is a fan-out bottleneck
- **Some broken builds**: `spring-ai-adapter` compilation error exists
- **Inconsistent test depth**: Some modules have 1 test file (quota, scheduler, cloud-resource), others have 43 (federation)

## Task Parallelization Potential

### Can be developed in parallel (no shared state):
- `payment-module` ↔ `notification-module` ↔ `storage-module`
- `billing-module` ↔ `commerce-module` (via SPI ports)
- `audit-compliance-module` ↔ `policy-governance-module` ↔ `outbox-event-module`
- `prompt-module` ↔ `extension-module` ↔ `sandbox-runtime-module`
- `delivery-module` ↔ `entitlement-module` ↔ `secrets-config-module`
- `identity-access-module` ↔ `artifact-catalog-module`

### Must be sequential (shared state or dependency):
- `shared-kernel` → ALL modules (must be stable first)
- `render-module` changes → `workflow-module` (workflow depends on render API)
- `billing-module` changes → `commerce-module` (commerce depends on billing domain)
- `platform-app` web layer → ALL modules (facade depends on everything)

## Recommended Agent Boundaries

| Agent | Modules | Rationale |
|-------|---------|-----------|
| **Agent A: Render Core** | render-module (providers, state machine, cache, farm) | Largest module, deepest technical complexity |
| **Agent B: Render Timeline** | render-module (timeline, AI editing, incremental, OTIO) | Timeline subsystem is a coherent domain |
| **Agent C: Commerce & Billing** | billing-module, commerce-module, quota-billing-module, payment-module | Revenue stack — tightly coupled business logic |
| **Agent D: Identity & Access** | identity-access-module, platform-app/security | Auth, RBAC, multi-tenancy |
| **Agent E: Platform Infrastructure** | outbox-event, policy-governance, audit-compliance, observability, scheduler, config | Cross-cutting platform services |
| **Agent F: Content Pipeline** | delivery-module, storage-module, artifact-catalog-module, secrets-config-module | Asset lifecycle: store → catalog → deliver |
| **Agent G: AI & Extensions** | ai-module, spring-ai-adapter, extension-module, prompt-module, sandbox-runtime-module | AI integration and extensibility |
| **Agent H: Frontend** | frontend/ | React 19 SPA, isolated from backend |
| **Agent I: Workflow & Orchestration** | workflow-module, remote-render-worker, sandbox-worker | Orchestration and worker processes |
| **Agent J: Platform App** | platform-app (web controllers, lifecycle, production) | Facade layer — must integrate all modules |

## Multi-Agent Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| **shared-kernel changes** | ALL modules affected | Version-locked; changes require ModularityTest pass |
| **Flyway migration conflicts** | Schema corruption | Single V1 baseline; new migrations must be additive-only |
| **platform-app fan-out** | 45 dependency surface area | Minimize changes to web controllers; delegate to module services |
| **render-module ownership** | 600 files, 2 agents needed | Split by subsystem (core vs timeline) with clear package boundaries |
| **Build breakage** | Blocked CI | ModularityTest + compilation checks before merge |
| **Configuration conflicts** | 17 application profiles | Each agent should only touch their module's config |

---

# Part 7: Backlog Proposal

## Next 30 Days

### P0 — Critical

| Task | Module | Rationale |
|------|--------|-----------|
| Fix `spring-ai-adapter` compilation error | spring-ai-adapter | Broken build blocks CI for any change touching AI module |
| Persist quota data (add Flyway tables + jOOQ repository) | quota-billing-module | In-memory quota is the last major stub blocking production |
| Production safety validation for quota persistence | quota-billing-module | Ensure ProductionSafetyValidator checks quota tables exist |
| Add QuotaService integration tests | quota-billing-module | Currently 1 test file — insufficient for production |

### P1 — Important

| Task | Module | Rationale |
|------|--------|-----------|
| Add Redis cache layer | New infra module | Feature flags, prompts, entitlements all use in-process cache |
| Scheduler engine integration | scheduler-module | Replace in-memory with Quartz or Temporal cron |
| Render-module test coverage for billing integration | render-module | BillingDecisionEngine and BillingEnforcementService have 0 tests |
| Frontend billing/settings pages | frontend | Commerce backend is complete but frontend pages appear early-stage |

### P2 — Nice to Have

| Task | Module | Rationale |
|------|--------|-----------|
| Cloud-resource real SDK integration | cloud-resource-module | Replace stub with real S3/GCS provisioning |
| Social-publish platform adapters | social-publish-module | Service layer exists but adapters are stubs |
| Observability dashboards | infra | Prometheus metrics exported but no Grafana dashboards |

## Next 90 Days

| Phase | Focus | Modules |
|-------|-------|---------|
| **Weeks 1-4** | Production readiness for quota + scheduler | quota-billing, scheduler, production-safety |
| **Weeks 5-8** | Caching + message broker infrastructure | New Redis module, outbox-event upgrade, feature flag caching |
| **Weeks 9-12** | Frontend feature parity | Billing UI, admin dashboard, collaboration UI |
| **Ongoing** | Render pipeline hardening | Incremental render validation, farm scaling, provider health |

## Technical Debt

| Debt | Impact | Urgency |
|------|--------|---------|
| `spring-ai-adapter` compilation error | Blocks AI module CI | **Critical** |
| `quota-billing-module` in-memory only | Cannot go to production | **Critical** |
| `render-module` at 600 files | Unmaintainable, blocks parallel dev | **High** |
| `web` virtual module with 45 deps | Fan-out anti-pattern | **High** |
| No Redis caching | All caches are in-process, don't survive restart | **High** |
| No E2E tests | Smoke tests only, no browser automation | **Medium** |
| 17 application profiles | Configuration complexity explosion | **Medium** |
| ADR-009 says Vue 3, code says React 19 | Documentation drift | **Low** |
| `config-module` has 0 tests | Untested code in production path | **Low** |

---

# Part 8: Decision Support

## What Should NOT Be Worked On Now

| Area | Reason |
|------|--------|
| **Microservice extraction** | Modular monolith is the right architecture at current scale; extraction adds operational complexity without benefit |
| **Full AI runtime integration** | Spring AI adapter exists but AI provider should remain stub until quota/billing is production-ready |
| **Multi-region deployment** | Single-region with GitOps staging → production is sufficient; multi-region adds complexity |
| **Custom render engine development** | FFmpeg/GPAC/GStreamer cover current needs; Natron/TuttleOFX are research-phase |
| **GraphQL federation** | Federation query service is a stub; focus on REST API stability first |
| **Over-engineering the scheduler** | Simple cron jobs via Temporal or Quartz are sufficient; don't build a custom scheduler |
| **Adding more payment providers** | Stripe + Hyperswitch is sufficient; focus on billing completeness |

## What Should Be Prioritized Now

| Priority | Action | Why |
|----------|--------|-----|
| **1** | Fix `spring-ai-adapter` build | Broken CI is a hard blocker |
| **2** | Persist quota data | Last major stub blocking production |
| **3** | Add tests for render billing integration | 0 tests for BillingDecisionEngine/BillingEnforcementService |
| **4** | Redis cache layer | All caches are in-process; production restart loses state |
| **5** | Frontend billing pages | Backend commerce is complete but UI is missing |

---

# Part 9: Confidence Assessment

## High Confidence (Observed Fact)

| Conclusion | Evidence |
|-----------|----------|
| Project is a modular monolith | `settings.gradle.kts` declares 35 subprojects, `spring-modulith-starter-core:2.0.4` in dependencies |
| 26 named Spring Modulith modules exist | 90 `package-info.java` files with `@ApplicationModule` annotations |
| ModularityTest enforces zero boundary violations | `ModularityTest.java` with `ALLOWED_VIOLATIONS` list and `assertTrue(unexpectedViolations.isEmpty())` |
| Render module has 7+ render providers | FFmpeg, GPAC, GStreamer, OFX, MLT, Natron, Remotion — each with dedicated implementation classes |
| Payment module has real Stripe integration | `StripeHttpPaymentProvider.java` with `HttpClient` calls to `https://api.stripe.com/v1/checkout/sessions` |
| Billing module has full subscription lifecycle | `SubscriptionBillingService.java` (223 lines) with plan CRUD, contract lifecycle, billing cycles |
| Quota module is in-memory only | `QuotaService.java` uses `ConcurrentHashMap` — no repository, no Flyway tables |
| Frontend uses React 19 + Remotion | `package.json` shows `react: ^19.0.0`, `remotion: ^4.0.0` |
| CI/CD uses GitHub Actions → GitOps | `.github/workflows/ci.yml` builds, pushes to GHCR, creates staging PR |
| 17 application profiles exist | `application-*.yml` files: dev, dev-postgres, local-preview, preview, prod, safe-mode, oidc, temporal, r2, ai, litellm, vault, hyperswitch, natron-worker, test, integration-test |

## Medium Confidence (Inference)

| Conclusion | Basis |
|-----------|-------|
| Platform is pre-revenue or early-revenue | Commerce/checkout flow is complete but catalog is hardcoded; no Stripe webhook delivery logs found |
| Frontend is early-stage for most pages | Timeline editor is advanced; billing/admin/settings pages appear to have minimal implementation |
| ~1,800+ production Java files | File count from module analysis; not precisely verified |
| ~500+ test files | File count from module analysis; not precisely verified |
| Render farm needs production validation | Worker lease management and heartbeat exist but no load testing evidence |
| Social-publish adapters are stubs | Only 1 test file for 29 production files; platform adapter implementations not verified |

## Low Confidence (Assumption)

| Conclusion | Basis |
|-----------|-------|
| No production deployment has occurred | Version is `0.2.0-SNAPSHOT`, production safety validator exists but no deployment evidence |
| Team size is 1-5 developers | Single GitOps repo, no CODEOWNERS file, commit patterns suggest small team |
| Project started ~6-12 months ago | 22 Flyway migrations, extensive codebase, but `0.2.0` version suggests early lifecycle |
| LiteFlow is minimally integrated | Only `SubtitleBurnInNode` found as LiteFlow component; may be research-phase |

---

# Part 10: Executive Recommendation

## If I Were the Project Lead, the Next Two Weeks Should Focus On:

### Week 1: Unblock and Stabilize

| Day | Action | Owner | Deliverable |
|-----|--------|-------|-------------|
| Day 1-2 | Fix `spring-ai-adapter` compilation error | AI Agent | Green CI build for `./gradlew test` |
| Day 1-2 | Add Flyway migration V23 for quota tables (quota_bucket, usage_record, quota_policy, threshold_event) | Commerce Agent | Schema for quota persistence |
| Day 3-4 | Implement `QuotaJdbcRepository` and wire into `QuotaService` | Commerce Agent | Quota data survives restart |
| Day 3-4 | Add `ProductionSafetyValidator` check for quota tables | Platform Agent | Production gate for quota |
| Day 5 | Add integration tests for quota persistence | Commerce Agent | `QuotaServiceTest` with Testcontainers |
| Day 5 | Add tests for `BillingDecisionEngine` and `BillingEnforcementService` | Render Agent | 0 → 4+ test files for render billing |

### Week 2: Infrastructure and Frontend

| Day | Action | Owner | Deliverable |
|-----|--------|-------|-------------|
| Day 6-7 | Add Redis cache module (feature flags, prompts, entitlements) | Platform Agent | New `redis-cache-module` or config in existing modules |
| Day 6-7 | Wire feature flag caching to Redis | Policy Agent | Feature flags survive restart |
| Day 8-9 | Build frontend billing dashboard page | Frontend Agent | `/billing` page showing subscription, usage, credits |
| Day 8-9 | Build frontend admin quota view | Frontend Agent | `/admin/quota` page showing tenant quotas |
| Day 10 | Run full E2E smoke test (submit render → complete → deliver) | QA / All | End-to-end validation |
| Day 10 | Update `project-intelligence-report.md` with post-fix status | Architect | Updated confidence levels |

### Specific Actions for Multi-Agent Development

1. **Do NOT let agents modify `shared-kernel` without approval** — it's the highest-risk module
2. **Assign render-module to TWO agents** — one for providers/state-machine, one for timeline/AI — with clear package ownership
3. **Keep platform-app changes minimal** — the web facade has 45 dependencies and is a merge conflict hotspot
4. **Run `./gradlew :platform-app:test --tests '*ModularityTest*'` after every agent change** — this is the architectural safety net
5. **New Flyway migrations must be additive-only** — V1 baseline is locked; V23+ for new tables only

---

# Appendix: Key Metrics

| Metric | Value |
|--------|-------|
| Gradle subprojects | 35 |
| Spring Modulith modules | 26 (+ 5 virtual in platform-app) |
| NamedInterface declarations | 64 |
| Production Java files (estimated) | ~1,800+ |
| Test Java files (estimated) | ~500+ |
| Flyway migrations | 22 (V1-V22) |
| Application profiles | 17 |
| REST controllers | 40+ |
| GraphQL resolvers | 6 |
| Render providers | 7+ |
| Delivery adapters | 6 |
| Payment providers | 2 (Stripe, Hyperswitch) |
| SPI/adapter implementations | 64+ |
| ADRs documented | 18 |
| Dockerfiles | 8 |
| Docker compose files | 6 |
| K8s manifest files | 15 |
| CI/CD workflows | 1 (with 5 jobs) |
| Scripts | 12 |
| Frontend dependencies (runtime) | 20+ |
| Frontend modules | ~15 (timeline, editor, observability, auth, api, components, etc.) |
