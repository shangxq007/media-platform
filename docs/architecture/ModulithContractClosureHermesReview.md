# Modulith Contract Closure R1 — Independent Hermes Review

**Task:** `MEDIA_PLATFORM_MODULITH_CONTRACT_CLOSURE_R1`
**Reviewer:** Hermes — independent read-only reviewer agent (separate context; no writes, no git writes)
**Producer:** DSH (the only writing agent)
**Base:** `cf0bb5adb334ef3192c99b2d698e9faea763cb9f`

> This file records the actual review. DSH's conclusions, the reviewer's independent findings, the
> conditions and their resolution are kept separate.

## 1. Review rounds

| Round | Scope | Result |
|---|---|---|
| Initial | `ca0a8915` (first candidate) | `APPROVE_WITH_CONDITIONS` — 4 conditions (graph rules now dead / docs counts / javadoc + duplicate entry / re-verify on the frozen SHA) |
| Delta 1 | `71fb8e3f` (graph + docs corrections) | `APPROVE_WITH_CONDITIONS` — documentation-only residuals (stale graph rationale comment; root-list miscount; verification rows stale) |
| Final | `6ec98c35` (comments/docs) | `APPROVE_WITH_CONDITIONS` — 4 documentation/evidence residuals (R1 vs matrix mismatch; root list providerplugin/bmf; provider test XML evidence; timeline rerun) |
| Closure | `a60d7589` (docs only) | residuals addressed in the final documentation commit (see §4) |

The reviewer verified code/contracts at `6ec98c35` as unchanged from the previously verified candidate
and re-ran the gates where possible.

## 2. Independently verified facts

- `ALLOWED_VIOLATIONS`: 33 → exactly 3 entries; the three graph rule literals are unchanged
  (`ModularityTest.java:34/55/59` in the final file); no rule was added or broadened; the assertion
  still requires zero unexpected violations.
- ModularityTest replays PASS with an empty violation log; the three retained rules currently match
  0 diagnostics because the consumers declare `graph :: api` / `graph :: result` while the graph
  module stays unannotated and Spring-free.
- No new `@NamedInterface` on `infrastructure` / `adapter` / `internal` packages; the pre-existing
  `render.infrastructure*` declarations are unchanged.
- `typedschema` generated records are declared from the generator-immune
  `src/main/modulith/java` source root; generated sources untouched.
- D6 diff is four deletions only: no `timeline.adapter` import, five-argument constructor, endpoints
  untouched.
- H8 changes are 15 hash values only; the guard re-run passes with
  `H8_GOVERNED_RUNTIME_SOURCE_HASH_MISMATCH_COUNT=0` and all 114 hashes matching.
- The pre-existing failures are genuine: `Cfrhi1LegacyWriteAuthorityGuardTest` (stale restore
  expectation; controller unchanged from base) and `RevisionCreatedEventTest` (controlled-launch
  PostgreSQL harness), plus `JooqVersionAuthorityTest` (expects 3.19.30, `gradle.properties` pins
  3.21.9; both unchanged from base).

## 3. Conditions and resolution

| # | Condition | Resolution |
|---|---|---|
| 1 | Graph rules now match 0 violations, while docs/rationale still claimed they were needed | Debt register records the 0-match state, the D3/D4 rationale and the exit condition; the in-code comments now state the D4 policy explicitly. Owner decision D4 mandates keeping the three rules. |
| 2 | Counts wrong (34 added / 23 extended / 17 files / 21 annotations; 7 roots) | Closeout corrected; root list now names bmf and drops providerplugin. |
| 3 | `timeline/diff/package-info.java` javadoc lost its `*`; duplicate `artifact :: app` | Fixed. |
| 4 | Verification not refreshed on the frozen SHA | Final runs recorded: 504 tests/1 failure, 40/0, 9/0, 820/2; all three failures documented as pre-existing. |

## 4. Remaining items

- Documentation-only residuals were closed in the final docs commit (`a60d7589`): root list,
  per-run test evidence, pre-existing failure list.
- No code, contract, declaration or rule change remains unverified.
- The three graph rules stay as a policy marker at owner decision D4; their exit condition is the
  formal modelling of the graph kernel.
