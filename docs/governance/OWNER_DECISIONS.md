# Owner Decisions — Effective Authority Index

> **Status:** ACTIVE. This is the single index of current Owner decisions.
> Historical architecture documents do not automatically override these decisions.
> Task-specific scope and exceptions live in the current Owner task record, not here.

| ID | Decision | Status | Scope | Exception / condition |
|---|---|---|---|---|
| OD-001 | Canonical Timeline is the only editing authority. | ACTIVE | Product, Web, Agent, MCP, rendering | None. |
| OD-002 | Timeline changes only through canonical Operation. | ACTIVE | All editing entry points | Migration/repair tooling requires explicit Owner approval. |
| OD-003 | Revisions preserve CAS, idempotent replay and deterministic identity. | ACTIVE | Operation apply, Revision persistence | No silent retry or non-CAS write. |
| OD-004 | BMF is the preferred provider for new media-graph processing. | ACTIVE | New audio/video graph capabilities | Existing FFmpeg functionality is preserved; no implicit fallback. |
| OD-005 | Formal path is RenderPlanning → PhysicalPlanning → ProviderBinding → Temporal → Provider → Artifact/Delivery. | ACTIVE | All render features | No parallel RenderJob or independent mix task. |
| OD-006 | AudioMix is formal domain semantics, not a browser/provider-local configuration. | ACTIVE | Timeline, Preview, BMF | Provider may compile it, not redefine it. |
| OD-007 | No compatibility layer is added solely for pre-production historical data. | ACTIVE | Domain/API evolution | Owner-approved migration is the exception. |
| OD-008 | One task has one writing Agent and a bounded, explicit scope. | ACTIVE | Task execution | A separate read-only reviewer is allowed. |
| OD-009 | Git integration, push and protected-work cleanup require explicit Owner authorization. | ACTIVE | Repository/worktrees/remote | Fast-forward only unless explicitly authorized. |
| OD-010 | Independent review and real E2E evidence are required for product acceptance. | ACTIVE | Product tasks | A review session with no verdict is not APPROVE. |
| OD-011 | Embedded video audio participates only through an explicit Canonical AudioRoute. | ACTIVE | Multi-video timelines | No implicit mixing of all video sources; explicit route avoids double-counting. |
| OD-012 | Canonical composition uses a versioned, fixed-point, resolution-independent space. Canonical Operation authors aspect and transform. Render requests specify a separate compatible output raster. | ACTIVE | Timeline, NLE preview, planning and native projection | Unsupported composition, raster and effect semantics fail closed. |

## Change control

- IDs are stable and never reused for a different decision.
- Superseding a decision requires a new ID and an explicit `SUPERSEDES` note.
- Agents must report conflicts with this index instead of silently choosing an older document.
- Product/API/security/database exceptions require an explicit Owner decision; an Agent may not
  self-authorize them.
