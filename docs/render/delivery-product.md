# Delivery product capability

Delivery records an immutable authorized selection of an already completed, bound-graph RenderJob final result. It neither executes media nor creates, adopts, commits or changes Artifact identity. No Temporal/Worker/Provider is started by Delivery.

## Product API

Authenticated tenant/workspace scope comes from the canonical identity and project authority; body fields cannot override it. All routes are `/api/projects/{projectId}/deliveries`:

- `POST` with mandatory `Idempotency-Key` (1–200 characters), JSON `sourceRenderJobId` and optional `artifactId`. If supplied, ArtifactId must equal that Job's exact accepted final result. No arbitrary asset selection. New selection returns201; identical normalized replay returns200; different request with same scoped key returns409 `IDEMPOTENCY_CONFLICT`.
- `GET /{deliveryId}` returns stable deliveryId, projectId, sourceRenderJobId, current immutable source Timeline revision pin, selected artifactId, status, createdAt and relative authenticated resultPath.
- `GET` lists at most100 records of the authorized project, in stable newest-first order (default50).
- `GET /{deliveryId}/result` returns verified content with private/no-store and attachment headers. No signed URL, persistent public URL or bearer credential is issued.

Creation requires existing `delivery.manage`, `delivery.read`, source RenderJob READ and canonical Artifact project READ. Detail/list/result require delivery.read, source Job READ and Artifact project READ. All checks precede identity lookups; result rechecks permissions after content loading. Tenant/project predicates and owner queries are mandatory. Cross-scope identities are unavailable even with a matching idempotency key.

## State and identity

The minimal state is READY: selection was accepted and verified atomically. There is no asynchronous work, so no ACCEPTED/PROCESSING/DISPATCH/WORKER/FAILED/CANCELLED state machine or retry worker is invented. Rejected creation leaves no Delivery. READY is an immutable acceptance fact, not a grant of future access: revocation, quarantine, deletion, permission changes or integrity failures are evaluated again on each request. Existing READY metadata can remain queryable by authorized readers while its result is unavailable. No Delivery cancellation or rebinding endpoint exists.

Request normalization binds the accepted source Job and Artifact, so explicitly selecting the accepted Artifact and omitting it are semantically equal. A tenant/project unique key identifies one immutable Delivery; no request hash or idempotency key is exposed in API responses. New keys may intentionally create separate delivery identities for the same source. Replay never changes the original selected result or requester.

## Transaction and authority

DeliveryRepository owns only the delivery table. Selection uses RenderJobService, GraphRenderLifecycle and ArtifactApplicationQuery/ArtifactOutputRead, not foreign tables or guessed first/latest Artifact. Only completed official bound-graph results are eligible; legacy output inference cannot substitute for Graph lifecycle acceptance. ArtifactOutputRead delegates committed state, exact output identity and byte integrity to Artifact/Storage owners.

A SERIALIZABLE creation transaction validates source/status/current Artifact/content/authorization and inserts READY. Serialization/deadlock conflicts retry boundedly5 times, with a fresh authoritative read; exhaustion returns503 `DELIVERY_CONCURRENT_RETRY`. Database failures roll back creation. A concurrent Artifact state change orders before creation (which rejects) or after a valid selection (which remains a historical READY fact but cannot bypass later access checks). No foreign lifecycle is mutated. A lost HTTP response is recovered by the same scoped key and immutable payload.

Composite FK constraints bind Delivery tenant/project/sourceJob/Artifact. Stable records reject UPDATE/DELETE at the database boundary. There is no independent Artifact digest authority; content validation and download delegate to official owner services. The inherited bounded Artifact read limit applies; this is not a new storage/streaming platform.

## Existing behavior mapping

| Existing behavior | Required semantics | Owner and disposition | Acceptance |
|---|---|---|---|
| Outbound destination/probe/policy/transfer/retry | Existing diagnostics and destination copies | Existing DeliveryAdministrationService/DeliveryJobService retained for actual Frontend/admin consumers; not used to manufacture R1D results | Existing consumer tests plus fresh build |
| Render completion listener | Optional outbound copying | Retained; no new policy is created by formal Delivery | No new execution/transfer during R1D creation |
| Final output selection | Exact completed Job result | RenderJob + GraphRenderLifecycle authority, ArtifactOutputRead | First/Incremental real results and invalid state negatives |
| Delivery identity/status/idempotency | Independent immutable business record | DeliveryService/DeliveryRepository/ProjectDeliveryController | Real PG/API concurrency and replay |
| Result download | Live authenticated verified Artifact bytes | Artifact/Storage official contracts | Digest/media comparison and state/permission negatives |
| Frontend Delivery diagnostics | Existing real consumer | Old DeliveryAPI retained; new DeliveriesAPI offers formal contract directly, no compatibility adapter | Gateway transport tests |

Legacy Render execution, MCP Pipeline tools and complete Frontend migration are outside this scope; no legacy runtime is deleted merely because formal Delivery no longer needs it. UNIFIED_EXECUTION remains IN PROGRESS; LEGACY_RETIREMENT remains NOT COMPLETE.
