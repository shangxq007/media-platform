# Documentation Governance Assessment

**Date:** 2026-06-22  
**Scope:** Full documentation governance analysis — drift, duplication, agent readiness, simplification  
**Baseline:** Code-fact verification against `settings.gradle.kts`, `build.gradle.kts`, `frontend/package.json`, `ModularityTest.java`, `QuotaService.java`, Flyway migration directory

---

## Part 1: Documentation Governance Executive Summary

### Is the documentation system healthy?

**No.** The documentation system has significant structural problems that will actively harm multi-agent development if not addressed.

### Are there multiple sources of truth?

**Yes.** At least 5 documents claim to describe the project overview (`README.md`, `docs/overview/01-project-overview.md`, `docs/code-derived-system-overview.md`, `docs/review/05-architecture-evaluation.md`, `docs/review/06-full-module-audit.md`). They disagree on fundamental facts.

### Is there severe drift?

**Yes.** Three critical drift categories:

1. **Frontend framework:** 5+ documents say "Vue 3" — code uses React 19
2. **Module count:** Values range from 30 to 34 across documents — actual is 35
3. **Flyway state:** Values range from "17 migrations" to "V1-V3" — actual is 1 file (V1__init_full_schema.sql)

### Has documentation impacted development efficiency?

**Yes, measurably.** The `ModularityTest` was disabled because module boundary documentation was stale. The `docs/module-boundaries.md` still shows pre-fix render allowedDependencies. An agent reading it would make incorrect dependency decisions.

### Documentation Health Score

**4 / 10**

| Dimension | Score | Reason |
|-----------|-------|--------|
| Accuracy | 3/10 | Critical drift in 5+ docs (Vue→React, module count, Flyway) |
| Completeness | 6/10 | Most modules and features are documented somewhere |
| Organization | 5/10 | Directory structure exists but has unclear hierarchy |
| Freshness | 3/10 | Most docs are 4-6 weeks stale; only review/issue-* are current |
| Discoverability | 4/10 | No clear entry point; multiple overlapping navigation docs |
| Agent readiness | 3/10 | An agent reading all docs would be actively misinformed |

---

## Part 2: Source of Truth Hierarchy

### Project Overview

```
Canonical:
  AGENTS.md
  Reason: Actively maintained, matches code, used by all AI agents

Supporting:
  docs/README.md
  Reason: Accurately describes documentation structure (not project facts)

Historical:
  docs/overview/01-project-overview.md
  Reason: Says "Vue 3" and "30 modules" — 6 weeks stale

Do Not Trust:
  docs/review/05-architecture-evaluation.md
  Reason: Says "Vue 3.5, Pinia, GraphQL" — completely wrong frontend stack
  docs/review/06-full-module-audit.md
  Reason: Says "Vue 3" and "31 modules" — double drift
  docs/code-derived-system-overview.md
  Reason: Says "34 modules" and "V1-V3" — stale
```

### Architecture

```
Canonical:
  docs/architecture/current/current-system-state.md
  Reason: Only doc that correctly says "1 Flyway version (V1 consolidated baseline)"

Supporting:
  docs/architecture/01-system-architecture.md through 08-deployment-architecture.md
  Reason: Comprehensive architecture series, but ADR-009 drifts on frontend
  docs/architecture/04-frontend-architecture.md
  Reason: Only architecture doc that correctly says "React 19"

Historical:
  docs/architecture/blueprint/*
  Reason: Target architecture, not current — by design

Do Not Trust:
  docs/architecture/07-architecture-decisions.md (ADR-009 only)
  Reason: Says "Vue 3 + Pinia + Apollo Client" — code uses React 19 + Zustand + TanStack Query
```

### Current Architecture

```
Canonical:
  docs/architecture/current/current-system-state.md
  docs/architecture/current/current-module-status.md (correct except module count)

Supporting:
  docs/architecture/current/current-startup-profiles.md
  docs/architecture/current/current-known-gaps.md

Historical:
  (none — these are designed to be current)

Do Not Trust:
  docs/module-boundaries.md
  Reason: Shows stale render allowedDependencies (missing billing, quota, extension, workflow)
```

### Module Status

```
Canonical:
  AGENTS.md (module priority list)
  .kilo/agents/main.md (detailed module boundaries)

Supporting:
  docs/modules/01-core-modules.md through 04-platform-modules.md
  docs/review/project-intelligence-report.md (most recent comprehensive analysis)

Historical:
  docs/review/06-full-module-audit.md
  Reason: 6 weeks stale, wrong module count and frontend framework

Do Not Trust:
  docs/architecture/current/current-module-status.md line 18
  Reason: Says "30 modules" — actual is 35
```

### Module Boundaries

```
Canonical:
  .kilo/agents/main.md
  Reason: Contains boundary definitions matching current code

Supporting:
  docs/modulith-debt-register.md
  Reason: Tracks violations (but count may be stale — see drift section)
  ModularityTest.java ALLOWED_VIOLATIONS list
  Reason: The actual enforcement — ground truth for what violations are permitted

Historical:
  docs/module-boundaries.md
  Reason: Generated 2026-05-08, render allowedDependencies has since changed

Do Not Trust:
  docs/module-boundaries.md for render-module
  Reason: Missing 7 dependencies added after generation
```

### Deployment

```
Canonical:
  DEPLOY.md
  docs/deployment/01-deployment.md

Supporting:
  docs/operations/gitops-staging-deploy-runbook.md
  docs/gitops.md
  docs/k8s-deployment.md
  .github/workflows/ci.yml

Historical:
  docs/archive/production-deployment-checklist.md

Do Not Trust:
  (none — deployment docs appear accurate)
```

### Operations

