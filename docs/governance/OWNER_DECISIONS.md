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
| OD-013 | Authored composition aspect is a reduced exact positive rational. Presets resolve to that value, and a verified source display aspect can initialize it as a fixed value. | ACTIVE | Timeline, NLE, render admission | Preview and output require an exact compatible raster. A canonically valid ratio can have no supported output. |
| OD-014 | Agent, MCP, frontend, external applications and Workflow consume stable semantic Query and Operation contracts. Generic invocation retains domain-specific semantics. | ACTIVE | Semantic interface direction | This decision does not authorize broad interface implementation. |
| OD-015 | BMF is preferred for multi-capability media analysis as well as rendering. | ACTIVE | Media analysis direction | Future analysis uses shared decode, safe frame reuse, independent sampling, typed observations, independent result commitment and selective reuse. |
| OD-016 | Platform-owned Workflow semantics coordinate Query, Analyze, Generate, Plan, Apply, Render, Evaluate, Review and Delivery. Temporal provides durable execution infrastructure. | ACTIVE | Workflow direction | Workflow definitions and native execution graphs retain distinct boundaries. |
| OD-017 | Future Parameter Channels, keyframes, automation and Auto Reframe build on exact composition and authored temporal semantics. | ACTIVE | Dynamic parameter direction | Scope and implementation require a separate Owner task. |
| OD-018 | Use one PostgreSQL application database with domain schemas, one write owner for each business state, one Flyway chain and one jOOQ generator. Preserve cross-domain local transactions. | ACTIVE | Persistence and execution | API, Worker, migration and read-only access have distinct privileges. |
| OD-019 | Codex freezes and pushes the task candidate. ChatGPT independently reviews the remote source and retained evidence. | ACTIVE | Candidate delivery | Owner acceptance and integration require separate authorization. Add no new machine architecture gates at this stage. |
| OD-020 | Preserve the canonical applied V1 and upgrade it through V2. Transition the known historical V1 into an independently initialized target in isolation. | ACTIVE | G2.2 database lineages | Match both catalog and history. Shared rollout requires separate authorization. |
| OD-021 | Binding evaluates exact capability support and interoperability across the plan, then applies a deterministic versioned policy. Prefer BMF among legal choices and honor explicit restrictions. | ACTIVE | #22, Restore, Worker Fabric | A replacement Worker must execute the pinned implementation and distribution. Provider substitution requires a new binding. |
| OD-022 | High-frequency media analysis prefers local pretrained models. External API capabilities complement the same platform execution foundation. Optimize sharing, fusion and sampling only under preserved semantics and result ownership. | ACTIVE | Media Analysis Foundation and performance measurement | Model weights, versions, licenses and hardware remain open. OD-015 governs observations and independent result commitment. |
| OD-023 | Extensible creative definitions give each Transition, Effect and Animation a platform-owned semantic identity, version, typed contract and capability requirements. | ACTIVE | NLE Core R1 | Authored instances pin the definition. Definition discovery and UI availability are separate from executable capability. |
| OD-024 | Unified parameter channels bind an instance identity and a definition-owned parameter key to its typed constant or authored dynamic value. | ACTIVE | Temporal editing | OD-017 and the existing exact time and interpolation contracts govern future keyframes, tracking and procedural sources. |
| OD-025 | Declarative creative compositions combine versioned primitives and parameters into reusable effects and transitions. | ACTIVE | Creative composition | Presets configure primitives. Composition describes creative meaning and providers implement it through the existing execution foundation. |
| OD-026 | Creative Packs distribute declarative presets, resources and typed compositions, with separately admitted executable implementations where required. | ACTIVE | Creative distribution | A pack update preserves references and meaning in existing revisions. |
| OD-027 | Creative semantic conformance requires that each implementation reproduce platform-defined parameter, geometry, timing and interpolation semantics. | ACTIVE | Provider admission | OD-021 governs feasibility and selection. Unsupported semantics require explicit rejection. |

## Change control

- IDs are stable and never reused for a different decision.
- Superseding a decision requires a new ID and an explicit `SUPERSEDES` note.
- Agents must report conflicts with this index instead of silently choosing an older document.
- Product/API/security/database exceptions require an explicit Owner decision; an Agent may not
  self-authorize them.
