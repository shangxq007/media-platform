# Timeline Merge DB Fix

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** TIMELINE-MERGE-DB-FIX.0

---

## Root Cause

**Table:** `timeline_revision`
**Column:** `content_hash` (varchar(64))
**Failing value:** `merge:trev_xxx:trev_yyy:-1234567890`
**Length:** ~93 chars
**Semantic:** Merge hash combining source/target revision IDs

## Fix

Changed `computeMergeHash` to use SHA-256:
- Input: `merge:sourceId:targetId:hashCode`
- Output: 64 hex chars (fits in varchar(64))

---

## Merge Status

- Merge classification: MERGE_EXPERIMENTAL
- Merge included in MVP: NO
- DB blocker: FIXED

---

## Status

- TIMELINE-MERGE-DB-FIX.0: COMPLETE
- Merge endpoint: NO LONGER FAILS
- Merge classification: MERGE_EXPERIMENTAL
