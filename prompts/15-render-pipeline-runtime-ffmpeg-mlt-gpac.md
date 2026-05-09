# Prompt 15: Render Pipeline Runtime with FFmpeg, MLT/melt, GPAC/MP4Box, and OTIO

## Purpose
Design and implement the first safe, replaceable, testable media render runtime architecture for real video processing.

## Preconditions
- Mock render flow works.
- Prompt 14 has clarified persistence, tenancy, outbox, and provider boundaries.
- Do not remove MockRenderProvider.

## Design Goal
Do not scatter `ffmpeg`, `melt`, or `MP4Box` calls across business services. Build a Render Pipeline Runtime where business code speaks in terms of timeline, render plan, render steps, profiles, tools, artifacts, and packaging.

## Tool Roles
- FFmpeg: probe, transcode, thumbnail, filters, subtitles, mux/demux, preview/proxy.
- MLT/melt: multi-track timeline render, transitions, nonlinear editing style composition.
- GPAC/MP4Box: MP4/ISOBMFF inspection, DASH/HLS/CMAF packaging, manifests and segments.
- OpenTimelineIO: timeline interchange model, not a renderer or media container.

## Global Constraints
- Do not require local ffmpeg/melt/MP4Box for full test suite.
- Do not execute shell strings built from user input.
- All process execution must go through a safe runner.
- Do not use `ProcessBuilder` directly in business modules.
- All executable paths must come from a whitelist/config.
- Use `List<String>` arguments, not shell concatenation.
- Keep Mock provider as default for e2e tests.

---

## Phase R1: Media Tool Runtime Abstraction

Subtask: `Media Tool Runtime Engineer`

Implement:
- ToolRegistry
- ToolDefinition
- ToolCapability
- ToolExecutionRequest
- ToolExecutionResult
- ToolExecutionLog
- ToolEnvironmentReport
- ToolSandboxPolicy
- ProcessToolRunner

Requirements:
- executable must be configured/whitelisted
- args must be `List<String>`
- no shell=true by default
- timeout support
- stdout/stderr capture
- exitCode recording
- working directory support
- path traversal protection
- max log/output size where practical

Tests:
- allowed executable
- rejected executable
- timeout handling
- stdout/stderr capture
- non-zero exit code mapping
- path traversal rejection

---

## Phase R2: RenderPlan and RenderStep

Subtask: `Render Plan Engineer`

Implement:
- RenderPlan
- RenderStep
- RenderStepType
- RenderStepStatus
- RenderProfile
- RenderPlanService
- RenderStepExecutionService

Step types:
- BUILD_TIMELINE
- FFMPEG_PROBE
- FFMPEG_TRANSCODE
- MLT_RENDER_TIMELINE
- GPAC_PACKAGE_HLS
- GPAC_PACKAGE_DASH
- REGISTER_ARTIFACT
- QC_PROBE

Requirements:
- RenderJob can reference RenderPlan.
- Each step has input artifacts, output artifacts, status, error code, duration.
- Step status transitions are validated.

Tests:
- plan creation
- step transition
- failed step behavior
- render job association

---

## Phase R3: TimelineSpec and OTIO Placeholder

Subtask: `Timeline Model Engineer`

Implement:
- TimelineSpec
- TimelineTrack
- TimelineClip
- TimelineAssetRef
- TimelineTextOverlay
- TimelineAudioSpec
- TimelineOutputSpec
- TimelineValidationResult
- OpenTimelineIO adapter placeholder

Requirements:
- Internal JSON model first.
- OTIO placeholder documents that OTIO is an interchange format, not a renderer.
- Validate durations, asset references, tracks, output spec.

Tests:
- valid timeline
- missing asset
- invalid duration
- output spec validation

Docs:
- `docs/timeline-model.md`

---

## Phase R4: FFmpeg Provider Skeleton

Subtask: `FFmpeg Provider Engineer`

