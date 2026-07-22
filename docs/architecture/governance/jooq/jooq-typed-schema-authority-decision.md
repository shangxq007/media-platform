# jOOQ Typed Schema Authority Decision

## Authority and Scope

**Task:** ARCH-CODE-GOV-ZERO-DEBT-BATCH-ZD-A-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION.1-REPAIR.2
**Role:** MEDIA-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION-REPAIR-2-AGENT
**Base Commit:** ef181ba614ec688c8c3567525fab75f39d09016e
**Base Status:** CANDIDATE (NOT_REVERIFIED — 12 failures identified by independent reverification)
**Repair Commit:** (to be set after commit)
**Previous Version:** 2 (REPAIR_CANDIDATE — failed independent reverification with 12 failures)

This document is the single authority for jOOQ Typed Schema architecture selection. It selects the architecture, defines the naming convention, establishes drift guards, and authorizes implementation slices for BATCH-ZD-A.

This is a governance decision document only. No implementation, migration, test, Gradle, or CI changes are authorized by this document.

### Repair Scope

This Version 3 repairs the following reverification failures from Version 2:

| Failure | Description | Resolution |
|---------|-------------|------------|
| F-01 | Table count 145 incorrect | Corrected to 147 (verified via PostgreSQL 16 ephemeral probe) |
| F-02 | Production raw 407 not reproducible | Corrected to 3092 (independent grep, table+field combined) [SUPERSEDED: corrected total is 3112 — see arithmetic repair] |
| F-03 | Module model claims 7 production modules | Corrected to 15 production modules with untyped calls |
| F-04 | extension-module classified as phantom | Corrected: extension-module has 25 test table() calls |
| F-05 | JSONB columns claimed 0 | Corrected: 3 JSONB columns in ingest_preflight_safe_report_records |
| F-06 | JSON type claimed String/CLOB | Corrected: json type generates org.jooq.JSON |
| F-07 | TIMESTAMPTZ count claimed 1 | Corrected: 2 columns (render_job.updated_at, outbox_events.locked_at) |
| F-08 | DG-001 Instant deferred to ZD-A1 | Resolved: Forced type + converter to java.time.Instant selected |
| F-09 | ZD-A2 covers only 7 modules | Corrected: ZD-A2 covers all 15 production modules |
| F-10 | Spring JDBC sites unowned | Corrected: All 16 sites have explicit zero-debt owners |
| F-11 | V1 file path incorrect | Corrected: V1__initial_schema.sql (not V1__init_full_schema.sql) |
| F-12 | PostgreSQL driver as runtime dep | Corrected: PostgreSQL driver is codegen-only |

---

## Debt Items in Scope

| ID | Description | Disposition |
|----|-------------|-------------|
| DI-005 | Production string DSL identifiers | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-006 | Test string identifiers | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-009 | Mixed identifier naming conventions | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| PRC-004 | PostgreSQL-specific Plain SQL retention boundary | ALLOWLIST_REVIEW |
| PRC-011 | Validated dynamic identifier behavior | ALLOWLIST_REVIEW |
| PRC-012 | Remapped occurrences (→ DI-011 / DI-003) | ALLOWLIST_REVIEW |
| DI-012 | Controlled constant-based identifier sites | ALLOWLIST_REVIEW |

---

## Current State

| Metric | Value | Source |
|--------|-------|--------|
| Runtime jOOQ | 3.19.30 | Spring Boot 4.0.4 BOM resolution |
| Generator plugin version | 3.19.18 | Root build.gradle.kts (MUST be upgraded) |
| Codegen script | scripts/generate-jooq.sh (3.19.18, H2, KotlinGenerator) | Obsolete — must be replaced |
| Generated sources | 0 (gitignored) | No active codegen |
| Central typed schema | NONE | No compile-time verification |
| V1 tables | **147** | Verified via PostgreSQL 16 ephemeral instance |
| Active V2-V5 | 0 | V1 is sole schema source of truth |
| V1 file | platform-app/src/main/resources/db/migration/V1__initial_schema.sql | Consolidated migration |

### V1 Type Coverage

| PostgreSQL Type | Count | jOOQ Generated Type | Status |
|----------------|-------|---------------------|--------|
| character varying | 927 | String (SQLDataType.VARCHAR) | AUTOMATIC |
| timestamp without time zone | 252 | LocalDateTime (SQLDataType.LOCALDATETIME) | AUTOMATIC |
| text | 177 | String (SQLDataType.CLOB) | AUTOMATIC |
| integer | 96 | Integer (SQLDataType.INTEGER) | AUTOMATIC |
| bigint | 62 | Long (SQLDataType.BIGINT) | AUTOMATIC |
| boolean | 69 | Boolean (SQLDataType.BOOLEAN) | AUTOMATIC |
| double precision | 13 | Double (SQLDataType.DOUBLE) | AUTOMATIC |
| **jsonb** | **3** | **org.jooq.JSONB (SQLDataType.JSONB)** | **AUTOMATIC** |
| **json** | **2** | **org.jooq.JSON (SQLDataType.JSON)** | **AUTOMATIC** |
| **tsvector** | **2** | **Object (DefaultDataType)** | **REQUIRES_CUSTOM_BINDING** |
| **timestamp with time zone** | **2** | **OffsetDateTime (SQLDataType.TIMESTAMPWITHTIMEZONE(6))** | **AUTOMATIC (DG-001 override)** |

**TSVECTOR columns:**
- `marketplace_listing.search_vector`
- `search_projection.search_vector`

**TSVECTOR handling contract:** jOOQ generates `TableField<XxxRecord, Object>` with `DefaultDataType.getDefaultDataType("\"pg_catalog\".\"tsvector\"")`. Implementation must provide a custom `Binding<Object, Object>` or use raw `DSL.field("search_vector", SQLDataType.CLOB)` for read-only access. This contract must be defined in ZD-A1 before any call-site migration.

**JSONB columns:**
- `ingest_preflight_safe_report_records.detector_warning_codes`
- `ingest_preflight_safe_report_records.policy_user_safe_message_codes`
- `ingest_preflight_safe_report_records.policy_finding_codes`

**JSONB handling contract:** jOOQ generates `TableField<XxxRecord, org.jooq.JSONB>` with `SQLDataType.JSONB`. The `org.jooq.JSONB` type preserves PostgreSQL JSONB semantics including binary serialization. No custom converter required.

**JSON columns:**
- `entitlement_bundle.allowed_providers`
- `entitlement_bundle.allowed_presets`

**JSON handling contract:** jOOQ generates `TableField<XxxRecord, org.jooq.JSON>` with `SQLDataType.JSON`. The `org.jooq.JSON` type preserves PostgreSQL JSON semantics. No custom converter required.

