# Frontend App Artifact Access Action Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-ARTIFACT-ACCESS-ACTION-DESIGN.0
**Decision:** FRONTEND_APP_ARTIFACT_ACCESS_ACTION_DESIGN_READY_WITH_LIMITS

---

## Action Scope

**Target:** /app/renders/$productId artifact items

**Allowed:** Preview, Open, Download (on-demand)

**Deferred:** Copy Link

**Forbidden:** Signed URL persistence, storage internals exposure

---

## Action Types

| Action | Behavior | Status |
|--------|----------|--------|
| Preview | Browser preview for media | READY |
| Open | Open in new tab/window | READY |
| Download | Trigger browser download | READY |
| Copy Link | Copy signed URL | DEFERRED |

---

## AccessDescriptor Mapping

| Field | Required | Notes |
|-------|----------|-------|
| accessType | YES | preview/open/download |
| accessUrl | YES | Short-lived signed URL |
| expiresAt | YES | Expiration timestamp |
| contentType | OPTIONAL | MIME type |
| disposition | OPTIONAL | inline/attachment |

**Forbidden:** storageReferenceId, bucket, objectKey, localPath, credentials

---

## Signed URL Safety Rules

| Rule | Value |
|------|-------|
| Request on page load | NO |
| Display as raw text | NO |
| Store in Zustand | NO |
| Store in localStorage | NO |
| Include in query key | NO |
| Include in route params | NO |
| Log by frontend | NO |

---

## Cache Policy

| Setting | Value |
|---------|-------|
| Enabled by default | NO |
| Triggered by | User action |
| Stale time | Short |
| GC time | Short |
| Auto-refresh | NO |
| Polling | NO |

---

## UI States

| State | Description |
|-------|-------------|
| idle | No request made |
| requesting | Loading access |
| available | Access ready |
| opening | Opening in browser |
| downloading | Downloading |
| expired | Access expired, can refresh |
| unavailable | Access not available |
| forbidden | Permission denied |
| not-found | Artifact not found |
| error | Safe error display |

---

## Status

- FRONTEND-APP-ARTIFACT-ACCESS-ACTION-DESIGN.0: COMPLETE
- No access action implemented
- No signed URL requested
- Safe preflight persistence: DEV_ONLY, PAUSED
