# Golden Render Project v1

## Overview

Golden Render Project v1 is a standard acceptance project for validating the core render capabilities of the media platform. It uses synthetic assets to provide a reproducible, network-free, copyright-safe test suite.

## Why Synthetic Assets

- **No copyright concerns** — All assets are generated programmatically
- **Reproducible** — Fixed ffmpeg parameters produce identical outputs
- **Network-free** — No external downloads required
- **Small footprint** — Short durations, efficient codecs
- **CI-friendly** — Fast to generate, fast to validate

## Relationship to Other Systems

### Effect Taxonomy v1

The Golden Project uses effects classified under Effect Taxonomy v1:

- **temporal**: fade_in, fade_out
- **transition**: cross_dissolve
- **filter**: blur, sharpen
- **color**: brightness, contrast
- **composite**: overlay, watermark
- **text**: subtitle_burn_in
- **audio**: volume

Operations like `video.dash_drm` (packaging) and `video.shotstack_template` (cloud_rendering) are explicitly marked as unsupported in the render plan.

### Spatial Coordinate System v1

The spatial plan uses `normalized_ppm` coordinates (integer, 0–1000000) with:
- Top-left origin
- Edge-based rounding
- Clamp-to-frame policy
- Four coordinate spaces: source, clip, canvas, safe-area

### Render Plan

The render plan describes:
- Output profiles (preview 480p, final 1080p, debug overlay)
- Track layout (video, overlay, text, audio, music, sfx)
- Effect operations with effectKey references
- Unsupported operations explicitly listed

### OTIO

The OTIO manifest provides a simplified OpenTimelineIO representation:
- Timeline with tracks and clips
- Source ranges in seconds
- Markers for transitions
- Metadata linking to render plan and asset bindings

## Acceptance Levels

### Level 1: Structural

- [ ] All asset files exist and are non-empty
- [ ] Manifests are valid JSON
- [ ] Spatial coordinates are integers in PPM range
- [ ] Validation points are sorted and within project duration
- [ ] Render plan references valid asset IDs

### Level 2: Timeline

- [ ] Output video duration is ~30s (±2s)
- [ ] Video stream exists with correct resolution
- [ ] Audio stream exists
- [ ] Frame rate is 30fps
- [ ] Multi-track layout is correct

### Level 3: Visual / Audio

- [ ] Frames at validation points show expected content
- [ ] Transitions are visible at expected timestamps
- [ ] Overlay elements are positioned correctly
- [ ] Audio levels are mixed correctly
- [ ] Subtitles are burned in at correct times

## Validation Scripts

| Script | Purpose |
|--------|---------|
| `generate-assets.sh` | Generate all synthetic assets |
| `validate-assets.sh` | Check asset existence and metadata |
| `extract-frames.sh` | Extract frames at validation timestamps |
| `validate-output.sh` | Validate rendered output structure |

## Implementation History

### P4-SPATIAL-1a: Parser / Filter 接入
- Created `SpatialPlan`, `SpatialOperation`, `SpatialSource`, `PpmRegion`, `PpmPosition` record types
- Created `SpatialPlanLoader` for JSON parsing
- Created `SpatialCoordinateConverter` for normalized_ppm → pixel conversion
- Integrated crop filter into `FFmpegCommandFactory.buildMultiTrackCommand()`
- Integrated overlay/composite operations from spatial plan
- Updated `GoldenRenderPlanAdapter` to load and pass spatial plan

### P4-SPATIAL-1b: Visual Validation & Rounding Fix
- Fixed rounding strategy to match spec: edge-based nearest rounding
  - `left = round(xPpm * width / 1_000_000)`
  - `top = round(yPpm * height / 1_000_000)`
  - `right = round((xPpm + widthPpm) * width / 1_000_000)`
  - `bottom = round((yPpm + heightPpm) * height / 1_000_000)`
  - `pixelWidth = max(1, right - left)`
  - `pixelHeight = max(1, bottom - top)`
- Added crop validation test with visual pixel diff verification
- Crop output: `outputs/crop_validation_1080p.mp4`
- Verified crop produces visible pixel difference (mean diff > 5)

### P4-EXPORT-1: Metadata-only Export API
- Added `POST /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports`
- Added `ProjectExportService` and `ProjectExportController`
- Added full DTO hierarchy: `ProjectExportResponse`, `ProjectExportManifestDto`, `ProjectExportProjectDto`, `ProjectExportAssetsDto`, `ProjectExportAssetDto`, `ProjectExportTimelineDto`, `ProjectExportRenderDto`, `ProjectExportEffectsDto`, `ProjectExportOutputsDto`, `ProjectExportAuditDto`, `ProjectExportSecurityDto`
- Export modes: `metadata_only` (✅), `linked_assets` (501), `bundled_assets` (501), `render_reproduction` (501)
- Security: storage refs null, download URLs null, signed URLs null, prompts redacted
- `ProjectExportServiceTest`: 7 tests covering request validation, manifest structure, security flags

## Future Work

- **P4-OTIO-1**: Full OTIO roundtrip validation
- **P4-EXPORT-2**: Signed URL generation for `linked_assets` mode
- **P4-EXPORT-3**: Project import with asset re-binding
- **P4-KEYING**: Chroma key runtime
- **LLM Integration**: Use Golden Project for multi-preview generation
