# JavaCV Migration Guide

> **Last updated**: 2026-05-12
> **Migration Status**: Completed
> **Target**: JavaCV 1.5.10 with FFmpeg 6.1.1

## Migration Rationale

### Why Migrate from CLI to JavaCV?

1. **Security**: Eliminate shell command injection risks
2. **Performance**: Direct JNI bindings vs. process spawning
3. **Portability**: No external binary dependencies required
4. **Maintainability**: Pure Java codebase, easier debugging
5. **Deployment**: Simplified Docker images (no FFmpeg installation)
6. **Stability**: Better error handling and recovery

### Apache Commons Exec Removal

**Before:**
```java
// Process spawning with Commons Exec
CommandLine cmd = CommandLine.parse("ffmpeg -i " + input);
DefaultExecutor exec = new DefaultExecutor();
exec.execute(cmd);
```

**After:**
```java
// Direct JavaCV JNI calls
FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(input);
grabber.start();
// ... process frames
grabber.stop();
```

## Dependencies

### Gradle Configuration

```kotlin
// render-module/build.gradle.kts
dependencies {
    implementation("org.bytedeco:javacv:1.5.10")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10")
    implementation("org.bytedeco:ffmpeg-platform:6.1.1-1.5.10")
    
    // Optional: platform-specific optimizations
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10:linux-x86_64")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:6.1.1-1.5.10:macosx-x86_64")
}
```

### Maven Configuration

```xml
<!-- Not used in this project (Gradle only) -->
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacv</artifactId>
    <version>1.5.10</version>
</dependency>
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>ffmpeg</artifactId>
    <version>6.1.1-1.5.10</version>
</dependency>
```

## JavaCV vs. FFmpeg CLI Capability Mapping

### ✅ Fully Supported

| CLI Feature | JavaCV Equivalent | Implementation |
|-------------|-------------------|----------------|
| `-i input.mp4` | `FFmpegFrameGrabber` | Direct file reading |
| `-c:v libx264` | `avcodec.AV_CODEC_ID_H264` | Built-in codec |
| `-s 1920x1080` | `recorder.setImageWidth/Height()` | Resolution setting |
| `-r 30` | `recorder.setFrameRate()` | Frame rate control |
| `-b:v 2000k` | `recorder.setVideoBitrate()` | Bitrate control |
| `-ss 10 -t 30` | Frame positioning + counting | Time-based clipping |
| `-acodec aac` | `avcodec.AV_CODEC_ID_AAC` | Audio encoding |
| `-ar 44100` | `recorder.setSampleRate()` | Sample rate |

### ⚠️ Partially Supported

| CLI Feature | JavaCV Status | Notes |
|-------------|---------------|-------|
| `-filter_complex` | Limited | Basic drawtext supported, complex graphs need full integration |
| `-vf subtitles=file.srt` | Framework only | Subtitle parsing works, burn-in requires filtergraph integration |
| `-map 0:v:0 -map 0:a:0` | First track only | Multi-track not yet implemented |

### ❌ Not Supported (Yet)

| CLI Feature | Status | Alternative |
|-------------|--------|-------------|
| `-c:v libx265` | ❌ | H.264 only |
| `-preset fast/medium/slow` | ❌ | Default preset only |
| `-crf` | ❌ | Bitrate-based only |
| Complex filtergraphs | ❌ | Single operations only |
| Multiple audio streams | ❌ | First audio stream only |

## JavaCV Implementation Patterns

### 1. Video Probing

**CLI Approach (Removed):**
```java
// ffprobe -v quiet -print_format json -show_format -show_streams input.mp4
```

**JavaCV Approach:**
```java
try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
    grabber.start();
    int width = grabber.getImageWidth();
    int height = grabber.getImageHeight();
    double frameRate = grabber.getFrameRate();
    int audioChannels = grabber.getAudioChannels();
    grabber.stop();
}
```

### 2. Transcoding

```java
public void transcode(String input, String output, String profile) throws Exception {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(input)) {
        grabber.start();
        
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(output, width, height)) {
            configureRecorder(recorder, profile);
            recorder.start();
            
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                recorder.record(frame);
            }
            
            recorder.stop();
        }
        grabber.stop();
    }
}
```

### 3. Frame Extraction

```java
public void extractFrame(String input, String output, double timestamp) throws Exception {
    try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(input)) {
        grabber.start();
        
        long targetFrame = (long) (timestamp * grabber.getFrameRate());
        grabber.setVideoFrameNumber((int) targetFrame);
        
        Frame frame = grabber.grabImage();
        if (frame != null) {
            // Save frame to image file
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.convert(frame);
            ImageIO.write(image, "jpg", new File(output));
        }
        
        grabber.stop();
    }
}
```

## Deployment Considerations

### Docker Image Size

**Before (with FFmpeg):**
```
Base: openjdk:21-slim
FFmpeg: ~50MB
Total: ~350MB
```

**After (JavaCV only):**
```
Base: openjdk:21-slim
JavaCV: ~80MB (includes native libs)
Total: ~380MB
```

**Note**: JavaCV slightly larger but includes native libraries, eliminating runtime dependencies.

### Native Dependencies

JavaCV includes pre-built native libraries for:
- Linux (x86_64, arm64)
- Windows (x86_64)
- macOS (x86_64, arm64)

**Loading Order:**
1. JAR contains Java classes
2. Native libs extracted to temp directory
3. JNI bridges loaded via `System.loadLibrary()`
4. FFmpeg functions accessible via JNI