```
Canonical:
  docs/operations/flyway-baseline-runbook.md
  docs/operations/gitops-staging-deploy-runbook.md
  docs/operations/security-preview-safe-mode-runbook.md

Supporting:
  docs/operations/manual-review-runbook.md
  docs/operations/postgres-preview-staging-runbook.md
  docs/runbook-local.md
  docs/runbook-local-docker.md

Historical:
  docs/archive/runbook-local.md
  docs/archive/runbook-local-docker.md

Do Not Trust:
  (none — operations docs appear accurate)
```

### CI/CD

```
Canonical:
  .github/workflows/ci.yml
  Reason: The actual CI definition — ground truth

Supporting:
  docs/gitops.md
  docs/deployment/02-deployment-checklist.md

Historical:
  (none)

Do Not Trust:
  (none — but no dedicated CI documentation exists explaining the workflow)
```

### Security

```
Canonical:
  docs/production-safety.md
  Reason: Matches ProductionSafetyValidator.java code
  docs/SECURITY_HEADERS.md

Supporting:
  docs/security-and-tenancy.md
  docs/security-alerts.md
  docs/sandbox-security.md
  docs/rbac-abac-access-control.md

Historical:
  docs/archive/security-policy.md

Do Not Trust:
  (none — security docs appear accurate)
```

### Identity

```
Canonical:
  AGENTS.md (identity module description)
  docs/billing-access/access-control-overview.md

Supporting:
  docs/rbac-abac-access-control.md
  docs/billing-access/01-entitlement.md through 12-collaboration-apis.md

Historical:
  docs/archive/rbac-abac-access-control.md

Do Not Trust:
  (none)
```

### Payment

```
Canonical:
  docs/review/issue-002-stripe-verify-payment.md
  Reason: Most recent, code-verified

Supporting:
  docs/commerce-payment-billing-entitlement.md
  docs/external-billing-integrations.md

Historical:
  (none)

Do Not Trust:
  (none)
```

### Quota

```
Canonical:
  docs/review/project-intelligence-report.md (quota section)
  Reason: Correctly identifies quota as "in-memory only, no persistence"

Supporting:
  docs/quota-policy.md
  docs/review/known-limitations.md

Historical:
  docs/archive/quota-policy.md

Do Not Trust:
  (none — but no dedicated doc explains the in-memory gap)
```

### Render

```
Canonical:
  docs/render/overview.md
  docs/render/adr/ADR-001 through ADR-007
  docs/media-rendering/render-provider-capability-matrix.md

Supporting:
  docs/render-pipeline-implementation.md
  docs/render-state-machine.md
  docs/render/capability-matrix.md
  docs/render/provider-roadmap.md

Historical:
  docs/archive/render-pipeline-implementation.md
  docs/archive/render-state-machine.md

Do Not Trust:
  docs/render/adr/ADR-007 (deprecate-ofx-javacv-natron)
  Reason: Deprecation decision exists but deprecated providers still have full implementations in code
```

### Frontend

```
Canonical:
  docs/architecture/04-frontend-architecture.md
  Reason: Only architecture doc that correctly says "React 19"

Supporting:
  docs/frontend/overview.md
  docs/frontend/react-architecture.md
  docs/frontend/01-editor-workbench.md through 09-ui-design-system.md

Historical:
  docs/archive/frontend-*.md (7 files)
  Reason: Written during Vue era

Do Not Trust:
  docs/overview/01-project-overview.md (frontend section)
  docs/review/05-architecture-evaluation.md (frontend section)
  docs/review/06-full-module-audit.md (frontend section)
  Reason: All say "Vue 3" — code is React 19
```

### Roadmap

```
Canonical:
  docs/roadmap/technical-debt-roadmap.md
  docs/roadmap/render-pipeline-roadmap.md

Supporting:
  docs/roadmap/ai-provider-ecosystem-roadmap.md
  docs/roadmap/automation-plugin-platform-roadmap.md
  docs/roadmap/capability-opening-roadmap.md
  docs/review/project-intelligence-report.md (roadmap section)

Historical:
  docs/technical-roadmap-video-platform.md

Do Not Trust:
  (none — roadmaps are forward-looking by nature)
```

### Agent Workflow

```
Canonical:
  AGENTS.md
  .kilo/agents/main.md

Supporting:
  .kilo/command/*.md (if exists)

Historical:
  docs/kilo-execution-summary.md
  docs/roo-execution-log.md
  docs/roo-final-report.md

Do Not Trust:
  prompts/*.md
  Reason: Historical prompt sessions from previous orchestrators — may confuse current agent context
```

---

## Part 3: Documentation Duplication Analysis

### Cluster 1: Project Overview (5 documents)

| Document | Role | Status |
|----------|------|--------|
| `README.md` | Quick-start | **Primary** — but has drift |
| `docs/overview/01-project-overview.md` | Detailed overview | **Secondary** — has drift |
| `docs/code-derived-system-overview.md` | Code-derived overview | **Redundant** — has drift, superseded by project-intelligence-report |
| `docs/review/05-architecture-evaluation.md` | Architecture evaluation | **Redundant** — has drift |
| `docs/review/06-full-module-audit.md` | Module audit | **Redundant** — has drift |

**Recommendation:** Keep `README.md` as primary (fix drift). Archive `code-derived-system-overview.md`. The review reports are point-in-time by design.

### Cluster 2: Gap Reports (4 documents)

| Document | Date | Status |
|----------|------|--------|
| `docs/roo-gap-report.md` | 2026-05-08 | **Redundant** — oldest, many gaps resolved |
| `docs/documentation-gap-report.md` | 2026-05-12 | **Redundant** — superseded |
| `docs/documentation-gap-analysis.md` | 2026-05-28 | **Redundant** — superseded |
| `docs/system-audit/platform-architecture-audit-2026-06-13.md` | 2026-06-13 | **Primary** — most recent |

