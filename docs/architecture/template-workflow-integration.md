# Template Workflow Integration

## Purpose

Define how TemplateApplication integrates with workflow orchestration for multi-step content production.

## Conceptual Workflow Steps

```
INGEST_PRODUCT       — Register/load source Products
ANALYZE_ASR          — Speech-to-text analysis
ANALYZE_SCENE        — Scene detection analysis
APPLY_TEMPLATE       — Apply TemplateDefinition to Products
COMPILE_TIMELINE     — Compile to TimelineSpec/TimelinePatch
RENDER_TIMELINE      — Execute through PLAN_BASED pipeline
LOOKUP_RESULT        — Query output Product status
DELIVER_PRODUCT      — Deliver output to consumer
```

## Example Workflows

### AutoCaptionShortVideoWorkflow

```
1. INPUT RAW_MEDIA Product
2. ASR produces captionSegments
3. APPLY_TEMPLATE basic-caption-template
   → targets: { CAPTION_TRACK: asr-output }
   → parameters: { fontFamily: "Inter", fontSize: 48 }
4. RENDER_TIMELINE
   → PLAN_BASED → FFmpeg/libass
5. RESULT_LOOKUP
   → READY FINAL_RENDER Product
6. DELIVER_PRODUCT
```

### ProductShowcaseWorkflow

```
1. INPUT main video, product image, logo
2. APPLY_TEMPLATE product-showcase-template
   → targets: { MAIN_VIDEO: main, PRODUCT_IMAGE: product, LOGO: logo }
3. APPLY_TEMPLATE brand-kit-template
   → targets: { BRAND_KIT: brand-ref }
4. RENDER_TIMELINE
5. DELIVER_PRODUCT
```

### MultiLanguageCaptionWorkflow

```
1. INPUT RAW_MEDIA Product
2. ASR produces captionSegments (language A)
3. TRANSLATE captionSegments → language B
4. APPLY_TEMPLATE caption-template (language A)
5. APPLY_TEMPLATE caption-template (language B)
6. RENDER_TIMELINE (both tracks)
7. DELIVER_PRODUCT
```

## Implementation Strategy

### Short-term (No workflow engine)

WorkflowSteps are executed by application service orchestration:

```
CaptionTemplateRenderService
  → validate
  → adapt to TimelineSpec
  → compile pipeline
  → execute
  → register output
```

This is the current P2C.1–P2C.6 pattern.

### Medium-term (Lightweight workflow)

```
WorkflowDefinition
  ├── workflowId
  ├── steps: List<WorkflowStep>
  └── transitions: step completion → next step

WorkflowStep
  ├── stepId
  ├── type: APPLY_TEMPLATE, RENDER, ANALYZE, DELIVER
  ├── config: step-specific configuration
  └── dependencies: prerequisite step IDs
```

Orchestrated by a WorkflowApplicationService that executes steps in dependency order.

### Long-term (Engine integration)

Temporal, LiteFlow, or equivalent workflow engine can execute WorkflowSteps. The semantic model should not depend on a specific engine.

## WorkflowStep Type Examples

| Step Type | Description | Implementation |
|-----------|-------------|---------------|
| INGEST_PRODUCT | Load/register source Products | ProductRuntime |
| ANALYZE_ASR | Speech-to-text | External service |
| ANALYZE_SCENE | Scene detection | External service |
| APPLY_TEMPLATE | Apply template to targets | TemplateApplicationCompiler |
| COMPILE_TIMELINE | Compile to TimelineSpec | TemplateApplicationCompiler |
| RENDER_TIMELINE | Execute render | PLAN_BASED pipeline |
| LOOKUP_RESULT | Query result status | ProductRuntime |
| DELIVER_PRODUCT | Deliver output | Delivery service |

## Safety Rules

- WorkflowSteps cannot bypass PLAN_BASED
- WorkflowSteps cannot expose provider internals
- WorkflowSteps cannot execute arbitrary code
- Plugin WorkflowStepHandlers are sandboxed
- No workflow step can select providers directly

## Relationship to Existing Architecture

```
WorkflowDefinition
  → WorkflowStep(APPLY_TEMPLATE)
    → TemplateApplicationCompiler → TimelineSpec
    → WorkflowStep(RENDER_TIMELINE)
      → PLAN_BASED → LocalExecutionPlanRunner → FFmpeg
      → Product
```

The workflow layer orchestrates; it does not replace the render pipeline.
