# Document governance and guard simplification — resolution

**Task:** `MEDIA_PLATFORM_DOCUMENT_GOVERNANCE_AND_GUARD_SIMPLIFICATION_R1`
**Branch:** `task/engineering-rules-and-guards-consolidation-r1` (continued from `dad62373`)
**Base main:** `6f995ce7` (tree `df9053ce`), unchanged during investigation
**Final candidate:** the commit that contains this file (`git log -1`); tree recorded by the verify manifest
**Status:** committed candidate, merged only after all gates below pass

## A. DG-006 — protected semantic body hash

### Root cause

`STALE_BASELINE`.

`scripts/document-governance/check_semantic_bodies.py` pinned 18 protected documents against
`protected-document-baseline.json`. The `api-contract.md` entry pinned `f66f1019…` with
`baseline_commit = 1af5ceb8`. Commit `e7cea1cb` ("govern repository instruction authority") added the
section *Composition Foundation Public Boundary (2026-09-23)* to that contract, producing body hash
`6c7a217d…`, but did not refresh the protected hash in the same commit. Every later run therefore
reported `PROTECTED_BASELINE_CHANGE`, including on untouched `main`.

Verified by computing the same body hash (front matter stripped) at each commit:

| Commit | Body hash |
|---|---|
| `1af5ceb8` (pin origin) | `f66f1019…` (matches the pin) |
| `d1f85392` | `f66f1019…` |
| `e7cea1cb` (adds the section) | `6c7a217d…` |
| `6f995ce7` (main) | `6c7a217d…` |
| `dad62373` (prior candidate) | `6c7a217d…` |

The guard was also **not commit-bound**: it ignored `--head`/`--base` and read the working tree, so its
result depended on local dirtiness rather than on the commit under test.

### What the original check protected

That a `CANONICAL_ACCEPTED` contract document cannot change silently: any body change must be an
explicit governance act with recorded evidence. The invariant is still valuable, so it was **kept**,
not retired.

### Minimal fix

1. `check_semantic_bodies.py` now resolves `--head` to a commit and reads each protected body from
   `git show <commit>:<path>`; a missing body is a violation (`PROTECTED_DOCUMENT_MISSING`) and an
   invalid ref fails closed (`INVALID_HEAD_REF`).
2. The `api-contract.md` baseline entry was refreshed **with adjudication evidence**, matching the
   existing `schema-intent-contract` precedent:
   - `previous_semantic_body_sha256: f66f1019…`
   - `baseline_commit: e7cea1cb…`
   - `governance_change_id: COMPOSITION_FOUNDATION_AUTHORITY_ALIGNMENT_20260923`
   - `semantic_reason`: the accepted owner decision added the provider-neutral boundary section, and
     the hash was not refreshed in that commit.
   - `governance_decision: docs/architecture/governance/composition-foundation-authority-decision.md`

### Why this does not weaken the API contract

- The added section came from the same commit as an `ACCEPTED` owner decision and change-ledger rows
  (`composition-foundation-authority-decision.md`, `…-alignment-change-ledger.tsv`, both added by
  `e7cea1cb`), so it is an already-adjudicated governance alignment, not a new contract choice.
- Its content restates the existing repository rule (provider-neutral public boundary; provider IDs,
  manifests, `ExecutionBackend`, `WorkerRuntime` remain internal), which `AGENTS.md` and the
  instruction-governance guard already enforce.
- Executable surfaces support the statement: `media-capability-contracts` explicitly excludes provider
  choice from its transport vocabulary, and the architecture drift guard keeps the formal render
  entries converged on one submission authority.
- The expected hash was **not** copied from "actual" to make a gate green: the refresh carries the
  previous hash, the governance change id, the reason and the decision record, so a reviewer can audit
  when and why it changed.

### Verification

- Positive: `check_semantic_body_guard` passes at `HEAD`, `6f995ce7`, `dad62373` and `e7cea1cb`
  (18/18 documents).
- Negative: a controlled repository whose protected body changes fails with
  `PROTECTED_BASELINE_CHANGE`; an invalid ref fails with `INVALID_HEAD_REF`
  (`scripts/document-governance/test_document_governance_guard.py`).

## B. DG-011 — broken-link increment

### Root cause

`INTENDED_RULE` implemented as a working-tree debt comparison with **no commit semantics**.

`check_markdown_links.py` ignored `--head`/`--base`, scanned the working-tree `docs/**.md`, and
submitted every broken link whose identity key was absent from `broken-link-debt-baseline.json` as
`INTRODUCED_BROKEN_LINK`. Therefore `--head == --base` still failed whenever the tree contained any
broken link not in the debt list. The reported link was real:

```
docs/architecture/governance/source-of-truth/architecture-authority-model.md:86
target: ../../../scripts/governance/check-instruction-governance.py
resolved: docs/scripts/governance/check-instruction-governance.py   (missing)
```

