# Documentation Structure Inventory

**Date:** 2026-06-22  
**Scope:** All documentation files (.md, .yaml, .yml, scripts) in the repository  
**Method:** Full filesystem scan + git-tracked file list + content spot-checks

---

## 1. Executive Summary

### Current Documentation Structure

The repository contains **641 Markdown files** (excluding `.git/`, `.gradle/`, `node_modules/`, `build/`). The primary documentation entry point is `docs/` (595 files), with supporting docs at the repo root (`README.md`, `AGENTS.md`, `DEPLOY.md`) and in `prompts/` (6 files).

### Main Documentation Entry Points

| Entry | Purpose | Status |
|-------|---------|--------|
| `README.md` (root) | Project quick-start, module overview, test commands | **Partially stale** — says "Vue 3 前端" but code uses React 19 |
| `docs/README.md` | Documentation structure guide with metadata conventions | **Current** — accurately describes docs/ layout |
| `AGENTS.md` | AI agent development rules and priorities | **Current** — matches code structure |
| `docs/index.md` | Needs validation — may be a documentation hub | Unknown purpose without reading |
| `docs/zh/platform-guide/README.md` | Chinese platform guide index | **Current** — comprehensive 11-chapter guide |

### Maintenance Status

- **~50% of docs appear actively maintained**: `docs/review/`, `docs/operations/`, `docs/architecture/current/`, `docs/render/`
- **~30% are historical/archived**: `docs/archive/` (224 files) — explicitly marked as "not current truth"
- **~20% have unknown maintenance status**: root-level `docs/*.md` files that duplicate archive content

### Single Trusted Entry

**No single canonical entry exists.** The project has `AGENTS.md` for AI agents, `README.md` for humans, `docs/README.md` for documentation navigation, and `docs/overview/01-project-overview.md` for a detailed overview. These overlap without a clear hierarchy.

---

## 2. Actual Documentation Tree

### Root Level

```text
README.md                              — Project quick-start (partially stale: says "Vue 3")
AGENTS.md                              — AI agent config: priorities, rules, module boundaries
DEPLOY.md                              — Deployment guide
.kilo/agents/main.md                   — Detailed AI agent instructions (module boundaries, P0-P6 priorities)
prompts/
  MANIFEST.md                          — Prompt session manifest
  13-functional-implementation-round.md — Historical prompt session
  14-hardening-persistence-tenancy-outbox.md
  15-render-pipeline-runtime-ffmpeg-mlt-gpac.md
  16-commerce-payment-billing-entitlement-persistence.md
  16-critical-stub-implementation.md
deployment-prep/
  environment-resource-requirements.md — Resource requirements doc
test-assets/golden-render-project-v1/
  README.md                            — Golden render project test data docs
```

### docs/ — Top Level

```text
docs/
  README.md                            — Documentation structure guide with metadata conventions
  README-ZH.md                         — Chinese documentation index
  index.md                             — Needs validation
  architecture-decisions.md            — 6 ADRs (ADR-001 through ADR-006)
  architecture-notes.md                — Architecture notes
  modulith-debt-register.md            — Modulith violation tracking (8 allowed violations)
  module-boundaries.md                 — Module boundary audit (372 lines)
  production-safety.md                 — Production startup safety checks
  production-blockers.md               — Production blockers list
  production-readiness.md              — Production readiness assessment
  reading-guide.md                     — Documentation reading guide
  final-project-status.md              — Project status snapshot
  technical-assessment.md              — Technical assessment
  skeleton-gap-priorities.md           — Gap analysis
  documentation-gap-analysis.md        — Doc gap analysis
  documentation-gap-report.md          — Doc gap report
  project-naming-audit.md              — Naming conventions audit
  project-architecture-operations-guide.md — Architecture ops guide
  code-derived-system-overview.md      — System overview from code analysis
  conditional-on-bean-removal-audit.md — Bean removal audit
  environments.md                      — Environment configuration
  kilo-execution-summary.md            — Kilo agent execution summary
  roo-execution-log.md                 — Historical execution log
  roo-final-report.md                  — Historical final report
  roo-gap-report.md                    — Historical gap report
```

### docs/ — Architecture

