# GStreamer Provider

> **Last updated**: 2026-05-13
> **Module**: `render-module`
> **Class**: `GStreamerRenderProvider`

## Overview

The GStreamer provider handles pipeline-based video processing using GStreamer's `gst-launch-1.0` command.

## Activation

```yaml
render:
  providers:
    gstreamer:
      enabled: true
```

## Capabilities

| Capability | Status |
|------------|--------|
| Pipeline processing | ✅ |
| Real-time streaming | ✅ |
| Filter graphs | ✅ |
| Multi-track compositing | ✅ |
| Subtitle overlay | ✅ |
| Test source generation | ✅ |

## Architecture

```
RenderJob → GStreamerRenderProvider → gst-launch-1.0 → Output
                    ↓
            GStreamerCommandFactory → Pipeline args
```

## Pipeline Examples

**Test source (placeholder video):**
```
gst-launch-1.0 videotestsrc num-frames=150 ! video/x-raw,width=1920,height=1080,framerate=30/1 ! videoconvert ! x264enc bitrate=8000 ! mp4mux ! filesink location=output.mp4
```

**File transcode:**
```
gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! x264enc bitrate=8000 ! mp4mux ! filesink location=output.mp4
```

**Subtitle overlay:**
```
gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! textoverlay text="Hello" valignment=bottom ! x264enc bitrate=8000 ! mp4mux ! filesink location=output.mp4
```

## Supported Profiles

- `gstreamer_1080p`, `gstreamer_720p`
- `default_1080p`, `default_720p`, `social_1080p`, `social_720p`

## Dependencies

```kotlin
// ProcessToolRunner (extension-module) for secure CLI execution
// gst-launch-1.0 binary must be available on PATH
// GStreamer plugins: x264, mp4mux, textoverlay, videotestsrc
```

## Testing

```bash
./gradlew :render-module:test --tests "com.example.platform.render.infrastructure.gstreamer.*"
```

## Security

- All commands use `List<String>` args (no shell concatenation)
- Executable allowlist enforced by `ToolRegistry`
- Timeout: 600s for rendering
