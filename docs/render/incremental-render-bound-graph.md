# Incremental Render on the bound graph

Incremental Render submits a normal RenderJob. Timeline remains the immutable media-semantic authority; #20/#21/#22 produce its normal provider-bound graph. Runtime may consume authorized historical intermediate results for unchanged bound computations. The current Job always owns and commits a new final result.

## Request and result

`POST /api/incremental-render/plans` accepts:

```json
{
  "targetRevisionId": "target-revision",
  "referenceRevisionId": "previous-revision",
  "referenceRenderJobId": "completed-reference-job",
  "extent": {
    "startMillis": 0,
    "endMillis": 3000,
    "frameRateNumerator": 4,
    "frameRateDenominator": 1
  },
  "outputFormat": "MP4",
  "profile": "default"
}
```

Tenant, actor and project authority come from canonical authentication and the target revision, never body overrides. The reference Job must be readable, COMPLETED, in the same tenant/project, and pinned to the supplied reference revision. It may refer to any such authorized historical revision; direct parenthood is not required.

The response uses the existing RenderJob submission projection: `renderJobId`, `requestId`, `executionPlanId`, `status`, `workflowId`, `workflowStarted`. `201` accepts a new Job; `200` replays the same accepted submission. `PLANNED` describes acceptance/preparation, not completed media or a promise of a cache hit. Results, history, authorized downloads and cancellation use the existing RenderJob endpoints and lifecycle.

`Idempotency-Key` is scoped by tenant/project. Incremental request identity binds the normalized full target request, reference Job and reference revision. The same key with different target/reference semantics returns `409 IDEMPOTENCY_CONFLICT`. Reference/project authorization occurs before identity lookup, including replay. Repeating a key never constitutes a new editing increment. A separate key can submit the same revision and exercise computation reuse.

Reservation, Job, accepted context, revision, graph, incremental reference and durable DISPATCH intent commit atomically. Workflow dispatch/recovery retains stable non-reusable Workflow ID and existing preparation deadlines. No IncrementalJob or second workflow state machine exists.

## Change and dependency analysis

`IncrementalExecutionImpact` belongs to physical/bound planning. It compares real `ExecutionReuseKeyDeriver` results, preserving Provider Binding, source Artifact pins, source sample windows, execution coverage, exact boundary actions and Merkle predecessor semantics. It never guesses a key from a clip name or a dirty flag.

Changed/deleted decode coverage contributes old/new affected Timeline regions. Changes without a narrower source-region proof conservatively affect the full output extent. These regions are derived diagnostics, not another Timeline or executable command representation. Downstream composition/encoding eligibility follows actual graph dependencies.

- TRIM changes its source window/coverage and downstream keys; exact sibling computations can remain eligible.
- MOVE changes old/new output locations. Current keys conservatively include absolute coverage, so the moved decode is recomputed even if its source samples are identical; siblings can reuse.
- DELETE removes its old coverage and changes composition inputs. Canonical input ordinals or a multi-clip-to-single-clip boundary change can invalidate surviving task keys. Such tasks recompute; the key is not weakened to manufacture a hit.
- Effects, input-digest, extent or Provider Binding changes require exact key equality. Unknown or unproven semantics never authorize reuse. Unsupported rendering semantics still fail through ordinary planning/provider gates.

## Intermediate input authority

Only CACHEABLE computations with an unchanged real key, one authoritative intermediate output and a validated AVAILABLE `DERIVED_MEDIA` pin in the current project may skip execution. The final render master is always excluded. Conditional cacheability is not a fully pinned proof.

The reuse index retains its first winning computation publication. A winning intermediate may belong to a historical same-project Job other than the requested reference Job. The reference graph proves unchanged computation; Artifact authority separately validates the actual historical input's tenant/project, kind, state and digest. Runtime never relabels that Artifact as reference-owned or current-Job-owned.

Skipped tasks do not mint a new native grant, publish a current-Job output, invent a native success, alias an Artifact, or alter the existing exact-output reuse guard. Their pins enter the existing dependency seed-input boundary. Storage revalidates project scope, committed resource identity and content integrity when the downstream computation materializes them.

The final composition/materialization and current-Job final encode use ordinary Provider execution, native attempt/generation fencing, official Artifact Commit and Render acceptance. A historical final Artifact cannot be returned as the current result.

## Fallback, cancellation and recovery

A missing/unusable reference graph, cache miss, non-cacheable task, unavailable/corrupt intermediate or unauthorized cache record cannot grant reuse. Eligible work falls back to normal execution. Invalid request authority/reference identity is rejected rather than silently changing the requested identity.

Cancellation and Artifact publication retain the canonical Render Job lock and native publication transaction. Partial output does not imply Job success. Reading a historical input cannot publish a new cache winner for a skipped attempt. Unknown process termination keeps the existing recovery-hold behavior.

On an Activity response retry, an exact already-committed final result can be recovered before re-reading historical inputs only when the immutable Render execution/bound output, current native task success, attempt/generation and current-Job AVAILABLE RENDER_MASTER all agree. Existing completion fencing still applies. Before final commit, recovery uses revalidated historical inputs and genuine current-Job native successes; it never synthesizes skipped-task completion.

Internal Worker task decisions record actual semantic keys, cacheability, planning/resolver decisions, executed/skipped status, actual input/output pins and frozen binding. Provider implementation identities and storage paths remain internal, not public request authority.

## Legacy boundary

This path does not use legacy Pipeline DAG, `final_compose`, incremental plan/submit or Render Cache orchestration. Existing Delivery, MCP and Frontend consumers are not migrated or retired by this capability. Whole legacy retirement remains separate work.
