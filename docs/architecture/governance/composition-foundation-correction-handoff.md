# Composition Foundation Correction Handoff

This correction binds composition reads and writes to `CanonicalActorResolver` plus `WorkspaceQueries`. A requested workspace is only a selection; Identity verifies the active tenant membership and rejects missing, inactive, cross-tenant, stale, or ambiguous membership results.

Composition drafts and validation snapshots use `CompositionRepository` and the Flyway schema. Draft writes use a revision predicate. Published values are copied into `composition_version`, whose database trigger rejects update and delete, while publish uses a transaction and a conditional draft transition.

Validation now intersects requested execution modes with capability modes and continues to fail closed for missing or unavailable capabilities, contract mismatches, assets, entitlements, malformed graph edges, cycles, and invalid ranges. The public contract remains provider-neutral. No provider, backend, worker, or topology identity is serialized.

This acceptance correction adds deterministic typed issue codes and paths for port/contract compatibility, cardinality, unreachable and terminal graph nodes, parameter schema/default/range checks, application execution-mode checks, and version-range checks. Resource checks now go through `CompositionResourceAuthority`, which delegates to the canonical artifact and entitlement authorities and enforces tenant/workspace scope, media/kind constraints, entitlement decisions, and quota estimates. Production composition services do not use placeholder asset or entitlement sets.

Application draft reopening restores the backend document, including selected workflows, requirements, execution modes, lifecycle and revision. Publish-readiness uses the application validator and rechecks referenced workflows and authoritative resources. The frontend exposes capability selection, application reopening, asset and entitlement editing, quota estimates, validation results, and 401/403/409 recovery.

Fresh evidence for this correction (2026-09-23) is stored under `/home/user/Documents/workspace/audit-runs/COMPOSITION_FOUNDATION_CORRECTION_20260923/`. The frozen correction commit is `aaba6bbbbb4b0b359b47faa401898f983459938b` with tree `16377310f463b80652e13e28508eb65fb9bf8d48`, and the checkout is clean. The hermetic PostgreSQL-backed `:composition-module:test` suite passed; the frontend clean `npm ci` suite passed 314/314, including 4 fresh composition tests (310 reused tests, 0 overlapping additions beyond the fresh file); typecheck, lint, and build passed. The API governance gate passed Spectral, runtime artifact validation, controller/runtime/base/candidate operation and schema consistency, pinned oasdiff additive comparison, and the intentional-breaking fixture. The checked-in runtime export contains 16 composition paths, 18 composition operations, and the composition schemas. The architecture drift guard passed against the committed tree, and the instruction-governance guard passed. Semgrep was NOT_RUN because no approved `semgrep` executable is installed. PostgreSQL was run as a disposable hermetic local runtime, never replaced by mocks.

The historical frontend 252 passed / 58 failed result was reproduced before the clean environment flag; all 58 failures had the same Node webstorage collision (`localStorage` absent). Re-running the exact clean suite with `NODE_OPTIONS=--no-experimental-webstorage` yielded 314/314; no assertions were weakened. The runtime exporter is `scripts/api/export-openapi.sh`; it validates the live `/v3/api-docs` export before publication and the consistency verifier fails closed on undocumented or extra controller operations and required schemas.

The architecture guard must be run against a committed candidate tree because H7 binds exact Git blobs; the pre-commit worktree run reports the expected `CompositionService.java` bound-tree mismatch. No production execution, external provider, or deployment validation was performed.

The frontend composition route is workspace-bound, passes the session-selected workspace to the catalog, handles retired sessions and 401/403 responses, and is reachable from both the existing developer route and `/w/{workspaceId}/composition`.

PVE, Temporal, Storage, FFmpeg/BMF, external providers, worker topology, production configuration, deployment, and real execution remain NOT_RUN.

# R1–R3 correction handoff — 2026-09-24

This dedicated correction worktree freezes candidate `417a6960682dfae4bac73ffe6923cfa710bd212a` with tree `39abac803c6eb86f88ba1754eb187b30b240ea35`, based on approved candidate `285b8a93fa7114b6285ea6eea3eb2c986a9102ed`. The worktree is clean after this handoff commit. Canonical `main`, `origin/main`, deployment configuration, PVE, GitOps, production data, and external systems were not modified.

The three remaining blockers are addressed:

* R1: `frontend/src/pages/compositionEditors.tsx` is the explicit platform issue-location mapping. Node, port, binding, parameter, output, asset, entitlement, entry, workflow, and application targets are real focusable controls with stable `data-editor-id` values. Unknown targets focus a keyboard-accessible recovery paragraph and never fall back to `document.body`. Six frontend tests cover all supported locations plus unknown recovery.
* R2: `WorkflowEntry` is a typed domain field on `TemplateWorkflow`; `CompositionService` preserves it for drafts and published versions, Jackson persistence restores it, the runtime OpenAPI export includes `WorkflowEntry`, and `CompositionValidator` rejects missing, unknown, upstream, or incompatible entry contracts. Tests cover missing-entry single-node, valid single-node, invalid target, disconnected graph, valid two-step chain, missing output, and invalid terminal/output behavior.
* R3: `contracts/composition/version-range-v1.json` is the platform grammar and `version-range-cases.json` is the conformance set. Java and TypeScript load the same canonical files. Exact versions, `1.x`, `1.*`, `1.2.x`, `1.2.*`, inclusive/exclusive bounds, contradictory ranges, malformed expressions, unsupported operators, prerelease/build metadata, and overflow boundaries are covered. The grammar is published in `docs/api/composition-version-range.md` and the OpenAPI candidate extension.

