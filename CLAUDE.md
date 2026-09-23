# CLAUDE.md — media-platform tool adapter

This file is a Claude-specific adapter. The canonical repository instructions are in
[`AGENTS.md`](AGENTS.md); read and follow that file before using this adapter.
`AGENTS.md` governs architecture, API, security, domain, persistence, testing,
deployment, delivery, authority and conflict resolution. This file must not
redefine those rules.

## Claude workflow context

- Use one task per branch/worktree and keep implementation updates concise.
- Before editing, inspect the applicable `AGENTS.md` and the current task record.
- Prefer existing repository services, contracts and conventions after inspection.
- In progress updates, state the next concrete verification step and surface blockers promptly.
- In the final response, report changed files, exact validation commands/results,
  limitations and whether the worktree is clean.

The following product facts are orientation only and are not independent authority:
this repository is the Media Capability Platform; Product is the canonical
communication object, Timeline is the canonical editing model, StorageRuntime
owns physical materialization/checksum/storage references, ProductRuntime owns
Product lifecycle/metadata/dependency/query, and OpenCue is an execution
environment. Consult the source and canonical governance documents for current
semantics.
