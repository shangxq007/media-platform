# BMF Provider R1 — Final Test Matrix

**Task:** `MEDIA_PLATFORM_BMF_PROVIDER_INTEGRATION_R1`
**Branch:** `task/bmf-provider-integration-r1`
**Inherited candidate:** `5b897349b16b2f76825b43e210ce945e8bd64874`
**Verified code commit / tree:** `a5b019658efb9bac3d0fae930b47a3886d530ef2` / `7c9ad806fd5d37653c9cd54fef1f6d648cf95d8a`
**Final candidate:** the commit that contains this matrix (adds only this file and the closeout)
**Rule applied:** a historical worker-sandbox or probe PASS is never reported as a formal RenderJob PASS; every row below cites the formal product path of this round unless stated otherwise.

| Gate | Result | Evidence |
|---|---|---|
| `BMF_SCENARIO_CONTRACT` | **PASS** | One silent 320×240 / 1 fps / 1.000 s single-clip video: 1 video track, 1 clip, 0 timed text, 0 audio routes, 0 transitions, 0 effects, extent `0..1000 ms @ 1/1`. Canonical snapshot read back from `/api/render/projects/project-fr-e2e/timeline/revisions/{rev}/snapshot`. Composite aggregate carries no canvas/transform/overlay parameter, so with `source raster == canvas` and `source rate == extent rate` the BMF pass-through is contract-equivalent. Evidence: `02-product/scenario.json`, `03-bmf-evidence/bmf-plan-canonical.txt` |
| `BMF_PROVIDER_SELECTION` | **PASS** | `platform.bmf-provider.enabled=true` + `platform.render.media-provider=bmf`. Persisted candidate set contains `bmf.cpu.v1` and **not** `ffmpeg.cpu.native-pull-v1` (2492 canonical bytes, sha256 `b47e520e…`). FFmpeg remains the default when unset (provider-selection unit test + the FFmpeg L3 run below). Evidence: `03-bmf-evidence/bound-candidates.txt` |
| `BMF_PHYSICAL_PLAN` | **PASS** | Real C20→C21 produced exactly three physical units for the single clip: `decode`/`video.decode`, `composite`/`render.composite`, `encode`/`render.output`. Physical plan digest `23bcb70e10daa3616025c84f7cc62cd547aa28378c4084c323d5813181748798`, read from `render_binding_inputs.plan_json`, not from test constants. Evidence: `03-bmf-evidence/physical-plan.canonical.txt` |
| `BMF_BOUND_GRAPH_PERSISTENCE` | **PASS** | `render_binding_inputs` row persisted with `plan_digest 23bcb70e…` and `expected_etg_digest ed239e8d…`; the dispatched `render_graph_execution.graph_digest` is the identical `ed239e8d…`, so the persisted binding inputs reproduce the executed graph. `candidate_set_digest` is `null` in this schema version (nullable column). Evidence: `03-bmf-evidence/bmf-evidence.json` → `persisted.bound_graph`, `persisted.graph_execution` |
| `BMF_TEMPORAL_WORKFLOW` | **PASS** | Workflow `render:tenant-fr-e2e:rj_0a4cae9e6e5b4cefb5581643ca4c6ade` reached `WORKFLOW_EXECUTION_STATUS_COMPLETED` (run `01a1df55-75c7-75f6-815a-f3cb82b0f0bc`, 24 history events), described through the task-owned Temporal container. Evidence: `04-temporal/workflow-describe.json` |
| `BMF_WORKER_ATTEMPT` | **PASS** | Three persisted `wf_execution_attempt` rows, backend `NATIVE_PULL_WORKER`, generation 1, all `SUCCEEDED`: `attempt-453e62d9…`, `attempt-991b1925…`, `attempt-0454f54f…`; matching `wf_execution_assignment` rows and three `wf_completion_event` rows. Evidence: `03-bmf-evidence/bmf-evidence.json` → `persisted.attempts/assignments/completions` |
| `BMF_REAL_GRAPH_EXECUTION` | **PASS** | Three real BMF graph processes inside the provider bubblewrap sandbox, executable `<verified-runtime>/host-bundle/usr/bin/python3.9`, exit 0, captured stdout untruncated (7941 / 5009 / 5003 bytes with recorded SHA-256), each producing the 1584-byte file it committed. Chain of custody: source `d9848a69…` → decode → `ceb6359987…` → composite → `ceb6359987…` → encode → master `ceb6359987…`; every unit's materialized input digest is the previous unit's committed content and every captured output-file length equals the committed content length. No `FFMPEG_ARGV` render invocation exists for this job. Evidence: `05-worker/graph-executions.json`, `05-worker/worker-log-excerpt.log` |
| `BMF_ARTIFACT_COMMIT` | **PASS** | Commit authority produced 2 `DERIVED_MEDIA` + 1 `RENDER_MASTER`, all `AVAILABLE`, 1584 bytes: `render-task-02d30b1e74bd27c2c6cd25db43368fe9be0611b8ec1e83854631` (master), `render-task-b3d233fd…`, `render-task-b62cd8bf…`; exactly 1 quota completion operation; status history `QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED`. Evidence: `03-bmf-evidence/bmf-evidence.json` → `artifact_commit`, `02-product/product-database-facts.json` |
| `BMF_DELIVERY_DOWNLOAD` | **PASS** | Formal Delivery API: create `201` (`dlv_797a6cc0f2bb4d528dad278d99b1762f`), idempotent replay `200` with the same id, `GET …/result` `200`, 1584 bytes, SHA-256 `ceb6359987869fe1627768bad6391352a7f8162618d71a71796b01a274e89c0f` equal to the render-master content digest; persisted `delivery` row `READY`. Not a copied Worker temporary file. Evidence: `07-delivery/` |
| `BMF_MEDIA_SEMANTICS` | **PASS** | MP4 container; `h264` High; 320×240; `r_frame_rate = avg_frame_rate = 1/1`; duration 1.000 s; 1 frame by `-count_frames`; `yuv420p`; 0 audio streams; full decode `-f null -` exit 0 with no error output; SHA-256 `ceb635…` differs from the source `d9848a69…`, so it is not a copy. Evidence: `07-delivery/delivery-ffprobe.json` |
| `FFMPEG_REGRESSION` | **PASS** | Existing FFmpeg entry (`first-render --timeline-operation-editing`, unmodified) submitted `rj_12b5ef780e974b4eb01575c57262ad9c` (HTTP 201, plan `1fb77617…`), executed 4 FFmpeg processes at `/usr/local/lib/media-platform/ffmpeg/ffmpeg`, committed `render-task-d5fe7ac7…` (1851 bytes), downloaded `200` with SHA-256 `287ef2638e8e3928dfd10f13a28559575a49c92b13ee55c8c780fea09af211cb`, probe `h264` 320×240 2.000 s, history complete, quota 1, `FIRST_RENDER_LOCAL_VALIDATION_PASS`. The FFmpeg L3 preflight still blocks on the same five original items while only the BMF preflight relaxes the subtitle-font requirement. Evidence: `10-ffmpeg-regression/` |
| `ARCHITECTURE_GUARDS` | **PASS** | `scripts/check-architecture-drift.sh --quiet` → `ARCHITECTURE_DRIFT: PASS (219 checks)`; `BmfArchitectureGuardTest` 8/8; provider/worker/plugin contract tests green. No public API, SPI, Flyway, fencing or CAS change was required. Evidence: `08-provider-negatives/architecture-drift.log` |

