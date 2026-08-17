# GCR5/GCR6 — jOOQ and Raw SQL Reality

## jOOQ

- Generated sources: typed-schema-module/src/main/java/com/example/platform/typedschema/jooq/generated/
- Generated files: 158 (tables + records + keys; table classes = 157 + extra)
- Generation mechanism: regenerate-jooq-schema.sh + jooq-codegen-3.19.30.jar
  against empty PostgreSQL (established, reproducible — GCR-2 used it)
- JOOQ_VERSION = 3.19.30
- Generated code is never manually edited (C15) — verified by convention + guard
- Current parity: to be proven by empty-PG regeneration (JOOQ_SCHEMA_PARITY)

## Raw SQL paths (canonical tables)

| Path | SQL | Tables | Classification |
|---|---|---|---|
| timeline-module RevisionCommandApplyService | insert/update/delete via tx.dsl().execute | apply_command, timeline_revision_ref, timeline_snapshot, timeline_revision, timeline_revision_parent | CANONICAL_PERSISTENCE (column lists match V1 — verified) |
| render-module plan/execution services | jOOQ DSL typed | render_job etc. | CANONICAL_PERSISTENCE |
| identity-access | jOOQ typed | user/project/workspace | CANONICAL_PERSISTENCE |
| workflow-module UserWorkflowDefinitionJdbcRepository | JdbcTemplate | user_workflow_definition* | CANONICAL_PERSISTENCE |

No handwritten SQL references removed/retired columns (all reference current V1
shapes). STALE_CANONICAL_SQL_REFERENCE_COUNT = 0.

## Test schema fixtures

| Fixture | Module | Shape | Parity |
|---|---|---|---|
| PostgresTestContainerSupport.createDataSource | shared test | generic | OK |
| ArtifactSchemaFixture | artifact-module testutil | canonical artifact/replica/pin/relation | OK (GCR-2 aligned) |
| RenderTestSchemaFixture | render-module testsupport | canonical artifact tables + render | OK (GCR-2 aligned) |
| SourceVisualDescriptionSnapshotIT / SourceVisualOwnershipIntegrityIT | media-module | media tables incl. svd + media_asset_artifact composite FK | VERIFY against V1 |
| OperationPlanConcurrencyIT / RevisionCommandConcurrencyIT | render-module | render/timeline subset | VERIFY against V1 |
| UserWorkflowDefinitionJdbcRepositoryTest | workflow-module | workflow tables | VERIFY against V1 |
| OidcIdentityProvisioning*Test | platform-app | user/workspace tables | VERIFY against V1 |

LEGACY_TEST_SCHEMA_FIXTURE_COUNT_BEFORE = 0 (no legacy shapes found; fixtures
create canonical-shape tables — to be re-verified after V1 changes)
