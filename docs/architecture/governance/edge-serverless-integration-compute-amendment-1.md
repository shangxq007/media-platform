# Edge, Serverless, Integration, and Compute — Amendment 1

TASK_ID=ROADMAP_22_PHASE_16_CLOSURE_AND_CROSS_CUTTING_GOVERNANCE_PERSISTENCE
MODE=APPEND_FORWARD_DOCS_ONLY_GOVERNANCE
STATUS=ADOPTED_REPOSITORY_PERSISTED
IMPLEMENTATION=NOT_STARTED_OR_DEFERRED_AS_INDEXED
EXTENDS=EXTERNAL_INTEGRATION_AND_WEBHOOK_FOUNDATION
COMPETING_AUTHORITY=NO

## 1. Scope

This amendment expands the existing external-integration and webhook
foundation. It does not create a parallel execution, workflow, storage,
Artifact, media, access-policy, event, or canonical-state authority. It adds
no mandatory Cloudflare Workers, AWS Lambda, edge, Camel, or other production
dependency and does not create a Serverless `ExecutionBackend`.

Existing equivalent IDs are reaffirmed rather than duplicated:

- `CORE_SERVICES_MUST_NOT_BLOCK_ON_LONG_RUNNING_REMOTE_EXECUTION_V1` covers
  long wait not being a synchronous core request.
- `REMOTE_POLLERS_ARE_EPHEMERAL_OBSERVERS_NOT_TASK_AUTHORITIES_V1` covers
  polling as ephemeral observation mechanics.
- `REMOTE_STATUS_OBSERVATIONS_MUST_BE_ATTEMPT_AND_GENERATION_FENCED_V1`
  covers poller attempt/generation binding.
- `SERVERLESS_INTEGRATION_IS_NOT_EXECUTION_STATE_AUTHORITY_V1` covers
  serverless mechanics not owning execution state.
- `WORKFLOW_OWNS_PROCESS` covers durable process authority.

## 2. Core semantics and typed integration adapters

Semantics remain in core compute even when mechanics run at the edge.
External-service authentication, protocol shape, callback signature, polling
cadence, rate limits, provider error mapping, SDK/client details, and
transport quirks stay behind a typed integration adapter. Native HTTP,
Apache Camel, Lambda, and Workers are replaceable mechanics only.

The typed adapter must lower external observations into existing platform
commands, events, or generation-fenced `ExecutionObservation` values. It must
not expose provider-native maps as semantic authority or let transport state
become canonical lifecycle state.

## 3. Long-running external execution

A long external wait is never held open as a synchronous core request.
Callback/webhook completion is preferred when the provider offers an
authenticated, idempotency-aware callback contract. Otherwise submission
emits durable asynchronous work/event state and a bounded poller observes the
provider until terminal convergence.

The poller is ephemeral observation mechanics. It owns no task, workflow, or
completion authority; restart must be safe. Every observation binds the exact
platform execution attempt and ownership generation. Stale, cross-attempt,
or cross-generation observations fail closed and cannot complete the task or
publish its Artifact result.

Inbound callbacks pass through the existing WebhookIngress trust,
authentication/signature validation, normalization, and typed-routing
boundary. Outbound webhooks remain driven by committed typed events through
the transactional outbox, with at-least-once/idempotency-aware delivery.

## 4. Serverless and workflow boundaries

Serverless compute is replaceable short-lived mechanics, not domain,
workflow, execution-state, or canonical-media authority. The platform
Workflow model owns durable process semantics. Temporal is a replaceable
durable-orchestration implementation. Lambda and Cloudflare Workers are
replaceable short-lived invocation/observer/access-edge mechanics.

No `ServerlessExecutionBackend` is adopted. A new ExecutionBackend kind may
be considered only when a concrete platform `ExecutableTask` use case proves
that serverless execution mechanics require that boundary and all existing
provider, attempt/generation, Artifact, completion, and placement contracts
can be preserved.

## 5. Edge data plane and direct upload

The edge is a data plane and access edge, not canonical media state. The
application control plane should not proxy large media bytes by default.
Direct upload uses a bounded, tenant/principal/object/purpose/size/type/time
storage grant so the client sends bytes directly to the storage data plane.

Bypassing the application bytes proxy does not bypass authority:

1. the platform authorizes and issues the bounded grant;
2. the client uploads to the allowed data-plane target;
3. the platform validates trusted storage evidence, exact size/digest/type
   and policy constraints;
4. platform finalization commits the Artifact and only then exposes canonical
   media references.

A signed URL or equivalent token is ephemeral access mechanics. Artifact
existence, lifecycle, digest, tenant authorization, retention, and access
policy remain platform authority. Signing format, provider SDK, URL shape,
CDN token, and expiry encoding remain adapter mechanics.

Frontend-computed digest, duration, media type, dimensions, codec, safety
classification, or completion assertions are untrusted hints until verified
by a trusted platform/storage/provider path. Hints may improve UX but never
authorize Artifact finalization or canonical media state.

## 6. Private delivery and edge transformations

Private delivery may use bounded signed access after platform policy and
Artifact authorization. The edge may enforce the issued grant but does not
own the access policy or Artifact lifecycle.

An edge resize, transcode, thumbnail, packaging, or format conversion is
delivery mechanics only when its output is explicitly ephemeral and
non-canonical. If an edge transform is intended to become reusable,
canonical, provenance-bearing, or an input to later execution, it must be
explicitly lowered to the platform Operation/ExecutableTask path, staging,
durable publication, Artifact commit, and fenced completion. The deployment
location does not weaken those laws.

## 7. Deferred triggers

Implementation remains deferred until a concrete trigger exists:

- first remote Provider whose operation outlives a synchronous request or
  requires callback/poll convergence;
- first product path requiring direct large-media browser upload;
- first private-media delivery path requiring bounded signed access;
- first edge transform whose canonical-versus-delivery classification must be
  decided.

Inbound and outbound webhook implementations remain aligned with their
existing deferred entries. No implementation is claimed here.