```text
docs/architecture/
  README.md                            — Architecture section index
  01-system-architecture.md            — System architecture
  02-backend-architecture.md           — Backend architecture
  03-module-architecture.md            — Module architecture
  04-frontend-architecture.md          — Frontend architecture
  05-request-flows.md                  — Request flow documentation
  06-data-architecture.md              — Data architecture
  07-architecture-decisions.md         — 12 ADRs (ADR-001 through ADR-012)
  08-deployment-architecture.md        — Deployment architecture
  backend-first-stabilization-plan.md  — Stabilization plan
  core-editing-rendering-architecture.md — Editing/rendering architecture
  p4-import-export-architecture.md     — Import/export architecture
  platform-architecture-assessment.md  — Platform assessment
  platform-fact-gathering-report.md    — Fact gathering report
  render-farm-readiness-and-worker-lease-design.md — Render farm design
  blueprint/                           — Target architecture (10 files)
    system-blueprint.md
    platform-composition-blueprint.md
    reference-architecture-map.md
    capability-opening-blueprint.md
    module-blueprint-render.md
    module-blueprint-ai-provider.md
    module-blueprint-artifact-storage.md
    module-blueprint-automation-plugin.md
    module-blueprint-observability.md
    module-blueprint-security-identity.md
  current/                             — Current system state (4 files)
    current-system-state.md
    current-module-status.md
    current-startup-profiles.md
    current-known-gaps.md
```

### docs/ — Render & Media

```text
docs/media-rendering/                  — 21 files
  01-render-pipeline.md through 13-internal-timeline-schema-v1.md
  effect-taxonomy.md, effect-taxonomy-v1.md, effect-taxonomy-implementation-guide.md
  golden-render-project.md, project-export.md
  render-provider-capability-matrix.md
  subtitle-burn-in-productization.md, subtitle-font-pipeline-readiness.md
  subtitle-rendering-strategy-adr.md
  spatial-coordinate-system.md, video-crop-implementation.md
  timeline-effect-api-productization.md
  examples/                            — 2 JSON sample files
  project-export-v1-example/           — 2 JSON example files

docs/render/                           — 30+ files
  overview.md, capability-matrix.md
  render-job-schema.md, render-planner-v1.md
  provider-roadmap.md, provider-deprecation.md, provider-types.md, routing-rules.md
  remotion-provider.md, remotion-provider-poc.md
  mlt-provider.md, otio-as-core-timeline.md
  liteflow-integration.md, liteflow-poc-chains.md
  bmf-research.md
  font-pipeline.md, font-security.md, font-validation.md, font-subsetting.md
  font-manifest-schema.md, font-ci-acceptance.md, font-qa-roadmap.md
  font-metadata-and-coverage.md, font-ots-sanitizer.md
  frontend-minimum-demo.md, testing-plan.md
  render-platform-foundation-summary.md
  adr/                                 — 7 render-specific ADRs (ADR-001 through ADR-007)

docs/render-ffmpeg.md                  — FFmpeg provider docs
docs/render-gpac-packaging.md          — GPAC packaging docs
docs/render-mlt.md                     — MLT provider docs
docs/render-pipeline-implementation.md — Pipeline implementation
docs/render-pipeline-implementation-zh.md — Chinese version
docs/render-provider-integration.md    — Provider integration guide
docs/render-provider-routing.md        — Provider routing
docs/render-provider-extension-roadmap.md — Provider extension roadmap
docs/renderprovider-javaCV.md          — JavaCV provider
docs/renderprovider-ofx.md             — OFX provider
docs/render-state-machine.md           — State machine docs
docs/render-worker-architecture.md     — Worker architecture
docs/render-worker-deployment.md       — Worker deployment
```

### docs/ — Business Modules

```text
docs/billing-access/                   — 13 files (entitlement through collaboration)
docs/commerce-payment-billing-entitlement.md — Commerce overview
docs/subscription-billing.md           — Subscription billing
docs/credit-wallet.md                  — Credit wallet
docs/custom-pricing.md                 — Custom pricing
docs/entitlement-policy.md             — Entitlement policy
docs/quota-policy.md                   — Quota policy
docs/event-flow-monetization.md        — Monetization event flow
docs/flexible-billing-models.md        — Billing models
docs/external-billing-integrations.md  — External billing
docs/workspace-entitlement-pool.md     — Workspace entitlement
```

### docs/ — API

```text
docs/api/                              — 9 files
  01-api-strategy.md                   — API strategy
  02-openapi.md                        — OpenAPI docs
  03-graphql.md                        — GraphQL docs
  api-productization-roadmap.md        — API productization
  public-api-capability-matrix.md      — API capability matrix
  public-api-job-model.md              — Job model API
  public-api-security.md               — API security
  public-capability-api.md             — Capability API
  subtitle-render-api-mvp.md           — Subtitle render API
docs/api-versioning.md                 — API versioning
```

