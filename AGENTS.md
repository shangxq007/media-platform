# AGENTS.md — Canonical Repository Instruction Authority

This file is the canonical repository instruction authority for the repository root and all
descendants. It states durable, cross-module governance only. Task branches, candidate SHAs,
acceptance gates, evidence paths and runtime session details belong to the current owner task
record and its external evidence package, never here.

## Authority and precedence

1. System and developer instructions have highest precedence.
2. Explicit user instructions override repository instructions; the task-scoped user/owner instruction
   for the current task wins over durable repository guidance.
3. This root `AGENTS.md` is the canonical repository instruction authority.
4. A nested `AGENTS.md` applies only to its directory subtree. It may narrow or add scope detail,
   but it may not contradict, broaden or weaken this file's safety, security, API, domain, testing
   or delivery rules.
5. `CLAUDE.md`, `.hermes.md` and `.kilo/agents/*.md` are tool-specific adapters. They may describe
   tool or runtime usage and must defer to this file; they must not define architecture, API,
   security, domain, persistence, deployment or delivery authority.
6. Where instructions conflict, the higher-precedence source wins and the conflict is recorded.

Before work, read this file and every applicable nested `AGENTS.md` between the repository root and
the target path, plus the adapter for the tool in use. For governed or safety-sensitive work, record
the instruction inventory, scopes and precedence in task evidence.

If a user instruction conflicts with a higher-precedence system or developer instruction, follow the
higher one and record the conflict. If repository instruction files conflict with each other, stop implementation, record a governance finding with exact paths and statements, apply the higher-scope authority provisionally, and schedule instruction alignment before resuming feature work. Do not silently edit an instruction file to conceal an unresolved conflict.

## Effective Owner decisions and task authority

The current Owner decisions are indexed in
[`docs/governance/OWNER_DECISIONS.md`](docs/governance/OWNER_DECISIONS.md). That index is the
single durable decision register; this file and tool adapters reference it rather than duplicating it.

Before a governed or safety-sensitive task:

1. Read the effective Owner decisions and record the applicable decision IDs in the task evidence.
2. Read this `AGENTS.md` and every applicable nested `AGENTS.md`, plus the active tool adapter.
3. If an older architecture/governance document conflicts with a current Owner decision, report the
   exact conflict and follow the current decision; do not silently restore the older path.
4. Do not infer broader authority from a failed Agent run or from an old task record.

Task records contain the frozen Git baseline, authorized scope, required real gates, independent
review scope, and stop conditions. A task may not widen its own authorization.

## Current product and execution facts (CURRENT)

These are navigation facts; source, executable contracts and accepted ADRs remain the authority for
behaviour and semantics. Deeper detail lives in
[docs/architecture/current/architecture-entrypoint.md](docs/architecture/current/architecture-entrypoint.md).

- Formal full render submission is `POST /api/render-jobs`; incremental render submission is
  `POST /api/incremental-render/plans`. Both converge on one revision-pinned submission service.
- MCP media tools and the React render workbench call those same formal application services; they
  never re-derive planning, binding, provider or completion semantics.
- Refused legacy: `/api/first-render` probe routes are retired. First Render remains only an
  automation/E2E scenario exercised through the formal entries.
- `render_submission_idempotency` is the single formal render-submission idempotency table. Render
  owns the job lifecycle and final-output acceptance; Artifact owns commit/fencing; native Execution
  owns fenced attempts/completions; Temporal owns scheduling. No second execution authority exists.
- Timeline changes only through Operation. Revision CAS and identity consistency are mandatory.
- C20 determinism, C21 cache hook and C22 incremental replanning are formal planning/binding
  capabilities, not experiments.
- Subtitle burn-in is executed by the formal FFmpeg provider (hard subtitles only). Full multilingual
  subtitle management remains a future product capability.
- The current execution architecture is frozen: see
  [execution architecture principles](docs/architecture/current/execution-architecture-principles.md).

## Repository and worktree governance

Use one owned branch and one linked worktree per active task unless the owner explicitly authorizes
another topology. Never develop features directly on canonical `main`; keep `main` clean except
during explicitly authorized serialized integration.

Freeze an exact candidate commit and tree before verification; any later history change requires
renewed verification. Integrate accepted candidates by fast-forward only unless explicitly
authorized. Preserve dirty, unique or owner-created work before any cleanup or retirement.

Do not use `git reset --hard`, `git clean`, wildcard deletion, force branch deletion, destructive
batch cleanup, manual ref updates, rebases, squashes or cherry-picks unless the owner task explicitly
authorizes the operation and evidence proves preservation and scope.

