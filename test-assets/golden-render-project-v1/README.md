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
cd test-assets/golden-render-project-v1
./scripts/generate-assets.sh
```

Requires: `ffmpeg`

### 2. Validate Assets

```bash
./scripts/validate-assets.sh
```

### 3. Run Golden Render Tests

From the `platform/` directory:

```bash
cd platform
./gradlew :render-module:test --tests '*GoldenRenderE2ETest*'
```

This runs:
- `shouldRenderSingleClip1080p` — single video clip smoke test
- `shouldRenderMultiClip30sTimeline` — 5-clip video-only timeline (25s)
- `shouldRenderGoldenTimelineWithAudio` — 5-clip video + BGM audio (25s)

### 4. Extract Frames

```bash
./scripts/extract-frames.sh outputs/final_1080p.mp4
```

### 5. Validate Output

```bash
# Basic validation (audio optional)
./scripts/validate-output.sh outputs/final_1080p.mp4

# Require audio stream
./scripts/validate-output.sh outputs/final_1080p.mp4 --require-audio
```

## Capability Matrix

| Capability | Status | Notes |
|------------|--------|-------|
| Multi-clip video concat | ✅ Supported | P4-GOLDEN-3: 5 clips × 5s = 25s |
| Audio track (BGM) | ✅ Supported | P4-GOLDEN-4: single AAC audio track |
| Video + Audio output | ✅ Supported | H.264 + AAC in MP4 |
| Transitions | ❌ Unsupported | fade_in, fade_out, cross_dissolve |
| Overlay / Composite | ❌ Unsupported | logo, product card PIP |
| Text / Subtitle | ❌ Unsupported | SRT, WebVTT, burn-in |
| Audio Mixing | ❌ Unsupported | voiceover, SFX, ducking |
| Color Adjustment | ❌ Unsupported | brightness, contrast |
| Filter Effects | ❌ Unsupported | blur, sharpen |
| Crop | ❌ Unsupported | Runtime not implemented in v1 |
| Transform | ❌ Unsupported | Runtime not implemented in v1 |
| Keying | ❌ Unsupported | Runtime not implemented in v1 |
| Deform | ❌ Unsupported | Runtime not implemented in v1 |
| Advanced Tracking | ❌ Unsupported | Not implemented |

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

- `docs/media-rendering/golden-render-project.md` — Full documentation
- `docs/media-rendering/effect-taxonomy.md` — Effect taxonomy v1
- `docs/media-rendering/spatial-coordinate-system.md` — Spatial coordinate system v1

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
| **Spatial** | Keying | ❌ | Runtime not implemented |
| **Spatial** | Deform | ❌ | Runtime not implemented |
| **Spatial** | Tracking | ❌ | Not implemented |
| **Spatial** | Region blur | ❌ | Runtime not implemented |
| **Transition** | Wipe/Slide/Zoom | ❌ | Not implemented |
| **Export** | OTIO converter | ❌ | Not implemented |
| **Export** | Project Export | ❌ | Not implemented |

## Crop Validation

```bash
# Generate crop validation output
cd platform
./gradlew :render-module:test --tests '*GoldenRenderE2ETest.shouldRenderCropValidationOutput'

# View output
ls -la test-assets/golden-render-project-v1/outputs/crop_validation_1080p.mp4
```

## Next Steps

- P4-OTIO-1: OTIO roundtrip validation
- P4-EXPORT-1: Metadata-only project export
- P4-KEYING: Chroma key runtime
- P4-EXPORT-1: Metadata-only project export
