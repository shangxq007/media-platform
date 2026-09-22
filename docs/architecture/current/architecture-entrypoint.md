---
metadata_schema_version: 1
document_id: "architecture-current-entrypoint"
title: "Current Architecture Entrypoint"
artifact_type: "ARCHITECTURE_DOCUMENT"
domain: "architecture-governance"
authority_class: "NORMATIVE_SUPPORTING"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "architecture-governance"
document_version: "1.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
review_cadence_days: 30
supersedes: []
superseded_by: []
canonical_contracts: ["api", "schema-intent"]
source_of_truth_domains: ["architecture", "api-contracts", "database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Current Architecture Entrypoint

This is the single navigation entrypoint for the repository architecture. It
links to each view and authority without copying their facts. Every statement
is labelled **CURRENT**, **TARGET**, **DEFERRED**, or **HISTORICAL** so a future
view cannot silently become a second source of truth.

## Authority model

| Authority | Role | Status |
|---|---|---|
| Source code, executable contracts, migration bytes, controller/DTO implementations, and guards | Implemented behaviour and executable constraints | **CURRENT authority** |
| Accepted ADRs and canonical-contract documents | Approved semantic decisions | **CURRENT normative authority** |
| LikeC4 source | Human-curated logical, physical, deployment, and trust intent | **CURRENT intent view** |
| Spring Modulith Documenter output | Generated as-built module structure | **GENERATED_DERIVED** |
| Archify and Fireworks-Tech-Graph | Optional derived analysis/visualisation | **DEFERRED option**; not integrated |
| Reports, diagrams, blueprints, and generated pages | Supporting evidence or projections | Never an independent authority |

Logical and physical views use the stable identifiers in
[architecture-identifiers.yaml](../maps/architecture-identifiers.yaml). The
identifier file is a cross-view key, not a third architecture model.

## Views

| View | Link | Fact class | Source |
|---|---|---|---|
| Logical architecture | [01-system-architecture](../01-system-architecture.md), [03-module-architecture](../03-module-architecture.md) | CURRENT + TARGET labels in source docs | ADRs, canonical contracts, source |
| Physical/deployment architecture | [08-deployment-architecture](../08-deployment-architecture.md), [deployment trust view](../maps/likec4/media-platform.likec4) | CURRENT deployment units; TARGET boundaries explicitly marked | source, deployment configuration, LikeC4 intent |
| Human intent map | [LikeC4 source](../maps/likec4/media-platform.likec4) | CURRENT/TARGET/DEFERRED intent | human-curated |
| As-built module map | [generated C4](../maps/generated/README.md) | CURRENT observed structure | Spring Modulith Documenter |
| Data flow and control flow | [05-request-flows](../05-request-flows.md), [06-data-architecture](../06-data-architecture.md) | CURRENT operational flow; TARGET flow labelled | source and contracts |
| Authority and module boundaries | [authority model](../governance/source-of-truth/architecture-authority-model.md), [module status](current-module-status.md) | CURRENT | ADRs, package metadata, guards |
| Canonical contracts | [canonical contract registry](../governance/canonical-contracts/canonical-contract-registry.json) | CURRENT normative, with candidate/deferred states in registry | accepted governance |
| ADR registry | [architecture ADR directory](../adr/), [registry](../governance/architecture-document-inventory.tsv) | CURRENT accepted decisions; superseded decisions labelled | ADRs |
| HTTP API contract | [OpenAPI authority](../../api/openapi-authority.md) | CURRENT transport contract | runtime export + checked-in artifact |
| Known gaps and deferred items | [current known gaps](current-known-gaps.md) | DEFERRED / gap register | governance |

## Reproducible viewing and generation

- **CURRENT logical intent:** open [`maps/likec4/media-platform.likec4`](../maps/likec4/media-platform.likec4) with LikeC4 Desktop/CLI. The checked-in HTML projection is [`maps/exports/html/index.html`](../maps/exports/html/index.html); it is a generated projection, not runtime discovery.
- **CURRENT as-built structure:** regenerate the Spring Modulith output with `./gradlew --no-daemon :platform-app:test --tests ModulithDocumentationGenerationTest`. The output is [`maps/generated/modulith/`](../maps/generated/modulith/), derived from the executable module model.
- **Provenance:** record the source commit, generator/tool version and SHA-256 of any published bundle. CI may publish the generated bundle; deployment of that bundle is out of scope for this batch.
- **Physical/deployment view:** [`08-deployment-architecture.md`](../08-deployment-architecture.md) and the LikeC4 deployment view describe declared boundaries. PVE material is **HISTORICAL** observation from dated reports and must not be read as live discovery.

## Stable cross-view identifiers

The logical and physical views share IDs for domains, modules, contracts,
authorities, data entities, and deployment units. A relationship may be shown
in one view and omitted in another, but its endpoint IDs remain stable.

| Namespace | Examples |
|---|---|
| Domain | `dom.workflow`, `dom.render`, `dom.storage` |
| Module | `mod.workflow`, `mod.render`, `mod.storage`, `mod.platform-app` |
| Contract | `ctr.api.http`, `ctr.schema.flyway`, `ctr.workflow.execution` |
| Authority | `auth.temporal`, `auth.plugin-runtime`, `auth.postgres` |
| Data entity | `data.render-job`, `data.artifact`, `data.workflow-run` |
| Deployment unit | `dep.platform-app`, `dep.remote-render-worker`, `dep.sandbox-worker` |

## Fact-state rules

- **CURRENT** describes implemented behaviour verified against source or a
  current runtime observation.
- **TARGET** describes an approved semantic direction that is not fully
  implemented; it must name its ADR or canonical contract.
- **DEFERRED** describes an intentionally postponed capability or tool.
- **HISTORICAL** describes evidence retained for traceability; it is not
  current guidance and must point to its replacement.

When a view conflicts with executable behaviour, source/executable contracts
win for what is implemented. When a semantic decision conflicts with a guide,
the accepted ADR or canonical contract wins. Resolve conflicts in the authority
model and change ledger; do not edit a generated view to hide drift.
