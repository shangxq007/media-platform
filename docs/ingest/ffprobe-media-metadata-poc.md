# FFprobe Media Metadata POC

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-FFPROBE-METADATA-POC.0

---

## Context

FFprobe design complete. FFprobe DTOs complete. This task adds controlled local POC only. Runtime upload behavior remains unchanged.

---

## Implementation

| Component | Status |
|-----------|--------|
| FFprobeMediaMetadataProvider | ✅ CREATED |
| Command execution | ✅ ProcessBuilder |
| JSON parsing | ✅ Allowlisted fields only |
| Video/audio mapping | ✅ MediaTechnicalMetadata |
| Failure handling | ✅ Missing binary, timeout, invalid media |

---

## Command Profile

```
ffprobe -v error -print_format json -show_format -show_streams <input>
```

---

## Parsed Fields

| Category | Fields |
|----------|--------|
| Format | format_name, duration, bit_rate, size |
| Video | codec_name, width, height, r_frame_rate, pix_fmt, color_space |
| Audio | codec_name, sample_rate, channels, bit_rate |
| Subtitle | codec_name, language |

---

## Tests

| Test | Result |
|------|--------|
| Non-existent file | ✅ PASSED |
| Invalid media | ✅ PASSED |
| Empty file | ✅ PASSED |
| Valid video (if FFprobe available) | ✅ PASSED |

---

## Status

- INGEST-FFPROBE-METADATA-POC.0: COMPLETE
- Runtime probing: LOCAL_POC_ONLY
- Upload behavior: UNCHANGED
