# Documentation Consolidation & Information Architecture Plan

**Date:** 2026-06-22  
**Inputs:**  
- `docs/review/documentation-structure-inventory.md` (641 files catalogued)  
- `docs/review/documentation-governance-assessment.md` (health score 4/10)  
- `docs/review/source-of-truth-validation-report.md` (trust ranking, 7 critical drifts)  
- `docs/review/blueprint-reality-mapping-report.md` (alignment score 5/10)  
- `AGENTS.md` (9.5/10 trust)  
- `.kilo/agents/main.md` (9.5/10 trust)

---

## Part 1: Executive Summary

### Current Documentation System Problems

| Problem | Severity | Evidence |
|---------|----------|----------|
| **Drift** | Critical | 7+ docs say Vue 3 (code is React 19); module count ranges 30-35; Flyway says 17/22 but code has 1 |
| **Duplication** | High | 5+ project overviews, 4 gap reports, 3 execution logs, 3 architecture decision locations |
| **Historical Noise** | High | 224 archived files + ~50 stale active files consume agent context |
| **Missing Canonical Sources** | High | No CI guide, no Flyway migration guide, no single architecture entry |
| **Agent Confusion** | Critical | Agent reading all docs would be actively misinformed about frontend, modules, Flyway |

### Scores

| Dimension | Score | Reason |
|-----------|-------|--------|
| **Documentation Architecture Score** | **4/10** | Structure exists but hierarchy is unclear, duplication is rampant, drift is severe |
| **Knowledge Management Score** | **3/10** | No lifecycle model, no freshness validation, no canonical enforcement |
| **Agent Readiness Score** | **3/10** | Only 2 docs are fully trustworthy (AGENTS.md, .kilo/agents/main.md); rest needs validation |

---

## Part 2: Documentation Taxonomy

| Category | Purpose | Retention Policy | Agent Load Policy |
|----------|---------|-----------------|-------------------|
| **Current Truth** | Describe what exists now | Update on every relevant code change | Tier 0-1: always/by-module |
| **Blueprint** | Describe target architecture | Update quarterly or on major pivot | Tier 2: on demand |
| **ADR** | Record architectural decisions | Immutable after accepted; add new ones | Tier 2: on demand |
| **Runbook** | Operational procedures | Update when procedures change | Tier 2: on demand |
| **Review Report** | Point-in-time validation snapshot | Never edit after creation; archive after 30 days | Tier 3: never auto-load |
| **Roadmap** | Future work plans | Update monthly | Tier 2: on demand |
| **Historical** | Provenance records | Never delete; archive only | Tier 3: never auto-load |
| **Temporary** | Working documents | Delete or archive after task | Tier 3: never auto-load |
| **Generated** | Auto-generated from code | Regenerate on build; don't edit manually | Tier 1: by-module |
| **Unknown** | Needs classification | Classify or archive | Tier 3: never auto-load |

### Current Distribution

| Category | Estimated Files | % of Total |
|----------|----------------|------------|
| Current Truth | ~80 | 12% |
| Blueprint | 10 | 2% |
| ADR | 25 | 4% |
| Runbook | 15 | 2% |
| Review Report | 23 | 4% |
| Roadmap | 5 | 1% |
| Historical (archive) | 224 | 35% |
| Historical (active but stale) | ~50 | 8% |
| Domain Documentation | ~180 | 28% |
| Unknown | ~30 | 5% |

---

## Part 3: Future Information Architecture

### Current Structure (24 top-level dirs in docs/)

```
docs/
  api/                    9 files
  architecture/           29 files (blueprint/ 10, current/ 4, + 15 others)
  archive/                224 files
  authentik/              1 file
  billing-access/         13 files
  deployment/             5 files
  development/            4 files
  engineering/            7 files
  examples/               3 files
  extensions/             2 files
  frontend/               25 files
  media-rendering/        26 files
  modules/                4 files
  observability/          8 files
  operations/             5 files
  overview/               2 files
  prompt-ai/              3 files
  releases/               15 files
  render/                 34 files
  review/                 23 files
  roadmap/                5 files
  system-audit/           2 files
  zh/                     38 files
  + 115 root-level .md files
```

### Proposed Structure

