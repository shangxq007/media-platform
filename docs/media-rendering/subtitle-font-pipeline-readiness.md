# Subtitle & Font Pipeline Readiness Assessment

**Created:** 2026-06-12
**Last updated:** 2026-06-12 (P0 security fixes applied)
**Status:** Assessment + security hardening
**Based on:** Code scan of render-module, ai-module, shared-kernel, docs

---

## 1. Executive Summary

| Pipeline | Readiness | Recommendation |
|----------|-----------|----------------|
| **Subtitle burn-in** | **POC/Partial** — FFmpeg drawtext and libass ASS paths exist with real implementations | **Recommended next route** — closest to user-visible value |
| **Soft subtitle packaging** | **Interface only** — no mux implementation | Deferred — needs packaging provider |
| **Font validation** | **Noop active** — `NoopFontValidator` is the default, real validators disabled | Part of font MVP |
| **Font fallback** | **Noop active** — `NoopFontStackResolver` returns `sans-serif` | Part of font MVP |
| **Font subsetting** | **Real impl disabled** — `PyftsubsetFontSubsetter` exists but `enabled()=false` | Deferred |
| **CJK/emoji/RTL** | **Partial** — `FontRegistryService` checks 10 Unicode ranges + emoji, but no shaping | Part of font MVP |

**Key insight:** The subtitle pipeline has more real implementation than the font pipeline. The font pipeline is mostly noop/skeleton with real implementations feature-flagged off. The shortest product loop is **subtitle burn-in with font-safe defaults**, which can work today using FFmpeg's built-in fonts.

---

## 2. Subtitle Capability Reality Matrix

| Capability | Status | Code Path | Provider Dependency | Tests | Product Risk | Notes |
|------------|--------|-----------|---------------------|-------|--------------|-------|
| **SRT parse** | IMPLEMENTED | `SrtSubtitleAdapter.parse()` | None | 1 test | Low | Full regex time parsing, `TimelineTextOverlay` conversion |
| **SRT export** | IMPLEMENTED | `SrtSubtitleAdapter.toSrt()` | None | 0 tests | Low | `HH:MM:SS,mmm --> HH:MM:SS,mmm` format |
| **WebVTT parse** | IMPLEMENTED | `WebVttSubtitleAdapter.parse()` | None | **0 tests** | Medium | BOM stripping, block splitting — no test coverage |
| **WebVTT export** | IMPLEMENTED | `WebVttSubtitleAdapter.toWebVtt()` | None | 0 tests | Low | Language header support |
| **ASS parse** | **MISSING** | No `AssSubtitleAdapter` | — | — | Medium | Can write ASS but cannot import it |
| **ASS write** | IMPLEMENTED | `LibassAssFileWriter.writeAss()` | None | 0 dedicated tests | Medium | Valid ASS v4+ with styles/events |
| **Subtitle normalization** | IMPLEMENTED | `SubtitleBurnInService` | None | 10 tests | Low | Time validation, cue dedup |
| **Burn-in via FFmpeg drawtext** | IMPLEMENTED | `SubtitleBurnInService.buildFilterString()` | FFmpeg binary | 10 tests | Low | Font resolution with fallback, glyph checking |
| **Burn-in via libass ASS** | IMPLEMENTED | `LibassSubtitleCompositor.compose()` | FFmpeg + libass | 0 dedicated tests | Medium | Generates ASS sidecar, runs `ffmpeg -vf ass=` |
| **Soft subtitle mux** | **NOT IMPLEMENTED** | No mux service | Packaging provider | — | Medium | Needs GPAC/Bento4/Shaka integration |
| **Multi-language subtitles** | PARTIAL | `SubtitleTrack` has `fontId` per track | Font system | 0 tests | Medium | Model exists, no multi-track burn-in |
| **Styling** | PARTIAL | `PublicCaptionStyle` record | None | 0 tests | Medium | DTO exists, limited burn-in support |
| **Positioning** | PARTIAL | ASS `\pos()` tags in `LibassAssFileWriter` | libass | 0 tests | Medium | Basic positioning, no user control |
| **Outline/shadow** | PARTIAL | ASS style parameters | libass | 0 tests | Low | Default ASS styles include outline |
| **Karaoke/animated** | PLANNED | `RemotionCaptionWord` record | Remotion | 0 tests | High | DTO exists, no render path |
| **Auto captions** | IMPLEMENTED (noop backend) | `AutoCaptionsService` | `SpeechToTextPort` | 3 tests (noop) | High | Only `NoopSpeechToTextProvider` — no real STT |
| **Subtitle preview** | **NOT IMPLEMENTED** | No preview service | — | — | Medium | No browser-side subtitle preview |
| **Subtitle artifact export** | IMPLEMENTED | `SrtSubtitleAdapter.toSrt()` | None | 0 tests | Low | Can export SRT from timeline |
| **ASS override sanitization** | **MISSING** | No sanitization | — | — | **HIGH** | User text written directly to ASS — injection risk |
| **Subtitle path validation** | **MISSING** | `FFmpegRenderProvider.extractSubtitlePath()` | — | — | **HIGH** | No path validation on subtitle path from timeline JSON |

