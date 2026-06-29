# P2L.3 — Real Media Source Materialization

## 1. Purpose

P2L.3 expands the local runner from synthetic `testsrc` input to controlled real media fixture input. This proves the local execution chain can consume a real MP4 file, apply caption overlay, and validate input/output with ffprobe.

## 2. Why Real Media Source Materialization Follows P2L.2

P2L.2 proved caption overlay works on synthetic testsrc input. P2L.3 proves the same chain works on a real media file — the first step toward production media input without exposing arbitrary user media or storage internals.

## 3. Current Planning-to-Local Execution Chain

```text
BasicTimeline with caption
  ↓
FFmpegLibassBasicRenderPlanner
  ↓
FFmpegLibassBasicRenderPlan
  ↓
BasicRenderPlanLocalExecutionAdapter (with controlled media source)
  ↓
BasicRenderPlanLocalRunner
  ↓
controlled input-fixture.mp4 (generated locally)
  ↓
platform-generated ASS subtitle (if captions present)
  ↓
controlled FFmpeg execution with real media input
  ↓
ffprobe input + output validation
  ↓
output.mp4 + local-render-execution-report.txt
```

## 4. What P2L.3 Implements

- `LocalMediaSourceKind` enum — controlled local fixture only
- `LocalMediaSourceOrigin` enum — platform-generated or controlled test fixture
- `LocalMediaSourceSpec` record — safe typed media source with path validation
- `LocalMediaSourceFixtureGenerator` — generates deterministic input MP4 under controlled root
- Extended `LocalRenderSmokeIssueCode` with media source issue codes
- Extended `LocalRenderExecutionRequest` with `mediaSourceSpec` field
- Extended `LocalRenderExecutionResult` with input source metadata
- Extended `LocalFfmpegSmokeCommandBuilder` with real media input command builder
- Extended `BasicRenderPlanLocalExecutionAdapter` with media source validation
- Extended `BasicRenderPlanLocalRunner` with input fixture materialization and validation
- Unit tests (no FFmpeg required)
- Integration test (FFmpeg required, disabled by default)

## 5. What P2L.3 Does Not Implement

- Arbitrary user media ingestion
- Remote URL media ingestion
- StorageRuntime materialization
- ProductRuntime integration
- Multi-file input
- Image sequence input
- Audio-only input
- Subtitle file input from user
- Untrusted metadata extraction
- Production media materialization
- OpenCue integration
- RenderExecutionPlan integration
- ProviderBindingRegistry integration
- Remotion execution
- Artifact DAG

## 6. Controlled Local Media Source Model

| Field | Constraint | Default |
|-------|-----------|---------|
| kind | CONTROLLED_LOCAL_FIXTURE only | required |
| origin | PLATFORM_GENERATED or CONTROLLED_TEST_FIXTURE | required |
| path | Under controlled output root, no traversal | required |
| format | mp4 | required |
| codec | h264 | required |

Path validation rejects:
- Path traversal (`..`)
- Remote URLs (`http://`, `https://`, `ftp://`, `rtmp://`)
- Storage internals (`bucket`, `objectKey`, `signedUrl`, `materializedPath`)

## 7. Materialization Strategy

**Option A (implemented):** Generate deterministic input fixture MP4 under controlled output root using FFmpeg testsrc.

- No binary fixture committed
- No remote downloads
- No user media ingestion
- No StorageRuntime
- Reproducible with installed FFmpeg

Fixture is generated under: `{outputRoot}/local-plan-smoke-003-real-media-source-caption-overlay/input-fixture.mp4`

## 8. Supported Media Source Subset

| Field | Value |
|-------|-------|
| Source kind | CONTROLLED_LOCAL_FIXTURE |
| Source origin | PLATFORM_GENERATED |
| Container | mp4 |
| Video codec | h264 |
| Duration | 2-5 seconds |
| Resolution | 320x180 |
| Audio | Optional |
| Path | Under controlled output root only |

## 9. Unsupported Media Source Types

Unsupported sources are rejected or reported:

- User-uploaded media → MEDIA_SOURCE_PATH_FORBIDDEN
- Remote URL media → MEDIA_SOURCE_PATH_FORBIDDEN
- Arbitrary local file path → MEDIA_SOURCE_PATH_FORBIDDEN (outside controlled root)
- StorageRuntime reference → MEDIA_SOURCE_STORAGE_REFERENCE_FORBIDDEN
- ProductRuntime reference → MEDIA_SOURCE_PRODUCT_REFERENCE_FORBIDDEN
- Signed URL / bucket/objectKey → MEDIA_SOURCE_PATH_FORBIDDEN

