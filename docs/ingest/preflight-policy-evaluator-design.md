# Ingest Preflight Policy Evaluator Design

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-POLICY-EVALUATOR-DESIGN.0

---

## Context

Upload preflight report-only integration complete. Tika + FFprobe merged result exists. Current hook is disabled by default, fail-open, never rejects. Policy evaluator needed before any future enforcement.

---

## Goals

- Define evaluator vocabulary
- Define input/output
- Map warnings to policy findings
- Define rejection candidates
- Define report-only behavior
- Define future enforce boundary
- Define user-safe messages
- Define audit/observability design

## Non-goals

- No upload rejection
- No enforce mode
- No upload behavior change
- No schema migration

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Policy Finding** | Normalized result from evaluating warning/metadata against rule |
| **Policy Severity** | INFO, WARN, REVIEW, BLOCKING_CANDIDATE, BLOCKING, INTERNAL_ONLY |
| **Policy Rule** | Declarative rule mapping preflight input to finding severity |
| **Policy Profile** | Named set of rules for runtime context |
| **Policy Mode** | DISABLED, REPORT_ONLY, ENFORCE |
| **Policy Decision** | ACCEPT, ACCEPT_WITH_WARNINGS, REJECT, DEFER, NEEDS_REVIEW |
| **Report-only Evaluation** | Produces findings but never alters upload acceptance |
| **Enforce Evaluation** | Future mode where BLOCKING findings may reject upload |
| **Enforcement Gate** | Explicit decision required before enabling enforce mode |

---

## Evaluator Input (Design-only)

```java
record IngestPolicyEvaluationInput(
    String tenantId,
    String projectId,
    String policyProfile,
    PreflightPolicyMode policyMode,
    UploadPreflightResult preflightResult,
    IngestMetadataResult metadata,
    MediaTechnicalMetadata mediaTechnicalMetadata,
    String sourceFilename,
    String declaredContentType,
    String detectedContentType,
    MediaCategory mediaCategory,
    Long sizeBytes,
    List<IngestWarning> warnings,
    List<IngestRejectionReason> rejectionReasonCandidates,
    List<DetectorProvenance> detectorProvenance,
    Instant evaluatedAt
) {}
```

---

## Evaluator Output (Design-only)

```java
record IngestPolicyEvaluationResult(
    String policyProfile,
    PreflightPolicyMode policyMode,
    UploadPreflightDecision decision,
    List<PolicyFinding> findings,
    List<IngestWarningCode> warningCodes,
    List<IngestRejectionReasonCode> rejectionReasonCandidates,
    List<IngestRejectionReasonCode> effectiveRejectionReasons,
    List<String> userSafeMessages,
    List<String> internalNotes,
    List<DetectorProvenance> detectorProvenance,
    Instant evaluatedAt,
    String evaluatorVersion
) {}

record PolicyFinding(
    String code,
    String source,
    PolicySeverity severity,
    IngestWarningCode relatedWarningCode,
    IngestRejectionReasonCode relatedRejectionReasonCode,
    boolean userVisible,
    String userSafeMessage,
    String internalReason,
    boolean retryable
) {}
```

---

## Warning-to-Policy Mapping

| Warning Code | Report-only | Enforce Future | Rejection Candidate |
|-------------|-------------|----------------|---------------------|
| DECLARED_CONTENT_TYPE_MISMATCH | WARN | BLOCKING_CANDIDATE | DECLARED_CONTENT_TYPE_CONFLICT |
| EXTENSION_CONTENT_TYPE_MISMATCH | WARN | BLOCKING_CANDIDATE | DETECTED_CONTENT_TYPE_CONFLICT |
| UNKNOWN_CONTENT_TYPE | WARN | BLOCKING_CANDIDATE | UNKNOWN_CONTENT_POLICY |
| UNSUPPORTED_CONTENT_TYPE | BLOCKING_CANDIDATE | BLOCKING | CONTENT_TYPE_UNSUPPORTED |
| DETECTION_LOW_CONFIDENCE | WARN | REVIEW | UNKNOWN_CONTENT_POLICY |
| DETECTION_LIMIT_REACHED | WARN | REVIEW | PARSING_RESOURCE_LIMIT_EXCEEDED |
| EMPTY_FILE | BLOCKING_CANDIDATE | BLOCKING | FILE_EMPTY |
| MISSING_FILENAME | WARN | WARN | — |
| MISSING_EXTENSION | WARN | REVIEW | — |
| SUSPICIOUS_EXTENSION | BLOCKING_CANDIDATE | BLOCKING | EXTENSION_BLOCKED |
| MULTIPLE_EXTENSIONS | WARN | BLOCKING_CANDIDATE | EXTENSION_BLOCKED |
| POSSIBLE_ARCHIVE_BOMB | BLOCKING_CANDIDATE | BLOCKING | ARCHIVE_EXPANSION_LIMIT_EXCEEDED |
| METADATA_TRUNCATED | INFO | WARN | — |
| RAW_METADATA_DROPPED | INFO | INFO | — |
| TEXT_EXTRACTION_DISABLED | INFO | INFO | — |
| OCR_DISABLED | INFO | INFO | — |
| MEDIA_TECHNICAL_METADATA_MISSING | WARN | BLOCKING_CANDIDATE | MEDIA_PROBE_FAILED |

