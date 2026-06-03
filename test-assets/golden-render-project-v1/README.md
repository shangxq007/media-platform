# Golden Render Project v1

Standard acceptance project for validating core render capabilities of the media platform.

## Purpose

- Validate timeline, transitions, compositing, audio mixing, subtitle burn-in
- Provide reproducible synthetic assets for local and CI testing
- Serve as reference for Project Export, OTIO, Spatial Plan, and LLM multi-preview
- Establish a baseline for regression testing

## Directory Structure

```
golden-render-project-v1/
  README.md
  assets/            # Synthetic media files
    video/           # Test video clips
    image/           # Test images (PNG)
    audio/           # Test audio files
    subtitle/        # Subtitle files (SRT, WebVTT)
  manifests/         # Project and plan definitions
    golden-project.json
    golden-render-plan.json
    golden-spatial-plan.json
    golden-otio.json
  expected/          # Expected outputs
    frames/          # Expected frame images
    probes/          # Expected ffprobe outputs
    reports/         # Validation reports
  outputs/           # Actual render outputs
  scripts/           # Generation and validation scripts
```

## Quick Start

### 1. Generate Assets

```bash
cd platform/test-assets/golden-render-project-v1
./scripts/generate-assets.sh
```

Requires: `ffmpeg`. Generates all synthetic video/audio/image assets locally.

### 2. Validate Assets

```bash
./scripts/validate-assets.sh
```

### 3. Run Golden Render Tests

```bash
cd platform
./gradlew :render-module:test --tests '*GoldenRenderE2ETest*'
```

This runs:
- `shouldRenderSingleClip1080p` — single video clip smoke test
- `shouldRenderMultiClip30sTimeline` — 5-clip video-only timeline (25s)
- `shouldRenderGoldenTimelineWithAudio` — 5-clip video + BGM audio (25s)
- `shouldRenderGoldenTimelineWithAudioSubtitleAndWatermark` — full pipeline (25s)
- `shouldRenderGoldenTimelineWithFadeInOut` — temporal effects (25s)
- `shouldRenderGoldenTimelineWithCrossDissolve` — transition effects (13s)
- `shouldRenderCropValidationOutput` — spatial crop verification

### 4. Extract Frames

```bash
./scripts/extract-frames.sh outputs/final_1080p.mp4 outputs/frames
```

### 5. Validate Output

```bash
./scripts/validate-output.sh outputs/final_1080p.mp4
./scripts/validate-output.sh outputs/final_1080p.mp4 --require-audio
```

## Capability Matrix

| Category | Capability | Status | Notes |
|----------|------------|--------|-------|
| **Timeline** | Multi-clip concat | ✅ | P4-GOLDEN-3 |
| **Timeline** | Cross-dissolve transition | ✅ | P4-TRANSITION-1, xfade filter |
| **Temporal** | fade_in / fade_out | ✅ | P4-TEMPORAL-1, video + audio |
| **Audio** | BGM track | ✅ | P4-GOLDEN-4 |
| **Audio** | Volume control | ✅ | P4-GOLDEN-4 |
| **Text** | Subtitle burn-in | ✅ | P4-GOLDEN-5, subtitles_en.srt |
| **Composite** | Watermark overlay | ✅ | P4-GOLDEN-5, logo top-right |
| **Spatial** | Crop | ✅ | P4-SPATIAL-1, ffmpeg crop filter |
| **Spatial** | Placement / PIP | ✅ | P4-SPATIAL-1, scale + overlay |
| **Spatial** | Safe area guide | ✅ | P4-SPATIAL-1, canvas coordinates |
| **Export** | metadata_only | ✅ | P4-EXPORT-1 |
| **Export** | linked_assets | ✅ | P4-EXPORT-2, signed URLs |
| **Import** | Preview | ✅ | P4-EXPORT-3a |
| **Spatial** | Keying | ❌ | Runtime not implemented |
| **Spatial** | Deform | ❌ | Runtime not implemented |
| **Spatial** | Tracking | ❌ | Not implemented |
| **Transition** | Wipe/Slide/Zoom | ❌ | Not implemented |
| **Export** | OTIO converter | ❌ | Not implemented |
| **Import** | Full import | ❌ | Not implemented |

