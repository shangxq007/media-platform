# OFX (OpenFX) Provider

> **Last updated**: 2026-05-13
> **Module**: `render-module`
> **Class**: `OFXRenderProvider`

## Overview

The OFX (Open Effects Association) provider delivers advanced video effects rendering using Java2D-based compositing. It supports filters, transitions, subtitle burn-in, and text overlay — all rendered through JavaCV's FFmpeg pipeline.

## Activation

```yaml
render:
  providers:
    javacv:
      enabled: true  # OFX is auto-detected via @Component
```

The OFX provider is always active (registered via `@Component`). Provider routing selects it based on profile or effect requirements.

## Architecture

```
RenderJob → ProviderRouter → OFXRenderProvider → Java2D Frame Rendering → FFmpegFrameRecorder → MP4 Output
                    ↓
            OTIO Timeline Parser → Effects/Transitions/Text
                    ↓
            createFrameWithEffects() → BufferedImage → Frame → Record
```

## Capabilities

| Capability | Status |
|------------|--------|
| **Filters** | Blur, Sharpen, Vignette, Chromatic Aberration, Brightness, Contrast, Grayscale, Sepia |
| **Transitions** | Dissolve, Wipe, Slide, Zoom, Fade In/Out, Cross Dissolve |
| **Text/Subtitles** | Burn-in with font, position, shadow |
| **Compositing** | Overlay, Picture-in-Picture (PIP) |
| **Watermark** | Image overlay support |
| **Multi-track** | OTIO timeline with multiple tracks |

## OTIO Timeline Metadata Mapping

The OFX provider reads effect metadata from the OTIO timeline JSON:

```json
{
  "tracks": [
    {
      "name": "Video 1",
      "type": "VIDEO",
      "children": [
        {
          "name": "clip_001",
          "source_range": { "start_time": 0, "duration": 5 },
          "media_reference": "file:///path/to/video.mp4",
          "effects": [
            { "type": "filter", "name": "blur", "params": { "radius": 3 } },
            { "type": "transition", "name": "dissolve", "duration": 0.5 },
            { "type": "text", "text": "Hello World", "position": "bottom" }
          ]
        }
      ]
    },
    {
      "name": "Subtitles",
      "type": "SUBTITLE",
      "burnIn": true,
      "cues": [
        { "text": "Subtitle text", "startTime": 0, "endTime": 5 }
      ],
      "fontId": "noto-sans",
      "fallbackFontIds": ["arial", "dejavu-sans"]
    }
  ]
}
```

### Effect Types

| Type | Name | Parameters |
|------|------|------------|
| `filter` | `blur` | `radius` (float) |
| `filter` | `vignette` | `strength` (float) |
| `filter` | `chromatic` | `offset` (int) |
| `filter` | `sharpen` | — |
| `transition` | `dissolve` | `duration` (seconds) |
| `transition` | `wipe` | `duration` (seconds) |
| `transition` | `slide` | `duration` (seconds) |
| `transition` | `zoom` | `duration` (seconds) |
| `text` | — | `text`, `position` (top/bottom) |

## Supported Profiles

| Profile | Resolution | Use Case |
|---------|-----------|----------|
| `ofx_1080p` | 1920x1080 | Standard HD with effects |
| `ofx_720p` | 1280x720 | Web/HD with effects |
| `default_1080p` | 1920x1080 | Fallback to OFX |
| `social_1080p` | 1920x1080 | Social media with effects |
| `social_720p` | 1280x720 | Social media standard |
| `mobile_480p` | 854x480 | Mobile devices |
| `4k_2160p` | 3840x2160 | Ultra HD |

## Provider Routing

The OFX provider is selected when:
- Profile starts with `ofx_`
- Effects like `video.vignette`, `video.chromatic`, `video.overlay`, `video.pip` are required
- User tier is PRO+ (based on `ExportPolicyService`)

```java
// Capability-based routing
RenderProvider provider = router.route("default_1080p", List.of("video.vignette"));
// → Returns OFXRenderProvider
```

## Rendering Pipeline

1. **Parse OTIO Timeline** → Extract clips, effects, subtitle tracks
2. **Collect Effects** → Aggregate all effects from all clips
3. **Frame Generation** → For each frame:
   - Create `BufferedImage` with background gradient
   - Apply filters (blur, vignette, chromatic, sharpen)
   - Apply transitions (dissolve, wipe, slide, zoom)
   - Apply text/subtitle overlay with shadow
4. **Encode** → Convert to `Frame` via `Java2DFrameRecorder`, record via FFmpeg
5. **Output** → MP4 file with H.264 video + AAC audio

## Error Handling

All exceptions use configured error codes:

| Error Code | Message | Trigger |
|------------|---------|---------|
| `RENDER-500-001` | Render execution failed | General render failure |
| `RENDER-500-001` | OFX render failed | OFX-specific failure |

## Dependencies

```kotlin
// JavaCV (already in render-module)
api("org.bytedeco:javacv-platform:1.5.9")
api("com.fasterxml.jackson.core:jackson-databind")
```

## Testing

```bash
# Run OFX tests only
./gradlew :render-module:test --tests "com.example.platform.render.infrastructure.OFXRenderProviderTest"

# Run all render module tests
./gradlew :render-module:test
```

### Test Coverage

| Test | Description |
|------|-------------|
| `getSupportedProfilesIncludesOFX` | Profile list verification |
| `supportsBlurFilter` | Blur filter capability |
| `supportsVignette` | Vignette filter capability |
| `supportsChromatic` | Chromatic aberration capability |
| `supportsDissolveTransition` | Dissolve transition capability |
| `supportsWipeTransition` | Wipe transition capability |
| `supportsTextBurn` | Text burn-in capability |
| `supportsOverlay` | Overlay compositing capability |
| `supportsPip` | Picture-in-picture capability |
| `doesNotSupportH265` | H.265 correctly unsupported |
| `renderWithEmptyTimelineReturnsResult` | Empty timeline → placeholder |
| `renderWithEffectsTimeline` | Full effects timeline rendering |
| `renderWithAiScriptReturnsResult` | AI script → placeholder with effects |
| `environmentValidationPasses` | Environment check |

## Security Notes

- ✅ No external process execution (pure Java2D + JavaCV JNI)
- ✅ No shell command construction
- ✅ File path validation against storage root
- ✅ All exceptions use configured error codes with i18n messages

## Performance

- **Rendering**: CPU-based Java2D compositing
- **Encoding**: H.264 via JavaCV (FFmpeg JNI)
- **Memory**: ~2-4GB per concurrent render
- **Speed**: Real-time for 1080p with basic effects

## Limitations

- ❌ No GPU acceleration (CPU-only Java2D)
- ❌ No real OFX plugin SDK integration (software simulation)
- ❌ No H.265/HEVC encoding
- ❌ No HDR support
- ❌ Multi-track compositing is sequential (not parallel)

---

*This document reflects the OFX provider implementation as of 2026-05-13.*