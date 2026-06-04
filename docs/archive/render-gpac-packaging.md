# GPAC/MP4Box Packaging Provider

> **Generated**: 2026-05-08T15:13Z
> **Status**: Skeleton established; command factory tested; provider conditional.

---

## Overview

The GPAC packaging provider handles media packaging for adaptive streaming using GPAC's MP4Box tool.

**Capabilities:**
- DASH packaging (.mpd manifest + segments)
- HLS packaging (.m3u8 manifest + segments)
- CMAF packaging (common media application format)
- MP4 inspection

## Activation

```yaml
render:
  providers:
    gpac:
      enabled: true
```

## Architecture

```
GpacPackagingProvider (conditional @Component, implements PackagingProvider)
├── Mp4BoxCommandFactory     → builds List<String> args for MP4Box
└── GpacEnvironmentValidator → checks MP4Box availability

All execution goes through:
  ProcessToolRunner (extension-module port)
```

## Packaging vs Rendering

Packaging is **separate from rendering**:

1. **Render job** → produces a mezzanine MP4 file
2. **Packaging job** → converts mezzanine to streaming format

```
TimelineSpec → [FFmpeg/MLT Render] → mezzanine.mp4 → [MP4Box Package] → manifest + segments
```

## Command Factory

The `Mp4BoxCommandFactory` builds MP4Box command arguments as `List<String>`:

```java
Mp4BoxCommandFactory factory = new Mp4BoxCommandFactory();

// DASH
List<String> dashArgs = factory.buildDashCommand(
    "storage://mezzanine.mp4", "storage://output/manifest.mpd", 4000);

// HLS
List<String> hlsArgs = factory.buildHlsCommand(
    "storage://mezzanine.mp4", "storage://output/master.m3u8", 6000);

// Inspect
List<String> inspectArgs = factory.buildInspectCommand("storage://input.mp4");
```

## Supported Formats

| Format | Manifest | Segments | Use Case |
|--------|----------|----------|----------|
| DASH | .mpd | .m4s | Web streaming |
| HLS | .m3u8 | .ts | Apple devices |
| CMAF | .mpd | .cmf | Unified format |

## PackagingRequest / PackagingResult

```java
PackagingRequest request = PackagingRequest.dash(
    "storage://mezzanine.mp4", "storage://output", 4);

PackagingResult result = packagingProvider.packageMedia(request);
// result.manifestUri() → "storage://output/manifest.mpd"
// result.segmentUris() → ["storage://output/segment_1.m4s", ...]
```

## Testing

Command construction is fully tested without requiring MP4Box:

```java
Mp4BoxCommandFactoryTest  // Verifies all command types
```

---

*See also: [render-ffmpeg.md](render-ffmpeg.md), [render-mlt.md](render-mlt.md), [render-provider-integration.md](render-provider-integration.md)*
