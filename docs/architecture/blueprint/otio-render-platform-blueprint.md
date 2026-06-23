---
status: blueprint
last_verified: 2026-06-23
scope: render-module
truth_level: target
owner: platform
---

# OTIO-first Semantic Video Rendering Platform Blueprint

> **Reality Check (2026-06-23):** Render module has 600 production files, 138 test files. 7+ providers active. OTIO adapter exists (`OpenTimelineioAdapter.java`). Internal Timeline IR with 30+ domain classes. Segment-based incremental planner. Content-addressable render cache. Remotion caption provider with font pipeline. Provider registry with capability negotiation. Worker registry with heartbeat. No BMF integration. No ASR pipeline. No semantic analysis layer.

---

## 1. Executive Summary

### Platform Goal

Build an OTIO-first semantic video rendering platform where:

1. **OpenTimelineIO** serves as the universal external exchange format for timeline data
2. The platform converts OTIO into an internal **Canonical Timeline IR** — a richer, platform-native representation that supports semantic annotations, incremental editing, and multi-provider rendering
3. **Semantic analysis** (ASR, visual understanding, LLM edit intent) enriches the Timeline IR with semantic labels, shot boundaries, speaker segments, and edit proposals
4. The enriched timeline is compiled into an **Artifact Dependency Graph** — a provider-neutral DAG describing intermediate product dependencies, caching, lineage, and invalidation
5. The Artifact Dependency Graph is resolved into a **Logical Render Plan / Capability Graph** — a provider-neutral execution plan with capability requirements
6. **Provider Capability Registry** and **Worker Self-registration** enable dynamic binding of logical nodes to concrete providers and workers
7. The bound plan becomes a **Render Execution Graph** — a provider-bound, worker-assigned execution DAG dispatched to BMF, Remotion, FFmpeg, GPAC, MLT, GStreamer, and other backends

### Core Value Proposition

| Value | Mechanism |
|-------|-----------|
| **Incremental rendering** | Artifact Dependency Graph enables segment-level cache reuse — only re-render changed segments |
| **Multi-provider flexibility** | Capability-based provider binding — swap engines without changing the timeline |
| **Semantic editing** | LLM-generated edit intent → timeline patch → semantic diff → incremental re-render |
| **Cost optimization** | Provider selection based on capability, cost model, and worker availability |
| **Lineage and auditability** | Every artifact has a content hash, provider binding, engine version, and cache key |
| **Caption/text specialization** | Remotion as canonical caption provider with font pipeline, CJK support, animated overlays |

### Why OTIO?

- **Industry standard**: OpenTimelineIO is the de facto standard for timeline interchange across NLE tools (Premiere, Avid, Resolve, Final Cut)
- **Ecosystem**: Existing adapters for AAF, EDL, FCP XML, CMX 3600
- **Extensible**: Custom metadata schema for platform-specific annotations
- **Bidirectional**: Can export back to OTIO for round-trip with external tools

### Why Three Graphs?

A single DAG cannot simultaneously describe:
1. **What to produce** (artifact dependencies — provider-neutral)
2. **How to produce it** (capability requirements — still provider-neutral)
3. **Where to execute it** (provider binding — provider-specific)

Separating these concerns enables:
- Cache invalidation at the artifact level without re-planning execution
- Provider rebinding at execution time without changing the artifact graph
- Incremental rendering by diffing artifact graphs, not execution graphs

---

## 2. Core Architecture