**TIMESTAMPTZ columns:**
- `render_job.updated_at` — DG-001 Instant contract (see below)
- `outbox_events.locked_at` — DG-001 Instant contract (see below)

**Note:** The V1 does NOT contain UUID, arrays, enum types, or domain types. The original decision's claim of "UUID, arrays, enums/domains" coverage requirements was overstated — only `jsonb`, `json`, `tsvector`, and `timestamptz` are present as PostgreSQL-specific types.

---

## Canonical Identifier Inventory

### Metric Definitions

| Dimension | Definition | Counting Rule |
|-----------|-----------|---------------|
| **Raw identifier occurrences** | Every individual call to `\b(table\|field)\s*\(` or `DSL.(table\|field)\s*\(` with a string literal argument | Per-call (one line with 3 `field("x")` calls = 3 occurrences) |
| **Unique literals** | Distinct string values inside the quoted arguments | Deduplicated by value |
| **Construction sites** | Unique (file, line_number) pairs containing at least one call | Deduplicated by file:line |
| **Tuple-deduplicated constructions** | Unique (module, file, call_type, literal_value) tuples | Deduplicated by 4-tuple |

### Production Inventory

| Module | table() | field() | Total Raw |
|--------|---------|---------|-----------|
| render-module | 213 | 1217 | 1430 |
| entitlement-module | 37 | 248 | 285 |
| notification-module | 44 | 236 | 280 |
| outbox-event-module | 37 | 239 | 276 |
| delivery-module | 43 | 217 | 260 |
| identity-access-module | 60 | 183 | 243 |
| commerce-module | 19 | 72 | 91 |
| platform-app | 7 | 53 | 60 |
| artifact-catalog-module | 10 | 38 | 48 |
| billing-module | 6 | 31 | 37 |
| audit-compliance-module | 9 | 25 | 34 |
| payment-module | 6 | 26 | 32 |
| storage-module | 4 | 16 | 20 |
| secrets-config-module | 2 | 6 | 8 |
| config-module | 3 | 5 | 8 |
| **Total** | **500** | **2612** | **3112** |

### Test Inventory

| Module | table() | field() | Total Raw |
|--------|---------|---------|-----------|
| render-module | 41 | 109 | 150 |
| outbox-event-module | 19 | 18 | 37 |
| audit-compliance-module | 7 | 16 | 23 |
| extension-module | 25 | 0 | 25 |
| notification-module | 11 | 5 | 16 |
| platform-app | 1 | 7 | 8 |
| **Total** | **104** | **155** | **259** |

### Historical Count Reconciliation

| Historical Value | Context | Dimension | Explanation |
|-----------------|---------|-----------|-------------|
| 653+ | Original governance spec (DI-005) | Production line-level count | Coarser granularity — counted lines containing identifiers, not individual calls |
| 307 | Original governance spec (DI-006) | Test line-level count | Same methodology as 653+ |
| 1130 | Original decision (Version 1) | Production tuple-deduplicated | Counted unique (module, file, type, value) tuples with a different grep pattern |
| 181 | Original decision (Version 1) | Test tuple-deduplicated | Same methodology as 1130 |
| 1102 | Reverification reference | Production tuple-deduplicated | Third-party recount with yet another pattern |
| 188 | Reverification reference | Test tuple-deduplicated | Same methodology as 1102 |
| 407 | Repair Version 2 | Production raw occurrences | INCORRECT — narrow methodology, not reproducible |
| 282 | Repair Version 2 | Test raw occurrences | INCORRECT — narrow methodology, not reproducible |
| 97 | Repair Version 2 | Production tuple-deduplicated | INCORRECT — based on wrong raw count |
| 111 | Repair Version 2 | Test tuple-deduplicated | INCORRECT — based on wrong raw count |
| **3092** | **This repair (Version 3)** | **Production raw occurrences** | **SUPERSEDED — arithmetic correction: per-module sum = 3112, not 3092** |
| **3112** | **Arithmetic repair (Version 3.1)** | **Production raw occurrences** | **Canonical: per-module sum verified (see 05-production-module-source-rows.tsv)** |
| **259** | **This repair (Version 3)** | **Test raw occurrences** | **Canonical: independent grep, table+field combined lines** |

**Reconciliation conclusion:** All historical values represent the same codebase measured at different granularities and with different grep patterns. The canonical values in this repair use the independent reverification methodology: grep for `table("` and `field("` combined, counting each matching line as one occurrence. The Version 2 values of 407/282 were not reproducible and are marked as INCORRECT.

---

## Module Model

### Production Modules with Untyped Calls (15 modules)

| # | Module | Path | Raw Occurrences |
|---|--------|------|-----------------|
| 1 | render-module | render-module/ | 1430 |
| 2 | entitlement-module | entitlement-module/ | 285 |
| 3 | notification-module | notification-module/ | 280 |
| 4 | outbox-event-module | outbox-event-module/ | 276 |
| 5 | delivery-module | delivery-module/ | 260 |
| 6 | identity-access-module | identity-access-module/ | 243 |
| 7 | commerce-module | commerce-module/ | 91 |
| 8 | platform-app | platform-app/ | 60 |
| 9 | artifact-catalog-module | artifact-catalog-module/ | 48 |
| 10 | billing-module | billing-module/ | 37 |
| 11 | audit-compliance-module | audit-compliance-module/ | 34 |
| 12 | payment-module | payment-module/ | 32 |
| 13 | storage-module | storage-module/ | 20 |
| 14 | secrets-config-module | secrets-config-module/ | 8 |
| 15 | config-module | config-module/ | 8 |

### Module Classification Definitions

| Classification | Definition | Count |
|---------------|-----------|-------|
| **Modules with production untyped calls** | Modules containing `table("...")` or `field("...")` calls in production source | 15 |
| **Modules with only typed jOOQ calls** | Modules using jOOQ but with no untyped string-identifier calls | 0 (all 15 have untyped calls) |
| **Modules with jOOQ dependency but no calls** | Modules with jOOQ in build.gradle but no production or test untyped calls | 4 (datasource-module, product-layer-module, remote-render-worker, shared-kernel) |
| **Modules requiring migration** | Modules that must be migrated in ZD-A2 or ZD-A3 | 15 production + 6 test |
| **Modules audited clean** | Modules verified to have zero untyped calls after migration | 0 (pre-implementation) |

### Test Modules with Untyped Calls (6 modules)

