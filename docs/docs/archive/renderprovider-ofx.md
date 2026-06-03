# OFX RenderProvider Integration

> **Last updated**: 2026-05-11
> **Module**: `render-module`
> **Class**: `OFXRenderProvider`

## Overview

OFXRenderProvider extends the rendering pipeline with Open Effects Association (OFX) compatible effects. It works alongside JavaCVRenderProvider to provide advanced video effects.

## Architecture

```
RenderProviderRouter
├── OFXRenderProvider (complex effects: blur, vignette, transitions, text)
├── JavaCVRenderProvider (standard: clip, transcode, basic filters)
└── MockRenderProvider (test only)
```

### Routing Logic
- Profiles starting with `ofx_` → OFXRenderProvider
- Effects requiring blur/vignette/transitions → OFXRenderProvider
- Standard profiles → JavaCVRenderProvider
- Test profile → MockRenderProvider

## Supported Effects

### Transitions
| Effect | Description |
|--------|-------------|
| Dissolve | Cross-fade between clips |
| Wipe | Horizontal wipe transition |
| Slide | Slide-in transition |
| Zoom | Zoom transition |

### Video Filters
| Effect | Parameters |
|--------|------------|
| Blur | radius (1-10) |
| Sharpen | amount (0.1-3.0) |
| Vignette | intensity (0-1) |
| Chromatic Aberration | offset (1-10) |
| Brightness | value (0.5-2.0) |
| Contrast | value (0.5-2.0) |
| Grayscale | intensity (0-1) |
| Sepia | intensity (0-1) |

### Text Effects
| Effect | Parameters |
|--------|------------|
| Subtitle burn-in | text, position (top/bottom/center) |

### Compositing
| Effect | Description |
|--------|-------------|
| Overlay | Picture-in-picture |
| Split screen | Side-by-side |

## OTIO Timeline Integration

OFX effects are specified in OTIO timeline JSON via the `effects` array:

```json
{
  "tracks": [{
    "name": "Video 1",
    "children": [{
      "name": "clip_001",
      "source_range": { "start_time": 0, "duration": 5 },
      "effects": [
        { "type": "filter", "name": "blur", "params": { "radius": 3 } },
        { "type": "transition", "name": "dissolve", "duration": 0.5 },
        { "type": "text", "text": "Hello World", "position": "bottom" }
      ]
    }]
  }]
}
```

## Supported Profiles

| Profile | Resolution | Use Case |
|---------|------------|----------|
| `ofx_1080p` | 1920x1080 | OFX HD rendering |
| `ofx_720p` | 1280x720 | OFX web rendering |
| `default_1080p` | 1920x1080 | Standard HD |
| `social_720p` | 1280x720 | Social media |
| `mobile_480p` | 854x480 | Mobile |
| `4k_2160p` | 3840x2160 | Ultra HD |

## RenderProviderRouter

The router selects the appropriate provider:

```java
// OFX profile → OFXRenderProvider
router.route("ofx_1080p");

// Standard profile → JavaCVRenderProvider
router.route("default_1080p");

// Effect-based routing
router.routeByEffect(List.of("blur", "dissolve"));
```

## Testing

```bash
./gradlew :render-module:test
```

### Test Coverage
- OFXRenderProvider: 13 tests (profiles, capabilities, rendering, effects)
- RenderProviderRouterTest: 6 tests (routing, fallback, effect-based routing)

## Frontend Integration

The Effects Panel includes OFX-compatible effects:
- 7 transitions (Fade In/Out, Crossfade, Dissolve, Wipe, Slide, Zoom)
- 8 video filters (Blur, Sharpen, Vignette, Chromatic, Brightness, Contrast, Grayscale, Sepia)
- 1 text effect (Subtitle)
- 1 audio effect (Volume)

Each effect has configurable parameters (intensity, duration, position).
