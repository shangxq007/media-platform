# Timeline Render by Revision - Adapter Fix

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-RENDER-BY-REVISION-ADAPTER-FIX.0

---

## Adapter Fix: APPLIED

**Root cause:** `TimelineRevisionRenderService` used `TimelineScriptParser` directly, but internal format needs `InternalTimelineAdapter`.

**Fix:** Added `InternalTimelineAdapter` to service, tries internal adapter first, falls back to script parser.

**File changed:**
- `TimelineRevisionRenderService.java` — Added InternalTimelineAdapter

---

## New Blocker: DSLContext NullPointerException

**Error:** `Cannot invoke "org.jooq.DSLContext.select(...)" because "this.dsl" is null`

**Location:** `PersistenceExceptionTranslationInterceptor.invoke()`

**Analysis:** This is a pre-existing jOOQ bean initialization issue that was masked because the render endpoint was failing earlier with "Timeline duration must be positive: 0.0" before reaching the code that uses the repository.

**Impact:** Render-by-revision still blocked by infrastructure issue, not adapter issue.

---

## Status

- TIMELINE-RENDER-BY-REVISION-ADAPTER-FIX.0: PARTIAL
- Adapter fix: APPLIED
- Duration parsing: FIXED (adapter now used)
- DSLContext issue: NEW BLOCKER (pre-existing infrastructure)

---

## Follow-up

- Investigate jOOQ DSLContext initialization in render path
- May need separate infrastructure fix task