```
docs/
  README.md                        — Navigation hub (updated)
  index.md                         — Entry point for humans

  architecture/
    current/                       — KEEP: current system truth (4 files)
    blueprint/                     — KEEP: target architecture (10 files, some archived)
    01-08-*.md                     — KEEP: architecture series (8 files)
    README.md                      — KEEP

  modules/                         — KEEP: module overview (4 files)

  render/                          — KEEP: render domain docs (34 files)
    adr/                           — KEEP: render ADRs (7 files)

  media-rendering/                 — KEEP: media rendering specs (26 files)

  frontend/                        — KEEP: frontend docs (25 files)

  api/                             — KEEP: API docs (9 files)

  billing-access/                  — KEEP: billing/access docs (13 files)

  extensions/                      — KEEP: extension docs (2 files)

  observability/                   — KEEP: observability docs (8 files)

  prompt-ai/                       — KEEP: prompt/AI docs (3 files)

  operations/                      — KEEP: runbooks (5 files)

  deployment/                      — KEEP: deployment docs (5 files)

  roadmap/                         — KEEP: active roadmaps (2 files; archive 3 deferred)

  review/                          — KEEP: review reports
    active/                        — Current truth reports (5 files)
    issues/                        — P0 issue reports (4 files)
    historical/                    — Archived review reports (14 files)

  system-audit/                    — KEEP: audit reports (2 files)

  engineering/                     — KEEP: engineering docs (7 files)

  releases/                        — KEEP: release records (15 files)

  zh/                              — KEEP: Chinese docs (38 files)

  archive/                         — KEEP: all archived content (224 + ~50 new)

  _templates/                      — NEW: document templates

  (root-level stale docs)          — MOVE to archive/
```

### Migration Effort

| Action | Files | Effort | Risk |
|--------|-------|--------|------|
| Move stale root-level docs to archive/ | ~50 | 1 hour | Low |
| Reorganize review/ into active/historical | 23 | 30 min | Low |
| Create _templates/ | 1 | 15 min | None |
| Update docs/README.md | 1 | 30 min | Low |
| Fix 7 P0 drifts | 7 | 2 hours | Low |
| **Total** | **~82** | **~4 hours** | **Low** |

---

## Part 4: Canonical Knowledge Layer

### Tier 0: Always Load (Every Agent Boot)

| # | Document | Size | Why |
|---|----------|------|-----|
| 1 | `AGENTS.md` | ~3KB | 14/14 claims verified; primary agent config |
| 2 | `.kilo/agents/main.md` | ~5KB | Module boundaries verified against package-info |

**Total context:** ~8KB  
**Maintenance:** Updated when module structure changes  
**Risk:** None — fully verified

### Tier 1: Load by Context

| # | Document | Context | Trust |
|---|----------|---------|-------|
| 3 | `docs/architecture/current/current-system-state.md` | Architecture work | 7/10 |
| 4 | `docs/modulith-debt-register.md` | Module boundary changes | 6/10 (fix count) |
| 5 | `docs/production-safety.md` | Production config | 9/10 |
| 6 | `docs/review/known-limitations.md` | Before implementing | 7/10 |
| 7 | `docs/review/project-intelligence-report.md` | Context loading | 8.5/10 |
| 8 | `docs/architecture/04-frontend-architecture.md` | Frontend work | 9/10 |
| 9 | `docs/render/overview.md` | Render work | 8/10 |
| 10 | `docs/billing-access/access-control-overview.md` | Billing work | 8/10 |

**Total context:** ~40KB  
**Maintenance:** Updated when modules change  
**Risk:** Low — mostly verified

### Tier 2: Load on Demand

| # | Document | When | Trust |
|---|----------|------|-------|
| 11 | `docs/operations/flyway-baseline-runbook.md` | DB tasks | 9/10 |
| 12 | `docs/operations/gitops-staging-deploy-runbook.md` | Deployment | 9/10 |
| 13 | `docs/render/adr/ADR-001` through `ADR-007` | Render decisions | 9/10 |
| 14 | `docs/architecture/07-architecture-decisions.md` | Arch decisions | 7/10 (fix ADR-009) |
| 15 | `docs/api/01-api-strategy.md` | API work | 8/10 |
| 16 | `docs/frontend/react-architecture.md` | Frontend work | 9/10 |
| 17 | `docs/spring-boot-4-upgrade-notes.md` | Dependency mgmt | 8/10 |
| 18 | `docs/review/issue-001-*` through `issue-003b-*` | Recent fixes | 9/10 |
| 19 | `DEPLOY.md` | Docker ops | 8/10 |
| 20 | `docs/roadmap/technical-debt-roadmap.md` | Tech debt | 7/10 |

**Total context:** Variable  
**Maintenance:** Updated when relevant code changes  
**Risk:** Low — domain-specific

### Tier 3: Never Auto-Load

| Category | Files | Why |
|----------|-------|-----|
| `docs/archive/*` | 224+ | "not current truth" |
| `docs/review/historical/*` | ~14 | Point-in-time snapshots |
| `prompts/*.md` | 6 | Historical sessions |
| `docs/roo-*.md` | 3 | Superseded |
| `docs/overview/*` | 2 | Critical drift (Vue 3) |
| `docs/code-derived-system-overview.md` | 1 | Stale |
| `docs/review/05-architecture-evaluation.md` | 1 | Vue 3 drift |
| `docs/review/06-full-module-audit.md` | 1 | Vue 3 drift |

**Total:** ~280 files  
**Load method:** Only on explicit user request

---

## Part 5: Agent Knowledge Loading Strategy

### Planner Agent

| Tier | Documents | Reason |
|------|-----------|--------|
| Always | AGENTS.md, .kilo/agents/main.md | Project context |
| By Module | current-system-state.md, modulith-debt-register.md | Architecture context |
| On Demand | technical-debt-roadmap.md, render-pipeline-roadmap.md | Planning context |
| Never | All blueprints, all historical | Waste context |

