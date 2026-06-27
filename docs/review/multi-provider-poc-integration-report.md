---
status: implementation-report
created: 2026-06-28
scope: render-module
truth_level: current
owner: platform
---

# Multi-Provider POC Integration Report

## Summary

This report documents the integration pattern for multiple render/tool providers as conservative POC/SPIKE providers, without making any new provider production-eligible and without changing the FFmpeg baseline.

## Safe Provider Integration Pattern

The proven integration pattern is:

```
TimelineRevision / RenderJob
→ StorageRuntime materialized input
→ provider-specific local execution or dry-run/smoke
→ StorageRuntime output registration when real output is produced
→ Product FINAL_RENDER or derived Product
→ ProductDependency lineage
→ R7 status/result API remains safe
```

## Provider Status Matrix

| Provider | Status | Priority | Auto Dispatch | Integration Smoke | Notes |
|----------|--------|----------|---------------|-------------------|-------|
| FFmpeg | PRODUCTION | P0 | ✅ | ✅ R8 real render | Only production baseline |
| Remotion | POC | P1 | ❌ | ✅ dry-run metadata | Real render blocked by missing CLI |
| MLT | POC | P1 | ❌ | ✅ dry-run metadata | Timeline/NLE provider |
| GPAC | POC | P1 | ❌ | ✅ dry-run metadata | Packaging provider |
| Blender | SPIKE | P1 | ❌ | ✅ dry-run metadata | 3D render provider |
| GStreamer | HOLD | P2 | ❌ | ✅ dry-run metadata | Media pipeline provider |
| Natron | HOLD | P3 | ❌ | ✅ dry-run metadata | VFX compositing provider |
| Libass | POC | P1 | ❌ | ✅ via FFmpeg | Subtitle burn-in |
| BMF | SPIKE | P2-P3 | ❌ | ❌ not implemented | GPU/AI media pipeline |
| OFX | DEPRECATED | P3 | ❌ | ❌ capability model only | Java2D simulation, not real OFX |

## Tool Availability Matrix

| Tool | Binary | Status | Version Detection | Provider Status |
|------|--------|--------|-------------------|-----------------|
| FFmpeg | `ffmpeg` | ✅ Available | `ffmpeg -version` | PRODUCTION |
| ffprobe | `ffprobe` | ✅ Available | `ffprobe -version` | PRODUCTION |
| Remotion | `npx remotion` | ❌ Not available | `npx remotion --version` | POC |
| MLT (melt) | `melt` | ⚠️ Check manually | `melt --version` | POC |
| Blender | `blender` | ⚠️ Check manually | `blender --version` | SPIKE |
| Natron | `natron` | ⚠️ Check manually | `natron --version` | HOLD |
| GStreamer | `gst-launch-1.0` | ⚠️ Check manually | `gst-launch-1.0 --version` | HOLD |
| GPAC (MP4Box) | `MP4Box` | ⚠️ Check manually | `MP4Box -version` | POC |
| Libass | (via FFmpeg) | ✅ Available | N/A (FFmpeg filter) | POC |
| OpenFX | N/A | N/A | Capability model only | DEPRECATED |

## Integration Smoke Tests

### ProviderIntegrationSmokeTest

Proves the safe integration pattern works for multiple providers:
- FFmpeg is the only production-eligible provider
- Non-FFmpeg providers are not production-eligible
- Non-FFmpeg providers are eligible for manual/experiment jobs
- StorageRuntime input materialization works for S3-compatible provider type
- StorageRuntime output registration creates Product with lineage
- All providers have valid capability declarations

### RemotionProviderSmokeTest (dry-run)

Validates Remotion provider without requiring real Remotion CLI:
- Provider is POC status
- Has correct capabilities (caption_burn_in, caption_effects, template_render)
- Does not handle video editing capabilities
- Not eligible for production jobs
- Eligible for manual/experiment jobs
- autoDispatch is false
- Metadata does not expose sensitive fields
- Props contract validates correctly

### GPACProviderSmokeTest (dry-run)

Validates GPAC provider without requiring real MP4Box:
- Provider is POC status
- Has packaging capabilities (package_hls, package_dash, package_cmaf)
- Does not handle video editing capabilities
- Not eligible for production jobs
- Eligible for manual/experiment jobs

### MLTProviderSmokeTest (dry-run)

Validates MLT provider without requiring real melt:
- Provider is POC status
- Has timeline capabilities (timeline_render, multi_track)
- Not eligible for production jobs
- Eligible for manual/experiment jobs

### BlenderProviderSmokeTest (dry-run)

Validates Blender provider without requiring real blender:
- Provider is SPIKE status
- Has 3D capabilities (3d_render)
- Not eligible for production jobs
- Eligible for manual jobs

### NatronProviderSmokeTest (dry-run)

Validates Natron provider without requiring real Natron:
- Provider is HOLD status
- Has VFX capabilities (node_effects, vfx_composite)
- Not eligible for production jobs
- Eligible for manual jobs
- autoDispatch is false

### GStreamerProviderSmokeTest (dry-run)

Validates GStreamer provider without requiring real gst-launch-1.0:
- Provider is HOLD status
- Has pipeline capabilities (realtime_pipeline, streaming)
- Not eligible for production jobs
- Eligible for manual jobs
- autoDispatch is false

## Architecture Compliance

- ✅ FFmpeg remains the only production baseline
- ✅ No new provider is production-eligible
- ✅ No provider details exposed in public APIs
- ✅ StorageRuntime semantics unchanged
- ✅ ProductRuntime semantics unchanged
- ✅ No Flyway V1 baseline changes
- ✅ No DB migration added
- ✅ All smoke tests pass or are skipped cleanly

## Known Gaps

| Gap | Status | Notes |
|-----|--------|-------|
| Remotion real render | Blocked | Missing Remotion CLI |
| MLT real render | Not tested | Missing melt binary |
| Blender real render | Not tested | Missing blender binary |
| Natron real render | Not tested | Missing natron binary |
| GStreamer real render | Not tested | Missing gst-launch-1.0 binary |
| BMF integration | Not implemented | Future work |
| OpenFX host | Not implemented | Capability model only |

## Related Documents

- `docs/review/render-tool-capability-inventory.md` — Tool inventory
- `docs/review/remotion-provider-poc-plan.md` — Remotion POC plan
- `docs/review/opencue-runtime-foundation.md` — OpenCue foundation
- `docs/review/openfx-capability-model-reservation.md` — OpenFX reservation
- `docs/render/capability-matrix.md` — Full capability matrix
- `docs/architecture/current/current-system-state.md` — Current system state
