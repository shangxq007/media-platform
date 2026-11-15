# BMF Provider R1 — Final Closeout

**Task:** `MEDIA_PLATFORM_BMF_PROVIDER_INTEGRATION_R1` — formal product E2E closeout
**Branch:** `task/bmf-provider-integration-r1`
**Base main:** `fbc8428018251b14d7b4dd9d5b1287ebcbb24f9f` (tree `0850ebfe56aab58de773ca97329d08cb1893059b`)
**Inherited candidate:** `5b897349b16b2f76825b43e210ce945e8bd64874` (tree `3bfe385ac0ea57d2408c11980b56cc8bfce70758`)
**Verified code commit:** `a5b019658efb9bac3d0fae930b47a3886d530ef2` (tree `7c9ad806fd5d37653c9cd54fef1f6d648cf95d8a`) — the exact tree both L3 runs executed
**Final candidate:** the commit that contains this file (its only difference from the verified code commit is this report and the test matrix)
**Result:** **PASS** — a real BMF-participating formal RenderJob completed, committed an Artifact through the commit authority and was downloaded through the formal Delivery API. No merge, no push.

## 0. Final status

```text
MEDIA_PLATFORM_BMF_PROVIDER_INTEGRATION_R1:

CANDIDATE_COMMIT: a5b019658efb9bac3d0fae930b47a3886d530ef2 (verified code) + this report commit
CANDIDATE_TREE:   7c9ad806fd5d37653c9cd54fef1f6d648cf95d8a (verified code) + reports

BMF_FORMAL_RENDERJOB:   PASS  rj_0a4cae9e6e5b4cefb5581643ca4c6ade
BMF_PROVIDER_BINDING:   PASS  bmf.cpu.v1 only; bound graph digest == persisted expected digest
BMF_WORKER_EXECUTION:   PASS  3 worker attempts, 3 real BMF graph processes, exit 0
BMF_ARTIFACT_COMMIT:    PASS  2 DERIVED_MEDIA + 1 RENDER_MASTER, state AVAILABLE
BMF_DELIVERY:           PASS  dlv_797a6cc0f2bb4d528dad278d99b1762f, 1584 bytes over HTTP
BMF_MEDIA_VALIDATION:   PASS  MP4 / h264 High / 320x240 / 1/1 fps / 1.000 s / 1 frame / clean decode
FFMPEG_REGRESSION:      PASS  rj_12b5ef780e974b4eb01575c57262ad9c through the existing entry
ARCHITECTURE_GUARDS:    PASS  architecture drift 219 checks + BMF module guards

CONFIRMED_BLOCKERS: none for this bounded scenario
UNVERIFIED_RISKS: raster/frame-rate preservation outside the matched-canvas/matched-rate case;
                  audio, multi-clip, transitions, effects and timed text on BMF; mixed-provider units
FINAL_RESULT: PASS (bounded: one silent single-clip video vertical slice)
```

## 1. What was not re-developed

No BMF technology selection, SDK research, Provider SPI design, runtime bundle build, capability
declaration or lowering was redone. The inherited candidate already carried the verified 0.2.0 runtime
bundle, the provider-native binding, the bubblewrap worker execution, the four bounded lowerings and
the architecture guards. This round only added the *product entry* that drives the formal RenderJob
path, two provider-aware scenario fixtures and a BMF evidence collector.

## 2. Scenario contract (§4.1/§4.3)

Fixed, minimal and contract-native:

| Property | Value | Why |
|---|---|---|
| tracks | 1 video track, 1 clip | no multi-layer, no transition |
| timed text | 0 | BMF V1 rejects timed-text composite inputs |
| audio | none (no audio track, `AudioMix.EMPTY`) | BMF V1 does not mix audio |
| effects | none | no transform/crop/opacity/blend node |
| source raster | 320×240 | equals the formal bounded render canvas |
| source frame rate | 1/1 | equals the requested extent frame rate |
| duration | 1.000 s | extent `startMillis=0 endMillis=1000` |
| codec | H.264 MP4 (`libopenh264` synthetic source) | the provider's pinned codec constraint |

The revision is produced by the maintained Operation APIs — authenticated `/source-media` upload →
`timeline-operations/initialize` → `timeline-operations/add-media-clip/preview` → `.../apply`
(`rateNumerator/rateDenominator = 1/1`) → `/api/render/projects/{project}/timeline/revisions/{id}/snapshot`.
The RenderJob is then submitted through `POST /api/render-jobs`. No BMF-specific endpoint, controller or
submission protocol was added.

