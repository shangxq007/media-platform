---
metadata_schema_version: 1
document_id: "database-flyway-current-policy"
title: "Current Flyway Migration Policy and Inventory"
artifact_type: "SCHEMA_CONTRACT"
domain: "database-schema"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "TECHNICAL_ACCEPTANCE_PENDING"
owner: "database-governance"
document_version: "3.1"
created_at: "2026-09-22"
last_reviewed_at: "2026-10-20"
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

At the **2026-10-15** checkpoint the Flyway directory contains exactly **one** SQL
migration: the single baseline. The former layered chain was consolidated by the
Owner-authorized task `FLYWAY-SINGLE-BASELINE-GOVERNANCE` (Phase 3A).

| Version | File | Current role |
|---|---|---|
| V1 | [`V1__baseline.sql`](../../platform-app/src/main/resources/db/migration/V1__baseline.sql) | **Single immutable baseline** — the whole current mainline schema |

The Owner decision `FLYWAY-OWNER-BASELINE-DECISION-R1` approved the current
candidate for controlled implementation and technical acceptance:

- Size: `210,121` bytes
- SHA-256:
  `49c79810db8e928626402b10f102613a5ffe8f6073b265458c0c09a964c58795`
- State: `APPROVED_FOR_IMPLEMENTATION`
- Technical acceptance: `TECHNICAL_ACCEPTANCE_PENDING`

This approval is not a successful empty-database migration record. The current
bytes must not be described as technically accepted until the separately
authorized isolated-database, schema-fingerprint, and jOOQ parity checks pass.

The baseline absorbs the former `V1__initial_schema.sql`,
`V2__render_binding_inputs.sql`, `V3__worker_fabric_admission_evidence.sql`,
`V4__worker_readiness_evidence.sql` and `V5__provider_candidate_snapshot.sql`.
Its schema scope is: **220 tables, 302 indexes (16 of them unique), 220 primary keys,
135 unique constraints, 122 foreign keys, 22 PL/pgSQL functions, 21 triggers and
14 seed rows**. The former incremental `ALTER TABLE` statements were folded into the
affected `CREATE TABLE` definitions (`render_binding_inputs.candidate_set_digest`
stays nullable and keeps its post-consolidation column position; the former V3
observed-usage columns were folded into `wf_host_resource_snapshot` and
`wf_host_resource_snapshot_device`).

The exact set and the immutable V1-baseline checksum are enforced by the Gradle
GCR-2 / GCR-5 / GCR-6 checks; `verifyJooqGeneratedSources` additionally enforces
exact two-way parity between the baseline and the committed jOOQ sources. A clean
checkout must report exactly this set (`V1__baseline.sql` and nothing else).
The current guard pins `49c79810...`, the hash approved for implementation.

The V5 source audit copy is retained outside the runtime migration directory at
[`evidence/flyway-single-baseline/V5__provider_candidate_snapshot.sql`](evidence/flyway-single-baseline/V5__provider_candidate_snapshot.sql)
with SHA-256
`9de6c8c3289934b55be718ae3d58c1d38950cc6bbc4b3d5e0f52ef99957e2021`.
It is historical source evidence, not an active V5 migration.

The original temporary source path
(`/tmp/g1-baseline-sources/V5__provider_candidate_snapshot.sql`) was not present
during the 2026-10-20 remediation review. It was not recreated or substituted.
The repository audit copy is the only retained source evidence; a byte-for-byte
re-comparison against the original could not be performed in this round. This
limitation does not change the baseline bytes or the pending
technical-acceptance status.

## Policy

- `V1__baseline.sql` is the Owner-approved implementation candidate. Its current
  bytes must not change during technical acceptance. Any byte change requires a
  new explicit Owner ruling. After acceptance its bytes are immutable. Every
  subsequent schema change starts at `V2__*.sql` and is forward-only.
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

## Canonical database and acceptance environment

- `application.yml` and the profile files default to the database/role
  `media_platform` on `127.0.0.1:5432`, while the repository compose files declare
  `platform`/`platform`. The two disagree; this is a known configuration drift and
  must not be "fixed" by editing configuration in a baseline task.
- The long-lived development database records an unrelated pre-consolidation
  migration lineage and therefore **cannot** host this baseline; it is retained
  untouched.
- Baseline acceptance therefore runs against a **dedicated, disposable, empty**
  PostgreSQL instance chosen by the Owner (D3). No other database — development,
  test or production — may be used for the acceptance run.

## Acceptance requirements (empty-database migration)

1. The validation instance is created for this purpose only: empty, isolated, and
   disposable, with no pre-existing migration history and no persistent volume that
   is shared with other environments.
2. A **single** `flyway migrate` run applies the baseline completely. No manual
   pre-creation of schema objects and no implicit initialisation step may be
   required.
3. `flyway_schema_history` on that instance must show the baseline applied.
   `baseline-on-migrate` (enabled in `application.yml`) must **not** be used as a
   substitute: on a pre-existing non-empty database it records a baseline row
   *without* executing the baseline DDL and is therefore not evidence of success.
4. Schema completeness is verified against the approved object list — object counts
   per type **and** the specific columns, keys, checks and indexes contributed by the
   consolidated migrations. A successful exit code alone is not acceptance.
5. jOOQ regeneration must reproduce the committed generated sources with no
   unexplained difference; each necessary difference must be explained and approved.

## Baseline and release procedure

1. Validate the exact inventory and V1 checksum with the repository Gradle
   governance checks.
2. Apply migrations on a disposable or release-candidate PostgreSQL database
   (see "Canonical database and acceptance environment" above).
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
./gradlew --no-daemon verifyJooqGeneratedSources
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
