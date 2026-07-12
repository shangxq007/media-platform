# Ingest Preflight Contract DTO

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-CONTRACT-DTO.0

---

## Context

Ingest Metadata Contract complete. Upload Preflight Design complete. Tika Detector POC complete. DTO implementation is internal only. Upload behavior remains unchanged.

---

## Implemented Types

### Enums
| Enum | Values |
|------|--------|
| MediaCategory | 10 values |
| IngestWarningCode | 17 values |
| IngestWarningSeverity | 4 values |
| IngestRejectionReasonCode | 18 values |
| DetectorProviderName | 8 values |
| DetectorResultStatus | 8 values |
| DetectorMode | 7 values |
| UploadPreflightPhase | 8 values |
| UploadPreflightDecision | 5 values |
| PreflightPolicyMode | 3 values |

### DTOs
| Type | Fields |
|------|--------|
| IngestWarning | code, severity, sourceProvider, message, userVisible |
| IngestRejectionReason | code, sourcePhase, retryable, userSafeMessage |
| DetectorProvenance | provider, mode, status, warnings |
| IngestMetadataResult | content type, media category, warnings, detectors |
| UploadPreflightResult | metadata, decision, warnings, provenance |

### Helper
| Helper | Purpose |
|--------|---------|
| IngestRejectionMessages | Maps rejection codes to safe user messages |

---

## Sensitive Field Exclusion

**Never included in DTOs:**
- bucket, objectKey, storageReferenceId
- signedUrl, accessKey, secretKey
- rawMetadata, extractedText
- Internal filesystem paths

---

## Tests

| Test | Result |
|------|--------|
| Enum completeness | ✅ PASSED |
| DTO construction | ✅ PASSED |
| Mismatch result | ✅ PASSED |
| User-safe messages | ✅ PASSED |

---

## Status

- INGEST-PREFLIGHT-CONTRACT-DTO.0: COMPLETE
- Runtime behavior: UNCHANGED
- Tika: DISABLED by default
