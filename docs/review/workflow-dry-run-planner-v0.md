# Workflow Dry-run Planner v0 (P2W.1)

## Purpose

Validates workflow graph structure, detects cycles, resolves deterministic step order, and summarizes APPLY_TEMPLATE steps without executing templates, workflows, rendering, storage, or product operations.

## Package Placement

`render-module/.../domain/workflow/planning/` — 12 types.

## WorkflowDryRunPlan

```
WorkflowDryRunPlan
  ├── id: WorkflowDryRunPlanId
  ├── workflowDefinitionId: WorkflowDefinitionId
  ├── workflowVersion: WorkflowVersion
  ├── steps: List<WorkflowDryRunStep>
  ├── issues: List<WorkflowDryRunIssue>
  ├── valid: boolean
  └── safeMetadata: Map<String, String>
```

## WorkflowDryRunStep

```
WorkflowDryRunStep
  ├── stepId: WorkflowStepId
  ├── stepType: WorkflowStepType
  ├── order: int
  ├── status: WorkflowDryRunStepStatus
  ├── templateSummary: WorkflowTemplateStepDryRunSummary (null for non-template steps)
  ├── issues: List<WorkflowDryRunIssue>
  └── safeMetadata: Map<String, String>
```

## Graph Validation

WorkflowGraphValidator checks:
- Duplicate step IDs → BLOCKING
- Unknown dependencies → BLOCKING
- APPLY_TEMPLATE without spec → ERROR

## Cycle Detection

WorkflowCycleDetector uses Kahn's algorithm:
- Returns empty list if acyclic
- Returns BLOCKING issue if cycle detected
- Handles self-cycles

## Topological Ordering

WorkflowStepOrderResolver:
- Deterministic topological order
- Preserves original step list order for independent steps
- Rejects cycles (returns empty list)

## APPLY_TEMPLATE Recognition

WorkflowTemplateStepDryRunSummary:
- templateId, templateVersion, templateKind
- targetCount, parameterCount
- compositeCandidate (heuristic based on template ID)

## Caption/Watermark/Composite Compatibility

- CaptionTemplate steps get proper summary with caption template ID
- WatermarkTemplate steps get proper summary with watermark template ID
- CompositeTemplate candidates detected by template ID pattern

## Safety Boundaries

- No render execution
- No template compiler calls
- No StorageRuntime/ProductRuntime calls
- No provider/storage internals
- No Remotion references

## Follow-up

- P2E.0: Workflow Runtime Evaluation
- P2P.0: Plugin registry
