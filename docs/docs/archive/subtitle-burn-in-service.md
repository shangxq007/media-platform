# Subtitle Burn-In Service

> **Purpose:** Unified subtitle burn-in architecture for rendering subtitles into video frames.
> **Last Updated:** 2026-05-17

---

## Architecture

The subtitle burn-in system generates FFmpeg filter expressions (`drawtext`) that burn subtitle text directly into video frames during transcoding. It supports multi-track subtitles, font management, glyph coverage checking, and fallback font chains.

```
SubtitleBurnInService (@Service)
    │
    ├── depends on ──→ FontRegistryService (@Service)
    │                       ├── Font registration and metadata
    │                       ├── Glyph coverage checking
    │                       ├── Font subset generation
    │                       └── Font fallback resolution
    │
    └── SubtitleRenderService (@Service)
            └── Thin wrapper around SubtitleBurnInService
                (for API layer separation)
```

**Note:** There is currently no `SubtitleBurnInAdapter` interface. The `SubtitleBurnInService` is used directly as a concrete `@Service`. This differs from the media probe system which uses an adapter pattern. If alternative subtitle rendering backends are added in the future, introduce a `SubtitleBurnInAdapter` interface at that time.

---

## SubtitleBurnInService

`SubtitleBurnInService` is the primary service for subtitle burn-in operations.

### Core Methods

| Method | Description |
|--------|-------------|
| `buildSubtitleFilter(tracks)` | Builds an FFmpeg `drawtext` filter string from subtitle tracks |
| `buildSubtitleFilter(tracks, preset)` | Same, with preset-aware escaping |
| `burnInFrame(image, frame, total, cues)` | Burns subtitles onto a single `BufferedImage` frame (Java2D) |
| `checkSubtitleCompatibility(tracks)` | Checks font availability and glyph coverage |
| `resolveFontFile(fontFilePath)` | Validates a font file exists |

### FFmpeg drawtext Filter Generation

The primary burn-in mechanism generates FFmpeg `drawtext` filter expressions:

```
drawtext=text='Hello':fontsize=24:fontcolor=white:box=1:boxcolor=black@0.5
  :x=(w-text_w)/2:y=h-text_h-20:enable='between(t,1.5,3.0)':fontfile=/path/to/font.ttf
```

Multiple cues are joined with `,` to form a filter chain.

#### Input Format

```java
List<Map<String, Object>> subtitleTracks = List.of(
    Map.of(
        "burnIn", true,
        "fontId", "noto-sans",
        "fallbackFontIds", List.of("arial", "roboto"),
        "cues", List.of(
            Map.of("text", "Hello World", "startTime", 1.5, "endTime", 3.0),
            Map.of("text", "Second line", "startTime", 3.5, "endTime", 5.0)
        )
    )
);
```

#### Filter Building Flow

1. Iterate subtitle tracks, skipping non-burn-in tracks
2. For each track, resolve the font file (primary → fallback chain)
3. Check glyph coverage and log warnings for missing glyphs
4. For each cue, escape text for FFmpeg drawtext syntax
5. Append `drawtext` expression with timing enable condition
6. Join multiple cues with `,`

### Java2D Frame Burn-In

`burnInFrame()` provides an alternative burn-in path using Java2D graphics:

```java
BufferedImage result = burnInFrame(image, frame, total, cues);
```

This is used by the `OFXRenderProvider` for frame-by-frame rendering. It:
1. Creates a copy of the input image
2. Calculates current time from frame number and FPS (30.0)
3. For each cue active at the current time, draws text with shadow and background box

### Font Resolution

```
resolveTrackFontFile(subTrack)
    │
    ├── Try: /tmp/fonts/{fontId}.ttf
    ├── Try: /tmp/fonts/{fallbackId}.ttf (for each fallback)
    └── Return: first match, or null
```

### Compatibility Checking

```java
List<String> warnings = burnInService.checkSubtitleCompatibility(subtrack);
// Returns warnings like:
//   "SUBTITLE_FONT_MISSING: noto-sans"
//   "SUBTITLE_GLYPH_MISSING: font=noto-sans missing=5"
```

---

