# FFmpeg Render Provider

> **Generated**: 2026-05-08T15:03Z
> **Status**: Skeleton established; command factory tested; provider conditional.

---

## Overview

The FFmpeg render provider handles video processing operations using FFmpeg/ffprobe:

- **Probe**: Extract media metadata with ffprobe
- **Transcode**: Convert media between formats/codecs
- **Thumbnail**: Extract still frames
- **Faststart**: Optimize MP4 for streaming
- **Filters**: Apply video/audio filters
- **Subtitles**: Burn subtitles into video

## Activation

The FFmpeg provider is **disabled by default** and activated via configuration:

```yaml
render:
  providers:
    ffmpeg:
      enabled: true
```

When disabled, the `MockRenderProvider` handles all render jobs.

## Architecture

```
FfmpegRenderProvider (conditional @Component)
├── FfmpegCommandFactory  → builds List<String> args (no shell concatenation)
├── FfmpegProbeService    → wraps ffprobe execution
└── FfmpegEnvironmentValidator → checks ffmpeg/ffprobe availability

All execution goes through:
  ProcessToolRunner (extension-module port)
    └── DefaultProcessToolRunner (Commons Exec, List<String> args)
```

## Command Factory

The `FfmpegCommandFactory` builds FFmpeg command arguments as `List<String>`:

```java
FfmpegCommandFactory factory = new FfmpegCommandFactory();

// Probe
List<String> probeArgs = factory.buildProbeCommand("storage://input.mp4");
// → [-i, input.mp4, -v, quiet, -print_format, json, -show_format, -show_streams]

// Transcode
List<String> transcodeArgs = factory.buildTranscodeCommand(
    "storage://input.mp4", "storage://output.mp4", RenderProfile.social1080p());
// → [-i, input.mp4, -c:v, libx264, -b:v, 8000k, -s, 1920x1080, ...]

// Thumbnail
List<String> thumbArgs = factory.buildThumbnailCommand(
    "storage://input.mp4", "storage://thumb.jpg", 5.0, 320);
```

## Security

- **No shell concatenation**: All commands use `List<String>` args
- **Executable allowlist**: Only registered executables can be run
- **Timeout enforcement**: Default 60s timeout per execution
- **Output limits**: stdout/stderr capped at 4MB
- **Path traversal protection**: Rejected by `ToolRegistry`

## Supported Profiles

| Profile | Resolution | Codec | Bitrate |
|---------|-----------|-------|---------|
| social_1080p | 1920x1080 | h264 | 8000k |
| social_720p | 1280x720 | h264 | 4000k |
| default_1080p | 1920x1080 | h264 | 8000k |
| default_720p | 1280x720 | h264 | 4000k |
| broadcast_4k | 3840x2160 | h265 | 20000k |
| proxy_480p | 854x480 | h264 | 1000k |

## Environment Validation

At startup, the provider checks for ffmpeg/ffprobe:

```java
boolean available = new FfmpegEnvironmentValidator(processToolRunner).validate();
```

If binaries are not found, the provider logs a warning and returns
`EnvironmentValidationResult.failed(...)`.

## Testing

Command construction is fully tested without requiring FFmpeg:

```java
FfmpegCommandFactoryTest  // Verifies all command types
```

Integration tests (requiring FFmpeg) are conditional:

```java
@EnabledIfSystemProperty(named = "ffmpeg.available", matches = "true")
class FfmpegRenderProviderIntegrationTest { }
```

---

*See also: [render-mlt.md](render-mlt.md), [render-gpac-packaging.md](render-gpac-packaging.md), [render-provider-integration.md](render-provider-integration.md)*
