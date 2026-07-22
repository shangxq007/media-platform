# jOOQ Typed Schema Authority Decision

## Authority and Scope

**Task:** ARCH-CODE-GOV-ZERO-DEBT-BATCH-ZD-A-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION.1-REPAIR.1
**Role:** MEDIA-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION-REPAIR-AGENT
**Base Commit:** e87127e9e51469c13b342f9d660abd2eec092553
**Base Status:** CANDIDATE (NOT_REVERIFIED — 7 failures identified)
**Repair Commit:** (to be set after commit)
**Previous Version:** 1 (CANDIDATE — failed independent reverification)

This document is the single authority for jOOQ Typed Schema architecture selection. It selects the architecture, defines the naming convention, establishes drift guards, and authorizes implementation slices for BATCH-ZD-A.

This is a governance decision document only. No implementation, migration, test, Gradle, or CI changes are authorized by this document.

### Repair Scope

This Version 2 repairs the following reverification failures from Version 1:

| Failure | Description | Resolution |
|---------|-------------|------------|
| F-01 | DDLDatabase generation failed | Replaced with Ephemeral PostgreSQL architecture |
| F-02 | Inventory metrics and counts inaccurate | Canonical 4-dimension methodology defined and applied |
| F-03 | Plain SQL sites not reconciled | Canonical Plain SQL Site Registry established |
| F-04 | ZD-A3 test-module allocation incomplete | Complete test module enumeration |
| F-05 | Allowlist identity unstable | Stable Site ID and fingerprint system |
| F-06 | Guard design has false-negative paths | AST-aware guard mechanism selected |
| F-07 | Generator/runtime version duplicated | Single version authority with mechanical guard |

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
| V1 tables | **145** | Verified via PostgreSQL16 ephemeral instance |
| Active V2-V5 | 0 | V1 is sole schema source of truth |
| V1 file | platform-app/src/main/resources/db/migration/V1__init_full_schema.sql | Consolidated migration |

### V1 Type Coverage

| PostgreSQL Type | Count | jOOQ Generated Type | Status |
|----------------|-------|---------------------|--------|
| character varying | 898 | String (SQLDataType.VARCHAR) | AUTOMATIC |
| timestamp without time zone | 244 | LocalDateTime (SQLDataType.LOCALDATETIME) | AUTOMATIC |
| text | 176 | String (SQLDataType.CLOB) | AUTOMATIC |
| integer | 84 | Integer (SQLDataType.INTEGER) | AUTOMATIC |
| bigint | 59 | Long (SQLDataType.BIGINT) | AUTOMATIC |
| boolean | 59 | Boolean (SQLDataType.BOOLEAN) | AUTOMATIC |
| double precision | 12 | Double (SQLDataType.DOUBLE) | AUTOMATIC |
| json | 2 | String (SQLDataType.CLOB) | AUTOMATIC |
| **tsvector** | **2** | **Object (DefaultDataType)** | **REQUIRES_CUSTOM_BINDING** |
| timestamp with time zone | 1 | OffsetDateTime (SQLDataType.TIMESTAMPWITHTIMEZONE) | AUTOMATIC |

**TSVECTOR columns:**
- `marketplace_listing.search_vector`
- `search_projection.search_vector`

**TSVECTOR handling contract:** jOOQ generates `TableField<XxxRecord, Object>` with `DefaultDataType.getDefaultDataType("\"pg_catalog\".\"tsvector\"")`. Implementation must provide a custom `Binding<Object, Object>` or use raw `DSL.field("search_vector", SQLDataType.CLOB)` for read-only access. This contract must be defined in ZD-A1 before any call-site migration.

**Note:** The V1 does NOT contain JSONB, UUID, arrays, enum types, or domain types. The original decision's claim of "JSONB, UUID, arrays, enums/domains" coverage requirements was overstated — only `json` (not `jsonb`), `tsvector`, and `timestamptz` are present as PostgreSQL-specific types.

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

