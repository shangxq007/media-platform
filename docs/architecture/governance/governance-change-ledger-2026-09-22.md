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
| GOV-2026-09-22-007 | Classify historical and superseded material | Historical evidence remains available but cannot appear as current normative guidance | Documentation index/inventory | `architecture-document-inventory.tsv` |
| GOV-2026-09-22-009 | Repair stale Flyway references and reclassify pre-checkpoint inventories | Current documentation must point to the V1–V9 executable inventory; old V1-only, V11+, and pre-checkpoint inventory claims are historical or target facts | Documentation and classification only; migration bytes unchanged | `docs/database/flyway-migration-baseline.md`, affected current guides, classification and superseded mappings |
| GOV-2026-09-22-010 | Label remaining V10+ design and external-provider migration references | Design documents and Cuebot examples may mention future or external migrations, but those references must not be mistaken for the platform's current Flyway inventory | Documentation labels only; product and provider behavior unchanged | affected design/provider documents, classification inventory |
| GOV-2026-09-22-011 | Correct the development Compose V1 bootstrap mount | `docker-compose.dev.yml` referenced a nonexistent historical filename while the executable Flyway baseline is `V1__initial_schema.sql` | One deployment-reference path; runtime topology and migration bytes unchanged | `docker-compose.dev.yml`, Flyway inventory, focused path validation |
| GOV-2026-09-22-012 | Close restorable local acceptance prerequisites and record external readiness boundaries | Semgrep and frontend checks are now fresh and passing; Docker/PVE/provider/OIDC checks remain explicitly environment-gated | Tooling and evidence only; no product behavior change | whole-repository acceptance report, toolchain inventory, PVE readiness matrix |
| GOV-2026-09-22-013 | Append the current OIDC transport to the frontend governed-scope ledger | The active source file existed after the H4 baseline and was missing from the append-forward identity ledger; no source behavior changed | Frontend governance metadata only | `frontend-current-governed-scope-ledger-v1.tsv`, H4 validation |
| GOV-2026-09-22-014 | Align H4 route validation with the registered legacy redirect route | `/render-jobs` is intentionally registered as a redirect compatibility route; the validator required a component-bearing route form and rejected the actual `createRoute`/`beforeLoad` registration | Governance validator only; frontend routing behavior unchanged | `frontend/scripts/verify-h4-route-reachability.mjs`, H4 route check |

No product source, executable application code, deployment configuration,
database migration, or database data was changed by these decisions.
