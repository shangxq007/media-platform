---
metadata_schema_version: 1
document_id: "composition-foundation-authority-decision"
title: "Composition Foundation Authority Decision"
artifact_type: "ARCHITECTURE_DECISION"
domain: "composition-foundation"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-23"
last_reviewed_at: "2026-09-23"
review_cadence_days: 90
supersedes: []
superseded_by: []
canonical_contracts: ["api"]
source_of_truth_domains: ["architecture", "api-contracts", "composition"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Composition Foundation Authority Decision

**Decision state:** ACCEPTED owner direction for the future composition-foundation implementation.  
**Decision date:** 2026-09-23  
**Scope:** instruction alignment only; no product implementation is authorized by this artifact.

## Conflict resolution

The earlier composition brief requested a public Provider Catalog API. `CLAUDE.md`
prohibits exposing provider/backend identities through public APIs. The owner has
resolved the conflict by narrowing the public contract to provider-neutral
capability metadata. Provider and backend identity remain internal. `CLAUDE.md`
and `AGENTS.md` are not edited in this change, and neither file silently changes
meaning. An explicit owner-approved instruction update is **not required** for
the revised scope because the public API no longer exposes the prohibited
identities. If a later task requires provider identity exposure, it must obtain a
separate owner-approved instruction update before implementation.

## Revised task scope

The composition foundation may implement:

- an authoritative, read-only public capability catalog;
- provider-neutral Template Workflow and Application definitions;
- deterministic composition validation and a clearly non-runtime local adapter;
- tenant/workspace-owned drafts, immutable published versions and validation snapshots;
- draft-management and publish-readiness APIs using the repository's canonical
  OpenAPI version;
- a frontend driven by capability and contract metadata.

It must not implement public provider catalog identity or manifest endpoints,
execution endpoints, Temporal integration, worker lifecycle, provider process
construction, external side effects, or speculative execution persistence.

## Public API boundary

Public platform contracts may expose:

| Public concept | Allowed content |
|---|---|
| Capability identity/version | Stable platform capability identity and contract version |
| Contracts | Input/output contract references and typed bindings |
| Media/assets | Supported media and asset types |
| Execution | Provider-neutral execution mode and execution characteristics |
| Eligibility | Availability and eligibility summary |
| Economics | Estimated cost and quota information |
| Reliability | Cancellation and retry characteristics |
| Composition | Application and Template Workflow compatibility |
| Validation | Typed, deterministic validation errors and publish-readiness results |

Public responses must not expose provider IDs, provider manifests,
ExecutionBackend or WorkerRuntime identities, worker nodes, internal registry
topology, or provider-specific configuration schemas.

## Internal registry boundary

The Capability Registry remains the authority for capability registration and
internal provider resolution. Provider manifests are implementation metadata.
ExecutionBackend and WorkerRuntime remain private platform infrastructure. Any
diagnostic/admin exposure requires explicit authorization and a separate
internal contract; it is not part of the public composition API. Application
and Template Workflow models depend only on platform Capability Contracts.

## Frontend exposure policy

The UI may browse available capabilities, execution modes, estimated cost and
quota, availability, provider-neutral compatibility, required assets,
entitlements, and validation errors. It must not render provider IDs,
provider-specific forms, backend or worker identities, registry topology, or
provider-specific configuration schemas. Tenant and workspace authority comes
from the authenticated session-bound client and is never accepted as a
user-supplied authority override.

## Public-to-internal mapping

| Public concept | Internal concept | Exposure policy | Owning module |
|---|---|---|---|
| Capability identity/version | `CapabilityId`, `ContractVersion`, capability registry entry | Public, stable contract fields only | `extension-module` capability authority |
| Input/output contract | Platform typed schema and capability contract | Public reference and binding shape | `typed-schema-module` plus capability authority |
| Asset/media support | Capability asset/media declaration | Public summary | Capability/composition authority |
| Execution mode | Platform execution-mode vocabulary | Public enum; no backend identity | Composition authority |
| Eligibility/availability | Internal registry health and policy projection | Public summarized decision only | Capability authority plus entitlement/quota policy |
| Cost/quota estimate | Cost/quota metadata projection | Public estimate and requirement | Billing/entitlement authorities |
| Cancellation/retry | Capability contract characteristics | Public provider-neutral characteristics | Workflow/composition authority |
| Provider resolution | Internal provider registry and manifests | Internal only; admin diagnostics require separate authorization | `extension-module` and provider runtime modules |
| Execution backend/runtime | `ExecutionBackend`, `WorkerRuntime`, worker topology | Internal only | Execution/worker modules |
| Template Workflow | Provider-neutral declarative definition | Public draft/published model | Composition authority, reusing workflow contracts where applicable |
| Application | Composition of capabilities and Template Workflows | Public draft/published model | Composition authority |

## Required follow-up implementation tasks

1. Confirm reuse boundaries against `CapabilityRegistryPort` and the existing
   workflow definition aggregate; do not create shadow authorities.
2. Define public capability, Template Workflow and Application schemas and
   typed validation errors.
3. Add tenant/workspace-owned draft persistence, optimistic concurrency,
   validation snapshots and immutable published versions only.
4. Add read/draft-management/publish-readiness APIs and update the candidate
   OpenAPI 3.1 contract with Spectral-compliant documentation.
5. Add the non-runtime deterministic validation adapter and its boundary port.
6. Add the provider-neutral frontend flow with session-bound tenant/workspace
   resolution.
7. Add focused backend/frontend/governance tests and documentation diagrams.

## Acceptance criteria for the future implementation

- No public response or frontend state contains provider IDs, manifests,
  backend/runtime identities, worker nodes, registry topology, or provider
  configuration schemas.
- Capability contracts, versions, media/assets, execution modes,
  availability, economics and retry/cancellation characteristics are readable
  and deterministic.
- Template Workflow and Application definitions are provider-neutral and never
  own durable execution or side effects.
- Validation rejects missing/incompatible capabilities, invalid bindings,
  unsupported modes, version mismatch, missing assets/entitlements, invalid
  ranges, disallowed cycles, ownership violations and quota failures with typed
  deterministic errors.
- Draft updates enforce tenant/workspace ownership, stale-session rejection,
  duplicate protection and optimistic concurrency; published versions are
  immutable.
- Public APIs use OpenAPI 3.1.0 and pass the repository's Spectral and API
  compatibility gates.
- PVE, Temporal, Storage, FFmpeg, BMF, external providers and production
  validation remain explicitly NOT_RUN for the foundation batch.
