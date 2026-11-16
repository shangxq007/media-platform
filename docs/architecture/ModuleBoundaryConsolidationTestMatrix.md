# Module Boundary Consolidation — Test Matrix

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1`
**Base:** `4765eda5f0527c25d61139b2af4dbb5824db3efc` / `636186a192c509fca6c326400b1b6bcb8386d3a1`
**Code candidate:** `f96c7f3c67ad3c549f5306c9859f470cbda1f0f0` / `d9193ed4b2be6d14c842c051eb7bdb7f7034a168`
**Final candidate:** the commit that contains this matrix (adds only the four reports)
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1`
**Evidence root:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1-evidence/`

| # | Check | Command | Exit | Result | Evidence |
|---|---|---|---|---|---|
| 1 | Base identity (local == remote) | `git rev-parse main`, `git ls-remote --heads origin main` | 0 | `4765eda5…` / `4765eda5…` equal | `evidence/baseline.txt` |
| 2 | Isolated worktree created from main | `git worktree add -b task/module-boundary-consolidation-r1 … main` | 0 | HEAD `4765eda5…`, clean | `evidence/baseline.txt` |
| 3 | Module inventory extraction | `python3 -I -B /tmp/module_inventory.py <worktree> <evidence>` | 0 | 53 modules, 1 HOLD, 0 main cycles, 2 772 cross-module imports | `module-inventory.json`, `module-dependency-graph.txt` |
| 4 | Whole-build compile (all 53 projects, main + test) | `./gradlew compileJava compileTestJava --no-daemon --max-workers=4` | 0 | `BUILD SUCCESSFUL in 56s` | `logs/compile.log` |
| 5 | Provider plugin tests | `./gradlew :provider-plugin-runtime-module:test :ffmpeg-provider-module:test :bmf-provider-module:test --no-daemon --max-workers=4` | 0 | 111 tests, 0 failures, 0 errors, 3 skipped | `logs/provider-tests.log`, `provider-test-results/`, `ffmpeg-test-results/`, `bmf-test-results/` |
| 6 | Spring roles / assembly / Modulith docs / in-process catalog | `./gradlew :platform-app:test --tests '*ModularityTest' --tests '*ModulithDocumentationGenerationTest' --tests '*CombinedRuntimeAssemblyTest' --tests '*PlatformRuntimeRole*' --tests '*FfmpegWorkerPluginConfigurationTest' --tests '*WorkerFabricApplicationContextTest' --tests '*InProcessProviderPluginsTest'` | 1 | 36 tests, 2 failures, 1 skipped — both failures pre-existing, see rows 7 and 8 | `logs/spring-tests.log`, `spring-test-results/` |
| 7 | Prove `ModularityTest` failure is pre-existing | restored base `platform-app/build.gradle.kts`, re-ran `--tests '*ModularityTest'` | 1 | same failure: 1004 unexpected violations incl. `Slice config -> Slice runtime -> Slice config` | `logs/base-tests.log` |
| 8 | Prove `WorkerFabricApplicationContextTest` failure is environmental | same run | 1 | `IllegalStateException: CONTROLLED_LAUNCH_REQUIRED` (requires controlled launcher) | `logs/base-tests.log` |
| 9 | Resolved runtime classpath unchanged by the fix | `./gradlew :platform-app:dependencies --configuration runtimeClasspath` before/after + `diff` | 0 | **0-line diff** | `classpath-before.txt`, `classpath-after.txt`, `classpath.diff` |
| 10 | Architecture drift | `bash scripts/check-architecture-drift.sh --quiet` | 0 | `ARCHITECTURE_DRIFT: PASS (220 checks)` | `logs/architecture-drift.log` |
| 11 | Guard suite | `python3 -B scripts/dev/verify guards` | 0 | 7/7 components, `results_pass: true` | `guards/20261116T153800Z/manifest.json` |
| 12 | Whitespace / generated-artifact hygiene | `git diff --check` | 0 | clean | `evidence/git-diff-check.txt` |
| 13 | Frontend untouched | `git diff --name-only BASE..CANDIDATE` | 0 | only `platform-app/build.gradle.kts` + the four reports | `evidence/git-diff-since-base.txt` |

## Not run (with reasons)

| Item | Status | Reason |
|---|---|---|
| New FFmpeg L3 (`scripts/dev/first-render --nle-browser`) | `NOT_RUN — evidence reused` | The only product change is a duplicate Gradle declaration; the resolved `runtimeClasspath` is byte-identical (row 9), so the accepted L3 run (`rj_5422d737bd324fc3889ab735d2e94c70`) still describes the produced runtime. Per H4 this reuse is only permitted when no impact is proven — it is proven here. |
| BMF native execution regression | `NOT_RUN` | No runnable BMF native runtime in this session (`scripts/dev/bmf-runtime.Containerfile` + `prepare-bmf-runtime` exist, but no prepared bundle/image was available). This blocks implementing F-02. |
| Timeline Trim Chrome regression | `NOT_RUN — evidence reused` | No frontend, Timeline, Operation or RenderPlanning code changed; runtime classpath identical. |
| Full repository test suite | `NOT_RUN` | Out of the authorized scope (H1 requires affected modules, not the whole suite). |
| `scripts/test-authority-modules.sh` | `NOT_RUN` | It requires the controlled-launch PG18 harness; its `ModularityTest` member is already covered by rows 6–7. |

## Interpretation

- **DEPENDENCY_BOUNDARIES: PASS** — rows 3, 4, 9, 10, 11, 12.
- **PACKAGE_API_BOUNDARIES: FAIL (pre-existing)** — row 7: the repository's zero-tolerance Modulith
  test is red at BASE and remains red. No allowlist expansion was used.
- **SPRING_ASSEMBLY: PASS** — row 6 role tests pass; the single worker-fabric context test cannot run
  outside the controlled launcher (row 8).
- **PF4J_PROVIDER_BOUNDARIES: PASS with caveat** — row 5 provider tests; the host/BMF coupling is
  recorded as F-02 and deliberately not changed without a BMF execution regression.
- **FFMPEG_PROVIDER: PASS (reused)**; **BMF_PROVIDER: UNVERIFIED (native not run)**;
  **TIMELINE_TRIM: PASS (reused)**.
- **INDEPENDENT_HERMES_REVIEW: NOT_PERFORMED** — see `ModuleBoundaryIndependentReview.md`.
- **FINAL_RESULT: PARTIAL** — inventory and dependency/Spring/PF4J facts are complete and verified,
  but the pre-existing package-boundary failure, the deferred provider-host coupling and the missing
  independent review prevent a PASS.