**Recommendation:** Archive the first three. Keep the June 13 audit as canonical.

### Cluster 3: Execution Logs (3 documents)

| Document | Format | Status |
|----------|--------|--------|
| `docs/roo-execution-log.md` | Narrative | **Redundant** — historical |
| `prompts/MANIFEST.md` | Table | **Redundant** — historical |
| `docs/kilo-execution-summary.md` | Single-phase | **Redundant** — historical |

**Recommendation:** Archive all three. No active value.

### Cluster 4: Architecture Decisions (3 locations)

| Document | ADR Count | Status |
|----------|-----------|--------|
| `docs/architecture-decisions.md` | 6 ADRs | **Redundant** — subset of 07-architecture-decisions.md |
| `docs/architecture/07-architecture-decisions.md` | 12 ADRs | **Primary** — most comprehensive |
| `docs/render/adr/` | 7 ADRs | **Primary** — domain-specific, complementary |

**Recommendation:** Archive `docs/architecture-decisions.md` (duplicate of 07). Keep `docs/architecture/07-architecture-decisions.md` and `docs/render/adr/`.

### Cluster 5: Module Boundaries (3 documents)

| Document | Date | Status |
|----------|------|--------|
| `docs/module-boundaries.md` | 2026-05-08 | **Stale** — render deps wrong |
| `docs/modulith-debt-register.md` | 2026-06-07 | **Primary** — active tracking |
| `.kilo/agents/main.md` | Active | **Primary** — agent config |

**Recommendation:** Update `module-boundaries.md` or archive it. Keep `modulith-debt-register.md` and `.kilo/agents/main.md`.

### Cluster 6: Security (4 documents with overlap)

| Document | Focus | Status |
|----------|-------|--------|
| `docs/production-safety.md` | Production startup checks | **Primary** |
| `docs/security-and-tenancy.md` | Security architecture | **Supporting** |
| `docs/SECURITY_HEADERS.md` | HTTP headers | **Supporting** |
| `docs/archive/security-policy.md` | General policy | **Archived** |

**Recommendation:** No changes needed — these are complementary, not redundant.

---

## Part 4: Documentation Drift Analysis

### Critical Drift (Actively Misleading)

| # | File | Claim | Reality | Impact |
|---|------|-------|---------|--------|
| 1 | `README.md` line 3 | "Vue 3 前端" | React 19 | Every new developer/agent reads this first |
| 2 | `docs/overview/01-project-overview.md` line 25 | "Frontend: Vue 3 + Vite" | React 19 + Vite | Detailed overview lies about framework |
| 3 | `docs/architecture/07-architecture-decisions.md` ADR-009 | "Vue 3 + Pinia + Apollo Client" | React 19 + Zustand + TanStack Query | ADR contradicts code — permanent record of wrong decision |
| 4 | `docs/review/05-architecture-evaluation.md` line 47 | "Vue 3.5, Vite 6, Pinia, GraphQL" | React 19, Vite 6, Zustand, TanStack Query | Review report with wrong stack |
| 5 | `docs/review/06-full-module-audit.md` line 5 | "31 个 Gradle 模块 + Vue 3 前端" | 35 modules + React 19 | Double drift |

### High Drift (Incorrect Facts)

| # | File | Claim | Reality | Impact |
|---|------|-------|---------|--------|
| 6 | `README.md` line 18 | "31 个 Gradle 子模块" | 35 | Module count wrong |
| 7 | `README.md` lines 26-34 | V1-V22 Flyway scripts described | Single V1__init_full_schema.sql | Flyway state completely wrong |
| 8 | `docs/overview/02-project-status.md` line 45 | "Total Gradle Modules: 30" | 35 | Module count wrong |
| 9 | `docs/overview/02-project-status.md` line 52 | "Flyway Migrations: 17" | 1 | Migration count wrong |
| 10 | `docs/module-boundaries.md` line 18 | render allowedDependencies (7 deps) | 17 deps (added billing, quota, extension, workflow) | Module boundary doc stale |
| 11 | `docs/modulith-debt-register.md` | 8 allowed violations | ModularityTest has 2 in ALLOWED_VIOLATIONS | Debt register and test disagree |

### Medium Drift (Outdated but Not Actively Misleading)

| # | File | Claim | Reality | Impact |
|---|------|-------|---------|--------|
| 12 | `docs/code-derived-system-overview.md` line 65 | "34 个 Gradle 模块" | 35 | Off by one |
| 13 | `docs/code-derived-system-overview.md` line 70 | "3 个逻辑版本 (V1-V3)" | 1 file (V1) | Flyway wrong |
| 14 | `docs/architecture/current/current-module-status.md` line 18 | "depends on all 30" | 35 | Module count wrong |
| 15 | `docs/reading-guide.md` line 4 | "适用版本: Media Platform v5" | 0.2.0-SNAPSHOT | Version wrong |
| 16 | `docs/render/adr/ADR-007` | "Deprecate OFX, JavaCV, Natron" | Providers still fully implemented | Deprecation not enforced |

### Low Drift (Minor or Cosmetic)

| # | File | Issue | Impact |
|---|------|-------|--------|
| 17 | `docs/infrastructure-as-code.md` | Describes IaC that is placeholder-only | Low — scaffolding |
| 18 | `docs/spring-boot-4-upgrade-notes.md` | Also exists in archive/ | Low — duplication |
| 19 | `docs/review/autonomous-prompt-completion-matrix.md` | References Vue3 codebase | Low — historical |

---

## Part 5: Historical Value Assessment

### Should Be Preserved (Active Value)

