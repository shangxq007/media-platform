# Platform Documentation

> **Last Updated:** 2026-06-24

## Canonical Knowledge Layers

### Tier 0 — Always Load (Agent Boot)

| Document | Purpose | Trust |
|----------|---------|-------|
| [AGENTS.md](../AGENTS.md) | Agent config — priorities, rules, module list | 9.5/10 |
| [.kilo/agents/main.md](../.kilo/agents/main.md) | Detailed module boundaries and development rules | 9.5/10 |

These two documents are the **primary source of truth** for AI agents. They are actively maintained and verified against code.

### Tier 1 — Load by Context

| Document | Context | Trust |
|----------|---------|-------|
| [Current System State](architecture/current/current-system-state.md) | Architecture work | 7/10 |
| [Current Module Status](architecture/current/current-module-status.md) | Module changes | 7/10 |
| [Modulith Debt Register](modulith-debt-register.md) | Boundary changes | 8/10 |
| [Production Safety](production-safety.md) | Production config | 9/10 |
| [Known Limitations](review/known-limitations.md) | Before implementing | 7/10 |
| [Project Intelligence Report](review/project-intelligence-report.md) | Comprehensive analysis | 8.5/10 |
| [Frontend Architecture](architecture/04-frontend-architecture.md) | Frontend work | 9/10 |
| [Render Overview](render/overview.md) | Render module | 8/10 |
| [Billing/Access Overview](billing-access/access-control-overview.md) | Billing work | 8/10 |

### Tier 2 — Load on Demand

| Document | When |
|----------|------|
| [Flyway Baseline Runbook](operations/flyway-baseline-runbook.md) | DB migration tasks |
| [GitOps Staging Runbook](operations/gitops-staging-deploy-runbook.md) | Deployment tasks |
| [Render ADRs](render/adr/) | Render provider decisions |
| [Architecture Decisions](architecture/07-architecture-decisions.md) | Architecture decisions |
| [API Strategy](api/01-api-strategy.md) | API development |
| [React Architecture](frontend/react-architecture.md) | Frontend development |
| [Spring Boot Upgrade Notes](spring-boot-4-upgrade-notes.md) | Dependency management |
| [P0 Issue Reports](review/issue-001-jwt-secret-hardening.md) | Recent fixes |
| [DEPLOY.md](../DEPLOY.md) | Docker deployment |
| [Technical Debt Roadmap](roadmap/technical-debt-roadmap.md) | Tech debt tracking |

### Tier 3 — Never Auto-Load (Historical / Stale)

| Category | Location | Reason |
|----------|----------|--------|
| Archive | `archive/` (224+ files) | Explicitly "not current truth" |
| Archived root-level docs | `roo-execution-log.md`, `roo-final-report.md`, `roo-gap-report.md`, `kilo-execution-summary.md`, `human-review-needed.md`, `documentation-gap-analysis.md`, `documentation-gap-report.md`, `final-project-status.md` | Superseded — see archive headers |
| Archived review reports | `review/autonomous-prompt-completion-matrix.md`, `review/05-architecture-evaluation.md`, `review/06-full-module-audit.md` | Contains critical drift (Vue 3, wrong module count) |
| Historical prompts | `prompts/*.md` | Previous orchestrator sessions |

---

## Document Categories

### Canonical Documents (Trust These)

| Document | Purpose |
|----------|---------|
| [AGENTS.md](../AGENTS.md) | Primary agent configuration |
| [.kilo/agents/main.md](../.kilo/agents/main.md) | Module boundaries and development rules |
| [Current System State](architecture/current/current-system-state.md) | What is implemented |
| [Current Module Status](architecture/current/current-module-status.md) | Module status |
| [Production Safety](production-safety.md) | Production startup checks |
| [Modulith Debt Register](modulith-debt-register.md) | Module boundary violations |
| [Known Limitations](review/known-limitations.md) | What is not production-ready |

### Blueprint Documents (Target State)

| Document | Purpose |
|----------|---------|
| [Platform Coordination Blueprint](architecture/blueprint/platform-coordination-blueprint.md) | **Coordination architecture** — PostgreSQL-backed job/task/barrier/recovery |
| [Domain Event & Outbox Blueprint](architecture/blueprint/domain-event-outbox-blueprint.md) | **Event architecture blueprint** — domain events, outbox, consumers, decoupling |
| [OTIO Render Platform Blueprint](architecture/blueprint/otio-render-platform-blueprint.md) | **Primary blueprint** — OTIO-first semantic rendering platform with Timeline Git |
| [Timeline Git Blueprint](architecture/blueprint/timeline-git-blueprint.md) | **Version control blueprint** — snapshot, patch, diff, merge, conflict, AI review |
| [Asset Ecosystem Blueprint](architecture/blueprint/asset-ecosystem-blueprint.md) | **Fifth pillar blueprint** — marketplace, search, asset ingestion, sharing |
| [OTIO + XMP + Asset Registry Placement Decision](review/otio-xmp-asset-registry-placement-decision.md) | Phase 1 placement: asset metadata, registry, XMP schema, governance, JSON-LD (2026-06-24) |
| [System Blueprint](architecture/blueprint/system-blueprint.md) | Target system architecture |
| [Render Blueprint](architecture/blueprint/module-blueprint-render.md) | Render pipeline target |
| [Security Blueprint](architecture/blueprint/module-blueprint-security-identity.md) | Security target |
| [Platform Composition](architecture/blueprint/platform-composition-blueprint.md) | Composition model |
| [Capability Opening](architecture/blueprint/capability-opening-blueprint.md) | Extension model |
| [Reference Architecture Map](architecture/blueprint/reference-architecture-map.md) | External reference projects (OTIO, Git, Perforce, Unity, Figma, etc.) |

