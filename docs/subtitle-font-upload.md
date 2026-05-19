# Subtitle Upload, Custom Fonts, and Timing Management

> **Last updated**: 2026-05-11

## Supported Formats

| Format | Extension | Parser |
|--------|-----------|--------|
| SubRip | .srt | `parseSRT()` |
| WebVTT | .vtt | `parseVTT()` |
| ASS/SSA | .ass/.ssa | `parseASS()` |

## Font Upload

| Format | Extension | Notes |
|--------|-----------|-------|
| TrueType | .ttf | Preferred |
| OpenType | .otf | Supported |

## OTIO Metadata

Subtitle tracks are written to OTIO timeline:

```json
{
  "subtitleTracks": [{
    "id": "sub_...",
    "language": "en",
    "format": "srt",
    "cues": [
      { "id": "cue_1", "startTime": 1.0, "endTime": 3.5, "text": "Hello" }
    ],
    "fontId": "font_...",
    "fallbackFontIds": ["font_default"],
    "burnIn": true
  }]
}
```

## Subtitle Modes

| Mode | Description |
|------|-------------|
| None | No subtitles |
| Burn-in | Hardcoded into video |
| External | Separate subtitle file |
| Multi-external | Multi-language package |

## Font Fallback

When a font is missing:
1. Try fallback font list
2. Use system default
3. Log warning in render output

## Security

- Font files are validated by extension only
- No arbitrary script execution
- All rendering goes through JavaCV/OFX provider layer
