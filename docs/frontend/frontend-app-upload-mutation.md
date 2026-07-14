# Frontend App Upload Mutation

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-UPLOAD-MUTATION.0
**Decision:** FRONTEND_APP_UPLOAD_MUTATION_READY_WITH_LIMITS

---

## Implemented Hook

| Field | Value |
|-------|-------|
| Hook name | useUploadRawMediaMutation |
| Hook path | src/query/app/upload/ |
| Upload client | Placeholder (pending backend contract) |
| Input | scope, file, displayName?, contentType? |
| Result | UploadRawMediaResult (productId, type, status) |
| Error | UploadMutationError (kind, message, retryable) |

---

## Mutation Policy

| Policy | Value |
|--------|-------|
| Creates product | RAW_MEDIA |
| Optimistic canonical Product | ❌ NOT ALLOWED |
| Storage reference exposed | ❌ NO |
| Signed URL exposed | ❌ NO |
| Render submission | ❌ NO |
| Safe preflight exposure | NONE_IN_APP |

---

## Cache Invalidation

| Event | Action |
|-------|--------|
| Upload success | Invalidate Product list |
| Upload success | Do NOT invalidate Render Results |

---

## Status

- FRONTEND-APP-UPLOAD-MUTATION.0: COMPLETE
- No upload page implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
