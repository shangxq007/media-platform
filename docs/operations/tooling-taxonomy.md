# Tooling Taxonomy — media-platform

**Date:** 2026-07-01
**Status:** ACTIVE
**Authority:** TOOLING.0

---

## Tooling Layers

| Layer | Name | Purpose |
|-------|------|---------|
| L1 | Canonical Knowledge | Architecture decisions, system state, safety rules |
| L2 | Architecture Map | Visual/derived architecture diagrams |
| L3 | Control Plane | Task orchestration, policies, reports |
| L4 | Agent Execution | Code implementation by AI agents |
| L5 | Review & Governance | PR review, code quality, architecture compliance |
| L6 | Runtime / Infra | Execution environments, deployment, cloud |
| L7 | Verification / Operations | Smoke checks, deployment verification |
| L8 | Maintenance / Security | Dependency updates, secrets, scanning, observability |

---

## L1 Canonical Knowledge

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| AGENTS.md | ACTIVE | Agent behavior rules | Human | Read only |
| Blueprint | ACTIVE | Architecture vision | Human | Read only |
| current-system-state | ACTIVE | Current architecture state | Hermes (update) | Read only |
| production-safety | ACTIVE | Safety constraints | Human | Read only |
| ADRs | ACTIVE | Architecture decisions | Human | Read only |

**Source of truth:** YES — these are canonical.

---

## L2 Architecture Map

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| LikeC4 | ACTIVE | Architecture diagrams as code | Hermes | Read/Update |
| exports/html | ACTIVE | Static site | Hermes | Build/Publish |
| exports.json | ACTIVE | JSON export | Hermes | Build/Publish |
| Cloudflare arch map | ACTIVE | Published architecture map | Hermes | Publish |

**Source of truth:** NO — visual/derived only.

---

## L3 Control Plane

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| Hermes | ACTIVE | Task orchestration | Self | Full |
| tasks/ | ACTIVE | Task definitions | Hermes | Read/Write |
| policies/ | ACTIVE | Policy files | Hermes | Read/Write |
| state/ | ACTIVE | Runtime state | Hermes | Read/Write |
| reports/ | ACTIVE | Execution reports | Hermes | Read/Write |
| Review Packet | ACTIVE | Per-task review context | Hermes | Generate |

**Source of truth:** Execution evidence, not canonical architecture.

---

## L4 Agent Execution

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| OpenCode | ACTIVE | Code implementation | Hermes (assign) | Level 2 |
| Codex | ACTIVE | Code implementation | Hermes (assign) | Level 2 |
| Claude Code | ACTIVE | Code + architecture review | Hermes (assign) | Level 2/0 |
| Kilo Code | ACTIVE | Code implementation | Hermes (assign) | Level 2 |
| Aider | ACTIVE | Pair programming | Hermes (assign) | Level 1-2 |
| OpenHands | AVAILABLE | Container-based coding | Hermes (assign) | Level 1 |
| CrewAI | AVAILABLE | Multi-agent orchestration | Hermes (assign) | Level 1 |
| Kiro CLI | AVAILABLE | AWS-backed coding | Hermes (assign) | Level 2 |

**Agent rules:**
- Work in assigned worktrees only
- No push main, no force push, no merge
- No production secrets, no Flyway V1
- Generate Review Packet on completion

---

## L5 Review & Governance

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| GitHub PRs | ACTIVE | Code review | Human | Create draft |
| CODEOWNERS | ACTIVE | Review ownership | Human | Read |
| PR template | ACTIVE | PR structure | Human | Read |
| Semgrep | ACTIVE | Architecture rules | Hermes | Run |
| Branch protection | ACTIVE | Main protection | Human | Read |
| Claude Code review | ACTIVE | Architecture review | Hermes | Level 0 |

---

## L6 Runtime / Infra

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| OpenCue | AVAILABLE | Render farm (ExecutionEnvironment) | Human | Read only |
| PVE / Proxmox | PLANNED | VM/container hosting | Human | Verify only |
| Dokploy | PLANNED | Deployment platform | Human | Verify only |
| Cloudflare R2/Pages | ACTIVE | Report/dashboard hosting | Hermes | Publish |
| OpenTofu | PLANNED | IaC | Human | None |
| Ansible | PLANNED | Configuration management | Human | None |

---

## L7 Verification / Operations

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| PVE Verification Agent | PLANNED | Preview runtime verification | Hermes | Level 1 |
| Cloud Verification Agent | PLANNED | External endpoint probing | Hermes | Level 1 |
| AWS Lambda | PLANNED | Cloud verification only | Human | None |
| Health checks | PLANNED | Service health | Hermes | Run |
| Smoke checks | ACTIVE | Local smoke tests | Agents | Run |

---

## L8 Maintenance / Security / Observability

| Tool | Status | Purpose | Owner | Agent Allowed |
|------|--------|---------|-------|---------------|
| Renovate | PLANNED | Dependency governance | Human | None |
| pre-commit | PLANNED | Local guardrails | Human | None |
| SOPS/age | PLANNED | Secret-at-rest handling | Human | None |
| OpenTelemetry | PLANNED | Traces/metrics/logs | Human | None |
| Fluent Bit | PLANNED | Lightweight node/worker log forwarding | Human | None |
| Fluentd | DEFERRED | Unified logging aggregator (evaluate after OBS.0) | Human | None |
| Trivy/Grype | PLANNED | Container/dependency scanning | Human | None |

---

## Source of Truth Hierarchy

| Level | Source | Authority |
|-------|--------|-----------|
| 1 | AGENTS.md | Highest |
| 2 | current-system-state.md | High |
| 3 | production-safety.md | High |
| 4 | Blueprint | High |
| 5 | ADRs | High |
| 6 | LikeC4 | Visual/derived |
| 7 | Dashboard | Display/derived |
| 8 | Reports | Execution evidence |
| 9 | Review Packets | Review artifacts |
| 10 | Agent outputs | Not authoritative until integrated |

---

*Generated by Hermes Agent — TOOLING.0*
