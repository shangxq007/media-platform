# CLAUDE.md — media-platform tool adapter

This file is a Claude-specific adapter. The canonical repository instructions are in
[`AGENTS.md`](AGENTS.md); read and follow that file before using this adapter.
`AGENTS.md` governs architecture, API, security, domain, persistence, testing, deployment,
delivery, authority and conflict resolution. This file must not redefine those rules.

- Effective Owner decisions: [`docs/governance/OWNER_DECISIONS.md`](docs/governance/OWNER_DECISIONS.md). Read it before implementation; adapters do not redefine it.

## Claude workflow context

- One task per branch and worktree; never develop on canonical `main`.
- Before editing, inspect the applicable `AGENTS.md`, the current task record and the existing code
  and contracts you intend to reuse.
- Verification entry: [`scripts/dev/verify`](scripts/dev/verify) with
  `preflight`, `l1`, `l2`, `l3` and `guards`; definitions in [`docs/dev/verify.md`](docs/dev/verify.md).
  Run the narrowest relevant check first and record exact commands and results.
- In progress updates, state the next concrete verification step and surface blockers with the exact
  failing command.
- In the final response, report changed files, exact validation commands/results, limitations and
  whether the worktree is clean.

Repository orientation (CURRENT facts, not independent authority): this is the Media Capability
Platform; Render owns the render-job lifecycle, Artifact owns commit and fencing, the Timeline changes
only through Operation, and First Render is an E2E scenario rather than a production entry. Navigate
from [`docs/architecture/current/architecture-entrypoint.md`](docs/architecture/current/architecture-entrypoint.md).