### Platform Compatibility

| Platform | Status | Notes |
|----------|--------|-------|
| Linux x86_64 | ✅ Fully tested | Primary deployment target |
| Linux arm64 | ✅ Supported | For ARM-based servers |
| Windows x86_64 | ✅ Supported | Development only |
| macOS x86_64 | ✅ Supported | Development only |
| macOS arm64 | ✅ Supported | Apple Silicon support |

### Docker Runtime

**Current Dockerfile:**
```dockerfile
FROM openjdk:21-slim
WORKDIR /app
COPY build/libs/platform-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**No FFmpeg installation needed** - JavaCV bundles native libraries.

### JVM Tuning

**Recommended Settings:**
```bash
java -jar app.jar \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Xmx4g \
  -Djava.io.tmpdir=/tmp/platform
```

**Native Memory:**
- JavaCV allocates native memory for frames
- Monitor with `jcmd <pid> VM.native_memory`
- Consider `-XX:MaxDirectMemorySize` if needed

## Performance Considerations

### CPU Usage
- **1080p**: ~100% single core per stream
- **4K**: ~200-300% single core per stream
- **Concurrent**: Limited by CPU cores

### Memory Usage
- **Per render**: 2-4GB heap + native memory
- **Idle**: ~500MB
- **Peak**: Depends on concurrent renders

### I/O Patterns
- **Input**: Sequential reads from storage
- **Output**: Sequential writes to artifacts
- **Temp**: Frame buffers in native memory

### Scaling Limits
- **Single node**: ~2-4 concurrent 1080p renders (8-core CPU)
- **Horizontal**: Planned for future release
- **Vertical**: CPU cores = concurrent capacity

## Rollback Strategy

### Fallback to FFmpeg CLI (If Needed)

If JavaCV proves insufficient, restore FFmpeg provider:

```java
@Component
@ConditionalOnProperty(name = "render.provider", havingValue = "ffmpeg")
public class FfmpegRenderProvider implements RenderProvider {
    // Restore Commons Exec implementation
}
```

**Configuration:**
```yaml
render:
  provider: ffmpeg  # Switch back to CLI
```

**Note**: This requires reinstalling FFmpeg in Docker images.

### Migration Rollback Checklist

1. **Before Deploy:**
   - Backup current CLI scripts
   - Test JavaCV in staging
   - Prepare rollback image with FFmpeg

2. **During Deploy:**
   - Feature flag: `render.provider=javacv`
   - Monitor error rates
   - Keep FFmpeg provider as backup

3. **If Issues:**
   - Change flag: `render.provider=ffmpeg`
   - Restart application
   - JavaCV becomes fallback

## Testing Strategy

### Unit Tests
```bash
./gradlew :render-module:test
```

### Integration Tests
```bash
# Test with real video files
./gradlew :render-module:integrationTest
```

### Performance Tests
```bash
# Generate test videos and benchmark
./gradlew :render-module:performanceTest
```

### Test Coverage Areas
- ✅ Profile support (6 profiles)
- ✅ Capability detection
- ✅ OTIO timeline parsing
- ✅ Error scenarios
- ✅ Empty timeline handling
- ✅ Subtitle processing
- ✅ Resolution mapping

## Troubleshooting

### Common Issues

**1. `UnsatisfiedLinkError`**
```java
// JavaCV native library not found
// Solution: Check classpath, verify OS compatibility
```

**2. `No suitable converter found`**
```java
// Frame type conversion failed
// Solution: Verify input format, check Java2DFrameConverter
```

**3. Memory leaks**
```java
// Always close grabbers/recorders
try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(...)) {
    // use grabber
}  // Auto-close
```

**4. Performance degradation**
- Check CPU usage per render
- Verify no thread contention
- Monitor native memory usage

### Debug Mode

Enable JavaCV debug logging:
```bash
java -Dorg.bytedeco.javacv.debug=true -jar app.jar
```

### Health Checks

```java
// Verify JavaCV availability
EnvironmentValidationResult result = provider.validateEnvironment();
if (!result.isValid()) {
    log.error("JavaCV not available: {}", result.getMessage());
}
```

## Monitoring

### Metrics Exported
- `render.job.duration` - Timer per job
- `render.job.success` - Counter for successful renders
- `render.job.failure` - Counter for failures
- `render.provider.health` - Gauge for provider status

### Log Patterns
```
JavaCVRenderProvider: rendering job={}, profile={}
JavaCVRenderProvider: render complete, artifact={}
JavaCVRenderProvider: render failed for job={}
```

## Security Notes

- ✅ No external process execution
- ✅ No shell command construction
- ✅ Input path validation
- ✅ Output path confinement to storage root
- ✅ Frame processing in isolated contexts

## Future Considerations

### When to Consider CLI Again

1. **Advanced filtergraphs needed**
2. **Hardware acceleration required**
3. **Specialized codecs not in JavaCV**
4. **Performance critical at scale**

### Migration Path Forward

1. **Current**: JavaCV (pure Java + JNI)
2. **Future**: GPU workers via CLI
3. **Future**: Remote render farms
4. **Future**: Hybrid approach (JavaCV + CLI)

---

*This migration guide reflects the current state as of 2026-05-12. For extension roadmap, see [render-provider-extension-roadmap.md](render-provider-extension-roadmap.md).*