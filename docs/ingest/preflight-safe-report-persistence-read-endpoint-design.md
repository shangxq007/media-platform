# Safe Preflight Report Persistence Read Endpoint Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-READ-ENDPOINT-DESIGN.0
**Decision:** READ_ENDPOINT_DESIGN_READY_WITH_LIMITS

---

## Context

Schema implemented. Writer integrated. Runtime persistence CONFIG_GATED. Default DISABLED.

---

## Endpoint Family

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/dev/ingest/preflight-reports` | GET | List records |
| `/dev/ingest/preflight-reports/{id}` | GET | Get record |
| `/dev/ingest/preflight-reports/tenant/{tenantId}` | GET | Query by tenant |
| `/dev/ingest/preflight-reports/product/{rawMediaProductId}` | GET | Query by product |

---

## Safe Response DTO

**Forbidden fields:**
- rawFfprobeJson, rawTikaMetadata, rawMetadata
- localPath, filePath, tempPath
- bucket, objectKey, storageReferenceId
- signedUrl, presignedUrl
- accessKey, secretKey, credentials
- originalFilename, fileHash
- extractedText, ocrText

**Allowed fields:**
- id, tenantId, projectId, rawMediaProductId, uploadAttemptId
- createdAt, expiresAt, lifecycleState
- persistenceMode, accessScope, retentionDays
- overallDecision, warningCount, findingCount, rejectCandidateCount
- declaredMime, detectedMime, mimeMismatch
- durationMs, width, height, containerFormat, videoCodec, audioCodec
- policyProfile, policyMode, policyDecision
- uploadContinues, blocking

---

## Access Control

| Aspect | Rule |
|--------|------|
| Endpoint family | /dev/* only |
| Reader scope | DEV_ONLY |
| User-facing | NO |
| Tenant isolation | Required |
| Admin access | FUTURE |

---

## Query Behavior

| Query | Filter |
|-------|--------|
| By tenant | tenantId + createdAt DESC |
| By product | rawMediaProductId |
| Expired | expiresAt < NOW() |
| Lifecycle | lifecycleState |

---

## Pagination

| Parameter | Default | Max |
|-----------|---------|-----|
| page | 0 | — |
| size | 20 | 100 |

---

## Tests Required

| Test | Expected |
|------|----------|
| List endpoint returns safe DTO | ✅ |
| Detail endpoint returns safe DTO | ✅ |
| Tenant query works | ✅ |
| Product query works | ✅ |
| No forbidden fields | ✅ |
| 404 for unknown ID | ✅ |
| Pagination works | ✅ |
| Expired records excluded or marked | ✅ |
| No /app/* route | ✅ |
| Drift guard passes | ✅ |

---

## Status

- INGEST-PREFLIGHT-SAFE-REPORT-PERSISTENCE-READ-ENDPOINT-DESIGN.0: COMPLETE
- Decision: READ_ENDPOINT_DESIGN_READY_WITH_LIMITS
- Implementation: NOT_STARTED