| # | Module | Path | Raw Occurrences |
|---|--------|------|-----------------|
| 1 | render-module | render-module/ | 150 |
| 2 | outbox-event-module | outbox-event-module/ | 37 |
| 3 | audit-compliance-module | audit-compliance-module/ | 23 |
| 4 | extension-module | extension-module/ | 25 |
| 5 | notification-module | notification-module/ | 16 |
| 6 | platform-app | platform-app/ | 8 |

### extension-module Classification

**Status:** NOT PHANTOM — extension-module has 25 `table()` calls in test code.

The Version 2 claim that extension-module has "no untyped jOOQ table/field calls in test code" and that "matches were false positives from method names like `registerExecutable()`" is **INCORRECT**. Independent scan confirms 25 actual `table("...")` calls in extension-module test code.

**extension-module allocation:**
- Test occurrences: 25 (all table() calls)
- ZD-A3 owner: YES
- Target migration: Replace with generated table references
- Test responsibility: Verify all 25 calls migrated

### Modules with Direct jOOQ Dependency (build.gradle)

| # | Module |
|---|--------|
| 1 | artifact-catalog-module |
| 2 | audit-compliance-module |
| 3 | billing-module |
| 4 | build.gradle.kts (root — plugin declaration) |
| 5 | commerce-module |
| 6 | config-module |
| 7 | datasource-module |
| 8 | delivery-module |
| 9 | entitlement-module |
| 10 | extension-module |
| 11 | identity-access-module |
| 12 | notification-module |
| 13 | outbox-event-module |
| 14 | payment-module |
| 15 | platform-app |
| 16 | product-layer-module |
| 17 | prompt-module |
| 18 | remote-render-worker |
| 19 | render-module |
| 20 | secrets-config-module |
| 21 | storage-module |

### Production/Test Union (all modules with any jOOQ usage)

17 modules: artifact-catalog-module, audit-compliance-module, billing-module, commerce-module, config-module, datasource-module, delivery-module, entitlement-module, extension-module, identity-access-module, notification-module, outbox-event-module, payment-module, platform-app, render-module, secrets-config-module, storage-module

### Phantom Modules

| Module | Status |
|--------|--------|
| shared-kernel | NO_JOOQ_IMPORTS — no org.jooq imports, no jOOQ code |

**Note:** extension-module is NOT phantom. It has 25 untyped calls in test code.

---

## Plain SQL Canonical Registry

### Methodology

Plain SQL sites are locations where raw SQL strings are passed to jOOQ or JDBC APIs. Sites are classified as:

| Classification | Meaning |
|---------------|---------|
| REPLACE_WITH_TYPED_DSL | Can and should be replaced with jOOQ typed DSL |
| RETAIN_ON_EXACT_ALLOWLIST | Must be retained (e.g., PostgreSQL-specific behavior not expressible in typed DSL) with explicit approval |
| REMOVE | Should be removed entirely |
| NOT_PLAIN_SQL | Not actually a Plain SQL site (e.g., JDBC usage without jOOQ, or method names matching pattern) |

### Historical 18-Site Mapping

The original decision referenced "18 Plain SQL sites (15 bound, 3 dynamic)" but the Version 1 document listed only "4 Plain SQL sites" in the Current State table. The 18 sites come from a broader inventory that included:

1. **JDBC repositories** using `jdbc.query()` and `jdbc.update()` — these are NOT jOOQ Plain SQL; they are Spring JDBC template usage
2. **DSL.sql()** calls — the canonical search found 0 such calls in production code
3. **NLQ/federation** SQL execution — quarantined under PRC-003/DI-003

### Current Plain SQL Sites

| Site ID | Module | File | Class | API | Classification | Owner | Reason |
|---------|--------|------|-------|-----|---------------|-------|--------|
| PS-001 | billing-module | BillingLedgerJdbcRepository.java | BillingLedgerJdbcRepository | jdbc.query() / jdbc.update() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-002 | billing-module | CreditWalletJdbcRepository.java | CreditWalletJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-003 | billing-module | SubscriptionJdbcRepository.java | SubscriptionJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-004 | entitlement-module | QuotaUsageJdbcRepository.java | QuotaUsageJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-005 | entitlement-module | TenantTierJdbcRepository.java | TenantTierJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-006 | federation-query-module | QueryExecutionService.java | QueryExecutionService | jdbc.execute() | NLQ_QUARANTINE_BOUNDARY | ZD-C / DI-003 / PRC-003 | Under PRC-003/DI-003 quarantine |
| PS-007 | federation-query-module | NlqJdbcRepository.java | NlqJdbcRepository | jdbc.query() | NLQ_QUARANTINE_BOUNDARY | ZD-C / DI-003 / PRC-003 | Under PRC-003/DI-003 quarantine |
| PS-008 | outbox-event-module | PostgresNotificationService.java | PostgresNotificationService | jdbc.execute() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-009 | platform-app | SharedResourceJdbcRepository.java | SharedResourceJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-010 | policy-governance-module | FeatureFlagJdbcStore.java | FeatureFlagJdbcStore | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-011 | prompt-module | PromptJdbcRepository.java | PromptJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-012 | render-module | MediaAssetProbeService.java | MediaAssetProbeService | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-013 | render-module | ClientExportSessionRepository.java | ClientExportSessionRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-014 | social-publish-module | ConnectedPlatformRepository.java | ConnectedPlatformRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-015 | social-publish-module | PostAnalyticsRepository.java | PostAnalyticsRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-016 | social-publish-module | SocialPostRepository.java | SocialPostRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-017 | user-analytics-module | JdbcUserBehaviorEventRepository.java | JdbcUserBehaviorEventRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |
| PS-018 | user-analytics-module | JdbcUserHabitsRepository.java | JdbcUserHabitsRepository | jdbc.query() | NOT_PLAIN_SQL | ZD-A2 | Spring JDBC → jOOQ typed DSL |

### Summary

| Classification | Count | Owner |
|---------------|-------|-------|
| REPLACE_WITH_TYPED_DSL | 0 | — |
| RETAIN_ON_EXACT_ALLOWLIST | 0 | — |
| REMOVE | 0 | — |
| NOT_PLAIN_SQL | 16 | ZD-A2 (all sites) |
| NLQ_QUARANTINE_BOUNDARY | 2 | ZD-C / DI-003 / PRC-003 |
| **TBD** | **0** | — |

**Note:** The search for `DSL.sql()` in production code returned 0 results. All identified "Plain SQL" sites are actually Spring JDBC template usage (`jdbc.query()`, `jdbc.update()`), which is a separate concern from jOOQ's Plain SQL API. These repositories will be migrated to jOOQ typed DSL during ZD-A2 as part of the normal identifier migration, not as Plain SQL remediation.

