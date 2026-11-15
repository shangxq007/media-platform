# BMF Provider R1 — final closeout

**Task:** `MEDIA_PLATFORM_BMF_PROVIDER_INTEGRATION_R1`
**Branch:** `task/bmf-provider-integration-r1`
**Base main:** `fbc8428018251b14d7b4dd9d5b1287ebcbb24f9f`
**Inherited candidate:** `29cbcddb30f6b4e69bd1c1f348168d30d082370b` (tree `1a305518…`)
**Final candidate:** the commit that contains this file (parent is the inherited candidate); resolve
with `git log -1`.
**Result:** **PARTIAL** — capability closure and real BMF worker execution are verified; the formal
`RenderJob → Artifact → Delivery` E2E was **not run** this round (blockers below). No merge, no push.

## 1. Capability closure

### What the real plan requires

A real #20 → #21 run for one silent video clip (`PhysicalPlanningEntryServiceIntegrationTest`
fixture, verified by an added test) produces exactly three physical units:

| Physical unit (operation key) | Required capability | FFmpeg | BMF before | BMF now |
|---|---|---|---|---|
| `decode` | `video.decode` | declared + lowered | **not declared** | declared + lowered |
| `composite` | `render.composite` | declared + lowered | **not declared** | declared + lowered (single base input only) |
| `encode` | `render.output` | declared + lowered | **not declared** | declared + lowered |
| `transcode` | `media.transcode` | declared + lowered | declared + lowered | declared + lowered |

Before this round BMF declared only `media.transcode`, which no render node requires. With
`platform.render.media-provider=bmf` every unit was therefore unbindable (`UNIT_UNBINDABLE`); with the
default (`ffmpeg`) BMF was never a feasible candidate. That is the formal-render gap this round closes.

### Why the closure is not a second execution path or a contract change

- #22 V1 binds a unit only when **exactly one** declared candidate is statically feasible
  (`ProviderBindingEntryService` "V1 defines no selection policy"). Per-unit mixing of BMF and FFmpeg
  is therefore impossible without adding a selection policy — a #22 contract change that this task is
  not authorized to make. Mixed binding is reported `NOT_APPLICABLE` for that reason.
- The existing internal property `platform.render.media-provider` (`ffmpeg` default) excludes the
  other media provider before binding, so a BMF-selected job runs all its units on BMF. Default
  behaviour with no configuration remains FFmpeg.
- No platform contract, Flyway V1, Artifact fencing, RenderJob API or core provider SPI semantics were
  changed. The additions are BMF-provider-local: capability declarations, operation vocabulary, plan
  fields, lowering validation, graph program and sandbox argv validation.

### Implemented operations (bounded V1)

`BmfOperation` maps one platform capability to one physical unit key; the lowerer rejects anything
else before a process starts:

| Operation | Capability | BMF graph |
|---|---|---|
| `decode` | `video.decode` | decode → optional authored source trim (`ff trim` + `setpts=PTS-STARTPTS`) → encode H.264 MP4 |
| `composite` | `render.composite` | decode → encode H.264 MP4, one base input only |
| `encode` | `render.output` | decode → encode H.264 MP4 |
| `transcode` | `media.transcode` | decode → encode H.264 MP4 |

Rejected fail-closed: timed-text composite inputs, more than one materialized input, authored
execution intents, artifact capability parameters, source windows on non-decode units, freeze-point
windows, unknown operation keys, capabilities BMF does not declare, and a unit whose capability
requirement does not include the operation's own capability.

## 2. Production paths changed

| File | Change |
|---|---|
| `bmf-provider-module/.../BmfOperation.java` (new) | bounded operation → capability vocabulary |
| `bmf-provider-module/.../BmfCpuTranscodePlan.java` | adds `operation` and optional decode `sourceWindow` |
| `bmf-provider-module/.../BmfCpuTranscodeLowerer.java` | admits the four units, validates capability/window/cardinality |
| `bmf-provider-module/.../BmfCpuRuntimeAdapter.java` | parameterized BMF graph program (operation, trim start/duration) |
| `bmf-provider-module/.../BmfSandboxExecutionPolicyResolver.java` | validates the bounded operation/seconds argv and passes them into the sandbox |
| `bmf-provider-module/.../BmfRuntimeBundleVerifier.java` | accepts an in-bundle soname symlink only when it resolves inside the verified root (digest already covers the link target) |
| `provider-plugin-runtime-module/.../BmfProviderExtension.java` | declares `video.decode`, `render.composite`, `render.output` beside `media.transcode` |

