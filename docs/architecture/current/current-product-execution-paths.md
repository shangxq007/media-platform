---
metadata_schema_version: 1
document_id: "architecture-current-product-execution-paths"
title: "Current Product Execution Paths"
artifact_type: "ARCHITECTURE_DOCUMENT"
domain: "architecture-governance"
authority_class: "NORMATIVE_SUPPORTING"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-11-15"
last_reviewed_at: "2026-11-15"
review_cadence_days: 30
supersedes: []
superseded_by: []
canonical_contracts: ["api", "schema-intent"]
source_of_truth_domains: ["architecture", "api-contracts", "database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Current Product Execution Paths

Every statement here is **CURRENT** and verified against source, executable contracts and accepted
contracts. Source code, controller/DTO definitions, migration bytes and guards remain the authority
for implemented behaviour; this page only names the current paths so agents stop rediscovering or
re-implementing them. Historical reports and dated reviews are not evidence for these statements.

## Formal render paths

1. **Ordinary (full) render** is `POST /api/render-jobs`
   ([RenderJobSubmissionController](../../../platform-app/src/main/java/com/example/platform/config/render/RenderJobSubmissionController.java)).
   The request carries only `revisionId`, `extent`, `outputFormat` and `profile`; tenant and initiator
   come from the authenticated context. An optional `Idempotency-Key` header is honoured.
2. **Incremental render** is `POST /api/incremental-render/plans`
   ([IncrementalRenderController](../../../platform-app/src/main/java/com/example/platform/config/render/IncrementalRenderController.java)).
   It converges on the same `RevisionRenderSubmissionService` acceptance chain.
3. **MCP media tools** (`McpMediaToolsController`) call the same application services for revision
   queries, clip Operations, render submission, job reads, delivery and Artifact access. They never
   choose a provider, graph or backend.

## First Render is an E2E scenario, not a product entry

4. First Render exercises the production path end to end. It owns **no** production Controller,
   orchestration service, database table, workflow type or idempotency store. The retired
   `/api/first-render/**` probe route is absent from production source and is protected by
   [scripts/check-architecture-drift.sh](../../../scripts/check-architecture-drift.sh); the scenario is
   driven by [scripts/dev/first-render](../../../scripts/dev/first-render) and
   [docs/testing/first-render.md](../../testing/first-render.md).

## Planning, binding and execution authority

5. **C20 determinism, C21 cache hook and C22 incremental replanning are formal planning/binding
   capabilities**, accepted in the roadmap-20 RenderPlan/RenderGraph contract. They are implemented
   through the render planning/binding services, not as experimental side paths.
6. **The formal worker and Artifact Authority are the only execution-submission system.** Render owns
   job transitions, history and final-output acceptance; native Execution owns fenced
   attempts/completions; Artifact owns commit and fencing; Storage owns bytes; Temporal owns scheduling,
   retry and cancellation. No compatibility or shadow execution adapter exists.
7. **Subtitle burn-in is executed by the formal FFmpeg provider**
   ([FfmpegCpuRenderLowerer](../../../ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/FfmpegCpuRenderLowerer.java),
   capability `subtitle.rasterize`). The worker must declare a font; a missing font fails closed. Only
   hard-subtitle burn-in is implemented today; full multilingual subtitle management remains a future
   product capability and is not claimed here.
8. **`render_submission_idempotency` is the single formal render-submission idempotency table**
   ([V1__baseline.sql](../../../platform-app/src/main/resources/db/migration/V1__baseline.sql)). The
   retired `first_render_idempotency` table and its generated jOOQ classes no longer exist.

## What is not an entry

Legacy render create/start, pipeline-incremental, cache presign, outbound-copy, the old direct
`/api/timeline-git/render-jobs` creation path and the `/api/first-render/**` probe are removed. Do not
re-introduce compatibility adapters, shadow authorities or provider selection in public APIs; the
architecture guards enforce these boundaries.