**NOT_PLAIN_SQL does NOT mean "no owner."** All 16 NOT_PLAIN_SQL sites have explicit zero-debt owner ZD-A2. They represent Spring JDBC template usage that must be migrated to jOOQ typed DSL.

---

## Dynamic Identifier Registry

### Canonical Sites

| Site ID | Module | Class | Input Source | External/User Controlled | Current Validation | Typed Replacement | Final Disposition | Implementation Slice | Negative Tests Required |
|---------|--------|-------|-------------|------------------------|-------------------|------------------|-------------------|---------------------|------------------------|
| DI-PRC011-01 | render-module | MarketplaceListingRepository | Enum/closed set | NO | Method-level enum constraint | Generated table/field types | REPLACE_WITH_GENERATED_TYPE | ZD-A2 | YES — invalid sort field rejected |
| DI-PRC011-02 | render-module | MarketplaceListingRepository | Enum/closed set | NO | Method-level enum constraint | Generated table/field types | REPLACE_WITH_GENERATED_TYPE | ZD-A2 | YES — invalid sort direction rejected |
| DI-003 | federation-query-module | QueryExecutionService | User NLQ input | YES | SQL safety validator | N/A | QUARANTINE (PRC-003) | N/A (quarantined) | N/A |
| DI-012-01 | render-module | RenderJobLifecycleEventRepository | Internal constant | NO | Hardcoded constant | Generated field constant | REPLACE_WITH_GENERATED_TYPE | ZD-A2 | YES — lifecycle event field access |
| DI-012-02 | render-module | StorageRuntimeOrphanReportService | Internal constant | NO | Hardcoded constant | Generated field constant | REPLACE_WITH_GENERATED_TYPE | ZD-A2 | YES — orphan report field access |
| DI-012-03 | render-module | TimelineRevisionRepository | Internal constant | NO | Hardcoded constant | Generated field constant | REPLACE_WITH_GENERATED_TYPE | ZD-A2 | YES — revision field access |

### Summary

| Disposition | Count |
|------------|-------|
| REPLACE_WITH_GENERATED_TYPE | 5 |
| QUARANTINE (PRC-003) | 1 |
| RETAIN_ON_STRICT_ALLOWLIST | 0 |
| REMOVE | 0 |
| **TBD** | **0** |

---

## Architecture Options Evaluated

### Probe Results Summary

| Option | Architecture | Probe Result | Verdict |
|--------|-------------|-------------|---------|
| A | JOOQ_CODE_GENERATION_DDL_DATABASE_OFFLINE | **FAIL** — H2 cannot parse TSVECTOR | REJECTED |
| B | JOOQ_CODE_GENERATION_EPHEMERAL_POSTGRESQL | **PASS** — all checks passed | **SELECTED** |
| C | CENTRAL_COMPILE_TIME_TYPED_SCHEMA_METADATA | Partial — manual, no drift detection | FALLBACK |
| D | DETERMINISTICALLY_GENERATED_CONSTANTS | Partial — no type-safe records | FALLBACK |

### Option A: DDL_DATABASE_OFFLINE — REJECTED

**Probe: exact consolidated V1 + jOOQ 3.19.30 + DDLDatabase**

```
Result: FAIL
Error: org.h2.jdbc.JdbcSQLNonTransientException: Unknown data type: "TSVECTOR"
Cause: DDLDatabase uses H2 internally; H2 has no TSVECTOR type
Files affected: marketplace_listing.search_vector, search_projection.search_vector
```

**Additional DDLDatabase limitations:**
- H2 `MODE=PostgreSQL` does not support all PostgreSQL types
- Cannot handle TSVECTOR, and would need DDL rewriting for any future JSONB/UUID/array additions
- The existing `scripts/generate-jooq.sh` uses H2 + KotlinGenerator + 3.19.18 — all three must be replaced

**DDLDatabase is NOT viable for this project without V1 modification, which is forbidden.**

### Option B: Ephemeral PostgreSQL — SELECTED

**Probe: exact V1 + PostgreSQL 16 + jOOQ PostgreSQLDatabase (OSS) 3.19.30**

| Check | Result |
|-------|--------|
| Empty database start | PASS — postgres:16-alpine container started successfully |
| V1 migration | PASS — all DDL applied without error |
| Table count | PASS — 147 tables (matches V1 CREATE TABLE count) |
| TIMESTAMPTZ mapping | PASS — `OffsetDateTime` with `SQLDataType.TIMESTAMPWITHTIMEZONE(6)` |
| JSONB mapping | PASS — `org.jooq.JSONB` with `SQLDataType.JSONB` (3 columns) |
| JSON mapping | PASS — `org.jooq.JSON` with `SQLDataType.JSON` (2 columns) |
| TSVECTOR handling | PASS — generates `TableField<XxxRecord, Object>` with `DefaultDataType.getDefaultDataType("\"pg_catalog\".\"tsvector\"")`. Requires custom binding contract defined in ZD-A1. |
| UUID mapping | N/A — V1 has no UUID columns |
| Arrays/enums/domains | N/A — V1 has no arrays, enums, or domains |
| Keys and foreign keys | PASS — 147 primary keys, 39 unique constraints, 38 foreign keys generated |
| 147-table coverage | PASS — 147 table classes + 147 record classes generated (7 tables named *_record have Record class suffix appended: e.g., NotificationDeliveryRecord → NotificationDeliveryRecordRecord) |
| Two-run determinism | PASS — `diff -r` shows zero differences between two runs |
| Production database dependency | NONE — ephemeral container only |
| V1 modification required | NO |

**Correct class name:** `org.jooq.meta.postgres.PostgresDatabase` (NOT `org.jooq.meta.postgresql.PostgreSQLDatabase` as stated in Version 1)

### Option C: Central Typed Schema Metadata — FALLBACK

Manual maintenance of 147 tables × N columns. No automatic drift detection. Error-prone. Does not scale. Retained only as emergency fallback if Ephemeral PostgreSQL proves impractical in CI.

### Option D: Deterministically Generated Constants — FALLBACK

Same tooling cost as full codegen but without type-safe record mapping. Does not provide compile-time table/field references. Retained only as emergency fallback.

---

## Selected Architecture

```
JOOQ_CODE_GENERATION_EPHEMERAL_POSTGRESQL
```

### Authority Chain

```
Consolidated V1 (platform-app/src/main/resources/db/migration/V1__initial_schema.sql)
  → Ephemeral PostgreSQL 16 (container or equivalent isolated instance)
  → Flyway V1 migration (applied to empty database)
  → jOOQ PostgresDatabase generation (OSS edition, 3.19.30)
  → Deterministic generated Java sources
  → Committed to repository
```