| File | Reason |
|------|--------|
| `docs/releases/rc-2026-06-06.md` | Release record |
| `docs/releases/staging-readiness-gate-2026-06-08.md` | Readiness gate |
| `docs/skeleton-gap-priorities.md` | Active planning |
| `docs/project-naming-audit.md` | Unresolved tech debt |
| `docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md` | Active blockers |
| `docs/system-audit/platform-architecture-audit-2026-06-13.md` | Latest audit |
| `docs/review/known-limitations.md` | Active reference |
| `docs/review/issue-001-*` through `issue-003b-*` | Recent P0 fixes |
| `docs/review/project-intelligence-report.md` | Latest comprehensive analysis |
| `DEPLOY.md` | Active ops doc |
| `docs/spring-boot-4-upgrade-notes.md` | Active dependency reference |

### Should Be Archived (Historical Value Only)

| File | Reason |
|------|--------|
| `docs/roo-execution-log.md` | Superseded by MANIFEST.md |
| `docs/roo-final-report.md` | Superseded by later reviews |
| `docs/roo-gap-report.md` | Superseded by June 13 audit |
| `prompts/MANIFEST.md` | Historical orchestration record |
| `prompts/13-*.md` through `16-*.md` | Historical prompt sessions |
| `docs/kilo-execution-summary.md` | Single-phase summary |
| `docs/human-review-needed.md` | Phase 20 only, 6+ weeks stale |
| `docs/documentation-gap-analysis.md` | Superseded by June 13 audit |
| `docs/documentation-gap-report.md` | Superseded by June 13 audit |
| `docs/review/autonomous-prompt-completion-matrix.md` | Based on Vue3 codebase |
| `docs/final-project-status.md` | Point-in-time snapshot |
| `docs/review/comprehensive-issue-report-2026-06-16.md` | Point-in-time report |

### Should Be Deleted (No Value)

No documents should be permanently deleted. All have some provenance value. Archive is sufficient.

---

## Part 6: Agent Readiness Assessment

### If an Agent Reads All Documentation, What Happens?

**It will be actively misinformed about:**
1. The frontend framework (Vue 3 vs React 19)
2. The number of modules (30 vs 35)
3. The Flyway migration state (17 vs 1)
4. The render module's dependencies (7 vs 17)
5. The quota module's persistence state (unclear)

**It will waste context window on:**
- 224 archived documents
- 15 release reports
- 6 historical prompt sessions
- 3 redundant gap reports
- 3 redundant execution logs

### High Risk Documents (Will Mislead Agent)

| Document | Risk | Why |
|----------|------|-----|
| `README.md` | Critical | First document any agent reads — says Vue 3 |
| `docs/overview/01-project-overview.md` | Critical | Detailed overview — says Vue 3, 30 modules |
| `docs/architecture/07-architecture-decisions.md` (ADR-009) | Critical | Permanent record says Vue 3 |
| `docs/module-boundaries.md` | High | Stale render dependencies |
| `docs/review/05-architecture-evaluation.md` | High | Says Vue 3.5, 31 modules |
| `docs/review/06-full-module-audit.md` | High | Says Vue 3, 31 modules |
| `docs/overview/02-project-status.md` | High | Says 30 modules, 17 Flyway |
| `docs/modulith-debt-register.md` | Medium | 8 violations vs 2 in test |
| `prompts/*.md` | Medium | Historical context may confuse current agent |
| `docs/archive/*.md` (224 files) | Medium | Agent may mistake archived docs for current |

### Medium Risk Documents (Outdated but Not Actively Harmful)

| Document | Risk |
|----------|------|
| `docs/code-derived-system-overview.md` | Says 34 modules, V1-V3 |
| `docs/reading-guide.md` | Says version 5 |
| `docs/architecture/current/current-module-status.md` | Says 30 modules |
| `docs/review/autonomous-prompt-completion-matrix.md` | References Vue3 |

### Safe Documents (Can Be Trusted)

| Document | Why Safe |
|----------|----------|
| `AGENTS.md` | Actively maintained, matches code |
| `.kilo/agents/main.md` | Agent config, matches code |
| `docs/architecture/current/current-system-state.md` | Only accurate Flyway doc |
| `docs/architecture/04-frontend-architecture.md` | Only correct React 19 doc |
| `docs/production-safety.md` | Matches ProductionSafetyValidator |
| `docs/modulith-debt-register.md` | Mostly accurate (minor count issue) |
| `docs/operations/*.md` | Operational runbooks, current |
| `docs/review/issue-001-*` through `issue-003b-*` | Recent, code-verified |
| `docs/review/project-intelligence-report.md` | Most recent comprehensive analysis |
| `docs/render/adr/*.md` | Domain-specific ADRs, current |
| `docs/frontend/react-architecture.md` | Correctly references React |

---

## Part 7: Recommended Canonical Knowledge Base

### Tier 0: Always Load (Agent Boot)

| # | Document | Why |
|---|----------|-----|
| 1 | `AGENTS.md` | Primary agent config — priorities, rules, module list |
| 2 | `.kilo/agents/main.md` | Detailed module boundaries and development rules |

**Rationale:** These two files are the only documents that are actively maintained and match the codebase. Every agent must read them first.

### Tier 1: Load by Context

| # | Document | Context |
|---|----------|---------|
| 3 | `docs/architecture/current/current-system-state.md` | Architecture context |
| 4 | `docs/architecture/current/current-module-status.md` | Module status (fix module count first) |
| 5 | `docs/modulith-debt-register.md` | Module boundary context |
| 6 | `docs/production-safety.md` | Production safety context |
| 7 | `docs/review/project-intelligence-report.md` | Comprehensive project analysis |
| 8 | `docs/review/known-limitations.md` | What's not production-ready |
| 9 | `docs/architecture/04-frontend-architecture.md` | Frontend context |
| 10 | `docs/render/overview.md` | Render module context |
| 11 | `docs/billing-access/access-control-overview.md` | Billing/access context |

