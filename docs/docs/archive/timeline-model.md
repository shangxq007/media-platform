# Timeline Model

> **Generated**: 2026-05-08T14:59Z
> **Status**: Internal JSON model established; OTIO adapter placeholder.

---

## Overview

The timeline model is the platform's canonical representation for multi-track video editing timelines. It is a JSON-serializable domain model that describes tracks, clips, text overlays, audio settings, and output specifications.

## Architecture

```
TimelineSpec
├── tracks: List<TimelineTrack>
│   └── clips: List<TimelineClip>
│       └── assetRef: TimelineAssetRef
├── textOverlays: List<TimelineTextOverlay>
├── outputSpec: TimelineOutputSpec
│   └── audioSpec: TimelineAudioSpec
└── metadata: Map<String, String>
```

## Core Types

### TimelineSpec

The root timeline object. Contains all tracks, overlays, and output settings.

```java
TimelineSpec spec = TimelineSpec.create("tl-1", "My Video", TimelineOutputSpec.mp4_1080p30());
```

### TimelineTrack

An ordered container for clips. Each track has a type (VIDEO, AUDIO, SUBTITLE) and a layer for compositing.

```java
TimelineTrack track = TimelineTrack.of("tr-1", "Video 1", TimelineTrack.TrackType.VIDEO);
```

### TimelineClip

A segment of an asset placed on a timeline track. Defines in/out points and timeline position.

```java
TimelineClip clip = TimelineClip.of("clip-1", assetRef, 0.0, 0.0, 10.0);
```

### TimelineAssetRef

Reference to an external media asset (video, audio, image).

```java
TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://bucket/video.mp4");
```

### TimelineTextOverlay

Text rendered on top of video at a specific position and time range.

```java
TimelineTextOverlay overlay = TimelineTextOverlay.of("ov-1", "Hello World", 0.0, 5.0);
```

### TimelineAudioSpec

Audio output configuration (codec, sample rate, channels, bitrate).

```java
TimelineAudioSpec audio = TimelineAudioSpec.aacDefault(); // 48kHz, stereo, 128kbps
```

### TimelineOutputSpec

Overall output configuration (format, resolution, frame rate, codecs).

```java
TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
```

## Validation

Timelines are validated before rendering:

```java
TimelineValidationResult result = timeline.validate();
if (!result.valid()) {
    result.errors().forEach(System.err::println);
}
```

Validation checks:
- At least one track exists
- Each clip has valid timing (out > in)
- Each clip has an asset reference
- Output spec has format and resolution
- Text overlays have non-empty text and valid duration

## OpenTimelineIO (OTIO)

OTIO is an **interchange format**, not a renderer. The `OpenTimelineioAdapter` provides conversion between `TimelineSpec` and OTIO JSON.

**Current status**: Placeholder. The internal `TimelineSpec` model is the canonical representation. OTIO conversion is deferred until the OTIO Java library is integrated.

```java
// Future usage:
String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);
TimelineSpec imported = OpenTimelineioAdapter.fromOtioJson(otioJson);
```

## MLT XML Generation

The `MltProjectXmlBuilder` (R5) converts `TimelineSpec` to MLT project XML for rendering with `melt`.

## FFmpeg Command Construction

The `FfmpegCommandFactory` (R4) converts `TimelineSpec` + `RenderProfile` into FFmpeg command-line arguments.

---

*See also: [render-ffmpeg.md](render-ffmpeg.md), [render-mlt.md](render-mlt.md), [render-gpac-packaging.md](render-gpac-packaging.md)*
