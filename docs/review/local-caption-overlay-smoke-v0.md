# P2L.2 — Local Caption Overlay Smoke

## 1. Purpose

P2L.2 expands the BasicRenderPlan-to-local-runner bridge to support the first visible overlay capability: controlled local caption/text overlay smoke through platform-owned FFmpeg/libass-style execution.

## 2. Why Caption Overlay Follows P2L.1

P2L.1 established the BasicRenderPlan-to-local-runner bridge with a conservative step subset (DECLARE_OUTPUT_PROFILE, ENCODE_OUTPUT, VERIFY_OUTPUT). Caption overlay is the first visual overlay capability to prove through the local execution boundary, demonstrating that the bridge can handle more than just video-only output.

## 3. Current Planning-to-Local Execution Chain

```text
BasicTimeline
  ↓
FFmpegLibassBasicRenderPlanner (P2R.3)
  ↓
FFmpegLibassBasicRenderPlan (with APPLY_CAPTION_OVERLAY steps)
  ↓
BasicRenderPlanLocalExecutionAdapter (P2L.2 — now recognizes caption overlay)
  ↓
LocalFfmpegSmokeCommandBuilder (P2L.2 — generates ASS file + FFmpeg args)
  ↓
BasicRenderPlanLocalRunner (P2L.2 — includes caption overlay counts)
  ↓
LocalProcessRunner → FFmpeg execution
  ↓
LocalFfprobeValidator → output validation
  ↓
output.mp4 + local-render-execution-report.txt
```

## 4. What P2L.2 Implements

- `LocalCaptionOverlaySpec` — safe typed caption specification
- Extended `LocalRenderSmokeIssueCode` with 13 caption overlay codes
- Extended `BasicRenderPlanLocalExecutionAdapter` to recognize APPLY_CAPTION_OVERLAY
- Extended `LocalFfmpegSmokeCommandBuilder` with ASS subtitle generation and caption overlay FFmpeg args
- Extended `BasicRenderPlanLocalRunner` with caption overlay counts in result/report
- Extended `LocalRenderExecutionRequest` with caption overlay specs
- Unit tests (no FFmpeg required)
- Integration test (FFmpeg required, disabled by default)

## 5. What P2L.2 Does Not Implement

- Full caption renderer
- Arbitrary ASS style support
- Arbitrary HTML captions
- Font path support
- Font upload
- Multi-language font fallback
- Karaoke timing
- Word-level captions
- Animated captions
- Rich text
- External subtitle file upload
- Full arbitrary timeline rendering
- Real media source materialization
- Multi-clip assembly
- RenderExecutionPlan integration
- OpenCue integration
- ProductRuntime/StorageRuntime integration
- ProviderBindingRegistry integration
- Public API
- Artifact DAG
- Remotion execution

## 6. Supported Caption Subset

| Field | Constraint | Default |
|-------|-----------|---------|
| text | Non-blank, max 200 chars, sanitized | required |
| startMs | >= 0 | required |
| endMs | > startMs, max 5 min duration | required |
| position | Bottom-center (ASS Default style) | fixed |
| font size | 24pt (ASS Default style) | fixed |
| color | #FFFFFF (ASS Default style) | fixed |
| background | Semi-transparent black (ASS Default style) | fixed |

## 7. Unsupported Caption Features

Unsupported features are reported as warnings or UNSUPPORTED issues:

- Arbitrary ASS style → CAPTION_RAW_ASS_STYLE_FORBIDDEN (blocking)
- Arbitrary HTML → not supported (filtered by sanitizer)
- Arbitrary font path → CAPTION_FONT_PATH_FORBIDDEN (blocking)
- Font upload → not supported
- Multi-language font fallback → not supported
- Karaoke timing → not supported
- Word-level captions → not supported
- Animated captions → not supported
- Rich text → not supported
- External subtitle file upload → CAPTION_EXTERNAL_SUBTITLE_FORBIDDEN (blocking)

## 8. Caption Safety Model

- Text is sanitized: braces removed (prevents ASS override tag injection), backslashes removed, newlines converted to ASS \N
- Text length bounded to 200 characters
- Time range validated: start >= 0, end > start, max 5 min duration
- Placement fixed to bottom-center (ASS Default style Alignment=2)
- No raw ASS style input accepted
- No external subtitle path accepted
- No font path accepted
- No raw filtergraph input accepted

