# Composition server-owned admission decision

The Composition selector request is the sole public planning/admission entry. It authenticates the actor and workspace selection, resolves an immutable published revision and all authority facts, lowers one `ProviderBoundExecutionPlan`, and submits that plan through the existing `PlatformExecutionAdmissionPort`/`platform_execution_admission` owner. The repository stores the serialized provider-bound plan and fingerprint so the accepted decision can be replayed without trusting caller facts.

Quota reservation is attempted only after the durable admission row is inserted and claimed idempotently. A failed authoritative quota charge removes the uncharged row in the same database transaction; an already charged idempotency key returns its original decision. A request hash or plan fingerprint mismatch on an existing key is a conflict. Runtime dispatch, worker assignment, leases, Temporal execution and provider execution remain outside admission.

The old Composition caller-supplied workflow/snapshot adapter remains test-only. No second admission authority or Render runtime path is introduced. `V15__composition_admission_authority_facts.sql` extends the existing admission row with plan facts, plan fingerprint and quota unit metadata; prior rows are retained with an explicit `legacy:` fingerprint and cannot be silently replayed as new server-owned decisions.

Affected callers are the Composition controller admission route and `CompositionAdmissionService`; the existing domain-neutral lifecycle port retains its Render-compatible plan method while adding a provider-bound overload for server-owned Composition facts.
