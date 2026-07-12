# Tika + FFprobe Merge Report-only Preflight

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-TIKA-FFPROBE-MERGE-REPORT-ONLY.0

---

## Context

Tika report-only preflight complete. FFprobe local POC complete. This task merges Tika + FFprobe internally. Runtime upload behavior remains unchanged.

---

## Implementation

| Component | Status |
|-----------|--------|
| IngestMetadataMerger | ✅ CREATED |
| Tika detection | ✅ Integrated |
| FFprobe media metadata | ✅ Integrated |
| Warning merge | ✅ IMPLEMENTED |
| Provenance merge | ✅ IMPLEMENTED |
| Report-only decision | ✅ ACCEPT / ACCEPT_WITH_WARNINGS |

---

## Merge Rules

| Scenario | Decision |
|----------|----------|
| No warnings | ACCEPT |
| Warnings exist | ACCEPT_WITH_WARNINGS |
| Never | REJECT |

---

## Tests

| Test | Result |
|------|--------|
| Tika only merge | ✅ PASSED |
| No rejection enforced | ✅ PASSED |
| FFprobe for video | ✅ PASSED (if FFmpeg available) |

---

## Status

- INGEST-TIKA-FFPROBE-MERGE-REPORT-ONLY.0: COMPLETE
- Upload behavior: UNCHANGED
- Enforcement: NOT_IMPLEMENTED