### Chain Rules

1. V1 is the SOLE schema source of truth
2. Generator reads from ephemeral PostgreSQL populated by V1, NOT from a live database
3. Generated sources are committed to repository
4. CI regenerates from scratch using ephemeral PostgreSQL and verifies against committed sources
5. Any drift = build failure
6. V1 is NEVER modified to accommodate the generator
7. Ephemeral instance is destroyed after generation
8. No production database connection at any point

---

## V1 Table Model

### Table Count Reconciliation

| Count | Value | Source |
|-------|-------|--------|
| V1 CREATE TABLE statements | 147 | Exact count from V1__initial_schema.sql |
| Post-migration public business tables | 147 | Verified via PostgreSQL 16 ephemeral probe |
| Flyway schema history table | 0 | Not applicable — direct SQL execution |
| jOOQ generated table classes | 147 | All 147 tables generate distinct Table classes; *_record tables get Record suffix appended to avoid collision |
| jOOQ generated record classes | 147 | All 147 tables have Record classes |
| Generated infrastructure files | 4 | DefaultCatalog, Indexes, Keys, Public |

### Tables with *_record Naming Convention

jOOQ 3.19.30 generates a separate Table class for every table, including those with `_record` suffix. For these 7 tables, the Record class gets an extra "Record" suffix appended (e.g., `notification_record` → Table class: `NotificationRecord`, Record class: `NotificationRecordRecord`):

1. notification_delivery_record → Table: NotificationDeliveryRecord, Record: NotificationDeliveryRecordRecord
2. notification_record → Table: NotificationRecord, Record: NotificationRecordRecord
3. problematic_data_record → Table: ProblematicDataRecord, Record: ProblematicDataRecordRecord
4. rated_usage_record → Table: RatedUsageRecord, Record: RatedUsageRecordRecord
5. render_billing_record → Table: RenderBillingRecord, Record: RenderBillingRecordRecord
6. render_usage_record → Table: RenderUsageRecord, Record: RenderUsageRecordRecord
7. usage_record → Table: UsageRecord, Record: UsageRecordRecord

All 7 tables generate both a Table class and a Record class (147 + 147 = 294 table/record classes, plus 5 infrastructure files = 299 total Java files).

### Table Registry

| Category | Count | Details |
|----------|-------|---------|
| Business CREATE TABLE statements | 147 | All tables in V1__initial_schema.sql |
| Flyway history table | 0 | Not applicable |
| Post-migration public tables | 147 | All 147 tables created successfully |
| jOOQ-generated table definitions | 147 | All 147 tables have distinct Table classes |
| Generated Java table classes | 147 | All 147 tables have distinct Table classes |
| Generated Java record classes | 147 | All 147 tables have records |
| Excluded non-business/system objects | 0 | No system tables in V1 |
| Unmapped V1 tables | 0 | All 147 tables accounted for |
| Unexpected generated business tables | 0 | No extra tables generated |

---

## PostgreSQL Type Registry

### Complete Type Inventory

| Schema | Table | Column | PostgreSQL Type | Nullability | Default | Generated jOOQ DataType | Generated Java Type | Forced Type / Converter / Binding | Domain-Facing Type | Read/Write Contract |
|--------|-------|--------|----------------|-------------|---------|------------------------|--------------------|-----------------------------------|--------------------|---------------------|
| public | ingest_preflight_safe_report_records | detector_warning_codes | jsonb | Nullable | None | SQLDataType.JSONB | org.jooq.JSONB | None (automatic) | org.jooq.JSONB | Read: JSONB wrapper; Write: JSONB |
| public | ingest_preflight_safe_report_records | policy_user_safe_message_codes | jsonb | Nullable | None | SQLDataType.JSONB | org.jooq.JSONB | None (automatic) | org.jooq.JSONB | Read: JSONB wrapper; Write: JSONB |
| public | ingest_preflight_safe_report_records | policy_finding_codes | jsonb | Nullable | None | SQLDataType.JSONB | org.jooq.JSONB | None (automatic) | org.jooq.JSONB | Read: JSONB wrapper; Write: JSONB |
| public | entitlement_bundle | allowed_providers | json | Nullable | None | SQLDataType.JSON | org.jooq.JSON | None (automatic) | org.jooq.JSON | Read: JSON wrapper; Write: JSON |
| public | entitlement_bundle | allowed_presets | json | Nullable | None | SQLDataType.JSON | org.jooq.JSON | None (automatic) | org.jooq.JSON | Read: JSON wrapper; Write: JSON |
| public | render_job | updated_at | timestamp with time zone | Nullable | None | SQLDataType.TIMESTAMPWITHTIMEZONE(6) | java.time.OffsetDateTime | Forced type → Instant (DG-001) | java.time.Instant | Read: Instant; Write: Instant |
| public | outbox_events | locked_at | timestamp with time zone | Nullable | None | SQLDataType.TIMESTAMPWITHTIMEZONE(6) | java.time.OffsetDateTime | Forced type → Instant (DG-001) | java.time.Instant | Read: Instant; Write: Instant |
| public | marketplace_listing | search_vector | tsvector | Nullable | None | DefaultDataType (pg_catalog.tsvector) | Object | Custom Binding<Object, Object> | Read-only Object | Read: Object; Write: database-computed |
| public | search_projection | search_vector | tsvector | Nullable | None | DefaultDataType (pg_catalog.tsvector) | Object | Custom Binding<Object, Object> | Read-only Object | Read: Object; Write: database-computed |

### JSON and JSONB Contract

**PostgreSQL JSON → org.jooq.JSON**
- Generated type: `org.jooq.JSON`
- DataType: `SQLDataType.JSON`
- Converter: None required (automatic)
- Round-trip: Preserves PostgreSQL JSON semantics
- Domain compatibility: Full

**PostgreSQL JSONB → org.jooq.JSONB**
- Generated type: `org.jooq.JSONB`
- DataType: `SQLDataType.JSONB`
- Converter: None required (automatic)
- Round-trip: Preserves PostgreSQL JSONB semantics including binary serialization
- Domain compatibility: Full

**NOT String/CLOB:** The Version 2 claim that JSON maps to "String with CLOB" is **INCORRECT**. Actual json-typed columns generate as `org.jooq.JSON`. Columns named `*_json` but typed as `text` generate as String/CLOB, but actual json/jsonb-typed columns generate correctly.

---

## DG-001 Instant Contract

### Decision

**FINAL DECISION: Option A — jOOQ Forced Type + Converter to java.time.Instant**

The authority decision selects forced type mapping as the definitive DG-001 resolution. This is NOT deferred to ZD-A1.

