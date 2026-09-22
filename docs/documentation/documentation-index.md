# Documentation index

Classification checkpoint: 2026-09-22. The [path-level classification inventory](../architecture/governance/document-classification-inventory.tsv) assigns exactly one document class to each in-scope artifact. Classification controls navigation; old `ACTIVE` or `canonical` labels retained inside evidence do not promote it to current guidance.

## Current normative guidance

- [Architecture entrypoint](../architecture/current/architecture-entrypoint.md): logical and deployment views, identifiers, flow and authority links.
- [Authority rules](../architecture/governance/source-of-truth/architecture-authority-model.md): implementation, approved semantics, intent and derived views.
- [Canonical semantic contracts](../architecture/governance/canonical-contracts/canonical-contract-registry.json).
- [ADR registry](../architecture/governance/adr-registry.tsv): accepted decisions, proposals and supersession.
- [Flyway policy](../database/flyway-migration-baseline.md): the only current migration policy.
- [HTTP/OpenAPI authority](../api/openapi-authority.md): REST artifact, generation and compatibility evidence.

## Current operations

Operational documents explain procedures; their exact class and source scope are in the inventory. A runbook does not establish semantic authority or prove that an environment is deployed.

- [Local runbook](../runbook-local.md).
- [Production safety](../production-safety.md).
- [Architecture verification](../architecture/maps/README.md).

## Derived artifacts

- [LikeC4 source and generation](../architecture/maps/likec4/README.md).
- [Spring Modulith generated C4](../architecture/maps/generated/README.md).
- [Validation report](../architecture/governance/validation-report-2026-09-22.md).

## Historical, superseded and archived material

[Replacement mapping](../architecture/governance/superseded-archive-mapping.tsv) records each retired guide and evidence location. [Reviews](../review/), [releases](../releases/), [archive](../archive/) and dated closeout reports are historical evidence. They are not present-day operating policy or release readiness. Original consolidated Flyway documents and the former index are preserved in [the archive manifest](../architecture/governance/preservation-manifest-2026-09-22.json).