It was introduced by `e7cea1cb` and never added to the debt baseline. The guard's "introduced" wording
was also wrong: it measured *unrecorded current* links, not links introduced by a change.

### Change

1. `check_markdown_links.py` now computes the broken-link set of the exact `--head` and `--base`
   commits from Git objects (`ls-tree` + `show`), checks link targets against the file set of that
   commit, and reports
   `introduced = broken(head) − broken(base) − accepted_debt`.
   Missing/invalid refs fail closed (`INVALID_DIFF_INPUT`).
2. The genuinely broken link was repaired to the correct depth
   (`../../../../scripts/governance/check-instruction-governance.py`).

### Verification (controlled repository, 10 cases)

| Case | Expected | Result |
|---|---|---|
| same commit | pass | pass (0 introduced) |
| later commit with a valid new link | pass | pass |
| commit adding a broken link | fail | fail `INTRODUCED_BROKEN_LINK` |
| same broken commit | pass | pass |
| unrelated file change | pass | pass (no false positive) |
| invalid ref | fail | fail `INVALID_DIFF_INPUT` |

On the real repository: `--head 6f995ce7 --base e7cea1cb~1` correctly reports the introduced link;
`--head dad62373 --base 6f995ce7` reports none; full document governance is 16/16 PASS.

## C. Guard simplification

Only checks actually invoked from `scripts/dev/verify`, `scripts/check-architecture-drift.sh`,
`scripts/check-document-governance.sh`, instruction governance and CI were considered.

| Original check | Action | Protected invariant | Replacement / evidence | Verification |
|---|---|---|---|---|
| `.hermes.md` `OpenCue: NOT_STARTED` string | RETIRE (prior round) | deferred capability status | drift guard now reads `architecture-assertions.md` FUT-001 | PASS |
| `.hermes.md` `Artifact DAG: POSTPONED` string | RETIRE (prior round) | deferred capability status | drift guard now reads ADR-025 | PASS |
| JGit assertions `OPTG-26`, `OPCG-13/14`, `RCG-38/39` | MERGE | no JGit in `render-module`/`platform-app` production sources | one `HAS_JGIT` scan; all three rule ids keep their own PASS/FAIL line | drift PASS, 0 failures |
| Credential residue `CIPG1`, `CIP2EG1` | MERGE | zero credential residue | one `git grep` count shared by both rule ids | drift PASS |
| Colour primaries `CIPG5/7`, `CIP2G13` | MERGE | `UNSPECIFIED` ≠ `UNKNOWN` retained | one file inspection shared by both rule ids | drift PASS |
| 219 per-check PASS lines on every guard run | OUTPUT_SIMPLIFIED | failure attribution preserved | `--quiet` prints failures only plus `ARCHITECTURE_DRIFT: PASS|FAIL (N checks)`; `verify guards` uses it | PASS/FAIL and count unchanged |
| DG-006 pinned hash read from working tree | FIX + baseline adjudication | protected canonical contract body | head-bound read + governance-evidenced hash refresh | 16/16 PASS |
| DG-011 working-tree debt scan | FIX | no newly broken links | ref-based diff (base→head) + repaired link + 10-case self-test | 16/16 PASS |

Not changed: H7/H8 authority guards, storage identity/placement counters, phase 16/17 guards,
marketplace guard, jOOQ parity, OIDC bundle, commit-scope and metadata guards. They protect current
invariants and passed unchanged. Canonical `api-contract.md`, Flyway V1, execution kernel, provider
behaviour and the persisted `first-render-release:` identity were **not** modified.

## D. Cost and maintainability

- Duplicate scans removed inside `check-architecture-drift.sh`: 3 JGit scans → 1, 2 credential
  `git grep` scans → 1, 2 colour-primaries inspections → 1. The credential scan measured 0.13 s; no
  percentage saving is claimed because no token/CPU metering was performed.
- `verify guards` no longer emits 219 PASS lines: one summary line, failures printed in full. The
  architecture-drift check count (219) and all FAIL semantics are unchanged.
- No heavy service (PG18, Temporal, browser) is started by `verify guards` or `verify l1`.
- Document governance is available as one entry (`verify docs --base main`) and is commit-bound, so
  the same command gives the same answer regardless of local uncommitted files.
- Core negative coverage increased: instruction-governance 7 mutation cases, document-governance 10
  controlled cases.

## Open items carried forward

- `ARTIFACT_CONTROLLED_TESTS: BLOCKED` — the 11 `CONTROLLED_LAUNCH_REQUIRED` tests still need a
  protected PG18 controller binding; no host security change was made.
- Full L3 product E2E was not re-run: this task changes governance tooling, documentation and guard
  logic only.
