# Watermark Template — Second Template Profile (P2T.3)

## Purpose

Add WatermarkTemplate as the second built-in Template profile to prove the Template System is not caption-only. Validates the role-based target model beyond captions.

## Why WatermarkTemplate

- Uses different target roles (MAIN_VIDEO + WATERMARK_IMAGE) vs caption (MAIN_VIDEO + CAPTION_TRACK)
- Uses different operations (ADD_WATERMARK, ADD_IMAGE_OVERLAY vs ADD_TEXT_OVERLAY)
- Uses different capabilities (IMAGE_OVERLAY, VIDEO_COMPOSITE vs TEXT_OVERLAY, SUBTITLE_BURN_IN)
- Proves multi-asset target binding (video + image vs video + text)

## Package Placement

`render-module/.../domain/template/profile/watermark/` — 3 classes.

## Built-in Watermark TemplateDefinition

```
id: builtin.watermark.basic
version: 1.0.0
type: WATERMARK
targetRoles: MAIN_VIDEO, WATERMARK_IMAGE
operations: ADD_WATERMARK, ADD_IMAGE_OVERLAY
capabilities: IMAGE_OVERLAY, VIDEO_COMPOSITE
```

## WatermarkTemplateApplicationInput

Internal input model:
- mainVideoProductId → MAIN_VIDEO target
- watermarkProductId → WATERMARK_IMAGE target
- placement, opacityPercent, marginX, marginY → parameters

## Mapping

| Source | Target |
|--------|--------|
| mainVideoProductId | TemplateTarget(MAIN_VIDEO, PRODUCT) |
| watermarkProductId | TemplateTarget(WATERMARK_IMAGE, PRODUCT) |
| placement | TemplateParameter |
| opacityPercent | TemplateParameter (0–100 validated) |
| marginX/marginY | TemplateParameter (non-negative validated) |

## Compiler

WatermarkTemplateApplicationCompiler:
- Validates MAIN_VIDEO target present
- Validates WATERMARK_IMAGE or LOGO target present
- Validates opacity 0–100
- Validates non-negative margins
- Returns provider-neutral TemplateApplicationResult

## Caption Template Compatibility

Existing CaptionTemplate profile unchanged. Profiles are distinct (different IDs, types, targets, operations).

## Safety

No provider/storage/raw-command fields. No FFmpeg/Remotion references.

## Follow-up

- P2W.0: Workflow semantic model
- P2P.0: Plugin registry
- P2T.4: CompositionTemplate or BrandTemplate as third profile
