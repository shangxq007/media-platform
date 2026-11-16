# Module Boundary Consolidation — Closeout

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1`
**Branch:** `task/module-boundary-consolidation-r1`
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1`

```text
BASE_COMMIT: 4765eda5f0527c25d61139b2af4dbb5824db3efc
BASE_TREE:   636186a192c509fca6c326400b1b6bcb8386d3a1

MODULES_INSPECTED:  53 active Gradle projects + 1 nested (:platform-algorithms:graph) + 1 HOLD
                    (spring-ai-adapter, excluded unless -PincludeHoldModules=true)
PROVIDER_PLUGINS_INSPECTED: 2
                    media.transcode.ffmpeg@1.2.0 (external PF4J plugin)
                    BMF (in-process ProviderPluginContribution, opt-in)

CODE_COMMIT: f96c7f3c67ad3c549f5306c9859f470cbda1f0f0
CODE_TREE:   d9193ed4b2be6d14c842c051eb7bdb7f7034a168
CANDIDATE_COMMIT: the commit that contains this report (parent = CODE_COMMIT)
CANDIDATE_TREE:   recorded in the final delivery report

CONFIRMED_FINDINGS:
  F-01 (P1) Zero-tolerance Modulith boundary test is red at BASE (pre-existing)
  F-02 (P1) provider-plugin-runtime-module couples the plugin host to the BMF provider
  F-03 (P2) platform-app declared :extension-module twice
  F-05 (P2) composite-resource-module has no production consumer
  F-06 (P2) storage-provider-opendal has no production consumer (kept intentionally)

IMPLEMENTED_FIXES:
  F-03 only. 1 line removed from platform-app/build.gradle.kts. Resolved runtimeClasspath diff
  before/after = 0 lines. No production source, no Spring wiring, no provider registration, no
  frontend, no runtime configuration changed.

NO_CHANGE_DECISIONS:
  F-04 23 "unused-looking" implementation project deps — import absence is not removal proof
        (component scanning and ServiceLoader use); no change.
  F-06 storage-provider-opendal — optional provider, direction guarded by
        OpenDalProviderBoundaryTest; keep.
  F-07 REST controllers in *.api packages — style only, renaming is churn without runtime benefit.

OWNER_DECISIONS_REQUIRED:
  1. F-01 — composition-root slice cycle (config <-> runtime) and bmf -> workerfabric non-exposed
     type dependencies. Fixing needs a composition-root refactor and/or new @NamedInterface
     exposures in worker-fabric (provider-facing internal API), plus a refreshed debt register.
     Do NOT expand ALLOWED_VIOLATIONS to make the test green (forbidden by the task).
  2. F-02 — decouple provider-plugin-runtime-module from bmf-provider-module in two steps
     (move BmfProviderExtension into bmf-provider-module; move RuntimeExecutionBackends.create()'s
     hardcoded backend list to the composition root), then run a real BMF regression. Not done this
     round because it rewrites provider runtime assembly and this session has no runnable BMF
     native runtime (only the containerfile/prepare script), so the required execution evidence
     cannot be produced here.
  3. F-05 — retire or integrate composite-resource-module.

GRADLE_DEPENDENCY_RESULT: PASS
  - main/test project-dependency graph acyclic (0 cycles)
  - only remaining declaration defect removed (F-03)
  - platform-distribution -> ffmpeg-provider-module is a packaging task dependency, not a code edge

PACKAGE_BOUNDARY_RESULT: FAIL (pre-existing, not introduced by this round)
  - 0 cross-module imports of *.internal/*.infrastructure/*.persistence outside the composition root
  - 0 public types in internal packages
  - :platform-app:test --tests '*ModularityTest' FAILS at BASE (and after this change): 1004
    unexpected-violation messages, including Slice config -> Slice runtime -> Slice config and
    bmf -> workerfabric non-exposed types. docs/modulith-debt-register.md claims 0 unexpected.

SPRING_WIRING_RESULT: PASS
  - API default role, WORKER @ConditionalOnProperty, COMBINED opt-in assembly, two separate worker
    binaries; provider beans registered under mutually exclusive role conditions (no duplicates)
  - CombinedRuntimeAssemblyTest, PlatformRuntimeRole*, FfmpegWorkerPluginConfigurationTest,
    ModulithDocumentationGenerationTest, InProcessProviderPluginsTest pass
  - WorkerFabricApplicationContextTest is NOT_RUN in this environment: it requires the controlled
    launch harness (CONTROLLED_LAUNCH_REQUIRED), not a product defect

PF4J_PLUGIN_RESULT: PASS (with the F-02 design caveat)
  - FFmpeg discovered through DefaultPluginManager with plugin.properties + @Extension
  - BMF registered in-process through InProcessProviderPlugins, opt-in flag, no descriptor
  - 111 provider tests (provider-plugin-runtime-module, ffmpeg-provider-module,
    bmf-provider-module): 0 failures, 0 errors, 3 skipped
  - provider identity (pluginId@version, preflight duplicate rejection, binding pins) unchanged

FFMPEG_REGRESSION: PASS (reused, justified)
  The accepted L3 run (rj_5422d737bd324fc3889ab735d2e94c70, artifact
  render-task-741f6c136e2dc8b9ca1ccec321ee2f93f22a15b5a0bb36c2318b, delivery
  dlv_6b773b188c984f0790b4279ffb58f9fc) is reused because the only product change is a duplicate
  Gradle declaration whose resolved runtimeClasspath is provably identical, and all provider tests
  still pass.

BMF_REGRESSION: UNVERIFIED (native execution not run this round)
  No BMF native runtime was available in this session; the BMF provider unit tests pass and the
  BMF classes/dependencies are byte-identical in the resolved classpath, but no real BMF execution
  was produced. This is the blocker for implementing F-02.

TIMELINE_TRIM_REGRESSION: PASS (reused, justified)
  No frontend, Timeline, Operation or RenderPlanning code changed; the runtime classpath is
  identical, so the accepted Trim L3 remains valid.

INDEPENDENT_HERMES_REVIEW: NOT_PERFORMED — UNVERIFIED
  No independent reviewer agent was attached to this session. The evidence package and the review
  checklist are prepared in ModuleBoundaryIndependentReview.md; its RESULT is NOT_PERFORMED, not
  APPROVED. This document does not impersonate Hermes.

FINAL_RESULT: PARTIAL
```