---

## Media Technical Policy Rules

| Condition | Candidate |
|-----------|-----------|
| durationMs > maxDurationMs | MEDIA_DURATION_TOO_LONG |
| width/height > maxResolution | MEDIA_RESOLUTION_UNSUPPORTED |
| video codec not allowed | MEDIA_CODEC_UNSUPPORTED |
| audio codec not allowed | MEDIA_CODEC_UNSUPPORTED |
| no video/audio stream for media | CONTENT_TYPE_UNSUPPORTED |
| probe timeout | PARSING_TIMEOUT |
| probe failed | MEDIA_PROBE_FAILED |
| container not allowed | CONTENT_TYPE_UNSUPPORTED |

---

## Policy Profiles

| Profile | Mode | Reject | Notes |
|---------|------|--------|-------|
| disabled | DISABLED | No | Default |
| preview-report-only | REPORT_ONLY | No | Fail-open |
| strict-media-report-only | REPORT_ONLY | No | Media thresholds |
| future-enforce-media | ENFORCE | Yes | Design-only |

---

## Configuration Sketch

```yaml
ingest:
  preflight:
    enabled: false
    mode: report-only
    upload-integration:
      enabled: false
      fail-open: true
    policy:
      enabled: false
      profile: preview-report-only
      reject-enabled: false
      report-blocking-candidates: true
      media:
        max-duration-ms: 3600000
        max-width: 3840
        max-height: 2160
        allowed-video-codecs: [h264, hevc]
        allowed-audio-codecs: [aac, mp3]
```

---

## Report-only Behavior

| Findings | Decision |
|----------|----------|
| No findings | ACCEPT |
| INFO only | ACCEPT |
| WARN/REVIEW/BLOCKING_CANDIDATE | ACCEPT_WITH_WARNINGS |
| System failure | ACCEPT_WITH_WARNINGS (fail-open) |

**Rule:** No report-only result may cause upload rejection.

---

## Future Enforce Boundary

**Prerequisites:**
- Policy evaluator implementation complete
- Report-only telemetry reviewed
- Safe report persistence decision complete
- User-facing error contract approved
- Allowlist/denylist thresholds approved
- Rollback plan exists
- Test coverage for all rejection cases
- Explicit release gate / ADR

---

## User-safe Messages

| Scenario | Message |
|----------|---------|
| FILE_EMPTY | "The uploaded file is empty." |
| FILE_TOO_LARGE | "The uploaded file exceeds the allowed size." |
| CONTENT_TYPE_UNSUPPORTED | "This file type is not supported." |
| DECLARED_CONTENT_TYPE_CONFLICT | "The uploaded file does not match its declared file type." |
| MEDIA_PROBE_FAILED | "We could not read the media file. Please try another file." |
| MEDIA_DURATION_TOO_LONG | "The media file is longer than the allowed duration." |
| MEDIA_CODEC_UNSUPPORTED | "This media codec is not supported." |
| PARSING_TIMEOUT | "The file could not be checked in time. Please try again." |

---

## Current Decisions

| Decision | Status |
|----------|--------|
| Policy evaluator | DESIGN_ONLY |
| Report-only hook | UNCHANGED |
| Upload behavior | UNCHANGED |
| Reject/enforce | NOT_ENABLED |
| Product/RAW_MEDIA schema | Unchanged |

---

## Follow-up Tasks

1. INGEST-PREFLIGHT-POLICY-EVALUATOR-DTO.0
2. INGEST-PREFLIGHT-POLICY-EVALUATOR-REPORT-ONLY.0
3. INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-DESIGN.0

---

## Status

- INGEST-PREFLIGHT-POLICY-EVALUATOR-DESIGN.0: COMPLETE
- Policy evaluator: DESIGN_ONLY
- Enforcement: NOT_IMPLEMENTED
