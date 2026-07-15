# Media Investigation: FFmpegRenderProvider.render() Minimal Fixtures

## How FFmpegRenderProvider.render() Resolves Media

### Entry Flow

1. `render(jobId, aiScript, profile)` parses `aiScript` as JSON timeline
2. Calls `resolveClips(aiScript)` → gets `List<ResolvedClip>`
3. If `resolvedClips.isEmpty()` → throws `RENDER-400-003` ("No renderable clips in timeline")
4. Otherwise builds FFmpeg command and executes via `ProcessToolRunner`

### resolveClips() Resolution Chain

```
resolveClips(aiScript)
  → timelineScriptParser.parse(aiScript) → Optional<TimelineSpec>
  → timelineScriptParser.videoClipsInOrder(timeline) → List<TimelineClip>
  → for each clip:
      clip.assetRef().storageUri() → String uri
      resolveToLocalPath(uri) → String localPath (must be non-null)
        → timelineScriptParser.mediaFileExists(uri, storageRoot)
            → resolveLocalPath(uri, storageRoot) → strips prefix
            → Files.isRegularFile(Path.of(path)) ← MUST EXIST ON DISK
        → if not found: assetResolver.resolveToLocalPath(uri) → downloads remote
      → new ResolvedClip(localPath, clip.assetInPoint(), clip.clipDuration())
```

### URI → Local Path Mapping (TimelineScriptParser.resolveLocalPath)

| URI Format | Resolution |
|---|---|
| `file:///tmp/test.mp4` | Strip `file://` → `/tmp/test.mp4` |
| `localFsStorageProvider://relative/path` | `{storageRoot}/relative/path` |
| `storage://relative/path` | `{storageRoot}/relative/path` |
| `/absolute/path` | Used as-is |
| `relative/path` | `{storageRoot}/relative/path` |

### Critical Gate: Files.isRegularFile()

`mediaFileExists()` calls `Files.isRegularFile(Path.of(path))`. The file MUST be a real, non-empty regular file on disk. A fake file created with `Files.writeString(input, "fake")` satisfies the existence check but would fail at actual FFmpeg execution (unless the tool runner is mocked).

## Timeline JSON Format Required

The parser accepts two shapes:

### OTIO-style (simpler, used in existing tests)

```json
{
  "tracks": [{
    "type": "VIDEO",
    "children": [{
      "media_reference": "file:///tmp/test.mp4",
      "source_range": {"start_time": 0, "duration": 2}
    }]
  }]
}
```

### Canonical TimelineSpec

```json
{
  "id": "test-timeline",
  "name": "Test",
  "outputSpec": {"format": "mp4", "width": 1920, "height": 1080, "frameRate": 30},
  "tracks": [{
    "id": "v1",
    "type": "VIDEO",
    "children": [{
      "id": "c1",
      "media_reference": "file:///tmp/test.mp4",
      "source_range": {"start_time": 0, "duration": 2}
    }]
  }]
}
```

### Alternative clip format (also parsed)

```json
{
  "tracks": [{
    "type": "VIDEO",
    "clips": [{
      "assetRef": {"storageUri": "file:///tmp/test.mp4"},
      "assetInPoint": 0,
      "clipDuration": 2
    }]
  }]
}
```

## Existing Test Media Fixtures

### None found on disk

- `test-assets/golden-render-project-v1/assets/` does NOT exist
- No `.mp4` files exist in the repository
- `FixturePath.goldenProjectAssets()` throws `IllegalStateException` without GOLDEN_PROJECT_DIR env var

### Existing test patterns

| Test | Approach |
|---|---|
| `FFmpegRenderProviderTest` | `Files.writeString(input, "fake")` + mocked `ProcessToolRunner` |
| `GoldenRenderE2ETest` | Expects real assets, uses `Assumptions.assumeTrue()` to skip |
| `R2FixtureGenerator.generateTestVideo()` | Generates real MP4 via FFmpeg testsrc at runtime |
| `LocalMediaSourceFixtureGenerator` | Generates real MP4 via FFmpeg testsrc at runtime |

## Generating a Tiny MP4 at Test Runtime

### FFmpeg Availability

- **FFmpeg 7.1.4** is installed at `/usr/bin/ffmpeg` ✅

### Encoder Availability

| Encoder | Available | Notes |
|---|---|---|
| `libx264` | ❌ | Not compiled in (disabled in build config) |
| `libopenh264` | ✅ | Works, produces valid H.264 MP4 |
| `mpeg4` | ✅ | MPEG-4 Part 2 |
| `libvpx_vp8` | ✅ | VP8/WebM |
| `libvpx_vp9` | ✅ | VP9/WebM |
| `libaom_av1` | ✅ | AV1 |
| `ffv1` | ✅ | Lossless |

