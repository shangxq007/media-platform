# Preview Frontend SPA Fallback Fix

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** PREVIEW-FRONTEND-SPA-FALLBACK-FIX.1

---

## Root Cause

GlobalExceptionHandler's `@ExceptionHandler(Exception.class)` catches `NoResourceFoundException` for SPA routes and returns HTTP 500 instead of forwarding to index.html.

---

## Fix

Added `SpaFallbackController` that forwards frontend document routes to index.html:

```java
@Controller
public class SpaFallbackController {
    @RequestMapping(value = {"/app/**", "/admin/**", "/dev/**"})
    public String forwardToFrontend() {
        return "forward:/index.html";
    }
}
```

**Excluded routes:** `/api/**`, `/actuator/**`, `/v3/api-docs/**`, static assets

---

## Status

- PREVIEW-FRONTEND-SPA-FALLBACK-FIX.1: COMPLETE
- Root cause: GlobalExceptionHandler catches NoResourceFoundException → 500
- Fix: SpaFallbackController forwards /app/**, /admin/**, /dev/** to index.html
