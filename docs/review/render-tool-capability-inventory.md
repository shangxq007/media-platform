# Render Tool Capability Inventory

> **Status:** historical inventory record — the live ambient inventory was removed by Phase20 P20-I7
> **Created:** 2026-06-28
> **Scope:** historical local tool availability and version-detection context for provider planning

## Purpose

This document preserves the historical render-tool inventory context used for provider planning. Phase20 P20-I7 removed the live ambient `RenderToolCapabilityInventory` and its process-level PATH/version probes. The authoritative implementation-closure evidence is `docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-implementation-closure-v1.json`. This historical record does not implement or enable new providers, and the removal does not change dispatch semantics.

## Tool Availability Summary

| Tool | Binary | Status | Version Detection | Production Ready |
|------|--------|--------|-------------------|------------------|
| FFmpeg | `ffmpeg` | ✅ Available | `ffmpeg -version` | ✅ Yes (baseline) |
| ffprobe | `ffprobe` | ✅ Available | `ffprobe -version` | ✅ Yes (baseline) |
| Remotion | `npx remotion` | ⚠️ Check manually | `npx remotion --version` | ❌ POC/SPIKE |
| MLT (melt) | `melt` | ⚠️ Check manually | `melt --version` | ❌ POC/SPIKE |
| Blender | `blender` | ⚠️ Check manually | `blender --version` | ❌ POC/SPIKE |
| Natron | `natron` | ⚠️ Check manually | `natron --version` | ❌ POC/SPIKE |
| GStreamer | `gst-launch-1.0` | ⚠️ Check manually | `gst-launch-1.0 --version` | ❌ Hold |
| GPAC (MP4Box) | `MP4Box` | ⚠️ Check manually | `MP4Box -version` | ❌ POC/SPIKE |
| Libass | (via FFmpeg) | ✅ Available | N/A (FFmpeg filter) | ✅ Yes |
| OpenFX | N/A | N/A | Capability model only | ❌ Future |

## Existing Capability Matrix

The platform already has a comprehensive capability matrix at:

`docs/render/capability-matrix.md`

Key facts:
- **FFmpeg**: Production, P0, auto-dispatch enabled. Handles trim, transcode, mux, demux, extract_audio, thumbnail, output_normalize.
- **Remotion**: POC, P1, auto-dispatch disabled. Handles caption_burn_in, caption_effects, template_render.
- **MLT**: POC, P1, auto-dispatch disabled. Handles timeline_render, multi_track, transition, audio_mix.
- **GPAC**: POC, P1, auto-dispatch disabled. Handles package_hls, package_dash, package_cmaf.
- **Libass**: POC, P1. Handles subtitle_overlay, ass_ssa_render, caption_burn_in.
- **Blender**: POC, P1. Handles 3d_render.
- **GStreamer/VapourSynth/Natron**: Hold/P2. Not enabled for dispatch.
- **BMF**: Spike/P2-P3. Not enabled for dispatch.

## Historical Runtime Tool Detection

The platform formerly had a `RenderToolCapabilityInventory` at:

`render-module/src/main/java/.../infrastructure/RenderToolCapabilityInventory.java`

Phase20 P20-I7 deleted this ambient global inventory. Its former behavior was limited to reporting local tool/version availability without affecting dispatch, installing tools, or upgrading provider status. There is no live `RenderToolCapabilityInventory` API to inject or call.

See the Phase20 implementation-closure JSON linked above for the exact deletion predicates and mechanical evidence.

## Provider Registry

The platform has a `RenderProviderRegistry` at:

`render-module/src/main/java/.../infrastructure/RenderProviderRegistry.java`

This registry tracks:
- Registered providers
- Provider capabilities
- Health status
- Profile-based capability filtering

## Provider Status Classification

| Status | Meaning | Auto Dispatch |
|--------|---------|---------------|
| Production | Stable, tested, auto-dispatch enabled | ✅ |
| POC | Proof of concept, manual dispatch only | ❌ |
| Spike | Experimental, not for production | ❌ |
| Hold | Deferred, not actively developed | ❌ |
| Deprecated | Superseded by another provider | ❌ |

## Architecture Boundaries

- Provider status does NOT change dispatch semantics
- Missing tools are reported as unavailable, not failure
- Do not install tools globally in this task
- Do not run untrusted downloads
- Provider statuses remain conservative unless explicitly upgraded
- OpenFX is a capability model / effect plugin interface, not an executable provider

## Related Documents

- `docs/render/capability-matrix.md` — Full provider capability matrix
- `docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-implementation-closure-v1.json` — Phase20 implementation-closure evidence
- `docs/architecture/public-capability-architecture.md` — Capability architecture
- `docs/review/capability-catalog.md` — Capability catalog
- `docs/review/capability-resolution.md` — Capability resolution logic
