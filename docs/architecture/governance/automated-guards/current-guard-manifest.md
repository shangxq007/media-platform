# Current guard manifest

> **Status:** CURRENT (2026-11-15). Authoritative inventory of automatic guards that protect the
> current architecture. Regenerate the "Last verified" column only from a real run against a frozen
> commit/tree; do not copy older PASS receipts.

Guards are evidence, not authorities: each one protects a source-verifiable invariant. A guard whose
target code no longer exists must be retired or retargeted to the invariant that still holds — deleting
a failing check to get green is forbidden.

## Entry points

| Guard | Entry | Invoked by | Invariant protected |
|---|---|---|---|
| Architecture drift (aggregate) | `scripts/check-architecture-drift.sh` | CI `architecture-drift.yml`; `scripts/dev/verify guards` | formal render entry paths, First Render absence, storage/identity placement, marketplace authority, deferred status, H7/H8/phase16/phase17 boundaries |
| Instruction governance | `scripts/governance/check-instruction-governance.py` | `scripts/dev/verify guards` | instruction inventory, precedence, adapter deferral, stale tool facts, contradictory rules |
| Instruction governance self-test | `scripts/governance/test_check_instruction_governance.py` | `scripts/dev/verify guards` | guard rejects each real mutation instead of passing vacuously |
| H7 architecture | `scripts/guards/h7-architecture-guard.py` | `scripts/check-architecture-drift.sh` | single Operation/Timeline transaction authority, idempotency, allocation, parent edges, NO_OP/genesis |
| H8 invocation boundary | `scripts/guards/h8-operation-invocation-boundary-guard.py` | `scripts/check-architecture-drift.sh` | narrow public Operation invocation boundary, delegated H7 pipeline |
| Marketplace authority | `scripts/guards/marketplace-authority-guard.py` | `scripts/check-architecture-drift.sh` | marketplace owner/public composition, displaced-authority retirement |
| Storage identity/placement M0/M1 | `scripts/check-storage-identity-placement-m0-m1.py` (+ RED matrix `test-check-storage-identity-placement-m0-m1.py`) | `scripts/check-architecture-drift.sh` | storage identity and placement authority counters match frozen laws |
| Phase 16 clean-forward | `scripts/phase16-clean-forward-guards.py` | `scripts/check-architecture-drift.sh` | retired legacy render/pipeline paths stay retired |
| Phase 17 sandbox isolation | `docs/architecture/governance/automated-guards/check-phase17-sandbox-architecture.py` | `scripts/check-architecture-drift.sh` | sandbox boundary and authority |
| jOOQ parity | `./gradlew verifyJooqGeneratedSources` | `scripts/dev/verify guards`; CI | committed generated sources match the current schema and layout |
| OIDC bundle config | `scripts/check-oidc-bundle-config.sh` | `scripts/dev/verify guards` | frontend build fails closed without canonical OIDC configuration |
| Frontend architecture | `frontend/scripts/frontend-architecture-guard.mjs` | frontend test/build scripts | frontend product-path classification and forbidden couplings |
| Change-impact classifier | `scripts/ci/change_impact_classifier.py` | CI `ci.yml` | changed paths select the required CI classes instead of always running everything |
| Document governance | `scripts/check-document-governance.sh` | document governance workflow | metadata, receipts and link integrity for governed documents |
| jOOQ authority fail-closed | `scripts/verify-pfirr1-jooq-authority-fail-closed.sh` | CI `architecture-drift.yml` | jOOQ authority failure path fails closed |

## Rules applied in this consolidation

- Deferred capability status (`OpenCue` NOT_STARTED, `Artifact DAG` POSTPONED) used to be asserted
  from `.hermes.md`. The adapter no longer carries status facts; the checks now read the accepted
  records (`docs/architecture/current/architecture-assertions.md` FUT-001 and ADR-025).
- The First Render section of the architecture drift guard asserts **absence**: no
  `/api/first-render/plans` route, no `FirstRender` production component, no `first_render_idempotency`
  persistence, and exactly one formal submission idempotency table.
- Instruction governance and its self-test are part of the standard `guards` run, so an adapter that
  redefines authority or a stale tool rule fails the normal gate instead of drifting silently.

## How to run

```sh
python3 scripts/dev/verify guards          # architecture + instruction + jOOQ + OIDC
python3 scripts/dev/verify preflight --level l1
```

Every run writes a manifest with commit, tree, commands, exit codes, log paths and tool versions.
Record those paths in the task evidence; do not paraphrase them into "all guards pass" without the
manifest.