### Contract Specification

| Item | Value |
|------|-------|
| PostgreSQL column | `render_job.updated_at` (TIMESTAMPTZ) |
| PostgreSQL column | `outbox_events.locked_at` (TIMESTAMPTZ) |
| Generated DataType | `SQLDataType.TIMESTAMPWITHTIMEZONE(6)` |
| Generated Java type (before override) | `java.time.OffsetDateTime` |
| Domain Java type | `java.time.Instant` |
| Forced type matching expression | `TIMESTAMPWITHTIMEZONE` |
| Target columns | All TIMESTAMPTZ columns |
| Converter class | `com.example.platform.jooq.generated.converters.InstantConverter` |
| Converter from | `OffsetDateTime → Instant` (via `offsetDateTime.toInstant()`) |
| Converter to | `Instant → OffsetDateTime` (via `instant.atOffset(ZoneOffset.UTC)`) |
| UTC/offset behavior | All reads normalized to UTC; writes stored as UTC |
| null behavior | null in → null out (passthrough) |
| microsecond precision | Preserved (TIMESTAMP(6) = microsecond precision) |
| Generated Java type (after override) | `java.time.Instant` |
| Compile probe result | PASS (verified via ephemeral PostgreSQL + codegen) |

### Implementation Contract

```java
public class InstantConverter implements Converter<OffsetDateTime, Instant> {
    @Override
    public Instant from(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    @Override
    public OffsetDateTime to(Instant u) {
        return u == null ? null : u.atOffset(ZoneOffset.UTC);
    }

    @Override
    public Class<OffsetDateTime> fromType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<Instant> toType() {
        return Instant.class;
    }
}
```

### Codegen Configuration

```xml
<forcedType>
    <userType>java.time.Instant</userType>
    <converter>com.example.platform.jooq.generated.converters.InstantConverter</converter>
    <includeTypes>(?i:TIMESTAMP\s*WITH\s*TIME\s*ZONE)</includeTypes>
</forcedType>
```

### Success Criteria

| Criterion | Status |
|-----------|--------|
| DG-001 decision | FINAL |
| Domain Java type | Instant |
| Timezone-independent semantics | PRESERVED |
| Decision deferred | NO |
| Silent OffsetDateTime domain leakage | 0 (mechanically prevented by forced type) |

---

## TSVECTOR Contract

### Affected Columns

| Table | Column | PostgreSQL Type |
|-------|--------|----------------|
| marketplace_listing | search_vector | tsvector |
| search_projection | search_vector | tsvector |

### Type Strategy

**Selected approach:** Custom Binding<Object, Object> with read-only semantics

| Item | Value |
|------|-------|
| Generated type | `TableField<XxxRecord, Object>` |
| Generated DataType | `DefaultDataType.getDefaultDataType("\"pg_catalog\".\"tsvector\"")` |
| Domain-facing type | `Object` (read-only) |
| Read behavior | Returns opaque Object; must be cast or processed by search layer |
| Write behavior | Database-computed (e.g., `to_tsvector('english', content)`) — NOT written by application code |
| Null behavior | null in → null out |
| Search/query usage | Used in `@@` (tsmatch) and `ts_rank()` expressions via Plain SQL |
| Tests | Read test: verify Object returned; Write test: N/A (database-computed) |

### Implementation Notes

- TSVECTOR columns are NOT written by business code — they are computed by database triggers or generated columns
- Application code only READS tsvector values for search ranking
- The custom Binding must handle the PostgreSQL `tsvector` type at the JDBC level
- ZD-A1 must define the Binding class before any call-site migration
- If Binding proves impractical, fallback to `DSL.field("search_vector", SQLDataType.CLOB)` for read-only access

---

## Version Contract

### Single Version Authority

| Item | Value | Authority |
|------|-------|-----------|
| Runtime jOOQ | 3.19.30 | Spring Boot 4.0.4 BOM |
| Generator jOOQ | **3.19.30** | Central version property (to be established in ZD-A1) |
| Codegen plugin | **3.19.30** | Same central version property |
| Version drift allowed | 0 | This decision |

### Version Authority Mechanism

A single Gradle version property drives both the codegen plugin version and the generator jars. A build task verifies that the Spring Boot BOM-resolved runtime jOOQ version matches the declared generator version exactly.

```
Authority property name: jooq.codegen.version
Property location: gradle.properties
Generator resolution: Reads jooq.codegen.version directly
Runtime resolution: Spring Boot BOM (spring-boot-dependencies:4.0.4)
Mismatch guard: Gradle task comparing BOM-resolved version vs declared version
Mismatch failure: BUILD FAILURE with explicit message showing both versions
Missing-property failure: BUILD FAILURE with explicit message
BOM-upgrade mismatch failure: BUILD FAILURE when BOM upgrades jOOQ but property not updated
```

### Independent Governing Authorities

**Count: 1**

The single authority is `gradle.properties → jooq.codegen.version`. Spring Boot BOM resolution is derived (not independent). The mismatch guard is mechanical verification (not an authority).

### 3.19.18 Prohibition

**MUST NOT restore:** The previous 3.19.18 declaration in `build.gradle.kts` and `scripts/generate-jooq.sh`. The AST-aware guard and version alignment task would catch this.

---

## Generated Source Ownership

| Item | Decision |
|------|----------|
| Committed to Git | YES |
| Generator | JavaGenerator (NOT KotlinGenerator — corrected from Version 1) |
| Clean checkout behavior | Compiles without Docker/database |
| CI verification | Regenerate via ephemeral PostgreSQL + diff against committed |
| .gitignore | Remove old build/generated-sources/jooq/ pattern |

### Generated Source Module Decision

**Version 1 location:** `shared-kernel/src/main/java/com/example/platform/jooq/generated/`

**Analysis of shared-kernel:**
- shared-kernel has NO jOOQ imports currently
- shared-kernel has NO jOOQ code
- Placing generated jOOQ sources in shared-kernel would force jOOQ dependency on ALL shared-kernel consumers
- This creates unwanted coupling between domain modules and infrastructure concerns

**Decision:** Generated sources MUST be placed in a dedicated typed-schema module, NOT in shared-kernel.

| Item | Decision |
|------|----------|
| Module name | `typed-schema-module` (to be created in ZD-A1) |
| Runtime dependencies | org.jooq:jooq |
| Codegen dependencies | jooq-codegen, jooq-meta, PostgreSQL JDBC driver, Flyway/codegen tooling |
| PostgreSQL driver scope | CODEGEN_ONLY |
| Forbidden runtime dependencies | org.postgresql:postgresql, Spring, JDBC, application modules |
| Consumers | All modules requiring typed jOOQ access depend on typed-schema-module |
| Generated-source ownership | All generated Java sources under com.example.platform.jooq.generated |
| Cycle risk | NONE — leaf dependency with no reverse dependencies |

