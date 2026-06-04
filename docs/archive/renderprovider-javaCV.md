# JavaCV RenderProvider Integration

> **Last updated**: 2026-05-11
> **Module**: `render-module`
> **Class**: `JavaCVRenderProvider`

## Overview

JavaCVRenderProvider replaces the MockRenderProvider for production video rendering. It uses JavaCV (a Java wrapper around FFmpeg) to perform real video editing and transcoding operations.

## Features

### Video Clipping
- Trim clips by start/end time
- Multi-track timeline merging
- OTIO JSON timeline format compatibility

### Transcoding
| Parameter | Options |
|-----------|---------|
| Resolution | 480p, 720p, 1080p, 4K |
| Frame Rate | 24fps, 30fps, 60fps |
| Encoder | H.264 (libx264), AAC audio |
| Format | MP4 (default), OGG, WebM |

### Filters & Effects
- **Color**: Grayscale, Sepia, Brightness, Contrast
- **Transitions**: Fade In, Fade Out, Crossfade
- **Text**: Subtitle burn-in
- **Watermark**: Image overlay

### Audio
- Track mixing
- Volume adjustment
- AAC encoding at 44100Hz stereo

## Supported Profiles

| Profile | Resolution | Use Case |
|---------|------------|----------|
| `default_1080p` | 1920x1080 | Standard HD |
| `default_720p` | 1280x720 | Web/HD |
| `social_1080p` | 1920x1080 | Social media HD |
| `social_720p` | 1280x720 | Social media standard |
| `mobile_480p` | 854x480 | Mobile devices |
| `4k_2160p` | 3840x2160 | Ultra HD |

## OTIO Timeline Compatibility

JavaCVRenderProvider accepts OTIO JSON timeline format:

```json
{
  "name": "my-timeline",
  "tracks": [
    {
      "name": "Video 1",
      "children": [
        {
          "name": "clip_001",
          "source_range": {
            "start_time": 0.0,
            "duration": 5.0
          },
          "media_reference": "file:///path/to/video.mp4",
          "transforms": []
        }
      ]
    }
  ]
}
```

When an empty timeline or missing source is detected, a placeholder video is generated.

## Architecture

```
RenderJobController → RenderOrchestratorService → RenderProviderRouter → JavaCVRenderProvider
                                                                   → MockRenderProvider (test only)
```

### Profile Routing
- Production: `JavaCVRenderProvider` (default)
- Test: `MockRenderProvider` (requires `@ActiveProfiles("test")`)

## Configuration

```yaml
app:
  storage:
    local-root: /tmp/platform  # Output directory for rendered artifacts
```

## Testing

```bash
# Run render module tests only
./gradlew :render-module:test

# Run full test suite
./gradlew clean test
```

### Test Coverage
- Profile support verification (6 profiles)
- Capability checks (h264, mp4, watermark, subtitle-burn, fade, clip, transcode)
- Render from empty timeline (placeholder generation)
- Render from AI script
- Resolution mapping (720p → 1280x720, 1080p → 1920x1080)

## Export Panel Parameter Mapping

| ExportPanel Setting | Backend Parameter |
|--------------------|-------------------|
| Format (MP4/OGG/WebM) | FFmpeg format |
| Resolution (720p/1080p/4K) | `-s WxH` |
| Profile | Sets resolution + bitrate |
| Frame Rate | `-r N` |
| Encoder | `-c:v libx264` |
| Audio Track | `-ac 2 -ar 44100` |

## Docker Integration

```bash
# Start all services including JavaCV rendering
docker compose up --build

# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
```

## Dependencies

```gradle
implementation "org.bytedeco:javacv-platform:1.5.9"
implementation "com.fasterxml.jackson.core:jackson-databind"
```

## Security Notes

- No `Runtime.exec()` or `ProcessBuilder` — JavaCV uses JNI bindings directly
- No shell command concatenation
- All file paths are validated against the storage root
- Temporary files are cleaned up after render completion
