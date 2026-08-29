# Roadmap 22 Phase 19 — Render Module FFmpeg Zero-Awareness Correction

## Record identity

- Record type: append-forward post-publication correction candidate
- Operating mode: `APPEND_FORWARD_HISTORY + CLEAN_FORWARD + FAIL_CLOSED + MINIMUM_CHANGE`
- Base commit: `923273758810195a22e4109cf145977bb7f3e970`
- Base tree: `1ce5c695aac3b43d3478c212650176960b72db41`
- Historical Phase 19 candidate retained: `55ffa1b1cced47fb8807961fed1b985b063bd13b`
- Historical publication retained: `923273758810195a22e4109cf145977bb7f3e970`
- Enclosing correction candidate SHA/tree: derived from the Git commit containing this record; this document does not persist a moving branch tip.
- Created: `2026-08-29T06:09:33+08:00`

No historical commit, evidence file, approval, or publication record is amended, rebased, squashed, reverted, or rewritten by this correction.

## Frozen architecture decision

`RENDER_MODULE_CONCRETE_RUNTIME_PROVIDER_NEUTRALITY_V1`

> `render-module` must not know that FFmpeg is a concrete runtime/provider.
>
> Render owns semantic/render WHAT.
>
> FFmpeg Provider owns FFmpeg-specific HOW.
>
> Provider/runtime conformance infrastructure may know FFmpeg.

The required mechanical target is:

`RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0`

The target covers production source, tests, fixtures, resources, Gradle tasks, concrete dependencies, binary discovery, command/argv/filter mechanics, process invocation, configuration, provider identities, compatibility aliases, generated tracked paths, and hidden generic aliases that merely rename concrete FFmpeg/JavaCV/libass authority.

## Explicit approval decision

- Decision: `APPROVAL=PASS`
- Decision source: explicit user instruction in the active correction session
- User statement: `审批可以补上通过吗？我决定通过审批`
- Recording rule: this is a current append-forward approval for the strengthened correction. It is not backdated and does not alter the historical Phase 19 publication evidence.
- Scope authorized by the decision: continue verification, independent review, candidate freeze, controlled branch publication, and fast-forward-only canonical integration when every stated gate is green.

## Inventory and CLEAN FORWARD accounting

The corrected base detector found 5,177 unique category/path/line/pattern tuples across 311 initial finding paths. The path ledger preserves those tuple counts and adds supplemental candidate-scope entries so every changed path is classified.

Authoritative ledgers:

- `roadmap-22-phase-19-render-zero-awareness-clean-forward-path-ledger-v2.json`
- `roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json`

Final ledger invariants:

- Initial finding paths: 311
- Initial raw finding tuples: 5,177
- Total ledger entries: 417
- Final candidate-scope paths: 364
- Supplemental candidate-scope paths: 106
- Duplicate paths: 0
- Unclassified paths: 0
- Unexplained KEEP: 0
- Candidate-scope uncovered paths: 0
- Stale unexplained finding paths: 0

One initial broad `filtergraph-api` match in the GStreamer provider surface is explicitly reclassified as provider-neutral/non-FFmpeg; it is not represented as a candidate change or as retained FFmpeg awareness.

## Historical functional capability accounting

The last meaningful pre-migration Render-owned FFmpeg baseline was mechanically derived as:

- Commit: `c058e187cfbb2fdb8037aca21ae333a7df27a4bb`
- Tree: `7629f79e7025e3dabd4ed19278f6de147e229892`
- Migration commit: `b626f2eb4fd462ada1db018720e8a06bbed61883`
- Migration tree: `6f0b46a2deee2f631ad462b9bc8f9f0716f4438a`

The 45-entry capability ledger reconciles as:

- Migrated to FFmpeg provider: 3
- Migrated to provider runtime infrastructure: 7
- Migrated to provider conformance: 3
- Retained provider-neutral Render semantics: 7
- Replaced by new architecture: 7
- Deleted as proven dead: 2
- Deleted as proven duplicate: 2
- Deferred with explicit bounded justification and not claimed supported: 14
- Currently claimed supported: 27
- Full parity: 3
- Parity via new architecture: 24
- Claimed-supported `PARTIAL`, `NO`, or `UNKNOWN`: 0
- Missing: 0
- Unclassified: 0
- Unexplained deletion: 0

The primary-disposition partition is mechanically complete:

- `CAPABILITY_DISPOSITION_RECONCILIATION=PASS`
- `CAPABILITY_DISPOSITION_RECONCILIATION_DELTA=0`
- `DUPLICATE_PRIMARY_DISPOSITION_COUNT=0`
- `UNACCOUNTED_CAPABILITY_COUNT=0`

The four entries outside `CURRENTLY_CLAIMED_SUPPORTED=27` plus
`DEFERRED=14` are not inferred or silently absorbed:

- `HARDCODED_DURATION_REPORTING` → `DELETED_AS_PROVEN_DEAD`
- `PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT` → `DELETED_AS_PROVEN_DEAD`
- `DUPLICATE_RENDER_PROBE_STACK` → `DELETED_AS_PROVEN_DUPLICATE`
- `SHADOW_LOCAL_RENDER_RUNNER` → `DELETED_AS_PROVEN_DUPLICATE`