The persisted worker confirmation prefix, FFmpeg provider behaviour, provider pin identity, capability
profile reference and runtime dependency declarations are unchanged.

## 3. Real BMF execution (L2, verified)

- Runtime bundle prepared from the pinned image with `scripts/dev/prepare-bmf-runtime`; manifest
  digests match `BmfCpuProvider.SOURCE_ARCHIVE_SHA256` / `RUNTIME_BUNDLE_SHA256`
  (`0d4c2e92…`) and `BmfRuntimeBundleVerifier.verify` returns true.
- `BmfCpuWorkerRuntimeIntegrationTest` ran through the real provider binding, bubblewrap sandbox and
  BMF 0.2.0 graph: output `ftyp` MP4, and the corrupt-input case fails through the native
  `PROCESS_NONZERO_EXIT` path.
- Output media: `/home/user/Documents/workspace/projects/media-platform-bmf-e2e-r1/worker-output.mp4`
  - SHA-256 `ba24b884f0de57d5b7fdc3e08c08f2e4b08cda47e63042c26274bbe48b4a31eb`
  - ffprobe: `h264`, 16×16, 1/1 fps, 1 frame, 1.000 s, 1526 bytes.
- Evidence: `media-platform-bmf-e2e-r1/` (`l2-worker-run.log`,
  `TEST-…BmfCpuWorkerRuntimeIntegrationTest.xml`, `worker-output.ffprobe.json`, media file).

This proves BMF executes media inside the formal worker runtime boundary. It does not by itself prove
the formal RenderJob path.

## 4. Formal RenderJob E2E — not run

`BMF_FORMAL_RENDERJOB`, `BMF_ARTIFACT_COMMIT` and `BMF_DELIVERY` are `NOT_RUN`. The blockers are
concrete:

1. The task-owned L3 harness [`scripts/dev/first-render`](../../scripts/dev/first-render) drives the
   FFmpeg-selected product path. It does not provision `platform.bmf-provider.enabled`,
   `platform.render.media-provider=bmf`, the BMF worker runtime root/admission evidence, or a
   BMF-compatible scenario.
2. No task-owned PG18/Temporal/OIDC runtime was started this round; the last L3 preflight reported
   PG18/Temporal/OIDC unreachable plus host FFmpeg missing `libx264` and no worker font.
3. The default product scenario includes timed text/audio semantics that BMF V1 correctly rejects, so
   a BMF run needs a dedicated silent single-clip scenario instead of silently relaxing the guard.

No test fixture artifact was presented as a RenderJob product; no fake completion, artifact or
delivery was produced.

## 5. Remaining risks

- **Raster/frame-rate parity:** BMF preserves the materialized input raster and frame rate; the FFmpeg
  composite path uses a bounded 320×240 canvas and `-r`. The same plan can therefore differ between
  providers. This is not hidden, but it needs an owner decision if exact parity is required.
- **Bounded capability claim:** BMF now declares `render.composite` while only supporting the single
  base-input, no-timed-text case; unsupported variants fail closed rather than mis-render.
- **Formal admission evidence:** BMF worker registration/admission with the new operations has not
  been exercised through the actual Temporal/Worker product loop.
- **Audio/multi-clip/effects:** not supported and explicitly rejected.

## 6. OpenCue current usage (read-only)

```
OPENCUE_CURRENT_USAGE: UNUSED_CODE / DOCUMENTATION_ONLY
```

Evidence:

- No Java import, dependency coordinate, configuration key or HTTP/gRPC client for OpenCue / Cuebot /
  RQD exists in the repository (`grep` over `*.java`, `*.kts`, `*.yml`, `*.yaml`, `*.properties`).
- `render-module/src/main/...` contains only comments and scenario flags (`"noOpenCue": "true"`) that
  assert OpenCue is *not* called; `render-module/build.gradle.kts` lists `opencue`/`OpenCue` as
  forbidden tokens outside comments.
- `docs/examples/opencue/*` are historical local smoke compose examples, not wired to any product
  entry; `docs/architecture/governance/project-state/deferred-items.yaml` keeps OpenCue deferred.
- The current dispatch authority is `RenderJob → Temporal → Worker Fabric → native provider`; no
  Job/Frame/Task identity from OpenCue exists and no worker obtains work through RQD.

```
CURRENT_DISPATCH_AUTHORITY: Temporal workflow + Worker Fabric native pull (provider-bound tasks)
RECOMMENDED_NEXT_STEP: DEFER (no owner decision required to keep it deferred)
```

No Cuebot, RQD, adapter or database was created.
