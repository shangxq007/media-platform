# Timeline Render by Revision - jOOQ Fix

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** TIMELINE-RENDER-BY-REVISION-JOOQ-FIX.0

---

## jOOQ Fix: COMPLETE

**Root cause:** `ProductRepository` had a `protected` no-arg constructor that set `dsl = null`. CGLIB proxy used this instead of Spring-managed constructor.

**Fix:** Added `@Autowired` to `ProductRepository` and `ProductDependencyRepository` DSLContext constructors.

**Files changed:**
- `ProductRepository.java` — Added @Autowired
- `ProductDependencyRepository.java` — Added @Autowired

---

## New Blocker: No RAW_MEDIA Product for Asset

**Error:** "Input product resolution failed: No READY RAW_MEDIA Product found for asset: c1"

**Analysis:**
- Inline render uses media URI directly (no Product required)
- Revision render uses `TimelineInputProductResolver` which requires RAW_MEDIA Products
- The revision snapshot references asset "c1" but no Product exists for it
- This is an architecture difference between inline and revision render paths

**Impact:** Render-by-revision still blocked by product resolution issue, not jOOQ issue.

---

## Status

- TIMELINE-RENDER-BY-REVISION-JOOQ-FIX.0: PARTIAL
- jOOQ DSLContext: FIXED
- Product resolution: NEW BLOCKER (architecture gap)

---

## Follow-up

- Align revision render with inline render's direct media URI approach
- Or create RAW_MEDIA Products during revision creation
- Or modify TimelineInputProductResolver to handle missing Products
