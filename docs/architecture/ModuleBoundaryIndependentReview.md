# Module Boundary — Independent Review Record

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1`
**Reviewer:** Hermes (independent, read-only) — **not attached to this executing session**
**Producer:** DSH (the only writing agent)

> **Status of this file.** This session ran without an independent reviewer agent. The file therefore
> records that the independent review **did not happen**, plus the exact evidence package and checklist
> a reviewer needs. It is **not** an approval and must not be read as one. When Hermes reviews the
> candidate, it should replace the UNVERIFIED fields below with its own findings.

```text
INDEPENDENT_REVIEW:

FACTUAL_CORRECTNESS:            UNVERIFIED
DEPENDENCY_ASSESSMENT:          UNVERIFIED
PACKAGE_BOUNDARY_ASSESSMENT:    UNVERIFIED
SPRING_WIRING_ASSESSMENT:       UNVERIFIED
PF4J_PLUGIN_ASSESSMENT:         UNVERIFIED
PROPOSED_CHANGES_RISK:          UNVERIFIED
MISSING_EVIDENCE:               independent reviewer agent (Hermes) and its sign-off

RESULT: NOT_PERFORMED
```

## What DSH claims (for independent checking, not accepted on trust)

1. 53 active Gradle projects, 1 nested (`:platform-algorithms:graph`), 1 HOLD
   (`spring-ai-adapter`); the main and test project-dependency graphs are acyclic.
2. Outside `platform-app`, no module imports another module's `internal`/`infrastructure`/
   `persistence` types; no `public` type lives in an `internal` package.
3. Spring assembly is role-explicit: API default, WORKER conditional, COMBINED opt-in, two separate
   worker binaries; provider beans are registered under mutually exclusive conditions.
4. Provider loading is HYBRID: FFmpeg is an external PF4J plugin (`plugin.properties` + `@Extension`
   + `DefaultPluginManager`), BMF is an in-process contribution (`InProcessProviderPlugins`,
   `platform.bmf-provider.enabled`, no descriptor).
5. `provider-plugin-runtime-module` is coupled to BMF (build dep, hosted extension, hardcoded
   `BmfExecutionBackend` in `RuntimeExecutionBackends.create()`); recorded as F-02, not changed.
6. `:platform-app:test --tests '*ModularityTest'` fails at BASE with 1004 unexpected violations;
   recorded as F-01, not "fixed" by expanding the allowlist.
7. The only implemented change is the removal of a duplicate `implementation(project(":extension-module"))`
   in `platform-app/build.gradle.kts`, with a zero-line resolved-classpath diff.

## Evidence index (read-only)

| Evidence | Location |
|---|---|
| Machine inventory (modules, deps, consumers, packages, imports, Spring/PF4J markers) | `media-platform-module-boundary-consolidation-r1-evidence/module-inventory.json` |
| Per-module dependency and consumer listing | `…-evidence/module-dependency-graph.txt` |
| Cross-module import list with implementation-hint flags | `…-evidence/cross-module-imports.tsv` |
| Resolved classpath before/after the fix | `…-evidence/classpath-before.txt`, `classpath-after.txt`, `classpath.diff` |
| Compile log (all projects) | `…-evidence/logs/compile.log` |
| Provider test log + JUnit XML | `…-evidence/logs/provider-tests.log`, `…-evidence/provider-test-results/`, `ffmpeg-test-results/`, `bmf-test-results/`, `spring-test-results/` |
| Spring role / Modulith test log + JUnit XML | `…-evidence/logs/spring-tests.log`, `…-evidence/spring-test-results/` |
| Base-commit reproduction of the two failures | `…-evidence/logs/base-tests.log`,  |
| Architecture drift | `…-evidence/logs/architecture-drift.log` |
| Guard manifest | `…-evidence/guards/20261116T153800Z/manifest.json` |
| Exact diff since base | `…-evidence/git-diff-since-base.txt` |

## Checklist for Hermes

- [ ] Re-derive the module list from `settings.gradle.kts`; confirm 53 + nested + HOLD.
- [ ] Recompute `api`/`implementation` edges; confirm 0 cycles and that
      `platform-distribution → ffmpeg-provider-module` is a task dependency, not a code dependency.
- [ ] Spot-check the "no implementation leakage" claim by re-running the import scan independently.
- [ ] Confirm the role conditions and that no duplicate `ProviderPluginHost`/`ProviderPluginCatalog`
      bean can coexist.
- [ ] Confirm the HYBRID classification from the packaging (`platform-distribution`) and the
      `ProviderPluginHost` startup path, not only from annotations.
- [ ] Judge whether F-02 must be implemented in this round or accepted as deferred debt.
- [ ] Judge whether F-01 (red Modulith test) should block the round.
- [ ] Re-run the four commands in `ModuleBoundaryConsolidationTestMatrix.md` and compare exit codes.

## Reviewer conclusion

Pending. No approval exists for this candidate.
