---
metadata_schema_version: 1
document_id: "database-flyway-current-policy"
title: "Current Flyway Migration Policy and Inventory"
artifact_type: "SCHEMA_CONTRACT"
domain: "database-schema"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "database-governance"
document_version: "2.0"
created_at: "2026-09-22"
last_reviewed_at: "2026-09-22"
review_cadence_days: 30
supersedes: ["database-flyway-migration-baseline-legacy"]
superseded_by: []
canonical_contracts: ["schema-intent"]
source_of_truth_domains: ["database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: true
blocks_v5: false
---

# Current Flyway Migration Policy and Inventory

This is the **one current normative Flyway document**. The executable source of
truth is the directory
[`platform-app/src/main/resources/db/migration/`](../../platform-app/src/main/resources/db/migration/).
No document may replace, reorder, rename, or describe a different current
migration inventory.

## Current checkpoint: verified inventory

The repository does **not** contain only `V1__initial_schema.sql`. At the
2026-09-22 checkpoint it contains these nine SQL migrations:

| Version | File | Current role |
|---|---|---|
| V1 | [`V1__initial_schema.sql`](../../platform-app/src/main/resources/db/migration/V1__initial_schema.sql) | Immutable greenfield baseline |
| V2 | [`V2__account_membership_and_project_scope.sql`](../../platform-app/src/main/resources/db/migration/V2__account_membership_and_project_scope.sql) | Account, membership, and project scope |
| V3 | [`V3__quarantine_unresolved_assignment_scopes.sql`](../../platform-app/src/main/resources/db/migration/V3__quarantine_unresolved_assignment_scopes.sql) | Forward quarantine classification |
| V4 | [`V4__quarantine_missing_assignment_tenants.sql`](../../platform-app/src/main/resources/db/migration/V4__quarantine_missing_assignment_tenants.sql) | Forward tenant quarantine classification |
| V5 | [`V5__workflow_durable_runs.sql`](../../platform-app/src/main/resources/db/migration/V5__workflow_durable_runs.sql) | Durable workflow runs |
| V6 | [`V6__marketplace_owner.sql`](../../platform-app/src/main/resources/db/migration/V6__marketplace_owner.sql) | Marketplace ownership fields |
| V7 | [`V7__marketplace_event_retirement.sql`](../../platform-app/src/main/resources/db/migration/V7__marketplace_event_retirement.sql) | Retire incompatible marketplace events |
| V8 | [`V8__social_publication_ownership.sql`](../../platform-app/src/main/resources/db/migration/V8__social_publication_ownership.sql) | Social publication attempt ownership |
| V9 | [`V9__social_credential_fence.sql`](../../platform-app/src/main/resources/db/migration/V9__social_credential_fence.sql) | Credential revision fencing |

The exact set and the immutable V1 checksum are also enforced by the Gradle
GCR-2/GCR-5/GCR-6 checks. A clean checkout must report exactly this set.

## Policy

- V1 is the adopted baseline and its bytes are immutable after acceptance.
- V2 and later are forward-only migrations. Never edit an applied migration;
  add the next version with a precise description.
- Use `V<N>__<description>.sql`, with a monotonic version and a reviewable,
  idempotency-safe data transition where required.
- Keep schema intent, executable migrations, jOOQ generated types, and runtime
  observations cross-referenced. `docs/ddl-postgresql.sql` is informative and
  is not a DDL authority.
- Do not create module-local production DDL as a competing source of truth.
- Do not use `flyway clean` in a persistent environment. `repair` is limited to
  an explicitly approved local checksum recovery and must be recorded.

## Baseline and release procedure

1. Validate the exact inventory and V1 checksum with the repository Gradle
   governance checks.
2. Apply migrations on a disposable or release-candidate PostgreSQL database.
3. Run schema and application integration tests, then record the migration
   history table and checksum evidence.
4. Promote the same migration bytes through environments; do not rebuild or
   rename an applied version.
5. For a failed release, stop promotion, preserve the database evidence, and
   use a reviewed forward repair migration. Flyway rollback is not automatic.

Commands:

```bash
find platform-app/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' | sort -V
./gradlew --no-daemon verifyGcr5Gcr6DatabaseCanonicalization
./gradlew --no-daemon :platform-app:test --tests '*Flyway*'
```

## Rollback limitations

Flyway migrations are not generally reversible. A release rollback may revert
application binaries only when the previous binary understands the already
applied schema. Destructive or incompatible schema changes require a reviewed
forward migration, backup/restore plan, and explicit operational approval.

## Historical and superseded references

Older migration reports, V1-only consolidation notes, and references to names
such as `V1__init_full_schema.sql`, historical V2/V6/V11 files, or old migration
counts are retained as **HISTORICAL_EVIDENCE** or **ARCHIVED**. They are not
current policy sources. See the [architecture document inventory](../architecture/governance/architecture-document-inventory.tsv).