### Verified Command (generates 25KB in ~1s)

```bash
ffmpeg -y -f lavfi -i testsrc=duration=1:size=320x180:rate=24 \
  -c:v libopenh264 -pix_fmt yuv420p /tmp/test-minimal.mp4
```

Output: `/tmp/test-minimal.mp4` — 25,233 bytes, valid MP4, 1 second, 320x180, 24fps.

### Alternative: mpeg4 encoder

```bash
ffmpeg -y -f lavfi -i testsrc=duration=1:size=320x180:rate=24 \
  -c:v mpeg4 -pix_fmt yuv420p /tmp/test-minimal.mp4
```

### Java Runtime Generation Pattern (from R2FixtureGenerator)

```java
List<String> cmd = List.of(
    "ffmpeg", "-y",
    "-f", "lavfi", "-i",
    "testsrc=duration=1:size=320x180:rate=24",
    "-c:v", "libopenh264",
    "-pix_fmt", "yuv420p",
    outputPath.toString()
);
new ProcessBuilder(cmd).start().waitFor();
```

## Minimal Fixtures for FFmpegRenderProvider.render()

### Option A: Mocked ProcessToolRunner (unit test, no real FFmpeg)

```java
// 1. Create temp file (existence check passes)
Path input = tempDir.resolve("input.mp4");
Files.writeString(input, "fake");

// 2. Build timeline JSON
String timeline = """
    {"tracks":[{"type":"VIDEO","children":[
      {"media_reference":"file://%s",
       "source_range":{"start_time":0,"duration":2}}
    ]}]}
    """.formatted(input);

// 3. Mock tool runner
when(mockToolRunner.execute(any()))
    .thenReturn(ToolExecutionResult.success(0, "ffmpeg", "", now, now.plusMillis(50)));

// 4. Create provider
FFmpegRenderProvider provider = new FFmpegRenderProvider(
    mockToolRunner, new FFmpegCommandFactory(),
    new TimelineScriptParser(), null);
provider.setStorageRoot(tempDir.toString());

// 5. Render
RenderResult result = provider.render("job-1", timeline, "default_1080p");
```

### Option B: Real FFmpeg execution (integration test)

```java
// 1. Generate real MP4 via testsrc
Path input = tempDir.resolve("input.mp4");
new ProcessBuilder("ffmpeg", "-y",
    "-f", "lavfi", "-i", "testsrc=duration=1:size=320x180:rate=24",
    "-c:v", "libopenh264", "-pix_fmt", "yuv420p",
    input.toString()
).start().waitFor();

// 2. Build timeline JSON (same as Option A)

// 3. Create provider with real runner (or GoldenRenderPlanAdapter.createLocalProvider)
FFmpegRenderProvider provider = GoldenRenderPlanAdapter.createLocalProvider(
    tempDir, new RealFfmpegRunner());

// 4. Render
RenderResult result = provider.render("job-1", timeline, "default_1080p");
```

### Option C: Synthetic testsrc profile (bypasses media file entirely)

```java
// Uses built-in synthetic path — no media file needed
provider.render("job-1", "{}", "synthetic_testsrc");
// OR
provider.render("job-1", "{}", "minimal-test");
```

Requires: `render.synthetic.enabled=true` in config.

## Domain Fixtures Summary

| Fixture | Required | Minimal Value |
|---|---|---|
| Timeline JSON with `tracks` | ✅ | OTIO-style with 1 VIDEO track, 1 child clip |
| Real media file on disk | ✅ (for non-synthetic) | 1-second 320x180 testsrc MP4 via `libopenh264` |
| `storageRoot` directory | ✅ | `@TempDir` or `/tmp/...` |
| `ProcessToolRunner` | ✅ | Mock (unit) or real `ProcessBuilder` (integration) |
| `FFmpegCommandFactory` | ✅ | `new FFmpegCommandFactory()` |
| `TimelineScriptParser` | ✅ | `new TimelineScriptParser()` |
| `MediaAssetResolver` | Optional | `null` (for `file://` URIs that exist locally) |

## Key Insight

The existing `FFmpegRenderProviderTest.rendersTimelineWithLocalClip()` already demonstrates the minimal pattern: a fake file + mocked tool runner. For boundary validation that exercises real FFmpeg, use `libopenh264` (not `libx264`, which is unavailable) with `testsrc` lavfi filter to generate a tiny real MP4 at test setup time.
