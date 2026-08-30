# Subtitle Rendering Strategy ADR

**Status:** Accepted
**Date:** 2026-06-12
**Deciders:** Platform Architecture Team

---

## 1. Context

The platform has completed:
- Subtitle Burn-in MVP hardening (ASS injection, path injection, FFmpeg filter path fixes)
- Font Validation/Fallback MVP (FontIdPolicy, BasicFontValidator, BasicFontStackResolver, BasicMissingGlyphDetector)
- Render Provider SPI formalization (ProviderStatus, ProviderEligibility)
- Render Farm Level 3 MVP (paused)

The platform needs a clear strategy for subtitle rendering to avoid:
- Binding all subtitle capabilities to a single provider
- Confusing baseline burn-in with advanced visual effects
- Premature dependency on providers that are not production-ready

## 2. Decision

**Baseline subtitle rendering uses FFmpeg/libass. Advanced visual subtitles will use Remotion in the future. These are separate tiers with separate responsibilities.**

### Tier 1: Baseline (FFmpeg/libass) — Current Production Path

| Capability | Provider | Status |
|------------|----------|--------|
| SRT parse | `SrtSubtitleAdapter` | IMPLEMENTED |
| WebVTT parse | `WebVttSubtitleAdapter` | IMPLEMENTED |
| ASS write | `LibassAssFileWriter` | IMPLEMENTED |
| ASS sanitization | `AssTextSanitizer` | IMPLEMENTED |
| Subtitle path validation | `SubtitlePathSanitizer` | IMPLEMENTED |
| FFmpeg drawtext burn-in | `SubtitleBurnInService` | IMPLEMENTED |
| libass ASS burn-in | `LibassSubtitleCompositor` | IMPLEMENTED |
| Font validation | `BasicFontValidator` | IMPLEMENTED |
| Font fallback | `BasicFontStackResolver` | IMPLEMENTED |
| Font security | `BasicFontSecurityScanner` | IMPLEMENTED |
| Missing glyph detection | `BasicMissingGlyphDetector` | IMPLEMENTED |

### Tier 2: Advanced Visual Subtitles (Remotion) — Future

| Capability | Provider | Status |
|------------|----------|--------|
| Template subtitle animation | Remotion | STUB/PLANNED |
| Word-level highlight | Remotion | STUB/PLANNED |
| Karaoke captions | Remotion | STUB/PLANNED |
| Social captions (TikTok style) | Remotion | STUB/PLANNED |
| Brand templates | Remotion | STUB/PLANNED |
| Dynamic text overlays | Remotion | STUB/PLANNED |

### Tier 3: Infrastructure Subtitles — Future

| Capability | Provider | Status |
|------------|----------|--------|
| Soft subtitle mux | Packaging provider (GPAC/Bento4/Shaka) | PLANNED |
| Multi-language subtitle packaging | Packaging provider | PLANNED |
| Auto captions (STT) | Speech-to-text provider | PLANNED |

## 3. Responsibility Split

| Capability | Owner | Current Status | Notes |
|------------|-------|----------------|-------|
| SRT/WebVTT parse | Backend (render-module) | IMPLEMENTED | `SrtSubtitleAdapter`, `WebVttSubtitleAdapter` |
| ASS sanitize/write | Backend (render-module) | IMPLEMENTED | `AssTextSanitizer`, `LibassAssFileWriter` |
| Basic burn-in | Backend (FFmpeg) | IMPLEMENTED | `SubtitleBurnInService` with drawtext filter |
| libass burn-in | Backend (libass/FFmpeg) | IMPLEMENTED | `LibassSubtitleCompositor` with `ffmpeg -vf ass=` |
| FFmpeg drawtext subtitles | Backend (FFmpeg) | IMPLEMENTED | Simple cue-based subtitles |
| Template subtitle animation | Remotion (future) | STUB/PLANNED | Not production baseline |
| Word-level highlight | Remotion (future) | STUB/PLANNED | Not production baseline |
| Karaoke captions | Remotion (future) | STUB/PLANNED | Not production baseline |
| Social captions | Remotion (future) | STUB/PLANNED | Not production baseline |
| Brand templates | Remotion (future) | STUB/PLANNED | Not production baseline |
| Soft subtitle mux | Packaging provider | PLANNED | Needs GPAC/Bento4/Shaka integration |
| Multi-language packaging | Packaging provider | PLANNED | Needs mux + HLS/DASH support |
| Auto captions STT | AI/STT provider | PLANNED | Typed unavailable; no production STT configured |
| Font validation | Backend (render-module) | IMPLEMENTED | `BasicFontValidator` |
| Font fallback | Backend (render-module) | IMPLEMENTED | `BasicFontStackResolver` |
| Font security | Backend (render-module) | IMPLEMENTED | `BasicFontSecurityScanner` |
| CJK/emoji detection | Backend (render-module) | IMPLEMENTED | `BasicMissingGlyphDetector` |
| RTL/shaping | HarfBuzz (future) | PLANNED | `HarfBuzzShapingValidator` disabled |

