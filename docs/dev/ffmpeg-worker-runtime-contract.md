# FFmpeg worker runtime contract

> **Status:** CURRENT (2026-11-15). applies to the formal `ffmpeg-worker` profile.

The worker executes media through a configured FFmpeg/FFprobe pair inside the sandbox, never through
a host default. Configuration lives in
[`application-ffmpeg-worker.yml`](../../platform-app/src/main/resources/application-ffmpeg-worker.yml).

## Configuration

| Setting | Environment variable | Default | Requirement |
|---|---|---|---|
| FFmpeg binary | `FFMPEG_WORKER_FFMPEG_PATH` | `/usr/bin/ffmpeg` | absolute path, executable, inside the worker runtime |
| FFprobe binary | `FFMPEG_WORKER_FFPROBE_PATH` | `/usr/bin/ffprobe` | absolute path, executable |
| Subtitle font | `FFMPEG_WORKER_FONT` | empty | required for timed-text/subtitle rasterization; blank fails closed at provider lowering |
| Bubblewrap | `FFMPEG_WORKER_BWRAP_PATH` | `/usr/bin/bwrap` | sandbox executable |

The configured FFmpeg must expose:

- filter `drawtext` (text/subtitle rasterization)
- filter `overlay` (compositing)
- encoder `libx264` (H.264 render master)
- encoder `qtrle` (lossless intermediate/output when requested)

A missing binary, filter, encoder or font fails the render closed; it must never produce a partial or
silently degraded artifact.

## Verification instead of discovery

Do not probe the worker by submitting real renders first. Run the cheap preflight:

```sh
python3 scripts/dev/verify preflight --level l3
```

It reports `BLOCKED` with explicit blockers when the worker FFmpeg lacks a required feature, when the
font is unset/unreadable, or when the PG18/Temporal/OIDC/browser prerequisites are absent. Only start
L3 when preflight returns `PASS`. Repeated retries against an unchanged blocked environment are
forbidden; fix the environment or report the blocker.

## Packaging and GPL compliance

A build that includes `libx264` is GPL-licensed. When redistributing such a binary or image:

- keep the license text and the corresponding-source offer/pointer with the artifact;
- record the exact build identity (version, configure flags, source URL) in deployment evidence;
- do not mix a GPL build into a distribution that claims incompatible licensing.

The local validation entry stages a managed FFmpeg from the declared Bytedeco artifact; the host
FFmpeg only prepares synthetic source media and is not the worker runtime.

## Scope

Only hard-subtitle burn-in is implemented today. Full multilingual subtitle management (track
selection, styling policy, per-language assets) remains a future product capability and is not
provided by this contract.