### Provider Subtitle Support

| Provider | Capabilities | Status |
|----------|-------------|--------|
| FFmpeg | `caption_burn_in` via drawtext filter | **IMPLEMENTED** |
| Libass overlay | `subtitle_overlay, ass_subtitle, ssa_subtitle, caption_burn_in` | **IMPLEMENTED (POC)** |
| JavaCV | `subtitle-burn` via FFmpegFrameFilter | **IMPLEMENTED (deprecated)** |
| GStreamer | `subtitle-overlay` | **IMPLEMENTED (HOLD)** |
| Remotion | `caption_burn_in, caption_effects` | **IMPLEMENTED (stub)** |

---

## 3. Font Capability Reality Matrix

| Capability | Status | Code Path | Provider Dependency | Tests | Product Risk | Notes |
|------------|--------|-----------|---------------------|-------|--------------|-------|
| **Font upload** | IMPLEMENTED | `FontRegistryService.registerFont()` | None | 0 tests | Low | Stores file, extracts metadata |
| **Font validation** | **NOOP** | `NoopFontValidator` (default) | None | via skeleton tests | **HIGH** | Returns `WARNING_PASS` for everything |
| **Font validation (basic)** | IMPLEMENTED | `BasicFontValidator` | None | ✅ New tests | Low | Magic bytes, size check, AWT family detection |
| **Font validation (bakery)** | STUB | `FontBakeryValidator` | Python fontbakery | via skeleton tests | Medium | `enabled()=false`, returns `DISABLED` |
| **Font security scanning** | IMPLEMENTED | `BasicFontSecurityScanner` | None | 10 tests | Low | Extension whitelist, magic bytes, 50MB limit, path traversal |
| **Font security (OTS)** | SKELETON | `OTSFontSecurityScanner` | OTS binary | via skeleton tests | Medium | `enabled()=false`, no real OTS call |
| **Font family detection** | IMPLEMENTED | `FontRegistryService` | `java.awt.Font` | 0 tests | Medium | Uses AWT font parsing |
| **Font metadata extraction** | SKELETON | `FontToolsMetadataExtractor` | Python fonttools | via skeleton tests | Medium | `enabled()=false`, returns empty |
| **Font fallback** | **NOOP** | `NoopFontStackResolver` (default) | None | via preflight tests | **HIGH** | Returns empty stack with `sans-serif` fallback |
| **Font fallback (real)** | IMPLEMENTED | `FontRegistryService.resolveFallbackChain()` | `java.awt.Font` | 0 tests | Medium | AWT-based glyph coverage checking |
| **CJK support** | PARTIAL | `FontRegistryService` checks CJK range | `java.awt.Font` | 0 tests | Medium | Range detection works, no shaping |
| **Emoji support** | PARTIAL | `FontRegistryService` checks emoji range | `java.awt.Font` | 0 tests | Medium | Range detection works, no color emoji |
| **RTL support** | **NOT IMPLEMENTED** | No RTL handling | — | — | High | No bidi algorithm, no RTL detection |
| **Shaping** | SKELETON | `HarfBuzzShapingValidator` | HarfBuzz binary | via skeleton tests | High | `enabled()=false`, no real shaping |
| **OpenType features** | **NOT IMPLEMENTED** | No OT feature support | — | — | Medium | No feature tag parsing |
| **Variable font axes** | **NOT IMPLEMENTED** | No variable font support | — | — | Low | No axis parsing |
| **Font subsetting** | **NOOP** | `NoopFontSubsetter` (default) | None | via skeleton tests | Medium | Returns failure |
| **Font subsetting (real)** | IMPLEMENTED (disabled) | `PyftsubsetFontSubsetter` | Python pyftsubset | via skeleton tests | Low | Full CLI integration, `enabled()=false` |
| **Font subset generation** | PLACEHOLDER | `FontRegistryService.generateFontSubset()` | None | 0 tests | Medium | Copies original file as "subset" |
| **Font delivery** | IMPLEMENTED | `FontAssetRepository` | None | via preflight tests | Low | In-memory store (dev mode) |
| **Font cache cleanup** | **NOT IMPLEMENTED** | No cleanup service | — | — | Low | No orphaned font cleanup |
| **Font licensing/policy** | PLANNED | `FontCiAcceptancePolicy` record | None | skeleton tests | Low | Policy definitions only, no enforcement |
| **Font in subtitle burn-in** | IMPLEMENTED | `SubtitleBurnInService` font resolution | `java.awt.Font` | 10 tests | Low | Font resolution with fallback chain |
| **Font in text overlay** | PARTIAL | `SkiaStickerOverlayProvider` | Java2D | 0 tests | Medium | Sticker overlay, not text renderer |