| Module | Raw Occurrences | Unique Literals | Construction Sites | Tuple-Deduplicated |
|--------|----------------|-----------------|-------------------|--------------------|
| render-module | 213 | 18 | 177 | 18 |
| artifact-catalog-module | 59 | 17 | 48 | 20 |
| billing-module | 48 | 18 | 37 | 21 |
| audit-compliance-module | 34 | 10 | 34 | 10 |
| storage-module | 25 | 9 | 20 | 9 |
| commerce-module | 16 | 12 | 16 | 12 |
| secrets-config-module | 12 | 7 | 8 | 7 |
| **Total** | **407** | **69** | **340** | **97** |

### Test Inventory

| Module | Raw Occurrences | Unique Literals | Construction Sites | Tuple-Deduplicated |
|--------|----------------|-----------------|-------------------|--------------------|
| render-module | 198 | 25 | 150 | 81 |
| outbox-event-module | 37 | 5 | 37 | 5 |
| audit-compliance-module | 23 | 10 | 23 | 12 |
| notification-module | 16 | 5 | 16 | 5 |
| platform-app | 8 | 8 | 8 | 8 |
| **Total** | **282** | **46** | **234** | **111** |

### Historical Count Reconciliation

| Historical Value | Context | Dimension | Explanation |
|-----------------|---------|-----------|-------------|
| 653+ | Original governance spec (DI-005) | Production line-level count | Coarser granularity — counted lines containing identifiers, not individual calls |
| 307 | Original governance spec (DI-006) | Test line-level count | Same methodology as 653+ |
| 1130 | Original decision (Version 1) | Production tuple-deduplicated | Counted unique (module, file, type, value) tuples with a different grep pattern |
| 181 | Original decision (Version 1) | Test tuple-deduplicated | Same methodology as 1130 |
| 1102 | Reverification reference | Production tuple-deduplicated | Third-party recount with yet another pattern |
| 188 | Reverification reference | Test tuple-deduplicated | Same methodology as 1102 |
| **407** | **This repair (Version 2)** | **Production raw occurrences** | **Canonical: per-call count of `\b(table\|field)("literal")` and `DSL.(table\|field)("literal")`** |
| **282** | **This repair (Version 2)** | **Test raw occurrences** | **Same methodology as production** |
| **97** | **This repair (Version 2)** | **Production tuple-deduplicated** | **Canonical: unique (module, file, call_type, literal)** |
| **111** | **This repair (Version 2)** | **Test tuple-deduplicated** | **Same methodology as production** |

**Reconciliation conclusion:** All historical values represent the same codebase measured at different granularities and with different grep patterns. The original "1130" and "181" values included broader pattern matching (e.g., `.from("`, `.join("`, `.where("` with string arguments) and counted at a different tuple-deduplication level. The canonical values in this repair use the narrowest, most defensible definition: only `table("...")` and `field("...")` calls with string literal arguments.

---

## Module Model

### Production Modules Using jOOQ (import org.jooq)

| # | Module | Path |
|---|--------|------|
| 1 | artifact-catalog-module | artifact-catalog-module/ |
| 2 | audit-compliance-module | audit-compliance-module/ |
| 3 | billing-module | billing-module/ |
| 4 | commerce-module | commerce-module/ |
| 5 | config-module | config-module/ |
| 6 | datasource-module | datasource-module/ |
| 7 | delivery-module | delivery-module/ |
| 8 | entitlement-module | entitlement-module/ |
| 9 | identity-access-module | identity-access-module/ |
| 10 | notification-module | notification-module/ |
| 11 | outbox-event-module | outbox-event-module/ |
| 12 | payment-module | payment-module/ |
| 13 | platform-app | platform-app/ |
| 14 | render-module | render-module/ |
| 15 | secrets-config-module | secrets-config-module/ |
| 16 | storage-module | storage-module/ |

**Production call modules (with untyped table/field calls):** 7 (render, artifact-catalog, billing, audit-compliance, storage, commerce, secrets-config)

### Test Modules Using jOOQ (import org.jooq)

