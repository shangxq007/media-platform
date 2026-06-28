# Workflow Semantic Model v0 (P2W.0)

## Purpose

Compile-safe Workflow semantic model for describing reusable processing flows. WorkflowStep(APPLY_TEMPLATE) references TemplateApplicationRequest, enabling workflows to compose template applications without provider/backend/storage knowledge.

## Package Placement

`render-module/.../domain/workflow/` — 16 types.

## WorkflowDefinition

```
WorkflowDefinition
  ├── id: WorkflowDefinitionId
  ├── version: WorkflowVersion
  ├── metadata: WorkflowDisplayMetadata
  ├── inputs: List<WorkflowInput>
  ├── steps: List<WorkflowStep> (non-empty)
  ├── outputs: List<WorkflowOutput>
  └── safeMetadata: Map<String, String>
```

## WorkflowStep

```
WorkflowStep
  ├── id: WorkflowStepId
  ├── type: WorkflowStepType
  ├── dependencies: List<WorkflowStepDependency>
  ├── parameters: Map<String, WorkflowParameterValue>
  ├── templateApplicationSpec: WorkflowTemplateApplicationStepSpec (null for non-template steps)
  └── safeMetadata: Map<String, String>
```

## WorkflowStepType

INGEST_PRODUCT, ANALYZE_ASR, ANALYZE_SCENE, VALIDATE_INPUT, NORMALIZE_TIMELINE, APPLY_TEMPLATE, COMPILE_TIMELINE, RENDER_TIMELINE, REGISTER_PRODUCT, LOOKUP_RESULT, DELIVER_PRODUCT, NOTIFY

## APPLY_TEMPLATE Step

```
WorkflowStep(APPLY_TEMPLATE)
  → WorkflowTemplateApplicationStepSpec
    → templateId: TemplateDefinitionId
    → templateVersion: TemplateVersion
    → templateApplicationRequest: TemplateApplicationRequest
```

## Template Composition

WorkflowStep(APPLY_TEMPLATE) can reference:
- CaptionTemplate profile (builtin.caption.basic)
- WatermarkTemplate profile (builtin.watermark.basic)
- Future templates

## Example Workflow

```
auto-caption-workflow:
  1. INGEST_PRODUCT
  2. APPLY_TEMPLATE(builtin.caption.basic) → depends on step 1
  3. RENDER_TIMELINE → depends on step 2
  4. LOOKUP_RESULT → depends on step 3
  5. DELIVER_PRODUCT → depends on step 4
```

## Safety Boundaries

- No provider/backend/storage internals
- No FFmpeg commands
- No Remotion props
- No execution environment IDs
- No queue/topic/worker IDs
- No signed URLs or local paths

## Not Implemented

- Workflow engine
- Workflow execution
- Workflow persistence
- Public workflow API
- Plugin runtime
- Temporal/OpenCue/LiteFlow integration

## Follow-up

- P2W.1: Lightweight workflow application service (dry-run planner)
- P2P.0: Plugin registry
