# Multi-Provider Orchestration

> **Last updated**: 2026-05-13
> **Module**: `render-module`

## Overview

The render pipeline supports multi-provider orchestration, where a single render job can chain multiple providers to produce the final artifact. Each stage of the pipeline uses the most appropriate provider based on capabilities, user tier, and profile requirements.

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        RenderJob Submission                              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    MultiProviderPipelineService                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   Stage 1    │    │   Stage 2    │    │   Stage 3    │              │
│  │   Effects    │───▶│  Transcode   │───▶│  Packaging   │              │
│  │   (OFX)      │    │  (JavaCV/    │    │  (GPAC)      │              │
│  │              │    │   GPU)       │    │              │              │
│  └──────────────┘    └──────────────┘    └──────────────┘              │
│         │                   │                    │                      │
│         ▼                   ▼                    ▼                      │
│   Filter chains      H.264/H.265/VP9      DASH/HLS/CMAF               │
│   Transitions        GPU acceleration      MP4 faststart               │
│   Subtitle burn-in   Multi-track mux       Multi-track mux             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Output Artifact                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Pipeline Stages

### Stage 1: Effects (OFX Provider)

Applied when the timeline contains effects or subtitle tracks.

- **Provider**: OFX (OpenFX) for PRO+ tiers, JavaCV for FREE tier
- **Operations**:
  - Filter chains (blur → color grade → vignette)
  - Transitions (dissolve, wipe, slide, zoom, fade in/out)
  - Subtitle burn-in with font fallback
  - Text overlay
  - Picture-in-picture

### Stage 2: Transcode (JavaCV / GPU Provider)

Encodes the processed frames to the target format.

- **Provider Selection**:
  - GPU profiles (`gpu_h264`, `gpu_h265`) → GPU-capable provider
  - Remote profiles (`remote_*`) → Remote worker provider
  - Default → JavaCV (CPU)
- **Codecs**: H.264 (NVENC/CPU), H.265 (NVENC/CPU), VP9 (VAAPI/CPU)
- **GPU Acceleration**: NVIDIA NVENC, Intel/AMD VAAPI

### Stage 3: Packages (GPAC Provider)

Optional packaging stage for streaming formats.

- **Provider**: GPAC/MP4Box
- **Formats**: DASH (.mpd), HLS (.m3u8), CMAF
- **Operations**: Segmentation, manifest generation, faststart

## Pipeline Planning

The `MultiProviderPipelineService.planPipeline()` method determines the stages:

```java
List<PipelineStage> stages = pipelineService.planPipeline(
    timeline, "default_1080p", "PRO", "mp4");
// Returns: [Effects(OFX), Transcode(JavaCV)]

stages = pipelineService.planPipeline(
    timeline, "gpu_h264", "TEAM", "dash");
// Returns: [Effects(OFX), Transcode(GPU), Packaging(GPAC)]
```

### Planning Rules

| Condition | Stage Added | Provider |
|-----------|-------------|----------|
| Timeline has effects or subtitles | Effects | OFX (PRO+) / JavaCV (FREE) |
| Always | Transcode | JavaCV / GPU / Remote |
| Output format is DASH/HLS/CMAF | Packaging | GPAC |
| GPU profile requested | Transcode | GPU-capable provider |
| Remote profile requested | Transcode | Remote worker |

## Provider Routing

The `RenderProviderRouter` selects providers based on:

1. **Profile matching** - Profile prefix determines provider (e.g., `ofx_` → OFX)
2. **Effect capabilities** - Required effects must be supported
3. **Health checks** - Unhealthy providers are skipped (with fallback)
4. **User tier** - Tier determines available providers and effects
5. **GPU availability** - GPU providers selected when profile requires GPU

### Selection Algorithm

```
1. Get capabilities for profile from registry
2. Filter by required effect support
3. Filter by health status (fallback to all if all unhealthy)
4. Sort: stable first, then by resolution (higher is better)
5. Return first match
```

### Fallback Chain

```
1. Preferred provider (via selection policy) → check health
2. Any healthy provider supporting the profile
3. Any provider (even unhealthy) as last resort
4. Throw PlatformException if nothing found
```

## OTIO Timeline Metadata Mapping

The pipeline reads effect metadata from the OTIO timeline JSON:

```json
{
  "tracks": [
    {
      "type": "VIDEO",
      "children": [{
        "effects": [
          {"type": "filter", "name": "blur", "params": {"radius": 3}},
          {"type": "transition", "name": "dissolve", "duration": 0.5},
          {"type": "overlay", "name": "subtitle_burn_in", "params": {"cues": [...]}}
        ]
      }]
    },
    {
      "type": "SUBTITLE",
      "burnIn": true,
      "fontId": "noto-sans",
      "fallbackFontIds": ["arial"],
      "cues": [{"text": "Hello", "startTime": 0, "endTime": 5}]
    }
  ],
  "outputSpec": {
    "format": "mp4",
    "resolution": "1920x1080",
    "frameRate": 30,
    "videoCodec": "h264"
  }
}
```

## Effect Pack Integration

Effects are defined in Effect Packs and mapped to provider implementations:

| Effect Key | OFX Mapping | JavaCV Mapping |
|------------|-------------|----------------|
| `video.fade_in` | OFX Fade In | FFmpeg fade filter |
| `video.blur` | OFX Blur | FFmpeg boxblur |
| `video.vignette` | OFX Vignette | Java2D composite |
| `video.chromatic` | OFX Chromatic Aberration | Java2D color shift |
| `text.subtitle_burn_in` | OFX Text | FFmpeg drawtext |
| `video.watermark` | OFX Overlay | FFmpeg overlay |

## Error Handling

All pipeline exceptions use configured error codes:

| Code | Message | Trigger |
|------|---------|---------|
| `RENDER-500-001` | Multi-provider pipeline failed | General pipeline failure |
| `RENDER-404-001` | Provider not found | Provider not registered |
| `RENDER-404-004` | Worker not found | Remote worker unavailable |

## Testing

```bash
# Run orchestration tests
./gradlew :render-module:test --tests "com.example.platform.render.app.MultiProviderPipelineServiceTest"

# Run effects pipeline tests
./gradlew :render-module:test --tests "com.example.platform.render.app.AdvancedEffectsPipelineTest"

# Run all render module tests
./gradlew :render-module:test
```

---

*This document reflects multi-provider orchestration as of 2026-05-13.*