# Frontend App Render Result Contract

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-RENDER-RESULT-CONTRACT.0
**Decision:** FRONTEND_APP_RENDER_RESULT_CONTRACT_READY_WITH_LIMITS

---

## Target Routes

| Route | Surface |
|-------|---------|
| /app/renders | Render Results List |
| /app/renders/$productId | Render Result Detail |

---

## Render Results List Contract

**Purpose:** Show FINAL_RENDER Products for user identification and navigation.

| Display Field | Source | Required |
|---------------|--------|----------|
| productId | Product | YES |
| label | Product | YES |
| productStatus | Product | YES |
| renderStatus | RenderJob | OPTIONAL |
| createdAt | Product | YES |
| updatedAt | Product | OPTIONAL |
| artifactAvailability | Artifact | OPTIONAL |

**Forbidden Fields:**
storageReferenceId, bucket, objectKey, localPath, credentials, rawMetadata, signedUrl, originalFilename, fileHash

**States:** loading, empty, error, unavailable, partial

---

## Render Result Detail Contract

**Purpose:** Show one FINAL_RENDER Product with render status, artifacts, and access boundary.

| Section | Fields |
|---------|--------|
| Product Summary | productId, label, status, createdAt, updatedAt |
| Render Status | renderJobId, status, startedAt, completedAt |
| Artifact Metadata | artifactId, type, contentType, size, createdAt, availability |
| Access Boundary | on-demand, short-lived, not canonical metadata |

---

## Render Status Display

| Status | Label | Polling |
|--------|-------|---------|
| QUEUED | Waiting | 5s |
| EXECUTING | Rendering | 5s |
| COMPLETED | Completed | Stop |
| FAILED | Failed | Stop |
| CANCELED | Canceled | Stop |

---

## AccessDescriptor Boundary

| Rule | Value |
|------|-------|
| Access is on-demand | YES |
| Signed URL is short-lived | YES |
| Signed URL in canonical metadata | NO |
| Signed URL persisted | NO |
| Signed URL in query key | NO |

---

## Status

- FRONTEND-APP-RENDER-RESULT-CONTRACT.0: COMPLETE
- No real pages implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
