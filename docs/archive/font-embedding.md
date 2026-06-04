# Font Embedding and Burn-in Subtitle Rendering

> **Last updated**: 2026-05-11

## Architecture

### Font Registry Service
- `FontRegistryService` manages font registration, subset generation, and glyph coverage checking
- Fonts stored in `${app.fonts.dir}/` (default: `/tmp/platform/fonts/`)
- Each font has: fontId, family, format, glyphCoverage, fallbackFontIds

### Font Subset Generation
- For each subtitle track, generate a subset font containing only required glyphs
- Reduces output file size and ensures consistent rendering
- Subset stored as `{fontId}_subset_{hash}.{format}`

### Subtitle Burn-in Rendering
- `SubtitleRenderService` builds FFmpeg drawtext filter with font embedding
- Supports multiple subtitle tracks with different languages
- Font fallback chain: primary → fallbackFontIds → system default → warning

### Multi-language Support
- Each language = separate `SubtitleTrack` in OTIO metadata
- Burn-in: all tracks rendered into video
- External: separate subtitle files per language
- Multi-language package: MKV container with multiple tracks

## OTIO Metadata

```json
{
  "subtitleTracks": [{
    "id": "sub_en",
    "language": "en",
    "format": "srt",
    "cues": [{"id": "cue_1", "startTime": 1.0, "endTime": 3.0, "text": "Hello"}],
    "fontId": "font_arial",
    "fontFilePath": "/tmp/platform/fonts/font_arial.ttf",
    "glyphSubsetFile": "/tmp/platform/fonts/font_arial_subset_abc123.ttf",
    "fallbackFontIds": ["font_noto_sans"],
    "burnIn": true
  }]
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `SUBTITLE-400-001` | Subtitle parsing failed |
| `SUBTITLE-404-001` | Subtitle font not found |
| `SUBTITLE-422-001` | Font glyph coverage insufficient |
| `RENDER-500-001` | Render execution failed |

## Security

- Font files validated by extension only (.ttf/.otf)
- No arbitrary script execution
- All rendering through JavaCV/OFX provider layer
- Font paths restricted to configured directory
