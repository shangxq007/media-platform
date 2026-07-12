# Upload Preflight Design

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-UPLOAD-PREFLIGHT-DESIGN.0

---

## Context

Tika evaluation complete. Tika detector POC complete. Ingest Metadata Contract complete. Tika disabled by default. Upload preflight not implemented yet. Need policy design before runtime integration.

---

## Goals

- Define preflight phases
- Classify warnings
- Map rejection reasons
- Define policy decision model
- Define safe user-facing errors
- Define report-only/enforce modes
- Protect RAW_MEDIA/Product canonical model

## Non-goals

- No runtime implementation
- No upload behavior change
- No schema migration
- No FFprobe replacement
- No malware scanning

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Upload Preflight** | Validation before RAW_MEDIA Product creation |
| **Preflight Phase** | Ordered stage in upload assessment |
| **Preflight Finding** | Warning, rejection, or metadata observation |
| **Warning** | Non-fatal finding |
| **Rejection Reason** | Fatal policy reason |
| **Policy Decision** | ACCEPT / ACCEPT_WITH_WARNINGS / REJECT / DEFER / NEEDS_REVIEW |
| **Staged Upload** | Uploaded object stored temporarily before acceptance |
| **Accepted RAW_MEDIA** | Product accepted into canonical flow |
| **Quarantine** | Future optional review state |

---

## Preflight Phases

| Phase | Name | Description |
|-------|------|-------------|
| 0 | Request Metadata Validation | Filename, declared type, size, scope |
| 1 | Storage Staging | Upload staged to internal storage |
| 2 | Generic Content Detection | Tika/Basic MIME detection, mismatches |
| 3 | Media-specific Probe | FFprobe for VIDEO/AUDIO |
| 4 | Security/Safety Gate | Future scanner, archive checks |
| 5 | Policy Evaluation | Combine findings, decide accept/reject |
| 6 | RAW_MEDIA Creation | Only after policy allows |
| 7 | Post-accept Enrichment | Optional future enrichment |

**Note:** Current runtime does not implement this full sequence.

---

## Policy Decision Model

| Decision | Meaning |
|----------|---------|
| ACCEPT | No blocking issues, safe to create RAW_MEDIA |
| ACCEPT_WITH_WARNINGS | Non-blocking findings, warnings retained |
| REJECT | Policy-blocking issue, return safe error |
| DEFER | Temporary/system reason, may retry |
| NEEDS_REVIEW | Future manual/async review |

---

## Warning Classification

| Warning Code | Severity | Policy Effect |
|-------------|----------|---------------|
| RAW_METADATA_DROPPED | INFO | None |
| TEXT_EXTRACTION_DISABLED | INFO | None |
| OCR_DISABLED | INFO | None |
| MEDIA_TECHNICAL_METADATA_MISSING | INFO | None |
| DECLARED_CONTENT_TYPE_MISMATCH | WARNING | Review candidate |
| EXTENSION_CONTENT_TYPE_MISMATCH | WARNING | Review candidate |
| UNKNOWN_CONTENT_TYPE | WARNING | Review candidate |
| DETECTION_LOW_CONFIDENCE | WARNING | Review candidate |
| DETECTION_LIMIT_REACHED | WARNING | Review candidate |
| MISSING_FILENAME | WARNING | Review candidate |
| MISSING_EXTENSION | WARNING | Review candidate |
| METADATA_TRUNCATED | WARNING | Review candidate |
| UNSUPPORTED_CONTENT_TYPE | POLICY_BLOCKING | Future rejection |
| EMPTY_FILE | POLICY_BLOCKING | Future rejection |
| SUSPICIOUS_EXTENSION | POLICY_BLOCKING | Future rejection |
| MULTIPLE_EXTENSIONS | POLICY_BLOCKING | Future rejection |
| POSSIBLE_ARCHIVE_BOMB | POLICY_BLOCKING | Future rejection |

---

## Rejection Policy Mapping

