# Render Provider Capability Matrix

**Last updated:** 2026-06-12
**Source of truth:** Code — `render-module/.../infrastructure/`

---

## 1. Status Definitions

| Status | Meaning | Dispatch Eligible? | Can Configure for Dispatch? |
|--------|---------|--------------------|-----------------------------|
| **PRODUCTION** | Fully implemented, safe for production | ✅ Yes | N/A |
| **POC** | Implemented as proof-of-concept | ❌ Not by default | ✅ With explicit allow |
| **OPTIONAL** | Implemented but not default | ❌ Not by default | ✅ With explicit enable |
| **STUB** | Interface exists, no real implementation | ❌ Never | ❌ Never |
| **SKELETON** | API client exists, not wired/tested | ❌ Never | ❌ Never |
| **HOLD** | Implementation paused/deferred | ❌ Not by default | ⚠️ Experiment/manual only |
| **SPIKE** | Experimental | ❌ Not by default | ⚠️ Manual only |
| **DEPRECATED** | Superseded by another provider | ❌ Never | ❌ Never |
| **MOCK** | Simulated for testing | ❌ Never in production | ❌ Test/dev only |

**Key rules:**
- `PRODUCTION` is the only status that allows automatic production dispatch
- `POC` requires explicit configuration (`render.providers.allow-poc=true`) or experiment mode
- `STUB`/`SKELETON`/`DEPRECATED`/`MOCK` can never be dispatched regardless of configuration
- `HOLD`/`SPIKE` require experiment or manual mode

---

## 2. Provider Inventory

| Provider | Status | Runtime | @Component | @ConditionalOnProperty | Default Enabled | Dispatch Eligible | Notes |
|----------|--------|---------|-----------|----------------------|-----------------|-------------------|-------|
| **FFmpegRenderProvider** | PRODUCTION | `ffmpeg` binary | ✅ | `render.providers.ffmpeg.enabled=true` | Yes | ✅ | Primary provider. Multi-track, subtitle burn-in, watermark, spatial plan |
| **RemoteRenderProvider** | PRODUCTION | Remote worker HTTP | ✅ `@Component("remote-ffmpeg")` | — | Yes | ✅ | Dispatches to remote worker via HTTP |
| **MockRenderProvider** | MOCK | None (simulated) | ✅ | — | Yes | ❌ | Test/dev only. Always registered as fallback |
| **MltRenderProvider** | POC | `melt` binary | ✅ | `render.providers.mlt.enabled=true` | No | ⚠️ Needs explicit allow | Multi-track NLE. MLT XML → `melt` |
| **GPACRenderProvider** | POC | `MP4Box` binary | ✅ | `render.providers.gpac.enabled=true` | No | ⚠️ Needs explicit allow | DASH/HLS/CMAF packaging |
| **GPACPackagingProvider** | POC | `MP4Box` binary | ✅ | `render.providers.gpac.enabled=true` | No | ⚠️ Needs explicit allow | Packaging-only sub-provider |
| **LibassOverlayRenderProvider** | POC | FFmpeg `ass=` filter | ✅ | `render.providers.libass.enabled=true` (matchIfMissing) | Yes | ⚠️ Needs explicit allow | ASS/SSA subtitle burn-in |
| **SkiaStickerOverlayProvider** | POC | Java2D raster + FFmpeg | ✅ | `render.providers.skia.enabled=true` | No | ⚠️ Needs explicit allow | Sticker overlay only, not general render |
| **Bento4PackagingProvider** | POC | `mp4fragment`/`mp4dash` | ✅ | `render.providers.bento4.enabled=true` | No | ⚠️ Needs explicit allow | Packaging-only |
| **ShakaPackagingProvider** | POC | `shaka-packager` | ✅ | `render.providers.shaka.enabled=true` | No | ⚠️ Needs explicit allow | DASH/HLS packaging. Has stub fallback |
| **GStreamerRenderProvider** | HOLD | `gst-launch-1.0` binary | ✅ | `render.providers.gstreamer.enabled=true` | No | ❌ | Real GStreamer pipeline. HOLD — only real-time/streaming |
| **BlenderRenderProvider** | STUB | `blender` binary | ❌ (Configuration @Bean) | `render.providers.blender.enabled=true` | No | ❌ | No @Component. Writes stub bytes on failure |
| **RemotionRenderProvider** | STUB | `remotion` CLI (Node.js) | ❌ (Configuration @Bean) | `render.providers.remotion.enabled=true` | No | ❌ | No @Component. Writes stub bytes on failure |
| **ShotstackRenderProvider** | SKELETON | Shotstack REST API | ❌ (Configuration @Bean) | `render.providers.shotstack.enabled=true` | No | ❌ | Real API client exists but not wired as @Component |
| **NatronRenderProvider** | SKELETON | `NatronRenderer` binary | ❌ (Configuration @Bean) | `render.providers.natron.enabled=true` | No | ❌ | Currently FFmpeg vignette fallback, not real Natron |
| **VapourSynthRenderProvider** | SKELETON | `vspipe` binary | ❌ (Configuration @Bean) | `render.providers.vapoursynth.enabled=true` | No | ❌ | FFmpeg fallback when vspipe missing |
| **JavaCVRenderProvider** | DEPRECATED | JavaCV/FFmpeg JNI | ✅ `@Deprecated` | — | Yes | ❌ | Superseded by FFmpegRenderProvider |
| **OFXRenderProvider** | DEPRECATED | Java2D simulation | ✅ `@Deprecated` | — | Yes | ❌ | Java2D simulation, NOT real OFX plugin |