Independent Gate B audit verdict: `PASS`, blockers 0.

The 14 deferred entries do not authorize BMF, cross-runtime effect conformance, Phase 20, OpenCue implementation, Roadmap 23, or FAOF-3.

## Implementation disposition

The correction:

1. Removes concrete Render-owned FFmpeg/JavaCV/libass execution, binary, command, configuration, build-task, fixture, compatibility, and shadow-test surfaces.
2. Renames only provider-neutral semantic WHAT families, including baseline effect, basic render, and baseline transition domain types.
3. Deletes concrete local-render and subtitle-provider shadow authority instead of preserving compatibility wrappers.
4. Preserves typed timed-text and render requirements while making provider execution fail closed with `TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED`.
5. Removes hidden aliases introduced by an initially rejected broad replacement, including `subtitle renderer`, `remote-provider`, fake `provider` executables/identities, `provider-subtitle renderer`, compound `PROVIDER_PROVIDER` identifiers, concrete probe field ownership, and command-plan aliases.
6. Distinguishes symbolic backend category `provider` from provider identity. Symbolic category values cannot resolve a registry bean or execute concrete machinery without separate typed-plugin binding.
7. Replaces the old command-plan draft alias with an opaque typed-provider request that carries no argv, filter graph, or concrete command HOW.
8. Preserves independent non-FFmpeg providers and their own explicit mechanics; this correction does not broaden its authority beyond the FFmpeg boundary.

## Rejection and repair chain

The first independent final review correctly returned `FAIL` with four blocker groups:

1. FFmpeg/JavaCV identities collapsed into an invented `provider` identity.
2. FFmpeg/libass authority hidden behind `remote-provider` and `subtitle renderer` aliases.
3. The guard missed aliases and underscore-separated `libass` tokens.
4. Compound identifiers such as `provider-subtitle renderer` were malformed and semantically false.

The rejected candidate was not committed, pushed, or integrated. Repairs were TDD-driven and expanded the mutation matrix from 66 to 90. Additional full-suite RED findings were repaired without weakening tests:

- 171 initial Render failures from replacement collisions;
- 66 residual backend-category/identity fixture failures;
- 13 null-provider ClientExport failures after provider identity became explicitly unresolved.

Every repair was followed by focused tests, clean compilation, the zero-awareness guard, and complete Render/full-repository reruns.

## Final mechanical zero-awareness result

```text
RENDER_MODULE_FFMPEG_PRODUCTION_REFERENCE_COUNT=0
RENDER_MODULE_FFMPEG_TEST_REFERENCE_COUNT=0
RENDER_MODULE_FFMPEG_BUILD_TASK_COUNT=0
RENDER_MODULE_FFMPEG_BINARY_DISCOVERY_COUNT=0
RENDER_MODULE_FFMPEG_COMMAND_OR_ARGV_COUNT=0
RENDER_MODULE_FFMPEG_PROCESS_INVOCATION_COUNT=0
RENDER_MODULE_FFMPEG_CONFIG_COUNT=0
RENDER_MODULE_FFMPEG_FIXTURE_SCRIPT_COUNT=0
RENDER_MODULE_TO_FFMPEG_PROVIDER_DEPENDENCY_COUNT=0
RENDER_MODULE_FFMPEG_COMPATIBILITY_SURFACE_COUNT=0
RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0
RENDER_MODULE_CONCRETE_FFMPEG_ZERO_AWARENESS=PASS
```

- Guard mutation count: 90
- Guard mutation result: PASS
- Hidden-alias mechanical scan result: 0 findings
- Staged diff whitespace check: PASS
- Unstaged candidate paths at gate time: 0

## Context-aware governance placeholder gate

The historical capability identifier
`PLACEHOLDER_SIMPLE_PROVIDER_OUTPUT` is opaque structured identity, not an
unresolved prose value. Placeholder validation is therefore context-aware:

- the capability-ledger exception is limited to
  `capabilities[*].CapabilityKey` in the exact capability ledger;
- the repeated historical value in the reconciliation is allowed only for the
  exact reconciliation artifact, the two declared JSON path shapes, and the
  exact historical identifier value;
- no global token allowlist exists;
- unresolved values in other fields, paths, and artifacts fail closed.

Final result:

- Placeholder tests: 21/21 PASS
- Negative controls: 50/50 PASS, including `TODO`, `TBD`, bare
  `PLACEHOLDER`, `<fill-me>`, and masked `***` values
- `PLACEHOLDER_NEGATIVE_CONTROL=PASS`
- `PLACEHOLDER_GATE=PASS`

## Test-surface change accounting

The historical Phase 19 report recorded 8,138 tests (the task's 8,139 value
was approximate), 999 suites, and 45 modules. That aggregate is not
reproducible from a fresh detached worktree at the same exact SHA. A fresh
same-SHA, same-command run produced 8,024 tests, 964 suites, and 44 modules.
The old aggregate exceeded the reproducible source surface by 114 tests and
35 suites; a polluted canonical build directory independently reproduced one
stale 14-test compatibility module, proving the non-hermetic XML aggregation
mechanism. The historical aggregate is not used as the behavioral denominator.

