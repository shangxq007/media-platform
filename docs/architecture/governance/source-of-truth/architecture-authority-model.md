---
metadata_schema_version: 1
document_id: "architecture-governance-authority-model"
title: "Architecture Authority Model"
artifact_type: "ARCHITECTURE_DOCUMENT"
domain: "architecture-governance"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
review_cadence_days: 90
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: ["architecture"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: true
blocks_v5: false
---

# Architecture Authority Model

This document records the authority rules for the repository architecture. It
is a governance decision and does not duplicate the architecture model.

1. **Implemented behaviour:** source code, executable contracts, controller and
   DTO definitions, migration bytes, package metadata, and architecture guards
   are authoritative for what currently runs.
2. **Approved semantics:** accepted ADRs and canonical-contract documents are
   authoritative for approved meaning and invariants.
3. **Intent:** LikeC4 is the human-curated architecture-intent model. It may
   show logical domains, physical deployment units, trust boundaries, external
   providers, Temporal, PostgreSQL, workers, storage, and target boundaries.
4. **As-built:** Spring Modulith generated C4 is the generated view of actual
   module/package relationships. It is derived and must be regenerated from
   `ApplicationModules`.
5. **Derived tools:** Archify and Fireworks-Tech-Graph are analysis or
   visualisation options only. Neither is integrated or promoted to authority
   in this checkpoint.
6. **No silent authority:** a diagram, graph, blueprint, review report,
   generated page, or evidence receipt cannot become a second authority. Its
   classification and replacement reference are recorded in the inventory.
7. **Shared identifiers:** logical and physical views use the IDs in
   `docs/architecture/maps/architecture-identifiers.yaml` for domains, modules,
   contracts, authorities, data entities, and deployment units.

## View ownership

| View | Owner | Authority class | Regeneration/editing rule |
|---|---|---|---|
| LikeC4 intent | Architecture governance | CURRENT_NORMATIVE intent | Human edit; validate syntax and drift |
| Spring Modulith C4 | Build/test toolchain | GENERATED_DERIVED | Regenerate; never hand-edit |
| Logical/physical prose | Architecture governance | CURRENT_NORMATIVE support | Link to contracts/ADRs; do not fork facts |
| Archify/Fireworks-Tech-Graph | No current owner | DEFERRED derived option | Do not claim integration |

## Conflict rule

For implemented behaviour, executable sources outrank documentation. For
semantic decisions, accepted governance contracts outrank implementation drift
and explanatory prose. A conflict is a governance gap until the change ledger
records its decision; it is not resolved by changing a generated diagram.
