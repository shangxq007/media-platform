# Normalization and Archival — Phase .4

## Scope

Normalize existing architecture documents based on Source of Truth Matrix (.2) and Canonical Contract Registry (.3).

## Principles

1. Canonical contracts are the highest document authority
2. Supporting documents must link to canonical contracts
3. Historical/quarantined material must be clearly marked
4. No canonical semantics are changed
5. No implementation drift is fixed
6. Git history is preserved

## Statistics

| Category | Count |
|----------|-------|
| Documents evaluated | 1115 |
| Documents modified in place | 79 |
| Authority notices added | 9 |
| Frontend pause notices added | 67 |
| Deferred extension notices added | 3 |
| Quarantine notices added | 8 |
| Documents moved to quarantine | 8 |
| Documents moved to archive | 0 |
| Redirect stubs created | 0 |
| Navigation index updated | 1 |

## Quarantined Materials

8 render-output-commit documents moved to `docs/architecture/quarantine/v5/`:
- render-output-commit-current-state.md
- render-output-commit-failure-window-matrix.md
- render-output-commit-implementation-roadmap.md
- render-output-commit-protocol-closeout.md
- render-output-commit-protocol-errata.md
- render-output-commit-schema-proposal.md
- render-output-commit-target-state.md
- render-output-commit-verification-contract.md

All marked: QUARANTINED, NOT_ACCEPTED, NOT_IMPLEMENTATION_AUTHORITY

## Authority Notices Added

9 active architecture documents received supporting authority notices:
- 01-system-architecture.md
- 02-backend-architecture.md
- 03-module-architecture.md
- 06-data-architecture.md
- execution-job-model.md
- execution-lifecycle.md
- product-runtime.md
- storage-runtime.md
- platform-kernel.md

## Deferred Extension Notices

3 Artifact DAG documents marked as deferred:
- ADR-025-artifact-dag-indefinite-deferral.md
- ADR-009-artifact-runtime.md
- artifact-runtime.md

## Frontend Pause Notices

67 frontend documents received implementation pause notices.

## Navigation

Architecture README.md updated with:
- Canonical contract links
- Source of Truth links
- Navigation sections
- Evidence policy
- Implementation status (V5 blocked, frontend paused, Artifact DAG deferred)

## Semantic Preservation

- Canonical contract files modified: 0
- Canonical semantics changed: 0
- Accepted ADR decision bodies changed: 0
- Production artifacts changed: 0
- Render-output candidate upgraded: NO
- V5 de-quarantined: NO
- Artifact DAG reactivated: NO
- Frontend unpaused: NO

## Deferred Items

- Full owner/version/supersedes metadata: .5
- Automated link guards: .6
- Control-plane guards: .6A
- ADR-016b renumbering: deferred
- ADR-026 disambiguation: deferred (render/adr/ vs architecture/adr/)
- Provider selection drift documentation: deferred

## .5 Inputs

Documents requiring full lifecycle metadata in .5:
- All ADRs
- All architecture/current documents
- All blueprints
- API contracts

## .6 Guard Inputs

Rules requiring automated guards:
- V1-V4 immutability
- RenderJob retry semantics
- Artifact DAG deferral
- V5 quarantine
- Frontend pause
