# Subtitle Burn-In Service — Migration Guide

## Overview

The render-module previously had two separate subtitle burn-in implementations:

| Implementation | Mode | Location |
|---|---|---|
| `SubtitleRenderService.buildSubtitleFilter()` | FFmpeg drawtext filter strings | `SubtitleRenderService.java` |
| `AdvancedEffectsPipeline.applySubtitleBurnIn()` | Java2D frame-by-frame rendering | `AdvancedEffectsPipeline.java` |

These have been unified into `SubtitleBurnInService`, which supports both modes through a single entry point.

## New Component

### `SubtitleBurnInService`

Unified service in `infrastructure/SubtitleBurnInService.java` with two burn-in modes:

**FFmpeg filter mode** — builds drawtext filter strings for FFmpeg filtergraph:
```java
String filter = burnInService.buildSubtitleFilter(subtitleTracks);
String filter = burnInService.buildSubtitleFilter(subtitleTracks, preset);
```

**Java2D frame mode** — renders subtitles onto `BufferedImage` frames:
```java
BufferedImage result = burnInService.burnInFrame(image, frame, total, cues);
```

Additional methods:
- `checkSubtitleCompatibility(tracks)` — validates font availability and glyph coverage
- `resolveFontFile(path)` — resolves font file paths with existence checks

## Migration

### For callers of `SubtitleRenderService`

`SubtitleRenderService` now delegates to `SubtitleBurnInService`. The public API is unchanged:
```java
// Still works as before
String filter = subtitleRenderService.buildSubtitleFilter(tracks);
List<String> warnings = subtitleRenderService.checkSubtitleCompatibility(tracks);
String fontPath = subtitleRenderService.resolveFontFile(path);
```

### For `AdvancedEffectsPipeline`

The `applySubtitleBurnIn()` private method has been removed. The overlay dispatcher now delegates to `SubtitleBurnInService.burnInFrame()`:

**Before:**
```java
return applySubtitleBurnIn(image, frame, total, cues);
```

**After:**
```java
return subtitleBurnInService.burnInFrame(image, frame, total, cues);
```

`AdvancedEffectsPipeline` now depends on `SubtitleBurnInService` instead of `SubtitleRenderService`.

### Error Codes Added

| Code | Description |
|---|---|
| `SUBTITLE-BURN-500-001` | Subtitle burn-in failed |
| `SUBTITLE-BURN-400-001` | Unsupported subtitle format |
| `SUBTITLE-BURN-404-001` | Subtitle font not found |
| `SUBTITLE-BURN-422-001` | Subtitle glyph missing in font |
