# MLT / melt Provider

> **Last updated**: 2026-05-13
> **Module**: `render-module`
> **Class**: `MltRenderProvider`

## Overview

The MLT provider handles multi-track timeline rendering using the MLT (Media Lovin' Toolkit) framework and its `melt` command-line tool.

## Activation

```yaml
render:
  providers:
    melt:
      enabled: true
```

## Capabilities

| Capability | Status |
|------------|--------|
| Multi-track timeline | ✅ |
| Transitions | ✅ |
| Compositing | ✅ |
| Subtitle burn-in | ✅ |
| XML project format | ✅ |

## Architecture

```
RenderJob → MltRenderProvider → melt CLI → Output
                    ↓
            MltProjectXmlBuilder → MLT XML
                    ↓
            MeltCommandFactory → Command args
```

## Supported Profiles

- `social_1080p`, `social_720p`
- `default_1080p`, `default_720p`

## MLT XML Generation

The `MltProjectXmlBuilder` converts timeline data to MLT project XML:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<mlt>
  <profile width="1920" height="1080" frame_rate_num="30" />
  <producer id="prod_0">
    <property name="resource">/path/to/video.mp4</property>
  </producer>
  <playlist id="main">
    <entry producer="prod_0" />
  </playlist>
  <tractor>
    <multitrack>
      <track producer="main" />
    </multitrack>
  </tractor>
</mlt>
```

## Dependencies

```kotlin
// ProcessToolRunner (extension-module) for secure CLI execution
// melt binary must be available on PATH
```

## Testing

```bash
./gradlew :render-module:test --tests "com.example.platform.render.infrastructure.mlt.*"
```

## Security

- All commands use `List<String>` args (no shell concatenation)
- Executable allowlist enforced by `ToolRegistry`
- Timeout: 600s for rendering
