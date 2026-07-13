# Frontend App Product Surface Plan

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-PRODUCT-SURFACE-PLAN.0
**Decision:** FRONTEND_APP_PRODUCT_SURFACE_PLAN_READY_WITH_LIMITS

---

## Recommended First App Surface

**App Render Results**

Reason: FINAL_RENDER Product consumption is the primary user-facing output.

---

## Route Catalog

| Route | Surface | Status | Contract | Query Hook | Mutation |
|-------|---------|--------|----------|------------|----------|
| /app | App Shell | READY | — | — | NO |
| /app/renders | Render Results List | READY_WITH_LIMITS | Product, RenderJob | useProducts, useRenderJobStatus | NO |
| /app/renders/$productId | Render Result Detail | READY_WITH_LIMITS | Product, Artifact, AccessDescriptor | useProductDetail, useArtifacts, useArtifactAccess | NO |
| /app/products | Product Library | DEFERRED | Product | useProducts | NO |
| /app/products/$productId | Product Detail | DEFERRED | Product | useProductDetail | NO |

---

## Surface-to-Contract Matrix

| Surface | Required | Optional | Hidden | Forbidden |
|---------|----------|----------|--------|-----------|
| Render Results List | productId, type, status, createdAt | duration, thumbnail | — | storageReferenceId, bucket, objectKey, localPath, credentials, rawMetadata |
| Render Result Detail | productId, status, artifacts, accessDescriptor | renderJobStatus, duration | — | storageReferenceId, bucket, objectKey, localPath, credentials, signedUrl |

---

## Artifact Access Boundary

| Rule | Value |
|------|-------|
| Access is on-demand | YES |
| Signed URL is short-lived | YES |
| Signed URL in canonical metadata | NO |
| Signed URL persisted | NO |
| Access cached separately | YES |

---

## Render Polling Display Boundary

| Status | Behavior |
|--------|----------|
| QUEUED | Poll 5s |
| EXECUTING | Poll 5s |
| COMPLETED | Stop |
| FAILED | Stop |
| CANCELED | Stop |

---

## Implementation Sequence

1. FRONTEND-APP-READONLY-SHELL.0
2. FRONTEND-APP-RENDER-RESULT-CONTRACT.0
3. FRONTEND-APP-RENDER-RESULT-LIST.0
4. FRONTEND-APP-RENDER-RESULT-DETAIL.0
5. FRONTEND-APP-ARTIFACT-ACCESS-ACTION.0

---

## Status

- FRONTEND-APP-PRODUCT-SURFACE-PLAN.0: COMPLETE
- No production /app pages implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