### docs/ — Frontend

```text
docs/frontend/                         — 20+ files
  overview.md, react-architecture.md, editor-state.md
  01-editor-workbench.md through 09-ui-design-system.md
  app-shell-and-navigation.md, user-dashboard.md
  asset-backend-api.md, caption-template-system.md
  font-asset-management.md, notification-settings.md
  remotion-integration.md, renderjob-contract.md
  render-job-dashboard.md, react-smoke-editor.md
  timeline-model.md, admin-console.md
docs/frontend-effects-panel.md         — Effects panel
docs/frontend-entitlement-management.md — Entitlement UI
docs/frontend-i18n-error.md            — i18n error
docs/frontend-operation-manual.md      — Frontend manual
docs/frontend-test-known-issues.md     — Known test issues
docs/frontend-ui-review-report.md      — UI review
```

### docs/ — Operations & Deployment

```text
docs/operations/                       — 5 runbooks
  flyway-baseline-runbook.md
  gitops-staging-deploy-runbook.md
  manual-review-runbook.md
  postgres-preview-staging-runbook.md
  security-preview-safe-mode-runbook.md

docs/deployment/                       — 5 files
  01-deployment.md, 02-deployment-checklist.md
  03-demo-script.md, 04-rollback.md
  external-resources.md

docs/runbook-e2e-render-flow.md        — E2E render flow runbook
docs/runbook-five-capabilities.md      — Five capabilities runbook
docs/runbook-local.md                  — Local dev runbook
docs/runbook-local-docker.md           — Docker dev runbook
docs/gitops.md                         — GitOps docs
docs/k8s-deployment.md                 — K8s deployment
docs/deployment-resource-requirements.md — Resource requirements
docs/environments.md                   — Environment config
docs/egress-smoke-rollout.md           — Egress proxy smoke
docs/docker-external-config.md         — Docker external config
```

### docs/ — Security & Observability

```text
docs/SECURITY_HEADERS.md               — Security headers
docs/security-and-tenancy.md           — Security & tenancy
docs/security-alerts.md                — Security alerts
docs/sandbox-security.md               — Sandbox security
docs/rbac-abac-access-control.md       — RBAC/ABAC
docs/observability.md                  — Observability overview
docs/observability/                    — 8 files (observability, monitoring, notifications)
docs/outbox-reliability.md             — Outbox reliability
```

### docs/ — Review Reports

```text
docs/review/                           — 18 files
  01-production-blockers.md            — Production blockers
  02-technical-debt.md                 — Technical debt
  03-review-checklists.md              — Review checklists
  04-documentation-audit-report.md     — Documentation audit
  05-architecture-evaluation.md        — Architecture evaluation
  06-full-module-audit.md              — Full module audit
  autonomous-prompt-completion-matrix.md — Prompt completion matrix
  comprehensive-issue-report-2026-06-16.md — Comprehensive issue report
  issue-001-jwt-secret-hardening.md    — JWT hardening (2026-06-22)
  issue-002-stripe-verify-payment.md   — Stripe verification (2026-06-22)
  issue-003a-modularity-test-investigation.md — Modularity investigation
  issue-003b-modularity-test-reenable.md — Modularity re-enable
  known-limitations.md                 — Known limitations
  manual-preview-smoke-report-2026-06-17.md — Smoke test report
  production-blockers.md               — Production blockers (duplicate name)
  project-intelligence-report.md       — Project intelligence report (2026-06-22)
  prompt-module-gap-report.md          — Prompt module gaps
  release-candidate-readiness-2026-06-17.md — RC readiness
  staging-readiness-final-audit-2026-06-19.md — Staging audit
```

### docs/ — Roadmap

```text
docs/roadmap/                          — 5 files
  ai-provider-ecosystem-roadmap.md
  automation-plugin-platform-roadmap.md
  capability-opening-roadmap.md
  render-pipeline-roadmap.md
  technical-debt-roadmap.md
```

### docs/ — System Audit

```text
docs/system-audit/                     — 2 files
  CRITICAL_GAPS_ACTION_PLAN.md
  platform-architecture-audit-2026-06-13.md
```

### docs/ — Engineering

```text
docs/engineering/                      — 7 files
  code-coverage-tool-selection.md
  cross-dissolve-implementation.md
  deployment-preparation.md
  media-platform-complete-tech-doc.md
  p4-import-export-pipeline-tech-doc.md
  required-staging-inputs.md
  schema-management-policy.md
```

