# Render Provider Registration

> **Purpose:** How render provider registration, routing, and health checking works.
> **Last Updated:** 2026-05-17

---

## Overview

Render providers are backend implementations that execute render jobs. The platform supports multiple providers (JavaCV, FFmpeg, GStreamer, GPAC, MLT, OFX, Mock) and routes jobs to the best provider based on capability matching, health status, and profile requirements.

**Key components:**

| Component | Class | Role |
|-----------|-------|------|
| SPI interface | `RenderProvider` | Defines the contract all providers implement |
| Registry | `RenderProviderRegistry` | Stores providers and their capabilities |
| Auto-configuration | `RenderProviderAutoConfiguration` | Registers all providers at startup |
| Router | `RenderProviderRouter` | Routes jobs to the best provider |
| Selection policy | `RenderProviderSelectionPolicy` | Selects the best provider for a profile + effects |
| Fallback policy | `RenderProviderFallbackPolicy` | Handles fallback when the preferred provider is unavailable |
| Health check | `RenderProviderHealthCheck` | Records provider health status |
| Properties | `RenderProviderProperties` | Configuration toggle for each provider |

---

## RenderProvider Interface

All providers implement the `RenderProvider` SPI:

```java
public interface RenderProvider {
    RenderResult render(String jobId, String aiScript, String profile);
    List<String> getSupportedProfiles();
    default boolean supports(String capability) { ... }
    default EnvironmentValidationResult validateEnvironment() { ... }

    record RenderResult(String artifactId, String storageUri, long duration,
                        String format, String resolution) {}
    record EnvironmentValidationResult(boolean valid, String message) { ... }
}
```

### Method Contracts

| Method | Description |
|--------|-------------|
| `render(jobId, aiScript, profile)` | Execute a render job. Returns artifact metadata. |
| `getSupportedProfiles()` | List of profile strings this provider handles (e.g., `"default_1080p"`). |
| `supports(capability)` | Check if the provider supports a specific capability (e.g., `"h264"`, `"watermark"`). |
| `validateEnvironment()` | Verify that external binaries/libraries are available. Called at startup. |

---

## RenderProviderAutoConfiguration

`RenderProviderAutoConfiguration` is a Spring `@Configuration` class that registers all providers at application startup via a `CommandLineRunner` bean.

### Registration Flow

```
1. Spring instantiates all @Component providers
2. CommandLineRunner.registerProviders() executes
3. Each provider is registered with:
   - A lowercase key (e.g., "javacv", "ffmpeg", "gpac")
   - The provider instance
   - Its RenderProviderCapability record
4. Environment validation is run for each provider
5. Health check results are stored in the registry
6. Registry logs summary of available providers and effects
```

### Provider Keys

| Key | Provider Class | Always Available |
|-----|---------------|-----------------|
| `"javacv"` | `JavaCVRenderProvider` | Yes (Spring `@Component`) |
| `"ofx"` | `OFXRenderProvider` | Yes (Spring `@Component`) |
| `"mock"` | `MockRenderProvider` | Yes (only in `test` profile) |
| `"mlt"` | `MltRenderProvider` | Conditional: `render.providers.melt.enabled=true` |
| `"ffmpeg"` | `FFmpegRenderProvider` | Conditional: `render.providers.ffmpeg.enabled=true` |
| `"gstreamer"` | `GStreamerRenderProvider` | Conditional: `render.providers.gstreamer.enabled=true` |
| `"gpac"` | `GPACRenderProvider` | Conditional: `render.providers.gpac.enabled=true` |

**Note:** JavaCV, OFX, and Mock are always registered. External-binary providers (FFmpeg, GStreamer, GPAC, MLT) are wrapped in `Optional<>` and only registered if their `@ConditionalOnProperty` matches.

### Capability Registration Example