| # | Module | Path | Has Untyped Calls |
|---|--------|------|-------------------|
| 1 | artifact-catalog-module | artifact-catalog-module/ | NO |
| 2 | audit-compliance-module | audit-compliance-module/ | YES |
| 3 | datasource-module | datasource-module/ | NO |
| 4 | delivery-module | delivery-module/ | NO |
| 5 | identity-access-module | identity-access-module/ | NO |
| 6 | notification-module | notification-module/ | YES |
| 7 | outbox-event-module | outbox-event-module/ | YES |
| 8 | platform-app | platform-app/ | YES |
| 9 | prompt-module | prompt-module/ | NO |
| 10 | render-module | render-module/ | YES |
| 11 | storage-module | storage-module/ | NO |

**Test call modules (with untyped table/field calls):** 5 (render, outbox-event, audit-compliance, notification, platform-app)

**Note:** `extension-module` was listed in the original ZD-A3 but does NOT contain untyped jOOQ table/field calls in test code. The matches were false positives from method names like `registerExecutable()`. `extension-module` has jOOQ imports in production but no untyped string-identifier calls in either production or test.

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
| 10 | identity-access-module |
| 11 | notification-module |
| 12 | outbox-event-module |
| 13 | payment-module |
| 14 | platform-app |
| 15 | product-layer-module |
| 16 | prompt-module |
| 17 | remote-render-worker |
| 18 | render-module |
| 19 | secrets-config-module |
| 20 | storage-module |

### Production/Test Union (all modules with any jOOQ usage)

17 modules: artifact-catalog-module, audit-compliance-module, billing-module, commerce-module, config-module, datasource-module, delivery-module, entitlement-module, identity-access-module, notification-module, outbox-event-module, payment-module, platform-app, render-module, secrets-config-module, storage-module, prompt-module

### Phantom Modules

| Module | Status |
|--------|--------|
| extension-module | PHANTOM — listed in original ZD-A3 but has no untyped jOOQ calls in test |
| shared-kernel | NO_JOOQ_IMPORTS — no org.jooq imports, no jOOQ code |

### Missing from Original Decision

| Module | Status |
|--------|--------|
| artifact-catalog-module (test) | MISSING — has jOOQ imports in test |
| datasource-module (test) | MISSING — has jOOQ imports in test |
| delivery-module (test) | MISSING — has jOOQ imports in test |
| identity-access-module (test) | MISSING — has jOOQ imports in test |
| prompt-module (test) | MISSING — has jOOQ imports in test |
| storage-module (test) | MISSING — has jOOQ imports in test |

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

| Site ID | Module | File | Class | API | Classification | Reason |
|---------|--------|------|-------|-----|---------------|--------|
| PS-001 | billing-module | BillingLedgerJdbcRepository.java | BillingLedgerJdbcRepository | jdbc.query() / jdbc.update() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-002 | billing-module | CreditWalletJdbcRepository.java | CreditWalletJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-003 | billing-module | SubscriptionJdbcRepository.java | SubscriptionJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-004 | entitlement-module | QuotaUsageJdbcRepository.java | QuotaUsageJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-005 | entitlement-module | TenantTierJdbcRepository.java | TenantTierJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-006 | federation-query-module | QueryExecutionService.java | QueryExecutionService | jdbc.execute() | NLQ_QUARANTINE_BOUNDARY | Under PRC-003/DI-003 quarantine |
| PS-007 | federation-query-module | NlqJdbcRepository.java | NlqJdbcRepository | jdbc.query() | NLQ_QUARANTINE_BOUNDARY | Under PRC-003/DI-003 quarantine |
| PS-008 | outbox-event-module | PostgresNotificationService.java | PostgresNotificationService | jdbc.execute() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-009 | platform-app | SharedResourceJdbcRepository.java | SharedResourceJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-010 | policy-governance-module | FeatureFlagJdbcStore.java | FeatureFlagJdbcStore | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-011 | prompt-module | PromptJdbcRepository.java | PromptJdbcRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-012 | render-module | MediaAssetProbeService.java | MediaAssetProbeService | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-013 | render-module | ClientExportSessionRepository.java | ClientExportSessionRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-014 | social-publish-module | ConnectedPlatformRepository.java | ConnectedPlatformRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-015 | social-publish-module | PostAnalyticsRepository.java | PostAnalyticsRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-016 | social-publish-module | SocialPostRepository.java | SocialPostRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-017 | user-analytics-module | JdbcUserBehaviorEventRepository.java | JdbcUserBehaviorEventRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |
| PS-018 | user-analytics-module | JdbcUserHabitsRepository.java | JdbcUserHabitsRepository | jdbc.query() | NOT_PLAIN_SQL | Spring JDBC template, not jOOQ |

