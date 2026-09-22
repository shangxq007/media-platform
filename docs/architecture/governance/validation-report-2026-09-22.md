---
metadata_schema_version: 1
document_id: "architecture-governance-validation-report-2026-09-22"
title: "Architecture and Contract Governance Validation Report"
artifact_type: "TEST_OR_GUARD"
domain: "architecture-governance"
authority_class: "EVIDENCE_ONLY"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: ["architecture", "api-contracts", "database-schema"]
retention_class: "LONG_TERM"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Validation report — 2026-09-22

Candidate: `819b73fbb62da54f5fff202579ac441cb4ef5083` (source checkpoint; final governance commit recorded in the change ledger).
This report records fresh execution separately from cached evidence. It does not
claim whole-repository release readiness.

## Fresh execution

| Check | Command | Result | Evidence/notes |
|---|---|---|---|
| Documentation governance | `bash scripts/check-document-governance.sh --head HEAD --base HEAD --mode current --verbose` | PASS 16/16 | DG-006 protected baselines re-recorded under GOV-2026-09-22-005/006; DG-011 introduced links 0. Baseline debt remains 126 identities. |
| Broken links | Included in document governance guard | PASS incremental | Full scan now reports 48 remaining baseline identities; 78 were resolved and 0 introduced. Remaining identities are classified in the inventory. |
| Protected documents | DG-006 | PASS | 18 protected documents checked. |
| LikeC4 syntax | `npx --yes likec4@1.58.0 validate docs/architecture/maps/likec4` | PASS | LikeC4 v1.58.0; 1 source file. |
| Architecture-map drift | `python3 docs/architecture/maps/scripts/architecture-map-drift-guard.py` | PASS | 50 active modules classified; 3 deployment units represented. |
| Generated C4 | `./gradlew --no-daemon :platform-app:test --tests com.example.platform.ModulithDocumentationGenerationTest` | PASS | 105 generated files under `docs/architecture/maps/generated/modulith/`. |
| Generated-C4 determinism | Run generator twice and compare sorted SHA-256 hashes of `*.puml` | PASS | Byte-identical relation ordering and output hashes. |
| Modularity | `./gradlew --no-daemon :platform-app:test --tests com.example.platform.ModularityTest` | PASS | Spring Modulith boundary verification passed. |
| OpenAPI artifact validation | Python JSON/OpenAPI 3.1.x validator in `scripts/check-api-contract-governance.sh` | PASS | Checked-in artifact: OpenAPI 3.1.0, 302 operations, operationIds present. |
| API lint and compatibility | `bash scripts/check-api-contract-governance.sh` | PASS | Spectral 6.14.3 PASS; oasdiff v1.28.0 positive and negative comparisons PASS. |
| Flyway inventory | `find platform-app/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' \| sort -V`; Gradle GCR-5/GCR-6 check | PASS | Exact V1–V9 set; V1 checksum guard passes. |
| Semgrep architecture policy | `python3 scripts/ci/test_semgrep_architecture_rules.py` | NOT_RUN | `uvx` prerequisite is unavailable. |

## Runtime and environment-gated checks

| Check | Result | Reason |
|---|---|---|
| Runtime `/v3/api-docs` | PASS (fresh local observation) | Local service at `http://127.0.0.1:8088/v3/api-docs` returned OpenAPI 3.1.0, 461 paths, 454 schemas. This is observation evidence, not a checked-in artifact mutation. |
| External provider/production verification | NOT_RUN | Requires explicit external environment and provider credentials; no production calls were made. |
| oasdiff v1.28.0 positive/negative comparisons | PASS (fresh) | Pinned binary downloaded using the system CA bundle `/etc/ssl/ca-bundle.pem`; no local proxy was used. |
| Frontend checks | NOT_RUN | `frontend/node_modules` prerequisite is absent; no frontend source was changed. |

## Cached or reused evidence

The repository's prior bounded backend acceptance report is retained under
`.agent-tasks/WHOLE-REPOSITORY-ARCHITECTURE-REVIEW-20260922/REPORT.md` and is
classified as HISTORICAL_EVIDENCE. It is not used to convert an environment-
gated check into a fresh pass.

## Decision

**CONDITIONAL_PASS.** Documentation governance, architecture-map validation,
generated-C4 determinism, Modularity, Flyway inventory, and the checked-in
OpenAPI 3.1.0 structural validation pass. Whole-repository governance remains
conditional because oasdiff and Semgrep prerequisites are unavailable, the full
broken-link debt baseline is not yet zero, frontend checks are environment-gated,
and external-provider/production checks were not run.

## Post-report repair verification

Commit `30ca4c4b` repaired remaining current-document Flyway references and
reclassified pre-checkpoint inventories as historical evidence. The fresh
command `bash scripts/check-document-governance.sh --head HEAD --base HEAD
--mode current --verbose` passed **16/16** with zero introduced links; the
canonical migration directory still reports exactly V1 through V9.
