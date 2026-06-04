# GPAC / MP4Box Provider

> **Last updated**: 2026-05-13
> **Module**: `render-module`
> **Class**: `GpacRenderProvider`, `GpacPackagingProvider`

## Overview

The GPAC provider handles MP4 packaging and streaming format output using GPAC's MP4Box tool.

## Activation

```yaml
render:
  providers:
    gpac:
      enabled: true
```

## Capabilities

| Capability | Status |
|------------|--------|
| MP4 faststart | ✅ |
| DASH packaging | ✅ |
| HLS packaging | ✅ |
| CMAF packaging | ✅ |
| Multi-track muxing | ✅ |
| Subtitle tracks | ✅ |

## Architecture

```
RenderJob → GpacRenderProvider → MP4Box CLI → Output
                    ↓
            GpacPackagingProvider → DASH/HLS segments
```

## Supported Profiles

- `gpac_dash` - DASH streaming output
- `gpac_hls` - HLS streaming output
- `gpac_cmaf` - CMAF unified format

## Dependencies

```kotlin
// ProcessToolRunner (extension-module) for secure CLI execution
// No additional JavaCV dependency needed
```

## Testing

```bash
./gradlew :render-module:test --tests "com.example.platform.render.infrastructure.gpac.*"
```

## Security

- All commands use `List<String>` args (no shell concatenation)
- Executable allowlist enforced by `ToolRegistry`
- Timeout: 300s for packaging, 120s for faststart
