# Frontend App Upload Surface Design

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-APP-UPLOAD-SURFACE-DESIGN.0
**Decision:** FRONTEND_APP_UPLOAD_SURFACE_DESIGN_READY_WITH_LIMITS

---

## Canonical Route

**Route:** /app/uploads
**Navigation label:** Upload
**Alternative:** /app/media/upload (deferred)
**Reason:** Upload creates RAW_MEDIA Product. Render Results displays FINAL_RENDER Product.

---

## MVP Scope

| Feature | Status |
|---------|--------|
| Single-file upload | YES |
| Multi-file upload | DEFERRED |
| Upload queue | DEFERRED |
| Resumable upload | DEFERRED |
| Background persistence | FORBIDDEN |
| Render submission | EXCLUDED |

---

## Page Layout

```
App shell
└── Upload page
    ├── Page header
    ├── File selection area
    ├── Selected file summary
    ├── Upload action
    ├── Progress/state area
    ├── Error/retry area
    └── Success Product summary
```

---

## State Model

| State | Upload Button | File Selector | Description |
|-------|--------------|---------------|-------------|
| idle | Disabled | Enabled | No file selected |
| fileSelected | Disabled | Enabled | File selected, validate |
| validating | Disabled | Disabled | Client checks |
| ready | Enabled | Enabled | Eligible for upload |
| uploading | Disabled | Disabled | Mutation active |
| processing | Disabled | Disabled | Backend processing |
| success | N/A | Enabled | Product available |
| error | Retryable | Enabled | Safe error |
| canceled | N/A | Enabled | User canceled |
| retrying | Disabled | Disabled | Retry in progress |

---

## Mutation Integration

- Hook: useUploadRawMediaMutation
- Trigger: Explicit user action only
- No API call on page load
- No API call on file selection

---

## Status

- FRONTEND-APP-UPLOAD-SURFACE-DESIGN.0: COMPLETE
- No upload page implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