Blueprints describe **target architecture**, not current implementation. They include "Reality Check" sections validated against code.

### Current State Documents

| Document | Purpose |
|----------|---------|
| [Current Timeline Git Status](architecture/current/current-timeline-git-status.md) | Implemented Timeline Git capabilities (2026-06-24) |
| [Current System State](architecture/current/current-system-state.md) | What is implemented |
| [Current Module Status](architecture/current/current-module-status.md) | Module status |

### Recommended Reading Order

For new agents or developers joining the project:

```
1. AGENTS.md                                  — Project overview, rules, safety constraints
2. Current System State                       — What is actually implemented
3. OTIO Render Platform Blueprint             — Primary architectural vision
4. Timeline Git Blueprint                     — Version control architecture
5. Current Timeline Git Status                — What is implemented in Timeline Git
6. Reference Architecture Map                 — What we learn from (OTIO, Git, Perforce, Unity, Figma)
7. Asset Ecosystem Blueprint                  — Marketplace, search, asset ingestion vision
8. Timeline Git Product Readiness             — Product maturity assessment
9. Architecture Re-Prioritization             — Strategic decisions (2026-06-24)
10. Known Limitations                         — What is not production-ready
```

### Historical Documents (Do Not Trust for Current State)

These documents have been archived with status headers. They contain stale facts and should not be used as reference:

- `roo-execution-log.md` — Superseded by project-intelligence-report
- `roo-final-report.md` — Superseded
- `roo-gap-report.md` — Superseded by June 13 audit
- `kilo-execution-summary.md` — Superseded
- `human-review-needed.md` — Phase 20 only
- `documentation-gap-analysis.md` — Superseded
- `documentation-gap-report.md` — Superseded
- `final-project-status.md` — Point-in-time snapshot
- `review/autonomous-prompt-completion-matrix.md` — Vue3-based
- `review/05-architecture-evaluation.md` — Vue 3 drift
- `review/06-full-module-audit.md` — Vue 3 drift

### Archived Documents

All documents in `archive/` (224+ files) are explicitly **not current truth**. See [archive/README.md](archive/README.md) for the archive index.

Root-level and review documents with `Status: Archived` headers are also historical — do not use as current reference.

### Governance Policies

| Policy | Purpose |
|--------|---------|
| [Document Lifecycle Policy](governance/document-lifecycle-policy.md) | Document states, transitions, creation/update/archival rules |
| [Document Validation Policy](governance/document-validation-policy.md) | How to validate docs against code, drift patterns to watch |
| [Agent Knowledge Policy](governance/agent-knowledge-policy.md) | What agents should/should not read, safety constraints |

---

## Documentation Structure

### architecture/blueprint/
Target architecture documents. These describe the **intended design**, not the current implementation.

### architecture/current/
Current system state documents. These describe **what is actually implemented** and validated.

### operations/
Runbooks and operational guides. These contain **commands and procedures** for running the system.

### review/
Review reports and validation results. Recent reports are **current truth**; older reports are **point-in-time snapshots**.

### roadmap/
Future work and planned improvements. These describe **what is not yet implemented**.

### archive/
Deprecated or historical documents. These are **not current truth** and should not be used as reference.

---

## Current Status (2026-06-22)

- **ModularityTest**: ✅ Re-enabled (2026-06-22)
- **Manual Preview**: ✅ READY
- **Staging Review**: ✅ READY
- **Production**: ⚠️ NOT READY (quota persistence, scheduler, observability gaps)

See [Known Limitations](review/known-limitations.md) for production blockers.

---

## Important Notes

> **Blueprint documents describe target architecture. Current state documents describe implemented reality.**

- H2 is unsupported (PostgreSQL only)
- Spring AI adapter exists but has compilation error; not in active runtime
- Quota module is in-memory only (no persistence)
- Automation/Plugin platform is blueprint only
- Capability opening documents describe a phased target model
- ADR-009 (Vue 3) is superseded — frontend is React 19
- See [Source of Truth Validation Report](review/source-of-truth-validation-report.md) for drift analysis
