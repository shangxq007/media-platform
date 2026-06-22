# Platform Documentation

> **Last Updated:** 2026-06-22

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
| Historical reviews | `review/01-06-*.md` | Point-in-time snapshots |
| Historical prompts | `prompts/*.md` | Previous orchestrator sessions |
| Stale overviews | `overview/` (pre-2026-06-22 versions) | Superseded by fixed versions |

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
