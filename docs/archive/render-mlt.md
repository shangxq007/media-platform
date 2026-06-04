# MLT/melt Render Provider

> **Generated**: 2026-05-08T15:13Z
> **Status**: Skeleton established; XML builder tested; provider conditional.

---

## Overview

The MLT render provider handles multi-track timeline rendering using the MLT (Media Lovin' Toolkit) framework and its `mel` command-line tool.

**Capabilities:**
- Multi-track timeline rendering
- Transitions between clips
- Nonlinear editing style composition
- Text overlay rendering

## Activation

```yaml
render:
  providers:
    melt:
      enabled: true
```

## Architecture

```
MltRenderProvider (conditional @Component)
├── MltProjectXmlBuilder  → converts TimelineSpec to MLT XML
├── MeltCommandFactory    → builds List<String> args for melt
└── MltEnvironmentValidator → checks melt availability

All execution goes through:
  ProcessToolRunner (extension-module port)
```

## MLT XML Generation

The `MltProjectXmlBuilder` converts a `TimelineSpec` to MLT project XML:

```java
MltProjectXmlBuilder builder = new MltProjectXmlBuilder();
String xml = builder.build(timelineSpec);
```

The XML includes:
- Profile settings (resolution, frame rate)
- Producers (one per unique asset)
- Playlists (one per track with clip entries)
- Tractor with multitrack compositing

## Command Factory

The `MeltCommandFactory` builds melt command arguments as `List<String>`:

```java
MeltCommandFactory factory = new MeltCommandFactory();

// Full render
List<String> args = factory.buildRenderCommand(
    "/tmp/project.xml", "storage://output.mp4", "atsc_1080p_30");

// Preview (fast, low quality)
List<String> previewArgs = factory.buildPreviewCommand(
    "/tmp/project.xml", "storage://preview.mp4");
```

## Supported Profiles

| Profile | Resolution | Use Case |
|---------|-----------|----------|
| social_1080p | 1920x1080 | Social media |
| social_720p | 1280x720 | Social media (smaller) |
| default_1080p | 1920x1080 | General purpose |
| default_720p | 1280x720 | General purpose (smaller) |

## Testing

MLT XML generation is fully tested without requiring melt:

```java
MltProjectXmlBuilderTest  // Verifies XML structure
MeltCommandFactoryTest    // Verifies command construction
```

---

*See also: [render-ffmpeg.md](render-ffmpeg.md), [render-gpac-packaging.md](render-gpac-packaging.md), [timeline-model.md](timeline-model.md)*