### docs/ — Archive (224 files)

```text
docs/archive/                          — 224 .md files
  README.md                            — Archive notice ("not current truth")
  docs-cn/                             — Chinese translations (historical, 50+ files)
  *.md                                 — Deprecated/archived documents
```

### docs/ — Chinese Documentation (38 files)

```text
docs/zh/                               — 38 files
  README.md                            — Chinese docs index
  platform-guide/                      — 11-chapter comprehensive guide
    README.md, 01-architecture.md through 11-doc-index.md
  platform-guide.md                    — Guide overview
  architecture.md, deployment.md, development-guidelines.md
  ai-gateway-architecture.md, ai-timeline-editing.md
  asset-lifecycle-governance.md
  authentik-oidc-resource-server.md, authentik-property-mapping-and-migration.md
  commerce-catalog-and-fulfillment.md
  delivery-subsystem.md, dynamic-extension.md
  graceful-shutdown-and-data-consistency.md
  incremental-rendering.md, module-reference.md
  monitoring-feedback.md, problematic-data.md
  production-acceptance-checklist.md, prompt-platform.md
  secrets-management.md, temporal-production-namespace.md
  timeline-version-control.md, usage-guide.md
  vault-and-rustfs-setup.md, faq.md
```

### docs/ — Other Topics

```text
docs/advanced-effects-pipeline.md      — Advanced effects
docs/ai-engine-spi.md                  — AI engine SPI
docs/api-versioning.md                 — API versioning
docs/asdf-vm.md                        — asdf version manager
docs/client-export-browser.md          — Client export
docs/configurable-navigation.md        — Configurable navigation
docs/database-schema.md                — Database schema
docs/ddl-postgresql.sql                — DDL SQL file
docs/effect-pack-schema.md             — Effect pack schema
docs/error-handling-design.md          — Error handling
docs/extension-module-boundary.md      — Extension boundaries
docs/extension-plugins.md              — Plugin system
docs/font-embedding.md                 — Font embedding
docs/future-roadmap-otio-llm.md        — OTIO/LLM roadmap
docs/gpac-provider.md                  — GPAC provider
docs/gpu-rendering.md                  — GPU rendering
docs/gstreamer-provider.md             — GStreamer provider
docs/human-review-needed.md            — Human review items
docs/infrastructure-as-code.md         — IaC docs
docs/javacv-migration-guide.md         — JavaCV migration
docs/jooq-workflow.md                  — jOOQ workflow
docs/layering-and-open-source.md       — Layering strategy
docs/liteflow-temporal-architecture.md — LiteFlow/Temporal
docs/media-processing-module.md        — Media processing
docs/mlt-provider.md                   — MLT provider
docs/multi-language-subtitle.md        — Multi-language subtitles
docs/multi-provider-orchestration.md   — Multi-provider
docs/natural-language-query-assistant.md — NLQ
docs/nix.md                            — Nix dev environment
docs/notification-integrations.md      — Notification integrations
docs/notification-template-management.md — Notification templates
docs/ofx-provider.md                   — OFX provider
docs/persistence-restart-semantics.md  — Persistence semantics
docs/prompt-63-verification-report.md  — Prompt verification
docs/spring-boot-4-upgrade-notes.md    — Spring Boot 4 upgrade
docs/subtitle-font-upload.md           — Subtitle font upload
docs/temporal-integration-plan.md      — Temporal integration
docs/timeline-model.md                 — Timeline model
docs/user-analytics-api.md             — User analytics API
docs/user-profile-and-habits.md        — User profiles
docs/video-processing-tools.md         — Video processing tools
```

### Non-docs Documentation

```text
infra/README.md                        — Infrastructure overview
gitops/production/README.md            — Production GitOps notes
gitops/staging/README.md               — Staging GitOps notes
render-module/src/main/java/.../docs/
  media-probe-service.md               — In-source media probe docs
  subtitle-burn-in-service.md          — In-source subtitle burn-in docs
.github/workflows/ci.yml              — CI/CD workflow definition
```

### Infrastructure & Deployment Files