## Coordinate System

- Unit: `normalized_ppm` (parts per million, 0–1000000)
- Origin: top-left
- Spaces: source, clip, canvas, safe-area
- Rounding: edge-based, nearest, min 1px, clamp to frame
- All coordinates in manifests use integer PPM values (no floats)

## Validation Points

Six timestamps for frame-level validation:

| Time (ms) | Timecode | Expected Content |
|-----------|----------|------------------|
| 2000 | 00:00:02 | Fade-in visible, logo overlay |
| 7000 | 00:00:07 | Main video, text visible |
| 12000 | 00:00:12 | Cross-dissolve region |
| 17000 | 00:00:17 | Grid motion, product card PIP |
| 22000 | 00:00:22 | Moving box, audio mix |
| 28000 | 00:00:28 | Near end, fade-out region |

## Assets

### Video (6 files)
- `color_bars_1080p.mp4` — 1920x1080, 30fps, 10s, SMPTE bars
- `grid_motion_1080p.mp4` — 1920x1080, 30fps, 10s, grid + moving box
- `moving_box_1080p.mp4` — 1920x1080, 30fps, 10s, motion reference
- `portrait_test_1080x1920.mp4` — 1080x1920, 30fps, 10s, vertical
- `square_test_1080x1080.mp4` — 1080x1080, 30fps, 8s, square
- `green_screen_test.mp4` — 1920x1080, 30fps, 6s, chroma key test

### Image (4 files)
- `logo_transparent.png` — 512x512, watermark
- `product_card.png` — 1000x1000, PIP overlay
- `lower_third_bg.png` — 1920x300, lower third
- `mask_shape.png` — 512x512, mask reference

### Audio (4 files)
- `music_bgm.wav` — 30s, stereo, background music
- `voiceover.wav` — 15s, mono, voiceover
- `sfx_click.wav` — 0.5s, mono, click
- `sfx_whoosh.wav` — 1.5s, mono, whoosh

### Subtitle (3 files)
- `subtitles_zh.srt` — Chinese SRT
- `subtitles_en.srt` — English SRT
- `subtitles_webvtt.vtt` — WebVTT

## Related Documents

- `platform/docs/media-rendering/golden-render-project.md` — Full documentation
- `platform/docs/media-rendering/effect-taxonomy.md` — Effect taxonomy v1
- `platform/docs/media-rendering/spatial-coordinate-system.md` — Spatial coordinate system v1
- `platform/docs/media-rendering/project-export.md` — Project export/import v1

## Asset Generation Strategy

| File Type | Strategy | Reason |
|-----------|----------|--------|
| manifests/*.json | ✅ Committed | Small (< 20KB), defines project structure |
| scripts/*.sh | ✅ Committed | Small (< 20KB), reproducible generation |
| subtitles/* | ✅ Committed | Small (< 2KB), test data |
| images/* | ✅ Committed | Small (< 15KB), test images |
| videos/* | ❌ Git ignored | Large (20MB), generated by `generate-assets.sh` |
| audio/* | ❌ Git ignored | Large (7MB), generated by `generate-assets.sh` |
| outputs/* | ❌ Git ignored | Large (35MB), render output, reproducible |
| reports/* | ❌ Git ignored | Generated by validation scripts |

Total committed test-assets: ~50KB. Total generated: ~62MB.

## Next Steps

- P4-OTIO-1: OTIO roundtrip validation
- P4-EXPORT-3b: Full project import (create project, re-bind assets)
- P4-KEYING: Chroma key runtime
- P4-KEYING: Chroma key runtime
- P4-EXPORT-1: Metadata-only project export
