# Render Provider Extension Roadmap

> **Last updated**: 2026-05-12
> **Status**: Active development on JavaCV, planning future providers
> **Next Major Version**: Planned provider federation

## Current Implementation (2026-05)

### JavaCV Provider (Active)
- **Status**: ✅ Production-ready
- **Capabilities**: H.264, AAC, clipping, basic filters, subtitles
- **Use Cases**: Standard video rendering, social media content
- **Limitations**: CPU-only, single-track, no advanced effects

### Provider Registry (Current)
```java
Map<String, RenderProvider> providers = Map.of(
    "javacv", javaCVRenderProvider,
    "mock", mockRenderProvider
);
```

**Router Logic (Simplified):**
```java
public RenderProvider route(String profile) {
    return javaCVRenderProvider; // Always returns JavaCV for now
}
```

## Future Providers (Planned)

### 1. OFX Render Provider

**Purpose**: Professional effects and compositing
- **Target**: PRO/TEAM/ENTERPRISE tiers
- **Capabilities**:
  - Advanced transitions (wipe, slide, cube)
  - Professional color grading
  - Motion tracking
  - Keying and compositing
  - 3D effects

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "render.providers.ofx.enabled")
public class OFXRenderProvider implements RenderProvider {
    // OpenFX plugin integration via native bindings or CLI wrapper
}
```

**Timeline**: Q4 2026 - Research phase

### 2. GStreamer Render Provider

**Purpose**: Pipeline-based processing
- **Target**: Advanced users, media processing pipelines
- **Capabilities**:
  - Complex filter graphs
  - Plugin ecosystem
  - Hardware acceleration (VAAPI, NVDEC)
  - Network streaming sources
  - Custom plugin support

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "render.providers.gstreamer.enabled")
public class GStreamerRenderProvider implements RenderProvider {
    // GStreamer Java bindings or CLI wrapper
}
```

**Timeline**: Q1 2027 - Planning phase

### 3. MLT Render Provider

**Purpose**: Non-linear editing capabilities
- **Target**: Video editors, content creators
- **Capabilities**:
  - Multi-track timeline
  - Advanced transitions
  - Keyframe animation
  - Producer-consumer pipeline
  - XML project format

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "render.providers.mlt.enabled")
public class MltRenderProvider implements RenderProvider {
    // MLT melt command wrapper via extension-module
}
```

**Timeline**: Q1 2027 - Implementation phase

### 4. GPAC/MP4Box Packaging Provider

**Purpose**: Adaptive streaming packaging
- **Target**: Streaming platforms, multi-device delivery
- **Capabilities**:
  - DASH packaging (.mpd + segments)
  - HLS packaging (.m3u8 + segments)
  - CMAF packaging
  - MP4 inspection

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "render.providers.gpac.enabled")
public class GpacPackagingProvider implements PackagingProvider {
    // MP4Box CLI wrapper via extension-module
}
```

**Timeline**: Q3 2026 - Implementation phase

### 5. FFmpeg CLI Fallback Provider

