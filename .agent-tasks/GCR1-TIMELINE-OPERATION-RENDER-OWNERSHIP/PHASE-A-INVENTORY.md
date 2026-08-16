# GCR-1 PHASE A — REALITY INVENTORY + MIGRATION MANIFEST

Base: 1a74a65e8ce403280f77de2a6edf73482aafc241 (tree 9352f70cbd6b6300d1e52805a45bdd994192a551)
Date: 2026-08-17

## Inventory (authoritative, recomputed on current main)

```
CURRENT_TIMELINE_NAMESPACE_PRODUCTION_TYPE_COUNT = 376
  (com.example.platform.render.domain.timeline.**, production src/main only)
CURRENT_OPERATION_PRODUCTION_TYPE_COUNT         = 25
  (render.domain.operation 13 + render.domain.plan 11 + render.app.plan 1)
CURRENT_RENDERPLAN_LIKE_TYPE_COUNT              = 33
  (repo-wide simple-name scan, render-module)
```

Note: the review estimate TIMELINE_SURFACE_TYPES ~= 595 is NOT reproduced by
the namespace count (376). The 595 estimate appears to count timeline-named
types repo-wide (incl. app/infrastructure/api layers); the implementation
authority is the namespace count + manifest below.

## Classification (417 manifest rows, 0 unclassified)

| Classification | Count | Target |
|----------------|-------|--------|
| TIMELINE_CANONICAL | 162 | timeline-module |
| TIMELINE_APPLICATION | 50 | timeline-module |
| OPERATION_CANONICAL | 18 | operation-module |
| OPERATION_APPLICATION | 7 | operation-module |
| RENDER_PLANNING | 108 | render-module |
| RENDER_EFFECT | 21 | render-module |
| RENDER_TRANSITION | 21 | render-module |
| RENDER_PROJECTION | 2 (TimelineSpec, TimelineTextOverlay) | render-module |
| INTERCHANGE | 9 | render-module |
| OBSOLETE_UNSHIPPED | 11 (root legacy Timeline* models) | render-module (DELETE) |
| PACKAGE_INFO | 8 | render-module |

RENDER_OWNED_INSIDE_TIMELINE_NAMESPACE = 135
  (RENDER_EFFECT 21 + RENDER_TRANSITION 21 + RENDER_PLANNING-in-namespace 93;
  excludes the 15 app.planner/domain.planner types outside the namespace)

## Feasibility proof (dependency direction)

- timeline-module target types import render.* NON-timeline packages: 0
- timeline-module target types import render-kept types: 0
- operation-module target types import render-kept types: 0
- operation → render.domain.timeline.* imports: 13 types (GroupId, GroupRelationship,
  PlaybackDirection, ResolvedScope, ScopeResolver, SelectionSpec, SemanticRelationship,
  SyncRelationship, TextElement, TextElementId, TimelineClipId, TimelineContentDigester,
  TimelineDocument, TimelineMetadata) — all MOVE to timeline-module, so the
  operation→timeline dependency is acyclic by construction.
- CORE_MODULE_DEPENDENCY_CYCLE_COUNT = 0 (expected; no render/extension/workflow
  references from either new module).

## Justified dependencies beyond the §8 list

1. timeline-module → storage-module (contract only): MediaStreamSourceBinding pins
   `com.example.platform.storage.contract.ContentDigest` (frozen TIMELINE_V2
   source-binding contract). Same pattern already used by artifact-module
   (Artifact.java / ArtifactCommitRequest.java / ArtifactQueryService.java).
   Acyclic (storage-module has zero render references).
2. TimelineContentDigester carries `org.springframework.stereotype.Component` —
   remove the annotation during migration (digester is constructed manually;
   keeps timeline-module annotation-free).

## §13 defect confirmed

OperationBatch / OperationDefinition / OperationInstance / OperationRequest /
ParameterDigest (5 types) import `com.example.platform.extension.domain.ContractVersion`.
OperationDefinitionVersion (operation-module) replaces it; all built-in
OperationDefinitions migrate (OperationDefinition.V1 uses ContractVersion.of(1,0)).

## RenderPlan inventory (33 simple-name hits)

- Plain `RenderPlan` class name occurs THREE times:
  com.example.platform.render.domain.RenderPlan
  com.example.platform.render.infrastructure.RenderPlan
  com.example.platform.render.infrastructure.renderplan.RenderPlan
  -> AMBIGUOUS_RENDERPLAN_SIMPLE_NAME_COUNT = 3 (must be 0 after GCR-1)
- FFmpegLibassBasicRenderPlan (domain.timeline.render.plan, 22 types) — precise
  role already; stays render-owned, package re-homed out of timeline namespace.
- RenderPlanService / RenderPlannerService / RenderPlanBridgeService /
  RenderPlanPolicyGuard / IncrementalRenderPlan(Service) / DefaultRenderPlanner /
  RenderPlanner / RenderPlanBuilder / RenderPlanExecutionService /
  BasicRenderPlanLocalExecutionAdapter / BasicRenderPlanLocalRunner /
  RenderPlanCalcNode / RenderPlanPolicy{Result,Status,Violation,ViolationType} —
  precise roles; full per-type disposition in migration-manifest.tsv §F analysis.

## Deliverables

- migration-manifest.tsv (417 rows: CURRENT_PATH, CURRENT_PACKAGE, TYPE, KIND,
  CLASSIFICATION, FINAL_MODULE, FINAL_PACKAGE, DISPOSITION, CALLER_FILES)
- generate-manifest.py (regenerable; rule-based + explicit override lists)
