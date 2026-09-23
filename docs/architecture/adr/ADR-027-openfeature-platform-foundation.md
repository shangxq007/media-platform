---
metadata_schema_version: 1
document_id: "architecture-adr-027-openfeature-platform-foundation"
title: "OpenFeature Platform Foundation and Service Deployment Decision"
artifact_type: "ARCHITECTURE_DECISION"
domain: "policy-governance"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-24"
last_reviewed_at: "2026-09-24"
canonical_contracts: ["api", "schema-intent", "workflow-execution"]
source_of_truth_domains: ["architecture", "api-contracts", "database-schema", "workflow"]
retention_class: "PERMANENT"
generated: false
do_not_edit: false
---

# ARCH-FLAG-001 — OpenFeature platform foundation

**Decision date:** 2026-09-24  
**Status:** Accepted for in-process implementation; service deployment deferred.  
**Scope:** Platform-owned feature evaluation only.

## Decision matrix

| Capability | Decision | Current boundary |
|---|---|---|
| OpenFeature SDK/API | **Integrate in-process now** | The typed `PlatformFeatureProvider` boundary is owned by `policy-governance-module`; the PostgreSQL feature-flag tables are the default control plane. |
| flagd | **Optional future adapter; deployment deferred** | A non-bean internal adapter seam may be added later. flagd is not a runtime dependency and cannot become a second authority. |
| OpenBao | **Deployment deferred** | Reconsider only after identity, persistence, backup, restore, and TLS prerequisites are verified. Bitwarden remains the human/development credential authority. |
| Tolgee | **Deployment deferred** | Reconsider only after an approved localization workflow and resource budget exist. |
| Bitwarden | **Reuse current authority** | Human/development credential authority; no migration is authorized by this decision. |

## Runtime rules

1. Evaluation goes through one provider-neutral typed boundary. PostgreSQL platform control-plane state is the default provider; in-memory or file-backed state is test/local-development only.
2. Provider implementations are not media Capability Providers, Plugin Registry entries, `WorkerRuntime`, or `ExecutionBackend`; those authorities remain unchanged.
3. HTTP evaluation uses server-resolved identity and tenant/workspace scope. Browser-supplied scope fields are ignored for product evaluation.
4. Percentage rollout uses the pinned `platform-feature-bucket-v1` SHA-256 algorithm. A missing stable subject is an explicit invalid-context result; random evaluation is forbidden.
5. Unknown flags, malformed context, provider failures, stale sessions, and unavailable control-plane data produce explicit reason codes. No alternate authority is silently consulted.
6. A workflow that depends on a flag captures a `FeatureFlagSnapshot` before durable Temporal admission. Replay reads only that immutable snapshot and never performs external evaluation.
7. Audit records may include flag key, scope, provider revision, resolved value, and reason. They must not include secrets. Public product APIs expose only the typed evaluation result and never provider credentials or internal connection details.

## Authority distinctions

- **Feature-evaluation configuration:** flag definitions, targeting rules, rollout algorithm, provider revision, and frozen snapshots.
- **Media capability/provider execution:** capability registrations and execution providers; feature flags may select a route but never define capability identity or execution authority.
- **Authorization and entitlement:** canonical actor, tenant/workspace authorization, subscription, quota, and entitlement authorities; a flag cannot grant or revoke them.
- **Canonical domain state:** Product, Timeline, StorageRuntime, Workflow, Artifact, billing, and audit state; flags are inputs to decisions, never replacements for these records.

## Deployment and acceptance

No OpenFeature/flagd, OpenBao, Tolgee, or other service is deployed by this decision. This is an implementation and governance decision only; it does not claim production readiness or acceptance.
