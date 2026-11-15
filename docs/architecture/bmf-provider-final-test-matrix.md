# BMF Provider R1 — final test matrix

**Task:** `MEDIA_PLATFORM_BMF_PROVIDER_INTEGRATION_R1`
**Branch:** `task/bmf-provider-integration-r1`
**Base main:** `fbc8428018251b14d7b4dd9d5b1287ebcbb24f9f`
**Final candidate:** the commit containing this file (`git log -1`); parent `29cbcddb…` (inherited
partial candidate).

Environment: JDK 25.0.5, Gradle 9.1.0 wrapper, Linux with rootless Podman. Task-owned BMF runtime
bundle at `/home/user/Documents/workspace/projects/media-platform-bmf-runtime-r1`; task-owned
evidence at `/home/user/Documents/workspace/projects/media-platform-bmf-e2e-r1`. No production data,
deployment or database contract was touched.

## Gate results

| Gate | Result | Evidence |
|---|---|---|
| BMF_PROVIDER_DISCOVERY | **PASS** | `BmfProviderExtension` declares `media.transcode`, `video.decode`, `render.composite`, `render.output`; `BmfProviderExtensionTest` (provider-plugin-runtime-module) passes; API catalogue stays opt-in (`platform.bmf-provider.enabled`) |
| BMF_CAPABILITY_CLOSURE | **PASS** | 4 operations in `BmfOperation`; lowerer accepts decode/composite/encode/transcode and rejects unknown/unsupported variants (`BmfCpuTranscodeSeamsTest`, 6 cases) |
| MULTI_PROVIDER_BINDING | **NOT_APPLICABLE** | #22 V1 binds only when exactly one candidate is feasible; per-unit BMF+FFmpeg mixing would require a new selection policy (contract change, not authorized). Selection is the internal `platform.render.media-provider` property; default `ffmpeg` |
| BMF_WORKER_EXECUTION | **PASS** | `BmfCpuWorkerRuntimeIntegrationTest` through real Binding + bubblewrap + BMF 0.2.0: valid `ftyp` MP4 and corrupt-input `PROCESS_NONZERO_EXIT`; evidence `media-platform-bmf-e2e-r1/l2-worker-run.log` |
| BMF_FORMAL_RENDERJOB | **NOT_RUN** | L3 harness is FFmpeg-path only and does not provision BMF provider selection/runtime/admission; see closeout §4 |
| BMF_ARTIFACT_COMMIT | **NOT_RUN** | no formal RenderJob run |
| BMF_DELIVERY | **NOT_RUN** | no formal RenderJob run |
| BMF_MEDIA_VALIDATION | **PARTIAL** | worker-level output validated by ffprobe/SHA-256; not through RenderJob/Artifact/Delivery |
| FFMPEG_REGRESSION | **PASS** | `:ffmpeg-provider-module:test` 43 tests, 0 failures, 1 skipped |
| ARCHITECTURE_GUARDS | **PASS** | `bash scripts/check-architecture-drift.sh` — 219 checks, 0 failures on the committed candidate |
| L3_ENVIRONMENT | **BLOCKED** | no task-owned PG18/Temporal/OIDC started; host FFmpeg lacks `libx264`; no worker font; BMF L3 wiring absent |
| OPENCUE_CURRENT_USAGE | **UNUSED_CODE / DOCUMENTATION_ONLY** | no client/import/config; comments, scenario flags, archived examples only; dispatch is Temporal + Worker Fabric |

## Test arithmetic

| Module | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `bmf-provider-module` | 22 | 0 | 0 | 2 | the 2 skipped are the runtime integration tests without env; they pass with `BMF_TEST_RUNTIME_ROOT`/`BMF_TEST_SOURCE` |
| `provider-plugin-runtime-module` (`*Bmf*`) | 6 | 0 | 0 | 0 | capability/descriptor declarations |
| `media-execution-plan-module` (`*PhysicalPlanningEntryServiceIntegrationTest*`) | 5 | 0 | 0 | 0 | includes the new real-plan capability matrix assertion |
| `ffmpeg-provider-module` | 43 | 0 | 0 | 1 | FFmpeg regression |

## Real BMF execution evidence

- Bundle manifest: `bmf.version=0.2.0`, `ffmpeg.version=4.3.7`, `python.version=3.9.2`,
  `numpy.version=1.19.5`, `source.sha256=8fb3ec03…`, `bundle.sha256=0d4c2e92…`
- Output: `worker-output.mp4`, SHA-256 `ba24b884f0de57d5b7fdc3e08c08f2e4b08cda47e63042c26274bbe48b4a31eb`
- ffprobe: `h264`, 16×16, 1/1 fps, 1 frame, duration 1.000 s, size 1526 bytes
- Test report: `TEST-com.example.platform.bmf.BmfCpuWorkerRuntimeIntegrationTest.xml`

## Not executed (honest status)

| Item | Reason |
|---|---|
| Formal RenderJob submission through API/Temporal/Worker with BMF | L3 harness not wired for BMF provider selection, runtime root and admission evidence; no task-owned PG18/Temporal/OIDC started |
| Artifact commit + Delivery of a BMF render | depends on the formal RenderJob run |
| Browser/product E2E | not required for this round's verified scope; would not exercise BMF without the harness work |

## Reproduction

```sh
# L1
./gradlew :bmf-provider-module:test :ffmpeg-provider-module:test --no-daemon
./gradlew :provider-plugin-runtime-module:test --tests '*Bmf*' --no-daemon
./gradlew :media-execution-plan-module:test --tests '*PhysicalPlanningEntryServiceIntegrationTest*' --no-daemon
bash scripts/check-architecture-drift.sh --quiet

# L2 (requires the prepared task-owned bundle and a source clip)
BMF_TEST_RUNTIME_ROOT=/home/user/Documents/workspace/projects/media-platform-bmf-runtime-r1 \
BMF_TEST_SOURCE=$PWD/platform-app/src/test/resources/render-output-fixture.mp4 \
BMF_TEST_OUTPUT=/home/user/Documents/workspace/projects/media-platform-bmf-e2e-r1/worker-output.mp4 \
./gradlew :bmf-provider-module:test --tests '*BmfCpuWorkerRuntimeIntegrationTest*' --no-daemon
```