**Rationale:** These are the most accurate and current documents. Load them when the agent needs specific domain context.

### Tier 2: Load On Demand

| # | Document | When |
|---|----------|------|
| 12 | `docs/operations/flyway-baseline-runbook.md` | DB migration tasks |
| 13 | `docs/operations/gitops-staging-deploy-runbook.md` | Deployment tasks |
| 14 | `docs/render/adr/ADR-001` through `ADR-007` | Render provider decisions |
| 15 | `docs/architecture/07-architecture-decisions.md` | Architecture decisions (fix ADR-009 first) |
| 16 | `docs/api/01-api-strategy.md` | API development |
| 17 | `docs/frontend/react-architecture.md` | Frontend development |
| 18 | `docs/spring-boot-4-upgrade-notes.md` | Dependency management |
| 19 | `docs/review/issue-001-*` through `issue-003b-*` | Recent P0 fixes |
| 20 | `DEPLOY.md` | Docker deployment |

**Rationale:** Useful reference material, but not needed for every task.

### Tier 3: Never Load Automatically

| Category | Files | Why |
|----------|-------|-----|
| Archive | `docs/archive/*` (224 files) | Explicitly "not current truth" |
| Historical reviews | `docs/review/01-06-*.md` | Point-in-time snapshots |
| Historical prompts | `prompts/*.md` | Previous orchestrator sessions |
| Historical reports | `docs/roo-*.md`, `docs/kilo-execution-summary.md` | Superseded |
| Historical gap reports | `docs/documentation-gap-*.md`, `docs/roo-gap-report.md` | Superseded |
| Release reports | `docs/releases/*.md` | Point-in-time |
| Stale overviews | `docs/overview/01-project-overview.md`, `docs/code-derived-system-overview.md` | Drift |

---

## Part 8: Documentation Simplification Plan

### Keep (Active, Current, Needed)

```
AGENTS.md
.kilo/agents/main.md
README.md                                    (fix drift first)
DEPLOY.md
docs/README.md
docs/architecture/current/current-system-state.md
docs/architecture/current/current-module-status.md      (fix module count)
docs/architecture/current/current-startup-profiles.md
docs/architecture/current/current-known-gaps.md
docs/architecture/01-system-architecture.md through 08-deployment-architecture.md  (fix ADR-009)
docs/architecture/README.md
docs/modulith-debt-register.md                          (align with test)
docs/production-safety.md
docs/production-blockers.md
docs/production-readiness.md
docs/module-boundaries.md                               (update or archive)
docs/spring-boot-4-upgrade-notes.md
docs/operations/*.md (5 files)
docs/deployment/*.md (5 files)
docs/render/overview.md
docs/render/adr/*.md (7 files)
docs/render/capability-matrix.md
docs/render/provider-roadmap.md
docs/render/render-job-schema.md
docs/render/font-*.md (9 files)
docs/media-rendering/render-provider-capability-matrix.md
docs/billing-access/*.md (13 files)
docs/frontend/overview.md
docs/frontend/react-architecture.md
docs/frontend/01-09-*.md (10 files)
docs/api/*.md (9 files)
docs/extensions/*.md (2 files)
docs/observability/*.md (8 files)
docs/prompt-ai/*.md (3 files)
docs/roadmap/*.md (5 files)
docs/review/issue-001-*.md through issue-003b-*.md (4 files)
docs/review/project-intelligence-report.md
docs/review/known-limitations.md
docs/review/documentation-structure-inventory.md
docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md
docs/system-audit/platform-architecture-audit-2026-06-13.md
docs/skeleton-gap-priorities.md
docs/project-naming-audit.md
docs/security-and-tenancy.md
docs/SECURITY_HEADERS.md
docs/security-alerts.md
docs/sandbox-security.md
docs/rbac-abac-access-control.md
docs/gitops.md
docs/k8s-deployment.md
docs/environments.md
docs/runbook-local.md
docs/runbook-local-docker.md
docs/runbook-e2e-render-flow.md
docs/extension-plugins.md
docs/database-schema.md
docs/event-flow-monetization.md
docs/error-handling-design.md
docs/notification-integrations.md
docs/notification-template-management.md
docs/outbox-reliability.md
docs/custom-pricing.md
docs/credit-wallet.md
docs/entitlement-policy.md
docs/quota-policy.md
docs/workspace-entitlement-pool.md
docs/conditional-on-bean-removal-audit.md
docs/code-coverage-tool-selection.md  (under engineering/)
docs/schema-management-policy.md      (under engineering/)
docs/reading-guide.md                 (fix version reference)
docs/architecture-decisions.md        (archive — duplicate of 07)
docs/zh/platform-guide/*.md (11 files)
docs/zh/*.md (other Chinese docs — keep if actively maintained)
docs/authentik/*.yaml
docs/examples/*.md, *.ts, *.java
docs/media-rendering/*.md (21 files)
docs/media-rendering/examples/*.json
.gitops/ (all)
k8s/ (all)
infra/ (all)
scripts/ (all)
prompts/MANIFEST.md (archive)
```

### Archive (Move to docs/archive/)

