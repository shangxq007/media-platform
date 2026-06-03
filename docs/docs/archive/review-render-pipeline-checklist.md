# RenderPipeline Review Checklist

> **Purpose:** Verify RenderPipeline and all Provider implementations.  
> **Reviewer:** _______________  
> **Date:** _______________

---

## JavaCV Provider

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | JavaCVRenderProvider renders video clips | ⬜ | |
| 2 | H.264 codec output works | ⬜ | |
| 3 | H.265 codec output works | ⬜ | |
| 4 | VP9 codec output works | ⬜ | |
| 5 | Watermark overlay works | ⬜ | |
| 6 | Subtitle burn-in via FFmpeg filtergraph | ⬜ | |
| 7 | Font embedding in output | ⬜ | |
| 8 | GPU encoder config (NVENC) | ⬜ | |
| 9 | CPU fallback when GPU unavailable | ⬜ | |
| 10 | Error code RENDER-500-001 on failure | ⬜ | |

## OFX Provider

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | OFXRenderProvider applies effects | ⬜ | |
| 2 | Blur filter works | ⬜ | |
| 3 | Sharpen filter works | ⬜ | |
| 4 | Vignette filter works | ⬜ | |
| 5 | Chromatic aberration filter works | ⬜ | |
| 6 | Dissolve transition works | ⬜ | |
| 7 | Wipe transition works | ⬜ | |
| 8 | Slide transition works | ⬜ | |
| 9 | Zoom transition works | ⬜ | |
| 10 | Text/subtitle overlay works | ⬜ | |
| 11 | Font fallback when font missing | ⬜ | |

## GPAC Provider

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | GpacRenderProvider packages output | ⬜ | |
| 2 | DASH packaging works | ⬜ | |
| 3 | HLS packaging works | ⬜ | |
| 4 | CMAF packaging works | ⬜ | |
| 5 | MP4 faststart command works | ⬜ | |
| 6 | GpacPackagingProvider multi-format | ⬜ | |

## MLT Provider

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | MltRenderProvider generates MLT XML | ⬜ | |
| 2 | Timeline-to-MLT conversion works | ⬜ | |
| 3 | MeltCommandFactory executes melt | ⬜ | |
| 4 | Subtitle overlay in MLT pipeline | ⬜ | |

## GStreamer Provider

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | GStreamerRenderProvider pipeline works | ⬜ | |
| 2 | GStreamerCommandFactory builds commands | ⬜ | |
| 3 | Pipeline-based processing works | ⬜ | |
| 4 | Subtitle overlay pipeline works | ⬜ | |

## GPU Presets

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | GPU_H264 preset selects NVENC H.264 | ⬜ | |
| 2 | GPU_H265 preset selects NVENC HEVC | ⬜ | |
| 3 | GPU_VP9 preset selects VAAPI VP9 | ⬜ | |
| 4 | GPU fallback to CPU when unavailable | ⬜ | |
| 5 | Frontend shows GPU indicator | ⬜ | |

## Remote Worker

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Worker registration works | ⬜ | |
| 2 | Worker status (IDLE/BUSY) tracking | ⬜ | |
| 3 | Job distribution to remote worker | ⬜ | |
| 4 | Worker health check | ⬜ | |
| 5 | Worker deregistration on shutdown | ⬜ | |
| 6 | Frontend shows worker status | ⬜ | |

## ProviderRouter

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Profile-based provider selection | ⬜ | |
| 2 | Tier-aware routing (FREE→JavaCV, PRO→OFX) | ⬜ | |
| 3 | GPU preset routes to remote-javacv | ⬜ | |
| 4 | Provider fallback on failure | ⬜ | |
| 5 | No provider available → RENDER-503-001 | ⬜ | |

## OTIO Timeline

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | OTIO clip parsing works | ⬜ | |
| 2 | OTIO track parsing works | ⬜ | |
| 3 | Effect chain application | ⬜ | |
| 4 | Subtitle track extraction | ⬜ | |
| 5 | Font metadata extraction | ⬜ | |
| 6 | Multi-format serialization | ⬜ | |
| 7 | Schema migration v1→v2 works | ⬜ | |

## Subtitle System

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | SRT upload and parsing | ⬜ | |
| 2 | VTT upload and parsing | ⬜ | |
| 3 | Multi-language subtitle support | ⬜ | |
| 4 | Subtitle burn-in during render | ⬜ | |
| 5 | Font embedding for subtitles | ⬜ | |
| 6 | Font fallback when glyph missing | ⬜ | |
| 7 | Font glyph subset detection | ⬜ | |

## RenderJob State Machine

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | QUEUED → PROCESSING transition | ⬜ | |
| 2 | PROCESSING → COMPLETED transition | ⬜ | |
| 3 | PROCESSING → FAILED transition | ⬜ | |
| 4 | FAILED → QUEUED retry | ⬜ | |
| 5 | Status history tracking | ⬜ | |

## Artifact Output

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Artifact created on COMPLETED | ⬜ | |
| 2 | Artifact metadata includes format/codec | ⬜ | |
| 3 | Storage URI generated | ⬜ | |
| 4 | Cost record finalized | ⬜ | |
| 5 | Artifact linked to RenderJob | ⬜ | |

## Quality Check (if implemented)

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Media probe for output validation | ⬜ | Stub |
| 2 | Quality metrics extraction | ⬜ | Stub |
| 3 | RENDER-422-001 on quality failure | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| JavaCV | ___/10 | 10 | |
| OFX | ___/11 | 11 | |
| GPAC | ___/6 | 6 | |
| MLT | ___/4 | 4 | |
| GStreamer | ___/4 | 4 | |
| GPU Presets | ___/5 | 5 | |
| Remote Worker | ___/6 | 6 | |
| ProviderRouter | ___/5 | 5 | |
| OTIO Timeline | ___/7 | 7 | |
| Subtitle System | ___/7 | 7 | |
| RenderJob State | ___/5 | 5 | |
| Artifact Output | ___/5 | 5 | |
| **Total** | ___/75 | **75** | |

**Reviewer Signature:** _______________  
**Date:** _______________