Do not fetch, pull, push, publish, deploy or mutate remote refs unless explicitly authorized. Do not
modify PVE, GitOps, production configuration or production secrets during local foundation work.

Canonical `main` is the only long-lived branch. Remote history keeps only `main`. One agent writes at
a time; concurrent integration on one branch or worktree is forbidden.

## Architecture, API, security and domain authority

Source code, executable contracts, controller/DTO definitions, migration bytes, package metadata and
architecture guards are authoritative for implemented behaviour. Accepted ADRs and canonical-contract
documents are authoritative for accepted semantics. Generated diagrams, inventories and reports are
derived evidence, never authorities.

Do not modify the frozen platform kernel, stable SPI, Product, Timeline, ExecutionJob/ExecutionTask/
ExecutionCommand, execution lifecycle, StorageRuntime, ProductRuntime, ProducerRuntime or Flyway V1
semantics unless the owner task explicitly authorizes governance alignment for that authority.

Do not introduce Artifact Runtime, a new graph runtime, compatibility or shadow-authority paths,
provider-owned platform contracts, or provider/backend/environment/storage-provider identity in
public APIs. Public platform contracts may expose provider-neutral capability identity/version,
input/output contracts, supported media/assets, execution mode, summarized eligibility/availability,
estimated cost/quota, cancellation/retry characteristics and Application/Template Workflow
compatibility. Provider IDs, manifests, ExecutionBackend and WorkerRuntime identities, worker nodes,
internal registry topology and provider-specific configuration schemas remain internal unless a
separate owner decision authorizes a distinct internal/admin contract.

The Capability Registry is the authority for capability registration and internal provider
resolution. Applications and Template Workflows depend only on platform Capability Contracts and
never redefine provider/backend semantics.

Every API endpoint must enforce canonical authenticated tenant/workspace scope. Free-form tenant or
workspace identifiers are never an authority override. Never expose signed URLs, local filesystem
paths, credentials or private environment dumps in public responses or evidence.

## Change-scope discipline

Keep each task inside its authorized scope. Do not expand a documentation, governance, test or
feature task into unrelated cleanup. Do not modify production source, tests, build files, application
configuration, database migrations or runtime behaviour unless explicitly authorized. Do not weaken,
disable, bypass or relabel tests to pass a gate. Prefer existing services and contracts after
inspecting the current code.

## Verification and delivery

Use the repository verification entry [scripts/dev/verify](scripts/dev/verify) and its L1/L2/L3
levels; the definitions live in [docs/dev/verify.md](docs/dev/verify.md). Run `preflight` before heavy
or integration work and do not start full E2E when a required environment precondition is absent.

- L1: affected compile, targeted unit tests and affected guards.
- L2: PostgreSQL/provider/Artifact/application integration tests through the controlled launcher.
- L3: real API, Temporal, Worker, FFmpeg, browser and media-result end-to-end.

Record commit, tree, command, exit code, tool versions and log locations. Never call cached or reused
results fresh; label overlapping evidence. Governance-only changes must not claim product or runtime
validation.

Before finalizing a governed task, record current branch, HEAD, tree, worktree status, applicable
instruction inventory, exact changed paths, commands/results and the final candidate SHA and parent.
Stop at explicit blockers, destructive or irreversible actions, unresolved authority conflicts or
missing owner decisions.

## Evidence and secrets

Do not place credentials, secrets, private environment dumps or hidden reasoning in repository files
or evidence. Evidence must be sufficient to verify scope, hashes, commands and outcomes without
unrelated private data.

## Nested instruction files and adapters

Nested `AGENTS.md` files are valid only when their scope is explicit from their path and their content
narrows this authority without contradiction. A missing nested file means the root rules apply. Do not
create nested instruction files for completeness. Retired instruction content must stay in history or
be retained as clearly marked governance evidence, recording why it moved and what replaced it.

Tool adapters (`.hermes.md`, `.kilo/agents/*.md`, `CLAUDE.md`) carry only tool/runtime mechanics and
short repository orientation. They must point at this file instead of restating normative rules.
Historical task reports and dated review documents are not instruction sources.

## Instruction governance guard

The repository maintains a fail-closed inventory and conflict check over every tracked instruction
surface: `AGENTS.md`, `CLAUDE.md`, `.hermes.md` and `.kilo/agents/*.md`. The guard reports each file's
scope and audience and fails when precedence declarations are missing, an adapter redefines authority,
or contradictory normative rules appear. It runs through
[scripts/governance/check-instruction-governance.py](scripts/governance/check-instruction-governance.py)
and must be part of `scripts/dev/verify guards`. It is a governance check only and must not inspect or
execute product runtime behaviour.