| File | Reason |
|------|--------|
| `docs/roo-execution-log.md` | Superseded by MANIFEST |
| `docs/roo-final-report.md` | Superseded |
| `docs/roo-gap-report.md` | Superseded by June 13 audit |
| `docs/kilo-execution-summary.md` | Single-phase, superseded |
| `docs/human-review-needed.md` | Phase 20 only, 6+ weeks stale |
| `docs/documentation-gap-analysis.md` | Superseded by June 13 audit |
| `docs/documentation-gap-report.md` | Superseded by June 13 audit |
| `docs/review/autonomous-prompt-completion-matrix.md` | Based on Vue3 codebase |
| `docs/final-project-status.md` | Point-in-time snapshot |
| `docs/review/01-production-blockers.md` | Superseded by review/production-blockers.md |
| `docs/review/02-technical-debt.md` | Superseded by roadmap/technical-debt-roadmap.md |
| `docs/review/03-review-checklists.md` | One-time checklists |
| `docs/review/04-documentation-audit-report.md` | Point-in-time |
| `docs/review/05-architecture-evaluation.md` | Has critical drift (Vue 3) |
| `docs/review/06-full-module-audit.md` | Has critical drift (Vue 3, 31 modules) |
| `docs/review/comprehensive-issue-report-2026-06-16.md` | Point-in-time |
| `docs/review/prompt-module-gap-report.md` | Point-in-time |
| `docs/review/release-candidate-readiness-2026-06-17.md` | Point-in-time |
| `docs/review/staging-readiness-final-audit-2026-06-19.md` | Point-in-time |
| `docs/review/manual-preview-smoke-report-2026-06-17.md` | Point-in-time |
| `docs/review/production-blockers.md` | Superseded by system-audit/CRITICAL_GAPS_ACTION_PLAN |
| `prompts/13-*.md` through `16-*.md` | Historical prompt sessions |
| `docs/architecture-decisions.md` | Duplicate of 07-architecture-decisions.md |
| `docs/advanced-effects-pipeline.md` | Superseded by media-rendering/ docs |
| `docs/ai-engine-spi.md` | Superseded by ai-module implementation |
| `docs/api-versioning.md` | May be stale |
| `docs/asdf-vm.md` | Tool config doc |
| `docs/client-export-browser.md` | Needs validation |
| `docs/commerce-payment-billing-entitlement.md` | Superseded by billing-access/ |
| `docs/configurable-navigation.md` | Superseded by frontend/ docs |
| `docs/subscription-billing.md` | Superseded by billing-access/ |
| `docs/external-billing-integrations.md` | Superseded by billing-access/ |
| `docs/flexible-billing-models.md` | Superseded by billing-access/ |
| `docs/gpac-provider.md` | Superseded by render/ docs |
| `docs/gpu-rendering.md` | Needs validation |
| `docs/gstreamer-provider.md` | Superseded by render/ docs |
| `docs/infrastructure-as-code.md` | Placeholder only |
| `docs/javacv-migration-guide.md` | Migration complete |
| `docs/jooq-workflow.md` | Needs validation |
| `docs/layering-and-open-source.md` | Superseded by architecture/ docs |
| `docs/liteflow-temporal-architecture.md` | Superseded by architecture/ docs |
| `docs/media-processing-module.md` | Superseded by render/ docs |
| `docs/mlt-provider.md` | Superseded by render/ docs |
| `docs/multi-language-subtitle.md` | Superseded by render/ docs |
| `docs/multi-provider-orchestration.md` | Superseded by render/ docs |
| `docs/natural-language-query-assistant.md` | Superseded by prompt-ai/ docs |
| `docs/nix.md` | Tool config doc |
| `docs/ofx-provider.md` | Superseded by render/ docs |
| `docs/persistence-restart-semantics.md` | Needs validation |
| `docs/prompt-63-verification-report.md` | Historical |
| `docs/render-ffmpeg.md` | Superseded by render/ docs |
| `docs/render-gpac-packaging.md` | Superseded by render/ docs |
| `docs/render-mlt.md` | Superseded by render/ docs |
| `docs/render-pipeline-implementation.md` | Superseded by render/ docs |
| `docs/render-pipeline-implementation-zh.md` | Superseded |
| `docs/render-provider-integration.md` | Superseded by render/ docs |
| `docs/render-provider-routing.md` | Superseded by render/ docs |
| `docs/render-provider-extension-roadmap.md` | Superseded by render/ docs |
| `docs/renderprovider-javaCV.md` | Superseded by render/ docs |
| `docs/renderprovider-ofx.md` | Superseded by render/ docs |
| `docs/render-state-machine.md` | Superseded by render/ docs |
| `docs/render-worker-architecture.md` | Superseded by render/ docs |
| `docs/render-worker-deployment.md` | Superseded by render/ docs |
| `docs/remote-worker-architecture.md` | Superseded |
| `docs/secrets-and-local-env.md` | Superseded by operations/ docs |
| `docs/subtitle-font-upload.md` | Superseded by render/font-*.md |
| `docs/technical-assessment.md` | Point-in-time |
| `docs/technical-roadmap-video-platform.md` | Superseded by roadmap/ |
| `docs/temporal-integration-plan.md` | Integration complete |
| `docs/timeline-model.md` | Superseded by render/ docs |
| `docs/user-analytics-api.md` | Needs validation |
| `docs/user-profile-and-habits.md` | Needs validation |
| `docs/video-processing-tools.md` | Superseded by render/ docs |
| `docs/documentation-gap-report.md` | Already listed above |
| `docs/skeleton-gap-priorities.md` | Keep (active) — move from this list |

### Delete

**No documents should be permanently deleted.** All have provenance value. Archive is sufficient.

### Merge

| From | Into | Action |
|------|------|--------|
| `docs/architecture-decisions.md` | `docs/architecture/07-architecture-decisions.md` | Archive the duplicate; 07 is superset |
| `docs/review/production-blockers.md` | `docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md` | Archive review version; system-audit is current |
| `docs/render-pipeline-implementation.md` | `docs/render/overview.md` | Archive the old one; render/overview.md is current |
| `docs/render-state-machine.md` | `docs/render/render-job-schema.md` | Archive the old one |

---

## Part 9: Proposed Future Documentation Structure

