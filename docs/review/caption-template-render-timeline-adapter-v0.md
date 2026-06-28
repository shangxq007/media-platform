# Caption Template Render Timeline Adapter v0

## Overview

Adapter that maps `CaptionTemplateRenderRequest` into the existing PLAN_BASED render pipeline.

## Architecture

```text
CaptionTemplateRenderRequest
  → CaptionTemplateRenderContractValidator.validate()
  → CaptionTemplateTimelineAdapter.adapt() → TimelineSpec
  → TimelineNormalizationService → NormalizedTimeline
  → ArtifactGraphCompiler → LogicalCapabilityGraph
  → ProviderBindingCompiler → ProviderBindingPlan
  → RenderExecutionPlanCompiler → RenderExecutionPlan
  → LocalExecutionPlanRunner → FFmpeg/libass execution
  → StorageRuntime output registration
  → ProductRuntime READY Product
  → CaptionTemplateRenderResult
```

## Components

### CaptionTemplateTimelineAdapter

Maps request → TimelineSpec:
- captionSegments → TimelineTextOverlay list
- style/placement → fontFamily/fontSize/color/position
- outputProfile → TimelineOutputSpec
- sourceProductId → TimelineAssetRef on video track
- Does NOT call FFmpeg, libass, StorageRuntime, or ProductRuntime

### CaptionTemplateRenderResultMapper

Maps internal results → CaptionTemplateRenderResult:
- Filters out provider/storage internals
- Safe result without raw commands, paths, or graph IDs

### CaptionTemplateRenderService

Orchestrates the full flow:
1. Validate request
2. Adapt to TimelineSpec
3. Resolve input products (asset ID → product ID)
4. Compile pipeline (normalize → artifact → capability → binding → plan)
5. Execute through LocalExecutionPlanRunner
6. Extract output product
7. Map to CaptionTemplateRenderResult

## Input Product Resolution

`sourceProductId` in the request is an asset ID (e.g., the ownerAssetId of a RAW_MEDIA Product). The service resolves it to actual Product IDs using `TimelineInputProductResolver`, following the same pattern as the existing `TimelineRevisionRenderService`.

## Safety Rules

- No direct FFmpeg call from caption service
- No Remotion execution
- No provider selection
- No storage internals in result
- No raw commands in result
- No graph/plan IDs in result
- Validates before adapting
- Invalid request returns validation errors, not exceptions

## Public API Safety

- CaptionTemplateRenderResult has no provider/backend/storage internals
- No graph IDs, plan IDs, correlation IDs in result
- No raw commands or local paths in result

## Follow-up

- P2C.2: Wire into public API endpoint if needed
- P2C.2: Add audit/correlation events to caption render
- P2C.3: Advanced template features