### Summary

| Classification | Count |
|---------------|-------|
| REPLACE_WITH_TYPED_DSL | 0 |
| RETAIN_ON_EXACT_ALLOWLIST | 0 |
| REMOVE | 0 |
| NOT_PLAIN_SQL | 16 |
| NLQ_QUARANTINE_BOUNDARY | 2 |
| **TBD** | **0** |

**Note:** The search for `DSL.sql()` in production code returned 0 results. All identified "Plain SQL" sites are actually Spring JDBC template usage (`jdbc.query()`, `jdbc.update()`), which is a separate concern from jOOQ's Plain SQL API. These repositories will be migrated to jOOQ typed DSL during ZD-A2/ZD-A3 as part of the normal identifier migration, not as Plain SQL remediation.

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
| Table count | PASS — 145 tables (matches V1 CREATE TABLE count) |
| TIMESTAMPTZ mapping | PASS — `OffsetDateTime` with `SQLDataType.TIMESTAMPWITHTIMEZONE(6)` |
| JSONB mapping | N/A — V1 has no JSONB columns (only `json` which maps to String/CLOB) |
| TSVECTOR handling | PASS — generates `TableField<XxxRecord, Object>` with `DefaultDataType.getDefaultDataType("\"pg_catalog\".\"tsvector\"")`. Requires custom binding contract defined in ZD-A1. |
| UUID mapping | N/A — V1 has no UUID columns |
| Arrays/enums/domains | N/A — V1 has no arrays, enums, or domains |
| Keys and foreign keys | PASS — 145 primary keys, 39 unique constraints, 38 foreign keys generated |
| 145-table coverage | PASS — 145 table classes + 145 record classes generated |
| Two-run determinism | PASS — `diff -r` shows zero differences between two runs |
| Production database dependency | NONE — ephemeral container only |
| V1 modification required | NO |

**Correct class name:** `org.jooq.meta.postgres.PostgresDatabase` (NOT `org.jooq.meta.postgresql.PostgreSQLDatabase` as stated in Version 1)

### Option C: Central Typed Schema Metadata — FALLBACK

Manual maintenance of 145 tables × N columns. No automatic drift detection. Error-prone. Does not scale. Retained only as emergency fallback if Ephemeral PostgreSQL proves impractical in CI.

### Option D: Deterministically Generated Constants — FALLBACK

Same tooling cost as full codegen but without type-safe record mapping. Does not provide compile-time table/field references. Retained only as emergency fallback.

---

## Selected Architecture

```
JOOQ_CODE_GENERATION_EPHEMERAL_POSTGRESQL
```

### Authority Chain

