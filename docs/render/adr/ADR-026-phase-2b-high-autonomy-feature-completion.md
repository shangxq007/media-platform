# ADR-026: Phase 2b High-Autonomy Feature Completion Mode

**Date:** 2026-07-01
**Status:** ACCEPTED
**Authority:** HERMES.7b

---

## Context

The media-platform project has completed Phase 1 (TL.0, PB.0, REV.0, Cloudflare publishing, review infrastructure). Subscription/token resources may expire. The user wants to prioritize feature completion over per-commit review.

The existing Phase 0-1 model required human review and push for every change. This is too slow for feature completion under resource constraints.

## Decision

Enable Phase 2b High-Autonomy Feature Completion Mode:

1. **Hermes** operates at Level 3 (Feature Coordinator / PR Creator)
2. **Coding agents** (OpenCode, Codex, Kilo Code, Claude Code, Kiro CLI) operate at Level 2 (Feature Branch Pusher)
3. Agents may commit, push feature branches, and create draft PRs for assigned TASK_IDs
4. Human review is deferred to feature milestones (VS.0), not per-commit
5. Review Packets are generated continuously by Hermes
6. Claude Code performs milestone-level architecture review
7. Human reviews accumulated changes before merge to main

## Consequences

### Positive
- Faster feature completion under resource constraints
- Agents can work autonomously on assigned tasks
- Review quality maintained at milestone level
- Clear permission boundaries prevent policy violations

### Negative
- Increased risk of architecture boundary violations between reviews
- Larger PRs at milestone review (more changes to review at once)
- Requires robust stop conditions to catch issues early

### Mitigations
- 12 stop conditions defined in `policies/stop-conditions.md`
- Review Packets generated per task for traceability
- Semgrep architecture rules catch violations early
- Claude Code architecture review at milestone
- Human final arbiter before merge

## Forbidden Assumptions

1. Phase 2b does NOT allow push to main
2. Phase 2b does NOT allow force push
3. Phase 2b does NOT allow auto merge
4. Phase 2b does NOT allow production deploy
5. Phase 2b does NOT bypass stop conditions
6. Phase 2b does NOT exempt agents from architecture constraints
