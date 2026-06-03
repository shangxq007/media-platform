# Render Provider Integration Guide

> **Generated**: 2026-05-08T14:00Z (Phase T8: Render Provider Realization Preparation)
> **Status**: SPI established; MockRenderProvider active; ProcessRenderProvider placeholder documented.

---

## 1. RenderProvider SPI

The `RenderProvider` interface in `render-module/infrastructure` defines the SPI for render backend implementations:

```java
public interface RenderProvider {
    RenderResult render(String jobId, String aiScript, String profile);
    List<String> getSupportedProfiles();
    boolean supports(String capability);
    EnvironmentValidationResult validateEnvironment();
}
```

### Method Contracts

| Method | Purpose | Called When |
|--------|---------|-------------|
| `render()` | Execute a render job | Job reaches RENDERING state |
| `getSupportedProfiles()` | List supported profile IDs | Provider registration / routing |
| `supports(capability)` | Check capability support | Capability-based routing |
| `validateEnvironment()` | Verify binaries/config | Application startup |

---

## 2. Active Provider: MockRenderProvider

**Profile**: Default (always active)

```yaml
# No configuration needed — auto-detected via @Component
```

**Supported Profiles**: `social_1080p`, `social_720p`, `default_1080p`, `default_720p`

**Capabilities**: `h264`, `mp4`, `watermark`

---

## 3. ProcessRenderProvider (Placeholder)

### Design

A future provider that invokes external render tools (FFmpeg, MLT, GPAC) via process execution.

### Security Boundaries

- **NO `ProcessBuilder` in business modules** — process execution is isolated to `extension-module/infrastructure`
- **Command whitelist** — only pre-configured executables are allowed
- **Timeout enforcement** — all process executions have configurable timeouts
- **Output capture** — stdout/stderr are captured with size limits to prevent OOM

### Configuration (Future)

```yaml
# Future: application-render.yml
render:
  providers:
    ffmpeg:
      enabled: true
      executable: /usr/bin/ffmpeg
      timeout-seconds: 3600
      max-output-bytes: 67108864  # 64MB
      allowed-args:
        - "-i"
        - "-c:v"
        - "-c:a"
        - "-b:v"
        - "-b:a"
        - "-s"
        - "-r"
```

### Implementation Skeleton

```java
// Future: render-module/infrastructure/ProcessRenderProvider.java
@Component
@ConditionalOnProperty(prefix = "render.providers.ffmpeg", name = "enabled", havingValue = "true")
public class ProcessRenderProvider implements RenderProvider {

    private final ToolRunner toolRunner; // From extension-module
    private final RenderProviderProperties properties;

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        // 1. Build command from template + aiScript
        // 2. Execute via ToolRunner (extension-module)
        // 3. Map exit code to RenderResult
        // 4. Capture stdout/stderr for debugging
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        // Verify executable exists and is executable
        // Test with: ffmpeg -version
    }
}
```

---

## 4. OpenTimelineIO Timeline Model (Placeholder)

### Future Integration

```java
// Future: render-module/domain/Timeline.java
public record Timeline(
        String id,
        List<Track> tracks,
        Duration totalDuration,
        Map<String, Object> metadata) {}

public record Track(
        String id,
        TrackType type, // VIDEO, AUDIO, SUBTITLE
        List<Clip> clips) {}

public record Clip(
        String id,
        String sourceUri,
        Duration inPoint,
        Duration outPoint,
        Map<String, Object> effects) {}
```

---

## 5. Provider Routing

### RenderProviderRouter

```java
@Component
public class RenderProviderRouter {
    public RenderProvider route(String profile) {
        // 1. Find providers supporting the profile
        // 2. Filter by environment validation status
        // 3. Return first match (or default)
    }

    public RenderProvider routeByCapability(String capability) {
        // Capability-based routing for advanced features
    }
}
```

---

## 6. Deployment Requirements

### Binary Dependencies

| Tool | Purpose | Required For |
|------|---------|-------------|
| FFmpeg | Video encoding/transcoding | ProcessRenderProvider |
| MLT | Video editing/compositing | ProcessRenderProvider (future) |
| GPAC | MP4Box packaging | ProcessRenderProvider (future) |

### System Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU cores | 4 | 16+ |
| RAM | 8 GB | 32+ GB |
| Temp storage | 10 GB | 100+ GB SSD |
| GPU | Optional | NVIDIA with NVENC |

### Fonts and Codecs

- **Fonts**: DejaVu Sans, Liberation Sans (for subtitle burn-in)
- **Codecs**: H.264 (libx264), H.265 (libx265), AAC, MP3

---

## 7. Testing Strategy

### Unit Tests

- `MockRenderProviderTest`: Verify mock behavior, capability checks
- `RenderProviderRouterTest`: Verify routing logic

### Integration Tests (Future)

- `ProcessRenderProviderTest`: Requires FFmpeg on PATH (conditional)
- `RenderFlowIntegrationTest`: End-to-end with MockRenderProvider

### Conditional Test Execution

```java
@EnabledIfSystemProperty(named = "ffmpeg.available", matches = "true")
class ProcessRenderProviderIntegrationTest {
    // Only runs when FFmpeg is available
}
```
