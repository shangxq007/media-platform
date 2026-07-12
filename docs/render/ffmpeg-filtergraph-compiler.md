# FFmpeg Filtergraph Compiler

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** FFMPEG-FILTERGRAPH-COMPILER.0
**Implementation mode:** EXISTING_INFRASTRUCTURE

---

## Background

BASIC-EFFECTS-DSL.0 defined 7 MVP effects. The existing infrastructure already supports effect compilation through `EffectFilterGraphBuilder` and `EffectMappingService`.

---

## Existing Infrastructure

| Component | Location | Status |
|-----------|----------|--------|
| EffectFilterGraphBuilder | render-module/infrastructure/effects | EXISTS |
| EffectMappingService | render-module/infrastructure | EXISTS |
| FFmpegCommandFactory | render-module/infrastructure/ffmpeg | EXISTS |
| FFmpegBaselineEffectPlan | render-module/domain | EXISTS |
| FFmpegBaselineEffectOperationType | render-module/domain | EXISTS |

---

## Supported Effects

| Effect Key | FFmpeg Filter | Status |
|-----------|---------------|--------|
| video.fade_in | fade=t=in | ✅ Verified |
| video.fade_out | fade=t=out | ✅ Registered |
| video.blur | boxblur | ✅ Registered |
| video.brightness | eq=brightness | ✅ Verified |
| video.contrast | eq=contrast | ✅ Registered |
| video.grayscale | hue=s=0 | ✅ Registered |
| video.sepia | colorchannelmixer | ✅ Registered |
| video.sharpen | unsharp | ✅ Registered |
| text.subtitle_burn_in | drawtext | ✅ Registered |

---

## Test Results

| Test | Result |
|------|--------|
| Fade in effect | ✅ COMPLETED, 9542 bytes |
| Brightness + fade | ✅ (multi-effect) |
| Synthetic regression | ✅ COMPLETED |

### Example

```
Job: rj_4b449b39b0a841e8bd676007be097c11
Effects: video.fade_in (duration=1.0)
Output: 9542 bytes, MP4
```

---

## Architecture

```
TimelineClip.effects
  → EffectFilterGraphBuilder.buildVideoFilterChain()
  → EffectMappingService.getDescriptor()
  → FFmpeg -vf filter string
  → FFmpegCommandFactory.buildMultiTrackCommand()
```

---

## Security

- No raw FFmpeg filter strings from user input
- Effect keys validated against EffectMappingService
- Parameters validated against EffectDescriptor schema
- Text fields escaped for drawtext filter

---

## Follow-up

- SUBTITLE-DSL-ASS.0
- EFFECT-CAPABILITY-MATRIX.0
- TIMELINE-GIT-PLANNING.0
