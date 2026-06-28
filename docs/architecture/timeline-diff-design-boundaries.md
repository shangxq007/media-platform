# Timeline Diff Design Boundaries

## 1. Purpose

Define the design boundaries for media-platform's future Canonical Timeline Diff model. Distinguish between OTIO exchange diff, platform-owned timeline diff, template/workflow diff, and artifact/cache impact.

## 2. Timeline Diff Layers

| Layer | Description | Platform-owned? |
|-------|-------------|----------------|
| OTIO Exchange Diff | External format differences | No (adapter) |
| Canonical Timeline Diff | Platform semantic diff | Yes (required) |
| TemplateApplication Diff | Template-applied changes | Yes (required) |
| CompositeTemplate Diff | Composite template changes | Yes (required) |
| WorkflowStep Diff | Workflow step changes | Yes (required) |
| Artifact DAG Impact | Render cache invalidation | Yes (required) |
| Product Lineage Impact | Provenance changes | Yes (required) |

## 3. OTIO Exchange Diff

- Compares OTIO JSON structures
- Detects track/clip/transition changes
- External tool (vedit, OTIO library)
- Input signal only, not canonical

## 4. Canonical Timeline Diff

Platform-owned diff covering:
- Track added/removed/reordered
- Clip added/removed/moved/trimmed
- Asset binding changed
- Caption segment changed
- Text style changed
- Watermark changed
- Output profile changed
- Timeline duration changed
- Metadata-only change
- Render-impacting change

## 5. TemplateApplication Diff

- Template ID/version changes
- Target role changes
- Parameter value changes
- New/removed operations

## 6. CompositeTemplate Diff

- Child template additions/removals
- Target binding changes
- Parameter binding changes
- Merge policy changes

## 7. WorkflowStep(APPLY_TEMPLATE) Diff

- Step additions/removals
- Dependency changes
- Template spec changes
- Parameter overrides

## 8. Artifact DAG Impact Diff

- Which artifacts must re-render
- Which capability nodes affected
- Which provider binding decisions affected

## 9. Render Cache Impact Diff

- Which outputs can be reused
- Which cache keys invalidated
- Incremental render eligibility

## 10. Product Lineage Impact Diff

- Output Product validity changes
- ProductDependency link changes
- Provenance metadata changes

## 11. Required Platform-owned Types

Future domain types (not implemented):

```
TimelineDiff — semantic diff between two timelines
TimelinePatch — set of change operations
TimelineChangeOperation — single change operation
TimelineConflict — conflicting changes
TimelineMergePolicy — conflict resolution rules
TimelineVersionGraph — version history DAG
TimelineCommit — immutable version snapshot
TimelineCommitId — content-addressed commit identity
TimelineSemanticHash — content-addressed timeline identity
TimelineRenderImpact — render cache invalidation analysis
ArtifactDAGImpact — artifact graph impact analysis
ProductLineageImpact — lineage provenance analysis
TemplateApplicationDiff — template application changes
CompositeTemplateDiff — composite template changes
WorkflowApplyTemplateStepDiff — workflow step changes
```

## 12. External Reference Boundaries

| System | Boundary | Used For |
|--------|----------|----------|
| vedit | POC/benchmark only | OTIO diff reference |
| pyvedit | Offline evaluation | Diff/merge benchmark |
| OpenTimelineIO | Exchange format | Input parsing |
| Git | Conceptual model | Version DAG semantics |
| Merkle-DAG | Conceptual model | Content-addressed identity |

## 13. vedit Boundary

- vedit operates on OTIO timelines
- vedit provides diff/merge/branch at OTIO level
- vedit does not understand Template/Workflow/Artifact/Product layers
- vedit is reference/POC, not canonical source

## 14. Merkle-DAG Boundary

- Merkle-DAG provides content-addressed identity concepts
- Platform may use hash-linked version nodes
- Platform does not need IPFS/IPLD runtime
- Platform does not need Merkle-CRDT runtime

## 15. Non-goals

- Replace Git
- Implement IPFS/IPLD
- Implement vedit runtime
- Execute user-submitted Python merge code
- Expose provider internals through diff APIs
- Create public diff API now

## 16. Future ADR Candidates

| Candidate | Problem | Trigger |
|-----------|---------|---------|
| Canonical Timeline Diff | How to diff platform timelines | Timeline versioning need |
| OTIO Diff Adapter | How to map OTIO diff to canonical diff | OTIO integration need |
| Timeline Merge Conflicts | How to classify and resolve conflicts | Concurrent editing need |
| Content-addressed Hash | How to compute stable timeline identity | Cache identity need |
| Render Impact Analysis | How to map diff to artifact invalidation | Incremental render need |
