# Render Provider Capability Matrix

**Date:** 2026-06-08
**RC Tag:** rc/p4-import-export-2026-06-06.3
**Status:** FFmpeg is the only production-ready provider

---

## 1. Current Provider Status

### 1.1 Production-Ready Providers

| Provider | Status | Runtime Available | CI Tested | Production Ready |
|----------|--------|------------------|-----------|------------------|
| **FFmpegRenderProvider** | ✅ Production | ✅ Yes | ✅ Yes | ✅ Yes |

### 1.2 Development/Staging Providers

| Provider | Status | Runtime Available | CI Tested | Production Ready |
|----------|--------|------------------|-----------|------------------|
| **GPACRenderProvider** | ⚠️ POC | ⚠️ Partial | ❌ No | ❌ No |
| **JavaCVRenderProvider** | ⚠️ POC | ⚠️ Partial | ❌ No | ❌ No |
| **MltRenderProvider** | ⚠️ POC | ⚠️ Partial | ❌ No | ❌ No |
| **GStreamerRenderProvider** | ⚠️ POC | ⚠️ Partial | ❌ No | ❌ No |

### 1.3 Spike/Future Providers

| Provider | Status | Runtime Available | CI Tested | Production Ready |
|----------|--------|------------------|-----------|------------------|
| **NatronRenderProvider** | 🔬 Spike | ❌ No | ❌ No | ❌ No |
| **VapourSynthRenderProvider** | 🔬 Spike | ❌ No | ❌ No | ❌ No |
| **OFXRenderProvider** | 🔬 Spike | ❌ No | ❌ No | ❌ No |
| **ShotstackRenderProvider** | 🔬 Spike | ❌ No | ❌ No | ❌ No |
| **Blender Provider** | 📋 Planned | ❌ No | ❌ No | ❌ No |
| **Remotion Provider** | 📋 Planned | ❌ No | ❌ No | ❌ No |
| **Cloud Render Provider** | 📋 Planned | ❌ No | ❌ No | ❌ No |

---

## 2. Capability Matrix

| Capability | FFmpeg | GPAC | JavaCV | MLT | GStreamer | Natron | VapourSynth | OFX | Shotstack | Blender | Remotion | Cloud |
|------------|--------|------|--------|-----|-----------|--------|-------------|-----|----------|---------|----------|-------|
| **Multi-clip concat** | ✅ | ❌ | ⚠️ | ✅ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Audio track** | ✅ | ❌ | ⚠️ | ✅ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Audio mix** | ✅ | ❌ | ⚠️ | ✅ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Subtitle burn-in** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Watermark overlay** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Fade** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Cross dissolve** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Crop** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Scale** | ✅ | ❌ | ⚠️ | ❌ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Placement** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **normalized_ppm spatial plan** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Chroma key** | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Region blur** | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Wipe/slide/zoom transitions** | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Node effects** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **3D scene** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| **Template-driven render** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **GPU acceleration** | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Deterministic golden test** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **CI runtime support** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Production readiness** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Legend:**
- ✅ = Fully supported
- ⚠️ = Partial/POC support
- ❌ = Not supported
- 🔬 = Spike/Experimental
- 📋 = Planned

---

## 3. FFmpeg Provider Details

### 3.1 Supported Capabilities

| Capability | Implementation | Status |
|------------|----------------|--------|
| **Multi-clip concat** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Audio track** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Audio mix** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Subtitle burn-in** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Watermark overlay** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Fade** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Cross dissolve** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Crop** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Scale** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **Placement** | FFmpegCommandFactory.buildMultiTrackCommand | ✅ Production |
| **normalized_ppm spatial plan** | SpatialCoordinateConverter | ✅ Production |
| **Deterministic golden test** | GoldenRenderPlanAdapter | ✅ Production |
| **CI runtime support** | RenderPipelineDagIT | ✅ Production |

### 3.2 Partial/POC Capabilities

| Capability | Implementation | Status | Notes |
|------------|----------------|--------|-------|
| **Chroma key** | FFmpeg filter complex | ⚠️ POC | Basic chroma key only |
| **Region blur** | FFmpeg filter complex | ⚠️ POC | Basic blur only |
| **Wipe/slide/zoom transitions** | FFmpeg filter complex | ⚠️ POC | Limited transitions |
| **GPU acceleration** | FFmpeg NVENC/VAAPI | ⚠️ POC | Hardware-dependent |