### Font Pipeline Architecture

```
Font Upload → FontSecurityScanner → FontValidator → FontSubsetter → FontAssetRepository
                    ↓                    ↓               ↓
            BasicFontSecurityScanner  NoopFontValidator  NoopFontSubsetter (active)
            OTSFontSecurityScanner    FontBakeryValidator  PyftsubsetFontSubsetter (disabled)
                                      FontToolsMetadataValidator (disabled)
```

**Key gap:** The active path uses `NoopFontValidator` and `NoopFontSubsetter`. The real implementations exist but are feature-flagged off. This means fonts pass validation without any real checking.

---

## 4. Noop / Skeleton / Documentation-Ahead Findings

| Component | Status | What It Actually Does | Documentation Claims |
|-----------|--------|----------------------|---------------------|
| `NoopFontValidator` | **NOOP (active)** | Returns `WARNING_PASS` for everything | Font validation pipeline documented as existing |
| `NoopFontSecurityScanner` | **NOOP (deprecated)** | Returns `WARNING_PASS`, `productionSafe()=false` | Font security documented |
| `NoopFontSubsetter` | **NOOP (active)** | Returns failure, no subsetting | Font subsetting documented |
| `NoopFontStackResolver` | **NOOP (deprecated)** | Returns empty stack, `sans-serif` fallback | Replaced by `BasicFontStackResolver` |
| `NoopMissingGlyphDetector` | **NOOP (deprecated)** | Returns zero missing glyphs | Replaced by `BasicMissingGlyphDetector` |
| `NoopSpeechToTextProvider` | **NOOP (active)** | Returns `"[Auto captions unavailable]"` | Auto captions documented |
| `NoopSubtitleTranslationProvider` | **NOOP (active)** | Returns `"[translated] " + original` | Translation documented |
| `FontBakeryValidator` | **STUB** | `enabled()=false`, returns `DISABLED` | Fontbakery validation documented |
| `OTSFontSecurityScanner` | **SKELETON** | Disabled, no OTS CLI call when enabled | OTS sanitization documented |
| `HarfBuzzShapingValidator` | **SKELETON** | `enabled()=false`, empty results | HarfBuzz shaping documented |
| `FontToolsCoverageChecker` | **SKELETON** | `enabled()=false`, no logic when enabled | FontTools coverage documented |
| `FontToolsMetadataExtractor` | **SKELETON** | `enabled()=false`, returns empty when enabled | FontTools metadata documented |
| `SubtitleBurnInNode` (LiteFlow) | **STUB** | Logs message, does no work | Policy-driven burn-in documented |
| `PyftsubsetFontSubsetter` | **REAL but OFF** | Full CLI integration, `enabled()=false` | Pyftsubset documented as available |

