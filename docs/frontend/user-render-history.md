# User Render History

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** FRONTEND-USER-RENDER-HISTORY.0

---

## Route

`/app/renders`

**Surface:** User App (not Dev, not Admin)

---

## User Status Mapping

| Internal Status | User Label | User Description |
|-----------------|------------|------------------|
| QUEUED | Waiting | Your render is waiting to start. |
| EXECUTING | Rendering | Your video is being rendered. |
| COMPLETED | Completed | Your render is ready. |
| FAILED | Failed | The render could not be completed. |
| FAILED (retryable) | Failed, can retry | The render failed, but you can try again. |

**Hidden from users:**
- workerId
- eventType
- lifecycle events
- stale EXECUTING
- retry/recovery internals
- warning codes

---

## Features

| Feature | Status |
|---------|--------|
| Summary cards | ✅ |
| User-friendly status labels | ✅ |
| Status filters | ✅ |
| Empty state | ✅ |
| Tailwind styling | ✅ |

---

## Status

- FRONTEND-USER-RENDER-HISTORY.0: COMPLETE
- User route: IMPLEMENTED
- Status mapping: IMPLEMENTED
