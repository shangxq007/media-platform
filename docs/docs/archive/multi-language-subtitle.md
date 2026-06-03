# Multi-Language Subtitle Support

> **Last updated**: 2026-05-11

## Architecture

Each language is a separate `SubtitleTrack`:

```json
{
  "subtitleTracks": [
    { "id": "sub_en", "language": "en", "label": "English", "burnIn": false },
    { "id": "sub_zh", "language": "zh", "label": "中文", "burnIn": false },
    { "id": "sub_ja", "language": "ja", "label": "日本語", "burnIn": true }
  ]
}
```

## Export Options

| Option | Description |
|--------|-------------|
| Single language burn-in | Render one language hardcoded |
| External subtitle package | Export all languages as separate files |
| Multi-language MKV | Package multiple tracks in MKV container |

## Language Selection

- Free tier: 1 language track
- Pro tier: Up to 3 language tracks
- Team/Enterprise: Unlimited

## Preview

The Timeline Editor preview panel supports:
- Language switching
- Font preview
- Timing adjustment per language

## Render Pipeline

1. Extract subtitle tracks from OTIO metadata
2. For burn-in: use FFmpeg drawtext filter with font
3. For external: write subtitle files alongside video
4. For multi-language: package in container (MKV)

## Font Fallback Chain

1. User-specified font
2. Fallback font list
3. System default font
4. Warning logged if glyphs missing
