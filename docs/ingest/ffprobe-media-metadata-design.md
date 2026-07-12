# FFprobe Media Metadata Design

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-FFPROBE-METADATA-DESIGN.0

---

## Context

Tika detector POC complete. Tika report-only preflight complete. Ingest metadata contract complete. FFprobe remains primary media technical metadata provider. This task is design-only.

---

## FFprobe Role

| Aspect | FFprobe | Tika |
|--------|---------|------|
| Primary purpose | Video/audio technical metadata | Generic MIME detection |
| Duration | ✅ | ❌ |
| Codec | ✅ | ❌ |
| Resolution | ✅ | ❌ |
| Frame rate | ✅ | ❌ |
| Container format | ✅ | ⚠️ |
| MIME detection | ⚠️ | ✅ |
| Extension mismatch | ❌ | ✅ |

---

## Metadata Field Taxonomy

### Allowlisted (safe for future use)
| Field | Description |
|-------|-------------|
| durationMs | Duration in milliseconds |
| containerFormat | Container format |
| formatLongName | Format long name |
| bitrate | Overall bitrate |
| sizeBytes | File size |
| hasVideo | Has video stream |
| hasAudio | Has audio stream |
| hasSubtitle | Has subtitle stream |
| videoStreamCount | Video stream count |
| audioStreamCount | Audio stream count |
| subtitleStreamCount | Subtitle stream count |
| primaryVideoCodec | Primary video codec |
| primaryAudioCodec | Primary audio codec |
| width | Video width |
| height | Video height |
| frameRate | Frame rate |
| sampleRate | Audio sample rate |
| channels | Audio channels |
| rotation | Video rotation |
| pixelFormat | Pixel format |
| colorSpace | Color space |

### Internal-only (debug)
| Field | Description |
|-------|-------------|
| probeDurationMs | Probe execution time |
| ffprobeVersion | FFprobe version |
| probeExitCode | Exit code |
| stderrSummary | Redacted stderr |

### Forbidden
| Field | Reason |
|-------|--------|
| Full raw FFprobe JSON | Security |
| Absolute local path | Security |
| Temporary file path | Security |
| Storage bucket/key | Security |
| Signed URL | Security |
| Credentials | Security |
| Full stderr | Security |

---

## Media Technical Metadata Contract (Design-only)

```java
record MediaTechnicalMetadata(
    MediaCategory mediaCategory,
    Long durationMs,
    String containerFormat,
    Long bitrate,
    Long sizeBytes,
    boolean hasVideo,
    boolean hasAudio,
    boolean hasSubtitle,
    VideoStreamMetadata video,
    AudioStreamMetadata audio,
    StreamCounts streams,
    MediaProbeSummary probe
) {}

record VideoStreamMetadata(
    String codec,
    Integer width,
    Integer height,
    Double frameRate,
    String pixelFormat,
    String colorSpace,
    Integer rotation
) {}

record AudioStreamMetadata(
    String codec,
    Integer sampleRate,
    Integer channels,
    Long bitrate
) {}
```

---

## Command Profile (Design-only)

```bash
ffprobe -v error -print_format json -show_format -show_streams <input>
```

**Rules:**
- No user-controlled arguments
- Bounded timeout
- No full frame counting initially
- Input must be local/staged file
- stderr summarized/redacted

---

## Failure Mapping

| Condition | Status | Warning | Rejection Candidate |
|-----------|--------|---------|---------------------|
| FFprobe missing | FAILED | MEDIA_TECHNICAL_METADATA_MISSING | MEDIA_PROBE_FAILED |
| Timeout | TIMEOUT | DETECTION_LIMIT_REACHED | PARSING_TIMEOUT |
| Unsupported container | FAILED | UNSUPPORTED_CONTENT_TYPE | CONTENT_TYPE_UNSUPPORTED |
| Corrupt media | FAILED | MEDIA_TECHNICAL_METADATA_MISSING | MEDIA_PROBE_FAILED |
| No streams | PARTIAL | UNKNOWN_CONTENT_TYPE | CONTENT_TYPE_UNSUPPORTED |
| Duration too long | SUCCESS | Policy warning | MEDIA_DURATION_TOO_LONG |
| Codec unsupported | SUCCESS | Policy warning | MEDIA_CODEC_UNSUPPORTED |
| Resolution unsupported | SUCCESS | Policy warning | MEDIA_RESOLUTION_UNSUPPORTED |

**Note:** Report-only never rejects. Enforce mode is future.

---

## Tika + FFprobe Merge Strategy

```
1. TikaDetectorProvider → generic MIME detection
2. If VIDEO/AUDIO → FFprobeMediaMetadataProvider
3. Merge into IngestMetadataResult
4. UploadPreflightResult (report-only)
```

**Conflict rules:**
- Tika classifies generic content type
- FFprobe confirms/contradicts media category
- FFprobe failure → MEDIA_TECHNICAL_METADATA_MISSING warning
- Raw outputs stay isolated

---

## Future Report-only Integration

```yaml
ingest:
  preflight:
    enabled: false
    mode: report-only
    media-probe:
      enabled: false
      provider: ffprobe
      timeout-ms: 5000
      require-for: [VIDEO, AUDIO]
```

---

## Future Test Plan

- Valid MP4 metadata extraction
- Valid audio metadata extraction
- Corrupt media warning
- Timeout handling
- FFprobe missing binary handling
- Tika + FFprobe merge success
- Report-only no rejection

---

## Current Decisions

| Decision | Status |
|----------|--------|
| FFprobe | Primary media technical metadata |
| FFprobe integration | NOT_IMPLEMENTED |
| Initial mode | REPORT_ONLY |
| Upload behavior | UNCHANGED |
| Product/RAW_MEDIA schema | Unchanged |

---

## Follow-up Tasks

1. INGEST-FFPROBE-METADATA-DTO.0
2. INGEST-FFPROBE-METADATA-POC.0
3. INGEST-TIKA-FFPROBE-MERGE-REPORT-ONLY.0

---

## Status

- INGEST-FFPROBE-METADATA-DESIGN.0: COMPLETE
- Design: DEFINED
- Runtime integration: NOT_IMPLEMENTED