## FontRegistryService

`FontRegistryService` manages font registration, subsetting, and glyph coverage.

### Key Methods

| Method | Description |
|--------|-------------|
| `registerFont(fontId, family, format, fileSize)` | Register a font and return metadata |
| `generateFontSubset(sourceFontId, text, format)` | Create a font subset for specific text |
| `checkGlyphCoverage(fontFilePath)` | Check which Unicode ranges the font supports |
| `findMissingGlyphs(fontFilePath, text)` | List characters the font cannot display |
| `resolveFontWithFallback(fontId, fallbackIds, text)` | Resolve best font with fallback chain |

### Glyph Coverage Ranges

The service checks these Unicode ranges:
- Basic Latin (U+0020–U+007F)
- Latin-1 Supplement (U+00A0–U+00FF)
- CJK Unified Ideographs (U+4E00–U+9FFF)
- Hiragana (U+3040–U+309F)
- Katakana (U+30A0–U+30FF)
- Hangul Syllables (U+AC00–U+D7AF)
- Cyrillic (U+0400–U+04FF)
- Arabic (U+0600–U+06FF)
- Devanagari (U+0900–U+097F)
- Thai (U+0E00–U+0E7F)
- Emoji (U+1F600)

---

## SubtitleRenderService

`SubtitleRenderService` is a thin wrapper around `SubtitleBurnInService`:

```java
@Service
public class SubtitleRenderService {
    private final SubtitleBurnInService burnInService;

    public SubtitleRenderService(SubtitleBurnInService burnInService) {
        this.burnInService = burnInService;
    }

    public String buildSubtitleFilter(List<Map<String, Object>> tracks) {
        return burnInService.buildSubtitleFilter(tracks);
    }

    public List<String> checkSubtitleCompatibility(List<Map<String, Object>> tracks) {
        return burnInService.checkSubtitleCompatibility(tracks);
    }

    public String resolveFontFile(String fontFilePath) {
        return burnInService.resolveFontFile(fontFilePath);
    }
}
```

**Purpose:** Provides a separate service name for the API layer, keeping `SubtitleBurnInService` as an internal implementation detail. Other modules should depend on `SubtitleRenderService` rather than `SubtitleBurnInService` directly.

---

## Integration with Render Providers

### JavaCVRenderProvider

Uses `SubtitleBurnInService` through `JavaCVRenderService`:

```java
// Build filter complex for subtitle burn-in
StringBuilder filter = buildSubtitleFilterComplex(subtitleTracks, preset);

// Apply via FFmpegFrameFilter
FFmpegFrameFilter filter = new FFmpegFrameFilter(filterComplex, width, height);
filter.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
filter.start();
// ... push frames, pull filtered frames
```

### OFXRenderProvider

Uses Java2D frame burn-in:

```java
BufferedImage image = createFrameWithEffects(width, height, i, totalFrames, progress, allEffects);
image = subtitleBurnInService.burnInFrame(image, i, totalFrames, subtitleCues);
```

---

## Migration from Old Implementations

### Previous Architecture

Subtitle rendering was previously embedded directly in render providers without a dedicated service. Each provider had its own inline subtitle handling.

### Migration Steps

1. **Replace inline subtitle filter building:**
   ```java
   // Old — inline string building in provider
   String filter = "drawtext=text='" + text + "':...";

   // New — use the service
   String filter = subtitleBurnInService.buildSubtitleFilter(tracks);
   ```

2. **Replace inline font resolution:**
   ```java
   // Old — manual path construction
   String fontPath = fontsDir + "/" + fontId + ".ttf";

   // New — use FontRegistryService
   String fontPath = fontRegistryService.resolveFontWithFallback(fontId, fallbacks, text);
   ```

3. **Use compatibility checking:**
   ```java
   List<String> warnings = subtitleBurnInService.checkSubtitleCompatibility(tracks);
   for (String warning : warnings) {
       log.warn("Subtitle issue: {}", warning);
   }
   ```

### Current State

All render providers now use `SubtitleBurnInService` (via `JavaCVRenderService` or directly). The old inline implementations have been removed. No deprecated wrappers exist for subtitle services.
