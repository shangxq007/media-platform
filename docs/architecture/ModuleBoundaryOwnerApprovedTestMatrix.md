# Module Boundary — Owner-Approved Final Test Matrix

**Task:** `MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1` (final implementation)
**Base:** `4765eda5f0527c25d61139b2af4dbb5824db3efc` / `636186a192c509fca6c326400b1b6bcb8386d3a1`
**Implementation commit (L3 runs):** `78aba9d5852c660f156746e8e87df7c9d158fcc0` / `83c7f2d2d9b7c297432d44d53fe6e5aeee646678`
**Code commit (final, review-condition fixes):** `c50a42ffabf990c810cd6056fbcb13978f53f2bc` / `ee70d9bbd014c6a1e0230610f9d3e0f7fccefff1`
The only production difference between the two is one removed allowedDependency line in the provider host package-info; the L3 runs therefore still describe the final runtime.
**Evidence root:** `/home/user/Documents/workspace/projects/media-platform-module-boundary-consolidation-r1-evidence/`

| # | Check | Command | Exit | Result | Evidence |
|---|---|---|---|---|---|
| 1 | Modulith gate | `./gradlew :platform-app:test --tests '*ModularityTest' --no-daemon` | 0 | 1 test, 0 failures, **0 unexpected violations, 0 cycles** | `logs/modularity-final-pass.log` |
| 2 | Negative boundary detection | remove web artifact declarations → same test | 1 | 36 unexpected messages → FAIL, then restored → PASS | `logs/modularity-negative.log`, `logs/modularity-restored.log` |
| 3 | Whole-build compile | `./gradlew compileJava compileTestJava --no-daemon` | 0 | BUILD SUCCESSFUL (incl. new bridge module) | `logs/final-compile2.log` |
| 4 | Provider + bridge + Modulith suite (final) | `./gradlew :provider-plugin-runtime-module:test :bmf-provider-plugin-module:test :bmf-provider-module:test :ffmpeg-provider-module:test :platform-app:test --tests '*ModularityTest' --no-daemon` | 0 | 115 tests, 0 failures, 0 errors, 3 skipped | `logs/provider-bridge-tests-final.log` |
| 5 | Spring roles / assembly / Modulith | `./gradlew :platform-app:test --tests '*ModularityTest' --tests '*CombinedRuntimeAssemblyTest' --tests '*PlatformRuntimeRole*' --tests '*FfmpegWorkerPluginConfigurationTest' --tests '*ProviderRuntimeExecutionCompositionTest' --tests '*MediaTaskAdmissionEvidenceFactoryTest'` | 0 | 48 tests, 0 failures, 0 errors, 2 skipped | `logs/spring-roles.log` |
| 6 | Architecture drift | `bash scripts/check-architecture-drift.sh --quiet` | 0 | `ARCHITECTURE_DRIFT: PASS (220 checks)` | `logs/drift-final.log` |
| 7 | Guard suite | `python3 -B scripts/dev/verify guards` | 0 | 7/7, `results_pass: true` | `guards/manifest-final2.json` |
| 8 | Document governance | `bash scripts/check-document-governance.sh --head HEAD --base main` | 0 | 16/16 PASS | `logs/docgov-final.log` |
| 9 | P17 ledger | `python3 docs/architecture/governance/automated-guards/check-phase17-sandbox-ledger.py` | 0 | rows=131, unclassified=0, duplicates=0 | `logs/p17-ledger.log` |
| 10 | Hygiene | `git diff --check`, `git status --porcelain` | 0 | clean | `git-diff-check.txt` |
| 11 | FFmpeg real L3 (Trim + render) | `python3 -B scripts/dev/first-render --output …/l3-ffmpeg-owner-final2 --timeline-operation-editing --nle-browser` | 0 | `FIRST_RENDER_LOCAL_VALIDATION_PASS`, cleanup PASS | `l3-ffmpeg-final.log`, `l3-ffmpeg-owner-final2/` |
| 12 | BMF native L3 | `python3 -B scripts/dev/first-render --output …/l3-bmf-owner-final --provider bmf --scenario single-video-no-audio --bmf-runtime-root …/bmf-runtime-root` | 0 | `FIRST_RENDER_LOCAL_VALIDATION_PASS`, cleanup PASS | `l3-bmf.log`, `l3-bmf-owner-final/` |
| 13 | BMF runtime staging | `python3 -B scripts/dev/prepare-bmf-runtime --output …/bmf-runtime-root` | 0 | release sha256 `8fb3ec03b06ba56e5276a6844614879cdc0ae3886b42a4bf783782708130e327`, bundle sha256 `0d4c2e9220a582f9ce286f7185f513ca71a0272910d0e79da8a226d734a03be1` | `bmf-runtime-prep.log` |
| 14 | Hermes independent review | independent read-only reviewer agent | — | see `ModuleBoundaryOwnerApprovedHermesReview.md` | that file |

## Real execution identities

| Item | Value |
|---|---|
| FFmpeg plugin identity | `media.transcode.ffmpeg` @ `1.2.0` (unchanged) |
| BMF provider identity | `media.bmf.cpu` (unchanged; contribution moved, identity not) |
| FFmpeg RenderJob | `rj_2f7dd25777504866b084ac9b01a2f88e` |
| FFmpeg Artifact | `render-task-e0c50ebaab3fd27c5ef03f1f378b17e293b9c8157c3c464ecac2` |
| FFmpeg Delivery | `dlv_25b9f96375a342849c2c6eb87db32369` |
| FFmpeg trimmed revisions | head `2/1→6/1`, tail `2/1→5/1`; 3.000 s output; green opening, blue tail |
| BMF RenderJob | `rj_2b45657b69db4799aef34802b6966374` |
| BMF Artifact | `render-task-bb658155a8be73569892bf8b13f4058917e330a4b6650f001fa7` |
| BMF Delivery | `dlv_34dc9439e01040b6b7d3b7faca96b5ed` |
| BMF operations | decode / composite / encode, frames=1, output sha256 `ceb6359987869fe1627768bad6391352a7f8162618d71a71796b01a274e89c0f` |
| BMF runtime root | `…/bmf-runtime-root` (pinned release + bundle digests above) |
| API/WORKER mode | independent API and WORKER processes (`independent_api_worker=true`), real PG18 + Temporal |

## Environment note

- `DEPLOYMENT_ISOLATION_NOT_VERIFIED` remains the harness's standing deployment caveat (local
  task-owned runtime, not a production deployment).
- The FFmpeg L3 required `frontend/node_modules` in the task worktree for the owned Chrome E2E; the
  dependency tree is gitignored and not part of the candidate.
- An earlier FFmpeg L3 attempt (`l3-ffmpeg-owner-final`) failed before execution because that
  dependency was missing; it is retained as a failed attempt, not as evidence.

## Not run (with reasons)

| Item | Status | Reason |
|---|---|---|
| Combined-role media E2E | `NOT_RUN` | `CombinedRuntimeAssemblyTest` covers the COMBINED assembly; no media run was repeated, per the task's optional `--combined-smoke`. |
| Full repository test suite | `NOT_RUN` | Out of the authorized scope; affected modules and product paths covered above. |
| Deployment isolation / multi-node worker | `NOT_RUN` | Explicitly out of scope. |