```text
.github/workflows/ci.yml              — GitHub Actions CI (single workflow, 5 jobs)
docker-compose.yml                     — Production-like (db + app)
docker-compose.dev.yml                 — Full dev stack (db + app + render-worker + sandbox-worker)
docker-compose.authentik.yml           — Authentik OIDC stack
docker-compose.local-postgres.yml      — Standalone PostgreSQL
Dockerfile                             — Multi-stage (Node → Gradle → JRE)
remote-render-worker/Dockerfile        — Render worker
sandbox-worker/Dockerfile              — Sandbox worker
frontend/Dockerfile                    — Frontend dev server
docker/                                — 3 specialized Dockerfiles (javacv, natron, ofx)
infra/docker/                          — 4 Dockerfiles + compose
infra/natron/docker-compose.poc.yml    — Natron POC
infra/opentofu/                        — OpenTofu IaC (placeholder modules)
k8s/                                   — K8s base + overlays (staging/production)
gitops/                                — ArgoCD app definitions + staging/production manifests
scripts/                               — 12 operational scripts
```

---

## 3. Documentation Categories

### Project Overview
- `README.md` (root)
- `docs/README.md`
- `docs/overview/01-project-overview.md`
- `docs/overview/02-project-status.md`
- `docs/code-derived-system-overview.md`
- `docs/reading-guide.md`
- `docs/final-project-status.md`
- `docs/zh/platform-guide/` (11 chapters)

### Architecture
- `docs/architecture/01-system-architecture.md` through `08-deployment-architecture.md`
- `docs/architecture/blueprint/` (10 target architecture docs)
- `docs/architecture/current/` (4 current state docs)
- `docs/architecture/core-editing-rendering-architecture.md`
- `docs/architecture/render-farm-readiness-and-worker-lease-design.md`
- `docs/module-boundaries.md`
- `docs/layering-and-open-source.md`
- `docs/liteflow-temporal-architecture.md`

### ADR / Decisions
- `docs/architecture-decisions.md` (6 ADRs)
- `docs/architecture/07-architecture-decisions.md` (12 ADRs)
- `docs/render/adr/` (7 render-specific ADRs)
- `docs/media-rendering/subtitle-rendering-strategy-adr.md`

### Module Facts
- `docs/modules/01-core-modules.md` through `04-platform-modules.md`
- `docs/billing-access/` (13 files)
- `docs/media-rendering/` (21 files)
- `docs/render/` (30+ files)
- `docs/frontend/` (20+ files)
- `docs/api/` (9 files)
- `docs/extensions/` (2 files)
- `docs/observability/` (8 files)
- `docs/prompt-ai/` (3 files)

### Review Reports
- `docs/review/` (18 files)
- `docs/system-audit/` (2 files)

### Operational Runbooks
- `docs/operations/` (5 runbooks)
- `docs/deployment/` (5 files)
- `docs/runbook-e2e-render-flow.md`
- `docs/runbook-local.md`, `docs/runbook-local-docker.md`
- `scripts/` (12 scripts)

### Deployment / CI
- `.github/workflows/ci.yml`
- `docs/gitops.md`
- `docs/k8s-deployment.md`
- `docs/deployment-resource-requirements.md`
- `docs/environments.md`
- `docs/egress-smoke-rollout.md`
- `docs/docker-external-config.md`
- `DEPLOY.md`
- `gitops/` (ArgoCD + staging/production manifests)
- `k8s/` (base + overlays)
- `infra/` (Docker, OpenTofu)

### Agent Workflow
- `AGENTS.md`
- `.kilo/agents/main.md`
- `prompts/` (6 files)
- `docs/kilo-execution-summary.md`
- `docs/review/autonomous-prompt-completion-matrix.md`

### Security / Production Safety
- `docs/production-safety.md`
- `docs/production-blockers.md`
- `docs/production-readiness.md`
- `docs/SECURITY_HEADERS.md`
- `docs/security-and-tenancy.md`
- `docs/security-alerts.md`
- `docs/sandbox-security.md`
- `docs/rbac-abac-access-control.md`

### API / Integration
- `docs/api/` (9 files)
- `docs/api-versioning.md`
- `docs/ai-engine-spi.md`
- `docs/notification-integrations.md`
- `docs/external-billing-integrations.md`

### Frontend Docs
- `docs/frontend/` (20+ files)
- `docs/frontend-effects-panel.md`
- `docs/frontend-entitlement-management.md`
- `docs/frontend-operation-manual.md`

### Roadmap
- `docs/roadmap/` (5 files)
- `docs/future-roadmap-otio-llm.md`
- `docs/technical-roadmap-video-platform.md`

### Unknown / Stale
- `docs/human-review-needed.md` — Needs validation
- `docs/skeleton-gap-priorities.md` — May be outdated
- `docs/documentation-gap-analysis.md` — May overlap with `documentation-gap-report.md`
- `docs/prompt-63-verification-report.md` — Specific prompt verification, likely historical
- `docs/user-profile-and-habits.md` — Unknown relevance
- `docs/project-naming-audit.md` — One-time audit
- `docs/conditional-on-bean-removal-audit.md` — One-time audit
- `docs/examples/` — Code examples, unknown if current