## 10. Media Source Safety Model

- Path must be under controlled output root
- Path must not contain traversal sequences
- Path must not be a remote URL
- Path must not reference storage internals
- Only CONTROLLED_LOCAL_FIXTURE kind accepted
- Only PLATFORM_GENERATED or CONTROLLED_TEST_FIXTURE origin accepted
- Input validated with ffprobe before main render

## 11. Command Safety Model

Reuses P2L.0/P2L.1/P2L.2 command safety model:

- Fixed binary allowlist: ffmpeg, ffprobe
- No shell invocation
- No user-provided command string
- Arguments built as `List<String>`
- Timeout required
- Working directory controlled
- Input directory controlled
- Output directory controlled

## 12. Local Execution Policy

Reuses `LocalRenderSmokePolicy`:

- Disabled by default
- Enable: `-Dmedia.platform.localSmoke.enabled=true`
- Output root: `-Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke`
- Strict mode: `-Dmedia.platform.localSmoke.strict=true`

## 13. Stable Output Root

When enabled, output is written to:

```text
/tmp/media-platform-local-smoke/local-plan-smoke-003-real-media-source-caption-overlay/
  input-fixture.mp4
  output.mp4
  caption-overlay-input.ass (if captions present)
  local-render-execution-report.txt
```

## 14. Unit Test Strategy

Unit tests (no FFmpeg required):

- Media source issue codes exist
- Media source kind/origin enum values
- Media source spec accepts controlled local fixture
- Media source spec rejects null kind/path
- Media source spec validates path under controlled root
- Media source spec rejects remote URL
- Media source spec rejects path traversal
- Media source spec rejects storage internals
- Execution request hasRealMediaSource returns false by default
- Adapter accepts controlled media source
- Adapter rejects media source outside controlled root
- Adapter rejects remote URL media source
- Fixture generator builds valid config
- Fixture generator rejects invalid dimensions
- Real media command builder rejects null input path
- Result hasInputSource returns false for synthetic input
- Result hasInputSource returns true for real media

## 15. Optional Integration Test Command

```bash
./gradlew :render-module:test \
  --tests "*RealMediaSourceLocalRunnerIntegrationTest" \
  -Dmedia.platform.localSmoke.enabled=true \
  -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
```

## 16. Visual Validation Boundary

P2L.3 does NOT require computer vision or OCR. It proves:

- The input fixture is a real MP4 file
- The plan references controlled local media source
- The local runner used controlled media input
- FFmpeg exited 0
- ffprobe validated input and output
- Caption overlay path still works on real media input
- Report includes input and output metadata

## 17. Relationship to BasicRenderPlan

P2L.1 consumes FFmpegLibassBasicRenderPlan for local execution smoke. P2L.3 expands the input from synthetic testsrc to controlled real media fixture.

## 18. Relationship to Scenario Runner

P2X.0 validates planning correctness. P2L.3 validates that a plan with real media input can drive actual FFmpeg execution. Both are independent.

## 19. Relationship to Provider Binding DSL

P2B.0 defines the Provider Capability Binding DSL design. P2L.3 does not consume or reference the DSL.

## 20. Relationship to Future RenderExecutionPlan

P2L.3 does not implement RenderExecutionPlan integration.

## 21. Relationship to Future Local Runner

P2L.3 is the third step toward a full Local Runner, expanding input sources from synthetic to real media.

## 22. Relationship to Future OpenCue

P2L.3 does not integrate with OpenCue. OpenCue is an ExecutionEnvironment, not a Provider.

## 23. Relationship to ProductRuntime / StorageRuntime

P2L.3 does not call ProductRuntime or StorageRuntime. Output is local-only temporary files.

## 24. Artifact DAG Boundary

P2L.3 does not reference or require Artifact DAG. Artifact DAG is indefinitely deferred.

## 25. Remotion Boundary

P2L.3 does not execute Remotion. Remotion remains non-executable.

## 26. Follow-up Tasks

- P2L.4 — Multi-clip assembly with real media
- P2O.0a — Local Docker OpenCue Shared-Path Smoke (complete)
- P2O.0b — OpenCue Cuebot/RQD Job Submission (future)
- Future: StorageRuntime-backed media materialization
- Future: Full Local Runner with arbitrary timeline rendering
