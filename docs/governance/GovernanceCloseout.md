# Engineering rules and guards consolidation — closeout

**Task:** `MEDIA_PLATFORM_ENGINEERING_RULES_AND_GUARDS_CONSOLIDATION_R1`
**Branch:** `task/engineering-rules-and-guards-consolidation-r1`
**Base main:** `6f995ce73756822e4c781dbe8a76060abc20be45` (tree `df9053ce06c821e8a74a49ae7fa22c6ed6b50efd`)
**Candidate:** the commit that contains this file; resolve with `git log -1 --format=%H`. Its tree is
recorded by the verification manifest, not restated here (a commit cannot contain its own SHA).
**Status:** committed, not merged, not pushed. Awaiting owner review.

## What changed and why

### 1. Agent instruction surface

| Surface | Before | After |
|---|---|---|
| `AGENTS.md` | 86 lines, partly duplicated by adapters | one authoritative file: precedence, current product facts, worktree discipline, authority rules, verification pointer, instruction-guard contract |
| `CLAUDE.md` | tool adapter with duplicated product orientation | thin Claude adapter: workflow context + verification pointer; defers to `AGENTS.md` |
| `.hermes.md` | 44 lines of stale tool facts (`Flyway V1-V4`, "32 checks", "37 submodules", "Testcontainers for all tests", module names that no longer exist, `Branch: main`, "CI: disabled", code-agent delegation) | tool adapter: toolchain, verification entry, working style; no architecture/status facts |
| `.kilo/agents/main.md` | 178 lines, declared by `docs/governance/agent-knowledge-policy.md` as Tier-0 always-load; contained its own module boundaries, a stale module list (`quota-billing-module`, `artifact-catalog-module`, `sandbox-runtime-module`), a mandatory `docs/roo-execution-log.md`, and a "modify files outside the workspace" prohibition that contradicts the worktree workflow | thin Kilo adapter: start-of-work, build/test, reporting, verification pointer |

Reduction is not the goal by itself; the goal is that an agent can start from one authority without
resolving contradictions. The always-loaded surface dropped from ~8 KB of mixed architecture and tool
config to one canonical file plus small adapters.

### 2. Instruction governance guard (new, fail-closed, self-tested)

`scripts/governance/check-instruction-governance.py` now inventories every loadable instruction
surface (`AGENTS.md` root and nested, `CLAUDE.md` root and nested, `.hermes.md`,
`.kilo/agents/*.md`), prints scope and audience, and fails on:

- missing precedence / conflict-stop declarations in the root file;
- an adapter that does not reference `AGENTS.md`;
- stale tool facts or independent authority statements in adapters
  (`Frozen Boundaries`, `Module Boundaries`, `Do not push to remote`,
  `Testcontainers PostgreSQL for all tests`, `Flyway V1-V4`, `H2 is permitted`);
- duplicated normative statements across `AGENTS.md` files;
- contradictory allow/deny rules across instruction surfaces.

`scripts/governance/test_check_instruction_governance.py` proves the guard rejects each real mutation
(7 cases: pass on current set, missing precedence, missing adapter deferral, stale push rule,
prohibited engine, contradictory testing rule, duplicated normative statement). Both are part of
`scripts/dev/verify guards`.

### 3. Architecture document authority

New CURRENT page: [current product execution paths](../architecture/current/current-product-execution-paths.md).
It states the eight facts the owner asked to be explicit:

1. First Render is an E2E scenario, not a production entry.
2. Ordinary render uses `POST /api/render-jobs`.
3. Incremental render and MCP use the same formal services.
4. C20 determinism, C21 cache hook, C22 incremental replanning are formal planning/binding capabilities.
5. The formal worker and Artifact Authority are the only execution-submission system.
6. Subtitle burn-in runs in the formal FFmpeg provider.
7. Only hard-subtitle burn-in exists today; multilingual subtitle management is future product work.
8. `render_submission_idempotency` is the formal submission idempotency table.

Corrected drift in `docs/testing/first-render.md` (retired `/api/first-render/plans` claim removed;
`POST /api/render-jobs` documented, worker font/FFmpeg requirement added),
`docs/testing/unified-execution-r1e.md`, `docs/architecture/current/execution-architecture-principles.md`
and `docs/development/canonical-baseline.md`. The pre-NLE consolidation record
`docs/development/legacy-work-consolidation.md` is marked **Archived / Superseded** with its
replacement named; its content was not rewritten. `architecture-entrypoint.md` links the new facts
page and labels the legacy record as historical.

### 4. Guard governance

- [current guard manifest](../architecture/governance/automated-guards/current-guard-manifest.md)
  records each guard, its entry, caller and the invariant it protects.
