---
metadata_schema_version: 1
document_id: "architecture-governance-whole-repository-acceptance-2026-09-22"
title: "Whole-Repository Acceptance Report"
artifact_type: "TEST_OR_GUARD"
domain: "architecture-governance"
authority_class: "EVIDENCE_ONLY"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
retention_class: "LONG_TERM"
generated: false
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Whole-repository acceptance — 2026-09-22

Candidate: `main` after the documentation baseline at `1db1df3a` plus the
acceptance follow-up changes recorded in the governance ledger.

## Fresh local checks

| Check | Command | Result |
|---|---|---|
| Documentation governance | `bash scripts/check-document-governance.sh --head HEAD --base HEAD --mode current --verbose` | PASS, 16/16; 0 introduced links |
| Semgrep contract matrix | `PATH=/tmp/media-platform-semgrep-venv/bin:$PATH SSL_CERT_FILE=/etc/ssl/ca-bundle.pem python3 scripts/ci/test_semgrep_architecture_rules.py` | PASS; validate 1/1, malformed 2/2, positive findings 4, negative findings 0 |
| Semgrep repository scan | `uvx semgrep==1.175.0 --metrics off --config .semgrep/media-platform-architecture.yml .` | PASS; 7 rules, 4,040 targets, 0 findings |
| LikeC4 | `npx --yes likec4@1.58.0 validate docs/architecture/maps/likec4` | PASS |
| Architecture drift | `python3 docs/architecture/maps/scripts/architecture-map-drift-guard.py` | PASS; 50 modules, 3 deployment units |
| OpenAPI governance | `bash scripts/check-api-contract-governance.sh` | PASS; Spectral 6.14.3, oasdiff 1.28.0, 5/5 checks |
| Frontend install | `npm ci --no-audit --no-fund` | PASS; 636 packages from lockfile |
| Frontend lint | `npm run lint` | PASS; 0 errors, 46 warnings |
| Frontend typecheck | `npm run typecheck` | PASS |
| Focused frontend API surfaces | `npm run test:projects`, `test:publication`, `test:render` | PASS; 104 tests |
| Complete frontend suite | `NODE_OPTIONS=--localstorage-file=/tmp/media-platform-vitest.localstorage npm run test` | PASS; 34 files, 310 tests |
| Frontend production build | `npm run build` | PASS; Vite 6.4.3 |
| Frontend architecture guards | `npm run architecture:guard`, `npm run architecture:guard:test` | PASS; all authority counts zero, 131 guard tests pass |
| Frontend H4 scope/routes/clean-forward | `npm run h4:ledger`, `npm run h4:routes`, `npm run h4:clean-forward` | PASS; 207 active identities, 21 routes, zero retired residue |
| Compose/Flyway reference | focused Python path assertion | PASS; V1 mount resolves to `V1__initial_schema.sql` |
| Docker Compose parser | `docker compose -f docker-compose.dev.yml config` | NOT_RUN; `docker` executable unavailable |

Generated-C4 determinism and Spring Modulith Modularity checks were freshly
executed after the acceptance follow-up:
`./gradlew --no-daemon :platform-app:test
--tests com.example.platform.ModulithDocumentationGenerationTest
--tests com.example.platform.ModularityTest` — BUILD SUCCESSFUL (97 actionable
tasks, all up-to-date). No source or generated application code changed in
that run. The pinned oasdiff, frontend dependencies, and Semgrep runtime were
restored outside tracked product sources.

## Failure-path and contract scope

The focused frontend and existing backend evidence cover unauthorized and
cross-scope access, expired-session fencing, malformed contract handling, stale
resource versions, cancellation/retry, and missing dependency/error paths in
the tested surfaces. Worker, Temporal, storage, and deployment-unavailability
paths remain environment-gated. Frontend mocks remain test fixtures and are not
production evidence. GraphQL, MCP, event, and worker protocols remain separate
contracts.

## External and deployment scope

External provider calls, production OIDC, PVE deployment, reverse-proxy/TLS
verification, registry provenance, live Temporal, live workers, backup/restore,
and production network trust checks were **NOT_RUN**. See the [PVE readiness
matrix](pve-readiness-matrix-2026-09-22.md).

## Decision

**CONDITIONAL_PASS.** All restorable local repository gates pass, including
Semgrep and the complete frontend suite. Conditional status remains because
Docker Compose parsing and the external/PVE/runtime checks require unavailable
or unauthorized environment prerequisites. This is not a production release
readiness claim.
