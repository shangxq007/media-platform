# Basic Timeline Effects DSL

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** BASIC-EFFECTS-DSL.0
**Implementation mode:** DOCS_ONLY

---

## Background

TIMELINE-RENDER-MVP.0 is COMPLETE. Existing `TimelineClipEffect` model supports effects with `effectKey` and `parameters`. This document defines the canonical effect schema, validation rules, and MVP effect set.

---

## Current Inventory

| Concept | File | Support | Placement |
|---------|------|---------|-----------|
| TimelineClipEffect | render-module domain | YES | clip |
| TimelineTextOverlay | render-module domain | YES | timeline |
| TimelineClip.effects | render-module domain | YES | clip |
| TimelineAudioSpec.volume | render-module domain | YES | clip/track |
| trim (assetInPoint/clipDuration) | TimelineClip | YES | clip |
| scale | — | NO | deferred |
| crop | — | NO | deferred |
| fade | — | NO | deferred |
| FFmpeg filtergraph compiler | — | NO | deferred |

---

## Effect Placement Model

| Level | Scope | MVP Support |
|-------|-------|-------------|
| Clip-level | One clip | YES |
| Track-level | All clips on track | DEFERRED |
| Timeline-level | Global output | DEFERRED |

### Evaluation Order

1. Media source trim (clip timing)
2. Spatial transform: crop / scale
3. Visual adjustment: color/brightness/contrast
4. Overlay: text/subtitle/watermark
5. Fade in/out
6. Audio: volume/gain
7. Output profile transform

---

## MVP Effect Set

### 1. trim

**Purpose:** Use a source in/out range from media.

**Placement:** clip

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| sourceInMs | int >= 0 | YES | Source start time |
| durationMs | int > 0 | YES | Duration to use |

**Status:** MVP (already supported via TimelineClip fields)

**FFmpeg mapping:** `-ss` / `-t` or `trim` filter

---

### 2. scale

**Purpose:** Resize video output.

**Placement:** clip or timeline

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| width | int > 0 | YES | Output width |
| height | int > 0 | YES | Output height |
| mode | enum | NO | fit / fill / stretch / preserve_aspect |

**Status:** MVP

**FFmpeg mapping:** `scale` filter

---

### 3. crop

**Purpose:** Crop video region.

**Placement:** clip

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| x | int >= 0 | YES | Crop x offset |
| y | int >= 0 | YES | Crop y offset |
| width | int > 0 | YES | Crop width |
| height | int > 0 | YES | Crop height |

**Status:** MVP

**FFmpeg mapping:** `crop` filter

---

### 4. fade

**Purpose:** Fade video in or out.

**Placement:** clip

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| direction | enum | YES | in / out |
| durationMs | int > 0 | YES | Fade duration |
| curve | enum | NO | linear (default) |

**Status:** MVP

**FFmpeg mapping:** `fade` filter

---

### 5. volume

**Purpose:** Adjust audio volume.

**Placement:** clip

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| gainDb | float | NO | Gain in dB |
| gain | float | NO | Multiplier (1.0 = normal) |
| mute | boolean | NO | Mute audio |

**Status:** MVP

**FFmpeg mapping:** `volume` filter

---

### 6. textOverlay

**Purpose:** Render text on video.

**Placement:** clip or timeline

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| text | string | YES | Text content (max 500 chars) |
| startMs | int >= 0 | YES | Start time |
| durationMs | int > 0 | YES | Duration |
| x | int/string | YES | Horizontal position |
| y | int/string | YES | Vertical position |
| fontFamily | string | NO | Font family name |
| fontSize | int > 0 | YES | Font size |
| color | string | YES | Text color (#RRGGBB) |
| backgroundColor | string | NO | Background color |

**Status:** MVP (already supported via TimelineTextOverlay)

**FFmpeg mapping:** `drawtext` filter or libass path

---

### 7. colorAdjust

**Purpose:** Adjust video color properties.

**Placement:** clip

**Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| brightness | float | NO | -1.0 to 1.0 |
| contrast | float | NO | 0.0 to 2.0 |
| saturation | float | NO | 0.0 to 3.0 |
| hue | float | NO | -3.14 to 3.14 |

**Status:** MVP

**FFmpeg mapping:** `eq` filter

---

## Canonical Effect Schema

```json
{
  "effectId": "eff_001",
  "effectKey": "fade",
  "enabled": true,
  "order": 10,
  "parameters": {
    "direction": "in",
    "durationMs": 500,
    "curve": "linear"
  }
}
```

### Clip with Effects

```json
{
  "id": "clip_001",
  "assetRef": {"storageUri": "localFsStorageProvider://preview-media/media_xxx/input.mp4"},
  "timelineStart": 0,
  "assetInPoint": 0,
  "clipDuration": 3,
  "effects": [
    {
      "effectId": "eff_scale_001",
      "effectKey": "scale",
      "parameters": {"width": 1280, "height": 720}
    },
    {
      "effectId": "eff_fade_001",
      "effectKey": "fade",
      "parameters": {"direction": "in", "durationMs": 500}
    }
  ]
}
```

---

## Validation Rules

### Structural

- `effectKey` required
- `parameters` required (may be empty)
- `effectId` recommended for diff/patch stability
- `enabled` defaults to true
- `order` controls evaluation sequence

### Type Validation

| Field | Rule |
|-------|------|
| durationMs | > 0 |
| sourceInMs | >= 0 |
| width/height | > 0 |
| gainDb | bounded |
| fontSize | > 0 |
| text length | max 500 chars |
| color | valid #RRGGBB |

### Security

- No raw FFmpeg filter strings
- No shell command injection
- No local file paths from user
- Text fields length-limited
- Font references must use allowed family names

---

## Deferred Effects

| Effect | Reason |
|--------|--------|
| transition | Multi-clip compositing |
| blur | Complex filter chain |
| chroma key | Advanced compositing |
| speed ramp | Time manipulation |
| motion/keyframes | Animation system |
| mask | Advanced compositing |
| LUT | Color science |
| subtitle ASS styling | Separate DSL task |
| multi-layer overlay | Complex compositing |

---

## Frontend / Backend Consistency

- Frontend must use same effect schema as backend
- Frontend must not invent CSS-only effects
- Effect IDs must remain stable across edits
- Unsupported backend effects marked in UI
- Font/text overlay aligns with font manifest

---

## Timeline Git Implications

- `effectId` enables stable diff
- `effectKey` and `parameters` allow semantic diff
- Order changes separate from param changes
- `enabled` toggle separate from deletion
- Timeline Git diffs canonical DSL, not FFmpeg filtergraph

---

## Follow-up Tasks

| Task | Description |
|------|-------------|
| FFMPEG-FILTERGRAPH-COMPILER.0 | Implement effect-to-FFmpeg mapping |
| SUBTITLE-DSL-ASS.0 | ASS subtitle styling |
| TEXT-OVERLAY-SECURITY.0 | Text overlay security rules |
| EFFECT-CAPABILITY-MATRIX.0 | Provider effect support matrix |
| TIMELINE-GIT-PLANNING.0 | Timeline version control |
