# Tika Metadata POC

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-TIKA-METADATA-POC.0

---

## Context

Tika evaluation: GO_WITH_LIMITS. Allowed scope: detector-only / light metadata. Tika must not replace FFprobe.

---

## Implementation

| Component | Status |
|-----------|--------|
| Tika dependency | ✅ `tika-core:2.9.2` |
| TikaExperimentalProperties | ✅ CREATED |
| TikaDetectionResult | ✅ CREATED |
| TikaDetectorProvider | ✅ CREATED |
| Tests | ✅ PASSED |

---

## Configuration

```yaml
ingest:
  experimental:
    tika:
      enabled: false  # disabled by default
      mode: detector-only
      extract-text: false
      ocr-enabled: false
      max-detect-bytes: 8192
```

---

## Detection Tests

| Test | Result |
|------|--------|
| PNG detection | ✅ PASSED |
| Text detection | ✅ PASSED |
| Content type mismatch | ✅ PASSED |
| Empty input | ✅ PASSED |
| Null input | ✅ PASSED |

---

## Security Rules

| Rule | Status |
|------|--------|
| Disabled by default | ✅ |
| No full text extraction | ✅ |
| No OCR | ✅ |
| No network access | ✅ |
| Byte limit | ✅ |
| No raw text persistence | ✅ |

---

## Status

- INGEST-TIKA-METADATA-POC.0: COMPLETE
- Tika status: EXPERIMENTAL_DETECTOR_POC
- Production path: DEFERRED
