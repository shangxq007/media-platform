---
status: canonical
created: 2026-06-27
scope: documentation
owner: chief-platform-architect
---

# Knowledge Architecture

## Five Documentation Layers

### Layer 0 — Project Identity
**Purpose:** Who are we? Where are we going?
**Contains:** Vision, Mission, Positioning, Philosophy, Roadmap, Non-goals
**Location:** `docs/identity/`

### Layer 1 — Platform Constitution
**Purpose:** What cannot change?
**Contains:** Architecture Freeze, Platform Constitution, Kernel Invariants, Stable SPIs, Evolution Policy, ADR Index
**Location:** `docs/architecture/` (constitution, kernel, governance, ADRs)

### Layer 2 — Project Handoff
**Purpose:** What is the current state?
**Contains:** Current Phase, Completed Milestones, Next Priorities, Known Constraints, Reading Guide
**Location:** `docs/handoff/`
**Read time:** < 10 minutes

### Layer 3 — Architecture Blueprint
**Purpose:** Why is the architecture designed this way?
**Contains:** Platform Kernel, Governance, Workflow, Public Capability, Component Descriptor, Timeline, Execution, Storage, Reference Architectures
**Location:** `docs/architecture/` (blueprints, design documents)

### Layer 4 — Implementation
**Purpose:** How is it implemented?
**Contains:** Runtime, Provider, Storage, Workflow, Frontend, Infrastructure, Testing, Operations, Review reports
**Location:** `docs/review/`, source code, tests

## Reading Order

| Role | Required | Optional | Skip |
|------|----------|---------|------|
| AI Agent | Layer 0 + 1 + 2 | Layer 3 | Layer 4 (unless implementing) |
| Human Developer | All layers | — | — |
| Architect | Layer 1 + 3 | Layer 0 + 2 | Layer 4 |
| Contributor | Layer 2 + 4 | Layer 3 | Layer 0 |
| Reviewer | Layer 1 | Layer 3 | Layer 4 |

## AI Agent Onboarding (< 15 minutes)

1. `AGENTS.md` — project overview, rules, safety constraints (2 min)
2. `docs/handoff/reading-guide.md` — what to read and why (3 min)
3. `docs/architecture/platform-constitution-v1.md` — frozen architecture (5 min)
4. `docs/handoff/current-phase.md` — current priorities and known constraints (5 min)