```java
registry.register("ffmpeg", ffmpeg, new RenderProviderCapability(
    "ffmpeg",
    Set.of("mp4", "webm", "mkv", "mov"),           // supportedFormats
    Set.of("h264", "h265", "vp9", "av1", "aac"),   // supportedCodecs
    Set.of("video.fade_in", "video.fade_out", ...), // supportedEffects
    Set.of("dissolve", "fade_in", "fade_out"),       // supportedTransitions
    Set.of("burn_in"),                                // supportedSubtitleModes
    "3840x2160",                                      // maxResolution
    true,                                             // requiresExternalBinary
    false,                                            // requiresGpu
    false,                                            // experimental
    Set.of("social_1080p", "broadcast_4k", ...)       // availableInProfiles
));
```

---

## RenderProviderProperties Configuration

`RenderProviderProperties` is a `@ConfigurationProperties` record bound to `render.providers.*`:

```java
@ConfigurationProperties(prefix = "render.providers")
public record RenderProviderProperties(
    ProviderConfig javacv,
    ProviderConfig ofx,
    ProviderConfig mock,
    ProviderConfig ffmpeg,
    ProviderConfig gstreamer,
    ProviderConfig gpac,
    ProviderConfig mlt
) {
    public record ProviderConfig(boolean enabled) {}
}
```

### application.yml

```yaml
render:
  providers:
    javacv:
      enabled: true      # Always enabled by default
    ofx:
      enabled: true      # Always enabled by default
    mock:
      enabled: false     # Only for test profile
    ffmpeg:
      enabled: false     # Enable when ffmpeg binary is available
    gstreamer:
      enabled: false     # Enable when gst-launch-1.0 is available
    gpac:
      enabled: false     # Enable when MP4Box is available
    mlt:
      enabled: false     # Enable when melt is available
```

**Note:** The `javacv` and `ofx` toggles exist for symmetry but these providers are always registered as `@Component` beans. The conditional providers (ffmpeg, gstreamer, gpac, mlt) use `@ConditionalOnProperty` to control bean creation.

---

## How to Add a New RenderProvider

1. **Create the provider class** in the appropriate sub-package of `infrastructure/`:
   ```java
   @Component
   @ConditionalOnProperty(prefix = "render.providers.<key>", name = "enabled", havingValue = "true")
   public class MyRenderProvider implements RenderProvider {
       @Override
       public RenderResult render(String jobId, String aiScript, String profile) { ... }

       @Override
       public List<String> getSupportedProfiles() { ... }

       @Override
       public boolean supports(String capability) { ... }

       @Override
       public EnvironmentValidationResult validateEnvironment() { ... }
   }
   ```

2. **Add configuration toggle** to `RenderProviderProperties`:
   ```java
   ProviderConfig myprovider
   ```

3. **Add default config** to `application.yml`:
   ```yaml
   render:
     providers:
       myprovider:
         enabled: false
   ```

4. **Register in `RenderProviderAutoConfiguration`**:
   ```java
   Optional<MyRenderProvider> myProvider

   myProvider.ifPresent(p -> {
       registry.register("myprovider", p, new RenderProviderCapability(...));
   });
   ```

5. **Add tests** in `RenderProviderRegistrationTest`

---

## Provider Keys and Capabilities

### Capability Record

```java
public record RenderProviderCapability(
    String providerKey,              // e.g., "ffmpeg"
    Set<String> supportedFormats,    // e.g., "mp4", "webm"
    Set<String> supportedCodecs,     // e.g., "h264", "h265"
    Set<String> supportedEffects,    // e.g., "video.fade_in"
    Set<String> supportedTransitions,// e.g., "dissolve"
    Set<String> supportedSubtitleModes, // e.g., "burn_in"
    String maxResolution,            // e.g., "3840x2160"
    boolean requiresExternalBinary,  // true for ffmpeg, gstreamer, gpac, mlt
    boolean requiresGpu,             // true for GPU-accelerated providers
    boolean experimental,            // true for mock
    Set<String> availableInProfiles  // profiles this provider handles
) {}
```

