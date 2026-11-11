# Unified Render product entry points

Media computation enters the existing First Render or Incremental submission owner and freezes
Render Planning, Physical Planning and Provider Binding before Temporal dispatch. The Worker
executes that bound graph; Artifact owns commit and content verification. Delivery selects an
already completed output and rechecks live access before returning content.

The Render workbench at `/operations/renders` uses canonical revision queries, clip Operations,
`/api/first-render/plans`, `/api/incremental-render/plans`, scoped RenderJob reads/cancellation,
Artifact content and `/api/projects/{projectId}/deliveries`. It does not submit a DAG or choose a Provider.

MCP uses authenticated JSON-RPC at `POST /api/mcp/media`: initialize, tools/list and tools/call.
Its fixed product tools query Revision/OTIO, invoke clip Operations, submit formal first/incremental
renders, query Jobs, create/read Delivery and query scoped Artifact access. The application services
retain authorization, request identity, CAS, binding and completion authority. Tools never accept a
free tenant override, unbound graph or backend selection.

Legacy Render create/start, pipeline-incremental, cache presign and outbound-copy APIs are removed.
The old direct `/api/timeline-git/render-jobs` creation path is removed. Legacy local RenderWorkflow,
worker queue/lease, Pipeline execution, alternative provider routing and receipt-based output fallback
are retired. No compatibility execution adapter is provided.

Independent Timeline queries/import through Operation, asset metadata, Product preview upload,
client-produced export sessions, Cover/Thumbnail and Artifact Capability admission remain separate
business capabilities. Client upload/export is not a server Render execution authority.

Schema changes are folded into the current greenfield Flyway V1 and regenerated from a fresh PG18.
Formal `wf_artifact_reuse_index`, native attempt/generation fencing and immutable Delivery remain.
Execution evidence and exact candidate/version identities belong to the owner task evidence package.
