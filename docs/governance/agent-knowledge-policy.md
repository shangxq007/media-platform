# Agent Knowledge Loading Policy

> **Last Updated:** 2026-06-22  
> **Purpose:** Define what documents AI agents should and should not read

## Knowledge Tiers

### Tier 0: Always Load (Every Agent, Every Session)

| Document | Size | Trust | Purpose |
|----------|------|-------|---------|
| `AGENTS.md` | ~3KB | 9.5/10 | Project overview, priorities, safety constraints |
| `.kilo/agents/main.md` | ~5KB | 9.5/10 | Module boundaries, development rules |

**Load method:** Inject into system prompt  
**Total context:** ~8KB  
**Risk:** None — fully verified against code

### Tier 1: Load by Context

| Document | Context | Trust |
|----------|---------|-------|
| `docs/architecture/current/current-system-state.md` | Architecture work | 7/10 |
| `docs/architecture/current/current-module-status.md` | Module changes | 7/10 |
| `docs/modulith-debt-register.md` | Boundary changes | 8/10 |
| `docs/production-safety.md` | Production config | 9/10 |
| `docs/review/known-limitations.md` | Before implementing | 7/10 |
| `docs/review/project-intelligence-report.md` | Context loading | 8.5/10 |
| `docs/architecture/04-frontend-architecture.md` | Frontend work | 9/10 |
| `docs/render/overview.md` | Render module | 8/10 |
| `docs/billing-access/access-control-overview.md` | Billing work | 8/10 |

**Load method:** Agent reads on first relevant task  
**Total context:** ~40KB  
**Risk:** Low — mostly verified

### Tier 2: Load on Demand

| Document | When |
|----------|------|
| `docs/operations/flyway-baseline-runbook.md` | DB migration tasks |
| `docs/operations/flyway-migration-guide.md` | Adding new migrations |
| `docs/operations/ci-verification-guide.md` | CI/debugging tasks |
| `docs/operations/gitops-staging-deploy-runbook.md` | Deployment tasks |
| `docs/render/adr/ADR-001` through `ADR-007` | Render provider decisions |
| `docs/architecture/07-architecture-decisions.md` | Architecture decisions |
| `docs/api/01-api-strategy.md` | API development |
| `docs/frontend/react-architecture.md` | Frontend development |
| `docs/review/issue-001-*` through `issue-003b-*` | Recent P0 fixes |
| `DEPLOY.md` | Docker deployment |
| `docs/roadmap/technical-debt-roadmap.md` | Tech debt tracking |

**Load method:** Agent reads when task requires specific domain knowledge  
**Risk:** Low — domain-specific

### Tier 3: Never Auto-Load

| Category | Files | Reason |
|----------|-------|--------|
| Archive | `docs/archive/*` (224+ files) | "not current truth" |
| Archived root-level | Files with `Status: Archived` header | Superseded |
| Archived review | `docs/review/01-06-*.md`, point-in-time reports | Historical |
| Historical prompts | `prompts/*.md` | Previous orchestrator sessions |
| Chinese docs | `docs/zh/*.md` | Use English canonical docs instead |
| Stale blueprints | Blueprints without reality checks | May mislead |

**Load method:** Only on explicit user request  
**Risk:** High — may contain stale facts

## Agent-Specific Rules

### Planner Agent

- **Always:** Tier 0
- **By Module:** Tier 1 (architecture, module status, known limitations)
- **On Demand:** Tier 2 (roadmaps, tech debt)
- **Never:** Tier 3

### Coder Agent

- **Always:** Tier 0
- **By Module:** Tier 1 (module-specific docs)
- **On Demand:** Tier 2 (production safety, known limitations)
- **Never:** Tier 3

### Tester Agent

- **Always:** Tier 0
- **By Module:** Tier 1 (known limitations)
- **On Demand:** Tier 2 (CI guide, production safety)
- **Never:** Tier 3

### Reviewer Agent

- **Always:** Tier 0 + `docs/modulith-debt-register.md`
- **By Module:** Tier 1 (module boundaries)
- **On Demand:** Tier 2 (ADR docs)
- **Never:** Tier 3

### Architect Agent

- **Always:** Tier 0
- **By Module:** Tier 1 (all)
- **On Demand:** Tier 2 (all blueprints, ADRs, roadmaps)
- **Never:** Tier 3

## Safety Constraints

All agents must遵守以下约束：

- Do not modify the Flyway V1 baseline (`V1__init_full_schema.sql`)
- Do not introduce H2 database (PostgreSQL only)
- Do not enable Spring AI active runtime
- Do not add `spring-modulith-starter-insight`
- Do not weaken `ProductionSafetyValidator`
- Do not commit real secrets or credentials
- Do not automatically merge pull requests
- Do not deploy to production automatically
- `ModularityTest` must remain enabled
