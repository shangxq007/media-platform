# ADR-022: General Template System, Workflow Integration, and Plugin Extension Points

## Status

Accepted

## Context

The platform has completed a Caption Template Render MVP (P2C.0–P2C.6) that provides a vertical backend loop:

```
CaptionTemplateRenderRequest → PLAN_BASED → FFmpeg/libass → READY FINAL_RENDER Product → safe result lookup
```

This MVP is subtitle/caption-specific. The platform needs a generalized template system that:

1. Extends beyond captions to text, image, video, audio, layout, transition, brand, and composition operations.
2. Uses role-based target binding instead of fixed request fields.
3. Compiles to TimelineSpec/TimelinePatch rather than directly to provider commands.
4. Integrates with workflow orchestration for multi-step content production.
5. Supports controlled plugin extension points.
6. Preserves the completed Caption Template MVP as the first vertical profile.

## Decision

### Template Definition Model

```text
TemplateDefinition — reusable parameterized Timeline operation bundle
  - templateId, name, version
  - targetRoles: list of TemplateTargetRole definitions
  - parameters: list of TemplateParameter definitions
  - operations: list of TemplateOperation definitions
  - constraints: list of TemplateConstraint
  - requiredCapabilities: list of capability codes
  - metadata: safe metadata only
```

TemplateDefinition does NOT contain:
- Provider names or selection
- Storage internals
- FFmpeg commands
- Remotion props
- Bucket/objectKey/signedUrl

### Template Application Model

```text
TemplateApplicationRequest — binds Products and parameters to a TemplateDefinition
  - templateId (references TemplateDefinition)
  - targets: list of TemplateTarget (role → Product mapping)
  - parameters: parameter overrides
  - outputProfile: output specification
```

### Target Role Model

Templates bind to assets through role-based targets:

| Role | TargetType | Example |
|------|-----------|---------|
| MAIN_VIDEO | PRODUCT | Primary video source |
| B_ROLL_VIDEO | PRODUCT | Secondary video |
| CAPTION_TRACK | TEXT | Caption segments |
| TITLE_TEXT | TEXT | Title overlay |
| SUBTITLE_TEXT | TEXT | Subtitle overlay |
| LOGO | PRODUCT | Brand logo image |
| WATERMARK_IMAGE | PRODUCT | Watermark overlay |
| PRODUCT_IMAGE | PRODUCT | Product showcase image |
| BACKGROUND_MUSIC | PRODUCT | Background audio |
| VOICEOVER_AUDIO | PRODUCT | Voice track |
| INTRO_CLIP | PRODUCT | Intro video |
| OUTRO_CLIP | PRODUCT | Outro video |
| BRAND_KIT | REFERENCE | Brand style reference |

Target shape:
```json
{
  "role": "MAIN_VIDEO",
  "targetType": "PRODUCT",
  "productId": "prod-main-video"
}
```

This is preferred over fixed fields (videoProductId, logoProductId) because it supports extensible multi-asset templates.

### TemplateOperation Model

TemplateOperation is a provider-neutral Timeline operation intent:

| Operation | Required Capabilities |
|-----------|---------------------|
| ADD_TEXT_OVERLAY | TEXT_OVERLAY |
| APPLY_TEXT_STYLE | TEXT_OVERLAY |
| ADD_IMAGE_OVERLAY | IMAGE_OVERLAY |
| ADD_VIDEO_CLIP | VIDEO_COMPOSITE |
| ADD_AUDIO_TRACK | AUDIO_MIX |
| DUCK_AUDIO | AUDIO_MIX |
| ADD_WATERMARK | IMAGE_OVERLAY |
| ADD_TRANSITION | TRANSITION |
| FIT_TO_CANVAS | LAYOUT_COMPOSITION |
| CROP | VIDEO_COMPOSITE |
| TRIM | VIDEO_COMPOSITE |
| ADD_INTRO | VIDEO_COMPOSITE |
| ADD_OUTRO | VIDEO_COMPOSITE |
| APPLY_BRAND_STYLE | LAYOUT_COMPOSITION |

**Critical rule:** TemplateOperation compiles to TimelinePatch or TimelineSpec. It does NOT compile directly to FFmpeg commands or Remotion props.

### Capability Binding Chain

```text
TemplateDefinition → requiredCapabilities
  → TemplateApplicationCompiler → TimelinePatch/TimelineSpec
  → LogicalCapabilityGraph → ProviderBindingPlan
  → RenderExecutionPlan → ExecutionEnvironment → Product
```

Templates declare required capabilities, not provider names.

### Plugin Extension Points

| Extension Point | Responsibility | Safety |
|----------------|---------------|--------|
| TemplateProvider | Supply template definitions | No arbitrary code |
| TemplateValidator | Validate template application | Pure validation |
| TemplateApplicationCompiler | Compile to TimelinePatch | No direct commands |
| TemplateOperationCompiler | Compile individual operations | No provider selection |
| WorkflowStepHandler | Execute workflow steps | Sandboxed |
| CapabilityProvider | Declare capability requirements | Read-only |
| ProviderExecutionDocumentGenerator | Generate execution documents | Internal only |

**Plugin safety rules:**
- No arbitrary user code execution in early versions
- No remote plugin execution
- No plugin may bypass ProductRuntime, StorageRuntime, Timeline, PLAN_BASED, or ProviderBinding
- No plugin may expose provider/storage internals
- No plugin may execute Remotion unless trusted execution policy enables it

### Workflow Integration

TemplateApplication is a WorkflowStep. Conceptual workflow steps:

```
INGEST_PRODUCT → ANALYZE → APPLY_TEMPLATE → COMPILE_TIMELINE → RENDER → RESULT_LOOKUP → DELIVER
```

Example workflows:
- **AutoCaptionShortVideo**: INPUT → ASR → APPLY_TEMPLATE(caption) → RENDER → DELIVER
- **ProductShowcase**: INPUT(video,image,logo) → APPLY_TEMPLATE(showcase) → APPLY_TEMPLATE(brand) → RENDER → DELIVER

No workflow engine required immediately. Short-term: application-service orchestration. Future: Temporal, LiteFlow, or equivalent.

## Consequences

### Positive
- Caption Template MVP preserved and extended naturally
- Provider selection remains hidden behind PLAN_BASED
- Plugin extensibility without safety compromise
- Workflow orchestration semantic model ready

### Negative
- Additional abstraction layers before execution
- Template compilation adds pipeline stages
- Plugin safety boundary enforcement required

### Risks
- Over-abstraction if template system is not grounded by real use cases
- Plugin safety boundary enforcement must be thorough

## References

- Caption Template MVP (P2C.0–P2C.6)
- PLAN_BASED render pipeline
- Capability binding architecture
- Provider binding plan
- Execution plan
- Workflow integration design (docs/architecture/template-workflow-integration.md)
- General template system design (docs/architecture/general-template-system.md)