**Composite semantics.** In the single-clip case #20 emits `COMPOSITE` as an aggregate node with no
requirement parameters (no canvas, transform, overlay or output description of its own); the canvas is
the bounded 320×240 raster the FFmpeg composite builds and the frame rate is the extent rate. The BMF
`render.composite` lowering preserves the input raster and rate. With `source raster == canvas` and
`source rate == extent rate`, the pass-through is semantically equivalent for this scenario, which is
exactly the condition §4.3 permits. This is **not** a general composite claim: any canvas conversion,
transform, overlay, multi-clip or timed-text composition remains unsupported and is rejected before a
process starts.

## 3. Formal planning and binding (C20 → C21 → C22)

All values below are read from the product's own persisted records, not from test constants.

| Fact | Value |
|---|---|
| RenderJob | `rj_0a4cae9e6e5b4cefb5581643ca4c6ade` |
| Execution plan | persisted `render_binding_inputs.plan_ref` / `plan_digest` |
| Physical plan digest | `23bcb70e10daa3616025c84f7cc62cd547aa28378c4084c323d5813181748798` |
| Physical units | `decode` → `video.decode`, `composite` → `render.composite`, `encode` → `render.output` |
| Persisted candidate set | `bmf.cpu.v1` only (2492 canonical bytes, sha256 `b47e520e…`) |
| Excluded candidate | `ffmpeg.cpu.native-pull.v1` absent from the persisted set |
| Expected bound-graph digest | `ed239e8d4a450d2c7b9a5234fd51d9495aa1e63faee6386626860c8c43c9429e` |
| Dispatched graph digest | identical `ed239e8d…` (`render_graph_execution.graph_digest`) |
| Candidate-set digest column | `null` in this schema version (nullable, not written by the entry) |

Selection: `platform.bmf-provider.enabled=true` adds the in-process BMF contribution to the API catalog,
`platform.render.media-provider=bmf` removes the FFmpeg candidate before #22 binding, and #22 V1 binds a
unit only when exactly one declared candidate is statically feasible — so every unit binds BMF and no
mixed execution is possible by construction (mixed binding is `NOT_APPLICABLE` under the V1 contract).

## 4. Worker execution (Temporal → Worker Fabric → BMF)

| Fact | Value |
|---|---|
| Workflow | `render:tenant-fr-e2e:rj_0a4cae9e6e5b4cefb5581643ca4c6ade` — `COMPLETED`, run `01a1df55-75c7-75f6-815a-f3cb82b0f0bc`, 24 history events |
| Worker attempts | 3 × `NATIVE_PULL_WORKER`, generation 1, all `SUCCEEDED` |
| Attempt ids | `attempt-453e62d9…` (decode), `attempt-991b1925…` (composite), `attempt-0454f54f…` (encode) |
| Executable | `<verified-runtime>/host-bundle/usr/bin/python3.9` (resolved inside the BMF runtime bundle) |
| Sandbox | provider-native bubblewrap sandbox, network none, read-only runtime/input mounts |
| Graph processes | 3, exit code 0, captured stdout untruncated (7941 / 5009 / 5003 bytes with recorded SHA-256) |
| Source window | decode `start=0 duration=1`; composite and encode carry no authored window (0/0) |
| Production | 1584 bytes per unit; the process output file is the committed content in every case |
| FFmpeg fallback | none: no `FFMPEG_ARGV` invocation exists in the Worker log for this job |

**Chain of custody.** Each unit's materialized input digest (embedded in the sandbox argv path
`.../materialized/sha_256/<xx>/<digest>`) is the previous unit's committed content:

```text
source d9848a69… --decode--> ceb6359987… --composite--> ceb6359987… --encode--> master ceb6359987…
```

`decode.input == uploaded source digest`; `composite.input` and `encode.input` are both committed
`DERIVED_MEDIA` artifacts; each unit's captured output-file byte length equals the byte length of the
content it committed (`decode.produced == 1584`, `composite.produced == 1584`, `encode.produced == 1584`).
BMF's decode→encode is deterministic here, so the intermediate content and the render master share one
content digest; the Artifact identities remain distinct.

**Graph-log boundary (disclosed, not hidden).** The runtime captures the child process stdout, records
its exact byte count and SHA-256 (`PROCESS_OUTPUT … stdoutBytes/ stdoutSha256`) and does not persist the
bytes; the graph's own log lines are therefore evidenced by that recorded digest plus the 1:1 output-file
↔ committed-content match above, not by a stored log file. Persisting child stdout would be a platform
observability change outside this task's authorized scope and was not made.

`wf_execution_assignment.worker_runtime_id` is `local-ffmpeg` because that is the configured identity of
the single local worker runtime; the backend is `NATIVE_PULL_WORKER`, the bound provider is
`bmf.cpu.v1`, and the executed process is the BMF runtime Python. The identifier is not evidence of
FFmpeg execution.

## 5. Artifact commit and Delivery