Implement:
- FfmpegRenderProvider
- FfmpegProbeService
- FfmpegCommandFactory
- FfmpegEnvironmentValidator

Capabilities:
- validateEnvironment
- probe command spec
- thumbnail command spec
- transcode command spec
- faststart command spec

Requirements:
- Does not require installed ffmpeg by default.
- Command construction is testable.
- If enabled and ffmpeg is present, allow conditional integration test.
- No direct shell concatenation.

Docs:
- `docs/render-ffmpeg.md`

---

## Phase R5: MLT/melt Provider Skeleton

Subtask: `MLT Provider Engineer`

Implement:
- MltRenderProvider
- MltProjectXmlBuilder
- MeltCommandFactory
- MltEnvironmentValidator

Capabilities:
- TimelineSpec -> MLT XML skeleton
- melt command spec
- render profile mapping

Requirements:
- Does not require installed melt by default.
- MLT XML generation is tested.
- Command construction is tested.
- No direct shell concatenation.

Docs:
- `docs/render-mlt.md`

---

## Phase R6: GPAC/MP4Box Packaging Provider Skeleton

Subtask: `GPAC Packaging Engineer`

Implement:
- PackagingProvider
- PackagingRequest
- PackagingResult
- GpacPackagingProvider
- Mp4BoxCommandFactory
- GpacEnvironmentValidator

Capabilities:
- DASH command spec
- HLS command spec
- CMAF placeholder
- MP4 inspection placeholder

Requirements:
- Does not require installed MP4Box by default.
- Command construction is tested.
- Packaging is separate from rendering.

Docs:
- `docs/render-gpac-packaging.md`

---

## Phase R7: Artifact Catalog Expansion

Subtask: `Artifact Catalog Expansion Engineer`

Add ArtifactType values:
- TIMELINE_JSON
- TIMELINE_OTIO
- MLT_PROJECT_XML
- FFMPEG_COMMAND_SPEC
- RENDER_LOG
- VIDEO_MEZZANINE
- VIDEO_MP4
- VIDEO_PROXY
- THUMBNAIL
- SUBTITLE_SRT
- SUBTITLE_VTT
- AUDIO_MIXDOWN
- HLS_MANIFEST
- HLS_SEGMENT
- DASH_MANIFEST
- DASH_SEGMENT
- CMAF_CHUNK
- QC_REPORT
- MEDIA_PROBE_JSON

Tasks:
- Add migration if artifact types are database constrained.
- Preserve existing artifact API.
- Add artifact relation tests.

---

## Phase R8: Render Worker Deployment Docs

Subtask: `Render Worker Deployment Documenter`

Create/update:
- `docs/render-provider-integration.md`
- `docs/render-worker-deployment.md`
- `docs/deployment-resource-requirements.md`

Document:
- ffmpeg/ffprobe
- melt/MLT
- GPAC/MP4Box
- fonts
- codecs
- subtitle rendering
- temp workspace
- object storage
- CPU/GPU
- concurrency
- timeouts
- sandbox policy
- observability
- worker image strategy

Recommended deployment split:
- platform-app
- render-worker
- packaging-worker
- ai-worker
- scheduler/outbox-worker

---

## Phase R9: Render Runtime Quality Gate

Subtask: `Render Runtime Release Gatekeeper`

Run:
- grep for direct `new ProcessBuilder` in business modules
- grep for shell-string process execution
- `./gradlew clean test`
- `./gradlew :platform-app:bootJar`

Check:
- existing mock e2e flow still passes
- no required local media binaries for default tests
- all tool execution goes through ProcessToolRunner
- docs explain ffmpeg/melt/GPAC split
- `docs/roo-final-report.md` updated

## Acceptance Criteria
- Safe render runtime skeleton exists.
- Real tools can be added without rewriting business services.
- No unsafe command execution pattern is introduced.
- Next prompt should be `16-...`.
