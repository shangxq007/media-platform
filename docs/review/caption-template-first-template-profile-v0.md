# Caption Template as First Template Profile (P2T.2)

## Purpose

Maps the existing Caption Template Render MVP as the first built-in profile of the General Template System (ADR-022). Existing caption-template APIs and render behavior remain unchanged.

## Package Placement

`render-module/.../domain/template/profile/caption/` — 3 classes.

## Built-in Caption TemplateDefinition

```
id: builtin.caption.basic
version: 1.0.0
type: CAPTION
targetRoles: MAIN_VIDEO, CAPTION_TRACK
operations: ADD_TEXT_OVERLAY, APPLY_TEXT_STYLE
capabilities: TEXT_OVERLAY, SUBTITLE_BURN_IN
```

## Mapping: CaptionTemplateRenderRequest → TemplateApplicationRequest

| Source | Target |
|--------|--------|
| sourceProductId | TemplateTarget(MAIN_VIDEO, PRODUCT) |
| captionSegments | TemplateTarget(CAPTION_TRACK, TEXT) + segmentCount metadata |
| template/style | TemplateParameter (fontFamily, fontSize, placement) |
| outputProfile | TemplateParameter (outputWidth, outputHeight, outputFps, outputContainer) |
| projectId | projectId |
| safeMetadata | safeMetadata |

No provider/storage/raw command fields created.

## Caption Template Application Compiler

`CaptionTemplateApplicationCompiler` implements `TemplateApplicationCompiler`:
- `supports()` returns true for builtin.caption.basic
- `compile()` validates MAIN_VIDEO and CAPTION_TRACK targets present
- Returns provider-neutral `TemplateApplicationResult`
- Does not call FFmpeg, Remotion, StorageRuntime, or ProductRuntime

## Caption Template API Compatibility

Existing APIs unchanged:
- `POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}`
- CaptionTemplateRenderRequest/Response/Controller/Service unchanged
- CaptionTemplateTimelineAdapter unchanged

## Safety Boundaries

- No provider names in profile/mapper/compiler
- No storage internals
- No FFmpeg commands
- No Remotion props
- No public Template API

## Follow-up

- P2T.3: WatermarkTemplate as second profile
- P2W.0: Workflow semantic model
- P2P.0: Plugin registry