- Two `.hermes.md` string checks (`OpenCue: NOT_STARTED`, `Artifact DAG: POSTPONED`) were retired from
  `scripts/check-architecture-drift.sh`. The status facts are still asserted, now against the governed
  records: `architecture-assertions.md` FUT-001 and ADR-025. A tool adapter is no longer an authority.
- The H7/H8 guards, storage-identity placement counters, phase16/phase17 guards, jOOQ parity, OIDC
  and marketplace guards are unchanged and pass on the candidate.

### 5. Verification workflow

[`scripts/dev/verify`](../../scripts/dev/verify) now carries the full L1/L2/L3 definition:

- `preflight --level l1|l2|l3` — environment gate; L3 also checks the worker FFmpeg features and
  `FFMPEG_WORKER_FONT`;
- `l1 --module … --tests …` — affected unit/contract tests;
- `l2 --binding … --password-file … --classpath-file …` — the controlled PostgreSQL18 selection via
  the existing `scripts/test-infra pg18-local` launcher, failing closed when inputs are absent;
- `guards` — architecture drift, instruction governance (+self-test), jOOQ parity, OIDC bundle;
- `e2e` — the real product path.

Definitions, evidence rules and level selection: [docs/dev/verify.md](../dev/verify.md).

### 6. Test toolchain

- **JDK 25 proxy failure fixed (test-side).** `JooqArtifactCommitServiceProvenanceErrorContextTest`
  used `Proxy.newProxyInstance(DSLContext.class, …)`; JDK 25 builds a `<clinit>` of 66 819 bytes and
  throws `Code length 66819 is outside the allowed range`. The test now uses jOOQ's `MockConnection`
  and records executed SQL, which also keeps the original intent (no row-writing SQL before the
  provenance rejection) without generating a proxy for the large interface. No production Artifact
  logic changed.
- **The remaining 11 table-driven PostgreSQL tests** (`CONTROLLED_LAUNCH_REQUIRED`) cannot run under
  plain `./gradlew test` by design; they are now reachable through `verify l2`, which reuses the
  existing controlled launcher instead of a new runtime. Actual execution is **BLOCKED** in this
  environment: no controller-produced container binding is available, so the launcher correctly
  refuses to start. See the test matrix.
- **Artifact suite arithmetic:** 170 tests, 159 passed, 11 failed, 0 skipped, 0 errors; all 11
  failures are `ExceptionInInitializerError` → `IllegalStateException: CONTROLLED_LAUNCH_REQUIRED`.
  The previous 15 failures are accounted for: 4 proxy failures fixed, 11 routed to L2.

### 7. FFmpeg worker runtime contract

New [FFmpeg worker runtime contract](../dev/ffmpeg-worker-runtime-contract.md) documents the worker
binary path, probe path, font, sandbox and the required `drawtext`/`overlay`/`libx264`/`qtrle`
features, the fail-closed behaviour, GPL redistribution obligations and the hard-subtitle-only scope.
`preflight --level l3` checks the same features and the font. The host FFmpeg here lacks `libx264`;
the repository's managed validation FFmpeg is the supported worker runtime, which is now stated
explicitly instead of being rediscovered per task.

### 8. Small, authorized cleanups

- worker bean methods `firstRenderAdmissionEvidenceFactory` / `firstRenderGrantMinter` renamed to
  `mediaTaskAdmissionEvidenceFactory` / `mediaTaskGrantMinter` (no by-name injection or
  `@Qualifier`/`getBean` usage existed);
- `GraphFinalOutput` exception text no longer names First Render;
- the persisted confirmation prefix `first-render-release:` was deliberately **not** changed: it is a
  durable identity, not an execution path.
- `.gitignore` now excludes `__pycache__/` and `*.pyc` produced by guard scripts.

## Open items / owner decisions

1. **Protected document baseline drift (pre-existing).** `scripts/check-document-governance.sh` is
   already RED on merged `main`: DG-006 reports `api-contract.md` semantic hash
   `6c7a217d…` against pinned `f66f1019…`, and DG-011 reports 1 introduced broken link even when
   `--head` and `--base` are the same commit. This task did not modify `api-contract.md` and did not
   update a protected baseline. Resolving it requires explicit governance approval for the protected
   document, so it is reported, not "fixed" by rewriting the pin.
2. **L2 coverage** needs a protected controller/binding in the runner environment. Without it the 11
   controlled PostgreSQL tests stay unexecuted; the launcher fails closed rather than silently
   skipping.
3. **L3** was not run: governance-only change, and the host environment reports blockers
   (`libx264` absent on host FFmpeg, `FFMPEG_WORKER_FONT` unset, PG18/Temporal/OIDC ports closed).

## Scope check

Changed: instruction files, architecture/testing/development documents, guard scripts, verification
entry, one test class, two production identifiers and one exception message. Not changed: execution
kernel, frozen contracts, migrations, provider behaviour, deployment configuration, product features.
No merge to `main`, no push, no branch/worktree cleanup of historical work.
