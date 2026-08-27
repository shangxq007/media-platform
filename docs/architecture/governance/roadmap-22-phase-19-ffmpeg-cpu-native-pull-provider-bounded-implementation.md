# Roadmap #22 Phase 19 — FFmpeg CPU Native Pull Provider Bounded Implementation

TASK_ID=ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_BOUNDED_IMPLEMENTATION
STATUS=IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW
BASE_SHA=7f0f29c1b7b7cf3d0517949c98e3b9aaba722313
BASE_TREE=88caba0e2b53ae617803a834aa26facd68222fa4
PHASE_19_STARTED=YES
PHASE_19_CLOSED=NO
ROADMAP_22=IN_PROGRESS
BMF_PROVIDER_POC_STARTED=NO
PHASE_20_STARTED=NO
PHASE_21_STARTED=NO
ROADMAP_23=NOT_STARTED
FAOF_3=NOT_AUTHORIZED
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_BOUNDED_IMPLEMENTATION_FINAL_REVIEW

## Candidate identity

CANDIDATE_SHA=DERIVED_FROM_GIT_AFTER_FREEZE
CANDIDATE_TREE=DERIVED_FROM_GIT_AFTER_FREEZE
CANDIDATE_PARENT=7f0f29c1b7b7cf3d0517949c98e3b9aaba722313
CANDIDATE_IDENTITY_SOURCE=GIT
CANDIDATE_IDENTITY_PERSISTED_VALUE=false

The immutable candidate SHA/tree are derived from the frozen Git commit and
reported with exact values after freeze. Embedding either value in this commit
would be self-referential and would change the value being embedded.

## Bounded result

This candidate introduces the first concrete CPU-only FFmpeg Native Pull
vertical slice without making the provider authoritative for placement,
capacity, reservation, usage, Artifact commit, fencing, or completion.

- `WorkerRuntimeSupportAdvertisement` is immutable runtime identity/kind plus
  static support evidence. The central matcher accepts it only together with an
  exact server-owned provider/runtime requirement and still requires probe,
  worker/host, runtime, sandbox, reservation, and resource evidence.
- `ffmpeg-provider-module` owns stable provider family identity `ffmpeg`, stable
  implementation identity `ffmpeg.cpu.native-pull.v1`, the exact binding, the
  bounded probe, the typed transcode plan, deterministic lowering, typed argv,
  and sandbox policy composition. The canonical modules do not depend on it.
- The admitted semantic shape is exactly one canonical `transcode`
  `PhysicalPlanUnit`, one platform-materialized source input, and one
  authoritative output. Parameterized, unsupported, multi-membership, and
  cardinality variants fail closed.
- FFmpeg produces deterministic CPU H.264/yuv420p fragmented MP4 bytes on
  `pipe:1`. Only `RuntimeClosedLoopOrchestrator` materializes input, stages the
  stream, durably publishes it, commits the immutable Artifact, publishes
  reuse, fences ownership, and decides completion.
- Concrete process execution composes through
  `SandboxRuntimeCommandExecutor` and the Phase 17 bounded sandbox launchers.
  There is no shell string and no concrete-provider `ProcessBuilder`.

## CLEAN FORWARD disposition

| Disposition | Exact bounded surfaces |
| --- | --- |
| REUSE_AS_CANONICAL | Provider IDs/descriptors/profiles/bindings; `ExecutableTask` and canonical `PhysicalPlanUnit`; `PlanLowerer`, `RuntimeAdapter`, `ProviderNativeRuntimeBinding`; sandbox enforcement; runtime eligibility; Phase 16 staging/commit/fencing/completion |
| REUSE_MECHANICS_ONLY | Existing render-local FFmpeg fixture/probe knowledge; no authority transfer |
| MIGRATE_REDESIGN | None |
| DELETE_SHADOW | None; old render-local FFmpeg surfaces remain untouched and non-authoritative |
| DEFER | BMF, OpenCue, Resource Accounting, Phase 21, GPU/NVIDIA/cloud, optimization, FAOF-3/4, Roadmap #23, dynamic plugin runtime |

UNCLASSIFIED_TOUCHED_SURFACES=0

## Failure and safety evidence

