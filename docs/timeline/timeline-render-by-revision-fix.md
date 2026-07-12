# Timeline Render by Revision - Tenant Context Fix

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-RENDER-BY-REVISION-FIX.0

---

## Tenant Context Fix: COMPLETE

**Root cause:** When `app.security.enabled=false`, no filter sets TenantContext from JWT.

**Fix:** Modified `TenantContextFilter` to extract tenantId from JWT when TenantContext is empty.

**Files changed:**
- `TenantContextFilter.java` — Added JWT parsing
- `TenantContextFilterConfig.java` — Pass JwtProperties
- Deleted `DevTenantContextFilter.java` (replaced by simpler fix)

---

## Revision API Validation: COMPLETE

| Endpoint | Status |
|----------|--------|
| List revisions | ✅ Returns `[]` or revision list |
| Get revision | ✅ Returns revision metadata |
| Get head | ✅ Returns head revision |
| Render revision | ⚠️ BLOCKED (duration=0) |

---

## Render Issue: BLOCKED

**Error:** "Timeline duration must be positive: 0.0"

**Root cause:** `TimelineRevisionRenderService` uses `TimelineScriptParser` which doesn't parse internal timeline format (`composition.tracks`). Should use `InternalTimelineAdapter`.

**Evidence:**
- Internal format has `composition.tracks` with duration info (90 frames @ 30fps = 3s)
- `TimelineScriptParser.parseOtioRoot()` looks for root-level `tracks`
- `InternalTimelineAdapter.toSpec()` handles `composition.tracks` correctly

**Fix needed:** Change `TimelineRevisionRenderService` to use `InternalTimelineAdapter` instead of `TimelineScriptParser`.

---

## Status

- TIMELINE-RENDER-BY-REVISION-FIX.0: PARTIAL
- Tenant context: FIXED
- Revision API: WORKING
- Render by revision: BLOCKED (parser issue)

---

## Follow-up

- Fix `TimelineRevisionRenderService` to use `InternalTimelineAdapter`
- Re-run render-by-revision validation