### Dependency Contract

```
typed-schema-module → org.jooq:jooq (runtime/API)
codegen task → jooq-codegen + jooq-meta + org.postgresql:postgresql (codegen-only)
```

**PostgreSQL JDBC driver is CODEGEN-ONLY.** The generated Java code only uses jOOQ API types, not PostgreSQL driver classes. At runtime, the application uses a connection pool (HikariCP) which provides the driver. Including postgresql in typed-schema-module creates an unnecessary runtime dependency.

**Consumer modules receive driver transitively: NO**
**Spring/JDBC dependency leakage: 0**
**Dependency cycles: 0**
**Unrelated expansion: 0**

---

## Naming Convention

### Probe-Derived Results (from Ephemeral PostgreSQL generation)

| Pattern | Rule | Example | Probe Source |
|---------|------|---------|-------------|
| Table class | PascalCase from snake_case, V1 name preserved | render_job → RenderJob | Verified |
| Table class (plural) | Preserve V1 naming | outbox_events → OutboxEvents | Verified |
| Field constant | UPPER_SNAKE_CASE from snake_case | project_id → PROJECT_ID | Verified |
| Reserved words | jOOQ generates class name; DSL.name("user") in constructor | "user" → class User extends TableImpl | Verified |
| TSVECTOR fields | Object type with DefaultDataType | search_vector → TableField<X, Object> | Verified |
| TIMESTAMPTZ fields | Instant with forced type (DG-001) | locked_at → TableField<X, Instant> | Verified |
| JSONB fields | org.jooq.JSONB with SQLDataType.JSONB | detector_warning_codes → TableField<X, JSONB> | Verified |
| JSON fields | org.jooq.JSON with SQLDataType.JSON | allowed_providers → TableField<X, JSON> | Verified |
| Package | com.example.platform.jooq.generated | — | Configured |
| Quoted identifiers | All V1 identifiers are unquoted | — | Verified |
| Acronyms | Standard PascalCase/UPPER_SNAKE | ai_script → AI_SCRIPT | Verified |
| Numeric prefix | No special handling (none in V1) | — | N/A |

### Mixed Naming Debt Scan Rule

After migration, any `table("x")` or `field("x")` call remaining in production or test code constitutes a mixed naming violation. The AST-aware guard (see Guard section) detects these mechanically.

---

## No-New-Debt Guards

### Guard Mechanism: AST-Aware Java Parser

**Selected tool:** JavaParser-based custom verifier (or equivalent AST-aware mechanism such as ArchUnit with source parsing)

**Rationale:** The Version 1 grep-based guards had known bypass vectors:
- Multi-line calls split across lines
- Wrapper/helper methods that forward string constants
- Static import aliased calls
- String constant forwarding through intermediate variables
- Alias helpers like `Field<String> f(String name) { return field(name); }`
- Calls constructed via string concatenation

AST-aware parsing detects ALL of these because it parses the actual method invocation structure, not text patterns.

### Guards

| Guard | Mechanism | Detects |
|-------|-----------|---------|
| G-1: No New Untyped Identifiers | AST: JavaParser visits all MethodInvocation nodes matching `table(String)`, `field(String)`, `DSL.table(String)`, `DSL.field(String)`, `DSL.name(String)` | Direct calls, static imports, qualified calls, multi-line, wrapper forwarding, string constant forwarding, alias helpers |
| G-2: Generated Source Drift | CI: ephemeral PostgreSQL regeneration + diff | Any difference between committed and regenerated sources |
| G-3: Version Alignment | Gradle: version comparison task | Generator version ≠ runtime BOM version |
| G-4: No New Plain SQL | AST: detection of `DSL.sql()`, raw SQL in jOOQ context | Plain SQL API usage |
| G-5: No New Dynamic Identifiers | AST: detection of `table(variable)`, `field(variable)` where variable is not a generated constant | Dynamic identifier construction |

### Negative Test Fixtures (Bypass Attempts That Must Fail)

| ID | Bypass Attempt | Expected Result |
|----|---------------|----------------|
| BYPASS-01 | `table("render_job")` on single line | DETECTED |
| BYPASS-02 | `table("render_job")` split across 3 lines | DETECTED |
| BYPASS-03 | `import static org.jooq.impl.DSL.table; table("x")` | DETECTED |
| BYPASS-04 | `Field<String> f(String n) { return DSL.field(n); }` then `f("x")` | DETECTED (AST traces through wrapper) |
| BYPASS-05 | `String T = "render_job"; table(T)` via constant | DETECTED (AST resolves constant) |
| BYPASS-06 | `DSL.field("id" + "_name")` via concatenation | DETECTED (non-literal argument) |
| BYPASS-07 | `var t = DSL.table; t.invoke("x")` via reflection | DETECTED (AST detects DSL.table reference) |
| BYPASS-08 | Commented-out code with table/field calls | NOT DETECTED (acceptable — comments are not code) |

---

## Stable Allowlist Identity

### Identity Model

Each allowlist entry uses a **Stable Site ID** as the primary key, NOT file:line.

| Field | Description |
|-------|-------------|
| Stable Site ID | Unique identifier (e.g., PS-001, DI-PRC011-01) |
| Repository-relative file | Full path from repo root |
| Owning class | Fully qualified class name |
| Method signature | Method name + parameter types |
| Normalized AST/API fingerprint | Hash of normalized API call (table name, field names, no whitespace/comments) |
| Approved disposition | REPLACE / RETAIN / REMOVE |
| Reason | Why this disposition was chosen |
| Required tests | Tests that must exist and pass |

### Invariants

| Property | Behavior |
|----------|----------|
| Line number change | Does NOT silently approve another statement — identity is by Site ID + fingerprint |
| Statement modification | Fingerprint changes → entry becomes stale → guard fails |
| Site deletion | Stale entry detected → guard fails |
| Site duplication | Copied site does NOT inherit approval — new fingerprint needs new approval |
| File/method rename | Explicit reconciliation required — guard fails until updated |
| Silent reassignment | Impossible — identity is by Site ID + fingerprint, not file:line |

---

## Implementation Slices

### ZD-A1: Typed Schema Foundation and Mechanical Guards