## 9. Command Safety Model

Reuses P2L.0/P2L.1 command safety model:

- Fixed binary allowlist: ffmpeg, ffprobe
- No shell invocation (sh -c, bash -c forbidden)
- No user-provided command string
- Arguments built as List<String>
- Timeout required
- Working directory controlled
- Output directory controlled
- stdout/stderr captured to result
- Exit code captured

Caption overlay generates a platform-owned ASS file from safe typed fields, then uses FFmpeg's `ass=` filter with the generated file path. No user-provided filtergraph.

## 10. Local Execution Policy

Reuses LocalRenderSmokePolicy:

- Disabled by default
- Enable: `-Dmedia.platform.localSmoke.enabled=true`
- Output root: `-Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke`
- Timeout: configurable
- Strict mode: `-Dmedia.platform.localSmoke.strict=true`

## 11. Stable Output Root

When enabled, output is written to:

```text
/tmp/media-platform-local-smoke/local-plan-smoke-002-basic-render-plan-caption-overlay/
  output.mp4
  caption-overlay-input.ass
  local-render-execution-report.txt
```

## 12. Unit Test Strategy

Unit tests (no FFmpeg required):

- Caption overlay issue codes exist
- Adapter recognizes APPLY_CAPTION_OVERLAY
- Safe caption overlay maps to request
- Missing caption text is rejected
- Invalid time range is rejected
- Raw filtergraph input is rejected
- Raw ASS style input is rejected
- External subtitle path is rejected
- Font path is rejected
- Text is safely escaped (braces, backslashes removed)
- Caption overlay count appears in result
- Command args remain List<String>
- No shell invocation
- No user command input

## 13. Optional Integration Test Command

```bash
./gradlew :render-module:test \
  --tests "*BasicRenderPlanCaptionOverlayLocalRunnerIntegrationTest" \
  -Dmedia.platform.localSmoke.enabled=true \
  -Dmedia.platform.localSmoke.outputRoot=/tmp/media-platform-local-smoke
```

## 14. Visual Validation Boundary

P2L.2 does NOT require computer vision or OCR to prove caption visibility. It only proves:

- The plan contained APPLY_CAPTION_OVERLAY
- The local runner recognized caption overlay as supported
- The controlled FFmpeg command included the platform-owned ASS overlay
- FFmpeg exited 0
- ffprobe validated output
- Report counted caption overlay support

## 15. Relationship to BasicRenderPlan

P2R.3 creates FFmpegLibassBasicRenderPlan with caption overlay steps. P2L.2 expands the supported local subset to include APPLY_CAPTION_OVERLAY.

## 16. Relationship to Scenario Runner

P2X.0 InternalScenarioRunner validates internal planning flow only. P2L.2 is a separate execution bridge — they are independent.

## 17. Relationship to Provider Binding DSL

P2B.0 defines the Provider Capability Binding DSL design. P2L.2 does not consume or reference the DSL.

## 18. Relationship to Future RenderExecutionPlan

P2L.2 does not implement RenderExecutionPlan integration.

## 19. Relationship to Future Local Runner

P2L.2 is the second step toward a full Local Runner, expanding the supported subset from P2L.1.

## 20. Relationship to Future OpenCue

P2L.2 does not integrate with OpenCue. OpenCue is an ExecutionEnvironment, not a Provider.

## 21. Relationship to ProductRuntime / StorageRuntime

P2L.2 does not call ProductRuntime or StorageRuntime. Output is local-only temporary files.

## 22. Artifact DAG Boundary

P2L.2 does not reference or require Artifact DAG. Artifact DAG is indefinitely deferred.

## 23. Remotion Boundary

P2L.2 does not execute Remotion. Remotion remains non-executable.

## 24. Follow-up Tasks

- P2L.3 — Real media source materialization for local execution
- P2L.4 — Multi-caption support expansion
- P2O.0 — OpenCue PVE Testbed Smoke Harness
- Future: Full Local Runner with arbitrary timeline rendering