| Fact | Value |
|---|---|
| Artifacts | 2 × `DERIVED_MEDIA` + 1 × `RENDER_MASTER`, all `AVAILABLE`, 1584 bytes |
| Render master | `render-task-02d30b1e74bd27c2c6cd25db43368fe9be0611b8ec1e83854631` |
| Completion authority | 3 `wf_completion_event` rows, one per attempt |
| Quota accounting | exactly 1 `render-job:<id>:completion` operation |
| Delivery | `dlv_797a6cc0f2bb4d528dad278d99b1762f`, `READY`, created `201`, idempotent replay `200` |
| Download | `GET /api/projects/project-fr-e2e/deliveries/dlv_…/result` → `200`, 1584 bytes |
| Download SHA-256 | `ceb6359987869fe1627768bad6391352a7f8162618d71a71796b01a274e89c0f` = render-master content digest |
| Fencing | completion events + artifact state/authority rows; zero leftover database sessions |

The delivered bytes are the committed Artifact content read back through the formal Delivery result
endpoint — not a Worker temporary file copied into a delivery directory.

## 6. Media semantics of the delivered file

| Property | Observed |
|---|---|
| container | MP4 (`mov,mp4,m4a,3gp,3g2,mj2`) |
| codec | `h264`, profile High |
| raster | 320 × 240 |
| frame rate | `1/1` (`r_frame_rate` = `avg_frame_rate`) |
| duration | 1.000 s |
| frames | 1 (`-count_frames` / `nb_read_frames`) |
| pixel format | `yuv420p` |
| audio streams | 0 |
| full decode | `ffmpeg -v error -i … -f null -` exit 0, no error output |
| SHA-256 | `ceb6359987869fe1627768bad6391352a7f8162618d71a71796b01a274e89c0f` |
| source SHA-256 | `d9848a69ef36ef2b1b75e7af716609c977efcbf281e8bf80da063812cf00603c` (differs: not a copy) |

**Scope of this claim.** One silent 320×240 / 1 fps / 1 s clip proves the minimal vertical slice only.
It does not establish BMF support for general transcode, multi-input composition, complex timelines,
audio, subtitles, effects or GPU work — none of which this provider declares for V1.

## 7. FFmpeg regression

The existing FFmpeg product entry (`scripts/dev/first-render --timeline-operation-editing`) was run
unmodified in the same task-owned L3 shape:

| Fact | Value |
|---|---|
| RenderJob | `rj_12b5ef780e974b4eb01575c57262ad9c`, HTTP `201` |
| Execution plan | `1fb77617-3e67-4353-8a7f-ee98a0a05389` |
| Worker execution | 4 FFmpeg processes at `/usr/local/lib/media-platform/ffmpeg/ffmpeg` (`FFMPEG_ARGV` present) |
| Status history | `QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED` |
| Render master | `render-task-d5fe7ac7412d644664e51e36708436a69bb88c81eb4ae0dc9655`, 1851 bytes |
| Download | `200`, 1851 bytes, SHA-256 `287ef2638e8e3928dfd10f13a28559575a49c92b13ee55c8c780fea09af211cb` |
| Probe | `h264`, 320×240, 2.000 s |
| Quota | exactly 1 completion operation |
| Result | `FIRST_RENDER_LOCAL_VALIDATION_PASS`, cleanup `PASS` |

Additional regression evidence: the FFmpeg L3 preflight still fails closed with the same five blockers
it reported before this change (`worker ffmpeg lacks libx264`, three unreachable external services, no
`FFMPEG_WORKER_FONT`), while only the BMF preflight relaxes the subtitle-font requirement.

## 8. Negative checks, guards and L1/L2

| Check | Result | Evidence |
|---|---|---|
| BMF not enabled → FFmpeg still selected | PASS | `RevisionRenderPlanningAndBindingServiceProviderSelectionTest`; FFmpeg L3 run above |
| Existing FFmpeg entry intact | PASS | §7 |
| BMF capability it does not declare is rejected | PASS | `BmfCpuTranscodeSeamsTest` (6) — unknown operation/capability, timed-text composite, authored intents |
| Missing BMF runtime fails closed | PASS | entry guard test (exit 2, no output created); `BmfProviderExtension.createRuntimeBinding` refuses an unconfigured/unverified root |
| Wrong bundle digest is rejected | PASS | entry guard test (manifest declares the pinned digest, computed digest differs → exit 2) |
| Failed execution produces no COMPLETED Artifact | PASS | `BmfCpuWorkerRuntimeIntegrationTest.corrupt_input_fails_through_the_native_process_error_path` (`PROCESS_NONZERO_EXIT`, no output); cancel job commits 0 artifacts |
| Authorization / identity isolation | PASS | 5 artifact negatives (401/403/404/400) + 3 authorization negatives in the same run |
| Architecture guard | PASS | `scripts/check-architecture-drift.sh --quiet` → 219 checks; `BmfArchitectureGuardTest` (8) |