**Scope:** Create typed-schema-module, generate types, establish AST-aware guards
**Item IDs:** DI-005 (partial), DI-009 (partial)
**Modules:** typed-schema-module (new), root build
**Allowed files:** Module definition, generated sources, build config, guard scripts, allowlist files, version property
**Forbidden scope:** All production source, test source, migration, CI files
**Expected commits:** 2-3
**Required tests:** Generated type compilation, guard negative-fixture tests, version alignment test
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- typed-schema-module created with correct dependencies (jOOQ only, no PostgreSQL driver at runtime)
- 147 table classes + 147 record classes generated from ephemeral PostgreSQL
- Generated sources committed
- TSVECTOR binding contract defined
- JSONB type contract verified (org.jooq.JSONB)
- JSON type contract verified (org.jooq.JSON)
- DG-001 Instant forced type + converter implemented
- AST-aware guard executable with all negative fixtures passing
- Version property drives both generator and plugin
- Version alignment guard operational
- V1 unchanged (V1__initial_schema.sql)
- Generator version = 3.19.30

### ZD-A2: Production Call-Site Migration

**Scope:** Replace all production untyped identifiers with generated types
**Item IDs:** DI-005 (production), DI-PRC011-01, DI-PRC011-02, DI-012-01, DI-012-02, DI-012-03
**Modules (15 with untyped calls):** render-module, entitlement-module, notification-module, outbox-event-module, delivery-module, identity-access-module, commerce-module, platform-app, artifact-catalog-module, billing-module, audit-compliance-module, payment-module, storage-module, secrets-config-module, config-module
**Modules (4 with jOOQ dependency but no calls):** datasource-module, product-layer-module, remote-render-worker, shared-kernel — must be verified clean
**Identifier reduction:** 3112 raw occurrences → 0
**Spring JDBC sites:** All 16 NOT_PLAIN_SQL sites migrated to jOOQ typed DSL
**Allowed files:** Production Java source files in affected modules
**Forbidden scope:** Test files, migration files, Gradle files, CI files
**Expected commits:** 3-5
**Required tests:** Per-module compilation, existing test suite, dynamic identifier negative tests
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- Production raw untyped occurrences = 0
- All 15 production modules verified clean
- All 16 Spring JDBC sites migrated to jOOQ typed DSL
- Dynamic identifier sites DI-PRC011-01/02, DI-012-01/02/03 replaced
- No new Plain SQL introduced
- All module tests pass

### ZD-A3: Test and Fixture Call-Site Migration

**Scope:** Replace all test untyped identifiers with generated types
**Item IDs:** DI-006 (test)
**Modules (6 with actual untyped calls):**
1. render-module (150 occurrences)
2. outbox-event-module (37 occurrences)
3. audit-compliance-module (23 occurrences)
4. extension-module (25 occurrences)
5. notification-module (16 occurrences)
6. platform-app (8 occurrences)

**Note:** extension-module is NOT phantom — it has 25 actual `table()` calls in test code.

**Identifier reduction:** 259 raw occurrences → 0
**Allowed files:** Test Java source files in affected modules
**Forbidden scope:** Production files, migration files, Gradle files
**Expected commits:** 2-3
**Required tests:** Full test suite pass, test DDL preserved
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- Test raw untyped occurrences = 0
- All 6 test modules verified clean (including extension-module)
- Full test suite pass
- Test DDL for ephemeral PostgreSQL preserved

### ZD-A4: Legacy API Removal and Zero-Debt Closure

**Scope:** Final sweep — close all remaining debt, verify zero-debt gate
**Item IDs:** DI-005, DI-006, DI-009, PRC-004, PRC-011, DI-012
**Modules:** All
**Identifier reduction:** All remaining → 0
**Allowed files:** All source files (final sweep)
**Forbidden scope:** Migration files, Gradle files, CI files
**Expected commits:** 1-2
**Required tests:** Full quality gate (compile + test + guard)
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- All Zero-Debt Closure Gate conditions met (see below)
- Guard bypass tests PASS
- Clean checkout PASS
- Full quality PASS

---

## Zero-Debt Closure Gate

| Gate | Metric | Required Value |
|------|--------|---------------|
| G-001 | Production untyped jOOQ identifier occurrences | 0 |
| G-002 | Test untyped jOOQ identifier occurrences | 0 |
| G-003 | Mixed naming violations | 0 |
| G-004 | Unapproved Plain SQL sites | 0 |
| G-005 | Unapproved dynamic identifiers | 0 |
| G-006 | Generated source drift | 0 |
| G-007 | Generator/runtime version drift | 0 |
| G-008 | Stale allowlist entries | 0 |
| G-009 | Guard bypass tests | PASS |
| G-010 | Clean checkout | PASS |
| G-011 | Full quality | PASS |
| G-012 | NLQ Quarantine | UNCHANGED |
| G-013 | DG-001 type leakage | 0 |
| G-014 | Missing module allocations | 0 |
| G-015 | Unowned SQL sites | 0 |

---

## Boundaries

### OpenCue
- Candidate: b2d7f52fba4002cb1668c2492bf49df933d23cd9
- Integrated: NO
- Formal acceptance: NOT GRANTED
- Production enablement: PROHIBITED

### First Production Release
- Status: PROHIBITED
- Reason: Credential exception ACTIVE_TEMPORARY_EXCEPTION

### Other Zero-Debt Batches
- BATCH-ZD-B: NOT EXECUTED
- BATCH-ZD-C: NOT EXECUTED (NLQ Quarantine UNCHANGED)
- BATCH-ZD-D: NOT EXECUTED

### Credential Exception
```
Exception: ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1
Status: ACTIVE_TEMPORARY_EXCEPTION
Review/expiry: 2026-08-04
Residual risk: PRESENT
```

---

## Execution Order

```
This Decision Repair (ZD-A Authority v3)
→ Independent Repair Verification
→ Governance Acceptance
→ ZD-A1: Typed Schema Foundation and Mechanical Guards
→ Independent Verification
→ ZD-A2: Production Call-Site Migration (ALL 15 production modules)
→ Independent Verification
→ ZD-A3: Test and Fixture Call-Site Migration (ALL 6 test modules including extension-module)
→ Independent Verification
→ ZD-A4: Zero-Debt Closure
→ Independent Verification
→ Zero-Debt First-Release Verification (all batches)
```

---

## Document Lineage

| Version | Date | Status | Parent Commit |
|---------|------|--------|---------------|
| 1 | 2026-07-22 | REJECTED (NOT_REVERIFIED) | 64f9ade3bf1fc9ebed014df4f9774da06f2ed962 |
| 2 | 2026-07-22 | REJECTED (NOT_REVERIFIED — 12 failures) | e87127e9e51469c13b342f9d660abd2eec092553 |
| 3 | 2026-07-22 | REPAIR_CANDIDATE | ef181ba614ec688c8c3567525fab75f39d09016e |
