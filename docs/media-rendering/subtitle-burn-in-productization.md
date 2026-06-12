# Subtitle Burn-in Productization

**Status:** Productized
**Date:** 2026-06-12
**Baseline:** FFmpeg/libass

---

## Supported Capabilities

| Capability | Status | Provider | Notes |
|------------|--------|----------|-------|
| SRT input | IMPLEMENTED | `SrtSubtitleAdapter` | Full parse + export |
| WebVTT input | IMPLEMENTED | `WebVttSubtitleAdapter` | Full parse + export |
| ASS sanitized output | IMPLEMENTED | `AssTextSanitizer` + `LibassAssFileWriter` | Injection-safe |
| FFmpeg drawtext burn-in | IMPLEMENTED | `SubtitleBurnInService` | Legacy/fallback path |
| libass ASS burn-in | IMPLEMENTED | `LibassSubtitleCompositor` | Default path when libass enabled |
| Subtitle path validation | IMPLEMENTED | `SubtitlePathSanitizer` | Rejects traversal, schemes, filter separators |
| Font validation | IMPLEMENTED | `BasicFontValidator` | Magic bytes, size, AWT family |
| Font fallback | IMPLEMENTED | `BasicFontStackResolver` | CJK/emoji detection |
| Font security scanning | IMPLEMENTED | `BasicFontSecurityScanner` | Extension whitelist, magic bytes |
| Missing glyph detection | IMPLEMENTED | `BasicMissingGlyphDetector` | AWT glyph checking |

## Not Supported Yet

| Capability | Status | Notes |
|------------|--------|-------|
| Remotion dynamic captions | STUB/PLANNED | Future advanced visual subtitles |
| Word-level highlight | PLANNED | Needs Remotion |
| Karaoke captions | PLANNED | Needs Remotion |
| Social captions (TikTok style) | PLANNED | Needs Remotion |
| Brand templates | PLANNED | Needs Remotion |
| Soft subtitle mux | PLANNED | Needs packaging provider (GPAC/Bento4/Shaka) |
| Multi-language packaging | PLANNED | Needs mux + HLS/DASH support |
| Auto captions STT | PLANNED | Needs Whisper/Deepgram |
| Full RTL shaping | PLANNED | Needs HarfBuzz |
| Font subsetting | PLANNED | Needs pyftsubset |
| FontBakery validation | PLANNED | Needs Python fontbakery |
| OTS font sanitization | PLANNED | Needs ots-sanitize CLI |

## Security Measures

| Risk | Mitigation | Enforcement |
|------|-----------|-------------|
| ASS injection | `AssTextSanitizer.sanitize()` strips braces, backslashes | Called in `LibassAssFileWriter` |
| Subtitle path injection | `SubtitlePathSanitizer.sanitize()` rejects traversal, schemes | Called in `FFmpegRenderProvider` |
| Font ID path injection | `FontIdPolicy.requireValidFontId()` rejects traversal | Called in `FontRegistryService` |
| Font file validation | `BasicFontSecurityScanner` checks magic bytes, extensions | Default bean |
| Shell injection | ProcessBuilder with argument lists (no shell) | All FFmpeg/libass invocations |
| Raw user path into FFmpeg | Subtitle path validated before embedding in filter | `extractSubtitlePath()` |

## Render Flow

```
Timeline JSON
  ↓
SrtSubtitleAdapter.parse() / WebVttSubtitleAdapter.parse()
  ↓
List<TimelineTextOverlay>
  ↓
AssTextSanitizer.sanitize() — neutralize override tags
  ↓
LibassAssFileWriter.write() — generate .ass file
  ↓
LibassSubtitleCompositor.applyTextOverlays() — FFmpeg -vf ass=
  ↓
Output video with burned-in subtitles
```

## Provider Selection

| Provider | Status | Dispatch Eligible | Used For |
|----------|--------|-------------------|----------|
| FFmpeg | PRODUCTION | ✅ Yes | Baseline subtitle burn-in |
| libass overlay | POC | ⚠️ Needs explicit allow | ASS/SSA burn-in |
| Remotion | STUB | ❌ No | Future advanced subtitles |

**Rule:** Subtitle burn-in never selects Remotion. FFmpeg/libass is the production baseline.

## Test Coverage

| Test | Coverage |
|------|----------|
| `SrtSubtitleAdapterTest` | SRT parse |
| `WebVttSubtitleAdapterTest` | WebVTT parse, export, BOM, unicode |
| `AssTextSanitizerTest` | Injection prevention, unicode, braces |
| `SubtitlePathSanitizerTest` | Traversal, schemes, filter separators |
| `LibassAssFileWriterTest` | ASS header, style, dialogue, unicode |
| `LibassSubtitleCompositorTest` | Command safety, path validation |
| `SubtitleBurnInServiceTest` | Drawtext filter, font resolution |
| `SubtitleRenderServiceTest` | Service delegation |
| `SubtitleBurnInProductizationTest` | End-to-end pipeline simulation |
| `ProviderEligibilityTest` | STUB/SKELETON/DEPRECATED/MOCK blocked |

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `render.subtitle.libass.enabled` | `true` | Enable libass ASS burn-in path |
| `app.fonts.dir` | `/tmp/platform/fonts` | Font file directory |
| `render.font.security.scanner` | `basic` | Font security scanner (`basic` or `noop`) |
