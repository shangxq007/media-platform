# Unified render product entry points

Media computation enters the formal Render submission owner (`POST /api/render-jobs`) or the
incremental submission owner (`POST /api/incremental-render/plans`) and freezes Render Planning,
Physical Planning and Provider Binding before Temporal dispatch. The Worker executes that bound graph;
Artifact owns commit and content verification. Delivery selects an already completed output and
rechecks live access before returning content. Both entries converge on one revision-pinned
submission service; no third submission path exists.

First Render is an E2E/automation scenario over these entries, not a product entry: it owns no
production Controller, orchestration, table or workflow, and the retired `/api/first-render/**` probe
route is absent from production source. Current paths are named in
[current product execution paths](../architecture/current/current-product-execution-paths.md).

The Render workbench at `/operations/renders` uses canonical revision queries, clip Operations,
`POST /api/render-jobs`, `POST /api/incremental-render/plans`, scoped RenderJob reads/cancellation,
Artifact content and `/api/projects/{projectId}/deliveries`. It does not submit a DAG or choose a
Provider.

MCP uses authenticated JSON-RPC at `POST /api/mcp/media`: initialize, tools/list and tools/call.
Its fixed product tools query Revision/OTIO, invoke clip Operations, submit formal full/incremental
renders, query Jobs, create/read Delivery and query scoped Artifact access. The application services
retain authorization, request identity, CAS, binding and completion authority. Tools never accept a
free tenant override, unbound graph or backend selection.

C20 determinism, C21 cache hook and C22 incremental replanning are formal planning/binding
capabilities of the accepted roadmap-20 contract. They are implemented through the render
planning/binding services and are not experimental side paths.

Subtitle burn-in runs in the formal FFmpeg provider (capability `subtitle.rasterize`); the worker must
declare a font and the render fails closed without one. Only hard-subtitle burn-in is implemented
today; complete multilingual subtitle management remains a future product capability.

`render_submission_idempotency` is the single formal render-submission idempotency table. Legacy
Render create/start, pipeline-incremental, cache presign and outbound-copy APIs are removed. The old
direct `/api/timeline-git/render-jobs` creation path is removed. Legacy local RenderWorkflow, worker
queue/lease, Pipeline execution, alternative provider routing and receipt-based output fallback are
retired. No compatibility execution adapter is provided.

Independent Timeline queries/import through Operation, asset metadata, Product preview upload,
client-produced export sessions, Cover/Thumbnail and Artifact Capability admission remain separate
business capabilities. Client upload/export is not a server Render execution authority.

Schema changes are folded into the current greenfield Flyway V1 and regenerated from a fresh PG18.
Formal `wf_artifact_reuse_index`, native attempt/generation fencing and immutable Delivery remain.
Execution evidence and exact candidate/version identities belong to the owner task evidence package.

Historical consolidation of legacy work is retained as an archived record:
[legacy work disposition](../development/legacy-work-consolidation.md); it is not current guidance.
