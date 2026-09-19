# Workflow Foundation

Workflow owns definitions, immutable compiled process plans, runs, steps and waits. Temporal executes that plan and retains its durable history. Identity resolves and authorizes the actual Project → Workspace → Tenant relationship. Workflow invokes the single public `OperationInvocationPort`; its read-only `validate` method preflights the same owner that executes `invoke`. Workflow never writes Timeline, Render, Artifact or Storage state.

## Authoring and admission

The existing `/api/tenants/{tenantId}/workflow-definitions` lifecycle supports executable schema 2. Every route requires an authenticated actor. Create/list Project-scoped definitions using an explicit `projectId`; version reads and commands authorize the definition's actual Project. Schema 1 remains readable declaration history and is explicitly rejected for execution.

A schema-2 node's `configValues` contains its typed WorkflowPlan node, including the matching `id` and `kind`. Its `nodeType` is one of SEQUENCE, PARALLEL, CHOICE, WAIT, LOOP, FOREACH, SUBWORKFLOW, OPERATION_INVOCATION. Edges describe ordered **control ownership**, not data references: each child has one parent, and the graph has one root. Executable node IDs use 1–64 ASCII letters, digits, underscores or hyphens; display names are separate. Multiple owners, duplicate order, unreachable nodes, cycles and unknown fields fail validation.

- SEQUENCE executes ordered children. PARALLEL executes at most 32 branches and joins ALL; there is no ANY/race mode.
- CHOICE has ordered true/false children and EQUAL, NOT_EQUAL, IS_SET or IS_EMPTY predicates. JSON comparison has no scripting or implicit coercion.
- WAIT uses TIMER, SIGNAL or APPROVAL with a positive deadline. All waits support cancellation.
- LOOP has one body, a deterministic predicate and `bound` in 1..1000. IS_SET/IS_EMPTY may inspect an in-scope Operation result from the body before the first iteration (unset initially). WAIT/control nodes have no result; results inside FOREACH or child-workflow scopes do not escape those scopes. Exhausting the bound is a failure, not successful completion or a retry.
- FOREACH traverses a pinned input or enclosing-item array with `bound` in 1..1000 and concurrency in 1..32. Item data is scoped to that iteration. Concurrent branches do not inherit one another's outputs.
- SUBWORKFLOW embeds an exact plan pin, but admission independently resolves that exact **published** definition/version from the Workflow owner and compares its scope and digest. Caller-provided content is not publication authority. Maximum depth is 8; recursion is rejected. Child result scope does not overwrite parent result names.
- OPERATION_INVOCATION carries the public typed OperationRequest. Parameters and exact initial base are pinned. The current production invocation owner supports ADD_MEDIA_CLIP_V1; other Operations fail admission. Generic notification/webhook/provider effects are not invented by Workflow.

`ITEM` key `item` refers to the whole immutable item; another key names an exact object field. Every pinned item is checked before acceptance.

Data references are separate `ValueRef(source,key)` values: INPUT, RESULT or iteration ITEM. Result availability is checked against control flow. Operation base bindings accept text at `baseRevisionId` and `baseContentHash`; supported result fields are `revisionId` and `contentHash`. Unknown fields, non-text bindings, unavailable results and non-text item-to-text bindings reject before run acceptance. Operation outcomes remain owner outcomes; the Workflow step projection exposes returned revision/content identities, not invented Artifact or Storage identities.

Capability requirements use Extension's public registry. Missing required capabilities reject; absence of an optional capability is pinned explicitly and does not select a future registration. Admission records exact compatible registration facts in run execution metadata, separate from provider-neutral plan semantics. Accepted runs never rediscover a different implementation. These registration snapshots are not proof of provider execution. The currently supported canonical editing Operation has no new Workflow-owned entitlement, quota, allocation or settlement decision; commercial decisions for effects remain with their owning commands.

## Run API

`POST /api/tenants/{tenantId}/workflow-executions` accepts:

```json
{
  "definitionId": "published-definition-id",
  "definitionVersion": 1,
  "projectId": "actual-project-id",
  "idempotencyKey": "caller-logical-request-key",
  "inputsJson": "{}"
}
```

Actor, Account/membership and Workspace are resolved on the server. Supplied actor, Workspace or allocation override fields are rejected. Acceptance atomically persists the run, canonical plan/digest, immutable input/context/version facts and a typed common-Outbox start intent. Identical same-tenant requests return the same run; conflicting reuse rejects. A rollback creates neither a run nor dispatchable work.

`GET /{runId}` returns actual resource scope, `workflowPlanDigest`, status, failure code and step projections. Run status is ACCEPTED, RUNNING or a terminal SUCCEEDED/FAILED/CANCELLED/TIMED_OUT; concurrent waits are represented by WAITING steps, not a misleading claim that every branch is blocked.

`POST /{runId}/cancel` and `POST /{runId}/release` authorize the current actor against the run's actual resource. Release body:

```json
{"stepId":"approval:opaque-step-digest","releaseId":"unique-command-id","approved":true}
```

Use the exact bounded `step_id` returned by the run projection; do not construct it from mutable UI position. Both commands persist accepted intent before Outbox delivery. Release records the authorized actor and binds one run/step/deadline; conflicting, late and terminal releases reject. Cancellation records its first accepted actor. Cancellation wins if its database request commits before terminal projection; a later cancellation cannot change an already terminal result. Previously committed effects are not rolled back by cancellation.

## Durable runtime and recovery

Use the `temporal` Spring profile with `TEMPORAL_TARGET` and the existing namespace settings. The `workflow-process` worker discovers the registered `PlanWalkWorkflowImpl` and Spring `WorkflowActivities` bean. Existing Render lifecycle coordination remains on its public ports; the old generic Render Workflow domain, dormant raw-payload pipeline and one-ACTION user runtime are retired.

Common Outbox owns claims, leases, retries and dead-letter recovery. A failed dispatch remains recoverable through those existing operations. Workflow start uses `workflow-run:{runId}` and rejects duplicate Temporal workflow IDs; retry reconciles both run identity and plan digest in Temporal memo. Every Continue-As-New explicitly carries those immutable accepted pins into its successor so durable controls and repeated start delivery retain the same verification. There is no after-commit-only launcher, second scheduler or fallback runtime.

The explicit cursor carries active branches, indices, loop/foreach progress, local results, pending releases and absolute wait deadlines through Continue-As-New. Activity invocation IDs are stable hashes of run plus logical step position. Projection commands are idempotent and terminal states cannot be resurrected. Activity receipt and step data live in Workflow; canonical effects remain with Operations. A committed Workflow receipt prevents retrying the effect after acknowledgement loss. The canonical Operation's own durable command identity covers its separate commit boundary. This is not a claim of exactly-once external execution.

## Focused local verification

Start a disposable Temporal development service, for example:

```sh
temporal server start-dev --headless --ip 127.0.0.1 --port 17233 --db-filename /tmp/ep07-temporal.db
bash scripts/test-authority-modules.sh workflow
```

`EP07_TEMPORAL_TARGET` changes the test service address. A Docker-compatible endpoint is required for disposable PostgreSQL Testcontainers. The application integration test uses full Spring services, real Identity/Outbox/PostgreSQL and the external local Temporal service; its controlled Operation owner records database effects through the public invocation interface. SDK unit tests separately exercise TestWorkflowEnvironment, continuation and history replay. Tests do not certify BMF, rendering, external providers, publishing or monetary settlement. Stop the task-owned Temporal service after verification.