## Negative and fail-closed checks

| Check | Result | Evidence |
|---|---|---|
| BMF runtime missing → fail closed | **PASS** | `test_bmf_requires_an_explicit_runtime_root`, `test_bmf_runtime_root_must_be_an_existing_directory` (exit 2, no output directory created) |
| Wrong bundle digest → rejected | **PASS** | `test_wrong_bundle_digest_is_rejected` (manifest declares the pinned digest, computed digest differs → exit 2 before any build/service) |
| BMF runtime root without the provider | **PASS** | `test_bmf_runtime_root_without_the_provider_is_rejected` |
| BMF + operation-editing/browser flow | **PASS** | `test_bmf_rejects_the_operation_editing_flow` |
| Unsupported capability / operation rejected | **PASS** | `BmfCpuTranscodeSeamsTest` 6/6 (unknown operation key, undeclared capability, timed-text composite input, authored intents, non-decode source window, cardinality) |
| Execution failure yields no completed Artifact | **PASS** | `BmfCpuWorkerRuntimeIntegrationTest` corrupt-input case: `PROCESS_NONZERO_EXIT`, no output; cancel job `rj_419460f0…` commits 0 artifacts and cancels its workflow |
| Authorization / identity isolation | **PASS** | 5 artifact negatives (401/403/404/400) and 3 authorization negatives in the BMF run |
| Source upload negatives | **PASS** | unauthenticated upload `401/403` |

## L1 arithmetic (from JUnit XML, not from a summary line)

| Selection | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `bmf-provider-module` full | 22 | 0 | 0 | 2 |
| `bmf-provider-module` runtime integration with the verified bundle | 2 | 0 | 0 | 0 |
| `provider-plugin-runtime-module` (`BmfProviderExtensionTest`) | 6 | 0 | 0 | 0 |
| `platform-app` provider selection + worker plugin configuration | 7 | 0 | 0 | 1 |
| `scripts/dev/test_first_render_entry.py` | 8 | 0 | 0 | 0 |

## Not run / not applicable

| Item | Status | Reason |
|---|---|---|
| NLE browser E2E | `NOT_RUN` | explicitly outside this round |
| Mixed-provider per-unit execution | `NOT_APPLICABLE` | #22 V1 requires exactly one statically feasible candidate per unit |
| BMF audio / multi-clip / timed text / effects / canvas conversion | `NOT_RUN` (unsupported by design) | not declared; rejected before process start |
| GPU / complex composition | `NOT_RUN` | out of scope |
| Deployment isolation | `NOT_VERIFIED` | local task-owned runtime only |