**Pattern:** Documentation is significantly ahead of implementation. Many font subsystems have "NOT production-safe" warnings in their noop implementations, but the docs don't always reflect this.

---

## 5. Security Findings

| # | Finding | Severity | Area | Status | Recommendation |
|---|---------|----------|------|--------|----------------|
| 1 | **ASS subtitle injection** — user text written directly to ASS Dialogue lines without sanitization | **P0** | Subtitle | ✅ Fixed | `AssTextSanitizer.sanitize()` — strips `{}`, `\`, override tags |
| 2 | **Subtitle path injection** — `extractSubtitlePath()` reads path from timeline JSON with no validation | **P0** | Subtitle | ✅ Fixed | `SubtitlePathSanitizer.sanitize()` — rejects traversal, schemes, filter separators |
| 3 | **FFmpeg subtitles= filter path not sanitized** — path embedded in filter graph with only quote escaping | **P1** | Subtitle | ✅ Fixed | Path validated via `SubtitlePathSanitizer` before embedding |
| 4 | **FontRegistryService no path validation on fontId** — concatenated into file paths without traversal check | **P1** | Font | Open | Use `assertValidId()` + path prefix check |
| 5 | **Font file read into memory for magic bytes** — `Files.readAllBytes()` reads entire file (up to 50MB) | **P2** | Font | Open | Read only first 4-16 bytes |
| 6 | **No font file type enforcement beyond extension** — `BasicFontSecurityScanner` checks magic bytes but noop validator passes everything | **P2** | Font | Open | Enable real font validator |
| 7 | **ProcessBuilder uses argument lists** — prevents shell injection | **INFO** | Both | Mitigated | Good pattern |
| 8 | **StorageKeyPolicy has strong traversal defenses** | **INFO** | Both | Mitigated | Reference implementation |

---

## 6. Test Results

| Command | Result | Notes |
|---------|--------|-------|
| `./gradlew :render-module:test --tests "*Subtitle*"` | ✅ BUILD SUCCESSFUL | SubtitleBurnInService (10), SubtitleRenderService (7), SrtSubtitleAdapter (1) |
| `./gradlew :render-module:test --tests "*Caption*"` | ✅ BUILD SUCCESSFUL | AutoCaptionsService (3), SubtitleRenderApiMvc (11) |
| `./gradlew :render-module:test --tests "*Font*"` | ✅ BUILD SUCCESSFUL | FontSecurityScanner (10), RenderJobFontPreflight (10), FontAssetStatus, FontQaSkeleton |
| `./gradlew :render-module:test --tests "*Libass*"` | ✅ BUILD SUCCESSFUL | Covered by subtitle tests |
| `./gradlew :render-module:test` | ✅ BUILD SUCCESSFUL | All render-module tests pass |
| `./gradlew :platform-app:test` | ✅ BUILD SUCCESSFUL | 236 tests, 0 failures |

### Test Coverage Gaps

| Area | Tests | Gap |
|------|-------|-----|
| WebVTT parse/export | 0 | No test for WebVTT adapter |
| ASS write | 0 | No test for LibassAssFileWriter |
| LibassSubtitleCompositor | 0 | No dedicated compositor test |
| ASS sanitization | 0 | No test for injection prevention |
| Subtitle path validation | 0 | No test for path traversal in subtitle paths |
| FontRegistryService | 0 | No dedicated test |
| Font fallback chain | 0 | No test for multi-font fallback |
| CJK/emoji detection | 0 | No test for Unicode range checking |

---

## 7. Product Route Recommendation

### Route A: Subtitle Burn-in MVP ✅ RECOMMENDED

**Goal:** SRT/WebVTT input → normalize cues → font-safe default → FFmpeg/libass burn-in → artifact output → tests

**Scope:**
1. Fix ASS text injection (P0 security)
2. Fix subtitle path injection (P0 security)
3. Add WebVTT parse test
4. Add ASS write test
5. Add LibassSubtitleCompositor test
6. Harden `SubtitleBurnInService` font resolution with fallback
7. Add subtitle burn-in characterization test (end-to-end)
8. Document burn-in limitations (no styling control, no multi-track)

**Why this route:**
- Closest to user-visible value — users can see subtitles on their videos
- Most code already exists and works (FFmpeg drawtext + libass paths)
- Security fixes are mandatory regardless of route
- Foundation for soft subtitle and font pipeline
- FFmpeg is the only runtime dependency (already production-ready)

**Non-goals:**
- No soft subtitle mux (needs packaging provider)
- No karaoke/animated captions (needs Remotion)
- No real STT for auto captions (needs Whisper/Deepgram)
- No RTL/shaping (needs HarfBuzz)

### Route B: Soft Subtitle Packaging MVP (Deferred)

**Goal:** SRT/WebVTT → mux into output → HLS/DASH subtitles

**Why deferred:**
- Needs packaging provider integration (GPAC/Bento4/Shaka)
- Player compatibility testing required
- Lower user visibility than burn-in

### Route C: Font Validation & Fallback MVP (Deferred)

**Goal:** Upload font → validate → metadata/family → safe delivery → fallback strategy

**Why deferred:**
- Users can't see font validation results directly
- FFmpeg built-in fonts work for burn-in today
- Complex CJK/emoji/RTL work needed for real value

---

## 8. Next Prompt Recommendation

**`SUBTITLE-BURN-IN-MVP-HARDENING`**

First steps:
1. Fix ASS text injection in `LibassAssFileWriter`
2. Fix subtitle path injection in `FFmpegRenderProvider`
3. Add WebVTT/ASS/compositor tests
4. Add subtitle burn-in characterization test
5. Document burn-in limitations and font fallback behavior

---

## 9. Code References

| Concept | File |
|---------|------|
| SRT adapter | `render-module/.../domain/timeline/standards/SrtSubtitleAdapter.java` |
| WebVTT adapter | `render-module/.../domain/timeline/standards/WebVttSubtitleAdapter.java` |
| ASS writer | `render-module/.../infrastructure/libass/LibassAssFileWriter.java` |
| Libass compositor | `render-module/.../infrastructure/libass/LibassSubtitleCompositor.java` |
| Burn-in service | `render-module/.../infrastructure/SubtitleBurnInService.java` |
| Render service | `render-module/.../infrastructure/SubtitleRenderService.java` |
| Auto captions | `render-module/.../app/autocaptions/AutoCaptionsService.java` |
| Font security scanner | `render-module/.../infrastructure/font/BasicFontSecurityScanner.java` |
| Font registry | `render-module/.../infrastructure/font/FontRegistryService.java` |
| Font preflight | `render-module/.../infrastructure/font/RenderJobFontPreflight.java` |
| Noop font validator | `render-module/.../infrastructure/font/NoopFontValidator.java` |
| Noop font subsetter | `render-module/.../infrastructure/font/NoopFontSubsetter.java` |