### Layer Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          External Tools                                  │
│  Premiere / Avid / Resolve / Custom NLE                                 │
│              ↕ OTIO (.otio)                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  L1: OTIO Exchange Layer                                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │ OTIO Import/Export│  │ AAF/EDL/FCP XML  │  │ SRT/WebVTT/ASS      │  │
│  │ (OpenTimelineio   │  │ Adapters         │  │ Subtitle Adapters    │  │
│  │  Adapter)         │  │                  │  │                      │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘  │
│           └──────────────┬──────┘────────────────────────┘              │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L2: Canonical Timeline IR                                               │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  TimelineSpec → TimelineTrack[] → TimelineClip[]                 │   │
│  │    ├── TimelineClipEffect[]                                      │   │
│  │    ├── TimelineTransition[]                                      │   │
│  │    ├── TimelineMarker[]                                          │   │
│  │    ├── TimelineSticker / TimelineTextOverlay                     │   │
│  │    └── TimelinePlatformMetadata (semantic labels, edit intent)   │   │
│  │  SubtitleTrack[] → SubtitleCue[]                                 │   │
│  │  TimelineOutputSpec (resolution, codec, delivery targets)        │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L3: Semantic Analysis Layer                                             │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  ASR Segments → Speaker Diarization → Word-level Timing          │   │
│  │  Visual Understanding → Shot Boundary Detection → Scene Labels   │   │
│  │  LLM Edit Intent → Timeline Patch Proposal → Semantic Labels     │   │
│  │  Caption Style Suggestion → Font Selection → Animation Preset    │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L4: Artifact Dependency Graph                                           │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  ArtifactNode[] (provider-neutral)                               │   │
│  │    ├── ArtifactNodeType (MEDIA_SEGMENT, EFFECT_OVERLAY,          │   │
│  │    │     CAPTION_BURN, AUDIO_MIX, PACKAGE, FINAL_COMPOSITE)     │   │
│  │    ├── inputAssetHash, timelineSegmentHash, effectParameterHash │   │
│  │    ├── cacheKey, engineVersion, fontManifest                    │   │
│  │    └── dependencyEdges[] (DAG)                                   │   │
│  │  Invalidation Rules (hash-based, version-based)                 │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L5: Logical Render Plan / Capability Graph                              │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  CapabilityRequirement[] (still provider-neutral)                │   │
│  │    ├── requiredCapabilities (e.g., "video.transcode.h264")       │   │
│  │    ├── fidelityLevel (DRAFT, PREVIEW, PRODUCTION)                │   │
│  │    ├── resourceRequirements (CPU, GPU, memory, time estimate)    │   │
│  │    └── fallbackPolicy (REQUIRED, DEGRADE, SKIP)                  │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L6: Provider Binding Layer                                              │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  Provider Capability Registry (what providers CAN do)            │   │
│  │  Worker Capability Registry (what workers ARE doing)             │   │
│  │  Capability Negotiation Service (match requirements → providers) │   │
│  │  Provider Selection Policy (cost, priority, health, affinity)    │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L7: Render Execution Graph                                              │
│  ┌───────────────────────┴──────────────────────────────────────────┐   │
│  │  ExecutionNode[] (provider-bound, worker-assigned)               │   │
│  │    ├── providerCode, workerId, commandTemplate                   │   │
│  │    ├── inputArtifacts[], outputArtifacts[]                       │   │
│  │    ├── timeout, retryPolicy, resourceAllocation                  │   │
│  │    └── executionStatus, progress, logs                           │   │
│  │  Execution DAG (topological sort, parallel execution)            │   │
│  └───────────────────────┬──────────────────────────────────────────┘   │
├──────────────────────────┼──────────────────────────────────────────────┤
│  L8: Execution Backends                                                  │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐    │
│  │ FFmpeg │ │Remotion│ │  BMF   │ │  GPAC  │ │  MLT   │ │GStreamer│   │
│  │(fallback│ │(caption│ │(GPU/AI │ │(packag-│ │(special│ │(special│    │
│  │ baseline│ │ text,  │ │ media  │ │ ing)   │ │ ty)    │ │ ty)    │    │
│  │ )       │ │motion) │ │pipeline│ │        │ │        │ │        │    │
│  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────┘    │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                          │
│  │Blender │ │ Natron │ │  OFX   │ │Vapour- │                          │
│  │(3D)    │ │(compos-│ │(legacy)│ │ Synth  │                          │
│  │        │ │ ite)   │ │        │ │        │                          │
│  └────────┘ └────────┘ └────────┘ └────────┘                          │
├─────────────────────────────────────────────────────────────────────────┤
│  Cross-Cutting: Artifact Cache / Lineage / Billing / Quota / Delivery   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
OTIO Import
  → Parse to TimelineSpec (Canonical Timeline IR)
    → Semantic Analysis (ASR, vision, LLM)
      → Enriched TimelineSpec (with semantic labels)
        → Compile Artifact Dependency Graph (provider-neutral)
          → Resolve Capability Requirements (provider-neutral)
            → Provider Binding (capability negotiation)
              → Render Execution Graph (provider-bound)
                → Dispatch to Workers
                  → Execute on Backends (FFmpeg, Remotion, BMF, GPAC, etc.)
                    → Produce Artifacts (with lineage)
                      → Cache / Deliver / Bill
```

---

## 3. Graph Model Decision

### Three-Graph Architecture

| Graph | Layer | Provider-Neutral? | Describes | Mutable When? |
|-------|-------|-------------------|-----------|---------------|
| **Artifact Dependency Graph** | L4 | Yes | What to produce (intermediate products and their dependencies) | On timeline change (diff-based) |
| **Logical Capability Graph** | L5 | Yes | How to produce it (capability requirements per node) | On capability model change |
| **Render Execution Graph** | L7 | No (provider-bound) | Where to execute it (provider + worker assignment) | On every render submission |

### Artifact Dependency Graph (L4)

**Purpose:** Describe the intermediate products (artifacts) needed to produce the final output, and their dependency relationships.

**Structure:**
```
ArtifactNode {
  id: ArtifactId
  type: MEDIA_SEGMENT | EFFECT_OVERLAY | CAPTION_BURN | AUDIO_MIX | PACKAGE | FINAL_COMPOSITE
  inputAssetHash: ContentHash
  timelineSegmentHash: ContentHash
  effectParameterHash: ContentHash
  cacheKey: CacheKey
  engineVersion: String
  fontManifest: FontManifestHash
  asrModelVersion: String | null
  visionModelVersion: String | null
  llmModelVersion: String | null
  dependencies: ArtifactId[]  // DAG edges
  invalidationRules: InvalidationRule[]
  reusable: boolean  // can this node be cached?
}
```

**Properties:**
- **Engine-neutral**: Does not reference any specific provider (FFmpeg, Remotion, BMF, etc.)
- **Content-addressable**: Cache key derived from content hashes, not provider identity
- **Incremental**: Diff two artifact graphs to find changed nodes → only re-render those
- **Lineage**: Every artifact traces back to its input hashes, enabling provenance tracking

**Why provider-neutral?** Because the same artifact (e.g., "30-second video segment with color correction") can be produced by FFmpeg, BMF, or GStreamer. The artifact graph should not change when we switch providers.

### Logical Capability Graph (L5)

**Purpose:** Annotate each Artifact Dependency Graph node with the capabilities required to produce it.

**Structure:**
```
CapabilityRequirement {
  artifactNodeId: ArtifactId
  requiredCapabilities: CapabilityCode[]  // e.g., ["video.transcode.h264", "audio.mix.stereo"]
  fidelityLevel: DRAFT | PREVIEW | PRODUCTION
  resourceRequirements: {
    cpuCores: int
    gpuRequired: boolean
    memoryMb: int
    estimatedDurationMs: long
  }
  fallbackPolicy: REQUIRED | DEGRADE | SKIP
  costBudgetCents: long | null  // max cost for this node
}
```

**Properties:**
- **Still provider-neutral**: Describes what is needed, not who does it
- **Enables provider negotiation**: Capability codes are matched against provider published capabilities
- **Enables cost optimization**: Compare provider cost models for the same capability requirement
- **Enables fidelity tiers**: Draft renders use fast/cheap providers; production renders use high-quality providers

### Render Execution Graph (L7)

**Purpose:** Bind each capability requirement to a specific provider and worker, producing a concrete execution plan.

**Structure:**
```
ExecutionNode {
  artifactNodeId: ArtifactId
  capabilityRequirement: CapabilityRequirement
  providerCode: ProviderCode  // e.g., "ffmpeg", "remotion", "bmf"
  workerId: WorkerId | null   // null = not yet assigned
  commandTemplate: CommandTemplate  // provider-specific
  inputArtifacts: ArtifactRef[]
  outputArtifacts: ArtifactRef[]
  timeout: Duration
  retryPolicy: RetryPolicy
  resourceAllocation: ResourceAllocation
  executionStatus: PENDING | RUNNING | COMPLETED | FAILED | CANCELLED
  progress: float
  logs: LogRef
}
```

**Properties:**
- **Provider-bound**: Each node references a specific provider implementation
- **Worker-assigned**: Each node can be assigned to a specific worker instance
- **Executable**: Contains concrete command templates for the bound provider
- **Observable**: Tracks execution status, progress, and logs

### Why Not a Single DAG?

A single DAG mixing artifact dependencies with execution details would:

1. **Cache invalidation becomes provider-coupled** — changing from FFmpeg to BMF would invalidate cache keys even though the output is identical
2. **Provider rebinding requires full re-planning** — cannot swap providers without regenerating the entire graph
3. **Incremental rendering is impossible** — cannot diff two execution graphs to find what changed in the timeline
4. **Lineage tracking is muddied** — artifact provenance is mixed with execution provenance

### Provider Binding Location

Provider binding happens at **Layer 6** — between the Logical Capability Graph (L5) and the Render Execution Graph (L7). The binding process:

1. Takes each CapabilityRequirement from L5
2. Queries the Provider Capability Registry for matching providers
3. Applies the Provider Selection Policy (cost, priority, health, affinity)
4. Queries the Worker Registry for available workers with the selected provider
5. Produces ExecutionNodes with concrete provider and worker assignments

---

## 4. Provider Capability Model

### Provider Capability Registry

```java
public record ProviderCapabilityDescriptor(
    ProviderCode providerCode,
    String displayName,
    ProviderType providerType,  // MEDIA_PIPELINE, COMPOSITION, PACKAGING, OVERLAY, THREE_D, CLOUD, UTILITY
    Set<CapabilityCode> supportedCapabilities,
    Set<CapabilityCode> publishedCapabilities,     // advertised to capability negotiation
    Set<CapabilityCode> experimentalCapabilities,  // opt-in only
    Set<CapabilityCode> deprecatedCapabilities,    // will be removed
    InputOutputContract inputOutputContract,
    FidelityLevel maxFidelity,
    CacheCompatibility cacheCompatibility,
    CostModel costModel,
    ResourceRequirements resourceRequirements,
    FallbackPolicy fallbackPolicy,
    String engineVersion,
    Instant lastHealthCheck
)
```

### Capability Codes

| Code | Description | Primary Provider |
|------|-------------|-----------------|
| `video.transcode.h264` | H.264 video transcoding | FFmpeg, BMF, GStreamer |
| `video.transcode.h265` | H.265 video transcoding | FFmpeg, BMF |
| `video.transcode.vp9` | VP9 transcoding | FFmpeg |
| `video.effect.color` | Color correction/grading | FFmpeg, BMF, MLT |
| `video.effect.overlay` | Video overlay/compositing | FFmpeg, BMF, Natron |
| `video.effect.motion` | Motion graphics | Remotion, Blender |
| `video.3d.render` | 3D rendering | Blender |
| `audio.mix.stereo` | Stereo audio mixing | FFmpeg, BMF |
| `audio.mix.surround` | Surround audio mixing | FFmpeg |
| `audio.transcode` | Audio transcoding | FFmpeg, BMF |
| `caption.burn` | Caption/subtitle burn-in | Remotion, FFmpeg (libass), BMF |
| `caption.animated` | Animated captions | Remotion |
| `caption.text.overlay` | Text overlay with styling | Remotion, Skia |
| `package.mp4` | MP4 packaging | GPAC, FFmpeg, Bento4 |
| `package.hls` | HLS packaging | GPAC, Shaka |
| `package.dash` | DASH packaging | GPAC, Shaka |
| `composite.final` | Final composition | FFmpeg, BMF |
| `preprocess.probe` | Media probing | FFmpeg |
| `preprocess.screenshot` | Frame extraction | FFmpeg |

### Provider Capability Table

| Provider | Type | Primary Capabilities | Fidelity | Cost | GPU |
|----------|------|---------------------|----------|------|-----|
| **FFmpeg** | MEDIA_PIPELINE | transcode, effect, audio, probe, fallback | PRODUCTION | Low | Optional (VAAPI/NVENC) |
| **Remotion** | COMPOSITION | caption.burn, caption.animated, caption.text.overlay, motion | PRODUCTION | Medium | No |
| **BMF** | MEDIA_PIPELINE | transcode, effect, audio, GPU inference | PRODUCTION | Medium | Yes |
| **GPAC** | PACKAGING | package.mp4, package.hls, package.dash | PRODUCTION | Low | No |
| **MLT** | MEDIA_PIPELINE | effect.color, effect.overlay | PREVIEW | Low | No |
| **GStreamer** | MEDIA_PIPELINE | transcode, effect | PREVIEW | Low | Optional |
| **Blender** | THREE_D | 3d.render | PRODUCTION | High | Yes |
| **Natron** | COMPOSITION | effect.overlay, composite | PREVIEW | Medium | No |
| **OFX** | UTILITY | effect.overlay (legacy) | DRAFT | Low | No |
| **VapourSynth** | UTILITY | effect.overlay (scripted) | PREVIEW | Low | No |
| **Bento4** | PACKAGING | package.mp4 | PRODUCTION | Low | No |
| **Shaka** | PACKAGING | package.hls, package.dash | PRODUCTION | Low | No |
| **Shotstack** | CLOUD | transcode, effect | PRODUCTION | High (API) | N/A |
| **Skia** | OVERLAY | caption.text.overlay | PRODUCTION | Low | No |
| **Libass** | OVERLAY | caption.burn (ASS/SRT) | PRODUCTION | Low | No |

### Provider Roles (Specialization)

| Role | Primary Provider | Rationale |
|------|-----------------|-----------|
| **Caption/Text Canonical** | Remotion | React-based composition, font pipeline, animated captions, transparent overlay |
| **Media Pipeline / GPU / AI** | BMF | C++ graph engine, GPU acceleration, AI inference nodes |
| **Packaging** | GPAC | Industry-standard MP4/HLS/DASH packaging |
| **Fallback Baseline** | FFmpeg | Universal compatibility, always available, debug-friendly |
| **3D Rendering** | Blender | Full 3D pipeline via Python API |
| **Compositing** | Natron | OFX-based node compositing (POC stage) |
| **Timeline Editing** | MLT | NLE-style timeline editing with effects |
| **Streaming Pipeline** | GStreamer | Real-time pipeline construction |
| **Text Overlay** | Skia | High-quality 2D graphics rendering |
| **Subtitle Burn-in** | Libass | Fast ASS/SRT subtitle rendering |

### Input/Output Contract

```java
public record InputOutputContract(
    Set<MimeType> acceptedVideoCodecs,
    Set<MimeType> acceptedAudioCodecs,
    Set<MimeType> acceptedSubtitleFormats,
    Set<String> acceptedContainerFormats,
    Set<MimeType> producedVideoCodecs,
    Set<MimeType> producedAudioCodecs,
    Set<String> producedContainerFormats,
    int maxResolutionWidth,
    int maxResolutionHeight,
    double maxFrameRate,
    long maxDurationSeconds
)
```

### Cache Compatibility

```java
public record CacheCompatibility(
    boolean outputIsContentAddressable,  // can output be cached by content hash?
    boolean supportsIncrementalInput,    // can provider accept partial input?
    Set<ProviderCode> cacheCompatibleWith,  // can reuse cache from these providers?
    String outputContractHash            // hash of output contract for cache key
)
```

### Cost Model

```java
public record CostModel(
    CostBasis basis,  // PER_SECOND, PER_FRAME, PER_JOB, PER_API_CALL
    long baseCostCents,
    long perSecondCostCents,
    long perFrameCostCents,
    long gpuSurchargeCents,
    Currency currency,
    Instant effectiveFrom,
    Instant effectiveUntil
)
```

---

## 5. Worker Self-registration

### Worker Registration Protocol

Workers self-register with the Render Farm Worker Registry on startup and maintain registration via heartbeat.

```java
public record WorkerRegistration(
    WorkerId workerId,
    String workerName,
    WorkerType workerType,  // RENDER_FARM, SANDBOX, LOCAL
    Set<ProviderCode> supportedProviders,
    RuntimeCapabilities runtimeCapabilities,
    ResourceProfile resourceProfile,
    HealthStatus healthStatus,
    Instant lastHeartbeat,
    WorkerVersion version,
    FontAvailability fontAvailability,
    EngineVersions engineVersions,
    String region,
    int maxConcurrency,
    int currentConcurrency,
    Map<String, String> labels  // arbitrary labels for affinity matching
)
```

### Runtime Capabilities

```java
public record RuntimeCapabilities(
    boolean gpuAvailable,
    String gpuModel,           // e.g., "NVIDIA A100", "Apple M2"
    int gpuMemoryMb,
    int cpuCores,
    int totalMemoryMb,
    long diskFreeMb,
    String osName,
    String osVersion,
    String architecture,       // amd64, arm64
    boolean containerized,
    Map<String, String> environmentVars  // sanitized, no secrets
)
```

### Font Availability

```java
public record FontAvailability(
    boolean fontManifestLoaded,
    Set<String> availableFontFamilies,
    Set<String> availableLanguages,  // ISO 639-1 codes
    boolean cjkSupported,
    boolean rtlSupported,
    Instant lastFontScanAt
)
```

### Engine Versions

```java
public record EngineVersions(
    String ffmpegVersion,
    String remotionVersion,
    String bmfVersion,
    String gpacVersion,
    String mltVersion,
    String gstreamerVersion,
    String blenderVersion,
    String natronVersion,
    Map<String, String> additionalVersions
)
```

### Worker Lifecycle

```
STARTUP
  → REGISTER (with capabilities, supported providers, resource profile)
    → READY (heartbeat every 30s)
      → CLAIM (accept work from queue)
        → EXECUTE (run render job)
          → COMPLETE / FAIL (report result)
            → READY (available for next job)
      → DRAIN (accept no new work, finish current)
        → SHUTDOWN
```

### Existing Implementation

The render module already has:
- `RenderWorkerRegistration` — worker registration record
- `RenderWorkerRecord` — persistent worker state
- `RenderWorkerRegistryService` — registration and lookup
- `RenderFarmWorkerController` — REST API for worker lifecycle (register, claim, complete, fail, heartbeat)
- `WorkerRegisterRequest/Response` — API DTOs
- `WorkerHeartbeatRequest`, `WorkerClaimRequest`, `WorkerCompleteRequest`, `WorkerFailRequest` — lifecycle DTOs

---

## 6. Cache and Artifact Lineage

### Cache Key Structure

```java
public record CacheKey(
    ArtifactId artifactId,
    ContentHash inputAssetHash,         // hash of input media file
    ContentHash timelineSegmentHash,    // hash of timeline segment specification
    ContentHash effectParameterHash,    // hash of effect parameters
    ContentHash providerOutputContract, // hash of output format specification
    String engineVersion,               // e.g., "ffmpeg-6.1.2"
    ContentHash fontManifestHash,       // hash of font manifest
    String asrModelVersion,             // e.g., "whisper-v3" or null
    String visionModelVersion,          // e.g., "gemini-pro-vision" or null
    String llmModelVersion              // e.g., "gpt-4o" or null
)
```

### Cache Key Derivation

```
cacheKey = SHA-256(
  artifactId + "|" +
  inputAssetHash + "|" +
  timelineSegmentHash + "|" +
  effectParameterHash + "|" +
  providerOutputContract + "|" +
  engineVersion + "|" +
  fontManifestHash + "|" +
  asrModelVersion + "|" +
  visionModelVersion + "|" +
  llmModelVersion
)
```

### Invalidation Rules

| Trigger | Invalidation Scope | Rationale |
|---------|-------------------|-----------|
| Input asset changed | All downstream nodes | Source material changed |
| Timeline segment changed | Segment + downstream | Edit changed |
| Effect parameters changed | Effect node + downstream | Effect changed |
| Engine version changed | All nodes using that engine | Bug fix or behavior change |
| Font manifest changed | Caption nodes + downstream | Font changed |
| ASR model changed | ASR-dependent caption nodes | Transcription changed |
| Vision model changed | Vision-dependent nodes | Scene detection changed |
| LLM model changed | LLM-dependent nodes | Edit suggestions changed |

### Reusable vs Non-reusable Nodes

| Node Type | Reusable? | Rationale |
|-----------|-----------|-----------|
| `MEDIA_SEGMENT` | Yes | Stable intermediate product |
| `EFFECT_OVERLAY` | Yes | Can be cached if parameters unchanged |
| `CAPTION_BURN` | Yes | Can be cached if text/font/timing unchanged |
| `AUDIO_MIX` | Yes | Can be cached if audio sources unchanged |
| `PACKAGE` | No | Always regenerated from final segments |
| `FINAL_COMPOSITE` | No | Always regenerated from all inputs |

### Existing Implementation

The render module already has:
- `RenderCacheContentHasher` — content hash computation
- `RenderCacheReuseValidator` — cache reuse validation
- `RenderCachePresignService` — S3 presigned URL for cache entries
- `RenderCacheCleanupService` — cache eviction
- `RenderCacheTenantGuard` — tenant-scoped cache isolation
- `SegmentCachePublisher` — publish segment to cache
- `MezzanineCachePublisher` — publish mezzanine to cache
- `ArtifactCache` (in renderplan) — in-memory artifact cache
- `ReusableArtifact` (in timeline/internal) — reusable artifact model
- `RenderCacheProperties` — cache configuration

---

## 7. Subtitle / Caption Architecture

### Why Remotion is the Caption/Text Canonical Provider

| Capability | Remotion | FFmpeg (libass) | BMF |
|-----------|----------|-----------------|-----|
| Word-level timing | ✅ React component per word | ❌ Line-level only | ❌ Line-level only |
| Animated captions | ✅ CSS/Framer Motion | ❌ Static overlay | ❌ Static overlay |
| Style presets | ✅ React props | ⚠️ ASS styles | ⚠️ Filter parameters |
| Font pipeline | ✅ FontManifest integration | ⚠️ System fonts | ⚠️ System fonts |
| CJK line breaking | ✅ React text layout | ⚠️ Basic | ⚠️ Basic |
| Safe area | ✅ Canvas-aware | ❌ Not aware | ❌ Not aware |
| Transparent overlay | ✅ RGBA PNG/WebM | ⚠️ Limited | ⚠️ Limited |
| Motion graphics | ✅ Full React | ❌ Not possible | ⚠️ Limited |
| Preview rendering | ✅ Instant in browser | ❌ Requires full render | ❌ Requires full render |

### Caption Pipeline

```
ASR Segments (from AI module)
  → Speaker Diarization (identify speakers)
    → Word-level Timing (align words to audio)
      → Style Preset Selection (from user preference or LLM suggestion)
        → Font Manifest Resolution (from FontManifest)
          → CJK Line Breaking (language-aware)
            → Safe Area Calculation (from SpatialCoordinateConverter)
              → Remotion Composition
                → Transparent Overlay (RGBA PNG or WebM)
                  → Compositing with Video (FFmpeg or BMF)
```

### Existing Implementation

The render module already has:
- `AutoCaptionsService` — ASR-to-caption pipeline
- `AutoCaptionsController` — REST API for auto-captions
- `SubtitleCue`, `SubtitleTrack`, `SubtitleFont` — domain models
- `SrtSubtitleAdapter`, `WebVttSubtitleAdapter` — format adapters
- `RemotionCaption`, `RemotionCaptionStyle`, `RemotionCaptionWord` — Remotion caption models
- `RemotionFontSpec` — font specification for Remotion
- `SubtitleBurnInService` — subtitle burn-in orchestration
- `SubtitleRenderService` — subtitle render orchestration
- `SubtitleBurnInNode` (LiteFlow) — policy-based subtitle routing
- `LibassSubtitleCompositor` — FFmpeg/libass fallback
- Full font pipeline (30+ files): manifest, security scanning, validation, subsetting, coverage checking

### Missing: ASR Pipeline

No ASR files exist in the render module. ASR integration requires:
1. AI module ASR provider (Whisper, etc.)
2. Speaker diarization service
3. Word-level timing alignment
4. Integration with `AutoCaptionsService`

---

## 8. BMF Integration Strategy

### BMF Positioning

**BMF should handle:**
- GPU-accelerated video transcoding (H.264, H.265)
- GPU-accelerated effects (color correction, denoise, super-resolution)
- AI inference nodes (object detection, scene classification, content-aware crop)
- Complex filter graphs (multi-input, multi-output)
- Real-time preview generation (low-latency pipeline)

**BMF should NOT handle:**
- Caption/text rendering (use Remotion)
- 3D rendering (use Blender)
- Packaging (use GPAC)
- Simple transcoding (use FFmpeg — lower overhead)
- Subtitle burn-in (use Remotion for rich, libass for simple)

### Logical Render Plan → BMF Graph

```
Logical Render Plan
  → Identify BMF-capable nodes (via CapabilityCode matching)
    → Group adjacent BMF-capable nodes into BMF subgraphs
      → For each subgraph:
        → Generate BMF graph JSON (nodes, edges, parameters)
        → Generate BMF input/output mapping
        → Generate BMF configuration (GPU, threading, memory)
      → Non-BMF nodes remain as separate execution steps
```

### Java/Spring → BMF Integration

| Approach | Pros | Cons |
|----------|------|------|
| **CLI subprocess** | Simple, isolated, no JNI | Process overhead, no streaming |
| **gRPC service** | Streaming, language-agnostic, scalable | Network overhead, service management |
| **Python subprocess** | Direct BMF Python API | Python dependency, GIL limitations |
| **BMF C++ library via JNI** | Zero-copy, lowest latency | Complex build, crash risk |

**Recommended:** Start with **CLI subprocess** for M5 spike, then migrate to **gRPC service** for production.

### Logging, Failure, Artifact Return

```java
public record BMFExecutionResult(
    BMFGraphId graphId,
    ExecutionStatus status,  // SUCCESS, FAILURE, TIMEOUT, OOM
    Map<String, ArtifactRef> outputArtifacts,
    Duration executionTime,
    BMFResourceUsage resourceUsage,  // GPU memory, CPU time
    String stdoutLog,
    String stderrLog,
    BMFErrorDetails errorDetails  // error code, message, stack trace
)
```

### First Phase Spike (M5)

**Scope:**
1. Define BMF graph JSON schema (Java record)
2. Implement CLI subprocess executor
3. Generate BMF graph from a simple 2-node plan (transcode + overlay)
4. Execute and capture output artifacts
5. Validate output with FFmpeg probe

**Non-goals:**
- GPU acceleration (CPU-only for spike)
- Streaming output
- AI inference nodes
- Complex filter graphs
- Production error handling

---

## 9. LLM Role

### What LLM Generates

| Output | Description | Used By |
|--------|-------------|---------|
| **Edit intent** | Natural language description of desired edit | Timeline Patch Service |
| **Timeline patch proposal** | Structured JSON patch (add/remove/modify clips, effects, transitions) | Timeline Patch Service |
| **Semantic labels** | Scene labels, mood tags, content classification | Timeline Metadata |
| **Caption style suggestions** | Font, size, color, animation based on content | Remotion Caption |
| **Shot selection suggestions** | Best takes, highlight reels, content-aware selection | Segment Timeline Planner |

### What LLM Does NOT Generate

| Output | Why Not | Generated By |
|--------|---------|-------------|
| FFmpeg command | Too complex, too many edge cases, needs codec expertise | `FFmpegCommandFactory` |
| BMF graph | Requires understanding of BMF node API, GPU topology | `BMFGraphCompiler` (future) |
| Worker execution command | Requires understanding of worker capabilities, resource allocation | `WorkerScheduler` |
| Production deployment config | Security-sensitive, requires infrastructure knowledge | `DeploymentConfig` |

### LLM Integration Pattern

```
User Edit Request
  → LLM generates edit intent (natural language)
    → LLM generates timeline patch proposal (structured JSON)
      → Semantic diff (compare current vs proposed)
        → Validate patch (schema, bounds, resource feasibility)
          → Apply patch to Timeline IR
            → Recompile Artifact Dependency Graph (incremental)
              → Re-render affected segments only
```

### Existing Implementation

The render module already has:
- `AiTimelineEditService` — LLM-powered timeline editing
- `AiTimelineProposalService` — LLM proposal generation
- `AiTimelineEditContext` — context for LLM editing
- `AiTimelineEditResponseParser` — parse LLM responses
- `TimelinePatchService` — apply patches to timeline
- `TimelineSemanticDiffService` — semantic diff between timelines

---

## 10. Milestones

### M0: Architecture ADRs

| Attribute | Value |
|-----------|-------|
| **Goal** | Establish architectural decisions for OTIO-first rendering platform |
| **Scope** | 3 ADRs: OTIO as exchange format, Three-graph model, Provider capability model |
| **Non-goals** | No code changes, no new modules |
| **Files affected** | `docs/render/adr/ADR-008-otio-exchange-layer.md`, `ADR-009-three-graph-model.md`, `ADR-010-provider-capability-model.md` |
| **Tests** | None (documentation only) |
| **Risks** | Low — documentation only |
| **Review checklist** | [ ] ADR format correct [ ] Decision rationale documented [ ] Consequences documented [ ] Alternatives considered |

### M1: OTIO + Timeline IR Enhancement

| Attribute | Value |
|-----------|-------|
| **Goal** | Enhance Timeline IR to support semantic annotations and improve OTIO round-trip fidelity |
| **Scope** | Extend `TimelineSpec`, `TimelineClip`, `TimelinePlatformMetadata` with semantic fields. Enhance `OpenTimelineioAdapter` for bidirectional OTIO support. |
| **Non-goals** | No new providers, no execution changes, no BMF |
| **Files affected** | `render-module/domain/timeline/*`, `render-module/infrastructure/otio/*`, `render-module/app/timeline/*` |
| **Tests** | OTIO round-trip tests, Timeline IR serialization tests, semantic annotation tests |
| **Risks** | Medium — Timeline IR changes affect many downstream consumers |
| **Review checklist** | [ ] ModularityTest passes [ ] OTIO round-trip fidelity [ ] Timeline IR backward compatible [ ] No breaking API changes |

### M2: Artifact Dependency Graph

| Attribute | Value |
|-----------|-------|
| **Goal** | Implement Artifact Dependency Graph compilation from Timeline IR |
| **Scope** | New `ArtifactGraphCompiler` service. Extend existing `ArtifactGraph` and `ArtifactNode` domain classes. Content-addressable cache key derivation. |
| **Non-goals** | No provider binding, no execution, no BMF |
| **Files affected** | `render-module/domain/artifact/*`, `render-module/app/planner/*`, new `ArtifactGraphCompiler.java` |
| **Tests** | Graph compilation tests, cache key derivation tests, incremental diff tests |
| **Risks** | Medium — new domain model, must integrate with existing `RenderPlan` |
| **Review checklist** | [ ] ModularityTest passes [ ] Graph is provider-neutral [ ] Cache keys are content-addressable [ ] Incremental diff works [ ] Backward compatible with existing RenderPlan |

### M3: Provider Capability + Worker Registry Enhancement

| Attribute | Value |
|-----------|-------|
| **Goal** | Formalize Provider Capability Registry and enhance Worker Self-registration |
| **Scope** | New `ProviderCapabilityDescriptor` model. Enhance `RenderWorkerRegistration` with engine versions, font availability. Implement `CapabilityNegotiationService`. |
| **Non-goals** | No new providers, no BMF, no execution changes |
| **Files affected** | `render-module/infrastructure/providerruntime/capability/*`, `render-module/infrastructure/farm/*` |
| **Tests** | Capability negotiation tests, worker registration tests, health check tests |
| **Risks** | Low — extending existing infrastructure |
| **Review checklist** | [ ] ModularityTest passes [ ] All providers have capability descriptors [ ] Worker registration includes engine versions [ ] Capability negotiation produces correct bindings |

### M4: Remotion Caption Provider Enhancement

| Attribute | Value |
|-----------|-------|
| **Goal** | Enhance Remotion as the canonical caption/text provider with word-level timing, animated captions, and transparent overlay |
| **Scope** | Enhance `RemotionRenderProvider` for caption-specific workflows. Implement word-level timing from ASR segments. Add animated caption presets. Implement transparent overlay output. |
| **Non-goals** | No ASR pipeline (use mock data), no BMF, no new providers |
| **Files affected** | `render-module/infrastructure/remotion/*`, `render-module/app/autocaptions/*`, `render-module/infrastructure/subtitle/*` |
| **Tests** | Caption rendering tests, word-level timing tests, animation preset tests, transparent overlay tests |
| **Risks** | Medium — Remotion CLI dependency, font pipeline complexity |
| **Review checklist** | [ ] ModularityTest passes [ ] Word-level timing works [ ] Animated captions render correctly [ ] Transparent overlay composites with video [ ] CJK line breaking works [ ] Font pipeline integration verified |

### M5: BMF Execution Backend Spike

| Attribute | Value |
|-----------|-------|
| **Goal** | Prove BMF can execute a simple 2-node render plan via CLI subprocess |
| **Scope** | BMF graph JSON schema. CLI subprocess executor. Simple transcode + overlay test. Output artifact capture. |
| **Non-goals** | No GPU acceleration, no streaming, no AI inference, no production use |
| **Files affected** | New `render-module/infrastructure/bmf/*` (5-10 files) |
| **Tests** | BMF CLI execution test, output validation with FFmpeg probe, error handling test |
| **Risks** | High — BMF Python dependency, subprocess management, output capture |
| **Review checklist** | [ ] BMF graph JSON schema documented [ ] CLI execution succeeds [ ] Output artifacts captured correctly [ ] Error handling works [ ] No BMF dependency in platform-app runtime |

### M6: Cache-aware Multi-provider Render Execution

| Attribute | Value |
|-----------|-------|
| **Goal** | End-to-end: OTIO → Timeline IR → Artifact Graph → Capability Graph → Provider Binding → Execution Graph → Multi-provider execution with cache reuse |
| **Scope** | Integrate all layers. Implement cache-aware execution. Multi-provider rendering (FFmpeg + Remotion + GPAC). Incremental re-render on timeline change. |
| **Non-goals** | No BMF in production (use FFmpeg as fallback), no ASR pipeline, no LLM integration |
| **Files affected** | `render-module/app/planner/*`, `render-module/infrastructure/renderplan/*`, `render-module/infrastructure/providerruntime/*` |
| **Tests** | End-to-end render test, cache reuse test, incremental re-render test, multi-provider test, failure recovery test |
| **Risks** | High — integration complexity, cache consistency, provider failure handling |
| **Review checklist** | [ ] ModularityTest passes [ ] End-to-end render completes [ ] Cache reuse works [ ] Incremental re-render only re-renders changed segments [ ] Multi-provider execution works [ ] Provider failure triggers fallback [ ] Billing/quota integration verified |

---

## 11. Multi-Agent Task Decomposition

### Task T001: OTIO Exchange Layer ADR

| Attribute | Value |
|-----------|-------|
| **Task ID** | T001 |
| **Title** | Write ADR-008: OTIO as Primary Exchange Format |
| **Target module** | render-module (documentation only) |
| **Allowed files** | `docs/render/adr/ADR-008-otio-exchange-layer.md` |
| **Forbidden files** | All `.java` files |
| **Output** | ADR document |
| **Acceptance criteria** | ADR format correct, decision rationale documented, alternatives considered, consequences listed |
| **Test command** | N/A |
| **Risk level** | Low |
| **Dependencies** | None |
| **Suggested agent type** | Architect |

### Task T002: Three-Graph Model ADR

| Attribute | Value |
|-----------|-------|
| **Task ID** | T002 |
| **Title** | Write ADR-009: Three-Graph Model (Artifact, Capability, Execution) |
| **Target module** | render-module (documentation only) |
| **Allowed files** | `docs/render/adr/ADR-009-three-graph-model.md` |
| **Forbidden files** | All `.java` files |
| **Output** | ADR document |
| **Acceptance criteria** | Three graphs clearly distinguished, responsibilities defined, data structures specified, provider neutrality explained |
| **Test command** | N/A |
| **Risk level** | Low |
| **Dependencies** | T001 |
| **Suggested agent type** | Architect |

### Task T003: Provider Capability Model ADR

| Attribute | Value |
|-----------|-------|
| **Task ID** | T003 |
| **Title** | Write ADR-010: Provider Capability Model |
| **Target module** | render-module (documentation only) |
| **Allowed files** | `docs/render/adr/ADR-010-provider-capability-model.md` |
| **Forbidden files** | All `.java` files |
| **Output** | ADR document |
| **Acceptance criteria** | Capability codes defined, provider roles documented, cost model specified, fallback policy documented |
| **Test command** | N/A |
| **Risk level** | Low |
| **Dependencies** | T002 |
| **Suggested agent type** | Architect |

### Task T004: Timeline IR Semantic Extension

| Attribute | Value |
|-----------|-------|
| **Task ID** | T004 |
| **Title** | Extend Timeline IR with semantic annotation fields |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/domain/timeline/*.java`, `render-module/src/main/java/com/example/platform/render/domain/timeline/internal/*.java` |
| **Forbidden files** | `render-module/src/main/java/com/example/platform/render/infrastructure/*.java`, `platform-app/**` |
| **Output** | Extended Timeline IR records with semantic fields |
| **Acceptance criteria** | `TimelinePlatformMetadata` supports semantic labels, edit intent, speaker segments. All existing tests pass. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test` |
| **Risk level** | Medium |
| **Dependencies** | T001 |
| **Suggested agent type** | Coder |

### Task T005: OTIO Round-trip Enhancement

| Attribute | Value |
|-----------|-------|
| **Task ID** | T005 |
| **Title** | Enhance OpenTimelineioAdapter for bidirectional OTIO support |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/domain/timeline/OpenTimelineioAdapter.java`, `render-module/src/main/java/com/example/platform/render/infrastructure/otio/*` |
| **Forbidden files** | `platform-app/**`, `render-module/src/main/java/com/example/platform/render/infrastructure/remotion/*` |
| **Output** | Enhanced OTIO adapter with import + export + round-trip validation |
| **Acceptance criteria** | OTIO import produces correct TimelineSpec. TimelineSpec export produces valid OTIO. Round-trip fidelity ≥ 95%. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*OTIO*'` |
| **Risk level** | Medium |
| **Dependencies** | T004 |
| **Suggested agent type** | Coder |

### Task T006: Artifact Graph Compiler

| Attribute | Value |
|-----------|-------|
| **Task ID** | T006 |
| **Title** | Implement ArtifactGraphCompiler: Timeline IR → Artifact Dependency Graph |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/domain/artifact/*`, `render-module/src/main/java/com/example/platform/render/app/planner/ArtifactGraphCompiler.java` (new) |
| **Forbidden files** | `render-module/src/main/java/com/example/platform/render/infrastructure/*`, `platform-app/**` |
| **Output** | ArtifactGraphCompiler service + tests |
| **Acceptance criteria** | Timeline IR compiles to ArtifactDependencyGraph. Graph is provider-neutral. Cache keys are content-addressable. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*ArtifactGraph*'` |
| **Risk level** | Medium |
| **Dependencies** | T004 |
| **Suggested agent type** | Coder |

### Task T007: Provider Capability Descriptor Model

| Attribute | Value |
|-----------|-------|
| **Task ID** | T007 |
| **Title** | Implement ProviderCapabilityDescriptor and CapabilityCode records |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/infrastructure/providerruntime/capability/*`, `render-module/src/main/java/com/example/platform/render/infrastructure/RenderProviderCapability.java` |
| **Forbidden files** | `platform-app/**`, `render-module/src/main/java/com/example/platform/render/app/*` |
| **Output** | ProviderCapabilityDescriptor, CapabilityCode, InputOutputContract, CostModel records |
| **Acceptance criteria** | All records compile. All existing providers can describe their capabilities. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test` |
| **Risk level** | Low |
| **Dependencies** | T003 |
| **Suggested agent type** | Coder |

### Task T008: Capability Negotiation Service

| Attribute | Value |
|-----------|-------|
| **Task ID** | T008 |
| **Title** | Implement CapabilityNegotiationService: match requirements to providers |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/infrastructure/providerruntime/capability/*` |
| **Forbidden files** | `platform-app/**` |
| **Output** | CapabilityNegotiationService + tests |
| **Acceptance criteria** | Given capability requirements, returns ranked list of matching providers. Respects fallback policy. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*CapabilityNegotiation*'` |
| **Risk level** | Low |
| **Dependencies** | T007 |
| **Suggested agent type** | Coder |

### Task T009: Worker Registration Enhancement

| Attribute | Value |
|-----------|-------|
| **Task ID** | T009 |
| **Title** | Enhance worker registration with engine versions, font availability, runtime capabilities |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/infrastructure/farm/*` |
| **Forbidden files** | `platform-app/**`, `render-module/src/main/java/com/example/platform/render/app/*` |
| **Output** | Enhanced WorkerRegistration, RuntimeCapabilities, FontAvailability, EngineVersions records |
| **Acceptance criteria** | Worker registration includes engine versions, font availability, GPU info. Existing farm tests pass. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*Worker*'` |
| **Risk level** | Low |
| **Dependencies** | None |
| **Suggested agent type** | Coder |

### Task T010: Cache Key Derivation Service

| Attribute | Value |
|-----------|-------|
| **Task ID** | T010 |
| **Title** | Implement CacheKeyDerivationService for artifact cache keys |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/app/cache/*`, `render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/ArtifactCache.java` |
| **Forbidden files** | `platform-app/**` |
| **Output** | CacheKeyDerivationService + tests |
| **Acceptance criteria** | Cache keys are content-addressable (SHA-256 of input hash + segment hash + effect hash + engine version + font manifest). Invalidation rules work. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*CacheKey*'` |
| **Risk level** | Low |
| **Dependencies** | T006 |
| **Suggested agent type** | Coder |

### Task T011: Remotion Caption Word-level Timing

| Attribute | Value |
|-----------|-------|
| **Task ID** | T011 |
| **Title** | Implement word-level caption timing in Remotion provider |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/infrastructure/remotion/*`, `render-module/src/main/java/com/example/platform/render/app/autocaptions/*` |
| **Forbidden files** | `platform-app/**`, `render-module/src/main/java/com/example/platform/render/infrastructure/ffmpeg/*` |
| **Output** | Word-level timing support in RemotionCaption, animated caption presets |
| **Acceptance criteria** | Each word has start/end time. Animated captions render correctly. CJK line breaking works. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*Remotion*'` |
| **Risk level** | Medium |
| **Dependencies** | T004 |
| **Suggested agent type** | Coder |

### Task T012: BMF Graph Schema and CLI Spike

| Attribute | Value |
|-----------|-------|
| **Task ID** | T012 |
| **Title** | BMF graph JSON schema + CLI subprocess executor (spike) |
| **Target module** | render-module |
| **Allowed files** | `render-module/src/main/java/com/example/platform/render/infrastructure/bmf/*` (new directory, 5-10 files) |
| **Forbidden files** | `platform-app/**`, `render-module/src/main/java/com/example/platform/render/app/*` |
| **Output** | BMF graph JSON schema, CLI executor, simple 2-node test |
| **Acceptance criteria** | BMF graph JSON compiles. CLI executor runs BMF. Output artifacts captured. Error handling works. ModularityTest passes. |
| **Test command** | `./gradlew :render-module:test --tests '*BMF*'` |
| **Risk level** | High |
| **Dependencies** | None |
| **Suggested agent type** | Coder |

### Task T013: Provider Capability Inventory

| Attribute | Value |
|-----------|-------|
| **Task ID** | T013 |
| **Title** | Document capability inventory for all existing providers |
| **Target module** | render-module (documentation + metadata) |
| **Allowed files** | `docs/render/provider-capability-inventory.md` (new), `render-module/src/main/java/com/example/platform/render/infrastructure/ProviderMetadata.java` |
| **Forbidden files** | `platform-app/**` |
| **Output** | Capability inventory document + ProviderMetadata enhancement |
| **Acceptance criteria** | All 15+ providers documented with capabilities, fidelity, cost, GPU requirements. ModularityTest passes. |
| **Test command** | N/A |
| **Risk level** | Low |
| **Dependencies** | T007 |
| **Suggested agent type** | Architect + Coder |

### Task T014: ASR Integration Design

| Attribute | Value |
|-----------|-------|
| **Task ID** | T014 |
| **Title** | Design ASR pipeline integration (Whisper → speaker diarization → word timing) |
| **Target module** | ai-module + render-module (design only) |
| **Allowed files** | `docs/render/asr-integration-design.md` (new) |
| **Forbidden files** | All `.java` files |
| **Output** | ASR integration design document |
| **Acceptance criteria** | ASR pipeline architecture defined, speaker diarization approach documented, word-level timing alignment specified, integration with AutoCaptionsService documented |
| **Test command** | N/A |
| **Risk level** | Low |
| **Dependencies** | T001 |
| **Suggested agent type** | Architect |

### Dependency Graph

```
T001 ──→ T002 ──→ T003 ──→ T007 ──→ T008
 │                                    ↑
 ├──→ T004 ──→ T005                  T013
 │      │
 │      ├──→ T006 ──→ T010
 │      │
 │      └──→ T011
 │
 └──→ T014

T009 (independent)
T012 (independent)
```

---

## 12. Review Gates

### Gate Format

Each task must produce a review package containing:

| Component | Description |
|-----------|-------------|
| **Architecture diff** | Changes to architecture docs, ADRs, blueprints |
| **Code diff** | Actual code changes with line-by-line review |
| **Test result** | Full test output (`./gradlew :render-module:test`) |
| **Module boundary impact** | ModularityTest result, NamedInterface changes |
| **Provider capability changes** | New/modified/removed capabilities |
| **Cache compatibility impact** | Cache key format changes, invalidation rule changes |
| **Rollback plan** | How to revert if the change causes issues |
| **Open questions** | Unresolved design decisions, risks, assumptions |

### Gate Checklist

```
[ ] Architecture diff reviewed and approved
[ ] Code diff reviewed (no security issues, no secrets, no H2)
[ ] All tests pass (./gradlew :render-module:test)
[ ] ModularityTest passes (./gradlew :platform-app:test --tests '*ModularityTest*')
[ ] No new NamedInterface without approval
[ ] Provider capability changes documented
[ ] Cache compatibility verified (existing cache entries still valid)
[ ] Rollback plan documented
[ ] Open questions tracked
[ ] No Flyway V1 modification
[ ] No ProductionSafetyValidator weakening
```

---

## 13. First Sprint Recommendation

### Recommended First 5 Tasks

| Task | Type | Parallelizable? | Effort |
|------|------|-----------------|--------|
| T001: OTIO ADR | Investigation only | Yes (with T009, T012, T014) | 2 hours |
| T009: Worker Registration Enhancement | Code | Yes (with T001) | 3 hours |
| T012: BMF Spike | Investigation + spike | Yes (with T001) | 4 hours |
| T013: Provider Capability Inventory | Investigation + metadata | Yes (with T001) | 3 hours |
| T014: ASR Integration Design | Investigation only | Yes (with T001) | 2 hours |

### Parallelism

```
Week 1:
  Agent A: T001 (OTIO ADR)
  Agent B: T009 (Worker Registration Enhancement)
  Agent C: T012 (BMF Spike)
  Agent D: T013 (Provider Capability Inventory)
  Agent E: T014 (ASR Integration Design)

Week 2 (serial dependency):
  Agent A: T002 (Three-Graph ADR) → T003 (Provider Capability ADR)
  Agent B: T007 (Capability Descriptor Model)
  Agent C: T006 (Artifact Graph Compiler) [depends on T004]

Week 3 (parallel):
  Agent A: T004 (Timeline IR Extension) → T005 (OTIO Round-trip)
  Agent B: T008 (Capability Negotiation)
  Agent C: T010 (Cache Key Derivation)
  Agent D: T011 (Remotion Caption Word-level)
```

### Serial Dependencies

- T002 depends on T001 (ADR chain)
- T003 depends on T002 (ADR chain)
- T007 depends on T003 (model depends on ADR)
- T004 depends on T001 (Timeline IR extension depends on OTIO ADR)
- T005 depends on T004 (OTIO round-trip depends on IR extension)
- T006 depends on T004 (artifact graph depends on IR extension)
- T008 depends on T007 (negotiation depends on model)
- T010 depends on T006 (cache depends on artifact graph)
- T011 depends on T004 (caption depends on IR extension)
- T013 depends on T007 (inventory depends on model)

### Investigation-only Tasks

- T001: OTIO ADR (no code)
- T002: Three-Graph ADR (no code)
- T003: Provider Capability ADR (no code)
- T014: ASR Integration Design (no code)

---

## 14. Final Recommendation

### Is This Architecture Suitable for Current media-platform?

**Yes.** The current render module (600 files) already implements 60-70% of the foundation:

| Component | Current State | Gap |
|-----------|--------------|-----|
| OTIO adapter | `OpenTimelineioAdapter.java` exists | Needs bidirectional support, semantic annotations |
| Timeline IR | 30+ domain classes (`TimelineSpec`, `TimelineClip`, etc.) | Needs semantic annotation fields |
| Semantic analysis | `AiTimelineEditService`, `TimelineSemanticDiffService` exist | Needs ASR pipeline, vision pipeline |
| Artifact graph | `ArtifactGraph`, `ArtifactNode` exist | Needs compiler from Timeline IR, content-addressable cache keys |
| Provider registry | `RenderProviderRegistry`, `RenderProviderRouter`, `CapabilityDescriptorRegistry` exist | Needs formal capability model, cost model |
| Worker registry | `RenderWorkerRegistration`, `RenderWorkerRegistryService`, farm API exist | Needs engine versions, font availability, runtime capabilities |
| Cache | `RenderCacheContentHasher`, `SegmentCachePublisher`, `ReusableArtifact` exist | Needs unified cache key derivation, invalidation rules |
| Remotion captions | `RemotionCaption`, `RemotionCaptionStyle`, font pipeline exist | Needs word-level timing, animated presets |
| BMF | Nothing exists | Full implementation needed |
| ASR | Nothing exists | Design needed |

### What Is Already Implemented

- **OTIO import** (basic) via `OpenTimelineioAdapter`
- **Timeline IR** with 30+ domain classes covering tracks, clips, effects, transitions, markers, stickers, text overlays
- **Semantic diff** via `TimelineSemanticDiffService`
- **Incremental render plan** via `IncrementalRenderPlanService`, `SegmentTimelinePlanner`
- **Artifact graph** (basic) via `ArtifactGraph`, `ArtifactNode`
- **Provider registry** with 15+ providers, fallback policy, health monitoring
- **Worker registry** with registration, heartbeat, claim/complete/fail lifecycle
- **Render cache** with content hashing, S3-backed remote cache, tenant isolation
- **Remotion caption provider** with font pipeline, caption styles
- **Billing/quota integration** via `BillingDecisionEngine`, `BillingEnforcementService`

### What Is Missing

- **BMF integration** — no files exist
- **ASR pipeline** — no files exist
- **Capability negotiation formalization** — `CapabilityNegotiationService` exists but uses informal capability model
- **Three-graph separation** — currently mixed in `RenderPlan` and `PipelineExecutionPlan`
- **Cache key derivation formalization** — scattered across multiple services
- **Word-level caption timing** — RemotionCaption has style but not word-level timing

### Next Minimum Action

**Execute Sprint 1 (Week 1):**
1. T001: OTIO ADR (2 hours) — establishes the architectural direction
2. T009: Worker Registration Enhancement (3 hours) — extends existing infrastructure
3. T013: Provider Capability Inventory (3 hours) — documents what we have
4. T014: ASR Integration Design (2 hours) — plans the missing ASR pipeline
5. T012: BMF Spike (4 hours) — proves BMF feasibility

**Total effort:** ~14 hours (2 developer-days)  
**Risk:** Low (mostly investigation and small infrastructure extensions)  
**Output:** 3 ADRs + 1 design doc + enhanced worker registration + BMF spike proof

### Constraints

All tasks must遵守以下约束：

- Do not modify the Flyway V1 baseline
- Do not introduce H2 database
- Do not enable Spring AI active runtime
- Do not weaken ProductionSafetyValidator
- Do not disable ModularityTest
- Do not commit real secrets
- Do not automatically merge
- Do not deploy to production
- 1 task = 1 branch = 1 worktree = 1 coding agent