### 3.3 Unsupported Capabilities

| Capability | Reason | Future Provider |
|------------|--------|-----------------|
| **Node effects** | Not supported by FFmpeg | Natron, OFX |
| **3D scene** | Not supported by FFmpeg | Blender |
| **Template-driven render** | Not supported by FFmpeg | Remotion |
| **Cloud render** | Not supported by FFmpeg | Cloud Render Provider |

---

## 4. Runtime Requirements

### 4.1 FFmpeg Runtime

| Dependency | Required | Check Method | Error Message |
|------------|----------|--------------|---------------|
| **ffmpeg binary** | ✅ Yes | `Files.isExecutable(Path.of("/usr/bin/ffmpeg"))` | "FFmpeg binary not found at {path}" |
| **ffprobe binary** | ✅ Yes | `Files.isExecutable(Path.of("/usr/bin/ffprobe"))` | "ffprobe binary not found at {path}" |
| **Executable permissions** | ✅ Yes | `Files.isExecutable(path)` | "FFmpeg binary not executable: {path}" |
| **Version detection** | ⚠️ Optional | `ffmpeg -version` | Warning only, not blocking |
| **Temp directory writable** | ✅ Yes | `Files.isWritable(tempDir)` | "Temp directory not writable: {path}" |

### 4.2 Runtime Probe Implementation

```java
public class FFmpegRuntimeProbe {
    private static final List<String> FFMPEG_PATHS = List.of(
        "/usr/bin/ffmpeg",
        "/bin/ffmpeg",
        "/usr/local/bin/ffmpeg"
    );

    private static final List<String> FFPROBE_PATHS = List.of(
        "/usr/bin/ffprobe",
        "/bin/ffprobe",
        "/usr/local/bin/ffprobe"
    );

    public static RuntimeCheckResult checkFFmpeg() {
        return checkBinary("ffmpeg", FFMPEG_PATHS);
    }

    public static RuntimeCheckResult checkFFprobe() {
        return checkBinary("ffprobe", FFPROBE_PATHS);
    }

    private static RuntimeCheckResult checkBinary(String name, List<String> paths) {
        for (String path : paths) {
            Path binary = Path.of(path);
            if (Files.isExecutable(binary)) {
                return RuntimeCheckResult.found(name, path);
            }
        }
        return RuntimeCheckResult.notFound(name, paths);
    }

    public record RuntimeCheckResult(String name, String path, boolean found, List<String> searchedPaths) {
        static RuntimeCheckResult found(String name, String path) {
            return new RuntimeCheckResult(name, path, true, List.of(path));
        }
        static RuntimeCheckResult notFound(String name, List<String> searchedPaths) {
            return new RuntimeCheckResult(name, null, false, searchedPaths);
        }
    }
}
```

---

## 5. Render Integration Profile

### 5.1 Default CI (platform-app:test)

**Includes:**
- Unit tests (no runtime required)
- Integration tests (H2 database, mocked services)
- P4 Import/Export tests (identity-access-module)

**Excludes:**
- Render integration tests (require FFmpeg)
- RenderPipelineDagIT (requires full render runtime)
- RenderNativeToolsIT (requires FFmpeg)
- RenderNatronEffectsIT (requires Natron)

### 5.2 Render Integration Test Profile

**Task:** `./gradlew :platform-app:renderIntegrationTest`

**Requires:**
- FFmpeg binary at `/usr/bin/ffmpeg` or `/bin/ffmpeg`
- ffprobe binary at `/usr/bin/ffprobe` or `/bin/ffprobe`
- Write access to temp directory
- Test assets (generated on-the-fly)

**Includes:**
- RenderPipelineDagIT
- RenderNativeToolsIT
- RenderNatronEffectsIT (if Natron available)

**CI Strategy:**
- Optional in default CI
- Required in render integration profile
- Can fail without blocking default CI
- Must pass before production deployment

### 5.3 GitHub Actions Configuration