### Coder Agent

| Tier | Documents | Reason |
|------|-----------|--------|
| Always | AGENTS.md, .kilo/agents/main.md | Rules and boundaries |
| By Module | module-specific docs (render/overview.md, billing-access/*, etc.) | Domain context |
| On Demand | production-safety.md, known-limitations.md | Safety context |
| Never | Blueprints, roadmaps, historical reviews | Not actionable |

### Tester Agent

| Tier | Documents | Reason |
|------|-----------|--------|
| Always | AGENTS.md, .kilo/agents/main.md | Rules |
| By Module | known-limitations.md | What to test |
| On Demand | production-safety.md, operations/runbooks | Verification context |
| Never | All blueprints, all architecture docs | Test from code, not docs |

### Reviewer Agent

| Tier | Documents | Reason |
|------|-----------|--------|
| Always | AGENTS.md, .kilo/agents/main.md, modulith-debt-register.md | Boundary enforcement |
| By Module | module-boundaries.md (after fix) | Dependency verification |
| On Demand | ADR docs, production-safety.md | Decision verification |
| Never | Historical reviews, prompts | Not relevant |

### Architect Agent

| Tier | Documents | Reason |
|------|-----------|--------|
| Always | AGENTS.md, .kilo/agents/main.md | Foundation |
| By Module | current-system-state.md, module-status.md, modulith-debt-register.md | Architecture truth |
| On Demand | All blueprints (after fixes), ADR docs, roadmaps | Target state |
| Never | Historical reviews, prompts, gap reports | Superseded |

---

## Part 6: Canonical Source Mapping

| Topic | Canonical Source | Trust | Secondary | Historical |
|-------|-----------------|-------|-----------|------------|
| **Project Overview** | `AGENTS.md` | 9.5 | `docs/README.md` | `docs/overview/*` |
| **Architecture** | `docs/architecture/current/current-system-state.md` | 7 | `docs/architecture/01-system-architecture.md` | `docs/architecture-notes.md` |
| **Module Boundaries** | `.kilo/agents/main.md` | 9.5 | `docs/modulith-debt-register.md` | `docs/module-boundaries.md` |
| **Frontend** | `docs/architecture/04-frontend-architecture.md` | 9 | `docs/frontend/react-architecture.md` | `docs/overview/01-project-overview.md` |
| **Backend** | `AGENTS.md` (Architecture section) | 9.5 | `docs/architecture/02-backend-architecture.md` | — |
| **Security** | `docs/production-safety.md` | 9 | `docs/security-and-tenancy.md` | `docs/archive/security-policy.md` |
| **Billing** | `docs/billing-access/access-control-overview.md` | 8 | `docs/subscription-billing.md` | `docs/archive/subscription-billing.md` |
| **Payment** | `payment-module` source code | 10 | `docs/review/issue-002-*` | — |
| **Quota** | `QuotaService.java` | 10 | `docs/review/project-intelligence-report.md` | `docs/quota-policy.md` |
| **Render** | `render-module` source code | 10 | `docs/render/overview.md` | `docs/render-pipeline-implementation.md` |
| **Workflow** | `workflow-module` source code | 10 | `docs/liteflow-temporal-architecture.md` | `docs/temporal-integration-plan.md` |
| **Deployment** | `docker-compose.yml` + `Dockerfile` | 10 | `DEPLOY.md` | `docs/archive/docker-external-config.md` |
| **CI/CD** | `.github/workflows/ci.yml` | 10 | (no doc) | — |
| **Flyway** | `db/migration/` directory | 10 | `docs/operations/flyway-baseline-runbook.md` | — |
| **Agent Rules** | `AGENTS.md` + `.kilo/agents/main.md` | 9.5 | — | — |

---

## Part 7: Duplicate Content Analysis

### Cluster 1: Project Overview (5 docs → 1 canonical)

| Document | Role | Action |
|----------|------|--------|
| `AGENTS.md` | **Master** — primary overview | Keep |
| `README.md` | **Derived** — human quick-start | Fix drift, keep |
| `docs/overview/01-project-overview.md` | **Redundant** — stale | Archive |
| `docs/overview/02-project-status.md` | **Redundant** — stale | Archive |
| `docs/code-derived-system-overview.md` | **Redundant** — stale | Archive |

### Cluster 2: Architecture Decisions (3 locations → 1 canonical)

| Document | Role | Action |
|----------|------|--------|
| `docs/architecture/07-architecture-decisions.md` | **Master** — 12 ADRs | Fix ADR-009, keep |
| `docs/render/adr/*.md` | **Domain-specific** — 7 render ADRs | Keep (complementary) |
| `docs/architecture-decisions.md` | **Redundant** — 6 ADRs (subset) | Archive |

### Cluster 3: Gap Reports (4 docs → 1 canonical)

| Document | Role | Action |
|----------|------|--------|
| `docs/system-audit/platform-architecture-audit-2026-06-13.md` | **Master** — latest audit | Keep |
| `docs/roo-gap-report.md` | **Redundant** — oldest | Archive |
| `docs/documentation-gap-report.md` | **Redundant** — superseded | Archive |
| `docs/documentation-gap-analysis.md` | **Redundant** — superseded | Archive |

### Cluster 4: Execution Logs (3 docs → archive all)

| Document | Role | Action |
|----------|------|--------|
| `docs/roo-execution-log.md` | **Historical** | Archive |
| `prompts/MANIFEST.md` | **Historical** | Archive |
| `docs/kilo-execution-summary.md` | **Historical** | Archive |

### Cluster 5: Module Boundaries (3 docs → 2 canonical)

| Document | Role | Action |
|----------|------|--------|
| `.kilo/agents/main.md` | **Master** — agent config | Keep |
| `docs/modulith-debt-register.md` | **Master** — violation tracking | Fix count, keep |
| `docs/module-boundaries.md` | **Stale** — render deps wrong | Update or archive |

### Cluster 6: Review Reports (23 docs → 5 active + 18 historical)

| Document | Role | Action |
|----------|------|--------|
| `docs/review/project-intelligence-report.md` | **Active** | Keep |
| `docs/review/known-limitations.md` | **Active** | Keep |
| `docs/review/issue-001-*` through `issue-003b-*` | **Active** | Keep |
| `docs/review/documentation-structure-inventory.md` | **Active** | Keep |
| `docs/review/documentation-governance-assessment.md` | **Active** | Keep |
| `docs/review/source-of-truth-validation-report.md` | **Active** | Keep |
| `docs/review/blueprint-reality-mapping-report.md` | **Active** | Keep |
| `docs/review/01-06-*.md` | **Historical** | Archive |
| `docs/review/comprehensive-issue-report-*.md` | **Historical** | Archive |
| `docs/review/autonomous-prompt-completion-matrix.md` | **Historical** | Archive |
| `docs/review/prompt-module-gap-report.md` | **Historical** | Archive |
| `docs/review/release-candidate-readiness-*.md` | **Historical** | Archive |
| `docs/review/staging-readiness-final-audit-*.md` | **Historical** | Archive |
| `docs/review/manual-preview-smoke-report-*.md` | **Historical** | Archive |
| `docs/review/production-blockers.md` | **Historical** | Archive |

---

## Part 8: Archive Plan

### Phase 1: Critical Stale Docs (This Week)

| File | Reason | Risk | Replacement |
|------|--------|------|-------------|
| `docs/overview/01-project-overview.md` | Vue 3, 30 modules | High — misleads agents | `AGENTS.md` |
| `docs/overview/02-project-status.md` | 30 modules, 17 Flyway | High — misleads agents | `AGENTS.md` |
| `docs/code-derived-system-overview.md` | 34 modules, V1-V3 | Medium | `project-intelligence-report.md` |
| `docs/review/05-architecture-evaluation.md` | Vue 3, 31 modules | Medium | `project-intelligence-report.md` |
| `docs/review/06-full-module-audit.md` | Vue 3, 31 modules | Medium | `project-intelligence-report.md` |
| `docs/roo-execution-log.md` | Superseded | Low | `prompts/MANIFEST.md` |
| `docs/roo-final-report.md` | Superseded | Low | — |
| `docs/roo-gap-report.md` | Superseded | Low | `system-audit/platform-architecture-audit-2026-06-13.md` |
| `docs/kilo-execution-summary.md` | Superseded | Low | — |
| `docs/human-review-needed.md` | Phase 20 only | Low | `CRITICAL_GAPS_ACTION_PLAN.md` |
| `docs/documentation-gap-analysis.md` | Superseded | Low | `system-audit/platform-architecture-audit-2026-06-13.md` |
| `docs/documentation-gap-report.md` | Superseded | Low | `system-audit/platform-architecture-audit-2026-06-13.md` |
| `docs/review/autonomous-prompt-completion-matrix.md` | Vue3-based | Low | — |
| `docs/final-project-status.md` | Point-in-time | Low | — |

### Phase 2: Superseded Domain Docs (Next Week)

| File | Reason | Replacement |
|------|--------|-------------|
| `docs/architecture-decisions.md` | Duplicate of 07 | `docs/architecture/07-architecture-decisions.md` |
| `docs/commerce-payment-billing-entitlement.md` | Superseded | `docs/billing-access/` |
| `docs/subscription-billing.md` | Superseded | `docs/billing-access/` |
| `docs/external-billing-integrations.md` | Superseded | `docs/billing-access/` |
| `docs/flexible-billing-models.md` | Superseded | `docs/billing-access/` |
| `docs/configurable-navigation.md` | Superseded | `docs/frontend/` |
| `docs/render-ffmpeg.md` | Superseded | `docs/render/` |
| `docs/render-gpac-packaging.md` | Superseded | `docs/render/` |
| `docs/render-mlt.md` | Superseded | `docs/render/` |
| `docs/render-pipeline-implementation.md` | Superseded | `docs/render/overview.md` |
| `docs/render-pipeline-implementation-zh.md` | Superseded | `docs/render/overview.md` |
| `docs/render-provider-integration.md` | Superseded | `docs/render/` |
| `docs/render-provider-routing.md` | Superseded | `docs/render/` |
| `docs/render-provider-extension-roadmap.md` | Superseded | `docs/render/` |
| `docs/renderprovider-javaCV.md` | Superseded | `docs/render/` |
| `docs/renderprovider-ofx.md` | Superseded | `docs/render/` |
| `docs/render-state-machine.md` | Superseded | `docs/render/render-job-schema.md` |
| `docs/render-worker-architecture.md` | Superseded | `docs/render/` |
| `docs/render-worker-deployment.md` | Superseded | `docs/render/` |
| `docs/gpac-provider.md` | Superseded | `docs/render/` |
| `docs/gstreamer-provider.md` | Superseded | `docs/render/` |
| `docs/mlt-provider.md` | Superseded | `docs/render/` |
| `docs/ofx-provider.md` | Superseded | `docs/render/` |
| `docs/subtitle-font-upload.md` | Superseded | `docs/render/font-*.md` |
| `docs/layering-and-open-source.md` | Superseded | `docs/architecture/` |
| `docs/liteflow-temporal-architecture.md` | Superseded | `docs/architecture/` |
| `docs/media-processing-module.md` | Superseded | `docs/render/` |
| `docs/multi-provider-orchestration.md` | Superseded | `docs/render/` |
| `docs/multi-language-subtitle.md` | Superseded | `docs/render/` |
| `docs/natural-language-query-assistant.md` | Superseded | `docs/prompt-ai/` |
| `docs/video-processing-tools.md` | Superseded | `docs/render/` |
| `docs/timeline-model.md` | Superseded | `docs/render/` |
| `docs/remote-worker-architecture.md` | Superseded | `docs/render/` |
| `docs/secrets-and-local-env.md` | Superseded | `docs/operations/` |
| `docs/temporal-integration-plan.md` | Complete | — |
| `docs/javacv-migration-guide.md` | Complete | — |

### Phase 3: Deferred Blueprints & Roadmaps (Week 3)

| File | Reason | Replacement |
|------|--------|-------------|
| `docs/architecture/blueprint/module-blueprint-automation-plugin.md` | Deferred 6+ months | `automation-plugin-platform-roadmap.md` |
| `docs/architecture/blueprint/module-blueprint-artifact-storage.md` | S3 implemented | — |
| `docs/roadmap/automation-plugin-platform-roadmap.md` | Deferred | — |
| `docs/roadmap/capability-opening-roadmap.md` | Deferred | — |
| `docs/roadmap/ai-provider-ecosystem-roadmap.md` | Deferred | — |
| `prompts/13-*.md` through `16-*.md` | Historical | `prompts/MANIFEST.md` |

---

## Part 9: Update Plan

### P0 — Critical (Fix This Week)

| File | Why | Effort | Dependency |
|------|-----|--------|------------|
| `README.md` | Vue→React, 31→35, V1-V22→V1 | 15 min | None |
| `docs/architecture/07-architecture-decisions.md` | ADR-009 Vue→React | 10 min | None |
| `docs/modulith-debt-register.md` | 8→2 violations | 10 min | None |
| `docs/overview/01-project-overview.md` | Vue→React, 30→35 | 15 min | Or archive |
| `docs/overview/02-project-status.md` | 30→35, 17→1 Flyway | 15 min | Or archive |
| `docs/architecture/current/current-system-state.md` | PG 15→16, 50+→133 tables | 10 min | None |
| `docs/architecture/current/current-module-status.md` | 30→35 modules | 5 min | None |

**Total P0 effort:** ~1.5 hours

### P1 — Important (Fix Next Week)

| File | Why | Effort | Dependency |
|------|-----|--------|------------|
| `docs/module-boundaries.md` | Stale render dependencies | 20 min | None |
| `docs/quota-policy.md` | Add in-memory disclaimer | 10 min | None |
| `docs/review/known-limitations.md` | Add quota gap | 10 min | None |
| `docs/architecture/blueprint/system-blueprint.md` | 32→35, 17→1 | 15 min | None |
| `docs/architecture/blueprint/module-blueprint-render.md` | Single→7+ providers | 15 min | None |
| `docs/architecture/blueprint/platform-composition-blueprint.md` | Temporal/LiteFlow status | 10 min | None |
| `docs/reading-guide.md` | Version 5→0.2.0-SNAPSHOT | 5 min | None |

**Total P1 effort:** ~1.5 hours

### P2 — Nice to Have (Week 3-4)

| File | Why | Effort | Dependency |
|------|-----|--------|------------|
| `docs/infrastructure-as-code.md` | Mark as scaffolding-only | 5 min | None |
| `docs/zh/*.md` | Verify sync with English | 2 hours | P0 fixes done |
| `docs/render/adr/ADR-007` | Clarify: advisory vs enforced | 10 min | None |

---

## Part 10: New Documents Needed

| Document | Purpose | Priority | Effort |
|----------|---------|----------|--------|
| `docs/ci-verification-guide.md` | Explain CI workflow, local reproduction, merge gates | P1 | 1 hour |
| `docs/flyway-migration-guide.md` | Rules for post-V1 migrations, naming, rollback | P1 | 1 hour |
| `docs/review/_templates/issue-report-template.md` | Standardize review reports | P2 | 30 min |
| `docs/review/_templates/adr-template.md` | Standardize ADRs | P2 | 30 min |

**Not needed:**
- Multi-Agent Development Guide — `AGENTS.md` + `.kilo/agents/main.md` already serve this
- Worktree Strategy Guide — operational, not documentation
- Review Workflow Guide — `docs/review/` structure is self-explanatory

---

## Part 11: Documentation Lifecycle Model

### Create

1. New docs go through review before merge
2. Use templates from `docs/review/_templates/`
3. Include metadata header: status, last_verified, scope, truth_level

### Review

1. Every PR that changes module structure must update `AGENTS.md` if needed
2. Every PR that adds a Flyway migration must update `README.md` Flyway table
3. Every PR that changes NamedInterfaces must update `docs/modulith-debt-register.md`

### Validate

1. Quarterly: run source-of-truth-validation against code
2. On major release: regenerate `docs/architecture/current/` from code
3. CI check: verify `README.md` module count matches `settings.gradle.kts`

### Deprecate

1. When a doc is superseded, add header: `**Status:** Deprecated — see [replacement]`
2. Move to `docs/archive/` after 30 days

### Archive

1. `git mv` to `docs/archive/`
2. Never delete — archive preserves provenance
3. Update `docs/archive/README.md` index

### Drift Prevention Rules

| Rule | Enforcement |
|------|-------------|
| Frontend framework changes | Must update `AGENTS.md`, `README.md`, `04-frontend-architecture.md` |
| Module added/removed | Must update `settings.gradle.kts`, `AGENTS.md`, `README.md` |
| Flyway migration added | Must update `README.md` Flyway table |
| NamedInterface changed | Must update `docs/modulith-debt-register.md` |
| New application profile | Must update `docs/architecture/current/current-startup-profiles.md` |
| Security config changed | Must update `docs/production-safety.md` |

---

## Part 12: Agent Safe Knowledge Set

### Default Agent Knowledge (20 documents)

| # | Document | Size | Tier | Trust |
|---|----------|------|------|-------|
| 1 | `AGENTS.md` | 3KB | 0 | 9.5 |
| 2 | `.kilo/agents/main.md` | 5KB | 0 | 9.5 |
| 3 | `docs/architecture/current/current-system-state.md` | 8KB | 1 | 7 |
| 4 | `docs/modulith-debt-register.md` | 2KB | 1 | 6 |
| 5 | `docs/production-safety.md` | 5KB | 1 | 9 |
| 6 | `docs/review/known-limitations.md` | 4KB | 1 | 7 |
| 7 | `docs/review/project-intelligence-report.md` | 15KB | 1 | 8.5 |
| 8 | `docs/architecture/04-frontend-architecture.md` | 6KB | 1 | 9 |
| 9 | `docs/render/overview.md` | 5KB | 1 | 8 |
| 10 | `docs/billing-access/access-control-overview.md` | 4KB | 1 | 8 |
| 11 | `docs/operations/flyway-baseline-runbook.md` | 3KB | 2 | 9 |
| 12 | `docs/operations/gitops-staging-deploy-runbook.md` | 3KB | 2 | 9 |
| 13 | `docs/render/adr/ADR-001-render-provider-classification.md` | 2KB | 2 | 9 |
| 14 | `docs/architecture/07-architecture-decisions.md` | 10KB | 2 | 7 |
| 15 | `docs/api/01-api-strategy.md` | 5KB | 2 | 8 |
| 16 | `docs/frontend/react-architecture.md` | 4KB | 2 | 9 |
| 17 | `docs/spring-boot-4-upgrade-notes.md` | 5KB | 2 | 8 |
| 18 | `docs/review/issue-003b-modularity-test-reenable.md` | 5KB | 2 | 9 |
| 19 | `DEPLOY.md` | 3KB | 2 | 8 |
| 20 | `docs/roadmap/technical-debt-roadmap.md` | 8KB | 2 | 7 |

**Total estimated context:** ~110KB  
**Maintenance cost:** Medium — 7 docs need P0 fixes  
**Risk:** Low after P0 fixes applied

---

## Part 13: Multi-Agent Governance Impact

### 50 Agent Tasks

| Requirement | Current State | Gap |
|-------------|--------------|-----|
| Consistent project context | AGENTS.md + main.md (verified) | None |
| Module boundary enforcement | ModularityTest (active) | None |
| Agent-specific knowledge loading | Tier system defined | Implementation needed |
| Drift prevention | No CI checks | **Add CI doc validation** |

### 100 Agent Tasks

| Requirement | Current State | Gap |
|-------------|--------------|-----|
| Parallel module work | Module boundaries enforced | None |
| Merge conflict prevention | Git worktree strategy needed | **Document worktree rules** |
| Review automation | ModularityTest only | **Add automated doc freshness checks** |

### 500 Agent Tasks

| Requirement | Current State | Gap |
|-------------|--------------|-----|
| Knowledge versioning | No versioning | **Add doc version tracking** |
| Agent specialization | No formal boundaries | **Define agent-module ownership** |
| Conflict resolution | Manual | **Automate merge conflict detection** |

### Required Governance Mechanisms

| Mechanism | Priority | When |
|-----------|----------|------|
| CI doc freshness validation | P0 | Before 50 tasks |
| Agent-module ownership map | P1 | Before 100 tasks |
| Automated drift detection | P1 | Before 100 tasks |
| Doc version tracking | P2 | Before 500 tasks |

---

## Part 14: Final Migration Roadmap

### Week 1: Fix Critical Drift + Archive Phase 1

| Day | Action | Files | Goal |
|-----|--------|-------|------|
| Day 1 | Fix P0 drifts | 7 files | Agent safety |
| Day 2 | Archive Phase 1 stale docs | 14 files | Reduce noise |
| Day 3 | Update docs/README.md | 1 file | Navigation clarity |
| Day 4 | Reorganize docs/review/ | 23 files | Structure clarity |
| Day 5 | Verify: run ModularityTest + full test suite | — | Validation |

**Target:** Documentation Architecture ≥ 6/10, Agent Readiness ≥ 7/10

### Week 2: Archive Phase 2 + Fill Gaps

| Day | Action | Files | Goal |
|-----|--------|-------|------|
| Day 6-7 | Archive Phase 2 superseded docs | ~35 files | Reduce duplication |
| Day 8 | Fix P1 drifts | 7 files | Blueprint accuracy |
| Day 9 | Create CI verification guide | 1 new file | Fill gap |
| Day 10 | Create Flyway migration guide | 1 new file | Fill gap |

**Target:** Documentation Architecture ≥ 7/10, Agent Readiness ≥ 8/10

### Week 3: Archive Phase 3 + Templates

| Day | Action | Files | Goal |
|-----|--------|-------|------|
| Day 11-12 | Archive Phase 3 deferred blueprints | ~6 files | Reduce noise |
| Day 13 | Create doc templates | 2 new files | Lifecycle support |
| Day 14 | Verify Chinese docs sync | 38 files | Completeness |

**Target:** Documentation Architecture ≥ 8/10, Agent Readiness ≥ 9/10

### Week 4: Validation + CI Integration

| Day | Action | Files | Goal |
|-----|--------|-------|------|
| Day 15-16 | Run full source-of-truth validation | — | Verify all fixes |
| Day 17 | Add CI doc freshness checks | 1 new file | Drift prevention |
| Day 18 | Create agent-module ownership map | 1 new file | Multi-agent support |
| Day 19-20 | Final review and sign-off | — | Governance established |

**Target:** Documentation Architecture ≥ 8/10, Knowledge Management ≥ 8/10, Agent Readiness ≥ 9/10

---

## Part 15: Executive Recommendation

### Should we: Continue fixing docs, Start archiving, Start multi-agent dev, or Do all in parallel?

**Answer: Do all in parallel. Start multi-agent development NOW. Fix docs in parallel.**

### Reasoning

| Action | Risk | Effort | Blocks Multi-Agent? |
|--------|------|--------|---------------------|
| Fix 7 P0 drifts | Low | 1.5 hours | **Yes** — agents read wrong facts |
| Archive Phase 1 | Low | 1 hour | **No** — but reduces context noise |
| Start multi-agent dev | Low | 0 | **No** — AGENTS.md + main.md are sufficient |
| Full doc cleanup | Low | 1 week | **No** — nice to have, not blocking |

### Recommended Sequence

1. **Today (1.5 hours):** Fix 7 P0 drifts. This is the only blocker.
2. **Today:** Start multi-agent development. Agents load AGENTS.md + .kilo/agents/main.md (verified accurate).
3. **This week (1 hour):** Archive Phase 1 stale docs (14 files).
4. **Next week (1.5 hours):** Fix P1 drifts + Archive Phase 2 (35 files).
5. **Week 3-4:** Fill gaps (CI guide, Flyway guide) + Archive Phase 3.

### What NOT to Do

- Do NOT block multi-agent development for documentation perfection
- Do NOT restructure docs/ directory — current structure is usable
- Do NOT delete any documents — archive is sufficient
- Do NOT add automated doc validation to CI until Week 4
- Do NOT rewrite architecture docs from scratch — fix specific facts only

---

## Summary

### Scores

| Dimension | Current | Target (Week 4) |
|-----------|---------|-----------------|
| **Documentation Architecture Score** | 4/10 | 8/10 |
| **Knowledge Management Score** | 3/10 | 8/10 |
| **Agent Readiness Score** | 3/10 | 9/10 |

### Top 20 Canonical Documents

| # | Document | Tier | Trust |
|---|----------|------|-------|
| 1 | `AGENTS.md` | 0 | 9.5 |
| 2 | `.kilo/agents/main.md` | 0 | 9.5 |
| 3 | `docs/architecture/current/current-system-state.md` | 1 | 7 |
| 4 | `docs/production-safety.md` | 1 | 9 |
| 5 | `docs/modulith-debt-register.md` | 1 | 6 |
| 6 | `docs/review/known-limitations.md` | 1 | 7 |
| 7 | `docs/review/project-intelligence-report.md` | 1 | 8.5 |
| 8 | `docs/architecture/04-frontend-architecture.md` | 1 | 9 |
| 9 | `docs/render/overview.md` | 1 | 8 |
| 10 | `docs/billing-access/access-control-overview.md` | 1 | 8 |
| 11 | `docs/operations/flyway-baseline-runbook.md` | 2 | 9 |
| 12 | `docs/operations/gitops-staging-deploy-runbook.md` | 2 | 9 |
| 13 | `docs/render/adr/ADR-001-render-provider-classification.md` | 2 | 9 |
| 14 | `docs/architecture/07-architecture-decisions.md` | 2 | 7 |
| 15 | `docs/api/01-api-strategy.md` | 2 | 8 |
| 16 | `docs/frontend/react-architecture.md` | 2 | 9 |
| 17 | `docs/spring-boot-4-upgrade-notes.md` | 2 | 8 |
| 18 | `docs/review/issue-003b-modularity-test-reenable.md` | 2 | 9 |
| 19 | `DEPLOY.md` | 2 | 8 |
| 20 | `docs/roadmap/technical-debt-roadmap.md` | 2 | 7 |

### Top 20 Archive Candidates

| # | File | Reason |
|---|------|--------|
| 1 | `docs/overview/01-project-overview.md` | Vue 3, 30 modules |
| 2 | `docs/overview/02-project-status.md` | 30 modules, 17 Flyway |
| 3 | `docs/code-derived-system-overview.md` | 34 modules, V1-V3 |
| 4 | `docs/review/05-architecture-evaluation.md` | Vue 3, 31 modules |
| 5 | `docs/review/06-full-module-audit.md` | Vue 3, 31 modules |
| 6 | `docs/roo-execution-log.md` | Superseded |
| 7 | `docs/roo-final-report.md` | Superseded |
| 8 | `docs/roo-gap-report.md` | Superseded |
| 9 | `docs/kilo-execution-summary.md` | Superseded |
| 10 | `docs/human-review-needed.md` | Phase 20 only |
| 11 | `docs/documentation-gap-analysis.md` | Superseded |
| 12 | `docs/documentation-gap-report.md` | Superseded |
| 13 | `docs/review/autonomous-prompt-completion-matrix.md` | Vue3-based |
| 14 | `docs/final-project-status.md` | Point-in-time |
| 15 | `docs/architecture-decisions.md` | Duplicate of 07 |
| 16 | `docs/commerce-payment-billing-entitlement.md` | Superseded |
| 17 | `docs/subscription-billing.md` | Superseded |
| 18 | `docs/render-pipeline-implementation.md` | Superseded |
| 19 | `docs/render-state-machine.md` | Superseded |
| 20 | `docs/layering-and-open-source.md` | Superseded |

### P0 Update List

| # | File | Fix |
|---|------|-----|
| 1 | `README.md` | Vue→React, 31→35, Flyway |
| 2 | `docs/architecture/07-architecture-decisions.md` | ADR-009 Vue→React |
| 3 | `docs/modulith-debt-register.md` | 8→2 violations |
| 4 | `docs/architecture/current/current-system-state.md` | PG 15→16, 50+→133 |
| 5 | `docs/architecture/current/current-module-status.md` | 30→35 |
| 6 | `docs/overview/01-project-overview.md` | Vue→React, 30→35 (or archive) |
| 7 | `docs/overview/02-project-status.md` | 30→35, 17→1 (or archive) |

### Future Knowledge Architecture

```
Tier 0 (Always):     AGENTS.md, .kilo/agents/main.md
Tier 1 (By Context): current-system-state, modulith-debt-register, production-safety,
                     known-limitations, project-intelligence-report, frontend-architecture,
                     render/overview, billing-access/overview
Tier 2 (On Demand):  operations/runbooks, render/adr/*, architecture-decisions,
                     api/strategy, react-architecture, spring-boot-upgrade-notes,
                     issue reports, DEPLOY.md, technical-debt-roadmap
Tier 3 (Never):      archive/*, historical reviews, prompts, stale overviews
```

### Agent Default Knowledge Set

20 documents, ~110KB context, all verified against code.

### Can Documentation Cleanup Start?

**Yes.** P0 fixes (1.5 hours) unblock multi-agent development. Archive Phase 1 (1 hour) reduces noise. Everything else can proceed in parallel with development.
