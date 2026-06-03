# Provider Extension Roadmap

> **Module:** `render-module`
> **Last Updated:** 2026-05-20  
> **Architecture:** [10-server-nle-layered-architecture.md](./10-server-nle-layered-architecture.md)

## Current State

6 render providers are implemented. JavaCV is the primary provider for video transcoding. The system supports multi-provider routing based on profile and capabilities.

## Layered NLE Target (L1–L7)

| Layer | Engine | Status |
|-------|--------|--------|
| L1 | FFmpeg / JavaCV / GStreamer | ✅ |
| L2 | MLT + Timeline Executor | ✅ / ⚠️ multi-track |
| L3 | Remotion / Shotstack | ✅ / ✅ |
| L4 | Blender | ✅ |
| L5 | NatronRenderer | ✅ POC |
| L6 | Skia + libass | ✅ / ✅ |
| L7 | GPAC / Bento4 / Shaka | ✅ / ✅ / ✅ |

## Roadmap

### Stage 1: Stabilization (Current)

| Task | Status |
|------|--------|
| JavaCV primary provider | ✅ |
| 6 provider implementations | ✅ |
| Provider auto-registration | ✅ |
| Profile-based routing | ✅ |
| Health checks | ✅ |

### Stage 2: Enhanced Capabilities

| Task | Status | Priority |
|------|--------|----------|
| Multi-track compositing | 📋 Future | High |
| Full subtitle burn-in | ⚠️ Partial | High |
| Complex transitions | 📋 Future | Medium |
| H.265/HEVC encoding | 📋 Future | Medium |
| HDR video support | 📋 Future | Low |

### Stage 3: Distributed Rendering

| Task | Status | Priority |
|------|--------|----------|
| Remote worker GPU acceleration | 📋 Future | High |
| Worker auto-scaling | 📋 Future | Medium |
| Job queue with priority | 📋 Future | Medium |
| Cross-region rendering | 📋 Future | Low |

### Stage 4: Advanced Features

| Task | Status | Priority |
|------|--------|----------|
| OTIO full import/export | 📋 Future | Medium |
| Real-time preview | 📋 Future | Low |
| AI-powered upscaling | 📋 Future | Low |
| Custom effect plugins | 📋 Future | Low |

**VFX / compositing ecosystem (Natron, OFX Host, commercial tools):** see [06-vfx-compositing-ecosystem-selection.md](./06-vfx-compositing-ecosystem-selection.md).

**Shotstack / NatronRenderer / PopcornFX / Bento4:** see [08-pipeline-tools-shotstack-natron-popcornfx-bento4.md](./08-pipeline-tools-shotstack-natron-popcornfx-bento4.md).

Suggested breakdown for custom effects:

| Tier | Engine | Notes |
|------|--------|-------|
| Tier-1 | FFmpeg / MLT filtergraph | Default; maps `effectKey` today |
| Tier-2 | Natron Worker (`NatronRenderer`) | **POC started** — see [07-natron-worker-poc.md](./07-natron-worker-poc.md) |
| Tier-2b | Bento4 (`mp4dash` / `mp4encrypt`) | Packaging/DRM; complements GPAC — **P2 planned** |
| Tier-3 | Shotstack cloud API | Optional template render — **P3 conditional** |
| Tier-3b | Resolve / Nuke / Flame exchange | OTIO/script handoff; TEAM+ delivery |
| Tier-4 | PopcornFX baked assets | Particle overlay via FFmpeg — **P4 conditional** |
| L3 | Remotion Worker | React template branch — **P3** |
| L4 | Blender Worker | 3D intro segments — **P4** |
| L6 | Skia + libass Provider | ASS burn-in / stickers — **P2** |
| L7 | Shaka Packager | DASH/HLS alongside GPAC/Bento4 — **P3** |
| Research | TuttleOFX Host / Sam | Reference only; not recommended for production |

## Architecture Implementation (2026-05-20)

| Component | Package / class | Status |
|-----------|-----------------|--------|
| Pipeline DAG plan | `RenderPlannerService`, `PipelineExecutionPlan` | ✅ |
| Final composer | `FinalComposerSelector` | ✅ |
| Timeline v2 extensions | `TimelineExtensionsReader` | ✅ |
| OTIO Gap/Transition/Marker | `OpenTimelineioAdapter` | ✅ |
| SRT / WebVTT | `SrtSubtitleAdapter`, `WebVttSubtitleAdapter` | ✅ |
| EDL / FCPXML import | `EdlTimelineAdapter`, `FcpXmlTimelineAdapter` | ✅ skeleton |
| Timeline validate | `TimelineValidationService` | ✅ |
| MCP tools | `McpMediaToolsController` | ✅ extended |
| Sample timeline | [examples/timeline-v2-sample.json](./examples/timeline-v2-sample.json) | ✅ |

Full design: [12-server-nle-standards-and-architecture.md](./12-server-nle-standards-and-architecture.md).

## Adding a New Provider

See `03-media-rendering/02-provider-registration.md` for implementation guide.
