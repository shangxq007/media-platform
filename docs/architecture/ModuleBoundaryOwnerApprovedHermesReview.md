# Module Boundary — Owner-Approved Final Independent Review (Hermes)

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1` (owner-approved final implementation)
**Reviewer:** Hermes — an independent read-only reviewer agent (separate context; no writes, no git
writes, no Gradle runs); it inspected the committed source and the raw evidence files directly.
**Producer:** DSH (the only writing agent).

> This file records the actual review. The reviewer's verdict is reproduced, followed by the
> conditions it raised and their resolution. DSH did not author the APPROVE verdict.

## 1. Final review of the implementation commit `78aba9d5`

```text
HERMES_FINAL_REVIEW:

F01: APPROVE
F02: APPROVE
EP05_P17: APPROVE
SPRING_ROLES: PASS
FFMPEG_PF4J: PASS
BMF_NATIVE_EVIDENCE: PASS
DIFF_SCOPE: PASS
RESULT: APPROVE_WITH_CONDITIONS
```

Verified points (reviewer's own file/line evidence):

- `ModularityTest` still asserts `unexpectedViolations.isEmpty()`; `ALLOWED_VIOLATIONS` was not
  widened; the final run reports `tests=1 failures=0` with `Unexpected violations: []` and no cycle.
- The new `@NamedInterface` declarations are package-scoped and consumed (e.g.
  `workerfabric/domain/package-info.java`, `render/domain/renderplan/package-info.java`); no
  whole-module exposure.
- The provider host has no BMF build edge and no BMF import; the bridge depends on both host and
  engine; the engine depends on neither; one registry, one `ProviderPluginContribution` SPI; the BMF
  backend is only contributed when `platform.bmf-provider.enabled=true`.
- EP05 retains its safety invariants and only the obsolete "host must depend on BMF" constraint was
  replaced; P17-L-072 carries an explicit superseding record.
- FFmpeg L3 (`rj_2f7dd25777504866b084ac9b01a2f88e` / `dlv_25b9f96375a342849c2c6eb87db32369`) and
  BMF native L3 (`rj_2b45657b69db4799aef34802b6966374` /
  `render-task-bb658155a8be73569892bf8b13f4058917e330a4b6650f001fa7` /
  `dlv_34dc9439e01040b6b7d3b7faca96b5ed`) are real runs bound to the code tree.

## 2. Conditions raised by the reviewer

1. **Stale host permission.** `provider-plugin-runtime-module/.../providerplugin/package-info.java`
   still granted the `bmf` dependency in `allowedDependencies`, although the host no longer depends
   on BMF.
2. **EP05 coverage displaced by the bridge move.** `RuntimeExecutionBackendsTest` had been reduced
   to synthetic registry tests; the deleted BMF fail-closed/cancellation and local-sandbox
   propagation assertions were not relocated, and the authority script did not run the bridge suite.
3. **Stale committed records.** The previous round's `ModuleBoundaryFinalClosure.md` and
   `ModuleBoundaryFinalTestMatrix.md` still stated `F-01 FAIL` / `F-02 BLOCKED` / `host→BMF PRESENT`
   as current facts.

## 3. Resolution (commit `c50a42ff`)

1. The `bmf` permission is removed from the host declaration; `ModularityTest` re-run passes with
0 unexpected violations.
2. `RuntimeExecutionBackendsTest` again covers `localCallerLookupHasOneBindingAndRejectsDuplicates`,
`duplicateCapabilityOwnershipIsRejectedAcrossBackends`,
`existingLocalCallerWithoutAnExplicitWorkspaceStillFailsClosed` and
`localExecutionPropagatesTheCanonicalSandboxResult`; the new `BmfProviderPluginTest` covers the BMF
contribution type, `bmfCannotManufactureSuccessOrExecuteWithoutPlatformIdentity`,
`registryResolvesTheBmfBackendOnlyWhenItIsContributed`; `scripts/test-authority-modules.sh` execution
group now runs `:bmf-provider-plugin-module:test`.
3. Both previous documents carry an explicit `SUPERSEDED (historical record)` banner pointing at the
owner-approved closeout and matrix (committed in the same candidate).

## 4. Delta review of the condition fixes

```text
HERMES_DELTA_REVIEW:

CONDITION_1: RESOLVED
CONDITION_2: RESOLVED
CONDITION_3: RESOLVED
REGRESSION_RISK: NONE (code)
RESULT: APPROVE
```

Reviewer's delta evidence: the host declaration edit is exactly one removed line; the build file and
host production still have no BMF edge/import; `ModularityTest` green on the fixed source;
`RuntimeExecutionBackendsTest` 4/4 and `BmfProviderPluginTest` 3/3 pass; the authority script includes
the bridge suite; the superseded banners are present. The reviewer's only remaining caveat was that
the owner-approved documents were still untracked at `c50a42ff` and had to be committed so the banner
links do not dangle in a clean checkout — that is exactly the docs commit that contains this file.

## 5. Residual risks (not contested)

- The Modulith declarations now describe the real cross-module contracts; a future change that adds a
  new cross-module import must declare it, and the gate will fail otherwise (proved by the negative
  check).
- The deployment-isolation caveat (`DEPLOYMENT_ISOLATION_NOT_VERIFIED`) from the harness remains.
- Combined-role media execution was not re-run; the COMBINED assembly is covered by
  `CombinedRuntimeAssemblyTest`.

## 6. Separation of findings

| Claim | Established by |
|---|---|
| Host had no BMF edge/import; bridge owns the contribution; one registry; flag-gated backend | Hermes (independent) |
| EP05 invariants retained; P17 superseding record present | Hermes (independent) |
| L3 job/artifact/delivery identities | Hermes (independent read of the raw evidence) |
| Restored test names/counts and gate result | Hermes (independent) |
| DSH's earlier summary numbers | DSH (execution evidence) |
