# Module Boundary Final — Test Matrix

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1_FINAL_CLOSURE`
**Base:** `4765eda5f0527c25d61139b2af4dbb5824db3efc` / `636186a192c509fca6c326400b1b6bcb8386d3a1`
**Previous candidate:** `ef80d09d07c73a4ae6b199b5b7bcd36945aecd83` / `379d2c9c12d77687e145d9bea0e84b6c85f7402d`
**Code commit:** `baa60c15919bfa3aede2515a11d65a332eef32e4` / `68d4ccae59904db99b5419baab7f62b2a9a0cf81`
**Final candidate:** the commit that contains this matrix
**Evidence root:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1-evidence/`

| # | Check | Command | Exit | Result | Evidence |
|---|---|---|---|---|---|
| 1 | Baseline identity | `git rev-parse main`, `git ls-remote --heads origin main` | 0 | main == remote == `4765eda5…` | `baseline-final.txt` |
| 2 | Root-cause aggregation (temporary, not committed) | `./gradlew :platform-app:test --tests '*ModulithViolationDumpTest' -Dmodulith.dump=/tmp/modulith-violations.txt` | 0 | 3519 total messages; 83 raw groups; 27 unexpected groups after the allowlist; 1 cycle | `logs/modulith-violations.txt`, `modulith-root-causes.json` |
| 3 | Modulith BEFORE the fix | `./gradlew :platform-app:test --tests '*ModularityTest' --no-daemon` | 1 | 1 test, 1 failure: **Cycle detected config↔runtime**, 1004 unexpected messages | `spring-test-results/*ModularityTest.xml` |
| 4 | Modulith AFTER the fix | same | 1 | 1 test, 1 failure: **no cycle**, 1002 unexpected messages | `logs/modularity-after.xml` |
| 5 | Golden workflow-id test | `./gradlew :media-execution-plan-module:test --tests '*CapabilityExecutionWorkflowIdsTest'` | 0 | 2/2 pass, bytes pinned `capability:<tenant>:<execution>:<generation>` | `logs/cycle-fix-test.log` |
| 6 | Whole build compile | `./gradlew compileJava compileTestJava --no-daemon --max-workers=4` | 0 | BUILD SUCCESSFUL | `logs/final-compile.log` |
| 7 | Provider suite | `./gradlew :provider-plugin-runtime-module:test :ffmpeg-provider-module:test :bmf-provider-module:test --no-daemon` | 0 | 111 tests, 0 failures, 0 errors, 3 skipped | `logs/final-provider-tests.log`, `*-test-results/` |
| 8 | Spring roles / assembly | `./gradlew :platform-app:test --tests '*CombinedRuntimeAssemblyTest' --tests '*PlatformRuntimeRole*' --tests '*FfmpegWorkerPluginConfigurationTest' --tests '*ProviderRuntimeExecutionCompositionTest' --tests '*MediaTaskAdmissionEvidenceFactoryTest'` | 0 | 47 tests, 0 failures, 0 errors, 2 skipped | `logs/final-spring-tests.log` |
| 9 | Architecture drift (committed tree) | `bash scripts/check-architecture-drift.sh --quiet` | 0 | `ARCHITECTURE_DRIFT: PASS (220 checks)` | `logs/final-drift.log` |
| 10 | Guard suite | `python3 -B scripts/dev/verify guards` | 0 | 7/7, `results_pass: true` | `guards/20261116T155955Z/manifest.json` |
| 11 | Document governance | `bash scripts/check-document-governance.sh --head HEAD --base main` | 0 | 16/16 PASS | `logs/final-docgov.log` |
| 12 | Hygiene | `git diff --check`, `git status --porcelain` | 0 | clean, 0 dirty | `git-diff-check.txt` |
| 13 | Hermes pre-review ×2 | independent read-only reviewer agents | — | APPROVE_WITH_CONDITIONS ×2 | `ModuleBoundaryFinalHermesReview.md` |
| 14 | Hermes final review | independent read-only reviewer agent | — | APPROVE_WITH_CONDITIONS | `ModuleBoundaryFinalHermesReview.md` |

## Plugin / runtime identities observed

| Item | Value |
|---|---|
| FFmpeg plugin identity | `media.transcode.ffmpeg` @ `1.2.0` (unchanged, not rebuilt) |
| BMF provider identity | `media.bmf.cpu` (unchanged; BMF extension not moved) |
| BMF runtime image available | `localhost/media-platform-bmf-runtime:0.2.0-r1` (594 MB, pinned by `scripts/dev/prepare-bmf-runtime`) |
| Host → BMF Gradle dependency | **PRESENT** (unchanged: `provider-plugin-runtime-module/build.gradle.kts:23`) |
| RenderJob / Artifact / Delivery | none created this round (no render-path change) |

## Not run (with reasons)

| Item | Status | Reason |
|---|---|---|
| Modulith green | `FAIL` | 1002 unexpected messages across 27 groups remain; the declaration pass is an Owner scope decision, not a bug fix. Allowlist untouched. |
| F-02 implementation + BMF native regression | `BLOCKED` | Conflicts with accepted EP05 `BmfArchitectureGuardTest` (bmf production must not reference `ProviderPlugin`/pf4j; host build must contain the bmf dependency) and P17-L-072 clean-forward ledger. Owner decision required. |
| FFmpeg real L3 | `NOT_RUN — reuse justified` | No provider SPI, catalog, host, plugin metadata or worker runtime code changed; provider suite green and compile clean. |
| Timeline Trim Chrome L3 | `NOT_RUN — reuse justified` | No timeline/operation/render/frontend change; workflow-id bytes identical. |
| Full repository test suite | `NOT_RUN` | Out of the authorized scope; affected modules covered above. |
