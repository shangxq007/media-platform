# FFprobe Media Metadata DTO

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-FFPROBE-METADATA-DTO.0

---

## Context

FFprobe Media Metadata Design complete. DTO implementation is internal only. Runtime FFprobe probing is not implemented. Upload behavior remains unchanged.

---

## Implemented Types

### Enums
| Enum | Values |
|------|--------|
| MediaStreamType | 6 values |
| MediaProbeProvider | 4 values |
| MediaProbeStatus | 8 values |

### Value Objects
| Type | Fields |
|------|--------|
| VideoStreamMetadata | codec, width, height, frameRate, bitrate, pixelFormat, colorSpace, rotation |
| AudioStreamMetadata | codec, sampleRate, channels, bitrate |
| SubtitleStreamMetadata | codec, language |
| MediaProbeSummary | provider, status, durationMs, warnings |
| MediaTechnicalMetadata | mediaCategory, duration, codec, resolution, streams, probe |

---

## Forbidden Fields

**Never included:**
- Raw FFprobe JSON
- Full stderr
- Local file path
- Storage bucket/key
- storageReferenceId
- Signed URL
- Credentials

---

## Tests

| Test | Result |
|------|--------|
| Video metadata | ✅ PASSED |
| Audio-only metadata | ✅ PASSED |
| No sensitive fields | ✅ PASSED |

---

## Status

- INGEST-FFPROBE-METADATA-DTO.0: COMPLETE
- Runtime probing: NOT_IMPLEMENTED
- Upload behavior: UNCHANGED
