# Media Probe Service — Migration Guide

## Overview

The render-module previously had three separate media probe implementations:

| Implementation | Location | Return Type |
|---|---|---|
| `MediaProbeService.probe()` | `MediaProbeService.java` | `MediaValidationReport` |
| `JavaCVRenderService.probe()` | `JavaCVRenderService.java` | `MediaValidationReport` |
| `JavaCVTranscodeService.probe()` | `JavaCVTranscodeService.java` | `MediaProbeResult` (inner record) |

These have been unified into a single probe pipeline:

```
MediaProbeService → MediaProbeAdapter (interface) → JavaCVMediaProbeAdapter
```

All probe operations now return the unified `MediaProbeResult` record.

## New Components

### `MediaProbeResult` (unified record)

Top-level record in `infrastructure/MediaProbeResult.java`. Contains all fields from both previous implementations:

- `jobId`, `valid`, `filePath`, `fileSizeBytes`, `durationMs`
- `width`, `height`, `videoCodec`, `audioCodec`
- `frameRate`, `bitrate`, `audioChannels`, `sampleRate`
- `warnings` (List<String>), `errorMessage` (String)

### `MediaProbeAdapter` (interface)

Defines the probe contract:
```java
public interface MediaProbeAdapter {
    MediaProbeResult probe(String jobId, String filePath);
    boolean isAvailable();
}
```

### `JavaCVMediaProbeAdapter` (component)

Single implementation using JavaCV `FFmpegFrameGrabber`. Extracted from the old `JavaCVRenderService.probe()` and `JavaCVTranscodeService.probe()`.

## Migration

### For callers of `MediaProbeService`

**Before:**
```java
MediaValidationReport report = probeService.probe(jobId, relativePath);
```

**After:**
```java
MediaProbeResult result = probeService.probe(jobId, relativePath);
```

For backward compatibility, `MediaProbeService.probeLegacy()` still returns `MediaValidationReport`:
```java
MediaValidationReport report = probeService.probeLegacy(jobId, relativePath);
```

### For callers of `JavaCVRenderService.probe()` or `JavaCVTranscodeService.probe()`

These methods have been removed. Inject `MediaProbeService` instead:

**Before:**
```java
MediaValidationReport report = javaCVRenderService.probe(jobId, filePath);
```

**After:**
```java
MediaProbeResult result = mediaProbeService.probeAbsolute(jobId, filePath);
```

### For `RenderQualityCheckService`

Updated to use `MediaProbeResult` instead of `MediaValidationReport`. The `QualityCheckResult` record now holds a `MediaProbeResult probeReport` field.

### Error Codes Added

| Code | Description |
|---|---|
| `MEDIA-PROBE-500-001` | Media probe failed |
| `MEDIA-PROBE-400-001` | Unsupported media format |
| `MEDIA-PROBE-404-001` | Media file not found |
| `MEDIA-PROBE-408-001` | Probe timeout |