Unsupported semantics, invalid binding/input, sandbox rejection, launch
failure, nonzero exit, cancellation, timeout, cleanup failure, empty output,
and unknown runtime failure are typed and fail closed. Failure and cancellation
do not stage a successful output, commit an Artifact, publish reuse, or complete
the task. Truncated provider output is typed as `PROCESS_OUTPUT_TRUNCATED` and
fails closed before candidate bytes are accepted, so partial media bytes cannot
be staged or committed. Cancellation uses the Phase 17 process-tree cleanup path
and the real integration test verifies no surviving FFmpeg process from the
cancelled run.

Exact input paths are resolved only from the matching
`MaterializedExecutionInput` inside sandbox policy composition. Staging-root
escape and unknown input-token tests are rejected. The provider has no output
filesystem or Artifact API authority.

## Real media and supply-chain observation

The integration test generates a tiny 64x48, five-frame source from FFmpeg
`lavfi` without network or copyrighted assets. It executes the real provider
binding inside bubblewrap, validates the committed output using real `ffprobe`,
and checks H.264, yuv420p, 64x48 dimensions, and positive duration.

Observed local tools:

```text
ffmpeg version 7.0.2-static https://johnvansickle.com/ffmpeg/  Copyright (c) 2000-2024 the FFmpeg developers
built with gcc 8 (Debian 8.3.0-6)
configuration: --enable-gpl --enable-version3 --enable-static --disable-debug --disable-ffplay --disable-indev=sndio --disable-outdev=sndio --cc=gcc --enable-fontconfig --enable-frei0r --enable-gnutls --enable-gmp --enable-libgme --enable-gray --enable-libaom --enable-libfribidi --enable-libass --enable-libvmaf --enable-libfreetype --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenjpeg --enable-librubberband --enable-libsoxr --enable-libspeex --enable-libsrt --enable-libvorbis --enable-libopus --enable-libtheora --enable-libvidstab --enable-libvo-amrwbenc --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libdav1d --enable-libxvid --enable-libzvbi --enable-libzimg
ffprobe version 7.0.2-static https://johnvansickle.com/ffmpeg/  Copyright (c) 2007-2024 the FFmpeg developers
built with gcc 8 (Debian 8.3.0-6)
configuration: --enable-gpl --enable-version3 --enable-static --disable-debug --disable-ffplay --disable-indev=sndio --disable-outdev=sndio --cc=gcc --enable-fontconfig --enable-frei0r --enable-gnutls --enable-gmp --enable-libgme --enable-gray --enable-libaom --enable-libfribidi --enable-libass --enable-libvmaf --enable-libfreetype --enable-libmp3lame --enable-libopencore-amrnb --enable-libopencore-amrwb --enable-libopenjpeg --enable-librubberband --enable-libsoxr --enable-libspeex --enable-libsrt --enable-libvorbis --enable-libopus --enable-libtheora --enable-libvidstab --enable-libvo-amrwbenc --enable-libvpx --enable-libwebp --enable-libx264 --enable-libx265 --enable-libxml2 --enable-libdav1d --enable-libxvid --enable-libzvbi --enable-libzimg
```

The complete exact configuration lines remain available from the bounded
probe/test command evidence. No binary is vendored and no runtime network
download is added. This records observed build metadata only and does not claim
license closure.

## Scope closure

Core production modules have no concrete module dependency/import. Canonical
modules contain no FFmpeg type. The provider contains no raw shell API, direct
process builder, Artifact commit authority, provider/worker/backend/device
identity collapse, BMF/OpenCue/GPU/cloud/accounting/optimization/formal or
Roadmap #23 implementation. Phase 20 and Phase 21 remain not started. Phase 19
is not closed.

## Local candidate validation

Machine-readable JUnit XML after `--rerun-tasks` records:

- `sandbox-isolation-module`: 37 tests, 37 passed, 0 failures, 0 errors, 0 skipped;
- `worker-fabric-module`: 318 tests, 318 passed, 0 failures, 0 errors, 0 skipped;
- `ffmpeg-provider-module`: 9 tests, 9 passed, 0 failures, 0 errors, 0 skipped.

The real FFmpeg/ffprobe integration, Phase 19 zero guard, Phase 16 clean-forward
guard, Phase 17 ledger guard and 44-mutation RED matrix, change-impact
classifier 19-case matrix, and repository architecture-drift guard pass. Full
repository tests were not run during bounded implementation. The independent
final review remains pending.
