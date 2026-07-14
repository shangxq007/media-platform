# Frontend App Upload Mutation Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-UPLOAD-MUTATION-DESIGN.0
**Decision:** FRONTEND_APP_UPLOAD_MUTATION_DESIGN_READY_WITH_LIMITS

---

## Scope

RAW_MEDIA upload only. No render submission. No Artifact creation by frontend.

---

## Target Surface

**Recommended:** /app/uploads (dedicated upload entry)

**Deferred:** /app/renders upload CTA, /app/media, /app/library

---

## Upload Request Contract

| Field | Allowed | Notes |
|-------|---------|-------|
| file payload | YES | File or upload reference |
| tenantId | YES | Scope |
| projectId | YES | Scope |
| contentType | YES | From File object |
| displayName | OPTIONAL | If contract-approved |
| storageReferenceId | ❌ NO | Internal |
| bucket/objectKey | ❌ NO | Internal |
| localPath | ❌ NO | Internal |
| credentials | ❌ NO | Internal |
| signedUrl | ❌ NO | Not canonical |

---

## Upload Response Contract

| Field | Allowed | Notes |
|-------|---------|-------|
| productId | YES | RAW_MEDIA Product ID |
| productType | YES | RAW_MEDIA |
| productStatus | YES | Active |
| createdAt | YES | Timestamp |
| storageReferenceId | ❌ NO | Internal |
| bucket/objectKey | ❌ NO | Internal |
| localPath | ❌ NO | Internal |
| credentials | ❌ NO | Internal |
| signedUrl | ❌ NO | Not canonical |

---

## Mutation State Machine

| State | Description |
|-------|-------------|
| idle | No file selected |
| fileSelected | File selected locally |
| validating | Client-side checks |
| ready | Eligible for upload |
| uploading | Upload in progress |
| processing | Backend processing |
| success | RAW_MEDIA Product available |
| error | Safe error display |
| canceled | User canceled |
| retrying | Retry in progress |

---

## Cache Invalidation

| Event | Action |
|-------|--------|
| Upload success | Invalidate Product list |
| Upload success | Optionally seed Product detail |
| Upload success | Do NOT invalidate Render Result list |

---

## Optimistic UI Policy

| Policy | Value |
|--------|-------|
| Optimistic canonical Product | ❌ NOT ALLOWED |
| Temporary local upload row | ALLOWED (future) |
| Persisted as Product | ❌ NO |

---

## Status

- FRONTEND-APP-UPLOAD-MUTATION-DESIGN.0: COMPLETE
- No upload UI implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