The reproducible comparison is:

- Fresh base `923273758810195a22e4109cf145977bb7f3e970`: 8,024 tests
- Candidate: 7,929 tests
- Non-Render modules: 5,098 → 5,098, delta 0
- Render: 2,926 → 2,831, delta -95
- Render testcase multiset: 312 removed identities, 217 added identities,
  net -95
- Provider-neutral planner renames: 144 removed / 144 added, net 0
- Whole-class retired/migrated surface: 97
- Modified same-class assertions: 71 removed / 73 added, net +2
- Equation: `8024 - 97 + 2 + 0 = 7929`

The 97 whole-class removals partition exactly as:

- moved to provider tests: 16
- moved to provider runtime conformance: 11
- moved to provider conformance: 4
- deleted with explicitly retired concrete subtitle/libass implementation: 48
- duplicate shadow-local-runner tests removed: 18

Every group names its replacement tests and capability-ledger keys in
`roadmap-22-phase-19-test-surface-change-accounting-v1.json`.

- `UNEXPLAINED_TEST_COUNT_REDUCTION=0`
- `UNEXPLAINED_BEHAVIORAL_TEST_LOSS=0`

## Static-analysis target accounting

The same Semgrep 1.175.0 binary and the same seven-rule repository config were
run against base and candidate with `--json --time` target extraction:

- Base targets: 3,722
- Candidate targets: 3,697
- Removed targets: 92
- Added targets: 67
- Net: -25
- Provider-neutral rename pairs: 49 source / 49 target
- Deleted concrete or retired surfaces: 43
- Added guard/test/governance targets: 18
- Findings and scanner errors: 0 on both sides
- `SEMGREP_TARGET_DELTA_EXPLAINED=YES`

## Validation evidence

Final local validation against the corrected candidate content:

- Render module JUnit: 2,831 tests; 2,826 passed; 0 failures; 0 errors; 5 skipped.
- Full serial repository JUnit: 44 modules; 957 suites; 7,929 tests; 7,900 passed; 0 failures; 0 errors; 29 skipped.
- Full serial duration: 21 minutes 57 seconds; 200 Gradle tasks executed.
- FFmpeg provider module: PASS.
- Provider plugin runtime module: PASS.
- Platform distribution tests: PASS.
- Dual-distribution plugin digest verification: PASS.
- FFmpeg plugin digest produced by final targeted build: `df496276e7a087431d9e5ded07163d92d2ccacaede2c0250fb9f8d9ea0319c30`.
- PFIRR1 jOOQ fail-closed mutation proof: PASS.
- PFIRR1 remediation check: PASS.
- Full production/test compilation with rerun tasks: PASS.
- Spring Modulith test: PASS.
- Platform bootJar: PASS; 89 tasks executed.
- Architecture drift: PASS; 42 checks, 0 failed, plus all extended governance guards green.
- Change-impact classifier mutation matrix: PASS; 19 cases, 15 workflow mutations, 7 governance mutations, 1 classifier mutation.
- Semgrep contract matrix: PASS.
- Semgrep final scan: 7 rules, 3,697 targets, 0 findings.
- Formal FAOF-2 validation: PASS; Lean 4.19.0 and Coq 8.20.1.
- Frontend: npm install PASS, lint 0 errors/56 warnings, Vitest 3/3, production build PASS.
- GitOps CI-authoritative staging and production validation: PASS.
- Runtime image smoke build: PASS; final image ID `fa99c15e51dc3a70091bae024f8f27987f55c1fffac5d87c0c438b541bad0178`.

Classifier result for this candidate requires backend, frontend, architecture, GitOps, Semgrep, formal, full CI, and runtime image publication validation. All corresponding local gates above were exercised.

## Observed non-candidate debt

These observations are not hidden and were not modified by this correction:

- Frontend npm audit reports 15 dependency vulnerabilities: 1 low, 13 high, 1 critical.
- Frontend lint reports 56 warnings and 0 errors.
- GitOps production strict-mode exploratory validation fails because egress smoke remains disabled and `example.com` remains in the allowed-domain placeholder list. The repository CI-authoritative non-strict commands pass with warnings. This correction does not modify GitOps policy or deployment readiness.
- Podman emits an OCI-format warning that Dockerfile `HEALTHCHECK` is ignored by the local shim. The image builds successfully; hosted Docker CI remains authoritative for Docker-format healthcheck preservation.

## Publication safety

This candidate record does not itself assert publication or integration. Before publication:

1. An independent reviewer must inspect the exact final staged diff and return PASS with zero blockers.
2. The candidate tree must be frozen and committed once, without amend/rebase/squash.
3. The branch must be pushed and read back exactly.
4. Remote CI must be associated with the exact candidate/publication SHAs.
5. `origin/main` must be fetched immediately before integration and must still equal the expected authority SHA.
6. Canonical integration may use fast-forward only and may not use force.

Future Phase 20/BMF/OpenCue/Roadmap 23/FAOF-3 execution remains not started.