**Purpose**: Emergency fallback when JavaCV fails
- **Target**: All tiers, backup provider
- **Capabilities**: Full FFmpeg feature set
- **Security**: Strict allowlist, no shell concatenation
- **Performance**: Process-based, higher overhead

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "render.providers.ffmpeg.enabled")
@ConditionalOnMissingBean(JavaCVRenderProvider.class)
public class FfmpegFallbackProvider implements RenderProvider {
    // Commons Exec based, secured via extension-module
}
```

**Timeline**: Q4 2026 - Implementation phase

## Provider Capability Model

### Capability Registry

```java
public record RenderProviderCapability(
    String providerKey,
    Set<String> formats,      // mp4, ogg, webm
    Set<String> codecs,       // h264, h265, aac
    Set<String> effects,      // blur, fade_in, watermark
    Set<String> transitions,  // dissolve, crossfade
    Set<String> features,     // subtitle-burn, multi-track
    String maxResolution,
    boolean supportsGPU,
    boolean supportsHDR,
    boolean supports4K,
    Set<String> profiles
) {}
```

### Example Capability Declarations

**JavaCV:**
```java
new RenderProviderCapability(
    "javacv",
    Set.of("mp4", "ogg", "webm"),
    Set.of("h264", "aac"),
    Set.of("video.fade_in", "video.blur", "text.subtitle_burn_in"),
    Set.of("fade_in", "fade_out"),
    Set.of("burn_in"),
    "3840x2160",
    false, false, true,  // No GPU, No HDR, Yes 4K
    Set.of("default_1080p", "social_720p", /* ... */)
);
```

**OFX:**
```java
new RenderProviderCapability(
    "ofx",
    Set.of("mp4", "mov"),
    Set.of("h264", "prores"),
    Set.of("video.vignette", "video.chromatic", "video.overlay"),
    Set.of("wipe", "slide", "cube"),
    Set.of("multi-track", "keying"),
    "3840x2160",
    true, true, true,  // GPU, HDR, 4K
    Set.of("pro_1080p", "team_4k", "enterprise_4k")
);
```

## Provider Routing Strategy

### Tier-Based Routing

```java
public RenderProvider route(String profile, UserTier tier) {
    return switch (tier) {
        case FREE -> javaCVProvider;  // Basic features only
        case PRO -> enhancedRouter.route(profile);  // JavaCV + OFX
        case TEAM -> teamRouter.route(profile);    // All providers
        case ENTERPRISE -> enterpriseRouter.route(profile);
        case EXPERIMENTAL -> experimentalRouter.route(profile);
    };
}
```

### Profile Pattern Matching

```java
// Profile naming: tier_effect_resolution_framerate
// Examples: free_720p, pro_1080p_vignette, team_4k_60fps

public RenderProvider routeByProfile(String profile) {
    if (profile.startsWith("ofx_")) {
        return ofxProvider;
    } else if (profile.contains("gstreamer")) {
        return gstreamerProvider;
    } else if (profile.contains("mlt")) {
        return mltProvider;
    }
    return javaCVProvider;
}
```

### Effect-Based Routing

```java
public RenderProvider routeByEffects(List<String> effectKeys) {
    // Check if any effect requires specific provider
    if (effectKeys.contains("video.vignette") || 
        effectKeys.contains("video.overlay")) {
        return ofxProvider;
    }
    if (effectKeys.contains("multi.track")) {
        return mltProvider;
    }
    // Default to JavaCV
    return javaCVProvider;
}
```

## User Tier Strategy

### FREE Tier
- **Provider**: JavaCV only
- **Resolution**: Max 720p
- **Effects**: Basic only (fade, blur)
- **Watermark**: Required
- **Concurrency**: 1 job at a time
- **Queue**: Standard priority

### PRO Tier
- **Providers**: JavaCV + OFX
- **Resolution**: Up to 1080p
- **Effects**: All JavaCV + OFX effects
- **Watermark**: None
- **Concurrency**: 2 jobs
- **Queue**: High priority

### TEAM Tier
- **Providers**: All available
- **Resolution**: Up to 4K
- **Effects**: All effects
- **Watermark**: None
- **Concurrency**: 4 jobs
- **Queue**: Highest priority
- **Features**: Multi-track editing

### ENTERPRISE Tier
- **Providers**: All + custom
- **Resolution**: 4K + future
- **Effects**: All + custom plugins
- **Watermark**: None
- **Concurrency**: Unlimited
- **Queue**: Dedicated workers
- **Features**: On-premise deployment, SLA guarantees

### EXPERIMENTAL Tier
- **Providers**: All + beta
- **Resolution**: All
- **Effects**: All + experimental
- **Watermark**: Optional
- **Concurrency**: 1 job
- **Queue**: Low priority
- **Features**: Preview features, opt-in only

## Render Worker Architecture

### Current: Monolithic
```
platform-app (all providers in one JVM)
```

### Future: Split Workers
```
platform-app (API only)
  │
  ├── render-worker-javacv (JavaCV provider)
  │     └── CPU only
  │
  ├── render-worker-ofx (OFX provider)
  │     └── GPU accelerated
  │
  ├── render-worker-gstreamer (GStreamer provider)
  │     └── Pipeline processing
  │
  └── packaging-worker-gpac (GPAC provider)
        └── Streaming formats