---

## 3. Render Capabilities

| Provider | videoTranscode | timelineComposition | multiTrack | subtitleBurnIn | textOverlay | imageOverlay | transition | effectFilter | packaging | remoteExecution | gpuAcceleration | partialRender |
|----------|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| FFmpeg | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | ✅ |
| Remote | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | ✅ | — | ✅ |
| MLT | ✅ | ✅ | ✅ | — | — | — | ✅ | — | — | — | — | — |
| GPAC | — | — | — | — | — | — | — | — | ✅ | — | — | — |
| libass overlay | — | — | — | ✅ | — | — | — | — | — | — | — | — |
| Skia sticker | — | — | — | — | — | ✅ | — | — | — | — | — | — |
| Bento4 | — | — | — | — | — | — | — | — | ✅ | — | — | — |
| Shaka | — | — | — | — | — | — | — | — | ✅ | — | — | — |
| GStreamer | ✅ | — | — | — | — | — | — | — | — | — | — | — |
| Mock | ✅ | — | — | — | — | — | — | — | — | — | — | — |

---

## 4. Font Capabilities

| Provider | fontFile | fallback | opentypeFeatures | variableAxes | cjk | emoji | rtl | shaping | colorFonts |
|----------|:--------:|:--------:|:----------------:|:------------:|:---:|:-----:|:---:|:-------:|:----------:|
| FFmpeg | ✅ | ✅ | — | — | ✅ | — | — | — | — |
| libass overlay | ✅ | ✅ | ✅ | — | ✅ | — | ✅ | ✅ | — |
| Skia sticker | ✅ | ✅ | — | — | ✅ | ✅ | — | — | ✅ |

**Note:** Font subsystem (OTS, fontTools, HarfBuzz, FreeType, Pango) is currently skeleton/noop. The capabilities above reflect what the provider can do when font subsystem is implemented.

---

## 5. Subtitle Capabilities

| Provider | srt | webvtt | ass | burnIn | softSubtitle | styling | positioning | outlineShadow | karaoke | multiLanguage |
|----------|:---:|:------:|:---:|:------:|:------------:|:-------:|:-----------:|:-------------:|:-------:|:-------------:|
| FFmpeg | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — | — |
| libass overlay | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ | ✅ | ✅ |
| Remote | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — | — |

---

## 6. Dispatch Rules

### Automatic Production Dispatch (default)
- Only `PRODUCTION` status providers are eligible
- Must match required capabilities
- Must not be in blocked providers list
- Must not have `notFor` capability conflicts

### Explicit Dispatch (configuration required)
- `POC` providers require:
  - Provider-specific `@ConditionalOnProperty` enabled, AND
  - Job in `experiment`/`manual` mode, OR provider in `preferredProviders`
- `OPTIONAL` providers require explicit enable

### Never Dispatchable
- `STUB` — no real implementation
- `SKELETON` — not wired/production-tested
- `DEPRECATED` — superseded
- `MOCK` — test/dev only

### Mode-Based Override
- `experiment` mode: allows POC + HOLD
- `manual` mode: allows POC + HOLD + SPIKE
- Default mode: only PRODUCTION

### Entitlement Layer
Tenant entitlement (`ProviderAccessPolicy`) further restricts which providers a tenant can use:
- FREE tier: limited providers
- PRO tier: more providers
- TEAM/ENTERPRISE: all implemented providers + GPU + remote worker

---

## 7. Render Farm Readiness

The provider SPI is designed to support future render farm capabilities:

| Concept | Current State | Future Extension |
|---------|---------------|-----------------|
| **Worker capabilities** | `ProviderMetadata.declaredCapabilities` | Worker-level capability registration |
| **Runtime requirements** | `ProviderMetadata.runtime` | Worker runtime availability probing |
| **Dispatch eligibility** | `ProviderEligibility.isEligible()` | Worker lease-based scheduling |
| **Health checks** | `RenderProviderHealthCheck` | Worker heartbeat + health aggregation |
| **Remote execution** | `RemoteRenderProvider` (HTTP dispatch) | Worker pool with load balancing |

**Not implemented in this task:**
- Worker lease protocol
- Worker heartbeat/registry
- Distributed scheduler
- GPU worker management
- Worker capability negotiation

---

## 8. Non-goals

- ❌ Implement BMF runtime
- ❌ Implement real Natron/OpenFX runtime
- ❌ Implement Blender/Remotion runtime
- ❌ GPU/CUDA worker
- ❌ Distributed scheduler
- ❌ Microservice extraction
- ❌ New database schema
- ❌ jOOQ codegen

---

## 9. Code References

| Concept | File |
|---------|------|
| ProviderStatus enum | `render-module/.../infrastructure/ProviderStatus.java` |
| ProviderMetadata record | `render-module/.../infrastructure/ProviderMetadata.java` |
| ProviderEligibility | `render-module/.../infrastructure/ProviderEligibility.java` |
| RenderProvider interface | `render-module/.../infrastructure/RenderProvider.java` |
| RenderProviderRouter | `render-module/.../infrastructure/RenderProviderRouter.java` |
| ProviderAccessPolicy | `entitlement-module/.../ProviderAccessPolicy.java` |
| RenderProviderCapability | `render-module/.../infrastructure/RenderProviderCapability.java` |