---

## 4. Source of Truth Assessment

### Canonical (Use as primary reference)

| File | Reason |
|------|--------|
| `AGENTS.md` | Actively maintained, matches code structure, used by AI agents |
| `.kilo/agents/main.md` | Detailed agent config, matches module boundaries |
| `docs/README.md` | Accurately describes documentation structure |
| `docs/modulith-debt-register.md` | Tracks active violations, updated with issue-003b |
| `docs/production-safety.md` | Matches `ProductionSafetyValidator` code |
| `docs/operations/flyway-baseline-runbook.md` | Operational truth |
| `docs/architecture/current/current-system-state.md` | Describes actual implementation |
| `docs/architecture/current/current-module-status.md` | Module status |
| `docs/review/issue-003b-modularity-test-reenable.md` | Most recent P0 fix report |

### Current but Partial

| File | Reason |
|------|--------|
| `docs/module-boundaries.md` | Generated 2026-05-08, render allowedDependencies has since changed (now includes billing, quota, extension) |
| `docs/architecture/07-architecture-decisions.md` | 12 ADRs but ADR-009 says "Vue 3 + Pinia" — code uses React 19 + Zustand |
| `docs/overview/02-project-status.md` | Status snapshot — needs date verification |
| `docs/review/06-full-module-audit.md` | Comprehensive but point-in-time |

### Historical

| File | Reason |
|------|--------|
| `docs/archive/*` (224 files) | Explicitly marked "not current truth" in `docs/archive/README.md` |
| `docs/roo-execution-log.md` | Historical agent execution log |
| `docs/roo-final-report.md` | Historical agent report |
| `docs/roo-gap-report.md` | Historical gap report |
| `prompts/*.md` | Historical prompt sessions |
| `docs/releases/*.md` (15 files) | Point-in-time release snapshots |

### Possibly Stale

| File | Reason |
|------|--------|
| `docs/render-provider-routing.md` | May not reflect current provider selection policy |
| `docs/render-worker-architecture.md` | May not reflect current farm implementation |
| `docs/database-schema.md` | May not match current Flyway V1 (2,339 lines) |
| `docs/temporal-integration-plan.md` | Temporal is now integrated; plan may be outdated |
| `docs/spring-boot-4-upgrade-notes.md` | Upgrade appears complete |
| `docs/infrastructure-as-code.md` | OpenTofu is placeholder-only |
| `docs/gpu-rendering.md` | Needs validation against current providers |

### Contradicts Code

| File | Reason |
|------|--------|
| `README.md` (root, line 3) | Says "Vue 3 前端" — code uses React 19 |
| `docs/architecture/07-architecture-decisions.md` ADR-009 | Says "Vue 3 + Vite + Pinia + Apollo Client" — code uses React 19 + Zustand + TanStack Query |
| `docs/module-boundaries.md` line 18 | Shows render allowedDependencies without billing/quota/extension — code now includes them |
| `docs/modulith-debt-register.md` | Lists 8 allowed violations; ModularityTest now only has 2 in its ALLOWED_VIOLATIONS list |

### Unknown

| File | Reason |
|------|--------|
| `docs/index.md` | Purpose unclear without reading |
| `docs/human-review-needed.md` | May be active or historical |
| `docs/code-derived-system-overview.md` | Generated from code — unknown freshness |
| Most `docs/zh/*.md` files | Chinese translations — unknown if kept in sync |

---

## 5. Documentation Drift Findings

### Finding 1

```
File: README.md (root, line 3)
Claim: "Spring Modulith 模块化单体 + Vue 3 前端"
Code / config reality: frontend/package.json uses react ^19.0.0, @tanstack/react-query, zustand
Severity: High
Suggested action: Change "Vue 3 前端" to "React 19 前端"
```

### Finding 2

```
File: docs/architecture/07-architecture-decisions.md (ADR-009)
Claim: "Vue 3 + Vite for Frontend (Pinia, Apollo Client)"
Code / config reality: React 19 + Zustand + TanStack Query + axios (no Vue, no Pinia, no Apollo)
Severity: High
Suggested action: Update ADR-009 to reflect React 19 stack, or mark as superseded
```

### Finding 3

