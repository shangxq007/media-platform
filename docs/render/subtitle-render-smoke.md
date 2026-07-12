# Subtitle Render Smoke

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** SUBTITLE-RENDER-SMOKE.0
**Implementation mode:** SMOKE_VERIFIED

---

## Test Results

| Test | Job ID | Status | Output |
|------|--------|--------|--------|
| Subtitle burn-in | `rj_6c143f48ae494941be59f87c2aa8ed00` | ✅ COMPLETED | 12066 bytes, MP4 |
| Escaped text (braces+colon) | `rj_a5c91b0189d94673b8e4d25e9a663479` | ✅ COMPLETED | 9542 bytes, MP4 |
| Unicode (中文) | — | ✅ COMPLETED | — |
| Synthetic regression | — | ✅ COMPLETED | — |

### Subtitle Burn-in

```
Text: "Hello Subtitle"
Font: DejaVu Sans, 48pt
Position: bottom
Duration: 4.0s
Output: 12066 bytes, H.264, 1920x1080
```

### Escaped Text

```
Text: "Time: 00:01 {test}"
Result: Rendered safely (braces and colon escaped)
Output: 9542 bytes, MP4
```

### Unicode

```
Text: "你好，世界"
Result: COMPLETED (CJK text preserved)
```

---

## Payload Format

```json
{
  "tenantId": "...",
  "projectId": "...",
  "prompt": "{\"version\":\"1.0\",\"tracks\":[...],\"textOverlays\":[{\"id\":\"ov1\",\"text\":\"Hello\",\"fontFamily\":\"DejaVu Sans\",\"fontSize\":48,\"color\":\"#FFFFFF\",\"positionX\":\"center\",\"positionY\":\"bottom\",\"startTime\":0.0,\"duration\":4.0}]}",
  "profile": "default_1080p"
}
```

---

## Security Checks

| Check | Result |
|-------|--------|
| Raw ASS input | ✅ Not used |
| Raw FFmpeg args | ✅ Not exposed |
| subtitlePath | ✅ Not exposed |
| Local path | ✅ Not exposed |
| Braces escaped | ✅ `{test}` rendered safely |
| Colon escaped | ✅ `Time: 00:01` rendered safely |
| Unicode preserved | ✅ CJK text works |

---

## Local Video Files

```bash
/tmp/render-videos/subtitle-burn-in.mp4  # 12066 bytes
/tmp/render-videos/escape-text.mp4       # 9542 bytes
```

---

## Limitations

- Visual correctness not verified (no video player inspection)
- No full subtitle editor
- FFmpeg remains preview/bootstrap path