```
docs/
  README.md                              — Navigation hub (keep, fix drift)
  
  architecture/
    01-system-architecture.md            — Keep
    02-backend-architecture.md           — Keep
    03-module-architecture.md            — Keep
    04-frontend-architecture.md          — Keep (correct React 19)
    05-request-flows.md                  — Keep
    06-data-architecture.md              — Keep
    07-architecture-decisions.md         — Keep (fix ADR-009)
    08-deployment-architecture.md        — Keep
    current/                             — Keep (4 files — primary truth)
    blueprint/                           — Keep (10 files — target architecture)
    README.md                            — Keep
    (other existing files — keep)
  
  modules/                               — Keep (4 files)
  
  render/
    adr/                                 — Keep (7 ADRs)
    (all existing files — keep)
  
  media-rendering/                       — Keep (21 files)
  
  billing-access/                        — Keep (13 files)
  
  frontend/                              — Keep (20+ files)
  
  api/                                   — Keep (9 files)
  
  extensions/                            — Keep (2 files)
  
  observability/                         — Keep (8 files)
  
  prompt-ai/                             — Keep (3 files)
  
  operations/                            — Keep (5 runbooks)
  
  deployment/                            — Keep (5 files)
  
  roadmap/                               — Keep (5 files)
  
  review/                                — Keep recent reports, archive old ones
    issue-001-jwt-secret-hardening.md    — Keep
    issue-002-stripe-verify-payment.md   — Keep
    issue-003a-modularity-test-investigation.md — Keep
    issue-003b-modularity-test-reenable.md — Keep
    project-intelligence-report.md       — Keep
    documentation-structure-inventory.md — Keep
    documentation-governance-assessment.md — Keep (this file)
    known-limitations.md                 — Keep
    (01-06 numbered reports — archive)
    (point-in-time reports — archive)
  
  system-audit/                          — Keep (2 files)
  
  engineering/                           — Keep (7 files)
  
  releases/                              — Keep (15 files — release records)
  
  zh/                                    — Keep (38 files — Chinese docs)
  
  archive/                               — Keep (224 files + newly archived)
    (all currently archived files — keep)
    (newly archived files from simplification plan)
  
  (root-level docs — keep active ones, archive stale ones)
```

**Minimal changes from current structure.** The only action is moving ~50 stale files from active `docs/` to `docs/archive/`.

---

## Part 10: Multi-Agent Knowledge Loading Strategy

### Tier 0: Always Load (Every Agent, Every Session)

```
AGENTS.md
.kilo/agents/main.md
```

**Load method:** Inject into system prompt  
**Rationale:** These are the only universally accurate, actively maintained docs

### Tier 1: Load by Module (When Agent Works on Specific Module)

```
docs/architecture/current/current-system-state.md     — Any architecture work
docs/architecture/current/current-module-status.md     — Module changes
docs/modulith-debt-register.md                         — Boundary changes
docs/production-safety.md                              — Production config
docs/review/known-limitations.md                       — Before implementing
docs/review/project-intelligence-report.md             — Context loading
```

**Load method:** Agent reads on first relevant task  
**Rationale:** Module-specific context that prevents mistakes

### Tier 2: Load On Demand (Specific Tasks)

```
docs/operations/flyway-baseline-runbook.md             — DB migration tasks
docs/operations/gitops-staging-deploy-runbook.md       — Deployment tasks
docs/render/adr/ADR-*.md                               — Render provider decisions
docs/architecture/07-architecture-decisions.md         — Architecture decisions
docs/api/01-api-strategy.md                            — API development
docs/frontend/react-architecture.md                    — Frontend work
docs/spring-boot-4-upgrade-notes.md                    — Dependency changes
docs/review/issue-001-* through issue-003b-*           — Recent fixes
docs/production-blockers.md                            — Production readiness
DEPLOY.md                                              — Docker operations
```

**Load method:** Agent reads when task requires specific domain knowledge  
**Rationale:** Useful but not universally needed

### Tier 3: Never Load Automatically

```
docs/archive/* (224 + newly archived files)
prompts/*.md
docs/roo-*.md
docs/kilo-execution-summary.md
docs/documentation-gap-*.md
docs/review/01-06-*.md
docs/review/comprehensive-issue-report-*.md
docs/review/autonomous-prompt-completion-matrix.md
docs/final-project-status.md
docs/technical-assessment.md
```

**Load method:** Only if explicitly requested by user  
**Rationale:** Historical, stale, or misleading — will waste context and may cause errors

---

## Part 11: Cleanup Backlog

### P0 — Critical (Do Before Multi-Agent Development)

| # | Task | Files | Impact |
|---|------|-------|--------|
| 1 | Fix README.md frontend stack | `README.md` line 3 | Every agent reads this first |
| 2 | Fix ADR-009 Vue→React | `docs/architecture/07-architecture-decisions.md` | Permanent wrong record |
| 3 | Fix README.md module count | `README.md` line 18 | 31→35 |
| 4 | Fix README.md Flyway description | `README.md` lines 26-34 | V1-V22→V1 only |
| 5 | Fix overview drift | `docs/overview/01-project-overview.md` | Vue 3→React 19, 30→35 |
| 6 | Fix status drift | `docs/overview/02-project-status.md` | 30→35, 17→1 Flyway |
| 7 | Align modulith-debt-register with test | `docs/modulith-debt-register.md` | 8 violations vs 2 in test |

### P1 — Important (Do Within 2 Weeks)