```
File: docs/module-boundaries.md (line 18)
Claim: render-module allowedDependencies = {"ai", "ai :: API", "ai :: domain", "shared", "storage", "storage :: API", "storage :: domain"}
Code / config reality: render/package-info.java now also includes "billing :: app", "billing :: domain", "entitlement", "entitlement :: domain", "quota :: app", "workflow", "extension", "extension :: app", "extension :: domain"
Severity: Medium
Suggested action: Regenerate module-boundaries.md from current package-info files
```

### Finding 4

```
File: docs/modulith-debt-register.md
Claim: 8 allowed violations (all identity → artifact/storage)
Code / config reality: ModularityTest.java ALLOWED_VIOLATIONS list has only 2 entries: "identity' depends on named interface(s) 'artifact" and "identity' depends on named interface(s) 'storage"
Severity: Medium
Suggested action: Align debt register with ModularityTest — either reduce register to 2 or expand test allowlist to 8
```

### Finding 5

```
File: README.md (line 18)
Claim: "31 个 Gradle 子模块"
Code / config reality: settings.gradle.kts declares 35 subprojects
Severity: Low
Suggested action: Update to "35"
```

### Finding 6

```
File: README.md (lines 26-34)
Claim: V1 through V22 Flyway scripts, describes V1-V5 and V20-V22
Code / config reality: Single V1__init_full_schema.sql (2,339 lines) — no V2-V22 scripts exist in current migration directory
Severity: Critical
Suggested action: Verify Flyway migration state; if V2-V22 were squashed into V1, update README to reflect single baseline
```

### Finding 7

```
File: docs/render/adr/ADR-007-deprecate-ofx-javacv-natron.md
Claim: OFX, JavaCV, and Natron are deprecated
Code / config reality: FFmpegRenderProvider, OFXRenderProvider, NatronRenderProvider all exist in render-module; Natron POC Docker compose exists
Severity: Low
Suggested action: Verify if deprecation is enforced or advisory; update code if deprecated providers should be removed
```

### Finding 8

```
File: docs/infrastructure-as-code.md
Claim: Infrastructure as Code documentation
Code / config reality: OpenTofu modules under infra/opentofu/ are all placeholder (null_resource) — no real infrastructure provisioning
Severity: Low
Suggested action: Mark document as "planned" or "scaffolding-only"
```

---

## 6. Missing Documentation

| Missing Document | Importance | Rationale |
|-----------------|-----------|-----------|
| **Single canonical architecture entry** | High | Multiple overlapping architecture docs; no clear "start here" for new developers or agents |
| **CI verification checklist** | High | `.github/workflows/ci.yml` exists but no doc explaining what CI checks, how to reproduce locally, or what blocks merge |
| **Flyway migration rules** | High | V1 baseline is locked but no doc explains how to add V23+ migrations, naming conventions, or rollback strategy |
| **Module ownership map** | Medium | No CODEOWNERS file; 35 modules with no documented owner assignment |
| **Production deployment runbook** | Medium | `docs/deployment/` exists but no end-to-end "deploy to production" runbook matching the GitOps flow |
| **API changelog** | Medium | OpenAPI is generated at runtime; no changelog tracking API breaking changes |
| **Frontend development guide** | Medium | `docs/frontend/` has feature docs but no "how to add a page" or "component conventions" guide |
| **Quota module status** | Low | Quota is in-memory stub; no doc explains the gap or migration plan to persistence |

---

## 7. Recommended Documentation Structure

### Recommended Canonical Docs

| File | Role |
|------|------|
| `AGENTS.md` | Primary entry for AI agents (keep current) |
| `README.md` | Primary entry for human developers (fix drift) |
| `docs/README.md` | Documentation navigation hub (keep current) |
| `docs/architecture/current/current-system-state.md` | Current architecture truth |
| `docs/modulith-debt-register.md` | Module boundary debt tracking |
| `docs/production-safety.md` | Production safety guardrails |
| `docs/operations/` | All operational runbooks |
| `docs/review/` | Point-in-time review reports |

### Recommended Archive/Historical Docs

| Action | Files |
|--------|-------|
| Keep in `docs/archive/` | All 224 files (already archived) |
| Move to `docs/archive/` | `docs/roo-execution-log.md`, `docs/roo-final-report.md`, `docs/roo-gap-report.md` |
| Move to `docs/archive/` | `docs/skeleton-gap-priorities.md`, `docs/human-review-needed.md` |
| Move to `docs/archive/` | `docs/spring-boot-4-upgrade-notes.md` (upgrade complete) |

### Recommended Docs to Update

