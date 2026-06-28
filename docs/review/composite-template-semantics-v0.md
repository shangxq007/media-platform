# Composite Template Semantics v0 (P2T.4)

## Purpose

Composite Template is a TemplateDefinition composition model that composes Atomic Template profiles. It is NOT a WorkflowDefinition.

## CompositeTemplate vs AtomicTemplate

| Aspect | Atomic Template | Composite Template |
|--------|----------------|-------------------|
| Templates | Single profile | Multiple child profiles |
| Examples | CaptionTemplate, WatermarkTemplate | SocialShortVideo, EcommerceProductAd |
| Composition | None | Children + bindings + merge policy |
| Execution | Single TemplateApplicationRequest | Expanded child requests |

## CompositeTemplate vs WorkflowDefinition

| Aspect | CompositeTemplate | WorkflowDefinition |
|--------|------------------|-------------------|
| Purpose | Template composition | Processing flow orchestration |
| Steps | Children (template refs) | WorkflowSteps |
| Execution | Expands to child requests | Orchestrates step execution |
| Dependencies | Child ordering | Step dependencies |
| Engine | Template expansion | Workflow engine |

## CompositeTemplateDefinition

```
CompositeTemplateDefinition
  ├── id: CompositeTemplateDefinitionId
  ├── templateId: TemplateDefinitionId
  ├── version: TemplateVersion
  ├── metadata: TemplateDisplayMetadata
  ├── children: List<CompositeTemplateChild>
  ├── targetBindings: List<TemplateTargetBinding>
  ├── parameterBindings: List<TemplateParameterBinding>
  ├── mergePolicy: CompositeTemplateMergePolicy
  ├── conflictPolicy: CompositeTemplateConflictPolicy
  └── safeMetadata: Map<String, String>
```

## CompositeTemplateChild

```
CompositeTemplateChild
  ├── id: CompositeTemplateChildId
  ├── childTemplateId: TemplateDefinitionId
  ├── childTemplateVersion: TemplateVersion
  ├── order: CompositeTemplateChildOrder (non-negative)
  ├── required: boolean
  └── safeMetadata: Map
```

## Target Binding

Maps parent target roles to child target roles:

```
parent MAIN_VIDEO → CaptionTemplate MAIN_VIDEO
parent MAIN_VIDEO → WatermarkTemplate MAIN_VIDEO
parent CAPTION_TRACK → CaptionTemplate CAPTION_TRACK
parent WATERMARK_IMAGE → WatermarkTemplate WATERMARK_IMAGE
```

## Parameter Binding

Maps parent parameters to child parameters:

```
parent captionStyle.fontSize → CaptionTemplate fontSize
parent watermarkOpacity → WatermarkTemplate opacityPercent
```

Expressions are inert strings only — no SpEL/JS/Python.

## Merge Policy

ORDERED, BY_LAYER, BY_TIMELINE_TIME, BY_Z_INDEX, MERGE_IF_COMPATIBLE (vocabulary only)

## Conflict Policy

FAIL_FAST, PARENT_OVERRIDES, CHILD_OVERRIDES, MERGE_IF_COMPATIBLE, WARN_AND_CONTINUE, MANUAL_REVIEW_REQUIRED (vocabulary only)

## Expansion Plan

CompositeTemplateApplicationCompiler produces:
- One CompositeTemplateExpansionStep per child
- Each step contains a child TemplateApplicationRequest
- Targets and parameters mapped from parent request
- Provider-neutral, no rendering

## SocialShortVideo Example

```
SocialShortVideoTemplate
  → CaptionTemplate (order=0, required)
  → WatermarkTemplate (order=1, required)

Bindings:
  MAIN_VIDEO → both children MAIN_VIDEO
  CAPTION_TRACK → CaptionTemplate CAPTION_TRACK
  WATERMARK_IMAGE → WatermarkTemplate WATERMARK_IMAGE

Parameters:
  captionStyle → CaptionTemplate fontSize/fontFamily/placement
  watermarkOpacity → WatermarkTemplate opacityPercent
```

## Safety

- No provider/storage internals
- No FFmpeg commands
- No Remotion props
- No render execution
- No workflow execution
- No script execution