```

### Worker Communication
```
Message Queue (Kafka/RabbitMQ)
  - Job dispatch
  - Status updates
  - Health checks
  - Metrics
```

## Provider Health and Fallback

### Health Check Protocol
```java
public interface RenderProvider {
    HealthStatus getHealthStatus();
    boolean isAvailable();
    int getActiveJobs();
    Duration getAverageLatency();
}
```

### Fallback Chain
1. **Primary**: Tier-based preferred provider
2. **Fallback 1**: JavaCV (if primary fails)
3. **Fallback 2**: FFmpeg CLI (if JavaCV fails)
4. **Final**: Queue for manual intervention

## Configuration Examples

### Development (Current)
```yaml
render:
  providers:
    javacv:
      enabled: true
    ofx:
      enabled: false
    gstreamer:
      enabled: false
    mlt:
      enabled: false
    gpac:
      enabled: false
    ffmpeg:
      enabled: false  # Fallback only
```

### Production (Planned)
```yaml
render:
  providers:
    javacv:
      enabled: true
      max-concurrent: 4
      cpu-limit: 200%
    ofx:
      enabled: true
      gpu-devices: [0, 1]
      license-server: "ofx-license.example.com"
    gpac:
      enabled: true
      packaging-queue: "high"
  routing:
    strategy: "tier-capability-effect"
    fallback-chain: ["javacv", "ffmpeg"]
```

## Docker Deployment Evolution

### Current
```dockerfile
FROM openjdk:21-slim
# JavaCV bundles FFmpeg
COPY app.jar /app/
```

### Future (Multi-Stage)
```dockerfile
# JavaCV worker
FROM openjdk:21-slim as javacv
COPY javacv-worker.jar /app/

# OFX worker (with GPU)
FROM nvidia/cuda:12.0-devel as ofx
COPY ofx-worker.jar /app/
RUN apt-get install -y ofx-plugins

# GPAC worker
FROM ubuntu:22.04 as gpac
RUN apt-get install -y gpac
COPY gpac-worker.jar /app/
```

## Implementation Priorities

### P0 (Current)
- ✅ JavaCV provider (done)
- ✅ Basic routing (done)
- ✅ Tier-based profiles (done)

### P1 (Next 3 months)
- 🔄 Capability model (in progress)
- 🔄 Enhanced router (in progress)
- 🔄 GPAC packaging provider (planned)

### P2 (Next 6 months)
- 📋 OFX provider research
- 📋 MLT provider planning
- 📋 Worker split architecture

### P3 (Future)
- 📋 GStreamer provider
- 📋 Remote worker federation
- 📋 Custom plugin support

## Risk Assessment

### High Risk
- **OFX licensing**: Commercial plugins may require paid licenses
- **GPU memory**: Multi-GPU rendering needs careful memory management
- **Worker coordination**: Distributed state management complexity

### Medium Risk
- **CLI security**: FFmpeg fallback needs strict sandboxing
- **Version compatibility**: Provider version matrix complexity
- **Performance tuning**: Each provider needs individual optimization

### Low Risk
- **JavaCV stability**: Mature library, well-tested
- **Configuration management**: Standard Spring Boot patterns
- **Error handling**: Established patterns from current implementation

## Migration Path

### For Users
1. **Profile upgrade**: Change profile name to access new features
2. **Tier upgrade**: Automatic access to new providers
3. **API compatibility**: Maintain backward compatibility

### For Developers
1. **Implement new provider**: Follow `RenderProvider` SPI
2. **Register capabilities**: Declare in `RenderProviderCapability`
3. **Configure routing**: Add to router logic
4. **Add tests**: Unit + integration tests required

---

*This roadmap reflects planning as of 2026-05-12. Priorities may change based on user feedback and technical feasibility.*