## 4. Provider Status Rules

| Provider | Status | Dispatch Eligible | Notes |
|----------|--------|-------------------|-------|
| FFmpeg | PRODUCTION | ✅ Yes | Baseline subtitle burn-in |
| libass overlay | POC | ⚠️ Needs explicit allow | ASS/SSA burn-in via FFmpeg |
| Remotion | STUB | ❌ No | Future advanced subtitles only |
| Blender | STUB | ❌ No | Not a subtitle provider |
| Natron | SKELETON | ❌ No | FFmpeg fallback only |
| BMF | PLANNED | ❌ No | Not implemented |
| Mock | MOCK | ❌ No | Test/dev only |

**Remotion dispatch rules:**
- `ProviderStatus.STUB` — `canBeConfiguredForDispatch = false`
- `RenderJobLeaseService` maps `"remotion"` to `ProviderStatus.STUB`
- Even if `@ConditionalOnProperty` enables the bean, lease-level dispatch is blocked
- Remotion is not a dependency for baseline subtitle rendering

## 5. Security Rules

| Rule | Enforcement |
|------|-------------|
| ASS text injection prevention | `AssTextSanitizer.sanitize()` — strips braces, backslashes |
| Subtitle path injection prevention | `SubtitlePathSanitizer.sanitize()` — rejects traversal, schemes |
| FontId path injection prevention | `FontIdPolicy.requireValidFontId()` — rejects traversal, schemes |
| Font file validation | `BasicFontSecurityScanner` — extension whitelist, magic bytes, 50MB limit |
| Font validation | `BasicFontValidator` — magic bytes, size, AWT family detection |
| Remotion templates | Must use declarative template inputs, not raw untrusted code |
| Dynamic captions | Must use structured data, not raw ASS/HTML |

## 6. Non-goals

- ❌ Remotion runtime implementation
- ❌ Browser/Node renderer integration
- ❌ Soft subtitle packaging (deferred to Phase B)
- ❌ Auto captions STT (deferred)
- ❌ Full typography/shaping (HarfBuzz deferred)
- ❌ FontBakery/OTS real integration (deferred)
- ❌ GPU/CUDA rendering
- ❌ BMF integration

## 7. Migration / Roadmap

### Phase A: FFmpeg/libass Burn-in Productization (Current)
- ✅ ASS injection fixed
- ✅ Subtitle path injection fixed
- ✅ Font validation/fallback MVP
- ✅ Font security scanner default cleanup
- 🔲 Characterization tests for full burn-in flow
- 🔲 Integration with render pipeline end-to-end

### Phase B: Soft Subtitle Mux
- SRT/WebVTT → GPAC/Bento4/Shaka mux
- HLS/DASH subtitle tracks
- Player compatibility testing

### Phase C: Remotion Subtitle Provider Spike
- Remotion rendering isolation (Node/browser sandbox)
- Template packaging and delivery
- Deterministic output verification
- Font loading in Remotion context
- Worker image strategy for Remotion

### Phase D: Advanced Templates / Animated Captions
- Word-level highlight
- Karaoke captions
- Social captions (TikTok style)
- Brand templates
- Dynamic text overlays

## 8. Open Questions

| Question | Impact |
|----------|--------|
| Remotion rendering isolation | Security — Node/browser sandbox required |
| Template packaging | Distribution — how to deliver templates to workers |
| Deterministic output | Testing — reproducible renders across environments |
| Font loading in Remotion | Typography — how Remotion accesses platform fonts |
| Worker image strategy | Infrastructure — same image vs multi-image for Remotion |
| Cost/performance | Operations — Remotion vs FFmpeg resource usage |
| Browser security | Security — if Remotion uses headless Chrome |
