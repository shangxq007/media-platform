# General Template System

## Purpose

Define the conceptual model for a general-purpose template system that extends beyond the Caption Template MVP to support text, image, video, audio, layout, transition, brand, and composition operations.

## Non-Goals

- Template marketplace (future)
- User-uploaded template plugins (future)
- Remote plugin execution (never without explicit trusted policy)
- Provider selection through public API (never)
- Direct FFmpeg/Remotion command compilation from templates (never)

## Core Model

### TemplateDefinition

A reusable, parameterized Timeline operation bundle.

```
TemplateDefinition
  ├── templateId: stable identifier
  ├── name: human-readable name
  ├── version: semantic version
  ├── targetRoles: required asset roles
  ├── parameters: parameterized inputs
  ├── operations: ordered list of TemplateOperations
  ├── constraints: bounds and rules
  ├── requiredCapabilities: capability codes for provider binding
  └── metadata: safe metadata only
```

**Must not contain:** provider names, storage internals, FFmpeg commands, Remotion props, signed URLs.

### TemplateApplicationRequest

Binds concrete Products and parameter values to a TemplateDefinition.

```
TemplateApplicationRequest
  ├── templateId: references TemplateDefinition
  ├── targets: List<TemplateTarget> — role → product mapping
  ├── parameters: parameter overrides
  └── outputProfile: output specification
```

### TemplateTarget

Maps a role to a concrete asset:

```json
{
  "role": "MAIN_VIDEO",
  "targetType": "PRODUCT",
  "productId": "prod-main-video"
}
```

### TemplateTargetRole

Defines what a template needs from the caller:

| Role | TargetType | Description |
|------|-----------|-------------|
| MAIN_VIDEO | PRODUCT | Primary video source |
| B_ROLL_VIDEO | PRODUCT | Secondary video |
| CAPTION_TRACK | TEXT | Caption segments |
| TITLE_TEXT | TEXT | Title overlay |
| SUBTITLE_TEXT | TEXT | Subtitle overlay |
| LOGO | PRODUCT | Brand logo |
| WATERMARK_IMAGE | PRODUCT | Watermark |
| PRODUCT_IMAGE | PRODUCT | Product showcase |
| BACKGROUND_MUSIC | PRODUCT | Background audio |
| VOICEOVER_AUDIO | PRODUCT | Voice track |
| INTRO_CLIP | PRODUCT | Intro video |
| OUTRO_CLIP | PRODUCT | Outro video |
| BRAND_KIT | REFERENCE | Brand style reference |

### TemplateParameter

Parameterized input for a template:

```
TemplateParameter
  ├── parameterId: stable identifier
  ├── name: human-readable name
  ├── type: STRING, NUMBER, COLOR, FONT, DURATION, POSITION, ENUM
  ├── required: boolean
  ├── defaultValue: default if not provided
  └── constraints: bounds, allowed values
```

### TemplateOperation

Provider-neutral Timeline operation intent:

| Operation | Description |
|-----------|-------------|
| ADD_TEXT_OVERLAY | Add text at position/time |
| APPLY_TEXT_STYLE | Apply style to text |
| ADD_IMAGE_OVERLAY | Add image overlay |
| ADD_VIDEO_CLIP | Add video clip to track |
| ADD_AUDIO_TRACK | Add audio to timeline |
| DUCK_AUDIO | Reduce audio under voice |
| ADD_WATERMARK | Add watermark overlay |
| ADD_TRANSITION | Add transition between clips |
| FIT_TO_CANVAS | Fit content to output dimensions |
| CROP | Crop video/image |
| TRIM | Trim clip duration |
| ADD_INTRO | Prepend intro clip |
| ADD_OUTRO | Append outro clip |
| APPLY_BRAND_STYLE | Apply brand styling |

**Critical:** Operations compile to TimelinePatch/TimelineSpec, never directly to FFmpeg commands or Remotion props.

### TemplateConstraint

Rules that constrain template application:

```
TemplateConstraint
  ├── type: MIN_DURATION, MAX_DURATION, ASPECT_RATIO, MAX_TRACKS, etc.
  └── value: constraint value
```

### TemplateCapabilityRequirement

Declares what capabilities the template needs:

```
TEXT_OVERLAY, IMAGE_OVERLAY, AUDIO_MIX, VIDEO_COMPOSITE,
TRANSITION, SUBTITLE_BURN_IN, LAYOUT_COMPOSITION
```

These feed into the ProviderBindingPlan via capability binding.

## Template Compilation

```
TemplateApplicationRequest
  → TemplateApplicationCompiler
  → TemplateOperationCompiler (per operation)
  → TimelinePatch or TimelineSpec
  → NormalizedTimeline (existing)
  → PLAN_BASED pipeline (existing)
```

The compiler is provider-neutral. Provider selection happens downstream through capability binding.

## Plugin Extension Points

| Extension | Responsibility | Safety |
|-----------|---------------|--------|
| TemplateProvider | Supply template definitions | Read-only, no arbitrary code |
| TemplateValidator | Validate application | Pure validation, no side effects |
| TemplateApplicationCompiler | Compile to Timeline | No direct commands |
| TemplateOperationCompiler | Compile operations | No provider selection |
| WorkflowStepHandler | Execute workflow steps | Sandboxed |
| CapabilityProvider | Declare capabilities | Read-only |
| ProviderExecutionDocumentGenerator | Generate execution docs | Internal only |

**Safety rules:**
- No arbitrary user code execution
- No remote plugin execution
- Plugins cannot bypass ProductRuntime, StorageRuntime, Timeline, PLAN_BASED, or ProviderBinding
- Plugins cannot expose provider/storage internals
- Plugins cannot execute Remotion without trusted execution policy

## Caption Template as First Vertical Profile

The existing CaptionTemplateRenderRequest/Service/Controller is the first Template Application profile. It maps to:

- TemplateDefinition: basic-caption-template
- TargetRole: CAPTION_TRACK
- Operations: ADD_TEXT_OVERLAY, APPLY_TEXT_STYLE
- Capabilities: TEXT_OVERLAY, SUBTITLE_BURN_IN

Future profiles:
- WatermarkTemplate: WATERMARK_IMAGE target, ADD_WATERMARK operation
- ProductShowcaseTemplate: MAIN_VIDEO + PRODUCT_IMAGE + LOGO targets
- BrandKitTemplate: BRAND_KIT target, APPLY_BRAND_STYLE operation

## Safety Boundaries

1. Templates compile to Timeline, not to provider commands
2. Templates declare capabilities, not provider names
3. PLAN_BASED / capability binding / provider binding decides execution
4. No storage internals in template APIs
5. No provider selection in public APIs
6. Remotion remains non-executable without trusted policy
7. Plugin code is sandboxed and cannot bypass core boundaries

## Future Implementation Phases

| Phase | Description |
|-------|-------------|
| P2T.1 | TemplateDefinition / TemplateApplication domain skeleton |
| P2T.2 | CaptionTemplate mapped as first Template profile |
| P2T.3 | WatermarkTemplate as second profile |
| P2W.0 | Workflow semantic model |
| P2W.1 | Lightweight workflow application service |
| P2P.0 | Controlled plugin registry |
