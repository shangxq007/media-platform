# Remotion Input Props Generation v0

## Overview

Internal Remotion execution document generation — produces a deterministic `RemotionInputProps` planning document from a NormalizedTimeline. This is a POC document-generation foundation only. Remotion is NOT executed.

## Status

- **Document generation:** IMPLEMENTED (v0)
- **Remotion execution:** NOT IMPLEMENTED
- **Production eligibility:** NOT ENABLED
- **Auto-dispatch:** NOT ENABLED
- **FFmpeg remains the only executable provider**

## Domain Model

### RemotionInputProps

Immutable record with:
- `schemaVersion` — "remotion-input-props-v0"
- `composition` — width, height, fps, durationInFrames, durationSeconds
- `timeline` — tracks, clips, totalDurationSeconds
- `mediaAssets` — assetId, mediaType, format, duration, dimensions
- `captions` — text, timing, font, position, color
- `fonts` — family, weight, style
- `output` — outputProfile, dimensions, fps, container, codec
- `metadata` — generationReady=false, provider=remotion

### Generator

`RemotionInputPropsGenerator` — maps NormalizedTimeline → RemotionInputProps:
- Single primary video input
- Caption layers if present
- Font references from captions
- Output profile metadata
- No materialization, no StorageRuntime, no ProductRuntime

### Serializer

`RemotionInputPropsSerializer` — deterministic JSON serialization:
- Stable field ordering
- No timestamps, no random UUIDs
- Sorted metadata keys

### Validator

`RemotionInputPropsValidator` — safety and completeness checks:
- Schema version present
- Composition fields positive
- Media assets present
- Caption time ranges valid
- No local paths
- No storage internals
- No raw commands
- No secrets

## Safety Rules

Props must NOT include:
- Local materialized paths
- Bucket/objectKey/rootPath/relativePath
- Signed URLs
- Raw FFmpeg/Remotion commands
- Process environment
- Secrets or credentials
- External JS/template references
- Network URLs
- npm/npx/node execution references

## Unsupported v0 Features

- Multiple inputs
- Complex effects/transitions
- 3D
- Arbitrary JS/React components
- User-uploaded Remotion projects
- External npm packages
- Network fetch
- Dynamic imports
- Custom plugins

## Architecture Rules

- Remotion remains POC/SPIKE — not production eligible
- Remotion must not be autoDispatch enabled
- FFmpeg remains the only executable provider
- RenderPlanPolicyGuard still rejects Remotion execution
- LocalExecutionPlanRunner still FFmpeg-only
- RemotionInputProps is internal only — not public API

## Future P1R Execution Prerequisites

1. RemotionInputProps generation (✅ done)
2. Remotion CLI availability check
3. Remotion render command construction
4. Output verification
5. Policy guard allowlist for Remotion
6. Execution environment target for Remotion
7. Production eligibility review
