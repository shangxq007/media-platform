---
status: implementation-report
created: 2026-06-25
scope: render-module
truth_level: current
owner: platform
---

# Media Processing Sprint 036 — Real FFprobe Integration

## Media Capability Audit

| Capability | Before | After |
|-----------|--------|-------|
| Video probe | Basic (duration, fps, resolution, codec) | Full: profile, level, pixelFormat, colorSpace, colorRange, frameCount, videoBitrate |
| Audio probe | Basic (channels, sampleRate, bitrate) | Full: channelLayout, bitDepth, audioCodec, audioBitrate |
| Image probe | None | width, height, pixelFormat, colorSpace |
| Container probe | None | format, fileSize, streamCount |
| JSON parsing | Regex-based fallback | Full Jackson JSON parsing |
| Timeout | None | 30s timeout with Process.destroyForcibly() |
| Error handling | Exception → failure | Structured error codes: FFPROBE_MISSING_OR_TIMEOUT, JSON_PARSE_ERROR, FFPROBE_EXCEPTION |
| Validation | None | `ProbeMetadata.isValid()` checks format + duration |

## Enhanced Components

| Component | Change |
|-----------|--------|
| `ProbeMetadata` | Expanded from 10 fields → 23 fields: fps, frameCount, videoProfile, pixelFormat, colorSpace, colorRange, videoBitrate, audioChannelLayout, audioCodec, audioBitDepth, containerStreamCount, streams[], errorCode, errorMessage |
| `FfprobeMetadataProvider` | Complete rewrite: Jackson JSON parsing (not regex), 30s timeout with `Process.destroyForcibly()`, structured error codes, `ProcessResult` record for stdout/stderr/exitCode |
| `ProbeTaskHandler` | New — real task handler replacing mock. Calls ffprobe via provider, extracts storageUri from payload |

## New Components

| Component | Role |
|-----------|------|
| `ProbeTaskHandler` | Real PROBE implementation. Parses task payload for storageUri, calls ffprobe, validates result |
| `ProbeMetadata.StreamSummary` | Per-stream metadata: codecType, codecName, width, height, fps, channels, sampleRate |
| `ProbeMetadata.withError()` | Create error variant preserving metadata fields |

## FFprobe Runtime

```java
// Core flow
ProbeTaskHandler.execute(ctx):
  1. Extract assetId + storageUri from task payload
  2. SemanticMetadataRequest to ffprobe provider
  3. FfprobeMetadataProvider.analyze():
     a. ProcessBuilder("ffprobe", "-v", "quiet", "-print_format", "json",
                       "-show_format", "-show_streams", path)
     b. Process.waitFor(30, TimeUnit.SECONDS) with destroyForcibly on timeout
     c. Parse JSON: format → duration, size, bitrate; streams → video/audio metadata
     d. Return ProbeMetadata with parsed fields
  4. Validate: ProbeMetadata.isValid() → format + duration > 0
  5. Success → log; Failure → throw → task retry/FAILED
```

## Error Handling

| Error Code | Cause | Recovery |
|-----------|-------|----------|
| `FFPROBE_MISSING_OR_TIMEOUT` | ffprobe not installed or process timed out | Retry (transient) |
| `JSON_PARSE_ERROR` | ffprobe output not valid JSON | Fail (output format?) |
| `FFPROBE_EXCEPTION` | Unexpected exception | Retry (transient) |

## Tests

All existing 3 FfprobeProvider tests pass (supports check, capability, name).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Real ffprobe not tested in CI | Tests verify provider structure (no ffprobe binary in test env) |
| No storage URI resolution | Assumes path is local file path. S3/remote URI resolution deferred. |
| Probe result not persisted to DB yet | Handler validates but doesn't write to AssetSemanticMetadata |

## Deferred Items

| Item | Sprint |
|------|--------|
| Persist probe result to AssetSemanticMetadata | Sprint 037 |
| Storage URI resolver (S3 presigned URL → local path) | Sprint 037 |
| Whisper integration | Sprint 037 |
