---
status: canonical
created: 2026-06-27
scope: documentation
owner: chief-platform-architect
---

# Reading Guide

## Minimal Reading Set (AI Agents — 15 min)

1. `AGENTS.md` — Project overview, rules, constraints
2. `docs/architecture/platform-constitution-v1.md` — Frozen architecture, 10 invariants, 7 SPIs
3. `docs/handoff/current-phase.md` — Current priorities

## Recommended Reading (Human Developers — 2 hours)

1. Everything in Minimal Reading Set
2. `docs/architecture/platform-kernel.md` — Complete kernel inventory
3. `docs/architecture/platform-governance.md` — B2/B2.1 governance blueprint
4. `docs/architecture/workflow-platform.md` — Workflow + capability composition
5. `docs/architecture/public-capability-architecture.md` — Public capability model

## Architecture Path (Architects — 1 day)

1. Everything in Recommended Reading
2. All ADRs (`docs/architecture/adr/`)
3. `docs/architecture/component-descriptor.md`
4. `docs/architecture/execution-environment.md`
5. `docs/review/` — Architecture validation reports

## Capability Implementation Path

1. Layer 0 + 1 + 2
2. `docs/architecture/public-capability-architecture.md`
3. Source: `render-module/.../domain/producer/Producer.java` (SPI)
4. Source: `render-module/.../domain/execution/` (Execution model)
