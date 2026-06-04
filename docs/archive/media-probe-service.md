# Media Probe Service

> **Purpose:** Unified media probing architecture for inspecting media file metadata.
> **Last Updated:** 2026-05-17

---

## Architecture

The media probing system provides a unified interface for extracting metadata from media files (duration, resolution, codecs, bitrate, etc.). It uses an adapter pattern to decouple the probing interface from the underlying implementation.

```
MediaProbeService (@Service)
    │
    ├── depends on ──→ MediaProbeAdapter (interface)
    │                           │
    │                           └── JavaCVMediaProbeAdapter (@Component)
    │                               └── Uses FFmpegFrameGrabber (JavaCV)
    │
    └── returns ──→ MediaProbeResult (record)
```

---

## MediaProbeAdapter Interface

```java
public interface MediaProbeAdapter {
    MediaProbeResult probe(String jobId, String filePath);
    boolean isAvailable();
}
```

| Method | Description |
|--------|-------------|
| `probe(jobId, filePath)` | Probe a media file and return structured metadata. |
| `isAvailable()` | Check if the underlying library is available on the classpath. |

---

## JavaCVMediaProbeAdapter

`JavaCVMediaProbeAdapter` is the primary (and currently only) implementation of `MediaProbeAdapter`. It uses JavaCV's `FFmpegFrameGrabber` to extract media metadata.

### Probe Flow

```
1. Check file exists → MediaProbeResult.failed() if not
2. Create FFmpegFrameGrabber(file)
3. grabber.start()
4. Extract metadata:
   - width, height (image dimensions)
   - durationMs (lengthInTime / 1000.0)
   - videoCodec, audioCodec
   - frameRate
   - videoBitrate
   - audioChannels, sampleRate
5. Collect warnings (no video stream, zero duration)
6. Build MediaProbeResult
7. grabber.stop()
```

### Availability Check

```java
public boolean isAvailable() {
    try {
        Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}
```

---

## MediaProbeResult

```java
public record MediaProbeResult(
    String jobId,
    boolean valid,
    String filePath,
    long fileSizeBytes,
    double durationMs,
    int width,
    int height,
    String videoCodec,
    String audioCodec,
    double frameRate,
    long bitrate,
    int audioChannels,
    int sampleRate,
    List<String> warnings,
    String errorMessage
) {
    public static MediaProbeResult failed(String jobId, String errorMessage) { ... }
    public boolean hasVideo() { ... }
    public boolean hasAudio() { ... }
}
```

| Field | Description |
|-------|-------------|
| `jobId` | The render job this probe is associated with |
| `valid` | Whether the probe succeeded |
| `filePath` | Absolute path to the probed file |
| `fileSizeBytes` | File size in bytes |
| `durationMs` | Duration in milliseconds |
| `width`, `height` | Video dimensions in pixels |
| `videoCodec` | Video codec name (e.g., "h264") |
| `audioCodec` | Audio codec name (e.g., "aac") |
| `frameRate` | Frames per second |
| `bitrate` | Video bitrate |
| `audioChannels` | Number of audio channels |
| `sampleRate` | Audio sample rate in Hz |
| `warnings` | Non-fatal issues (e.g., "No video stream detected") |
| `errorMessage` | Error description when `valid` is false |

---

## MediaProbeService

`MediaProbeService` is the Spring `@Service` that coordinates probing operations. It wraps the `MediaProbeAdapter` and provides path resolution.

```java
@Service
public class MediaProbeService {
    private final MediaProbeAdapter probeAdapter;

    public MediaProbeService(JavaCVMediaProbeAdapter probeAdapter) {
        this.probeAdapter = probeAdapter;
    }

    // Probe using a relative path (resolved against storage root)
    public MediaProbeResult probe(String jobId, String relativePath);

    // Probe using an absolute path
    public MediaProbeResult probeAbsolute(String jobId, String absolutePath);

    // Legacy method — @Deprecated, returns old MediaValidationReport
    public MediaValidationReport probeLegacy(String jobId, String relativePath);
}
```

### Constructor Injection

The service explicitly requests `JavaCVMediaProbeAdapter` (the concrete type) rather than the `MediaProbeAdapter` interface. This is intentional — it ensures the adapter is a Spring bean (via `@Component`) and makes the dependency explicit.

### Path Resolution

- `probe()`: Resolves `relativePath` against `app.storage.local-root` (default: `/tmp/platform`)
- `probeAbsolute()`: Uses the path as-is

---

## Usage in Other Services

### RenderQualityCheckService
```java
MediaProbeResult report = probeService.probe(jobId, outputPath);
// Validates resolution, codec, duration, file size against profile
```

### SubtitleBurnInService
Uses `FontRegistryService` (not `MediaProbeService`) for font glyph checking.

---

## Migration from Old Probe Implementations

### Previous Architecture (Before Unification)

Previously, probing was scattered across multiple classes:

| Old Class | Role | Status |
|-----------|------|--------|
| `FFmpegProbeService` | Probed via FFprobe CLI | **Removed** — replaced by `JavaCVMediaProbeAdapter` |
| `MediaValidationReport` | Old result type | **@Deprecated** — use `MediaProbeResult` |
| `MediaProbeService.probeLegacy()` | Old method signature | **@Deprecated** — use `probe()` or `probeAbsolute()` |

### Migration Steps

1. **Replace `MediaValidationReport` with `MediaProbeResult`:**
   ```java
   // Old
   MediaValidationReport report = mediaProbeService.probeLegacy(jobId, path);
   if (report.valid()) {
       int w = report.width();
   }

   // New
   MediaProbeResult result = mediaProbeService.probe(jobId, path);
   if (result.valid()) {
       int w = result.width();
   }
   ```

2. **Replace direct `FFmpegProbeService` usage:**
   ```java
   // Old
   Map<String, Object> data = ffmpegProbeService.probe(inputUri);

   // New
   MediaProbeResult result = mediaProbeService.probeAbsolute(jobId, inputUri);
   ```

3. **Use `hasVideo()` / `hasAudio()` helpers:**
   ```java
   // Old — manual checks
   if (report.width() > 0 && report.videoCodec() != null) { ... }

   // New — helper methods
   if (result.hasVideo()) { ... }
   ```

### Compatibility Layer

`MediaProbeService.probeLegacy()` provides backward compatibility. It calls `probe()` internally and converts the `MediaValidationReport` to the old `MediaValidationReport` type. This method will be removed when all callers are migrated.
