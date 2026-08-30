---
metadata_schema_version: 1
document_id: "architecture-readme"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "INFORMATIVE"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: null
created_at: null
last_reviewed_at: "2026-07-17"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: []
retention_class: "PROJECT_LIFETIME"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Architecture Documentation

## Start Here for Canonical Semantics

**Canonical contracts define authoritative architecture semantics:**
→ [Canonical Contracts](governance/canonical-contracts/)

**Source of Truth Matrix defines authority hierarchy:**
→ [Source of Truth](governance/source-of-truth/)

## Navigation

### Canonical Contracts
- [Platform Kernel](governance/canonical-contracts/platform-kernel-contract.md)
- [Product](governance/canonical-contracts/product-contract.md)
- [Timeline](governance/canonical-contracts/timeline-contract.md)
- [RenderJob](governance/canonical-contracts/render-job-contract.md)
- [Execution](governance/canonical-contracts/execution-contract.md)
- [Provider](governance/canonical-contracts/provider-contract.md)
- [Storage](governance/canonical-contracts/storage-contract.md)
- [Schema Intent](governance/canonical-contracts/schema-intent-contract.md)
- [Render Output](governance/canonical-contracts/render-output-contract.md) (CANDIDATE — pending approval)
- [API](governance/canonical-contracts/api-contract.md)
- [Control-Plane Governance](governance/canonical-contracts/control-plane-governance-contract.md)

### Source of Truth and Governance
- [Source of Truth Matrix](governance/source-of-truth/source-of-truth-matrix.json)
- [Authority Precedence](governance/source-of-truth/authority-precedence.md)
- [Conflict Adjudication](governance/source-of-truth/conflict-adjudication.json)
- [Canonical Contract Registry](governance/canonical-contracts/canonical-contract-registry.json)

### Current-State Supporting Documents
- [01 System Architecture](01-system-architecture.md)
- [02 Backend Architecture](02-backend-architecture.md)
- [03 Module Architecture](03-module-architecture.md)
- [06 Data Architecture](06-data-architecture.md)
- [Frontend Product Information Architecture V1](governance/frontend-product-information-architecture-v1.md)
- [Frontend/Backend Application API Gap Ledger V1](governance/frontend-backend-application-api-gap-ledger-v1.md)
- [Frontend Product IA F0/F1 Interim Report](governance/frontend-product-ia-f0-f1-interim-report-v1.md)
- [Frontend Foundation Checkpoint A Implementation](governance/frontend-foundation-checkpoint-a-implementation-v1.md)
- [Frontend Path Classification V1](governance/frontend-product-path-classification-v1.tsv)
- [Execution Job Model](execution-job-model.md)
- [Product Runtime](product-runtime.md)
- [Storage Runtime](storage-runtime.md)

### Architecture Decisions (ADRs)
- [ADR Index](adr/) — ADR-005 through ADR-026
- ADR-025: Artifact DAG indefinite deferral
- ADR-026: Render output commit protocol (in `render/adr/` — ACCEPTED version)

### Roadmaps and Future Designs
- [Blueprints](blueprint/) — 19 design blueprints
- [Platform Roadmap](platform-roadmap.md)

### Deferred Extensions
- [Artifact DAG](artifact-runtime.md) — postponed indefinitely, extension-layer only
- [ADR-009: Artifact Runtime](adr/ADR-009-artifact-runtime.md) — deferred
- [ADR-025: Artifact DAG Deferral](adr/ADR-025-artifact-dag-indefinite-deferral.md)

### Historical Archive
- [Archive](archive/) — historical, superseded, deprecated documents

### Quarantined Material
- [V5 Quarantine](quarantine/v5/) — V5 render-output-commit documents (NOT accepted, NOT authority)

### Evidence Policy
- `.agent-tasks/**` is EVIDENCE_ONLY and NON_CANONICAL
- Memory entries are CONTEXT_AND_AUDIT_ONLY, NOT approval authority
- Kanban `done` ≠ `accepted`
- Detached receipts are EVIDENCE_ONLY

### Implementation Status
- **V1-V4:** FROZEN — migration bytes must not be modified
- **V5:** QUARANTINED — blocked until governance .1-.7 closes
- **Frontend:** PAUSED — implementation paused until backend contracts stabilize
- **Artifact DAG:** DEFERRED — postponed indefinitely
