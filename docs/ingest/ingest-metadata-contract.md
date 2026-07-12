# Ingest Metadata Contract

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-METADATA-CONTRACT.0

---

## Context

Tika evaluation complete. Tika detector POC complete. Tika disabled by default. Need stable contract before upload integration. FFprobe remains primary for media technical metadata.

---

## Goals

- Define canonical metadata result
- Define warnings
- Define rejection reasons
- Define detector provenance
- Define allowlisted metadata
- Support future upload preflight
- Keep Product/RAW_MEDIA schema stable

## Non-goals

- No runtime upload integration
- No schema migration
- No FFprobe replacement
- No full text extraction
- No OCR

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Ingest Metadata** | Metadata observed during upload/ingestion |
| **Declared Content Type** | Content type supplied by client (untrusted hint) |
| **Detected Content Type** | Content type inferred by detector (more reliable) |
| **Filename Extension** | Extension parsed from filename (untrusted hint) |
| **Detector Provider** | Component that produces metadata observations |
| **Detector Provenance** | Source and method used to produce metadata |
| **Warning** | Non-fatal ingest finding |
| **Rejection Reason** | Fatal/policy-level reason to reject (future) |
| **Metadata Allowlist** | Explicit set of safe fields |
| **Raw Parser Output** | Full tool output, must not be stored by default |

---

## Result Shape (Design-only)

```java
record IngestMetadataResult(
    // Identity
    String sourceFilename,
    String normalizedExtension,
    long sizeBytes,

    // Content type
    String declaredContentType,
    String detectedContentType,
    String normalizedContentType,
    MediaCategory mediaCategory,

    // Validation
    boolean extensionMatchesDetectedType,
    boolean declaredMatchesDetectedType,
    boolean acceptedByPolicy,
    List<String> warnings,
    List<String> rejectionReasons,

    // Provenance
    List<DetectorProvenance> detectors,
    String primaryDetector,
    String detectionMode,

    // Safe metadata (allowlisted only)
    Map<String, String> safeMetadata,

    // Privacy
    boolean containsExtractedText,
    boolean containsRawMetadata,
    boolean redactionApplied
) {}
```

**Note:** Design-only. Not implemented. No schema change.

---

## Media Categories

| Category | Derivation | Notes |
|----------|------------|-------|
| VIDEO | content type starts with video/ | FFprobe for technical metadata |
| AUDIO | content type starts with audio/ | FFprobe for technical metadata |
| IMAGE | content type starts with image/ | ExifTool for specialized metadata |
| TEXT | content type starts with text/ | — |
| DOCUMENT | application/pdf, office formats | Tika for light metadata only |
| ARCHIVE | application/zip, etc. | Security considerations |
| SUBTITLE | text/vtt, application/ttml | — |
| PROJECT_FILE | application/x-*, specialized | — |
| UNKNOWN | cannot determine | Not rejection by itself |
| UNSUPPORTED | policy cannot process | May trigger rejection |

---

## Warning Codes

| Code | Meaning | Severity | Provider |
|------|---------|----------|----------|
| DECLARED_CONTENT_TYPE_MISMATCH | Declared ≠ detected | WARNING | Tika/Basic |
| EXTENSION_CONTENT_TYPE_MISMATCH | Extension ≠ detected | WARNING | Tika/Basic |
| UNKNOWN_CONTENT_TYPE | Cannot determine type | WARNING | Tika |
| UNSUPPORTED_CONTENT_TYPE | Policy cannot process | WARNING | Policy |
| DETECTION_LOW_CONFIDENCE | Detector confidence low | INFO | Tika |
| DETECTION_LIMIT_REACHED | Byte limit reached | INFO | Tika |
| EMPTY_FILE | File is empty | WARNING | Basic |
| MISSING_FILENAME | No filename provided | INFO | Basic |
| MISSING_EXTENSION | No extension | INFO | Basic |
| SUSPICIOUS_EXTENSION | Double extension, etc. | WARNING | Basic |
| MULTIPLE_EXTENSIONS | Multiple dots | INFO | Basic |
| POSSIBLE_ARCHIVE_BOMB | Archive expansion risk | WARNING | Security |
| METADATA_TRUNCATED | Metadata too large | INFO | Tika |
| RAW_METADATA_DROPPED | Raw output not stored | INFO | Policy |
| TEXT_EXTRACTION_DISABLED | Text extraction off | INFO | Tika |
| OCR_DISABLED | OCR not enabled | INFO | Tika |
| MEDIA_TECHNICAL_METADATA_MISSING | No FFprobe result | INFO | — |

---

## Rejection Reasons (Design-only)

| Code | Meaning | Currently Enforced |
|------|---------|-------------------|
| FILE_EMPTY | File is empty | NO |
| FILE_TOO_LARGE | Exceeds size limit | NO |
| CONTENT_TYPE_UNSUPPORTED | Type not supported | NO |
| CONTENT_TYPE_BLOCKED | Type blocked by policy | NO |
| EXTENSION_BLOCKED | Extension blocked | NO |
| DECLARED_CONTENT_TYPE_CONFLICT | Declared ≠ detected conflict | NO |
| DETECTED_CONTENT_TYPE_CONFLICT | Detected type conflict | NO |
| MEDIA_PROBE_FAILED | FFprobe/MediaInfo failed | NO |
| MEDIA_DURATION_TOO_LONG | Duration exceeds limit | NO |
| MEDIA_CODEC_UNSUPPORTED | Codec not supported | NO |
| MEDIA_RESOLUTION_UNSUPPORTED | Resolution not supported | NO |
| ARCHIVE_NOT_ALLOWED | Archives not allowed | NO |
| ARCHIVE_EXPANSION_LIMIT_EXCEEDED | Archive too large expanded | NO |
| PARSING_TIMEOUT | Parser timeout | NO |
| PARSING_RESOURCE_LIMIT_EXCEEDED | Resource limit exceeded | NO |
| SECURITY_SCAN_FAILED | Security scan failed | NO |
| SECURITY_SCAN_REQUIRED | Security scan required | NO |
| UNKNOWN_CONTENT_POLICY | Policy cannot determine | NO |

