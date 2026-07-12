# Source of Truth — Control Plane Update

**Date:** 2026-07-01
**Authority:** DOCSYNC.1

---

## Authority Levels

### Level 1: Canonical Architecture

| Document | Path | Authority |
|----------|------|-----------|
| AGENTS.md | `AGENTS.md` | Highest — agent behavior rules |
| Current System State | `docs/architecture/current/current-system-state.md` | Architecture state |
| Production Safety | `docs/production-safety.md` | Safety constraints |
| Blueprint | `docs/architecture/blueprint/otio-render-platform-blueprint.md` | Architecture vision |
| ADRs | `docs/render/adr/ADR-*.md` | Architecture decisions |

### Level 2: Control Plane Policy

| Document | Path | Authority |
|----------|------|-----------|
| Feature Completion Mode | `policies/feature-completion-mode.md` | Phase 2b mode |
| Agent Permissions | `policies/agent-permissions.md` | Permission levels |
| Push Policy | `policies/push-policy.md` | Push rules |
| PR Policy | `policies/pr-policy.md` | PR rules |
| Stop Conditions | `policies/stop-conditions.md` | Stop conditions |
| Architecture Constraints | `policies/architecture-constraints.md` | Architecture rules |

### Level 3: Review/Execution Artifacts

| Document | Path | Authority |
|----------|------|-----------|
| Review Packets | `reports/review-packets/*.md` | Per-task review |
| Hermes Reports | `reports/hermes/*.md` | Control plane reports |
| Dashboard State | scribe.cc.cd | Live status |
| Task Reports | `reports/media-platform/*.md` | Task outcomes |

### Level 4: Visual/Derived

| Document | Path | Authority |
|----------|------|-----------|
| LikeC4 Maps | `docs/architecture/maps/*.likec4` | Visual reference |
| Exported Diagrams | `docs/architecture/diagrams/` | Visual reference |
| Cloudflare Dashboard | scribe.cc.cd | Live visualization |

---

## Conflict Resolution

When documents conflict:
1. Level 1 (Canonical Architecture) takes precedence
2. Level 2 (Control Plane Policy) governs agent behavior
3. Level 3 (Review/Execution) is operational detail
4. Level 4 (Visual/Derived) is reference only

---

## Update Triggers

| Trigger | Documents to Update |
|---------|-------------------|
| New ADR | AGENTS.md, blueprint, current-system-state |
| Phase change | All Level 1 + Level 2 |
| New agent | agent-permissions.md, tool-roles.md |
| Architecture boundary change | architecture-constraints.md, production-safety.md, Semgrep rules |
| New task type | feature-completion-mode.md, stop-conditions.md |