L1 (recorded manifests under the evidence package):

| Selection | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `bmf-provider-module` (full) | 22 | 0 | 0 | 2 (runtime tests need env) |
| `bmf-provider-module` runtime tests with the verified bundle | 2 | 0 | 0 | 0 |
| `provider-plugin-runtime-module` `BmfProviderExtensionTest` | 6 | 0 | 0 | 0 |
| `platform-app` provider selection + worker plugin configuration | 7 | 0 | 0 | 1 |
| `scripts/dev/test_first_render_entry.py` | 8 | 0 | 0 | 0 |

L2 is the real Worker execution in the L3 run (Temporal + PG18 + sandbox + BMF graph) plus the
provider runtime integration test above; no separate mock PG18 suite was added.

## 9. Changes made in this round

| File | Change |
|---|---|
| `scripts/dev/first-render` | `--provider {ffmpeg,bmf}` (default `ffmpeg`), `--scenario`, `--bmf-runtime-root`; fail-closed verified-bundle preflight (manifest, Python, bundle digest) before any output/service is created; scenario-aware exercise selection; provider/scenario recorded in `run.json`/`product-result.json` |
| `scripts/dev/_local_validation/run_apps.py` | opts the API into `platform.bmf-provider.enabled` + `platform.render.media-provider=bmf`, gives the Worker the verified runtime root, and exposes that root read-only in **both** the outer and the nested bootstrap bubblewrap boundary |
| `scripts/dev/_local_validation/exercise_single_video_no_audio.py` (new) | the minimal scenario: real upload, Operation-produced revision, canonical snapshot, formal RenderJob submission, project-scoped delivery permissions |
| `scripts/dev/_local_validation/bmf_render_evidence.py` (new) | reads only product-path evidence (persisted bound-graph inputs, Temporal workflow, Worker process record, committed artifacts) and exercises the formal Delivery API plus media semantics |
| `scripts/dev/verify` | provider-aware preflight and `e2e --provider/--scenario/--bmf-runtime-root`; the FFmpeg preflight keeps every existing check |
| `scripts/dev/test_first_render_entry.py` | fail-closed guards for the new provider inputs (5 new cases) |

No production contract, controller, SPI, Flyway migration, Artifact fencing, Revision CAS or RenderJob
API was modified. No capability was added.

## 10. Found but out of scope (Owner decision)

`POST /api/render/timeline-snapshots` cannot persist its import command on this base:
`TimelineSnapshotController` derives `commandId = "import-" + sha256(...)` (71 chars) while
`apply_command.apply_command_id` is `varchar(64)`, so the request fails with
`ERROR: value too long for type character varying(64)` wrapped as `APPLY_FAILURE` **for every provider**.
The legacy `exercise_first_render` fixture uses that endpoint and is therefore unusable; this round
routed the scenario through the maintained Operation APIs instead of changing the controller. Fixing it
(the id derivation or a migration) is a product decision outside this task's authorized scope.

## 11. Known boundaries / not run

- BMF composite remains single-base-input only; timed text, multi-clip, audio, transitions, effects and
  any canvas/rate conversion are rejected before process start.
- The NLE browser E2E was not re-run (explicitly out of scope for this round).
- Mixed-provider unit binding is `NOT_APPLICABLE` under #22 V1 (one candidate per unit).
- GPU, complex composition and multi-input transcoding remain unsupported and untested.
- The platform does not persist captured child-process stdout (see §4).
- OpenCue remains unused: no import, dependency, configuration key or client exists; the current
  dispatch authority is Temporal + Worker Fabric native pull, unchanged by this round.

## 12. Reproduction

```bash
# BMF formal RenderJob E2E (task-owned PG18, Temporal, OIDC, API, Worker, verified BMF bundle)
python3 -B scripts/dev/verify e2e \
  --output <new-external-dir> \
  --provider bmf --scenario single-video-no-audio \
  --bmf-runtime-root <verified-bmf-runtime-root>

# provider-aware preflight only
python3 -B scripts/dev/verify preflight --level l3 --provider bmf \
  --bmf-runtime-root <verified-bmf-runtime-root>

# existing FFmpeg entry (regression)
python3 -B scripts/dev/first-render --output <new-external-dir> --timeline-operation-editing

# L1
python3 -B scripts/dev/verify l1 --module bmf-provider-module
python3 -I -B scripts/dev/test_first_render_entry.py
```

The raw evidence package (`00-manifest.json` with per-file SHA-256, commit/tree binding) is stored
outside the repository at `media-platform-bmf-e2e-r1/` together with the two L3 run directories.