**Note:** Design-only. Not enforced yet.

---

## Detector Provenance

```java
record DetectorProvenance(
    String provider,        // TIKA, FFPROBE, MEDIAINFO, EXIFTOOL, BASIC
    String providerVersion,
    String mode,            // DETECTOR_ONLY, LIGHT_METADATA, FULL_PARSE
    int inputBytesLimit,
    int observedBytes,
    boolean usedFilename,
    boolean usedDeclaredContentType,
    boolean usedMagicBytes,
    Instant startedAt,
    long durationMs,
    String resultStatus,    // SUCCESS, PARTIAL, UNKNOWN, FAILED, SKIPPED, DISABLED, TIMEOUT
    List<String> warnings
) {}
```

---

## Metadata Allowlist

### Safe fields (allowed)
- detectedContentType
- declaredContentType
- normalizedContentType
- mediaCategory
- sourceFilename
- normalizedExtension
- sizeBytes
- warnings
- rejectionReasons
- detectorNames
- durationMs (detector execution)

### Future media fields (FFprobe/MediaInfo)
- durationMs, width, height, frameRate
- videoCodec, audioCodec, bitrate
- containerFormat, hasAudio, hasVideo, hasSubtitle

### Forbidden by default
- Raw Tika metadata map
- Full extracted text
- OCR output
- Embedded document author metadata
- GPS/location metadata
- Filesystem path
- Storage bucket, object key, storageReferenceId
- Signed URL, credentials
- Internal temp path
- Full FFprobe raw JSON

---

## JSON Examples

### Valid MP4 upload
```json
{
  "sourceFilename": "clip.mp4",
  "declaredContentType": "video/mp4",
  "detectedContentType": "video/mp4",
  "normalizedContentType": "video/mp4",
  "mediaCategory": "VIDEO",
  "extensionMatchesDetectedType": true,
  "declaredMatchesDetectedType": true,
  "warnings": [],
  "rejectionReasons": [],
  "detectors": [{"provider": "TIKA", "mode": "DETECTOR_ONLY", "resultStatus": "SUCCESS"}]
}
```

### Extension mismatch
```json
{
  "sourceFilename": "image.txt",
  "declaredContentType": "text/plain",
  "detectedContentType": "image/png",
  "mediaCategory": "IMAGE",
  "extensionMatchesDetectedType": false,
  "declaredMatchesDetectedType": false,
  "warnings": ["EXTENSION_CONTENT_TYPE_MISMATCH", "DECLARED_CONTENT_TYPE_MISMATCH"],
  "rejectionReasons": []
}
```

### Unknown content
```json
{
  "sourceFilename": "upload.bin",
  "declaredContentType": "application/octet-stream",
  "detectedContentType": "application/octet-stream",
  "mediaCategory": "UNKNOWN",
  "warnings": ["UNKNOWN_CONTENT_TYPE"],
  "rejectionReasons": []
}
```

---

## Provider Merge Strategy

1. Basic filename/declared content-type observation
2. Tika detector for generic MIME detection
3. FFprobe for VIDEO/AUDIO technical metadata
4. MediaInfo/ExifTool as optional specialized enrichers
5. Security scanner as separate policy gate

**Rules:**
- Declared content type is never authoritative
- Tika MIME result classifies general content type
- FFprobe overrides/adds media technical metadata for video/audio
- Conflicting detector results produce warnings
- Rejection policy is separate from detection
- Raw outputs stay isolated

---

## Integration Boundaries (Future)

```
Upload request
  → store/input staging
  → IngestMetadataService
    → TikaDetectorProvider
    → FFprobeMediaMetadataProvider
    → MediaInfoMetadataProvider
    → ExifToolMetadataProvider
  → IngestMetadataResult
  → IngestPolicyEvaluator
  → RAW_MEDIA Product creation
```

**Note:** Design-only. Not implemented.

---

## Security / Privacy Rules

- No raw provider output by default
- No extracted text by default
- No OCR by default
- No sensitive metadata persistence without allowlist
- No storage internals
- No signed URLs
- No credentials
- No internal filesystem paths

---

## Current Decisions

| Decision | Status |
|----------|--------|
| Tika | Detector-only, disabled by default |
| FFprobe | Primary media technical metadata |
| Product/RAW_MEDIA schema | Unchanged |
| Upload behavior | Unchanged |
| Rejection reasons | Design-only |
| Runtime policy enforcement | Future |

---

## Follow-up Tasks

1. INGEST-UPLOAD-PREFLIGHT-DESIGN.0
2. INGEST-METADATA-CONTRACT-DTO.0
3. INGEST-TIKA-METADATA-INTEGRATION.0
4. INGEST-FFPROBE-METADATA-DESIGN.0

---

## Status

- INGEST-METADATA-CONTRACT.0: COMPLETE
- Contract: DEFINED
- Runtime integration: NOT_IMPLEMENTED
