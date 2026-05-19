# Advanced Effects Pipeline

> **Last updated**: 2026-05-13
> **Module**: `render-module`
> **Class**: `AdvancedEffectsPipeline`

## Overview

The advanced effects pipeline provides complex filter chains, transitions, and subtitle burn-in for video rendering. It supports 11 filter types, 7 transition types, and 3 overlay types — all rendered through Java2D compositing.

## Filter Chain Architecture

```
Input Frame → [Filter 1] → [Filter 2] → ... → [Filter N] → Output Frame
                  │              │                   │
                  ▼              ▼                   ▼
              Blur(3px)    Vignette(0.4)      Color Grade
```

Filters are applied sequentially. Each filter receives the output of the previous filter.

## Supported Filters

| Filter | Parameters | Description |
|--------|------------|-------------|
| `blur` | `radius: float` | Box blur approximation |
| `sharpen` | — | Bicubic interpolation sharpening |
| `vignette` | `strength: float` (0-1) | Darkened edges |
| `brightness` | `value: float` (-1 to 1) | Brightness adjustment |
| `contrast` | `value: float` (0-2) | Contrast adjustment |
| `saturation` | `value: float` (0-2) | Saturation adjustment |
| `grayscale` |  | Desaturation |
| `sepia` |  | Sepia tone overlay |
| `chromatic` | `offset: int` | RGB channel offset |
| `color-grade` | `shadowsR`, `midtonesG`, `highlightsB` | Per-channel color grading |

## Supported Transitions

| Transition | Parameters | Description |
|------------|------------|-------------|
| `fade_in` | `duration: double` (seconds) | Fade from black |
| `fade_out` | `duration: double` | Fade to black |
| `dissolve` | `duration: double` | Cross-dissolve with black |
| `wipe` | `duration: double` | Horizontal wipe |
| `slide` | `duration: double` | Slide from right |
| `cross_dissolve` | `duration: double` | Alias for dissolve |
| `zoom` | `duration: double` | Zoom out effect |

Transition progress is calculated as `frame / (duration * frameRate)`.

## Supported Overlays

| Overlay | Parameters | Description |
|---------|------------|-------------|
| `subtitle_burn_in` | `cues: List<Cue>` | Text overlay with timing |
| `watermark` | `text: String`, `opacity: float` | Corner watermark |
| `pip` |  | Picture-in-picture placeholder |

## Subtitle Burn-In

Subtitle burn-in renders text cues onto video frames based on timing:

```json
{
  "type": "overlay",
  "name": "subtitle_burn_in",
  "params": {
    "cues": [
      {"text": "Hello World", "startTime": 0.0, "endTime": 5.0},
      {"text": "你好世界", "startTime": 5.5, "endTime": 10.0}
    ]
  }
}
```

Features:
- Font shadow (2px offset, 180 alpha black)
- Background box (rounded, 120 alpha black)
- Centered horizontal positioning
- Bottom vertical positioning (50px from margin)
- Font: SansSerif Bold 24pt

## GPU Acceleration Integration

When a GPU preset is selected, the effects pipeline works with GPU-accelerated encoding:

```
[Java2D Effects] → [GPU Frame Buffer] → [NVENC Encoder] → H.264/H.265 Output
```

GPU encoding options:
- **NVIDIA NVENC**: `preset=p4`, `tune=hq`, `rc=vbr`, `cq=23/28`
- **Intel/AMD VAAPI**: `quality=balanced`

## Effect Mapping

Effects are mapped to provider implementations via `EffectMappingService`:

| Standard Key | OFX Native | JavaCV Native |
|--------------|------------|---------------|
| `video.fade_in` | OFX Fade In | FFmpeg fade |
| `video.blur` | OFX Blur | FFmpeg boxblur |
| `video.vignette` | OFX Vignette | Java2D |
| `video.chromatic` | OFX Chromatic | Java2D |
| `text.subtitle_burn_in` | OFX Text | FFmpeg drawtext |
| `video.watermark` | OFX Overlay | FFmpeg overlay |

## Usage

```java
@Autowired
private AdvancedEffectsPipeline effectsPipeline;

// Apply single filter
BufferedImage result = effectsPipeline.applyFilter(
    image, frame, total, "filter", "blur", Map.of("radius", 3.0f));

// Apply filter chain
List<Map<String, Object>> effects = List.of(
    Map.of("type", "filter", "name", "blur", "params", Map.of("radius", 3.0f)),
    Map.of("type", "filter", "name", "vignette", "params", Map.of("strength", 0.4f)),
    Map.of("type", "transition", "name", "fade_in", "params", Map.of("duration", 0.5))
);
BufferedImage result = effectsPipeline.applyFilterChain(image, frame, total, effects);
```

## Performance

| Operation | 1080p CPU | 1080p GPU |
|-----------|-----------|-----------|
| Blur (r=3) | ~5ms/frame | ~1ms/frame |
| Vignette | ~2ms/frame | ~0.5ms/frame |
| Subtitle burn-in | ~3ms/frame | ~1ms/frame |
| H.264 encode | ~15ms/frame | ~3ms/frame (NVENC) |

## Testing

```bash
# Run effects pipeline tests
./gradlew :render-module:test --tests "com.example.platform.render.app.AdvancedEffectsPipelineTest"
```

### Test Coverage

| Test | Description |
|------|-------------|
| `applyBlurFilter` | Blur filter with radius |
| `applyVignetteFilter` | Vignette with strength |
| `applyBrightnessFilter` | Brightness adjustment |
| `applyContrastFilter` | Contrast adjustment |
| `applyGrayscaleFilter` | Grayscale conversion |
| `applySepiaFilter` | Sepia tone |
| `applyChromaticFilter` | Chromatic aberration |
| `applyColorGradeFilter` | Per-channel color grading |
| `applySharpenFilter` | Sharpening |
| `applySaturationFilter` | Saturation adjustment |
| `applyFadeInTransition` | Fade in |
| `applyFadeOutTransition` | Fade out |
| `applyDissolveTransition` | Dissolve |
| `applyWipeTransition` | Wipe |
| `applySlideTransition` | Slide |
| `applyZoomTransition` | Zoom |
| `applySubtitleBurnIn` | Text overlay with cues |
| `applyWatermarkOverlay` | Corner watermark |
| `applyPictureInPicture` | PiP placeholder |
| `applyFilterChainWithMultipleEffects` | Multi-effect chain |
| `applyFilterWithUnknownTypeReturnsOriginal` | Unknown type passthrough |

---

*This document reflects the advanced effects pipeline as of 2026-05-13.*