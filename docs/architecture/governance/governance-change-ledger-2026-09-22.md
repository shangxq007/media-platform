---
metadata_schema_version: 1
document_id: "architecture-governance-change-ledger-2026-09-22"
title: "Architecture and Contract Governance Change Ledger"
artifact_type: "ARCHITECTURE_DOCUMENT"
domain: "architecture-governance"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: ["api", "schema-intent"]
source_of_truth_domains: ["architecture", "api-contracts", "database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: true
blocks_v5: false
---

# Governance Change Ledger

| ID | Change | Semantic reason | Scope | Evidence |
|---|---|---|---|---|
| GOV-2026-09-22-001 | Establish one architecture entrypoint and authority model | Prevent LikeC4, generated C4, reports, and blueprints from becoming competing authorities | Documentation only | `architecture-entrypoint.md`, `architecture-authority-model.md` |
| GOV-2026-09-22-002 | Register shared cross-view identifiers | Logical and physical views must be traceable without duplicating the model | Documentation only | `architecture-identifiers.yaml` |
| GOV-2026-09-22-003 | Adopt actual Flyway inventory V1–V9 as current state | The migration directory and build guards are authoritative; old V1-only claims are false | Documentation only; migration bytes unchanged | `docs/database/flyway-migration-baseline.md` |
| GOV-2026-09-22-004 | Repair DG-011 broken links | Current documentation must resolve repository links; obsolete targets are replaced by existing source paths | Documentation only | governance validation report |
| GOV-2026-09-22-005 | Re-record protected schema-intent baseline | Commits `b509be5f` and `673e180a` were explicit governance amendments. The registered hash was not advanced, causing a false protected-baseline failure. | Protected baseline metadata only; semantic change already authorized | `protected-document-baseline.json`, this ledger |
| GOV-2026-09-22-006 | Make runtime OpenAPI export the checked-in transport artifact | Keep one REST contract authority at OpenAPI 3.1.0, with base/candidate files used only for compatibility fixtures | Documentation/tooling only | `docs/api/openapi-authority.md` |
| GOV-2026-09-22-008 | Update protected API-contract baseline hash | The API authority document now records OpenAPI 3.1.0, runtime export, and compatibility semantics; the registered hash was advanced with this decision. | Protected baseline metadata only; endpoint behaviour unchanged | `protected-document-baseline.json`, this ledger |
| GOV-2026-09-22-007 | Classify historical and superseded material | Historical evidence remains available but cannot appear as current normative guidance | Documentation index/inventory | `architecture-document-inventory.tsv` |

No product source, executable application code, deployment configuration,
database migration, or database data was changed by these decisions.