### Capability Matrix (as registered)

| Provider | Max Resolution | External Binary | Formats | Codecs | Effects |
|----------|---------------|-----------------|---------|--------|---------|
| JavaCV | 3840x2160 | No | mp4, ogg, webm, mov | h264, h265, vp9, aac, mp3 | 10 video + subtitle + audio |
| OFX | 3840x2160 | Yes | mp4, webm | h264, aac, vp9 | 14 video + text + audio |
| FFmpeg | 3840x2160 | Yes | mp4, webm, mkv, mov | h264, h265, vp9, av1, aac, mp3 | 10 video + subtitle + audio |
| GStreamer | 1920x1080 | Yes | mp4, webm | h264, aac | 7 pipeline effects |
| GPAC | 1920x1080 | Yes | mp4, mpd, m3u8 | h264, aac | 6 packaging effects |
| MLT | 1920x1080 | Yes | mp4, webm | h264, aac | 4 timeline effects |
| Mock | 1920x1080 | No | mp4 | h264, aac | 4 basic effects |

### Profile-to-Provider Routing

| Profile Prefix | Default Provider | Reason |
|----------------|-----------------|--------|
| `default_*`, `social_*` | JavaCV | Broad format/codec support, no external binary |
| `ofx_*`, `pro_*`, `team_*`, `enterprise_*` | OFX | Advanced effects, transitions |
| `gpu_*` | JavaCV (with GPU preset) | GPU-accelerated encoding via NVENC/VAAPI |
| `broadcast_*` | FFmpeg | High-resolution broadcast presets |
| `gpac_dash`, `gpac_hls`, `gpac_cmaf` | GPAC | Packaging-specific profiles |
| `gstreamer_*` | GStreamer | Pipeline-specific profiles |
| `test_mock` | Mock | Test-only profile |

---

## Health Checks

### Startup Health Check

During `CommandLineRunner` execution, each registered provider's `validateEnvironment()` is called:

```java
for (RenderProviderCapability cap : registry.getAllCapabilities()) {
    RenderProvider provider = registry.getProvider(cap.providerKey()).orElse(null);
    if (provider != null) {
        long start = System.currentTimeMillis();
        EnvironmentValidationResult envResult = provider.validateEnvironment();
        long latency = System.currentTimeMillis() - start;
        RenderProviderHealthCheck health = envResult.valid()
                ? RenderProviderHealthCheck.ok(cap.providerKey(), latency)
                : RenderProviderHealthCheck.failed(cap.providerKey(), envResult.message());
        registry.updateHealthCheck(cap.providerKey(), health);
    }
}
```

### Health Check Record

```java
public record RenderProviderHealthCheck(
    String providerKey,
    boolean healthy,
    String message,
    Instant lastChecked,
    long latencyMs
) {
    public static RenderProviderHealthCheck ok(String providerKey) { ... }
    public static RenderProviderHealthCheck ok(String providerKey, long latencyMs) { ... }
    public static RenderProviderHealthCheck failed(String providerKey, String message) { ... }
}
```

### How Health Affects Routing

`RenderProviderSelectionPolicy` filters by health:

1. Filter by profile support
2. Filter by required effects
3. **Filter by health status** — unhealthy providers are excluded unless all providers are unhealthy
4. Prefer stable over experimental
5. Prefer higher resolution capability

### Environment Validation by Provider

| Provider | Binary Checked | Command |
|----------|---------------|---------|
| JavaCV | JavaCV/FFmpeg JNI | Class availability check |
| OFX | (self-contained) | Always passes |
| FFmpeg | `ffmpeg`, `ffprobe` | `ffmpeg -version` |
| GStreamer | `gst-launch-1.0` | `gst-launch-1.0 --version` |
| GPAC | `MP4Box` | `MP4Box -version` |
| MLT | `melt` | `melt -query plugins` |
| Mock | (test-only) | Always passes |