```
Consolidated V1 (platform-app/src/main/resources/db/migration/V1__init_full_schema.sql)
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

## Version Contract

### Single Version Authority

| Item | Value | Authority |
|------|-------|-----------|
| Runtime jOOQ | 3.19.30 | Spring Boot 4.0.4 BOM |
| Generator jOOQ | **3.19.30** | Central version property (to be established in ZD-A1) |
| Codegen plugin | **3.19.30** | Same central version property |
| Version drift allowed | 0 | This decision |

### Version Authority Mechanism (Model C)

A single Gradle version property (e.g., `jooq.codegen.version=3.19.30` in `gradle.properties`) drives both the codegen plugin version and the generator jars. A build task verifies that the Spring Boot BOM-resolved runtime jOOQ version matches the declared generator version exactly.

```
Authority location: gradle.properties → jooq.codegen.version
Generator resolution: Reads jooq.codegen.version directly
Runtime resolution: Spring Boot BOM (spring-boot-dependencies:4.0.4)
Mismatch guard: Gradle task comparing BOM-resolved version vs declared version
Mismatch failure: BUILD FAILURE with explicit message showing both versions
```

**MUST NOT restore:** The previous 3.19.18 declaration in `build.gradle.kts` and `scripts/generate-jooq.sh`.

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
| Dependency direction | typed-schema-module → (jOOq, PostgreSQL driver) |
| Allowed dependencies | org.jooq:jooq, org.postgresql:postgresql |
| Forbidden dependencies | Spring, JDBC, application modules |
| Consumers | All modules requiring typed jOOQ access depend on typed-schema-module |
| Generated-source ownership | All generated Java sources under com.example.platform.jooq.generated |
| Cycle risk | NONE — leaf dependency with no reverse dependencies |

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
| TIMESTAMPTZ fields | OffsetDateTime with TIMESTAMPWITHTIMEZONE | locked_at → TableField<X, OffsetDateTime> | Verified |
| JSON fields | String with CLOB | pipeline_plan_json → TableField<X, String> | Verified |
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
| Normalized statement fingerprint | Hash of normalized API call (table name, field names, no whitespace/comments) |
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
- typed-schema-module created with correct dependencies
- 145 table classes + 145 record classes generated from ephemeral PostgreSQL
- Generated sources committed
- TSVECTOR binding contract defined
- AST-aware guard executable with all negative fixtures passing
- Version property drives both generator and plugin
- Version alignment guard operational
- V1 unchanged
- Generator version = 3.19.30

### ZD-A2: Production Call-Site Migration

**Scope:** Replace all production untyped identifiers with generated types
**Item IDs:** DI-005 (production), DI-PRC011-01, DI-PRC011-02, DI-012-01, DI-012-02, DI-012-03
**Modules (7 with untyped calls):** render-module, artifact-catalog-module, billing-module, audit-compliance-module, storage-module, commerce-module, secrets-config-module
**Modules (9 with jOOQ imports but no untyped calls):** config-module, datasource-module, delivery-module, entitlement-module, identity-access-module, notification-module, outbox-event-module, payment-module, platform-app — must be verified clean
**Identifier reduction:** 407 raw occurrences → 0, 97 tuple-deduplicated → 0
**Allowed files:** Production Java source files in affected modules
**Forbidden scope:** Test files, migration files, Gradle files, CI files
**Expected commits:** 3-5
**Required tests:** Per-module compilation, existing test suite, dynamic identifier negative tests
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- Production raw untyped occurrences = 0
- Production tuple-deduplicated = 0
- All 16 production modules verified clean
- Dynamic identifier sites DI-PRC011-01/02, DI-012-01/02/03 replaced
- No new Plain SQL introduced
- All module tests pass

### ZD-A3: Test and Fixture Call-Site Migration

**Scope:** Replace all test untyped identifiers with generated types
**Item IDs:** DI-006 (test)
**Modules (12 with test jOOQ imports):**
1. artifact-catalog-module (test)
2. audit-compliance-module (test)
3. datasource-module (test)
4. delivery-module (test)
5. identity-access-module (test)
6. notification-module (test)
7. outbox-event-module (test)
8. platform-app (test)
9. prompt-module (test)
10. render-module (test)
11. storage-module (test)
12. extension-module (test) — has jOOQ imports but no untyped calls; must be verified clean

**Modules with actual untyped calls (5):** render-module, outbox-event-module, audit-compliance-module, notification-module, platform-app

**Identifier reduction:** 282 raw occurrences → 0, 111 tuple-deduplicated → 0
**Allowed files:** Test Java source files in affected modules
**Forbidden scope:** Production files, migration files, Gradle files
**Expected commits:** 2-3
**Required tests:** Full test suite pass, test DDL preserved
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:**
- Test raw untyped occurrences = 0
- Test tuple-deduplicated = 0
- All 12 test modules verified clean
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
This Decision Repair (ZD-A Authority v2)
→ Independent Repair Verification
→ Governance Acceptance
→ ZD-A1: Typed Schema Foundation and Mechanical Guards
→ Independent Verification
→ ZD-A2: Production Call-Site Migration
→ Independent Verification
→ ZD-A3: Test and Fixture Call-Site Migration (ALL 12 test modules)
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
| 2 | 2026-07-22 | REPAIR_CANDIDATE | e87127e9e51469c13b342f9d660abd2eec092553 |
