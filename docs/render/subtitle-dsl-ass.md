# Subtitle/Caption DSL and ASS/libass Render Path

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** SUBTITLE-DSL-ASS.0
**Implementation mode:** DOCS_FIRST

---

## Background

BASIC-EFFECTS-DSL.0 defined textOverlay as MVP. Subtitles require separate treatment due to timed text, multi-language support, font fallback, and ASS style generation.

---

## Current Inventory

| Concept | File | Support |
|---------|------|---------|
| TimelineTextOverlay | render-module domain | EXISTS |
| Subtitle Render API | docs/api | EXISTS (MVP) |
| libass Provider Binding | docs/examples | EXISTS |
| FFmpegCommandFactory subtitlePath | render-module/ffmpeg | EXISTS |
| EffectMappingService text.subtitle_burn_in | render-module/infrastructure | EXISTS |
| Font management | frontend docs | EXISTS |

---

## Subtitle DSL Contract

### TimelineTextOverlay (Existing)

```json
{
  "id": "overlay_001",
  "text": "Hello World",
  "fontFamily": "DejaVu Sans",
  "fontSize": 24,
  "color": "#FFFFFF",
  "positionX": "50%",
  "positionY": "90%",
  "startTime": 0.0,
  "duration": 3.0,
  "backgroundColor": null
}
```

### Subtitle Render API (Existing)

```json
{
  "video": {"assetId": "asset_xxx"},
  "captions": [
    {
      "text": "Hello World",
      "startTime": 0.0,
      "endTime": 3.0,
      "words": [
        {"text": "Hello", "startTime": 0.0, "endTime": 1.5},
        {"text": "World", "startTime": 1.5, "endTime": 3.0}
      ]
    }
  ],
  "effects": [
    {
      "type": "CAPTION_STYLE",
      "params": {
        "fontSize": 24,
        "fontColor": "#FFFFFF",
        "position": "bottom-center"
      }
    }
  ]
}
```

---

## ASS Generation Strategy

### Approach

1. Parse TimelineTextOverlay or captions from DSL
2. Generate ASS header with style definitions
3. Generate ASS events with timing
4. Write to temporary .ass file
5. Pass to FFmpeg via `-vf ass=file.ass`

### ASS Structure

```
[Script Info]
Title: Platform Subtitle
ScriptType: v4.00+

[V4+ Styles]
Format: Name,Fontname,Fontsize,PrimaryColour,...
Style: Default,DejaVu Sans,24,&H00FFFFFF,...

[Events]
Format: Layer,Start,End,Style,Text
Dialogue: 0,0:00:00.00,0:00:03.00,Default,Hello World
```

---

## FFmpeg Render Path

### libass Path (Recommended)

```bash
ffmpeg -i input.mp4 -vf "ass=subtitle.ass" -c:a copy output.mp4
```

### drawtext Path (Fallback)

```bash
ffmpeg -i input.mp4 -vf "drawtext=text='Hello':fontsize=24:fontcolor=white:x=(w-tw)/2:y=h-th-10" output.mp4
```

### Current Implementation

FFmpegCommandFactory supports `subtitlePath` parameter for SRT/ASS burn-in.

---

## Font Management

### Font Manifest

- Platform maintains font manifest
- Font references use family names, not file paths
- Font fallback chain defined
- Font subsetting for CJK/emoji support

### Security

- No user-provided font file paths
- Font references validated against manifest
- Text escaping for ASS special characters
- Text length limits enforced

---

## Escaping Rules

| Character | ASS Escape | drawtext Escape |
|-----------|-----------|-----------------|
| `\n` | `\N` | `\n` |
| `{` | `{"{"}` | `\{` |
| `}` | `{"}`` | `\}` |
| `'` | `'` | `\'` |
| `:` | `:` | `\:` |

---

## Frontend/Backend Consistency

- Frontend uses same DSL schema
- Preview may use CSS approximations
- Final render uses libass for exact match
- Font family names must match manifest
- Timing precision: frame-level (30fps = 33ms)

---

## Timeline Git Implications

- Subtitle overlay IDs enable stable diff
- Text changes are semantic diffs
- Timing changes separate from content
- Style changes separate from text

---

## Deferred

| Feature | Reason |
|---------|--------|
| Karaoke styling | Complex ASS animation |
| SSA v4+ advanced | Rarely needed |
| Multi-track subtitles | Language switching |
| Subtitle import (SRT/VTT) | Separate task |
| Auto-transcription | AI service integration |

---

## Follow-up

| Task | Description |
|------|-------------|
| TEXT-OVERLAY-SECURITY.0 | Text escaping validation |
| SUBTITLE-IMPORT-SRT.0 | SRT/VTT import |
| FONT-MANIFEST-MANAGEMENT.0 | Font registry |
| AUTO-TRANSCRIPTION.0 | AI transcription |