```yaml
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - name: Run default tests
        run: ./gradlew :platform-app:test

  render-integration:
    runs-on: ubuntu-latest
    if: github.event_name == 'workflow_dispatch' || contains(github.event.head_commit.message, '[render-test]')
    steps:
      - name: Run render integration tests
        run: ./gradlew :platform-app:renderIntegrationTest || true
```

---

## 6. Failure Modes

### 6.1 Missing Binary

| Scenario | Error Code | Message | Recovery |
|----------|------------|---------|----------|
| **ffmpeg not found** | RENDER-500-001 | "FFmpeg binary not found. Searched: {paths}" | Install FFmpeg |
| **ffprobe not found** | RENDER-500-001 | "ffprobe binary not found. Searched: {paths}" | Install FFmpeg |
| **Not executable** | RENDER-500-001 | "FFmpeg binary not executable: {path}" | chmod +x |

### 6.2 Unsupported Operation

| Scenario | Error Code | Message | Recovery |
|----------|------------|---------|----------|
| **Node effects** | RENDER-400-001 | "Node effects not supported by {provider}" | Use Natron/OFX provider |
| **3D scene** | RENDER-400-001 | "3D scenes not supported by {provider}" | Use Blender provider |
| **Template render** | RENDER-400-001 | "Template-driven render not supported by {provider}" | Use Remotion provider |

### 6.3 Render Failure

| Scenario | Error Code | Message | Recovery |
|----------|------------|---------|----------|
| **FFmpeg non-zero exit** | RENDER-500-002 | "FFmpeg rendering failed (exit={code}): {stderr}" | Check input/output |
| **Output file missing** | RENDER-500-003 | "Render output not found: {path}" | Check FFmpeg command |
| **Timeout** | RENDER-500-004 | "Render timed out after {timeout}ms" | Increase timeout or optimize |
| **Invalid input** | RENDER-400-002 | "Invalid render input: {reason}" | Fix input format |

---

## 7. Future Provider Roadmap

### 7.1 Spike Phase (Not P4 RC Blocker)

| Provider | Priority | Effort | Dependencies | Capabilities |
|----------|----------|--------|--------------|--------------|
| **Natron** | P2 | High | Natron binary, OFX plugins | Node effects, compositing |
| **VapourSynth** | P3 | Medium | VapourSynth binary, Python | Script-based rendering |
| **OFX** | P3 | High | OFX host, plugins | Node effects, transitions |
| **Shotstack** | P3 | Low | API key, internet | Cloud rendering |

### 7.2 Planned (Post-RC)

| Provider | Priority | Effort | Dependencies | Capabilities |
|----------|----------|--------|--------------|--------------|
| **Blender** | P3 | Very High | Blender binary, Python | 3D scenes, animation |
| **Remotion** | P3 | Medium | Node.js, Chrome | Template-driven rendering |
| **Cloud Render** | P3 | High | Cloud account, API | Scalable rendering |

### 7.3 Provider SPI Requirements

All future providers must implement:

```java
public interface RenderProvider {
    RenderResult render(String jobId, String aiScript, String profile);
    List<String> getSupportedProfiles();
    default boolean supports(String capability) { ... }
    default EnvironmentValidationResult validateEnvironment() { ... }
}
```

And provide:
- Capability matrix update
- Runtime probe implementation
- Error handling with clear messages
- Unit tests (mocked runtime)
- Integration tests (real runtime, optional in CI)

---

## 8. Related Documents

| 文档 | 说明 |
|------|------|
| [P4 Architecture](architecture/p4-import-export-architecture.md) | P4 Import/Export Pipeline 架构 |
| [Render Provider Roadmap](03-provider-roadmap.md) | Provider 路线图 |
| [Render Pipeline](01-render-pipeline.md) | 渲染流水线说明 |
| [Provider Registration](02-provider-registration.md) | Provider 注册机制 |
| [VFX Compositing Ecosystem](06-vfx-compositing-ecosystem-selection.md) | VFX 合成生态系统 |
| [Natron Worker POC](07-natron-worker-poc.md) | Natron Worker POC |
| [Pipeline Tools](08-pipeline-tools-shotstack-natron-popcornfx-bento4.md) | 流水线工具 |

---

**Document prepared by:** Kilo (AI-assisted)  
**Date:** 2026-06-08  
**Status:** FFmpeg is the only production-ready provider
