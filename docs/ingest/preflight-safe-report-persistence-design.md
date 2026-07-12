# Ingest Preflight Safe Report Persistence Design

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-DESIGN.0

---

## Context

Upload preflight report-only integration complete. Policy evaluator design complete. Current hook is disabled by default, fail-open, never rejects. Safe persistence boundary needed before any report storage.

---

## Goals

- Define what may be persisted
- Define what must never be persisted
- Define persistence modes
- Define relation to upload/Product/RAW_MEDIA
- Define access-control and retention
- Define safe DTO/schema sketches
- Define future test plan

## Non-goals

- No DB schema
- No Flyway migration
- No runtime persistence
- No upload behavior change
- No public API change
- No enforcement

---

## Vocabulary

| Term | Meaning |
|------|---------|
| **Preflight Report** | Internal record of what preflight observed and decided |
| **Safe Preflight Summary** | Reduced, sanitized report that may be persisted |
| **Raw Provider Output** | Raw FFprobe JSON, Tika metadata, stderr, parser output |
| **Sensitive Execution Context** | Local path, bucket, objectKey, storageReferenceId, signed URL, credentials |
| **Finding Snapshot** | Persisted warning/policy codes without raw output |
| **Detector Provenance Snapshot** | Provider name/status/mode/duration only |
| **Media Technical Summary** | Allowlisted subset of media metadata |
| **Retention Policy** | How long persisted summary is kept |
| **Visibility Level** | INTERNAL_ONLY, ADMIN_VISIBLE, TENANT_ADMIN_VISIBLE, USER_VISIBLE_FUTURE |

---

## Persistence Decision

| Mode | Description | Status |
|------|-------------|--------|
| NONE | No DB persistence | CURRENT DEFAULT |
| LOG_ONLY | Safe structured logs only | FUTURE |
| SAFE_SUMMARY | DB row with sanitized fields | FUTURE |
| AUDIT_EVENT | Append-only event summary | FUTURE |
| FULL_REPORT | Full raw report | FORBIDDEN |

---

## Allowed Persisted Fields

### Identifiers
- tenantId, projectId, uploadId (future), rawMediaProductId (future), preflightReportId (future)

### Status fields
- policyMode, policyProfile, preflightDecision, reportOnly, failOpen, evaluatorVersion, createdAt

### Finding fields
- warningCodes[], policyFindingCodes[], rejectionCandidateCodes[], userSafeMessageCodes[]

### Detector provenance snapshot
- detectorProviders[], detectorStatuses[], detectorModes[], detectorDurationsMs[]

### Generic metadata summary
- declaredContentType, detectedContentType, normalizedContentType, mediaCategory, sizeBytes, extension

### Media technical summary
- durationMs, containerFormat, bitrate, hasVideo, hasAudio, hasSubtitle
- videoStreamCount, audioStreamCount, subtitleStreamCount
- primaryVideoCodec, primaryAudioCodec, width, height, frameRate
- sampleRate, channels, rotation, probeStatus

---

## Forbidden Fields

| Field | Reason |
|-------|--------|
| Raw FFprobe JSON | Security |
| Raw Tika metadata map | Security |
| Raw parser output | Security |
| Full stderr | Security |
| Full command line | Security |
| Local file path | Security |
| Temporary file path | Security |
| Bucket | Security |
| ObjectKey | Security |
| StorageReferenceId | Security |
| Signed URL | Security |
| X-Amz-Signature | Security |
| AccessKey/SecretKey | Security |
| Credentials | Security |
| Raw uploaded bytes | Security |
| Text extraction output | Security |
| OCR output | Security |
| GPS/location metadata | Security |
| Stack trace | Security |

---

## Report Shape Options

### Option 1: Single safe summary row
```
ingest_preflight_report
  id, tenant_id, project_id, raw_media_product_id
  mode, profile, decision, report_only, fail_open
  media_category, declared_content_type, detected_content_type
  warning_codes, rejection_candidate_codes
  detector_providers, detector_statuses
  media_summary, created_at
```

### Option 2: Event-style append-only
```
ingest_preflight_events
  id, tenant_id, project_id, upload_id
  event_type, mode, decision, finding_codes
  detector_providers, created_at
```

### Option 3: No DB persistence (logs/metrics only)
Safest, simplest, no schema.

**Recommendation:** Start with Option 3 (logs only), move to Option 1 or 2 after DTO contract task.

---

## Relationship to Product / RAW_MEDIA

| Link Strategy | Use Case |
|---------------|----------|
| rawMediaProductId | After RAW_MEDIA created (report-only) |
| uploadId | Before Product creation (future enforce) |
| tenantId/projectId | Always available |

**Recommendation:** Future persistence should support upload-level identity before RAW_MEDIA creation.

---

## Report-only Semantics

- May persist safe summary in future
- Must not block upload on persistence failure
- Persistence failure must be fail-open
- Summary must mark reportOnly=true
- Summary must not contain effective rejection
- Public upload response unchanged

---

## Future Enforce Semantics

- May persist for accepted and rejected uploads
- Rejected upload may not have RAW_MEDIA Product
- Upload-level identity required
- effectiveRejectionReasons may be persisted
- Raw provider outputs still forbidden

---

## Access Control

| Level | Description |
|-------|-------------|
| INTERNAL_ONLY | Default for all reports |
| ADMIN_VISIBLE | Platform admins only |
| TENANT_ADMIN_VISIBLE | Future, sanitized summaries |
| USER_VISIBLE_FUTURE | Safe user-facing messages only |
| NOT_VISIBLE | Raw/internal data, never stored |

---

## Retention Policy

| Mode | Retention |
|------|-----------|
| LOG_ONLY | Normal log retention |
| SAFE_SUMMARY | 30-90 days |
| AUDIT_EVENT | Configurable, longer |
| Rejected uploads | Short unless compliance requires |

---

## Redaction Rules

- Normalize content types to lowercase
- Store extension, not full filename, by default
- Store codes, not verbose internal notes
- Store provider names/statuses, not raw outputs
- No command line
- No stderr (or redacted summary only if approved)

---

## Configuration Sketch

```yaml
ingest:
  preflight:
    report-persistence:
      enabled: false
      mode: none
      persist-safe-summary: false
      include-source-filename: false
      include-detector-durations: true
      include-media-summary: true
      retention-days: 30
      visibility: internal-only
```

---

## Current Decisions

| Decision | Status |
|----------|--------|
| Preflight report persistence | NOT_IMPLEMENTED |
| Full raw report persistence | FORBIDDEN |
| Safe summary persistence | FUTURE_ONLY |
| Report-only hook | Disabled by default |
| Upload behavior | UNCHANGED |
| Product/RAW_MEDIA schema | Unchanged |

---

## Follow-up Tasks

1. INGEST-PREFLIGHT-SAFE-REPORT-DTO.0
2. INGEST-PREFLIGHT-POLICY-EVALUATOR-DTO.0
3. STORAGE-DELIVERY-PROFILE-CONTRACT.0

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-DESIGN.0: COMPLETE
- Persistence: NOT_IMPLEMENTED
- Full raw report: FORBIDDEN