## 1. What was done

- Completed the machine inventory: 53 active projects, 1 nested project, 1 HOLD project, their
  `api`/`implementation`/`compileOnly`/`runtimeOnly`/`testImplementation` declarations, reverse
  consumers, package roots, Spring markers and provider surface.
- Verified the compile-time graph is acyclic and that package boundaries are clean outside the
  composition root.
- Verified the three runtime roles and the provider beans are mutually exclusive.
- Classified the provider loading mode as HYBRID with exact code evidence (PF4J host vs in-process
  catalog) and answered the third-provider cost question.
- Removed the only provably defective Gradle declaration (duplicate `:extension-module`), proving
  with `:platform-app:dependencies --configuration runtimeClasspath` that the resolved classpath is
  unchanged (diff = 0 lines).
- Confirmed a pre-existing red boundary test (`ModularityTest`) and a pre-existing provider-host
  coupling (F-02), and recorded both as findings instead of hiding them behind allowlist changes or
  unverified refactors.

## 2. Why F-02 was not implemented

The coupling is real: `provider-plugin-runtime-module` (host + SPI) declares
`implementation(project(":bmf-provider-module"))`, hosts `providerplugin/bmf/BmfProviderExtension`,
and its `RuntimeExecutionBackends.create()` instantiates `BmfExecutionBackend` unconditionally.
Consequences: every provider plugin built against the host also compiles against BMF, the host is not
provider-neutral, and the API role registers a BMF execution backend even when
`platform.bmf-provider.enabled=false`.

It is nevertheless a *provider runtime assembly* change: moving the extension and the backend list
changes which contribution classes exist and where the worker resolves them. The task requires a real
affected-provider execution regression after such a change, and no BMF native runtime could be
executed in this session. Implementing it with unit tests only would violate
"不能以 Java 编译通过代替插件真实发现与执行验证". It is therefore deferred with a precise plan
(F-02 in the inventory) rather than half-implemented.

## 3. Verification (see the test matrix for commands and exit codes)

| Check | Result |
|---|---|
| `./gradlew compileJava compileTestJava` (all projects) | PASS |
| `:provider-plugin-runtime-module:test :ffmpeg-provider-module:test :bmf-provider-module:test` | PASS 111 / 0 / 3 skipped |
| Spring role/assembly/modularity subset | 36 tests, 33 pass, 1 skipped, 2 pre-existing failures (ModularityTest; WorkerFabricApplicationContextTest requires controlled launch) |
| `scripts/check-architecture-drift.sh --quiet` | PASS (220 checks) |
| `python3 -B scripts/dev/verify guards` | PASS 7/7 |
| `:platform-app:dependencies runtimeClasspath` before/after | identical (0-line diff) |
| `git diff --check` | clean |

## 4. Limits of this result

- The round does not claim a green package-boundary gate: the repository's own zero-tolerance
  Modulith test is red at BASE and remains red. That is the largest unresolved finding.
- Provider host decoupling (F-02) and dead-foundation disposition (F-05) need Owner decisions.
- Independent independent review (Hermes) was not performed; the review package is prepared but
  unverified.