| File | Update Needed |
|------|--------------|
| `README.md` | Fix "Vue 3" → "React 19"; update module count 31 → 35; fix Flyway description |
| `docs/architecture/07-architecture-decisions.md` | Fix ADR-009 Vue 3 → React 19 |
| `docs/module-boundaries.md` | Regenerate from current package-info files |
| `docs/modulith-debt-register.md` | Align with ModularityTest ALLOWED_VIOLATIONS (8 → 2 or vice versa) |
| `docs/infrastructure-as-code.md` | Mark as scaffolding-only |

### Recommended Docs to Create

| File | Purpose |
|------|---------|
| `docs/flyway-migration-guide.md` | Rules for adding migrations after V1 baseline |
| `docs/ci-verification-guide.md` | What CI checks, how to reproduce locally |

---

## 8. Maintenance Rules

### What Changes Require Which Doc Updates

| Change Type | Must Update |
|-------------|------------|
| New module added | `AGENTS.md`, `docs/module-boundaries.md`, `README.md` module count |
| NamedInterface changed | `docs/module-boundaries.md`, `ModularityTest` if boundary changes |
| Flyway migration added | `README.md` Flyway table, `docs/database-schema.md` |
| New render provider | `docs/render/provider-roadmap.md`, `docs/media-rendering/render-provider-capability-matrix.md` |
| Security config changed | `docs/production-safety.md`, `docs/SECURITY_HEADERS.md` |
| New application profile | `docs/architecture/current/current-startup-profiles.md` |
| P0 hardening completed | `docs/review/issue-NNN-*.md` (new file) |

### P0 Hardening Report Archival

- Create as `docs/review/issue-NNN-<short-description>.md`
- Include: date, background, changes, test results, remaining violations
- These are point-in-time snapshots; do not edit after completion

### ADR Addition Process

- Platform-level: add to `docs/architecture/07-architecture-decisions.md`
- Render-specific: add to `docs/render/adr/ADR-NNN-*.md`
- Use format: Status, Date, Context, Decision, Consequences

### docs/review Usage

- Point-in-time snapshots only
- Never edit a completed report; create a new one if state changes
- Naming: `issue-NNN-<short-description>.md` or `YYYY-MM-DD-<topic>.md`

### Multi-Agent Doc Drift Prevention

- Agents must not modify docs outside their assigned module scope
- `AGENTS.md` and `.kilo/agents/main.md` are the only agent-facing canonical docs
- Agents should create `docs/review/` reports, not edit architecture docs
- Run `ModularityTest` after any package-info change

### CI Doc Checks (Recommended)

- Verify `AGENTS.md` exists
- Verify `docs/production-safety.md` exists
- Verify `docs/modulith-debt-register.md` exists
- Verify `README.md` module count matches `settings.gradle.kts` subproject count

---

## Command Output Summary

### `find . -maxdepth 4 (*.md, *.yml, *.yaml, *.txt, *.adoc)` 

Found ~500 files at maxdepth 4. Key directories: `docs/` (primary), `k8s/`, `gitops/`, `infra/`, `scripts/`, `prompts/`, `.github/`, `docker/`.

### `find docs -maxdepth 5 -type f`

Found **595 .md files** in `docs/`, plus `.sql`, `.json`, `.ts`, `.yaml` files.

### `find adr ADRs docs/adr docs/adrs`

**Path not found.** No standalone ADR directories. ADRs are embedded in:
- `docs/architecture-decisions.md` (6 ADRs)
- `docs/architecture/07-architecture-decisions.md` (12 ADRs)
- `docs/render/adr/` (7 ADRs)

### `find .github scripts deployment deploy infra k8s helm docker`

- `.github/workflows/ci.yml` — single CI workflow
- `scripts/` — 12 operational scripts
- `infra/` — Docker, OpenTofu, scripts
- `k8s/` — base + overlays
- `docker/` — 3 Dockerfiles
- `deployment/`, `deploy/`, `helm/` — **Path not found**

### `git ls-files | grep -E '(^README|\.md$|\.ya?ml$)'`

~400 tracked documentation/config files.

### File Counts

| Category | Count |
|----------|-------|
| Total .md (excl deps) | 641 |
| docs/ .md files | 595 |
| docs/archive/ .md files | 224 |
| docs/zh/ .md files | 38 |
| Non-docs .md files | 46 |
| .yml/.yaml config files | ~80 |
| Scripts | 12 |
| Dockerfiles | 8+ |
| K8s manifests | ~40 |
