# Safe Preflight Report Report-only Persistence Design Review

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-REPORT-ONLY-PERSISTENCE-DESIGN-REVIEW.0

---

## Decision

**GO_WITH_LIMITS**

---

## Executive Summary

Safe preflight report persistence is recommended with strict limits:
- Dev/preview ephemeral only
- 7-day retention
- Internal /dev visibility only
- No raw metadata
- No public exposure
- Fail-open integration

**Main risks:**
- Schema coupling to future enforcement
- Retention cleanup complexity
- Access control drift

**Safest next step:**
Implement drift guard additions before any persistence code.

---

## Current State

| Component | Status |
|-----------|--------|
| Report-only evaluator | ✅ IMPLEMENTED |
| Hook integration | ✅ IMPLEMENTED |
| Config binding | ✅ IMPLEMENTED |
| Diagnostics | ✅ IMPLEMENTED |
| /dev frontend | ✅ IMPLEMENTED |
| Persistence | ❌ NOT_IMPLEMENTED |
| Enforcement | ❌ NOT_ENABLED |

---

## Motivation Assessment

**Strength: MODERATE**

Valid reasons:
- Debugging upload failures in preview/dev
- Tracking report-only warnings over time
- Supporting internal QA of detector/policy rules
- Comparing Tika/FFprobe behavior across fixtures

Invalid/premature reasons:
- Storing everything for convenience
- Preparing enforcement without explicit task
- Audit/compliance claims without requirements
- User-facing history before access model exists

---

## Persistence Modes Reviewed

| Mode | Risk | Value | Recommended |
|------|------|-------|-------------|
| No persistence | LOW | LOW | NO |
| Dev/preview ephemeral | LOW | MEDIUM | YES |
| Tenant-scoped internal | MEDIUM | MEDIUM | NO |
| Product-linked metadata | HIGH | LOW | NO |
| Policy result only | LOW | LOW | NO |
| Event/outbox only | MEDIUM | LOW | NO |

---

## Recommended Mode

**DEV_PREVIEW_EPHEMERAL_ONLY**

- 7-day retention
- Internal /dev visibility only
- No production certification
- Fail-open integration

---

## Allowed Fields

| Field | Source | Risk | Allowed |
|-------|--------|------|---------|
| createdAt | System | LOW | YES |
| reportOnlyMode | Config | LOW | YES |
| failOpen | Config | LOW | YES |
| overallDecision | Evaluator | LOW | YES |
| warningCount | Evaluator | LOW | YES |
| findingCount | Evaluator | LOW | YES |
| rejectCandidateCount | Evaluator | LOW | YES |
| detectedContentType | Tika | LOW | YES |
| declaredContentType | Request | LOW | YES |
| mimeMismatch | Computed | LOW | YES |
| mediaCategory | Computed | LOW | YES |
| durationMs | FFprobe | LOW | YES |
| width | FFprobe | LOW | YES |
| height | FFprobe | LOW | YES |
| containerFormat | FFprobe | LOW | YES |
| videoCodec | FFprobe | LOW | YES |
| audioCodec | FFprobe | LOW | YES |
| warningCodes | Evaluator | LOW | YES |
| findingCodes | Evaluator | LOW | YES |
| policyProfile | Config | LOW | YES |
| policyResultDecision | Evaluator | LOW | YES |

---

## Forbidden Fields

| Field | Reason |
|-------|--------|
| raw FFprobe JSON | Security |
| raw Tika metadata | Security |
| raw media metadata | Security |
| extracted text | Security |
| OCR text | Security |
| local path | Security |
| temp path | Security |
| upload file path | Security |
| bucket | Security |
| objectKey | Security |
| storageReferenceId | Security |
| signed URL | Security |
| presigned URL | Security |
| accessKey/secretKey | Security |
| credentials | Security |
| provider endpoint | Security |
| command line | Security |
| stderr/stdout | Security |
| stack trace | Security |
| GPS/location EXIF | Privacy |
| device/camera metadata | Privacy |
| full original filename | Privacy |

---

## Access Boundary

| Aspect | Recommendation |
|--------|----------------|
| Reader roles | DEV_ONLY |
| Endpoint family | /dev/* |
| Frontend visibility | /dev/ingest/preflight-policy only |
| User-facing exposure | NONE |
| Admin exposure | FUTURE_WITH_REVIEW |

---

## Retention

| Aspect | Recommendation |
|--------|----------------|
| Retention window | 7 days |
| Cleanup requirement | Automatic TTL-based |
| Deletion behavior | Hard delete after TTL |
| Redaction behavior | Not needed (no raw data) |

---

## Runtime Integration Boundary

| Aspect | Recommendation |
|--------|----------------|
| Integration point | After policy evaluation |
| Sync vs async | Async (fail-open) |
| Failure behavior | Log warning, continue upload |
| Default enablement | Disabled by default |
| Public response change | NONE |

---

## Drift Guard Requirements Before Implementation

Required additions to `scripts/check-architecture-drift.sh`:

1. No raw FFprobe JSON in persistence code
2. No raw Tika metadata in persistence code
3. No localPath/filePath in persistence code
4. No bucket/objectKey/storageReferenceId in persistence code
5. No signedUrl/presignedUrl in persistence code
6. No accessKey/secretKey/credentials in persistence code
7. No OCR/extractedText in persistence code
8. No public upload response field added
9. No Product metadata mutation
10. Persistence writer must be fail-open
11. Upload rejection remains not implemented
12. Enforce mode remains not enabled

---

## Tests Required Before Implementation

1. Serialization forbidden field tests
2. Repository forbidden field tests
3. Migration forbidden field tests
4. Upload behavior unchanged tests
5. Fail-open persistence failure tests
6. Access control tests
7. Retention cleanup tests

---

## Non-goals Reaffirmed

- No enforce mode
- No upload rejection
- No public upload response change
- No raw metadata persistence
- No storage internals persistence
- No signed URL persistence
- No Product/RAW_MEDIA schema change

---

## Recommended Next Task

**INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-DRIFT-GUARD.0**

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-REPORT-ONLY-PERSISTENCE-DESIGN-REVIEW.0: COMPLETE
- Decision: GO_WITH_LIMITS
- Implementation: NOT_STARTED