| Rejection Reason | Enforcement | Retryable |
|-----------------|-------------|-----------|
| FILE_EMPTY | FUTURE_ENFORCED | No |
| FILE_TOO_LARGE | FUTURE_ENFORCED | No |
| CONTENT_TYPE_UNSUPPORTED | FUTURE_ENFORCED | No |
| CONTENT_TYPE_BLOCKED | FUTURE_ENFORCED | No |
| EXTENSION_BLOCKED | FUTURE_ENFORCED | No |
| DECLARED_CONTENT_TYPE_CONFLICT | FUTURE/WARNING | No |
| DETECTED_CONTENT_TYPE_CONFLICT | FUTURE/WARNING | No |
| MEDIA_PROBE_FAILED | FUTURE_ENFORCED | Maybe |
| MEDIA_DURATION_TOO_LONG | FUTURE_ENFORCED | No |
| MEDIA_CODEC_UNSUPPORTED | FUTURE_ENFORCED | No |
| MEDIA_RESOLUTION_UNSUPPORTED | FUTURE_ENFORCED | No |
| ARCHIVE_NOT_ALLOWED | FUTURE_ENFORCED | No |
| ARCHIVE_EXPANSION_LIMIT_EXCEEDED | FUTURE_ENFORCED | No |
| PARSING_TIMEOUT | DEFER/FUTURE | Yes |
| PARSING_RESOURCE_LIMIT_EXCEEDED | FUTURE_ENFORCED | No |
| SECURITY_SCAN_FAILED | FUTURE_ENFORCED | No |
| SECURITY_SCAN_REQUIRED | FUTURE_ENFORCED | No |
| UNKNOWN_CONTENT_POLICY | DEFER | Yes |

---

## Preflight Result Shape (Design-only)

```java
record UploadPreflightResult(
    // Request
    String tenantId,
    String projectId,
    String sourceFilename,
    String declaredContentType,
    long sizeBytes,

    // Metadata
    IngestMetadataResult ingestMetadataResult,

    // Policy
    PolicyDecision decision,
    List<String> warnings,
    List<String> rejectionReasons,
    List<String> userSafeMessages,

    // Provenance
    List<DetectorProvenance> detectorProvenance,
    String policyVersion,
    Instant evaluatedAt,

    // Storage
    String stagedStorageReference,
    String acceptedStorageReference,

    // Security
    String scanStatus,
    String quarantineStatus
) {}
```

---

## User-facing Error Mapping

| Scenario | Message |
|----------|---------|
| FILE_EMPTY | "The uploaded file is empty." |
| FILE_TOO_LARGE | "The uploaded file exceeds the allowed size." |
| CONTENT_TYPE_UNSUPPORTED | "This file type is not supported." |
| EXTENSION_CONTENT_TYPE_MISMATCH | "The file extension does not match the detected file type." |
| MEDIA_PROBE_FAILED | "We could not read the media file. Please try another file." |
| PARSING_TIMEOUT | "The file could not be checked in time. Please try again." |
| SECURITY_SCAN_REQUIRED | "The file requires additional security checks before it can be used." |

---

## Policy Configuration Sketch (Design-only)

```yaml
ingest:
  preflight:
    enabled: false
    mode: report-only
    max-file-size-mb: 500
    allowed-media-categories:
      - VIDEO
      - AUDIO
      - IMAGE
      - SUBTITLE
    blocked-extensions:
      - exe
      - bat
      - sh
    suspicious-multiple-extensions: true
    unknown-content-type-policy: warn
    mismatch-policy: warn
    require-media-probe-for:
      - VIDEO
      - AUDIO
```

**Modes:** disabled, report-only, enforce

---

## Provider Execution Order

1. Basic request/file observation
2. TikaDetectorProvider (generic MIME)
3. FFprobeMediaMetadataProvider (VIDEO/AUDIO)
4. Optional MediaInfo/ExifTool enrichers
5. Future security scanner
6. IngestPolicyEvaluator

---

## RAW_MEDIA Creation Timing

**Future strict mode:** Run preflight before canonical RAW_MEDIA creation.

**Initial integration:** Report-only to avoid breaking upload behavior.

---

## Audit / Observability

- Preflight decision logged with correlation ID
- Detector provider names logged
- Warning/rejection codes logged
- No raw metadata/logs/file paths
- Duration/timeout metrics (future)

---

## Current Decisions

| Decision | Status |
|----------|--------|
| Upload preflight | DESIGN_ONLY |
| Tika | Detector-only, disabled by default |
| FFprobe | Primary media technical metadata |
| Initial integration mode | REPORT_ONLY |
| Product/RAW_MEDIA schema | Unchanged |
| Upload behavior | Unchanged |

---

## Follow-up Tasks

1. INGEST-PREFLIGHT-CONTRACT-DTO.0
2. INGEST-TIKA-PREFLIGHT-REPORT-ONLY.0
3. INGEST-FFPROBE-METADATA-DESIGN.0
4. INGEST-PREFLIGHT-POLICY-EVALUATOR.0

---

## Status

- INGEST-UPLOAD-PREFLIGHT-DESIGN.0: COMPLETE
- Policy design: DEFINED
- Runtime integration: NOT_IMPLEMENTED
