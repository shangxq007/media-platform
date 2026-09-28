# AGENTS.md — Canonical Repository Instruction Authority

This is the canonical repository instruction source for `/home/user/Documents/workspace/projects/media-platform` and all descendants. It defines durable repository governance for coding agents. Task-specific commands, acceptance gates, branch names, worktree paths, commit SHAs, queues, evidence locations, and runtime session details belong in the current owner task record and external evidence package, not here.

## Authority and precedence

1. System and developer instructions have highest precedence.
2. Explicit user/owner instructions for the current task override repository instructions.
3. This root `AGENTS.md` is the canonical repository instruction authority.
4. A nested `AGENTS.md` applies only to its directory subtree. It may narrow or add requirements within that scope, but it may not contradict, broaden, or weaken this file's authority, safety, security, API, domain, testing, or delivery rules.
5. `CLAUDE.md` files are tool-specific adapters. They may contain only tool-facing workflow guidance and repository context that does not define or redefine architecture, API, security, domain, testing, persistence, deployment, or delivery authority. Where present, they defer to the applicable `AGENTS.md`.
6. More-specific nested instructions take precedence over less-specific repository instructions only for additional, non-conflicting scope detail.

Before work, inspect this root file and every applicable nested `AGENTS.md` from the repository root to the target path. Inspect applicable `CLAUDE.md` adapters when the tool provides them. Record the inventory, scope, conflicts, and precedence in task evidence for governed or safety-sensitive work.

Explicit user instructions override repository instructions. If a user instruction conflicts with a higher-priority system or developer instruction, follow the higher-priority instruction and record the conflict. If repository instruction files conflict with one another, stop implementation, record a governance finding with exact paths and statements, apply the higher-scope/root authority provisionally, and schedule explicit instruction alignment before resuming feature work. Do not silently edit an instruction file to hide an unresolved conflict.

## Repository and worktree governance

Use one owned branch and one linked worktree per active task unless the owner explicitly authorizes another topology. Do not perform feature development directly in canonical `main`; keep canonical `main` clean except during explicitly authorized serialized integration.

Freeze an exact candidate SHA before verification. Any commit or history change after verification requires renewed verification. Integrate accepted candidates through fast-forward only unless explicitly authorized. Preserve dirty, unique, or owner-created work before cleanup or retirement.

Do not use `git reset --hard`, `git clean`, wildcard deletion, force branch deletion, destructive batch cleanup, manual ref updates, rebases, squashes, or cherry-picks unless the current owner task explicitly authorizes the operation and evidence proves preservation and scope.

Do not fetch, pull, push, publish, deploy, or mutate remote refs unless explicitly authorized. Do not modify PVE, GitOps, production configuration, or production secrets during local foundation work.

## Branch and worktree discipline

### Single active branch

At most one active task branch and one linked worktree exist at a time. A task branch must end either fast-forwarded into `main` or deleted on task completion; orphaned worktrees are not permitted.

### Main as the only long-lived branch

All work branches from `main` and all completed work merges back into `main`. Long-lived feature branches are not supported; `main` is the single canonical integration line.

### Task lifecycle

Each task follows create worktree, do the authorized work, integrate or discard, and clean up. Record the candidate SHA and tree when freezing, and retire the linked worktree with normal Git commands once the outcome is recorded.

### Remote branch policy

GitHub keeps only `main`. Branches whose content is integrated are deleted on the remote promptly, and periodic audits keep the remote branch count at one. Remote mutations require the same explicit authorization as any other push.

### Serialization

One agent operates at a time, or explicitly coordinated multi-agent work is required. Owners must not run concurrent integration on the same branch or worktree.

## Architecture, API, security and domain authority

Source code, executable contracts, controller/DTO definitions, migration bytes, package metadata, and architecture guards are authoritative for implemented behavior. Accepted ADRs and canonical-contract documents are authoritative for accepted semantics. Generated diagrams and reports are derived evidence, not authorities.

Do not modify frozen platform kernel, stable SPI, Product, Timeline, ExecutionJob/ExecutionTask/ExecutionCommand, execution lifecycle, StorageRuntime, ProductRuntime, ProducerRuntime, or Flyway V1 semantics unless the owner task explicitly authorizes governance alignment for that authority.

Do not introduce Artifact Runtime, a new graph runtime, compatibility or shadow-authority paths, provider-owned platform contracts, or provider/backend/environment/storage-provider identity in public APIs. Public platform contracts may expose provider-neutral capability identity/version, input/output contracts, supported media/assets, execution mode, summarized eligibility/availability, estimated cost/quota, cancellation/retry characteristics, and Application/Template Workflow compatibility. Provider IDs, manifests, ExecutionBackend and WorkerRuntime identities, worker nodes, internal registry topology, and provider-specific configuration schemas remain internal unless a separate explicit owner decision authorizes a distinct internal/admin contract.

The Capability Registry is the authority for capability registration and internal provider resolution. Applications and Template Workflows depend only on platform Capability Contracts and never redefine provider/backend semantics.

All API endpoints must enforce canonical authenticated tenant/workspace scope. Do not accept free-form tenant or workspace identifiers as authority overrides. Never expose signed URLs, local filesystem paths, credentials, or private environment dumps in public responses or evidence.

## Change-scope discipline

Keep each task within its authorized scope. Do not expand a documentation, governance, test, or feature task into unrelated cleanup. Do not modify production source, tests, build files, application configuration, database migrations, or runtime behavior unless explicitly authorized. Do not weaken, disable, bypass, or relabel tests to pass a gate. Prefer existing services and contracts after inspecting the current code.

## Testing, verification and delivery

Run checks appropriate to the authorized change and record exact commands and results. Use machine-readable reports where available and verify total/passed/failure/error/skipped arithmetic. Do not call cached results fresh; label reused or overlapping evidence. Governance-only changes must not claim product or runtime validation.

Before finalizing a governed task, record current branch, HEAD, tree, worktree status, stash state, applicable instruction inventory, exact changed paths and scope, commands/results, final candidate SHA and parent, and post-integration state when integration is authorized. Stop at explicit blockers, destructive/irreversible actions, unresolved authority conflicts, or missing decisions; continue independent non-blocked work only when safe.

## Evidence and secrets

Do not place credentials, secrets, private environment dumps, or hidden reasoning in repository files or evidence. Evidence must be sufficient to verify scope, hashes, commands, and outcomes without unrelated private data.

## Nested instruction files

Nested `AGENTS.md` files are valid only when their scope is explicit from their path and their content narrows this authority without contradiction. A missing nested file means the root rules apply. Do not create nested instruction files merely for completeness. Retired instruction files must remain in history or be retained as clearly marked governance evidence; record why retired content moved, its replacement authority, affected tools, and scopes.

## Instruction governance guard

The repository must maintain an automated check over every tracked `AGENTS.md` and `CLAUDE.md`. The guard must fail when it finds duplicate normative repository rules, conflicting public API exposure rules, contradictory provider/backend authority rules, inconsistent testing or delivery rules, or missing precedence declarations. It must also report the complete file inventory and scopes. The guard is a governance check only and must not inspect or execute product runtime behavior.