Changed paths are exactly those shown by `git diff 285b8a93fa7114b6285ea6eea3eb2c986a9102ed --name-status`; no migration was required because the entry is part of the existing JSON definition column and old drafts intentionally validate as missing-entry until edited.

Evidence commands and results:

* `XDG_RUNTIME_DIR=/tmp/codex-runtime scripts/test/podman-hermetic.sh run ./gradlew --no-daemon --console=plain :composition-module:test --no-build-cache`: PASS. 51 tests: Access 2, Foundation 11, VersionRange 33, Repository 5; PostgreSQL persistence, rollback, restart/reconstruction, concurrent publish, stale revisions, and immutable versions included.
* Clean frontend `npm ci --no-audit --no-fund`; `NODE_OPTIONS=--no-experimental-webstorage npm test -- --run`: PASS, 36 files / 349 tests. Fresh correction additions: 39 (6 navigation/recovery and 33 range cases); reused historical candidate tests: 310; overlap with prior candidate evidence: 310. These populations are reported separately and are not added together.
* `npm run typecheck`, `npm run lint -- --quiet`, `npm run build`: PASS. Build output was restored so the worktree remains clean.
* `scripts/api/export-openapi.sh http://127.0.0.1:8080 docs/api/openapi-preview-current.json`: PASS against the locally running application; runtime export had 563 operations and includes `WorkflowEntry`. `python3 scripts/api/verify-composition-openapi.py`: controller/runtime/base/candidate each 18 composition operations; required schemas present.
* `OASDIFF_BIN=/home/user/Documents/workspace/projects/media-platform-composition/scripts/tools/oasdiff bash scripts/check-api-contract-governance.sh`: PASS, Spectral and pinned oasdiff 1.28.0 including intentional-breaking fixture.
* `TREE=$(git rev-parse HEAD^{tree}); H7_SOURCE_TREE="$TREE" bash scripts/check-architecture-drift.sh`: PASS against the exact frozen tree. `python3 scripts/governance/check-instruction-governance.py`: PASS.
* Semgrep: NOT_RUN. Neither an approved `semgrep` executable nor `python3 -m semgrep` is available in this environment.

Explicit NOT_RUN boundaries remain PVE, Temporal, Storage/provider execution, FFmpeg/BMF, external providers, worker topology, production configuration/data, GitOps, deployment, image publication, and real execution. The candidate is not acceptance-approved; the original independent reviewer must independently re-review and close R1, R2, and R3.

## R1 display-name navigation correction handoff — 2026-09-24

The original independent R1 finding was limited to validation navigation for the Application display-name field: backend location `application:displayName` focused the generic validation fallback instead of the real editor control. R2 (`WorkflowEntry`) and R3 (canonical version-range grammar) were already independently closed and remain preserved.

Correction worktree: `/home/user/Documents/workspace/projects/.worktrees/composition-r1-display-name-20260924`, based on candidate `64fc5a9bc4e2e25590f08fec5205287f2b852c8e` (tree `f81e8b9b2fe8d47dc52147919883b9b6c8060d90`).

Changed files:

* `frontend/src/pages/compositionEditors.tsx` — maps Application issues whose typed object is `application` and whose location/path is the exact `application:displayName` form or its canonical dotted/path equivalent to the stable target `application:displayName`. Unrelated workflow or application fields remain on their existing mappings.
* `frontend/src/pages/CompositionFoundationPage.test.tsx` — adds a regression that activates an `application:displayName` issue and asserts the real input is focused; extends navigation coverage for entry and workflow targets while retaining node, binding, parameter, port/output, asset, entitlement, and unknown fallback assertions.

The Application editor is the focusable input with `data-editor-id="application:displayName"`. Navigation activates the application editor, scrolls the target into view, and leaves `document.activeElement` on that input. Unknown locations alone use `data-editor-id="validation-fallback"`.

Verification:

* Fresh focused run: `NODE_OPTIONS=--no-experimental-webstorage npm test -- --run src/pages/CompositionFoundationPage.test.tsx` — PASS, 1 file / 7 tests.
* Fresh full frontend run after `npm ci --no-audit --no-fund`: `NODE_OPTIONS=--no-experimental-webstorage npm test -- --run` — PASS, 36 files / 350 tests.
* Fresh frontend typecheck: `npm run typecheck` — PASS.
* Fresh frontend lint: `npm run lint -- --quiet` — PASS.
* Fresh frontend build: `npm run build` — PASS; generated static output was restored and is not part of this correction.
* Fresh architecture drift guard: `TREE=$(git rev-parse HEAD^{tree}); H7_SOURCE_TREE="$TREE" bash scripts/check-architecture-drift.sh` — PASS.
* Fresh instruction-governance guard: `python3 scripts/governance/check-instruction-governance.py` — PASS.

Test accounting is separate: 350 fresh test executions in this correction, 1 newly added test, 349 executions overlapping the prior candidate population, and 0 reused results claimed as fresh evidence. Counts are not summed across overlapping categories. No backend composition contract or generated artifact changed, so backend composition tests and OpenAPI/Spectral/oasdiff were not rerun for this frontend-only correction; prior approvals remain evidence. Semgrep is `NOT_RUN` because neither an approved executable nor `python3 -m semgrep` is available.

PVE, Temporal, Storage/provider execution, FFmpeg/BMF, external providers, worker topology, production configuration/data, GitOps, deployment, image publication, and real execution remain `NOT_RUN`.

R2 and R3 were preserved and not reopened. The worktree is clean after the correction commit. Request independent re-review from the original reviewer; delivery remains blocked until R1 is independently closed while the prior R2/R3 approvals are retained.