| # | Task | Files | Impact |
|---|------|-------|--------|
| 8 | Archive stale docs | ~50 files from Part 8 | Reduce agent confusion |
| 9 | Update module-boundaries.md | `docs/module-boundaries.md` | Stale render deps |
| 10 | Fix review report drift | `docs/review/05-architecture-evaluation.md`, `06-full-module-audit.md` | Vue 3 references |
| 11 | Fix code-derived-system-overview.md | `docs/code-derived-system-overview.md` | 34→35, V1-V3→V1 |
| 12 | Create CI verification guide | New file | No doc explains CI workflow |
| 13 | Create Flyway migration guide | New file | No doc explains post-V1 migrations |

### P2 — Nice to Have (Do Within 30 Days)

| # | Task | Files | Impact |
|---|------|-------|--------|
| 14 | Consolidate Chinese docs | `docs/zh/` (38 files) | Verify sync with English |
| 15 | Validate stale root-level docs | ~20 `docs/*.md` files | Archive or update |
| 16 | Add module ownership map | New file or CODEOWNERS | Multi-agent coordination |
| 17 | Add doc freshness metadata | All major docs | Automated staleness detection |
| 18 | Remove duplicate archive copies | `docs/archive/spring-boot-4-upgrade-notes.md` | Duplication |

---

## Part 12: Executive Recommendation

### If the project is entering multi-agent development, documentation governance in the next two weeks should be:

### Execute Immediately (This Week)

| Action | Why |
|--------|-----|
| Fix `README.md` (Vue→React, 31→35, Flyway) | Every agent reads this first — it lies |
| Fix ADR-009 (Vue→React) | Permanent wrong record in architecture decisions |
| Fix `docs/overview/01-project-overview.md` | Detailed overview lies about frontend and modules |
| Fix `docs/overview/02-project-status.md` | Status doc lies about modules and Flyway |
| Align `docs/modulith-debt-register.md` with ModularityTest | Debt register and test disagree on violation count |
| Archive `docs/roo-*.md`, `docs/kilo-execution-summary.md` | Historical noise confuses agents |
| Archive `prompts/13-*.md` through `16-*.md` | Historical prompt sessions confuse agents |

### Execute Next Week

| Action | Why |
|--------|-----|
| Archive ~40 stale root-level `docs/*.md` files | Reduce noise for agent context loading |
| Update `docs/module-boundaries.md` | Stale render dependencies |
| Archive stale review reports (01-06) | Point-in-time with drift |
| Create `docs/ci-verification-guide.md` | Missing critical doc |
| Create `docs/flyway-migration-guide.md` | Missing critical doc |

### Do NOT Execute Now

| Action | Why |
|--------|-----|
| Restructure `docs/` directory | Current structure is usable — don't add migration risk |
| Rewrite architecture docs from scratch | Existing docs are mostly accurate — fix specific drift only |
| Add automated doc validation to CI | Premature — stabilize docs first |
| Delete any documents | Archive is sufficient; deletion loses provenance |
| Translate all docs to Chinese | `docs/zh/` exists — verify sync instead |
| Merge all review reports into one | Reports are point-in-time by design |

---

## Summary

### Documentation Health Score

**4 / 10** — Significant drift, duplication, and staleness. Will actively harm multi-agent development if not fixed.

### Canonical Entry Point

**`AGENTS.md`** — the only universally accurate, actively maintained document.

### Top 10 Most Trustworthy Documents

| # | Document | Why |
|---|----------|-----|
| 1 | `AGENTS.md` | Actively maintained, matches code |
| 2 | `.kilo/agents/main.md` | Agent config, matches code |
| 3 | `docs/architecture/current/current-system-state.md` | Only accurate Flyway doc |
| 4 | `docs/architecture/04-frontend-architecture.md` | Only correct React 19 doc |
| 5 | `docs/production-safety.md` | Matches ProductionSafetyValidator |
| 6 | `docs/modulith-debt-register.md` | Mostly accurate |
| 7 | `docs/review/project-intelligence-report.md` | Latest comprehensive analysis |
| 8 | `docs/review/issue-003b-modularity-test-reenable.md` | Latest P0 fix |
| 9 | `docs/operations/flyway-baseline-runbook.md` | Operational truth |
| 10 | `docs/render/adr/ADR-001-render-provider-classification.md` | Domain truth |

### Top 10 Most Dangerous Documents

| # | Document | Why |
|---|----------|-----|
| 1 | `README.md` | Says Vue 3, 31 modules, V1-V22 Flyway |
| 2 | `docs/overview/01-project-overview.md` | Says Vue 3, 30 modules |
| 3 | `docs/architecture/07-architecture-decisions.md` (ADR-009) | Says Vue 3 + Pinia + Apollo |
| 4 | `docs/review/05-architecture-evaluation.md` | Says Vue 3.5, 31 modules |
| 5 | `docs/review/06-full-module-audit.md` | Says Vue 3, 31 modules |
| 6 | `docs/overview/02-project-status.md` | Says 30 modules, 17 Flyway |
| 7 | `docs/module-boundaries.md` | Stale render dependencies |
| 8 | `docs/code-derived-system-overview.md` | Says 34 modules, V1-V3 |
| 9 | `docs/architecture/current/current-module-status.md` | Says 30 modules |
| 10 | `prompts/*.md` | Historical context confuses agents |

### Recommended Archive (50 files)

See Part 8 "Archive" section for complete list.

### Recommended Delete

**None.** Archive is sufficient.

### Recommended Keep

See Part 8 "Keep" section for complete list.

### Documentation Governance Roadmap

| Week | Focus | Deliverables |
|------|-------|-------------|
| Week 1 | Fix critical drift | README.md, ADR-009, overview docs, modulith-debt-register |
| Week 2 | Archive stale docs | Move ~50 files to docs/archive/ |
| Week 3 | Fill gaps | CI guide, Flyway guide, module-boundaries update |
| Week 4 | Establish maintenance rules | Doc update checklist, agent loading strategy |
