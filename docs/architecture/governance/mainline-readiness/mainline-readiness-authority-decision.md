# Mainline Readiness Batch Authority and Retirement Decision

## Authority and Scope

**Task:** ARCH-CODE-GOV-MAINLINE-READINESS-BATCH-AUTHORITY-AND-RETIREMENT-DECISION.1
**Role:** MEDIA-MAINLINE-READINESS-GOVERNANCE-DECISION-AGENT
**Base Commit:** aac6fbad7914505d96b8564065ba7c6428f400bb
**Base Status:** GREENFIELD_QUALITY_BASELINE_CLOSURE_GOVERNANCE_ACCEPTED

This document is the single authority for mainline debt classification, remediation batch authorization, and retirement decisions. It converts accepted governance inventory inputs into actionable dispositions.

This is a governance decision document only. No implementation, migration, test, Gradle, or CI changes are authorized by this document.

---

## Accepted Inventory Inputs

### Input 1: jOOQ Inventory

**Source:** ARCH-CODE-GOV-MAINLINE-READINESS-JOOQ-HARD-CODING-CODEGEN-INVENTORY.1

| Metric | Value |
|--------|-------|
| jOOQ codegen tasks | 0 |
| Generated jOOQ source | 0 |
| Clean checkout compilation | PASS |
| Production string DSL identifiers | 653+ |
| Test string identifiers | 307 |
| Confirmed Mainline blockers | 0 |
| PRC findings | PRC-001 through PRC-012 |

### Input 2: Stale / Compatibility Inventory

**Source:** ARCH-CODE-GOV-MAINLINE-READINESS-STALE-COMPATIBILITY-AND-BROKEN-LINK-INVENTORY.1

| Metric | Value |
|--------|-------|
| Final reconciliation status | STALE_COMPATIBILITY_AND_BROKEN_LINK_INVENTORY_EVIDENCE_FULLY_RECONCILED |
| Raw occurrences | 40 |
| Deduplicated references | 25 |
| Unique root causes | 16 |
| Unique provisional decision items | 37 |
| Current confirmed Mainline blockers | 0 |
| Runtime | NOT ASSESSED |

### Input 3: Automated Broken-Link Baseline

**Source:** docs/architecture/governance/automated-guards/broken-link-debt-baseline.json

| Metric | Value |
|--------|-------|
| unique_broken_identity_count | 126 |
| scan baseline commit | 1af5ceb8a682cb946dd04a1891a4bf68125794a9 |

**Important:** This metric is a docs/ automated scan historical baseline. It is NOT the current repository mainline debt total. It must NOT be directly added to or compared with the curated raw occurrence count of 40.

---

## Canonical Metrics

| Metric | Value |
|--------|-------|
| Total unique decision items | 37 |
| Classified | 37 |
| Unclassified | 0 |
| Duplicate primary classifications | 0 |
| PRC findings decided | 12 (PRC-001 through PRC-012) |
| Confirmed Mainline blockers | 0 |
| Items requiring pre-Mainline remediation | 4 |
| Items with deprecation deadlines | 7 |
| Items quarantined | 1 |
| Items kept with conditions | 25 |

---

## 37-Item Final Decision Matrix

### Decision Item Registry

| # | Decision ID | Original ID | Root-Cause ID | PRC ID | Module | Primary Disposition | Secondary Tags |
|---|-------------|-------------|---------------|--------|--------|---------------------|----------------|
| 1 | DI-001 | SC-001 | RC-001 | PRC-001 | render-module | REMOVE_BEFORE_MAINLINE | TEST_HYGIENE |
| 2 | DI-002 | SC-002 | RC-002 | PRC-002 | storage-module | REMOVE_BEFORE_MAINLINE | TEST_HYGIENE |
| 3 | DI-003 | SC-003 | RC-003 | PRC-003 | federation-query-module | QUARANTINE | SECURITY_BOUNDARY |
| 4 | DI-004 | SC-004 | RC-004 | PRC-004 | render-module | KEEP | JOOQ |
| 5 | DI-005 | SC-005 | RC-005 | PRC-005 | all modules | DEPRECATE_WITH_DEADLINE | JOOQ |
| 6 | DI-006 | SC-006 | RC-006 | PRC-006 | all test modules | DEPRECATE_WITH_DEADLINE | JOOQ, TEST_HYGIENE |
| 7 | DI-007 | SC-007 | RC-007 | PRC-007 | build config | REMOVE_BEFORE_MAINLINE | BUILD_HYGIENE |
| 8 | DI-008 | SC-008 | RC-008 | PRC-008 | build config | REMOVE_BEFORE_MAINLINE | BUILD_HYGIENE |
| 9 | DI-009 | SC-009 | RC-009 | PRC-009 | identity-access-module | DEPRECATE_WITH_DEADLINE | JOOQ |
| 10 | DI-010 | SC-010 | RC-010 | PRC-010 | test modules | KEEP | TEST_HYGIENE |
| 11 | DI-011 | SC-011 | RC-011 | PRC-011 | render-module | KEEP | JOOQ |
| 12 | DI-012 | SC-012 | RC-012 | PRC-012 | render-module | KEEP | JOOQ |
| 13 | DI-013 | SC-013 | RC-013 | — | identity-access-module | DEPRECATE_WITH_DEADLINE | COMPATIBILITY |
| 14 | DI-014 | SC-014 | RC-014 | — | render-module | DEPRECATE_WITH_DEADLINE | COMPATIBILITY |
| 15 | DI-015 | SC-015 | RC-015 | — | render-module/font | KEEP | — |
| 16 | DI-016 | SC-016 | RC-016 | — | render-module/font | KEEP | — |
| 17 | DI-017 | SC-017 | RC-017 | — | platform-app/security | DEPRECATE_WITH_DEADLINE | SECURITY_BOUNDARY |
| 18 | DI-018 | SC-018 | RC-018 | — | compatibility-migration-module | DEPRECATE_WITH_DEADLINE | COMPATIBILITY |
| 19 | DI-019 | SC-019 | RC-019 | — | ai-module | KEEP | — |
| 20 | DI-020 | SC-020 | RC-020 | — | ai-module/video | KEEP | — |
| 21 | DI-021 | SC-021 | RC-021 | — | ai-module/video | KEEP | — |
| 22 | DI-022 | SC-022 | RC-022 | — | ai-module/video | KEEP | — |
| 23 | DI-023 | SC-023 | RC-023 | — | ai-module/video | KEEP | — |
| 24 | DI-024 | SC-024 | RC-024 | — | ai-module/video | KEEP | — |
| 25 | DI-025 | SC-025 | RC-025 | — | audit-compliance-module | KEEP | — |
| 26 | DI-026 | SC-026 | RC-026 | — | billing-module | KEEP | — |
| 27 | DI-027 | SC-027 | RC-027 | — | cloud-resource-module | KEEP | — |
| 28 | DI-028 | SC-028 | RC-028 | — | commerce-module | KEEP | — |
| 29 | DI-029 | SC-029 | RC-029 | — | datasource-module | KEEP | — |
| 30 | DI-030 | SC-030 | RC-030 | — | payment-module | KEEP | — |
| 31 | DI-031 | SC-031 | RC-031 | — | payment-module | KEEP | — |
| 32 | DI-032 | SC-032 | RC-032 | — | render-module | KEEP | — |
| 33 | DI-033 | SC-033 | RC-033 | — | render-module/font | KEEP | — |
| 34 | DI-034 | SC-034 | RC-034 | — | render-module/font | KEEP | — |
| 35 | DI-035 | SC-035 | RC-035 | — | render-module/font | KEEP | — |
| 36 | DI-036 | SC-036 | RC-036 | — | sandbox-runtime-module | KEEP | — |
| 37 | DI-037 | SC-037 | RC-037 | — | social-publish-module | KEEP | — |

### Disposition Summary

| Disposition | Count | IDs |
|-------------|-------|-----|
| BLOCKER | 0 | — |
| REMOVE_BEFORE_MAINLINE | 4 | DI-001, DI-002, DI-007, DI-008 |
| DEPRECATE_WITH_DEADLINE | 7 | DI-005, DI-006, DI-009, DI-013, DI-014, DI-017, DI-018 |
| QUARANTINE | 1 | DI-003 |
| KEEP | 25 | DI-004, DI-010, DI-011, DI-012, DI-015, DI-016, DI-019–DI-037 |

---

## Detailed Decision Rationales

### DI-001: RenderTestSchemaFixture timestamp vs TIMESTAMPTZ (PRC-001)

**Disposition:** REMOVE_BEFORE_MAINLINE

**Facts:**
- RenderTestSchemaFixture uses `timestamp` for all columns
- V1 production schema uses `TIMESTAMPTZ` for `outbox_events.locked_at`
- Test fixture diverges from production schema semantics
- Production DG-001 has PASSED (TIMESTAMPTZ alignment confirmed)

**Rationale:** The test fixture does not match production schema types. This must be aligned before Mainline verification to ensure test fidelity. Not a runtime blocker since production schema is authoritative.

**Owner:** test-infrastructure
**Deadline:** Before Mainline verification
**Verification:** Test fixture `locked_at` column uses `TIMESTAMPTZ` matching V1 schema

---

### DI-002: StorageTestSchemaFixture Missing Production Columns (PRC-002)

**Disposition:** REMOVE_BEFORE_MAINLINE

**Facts:**
- StorageTestSchemaFixture defines only `artifact` table with minimal columns
- V1 production schema has additional columns (e.g., `tenant_id`, `checksum`, `size_bytes`)
- Tests using this fixture cannot exercise production column semantics

**Rationale:** Test fixture is incomplete relative to production schema. Must be aligned before Mainline verification.

**Owner:** test-infrastructure
**Deadline:** Before Mainline verification
**Verification:** StorageTestSchemaFixture artifact table matches V1 schema columns

---

### DI-003: NLQ QueryExecutionService Security Boundary (PRC-003)

**Disposition:** QUARANTINE

**Facts:**
- SqlSafetyValidator: present
- User/AI-generated SQL: accepted
- SELECT/WITH restriction: present
- DDL/DML prohibition: present
- Multi-statement prohibition: present
- LIMIT / timeout / max rows: present
- Result redaction: present
- Read-only enforcement: NOT ESTABLISHED
- Schema/table allowlist: NOT ESTABLISHED at database level
- No read-only transaction boundary confirmed
- No read-only database principal confirmed

**Rationale:** The NLQ feature has significant validator-level protections but lacks database-level enforcement (read-only principal, read-only transaction, schema allowlist). The validator is regex-based and can be bypassed. This is not a Mainline blocker because the feature is not on the critical path, but it must be quarantined until proper database-level controls are established.

**Quarantine boundary:** NLQ feature must not be exposed to untrusted users
**Forbidden:** New callers without security review
**Allowed:** Internal development and testing only
**Review trigger:** Before any production exposure

See dedicated NLQ Authority Decision section below.

---

### DI-004: PostgreSQL-Specific Plain SQL (PRC-004)

**Disposition:** KEEP

**Facts:**
- Production repositories use `DSL.table("render_job")`, `DSL.field("status")` etc.
- These are PostgreSQL-specific plain SQL patterns
- Parameter binding is used (jOOQ handles escaping)
- Tests use H2 which accepts most of these patterns

**Rationale:** These are intentional PostgreSQL-specific patterns with proper parameter binding. They are the current implementation approach and have test coverage. No safer alternative exists without full codegen.

**Owner:** architecture-team
**Verification:** Existing test suite covers these patterns

---

### DI-005: Production String jOOQ DSL Identifiers (PRC-005)

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- 653+ production string DSL identifiers across 90 files
- No jOOQ codegen is configured (codegen tasks = 0)
- String DSL is the current implementation approach
- All identifiers use proper parameter binding
- No SQL injection risk from string identifiers themselves

**Rationale:** String DSL identifiers are technical debt but not a security risk. Rewriting 653+ call points is a large-scale refactoring that should not block Mainline. Deprecate with a deadline to migrate to codegen.

**Owner:** architecture-team
**Replacement:** jOOQ codegen with type-safe DSL
**Deadline:** 2026-10-01 (post-Mainline stabilization)
**Removal verification:** All production jOOQ usage migrated to generated types
**Rule:** No new string DSL identifiers permitted after this decision

---

### DI-006: Test String jOOQ DSL Identifiers (PRC-006)

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- 307 test string identifiers
- Test fixtures use plain SQL DDL via `dsl.execute()`
- Tests use `DSL.table()` and `DSL.field()` for queries

**Rationale:** Same as DI-005. Test string identifiers should be migrated alongside production code.

**Owner:** test-infrastructure
**Replacement:** jOOQ codegen test fixtures
**Deadline:** 2026-10-01 (aligned with DI-005)
**Rule:** No new test string DSL identifiers permitted

---

### DI-007: Unused Codegen Plugin Declaration (PRC-007)

**Disposition:** REMOVE_BEFORE_MAINLINE

**Facts:**
- `build.gradle.kts` declares `id("org.jooq.jooq-codegen-gradle") version "3.19.18" apply false`
- No codegen tasks are configured
- `infra/scripts/generate-jooq.sh` exists but generates no output
- The plugin is declared but never applied

**Rationale:** Unused build plugin declaration is dead configuration. Remove to avoid confusion.

**Owner:** build-infrastructure
**Deadline:** Before Mainline verification
**Verification:** Plugin declaration removed from build.gradle.kts

---

### DI-008: Codegen/Runtime Version Drift (PRC-008)

**Disposition:** REMOVE_BEFORE_MAINLINE

**Facts:**
- `generate-jooq.sh` references `JOOQ_VERSION="3.19.18"`
- `build.gradle.kts` declares jOOQ codegen plugin version `3.19.18`
- Runtime jOOQ version should be aligned with codegen version
- Currently no codegen is active, so drift is theoretical

**Rationale:** Since codegen is not active, version drift is not a runtime risk. However, the declaration should be cleaned up or aligned.

**Owner:** build-infrastructure
**Deadline:** Before Mainline verification (as part of DI-007 cleanup)
**Verification:** Version references consistent or removed

---

### DI-009: Case Naming Inconsistency (PRC-009)

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- `JooqRecords.java` has workaround for H2 uppercase vs PostgreSQL lowercase column names
- `record.field(column)` vs `record.get(column.toUpperCase(), String.class)`
- This is a test-vs-production dialect difference

**Rationale:** The case inconsistency is a known H2/PostgreSQL dialect difference. The workaround is functional. Migrate to codegen to eliminate.

**Owner:** identity-access-module
**Replacement:** jOOQ codegen (type-safe column references)
**Deadline:** 2026-10-01 (aligned with DI-005)
**Verification:** JooqRecords workaround removed after codegen migration

---

### DI-010: Test DDL Fixtures (PRC-010)

**Disposition:** KEEP

**Facts:**
- 5 test DDL fixtures exist:
  - RenderTestSchemaFixture
  - StorageTestSchemaFixture
  - NotificationTestSchemaFixture
  - OutboxEventTestSchemaFixture
  - PromptTestSchemaFixture
- Each defines intentional minimal schema subsets
- Tests depend on these minimal fixtures

**Rationale:** Test fixtures are intentional minimal subsets for test isolation. They serve a valid purpose. DI-001 and DI-002 address specific alignment issues; the fixture pattern itself is sound.

**Owner:** test-infrastructure
**Verification:** Existing test suite passes with fixtures

---

### DI-011: Dynamic WHERE Patterns (PRC-011)

**Disposition:** KEEP

**Facts:**
- Dynamic WHERE used in:
  - PolicyEvaluationService
  - StaleRenderJobCompensationService
  - RenderCacheCleanupService
  - Policy/PolicyEngine
- All use controlled Condition building with jOOQ API
- No user input directly injected into conditions

**Rationale:** Dynamic WHERE patterns are controlled and use jOOQ's type-safe Condition API. No injection risk.

**Owner:** architecture-team
**Verification:** Code review confirms no untrusted input in condition building

---

### DI-012: Constant-Based Identifier Construction (PRC-012)

**Disposition:** KEEP

**Facts:**
- Three locations use constant-based jOOQ DSL identifiers:
  1. `RenderJobLifecycleEventRepository` - `DSL.table()` and `DSL.field()` with compile-time string constants
  2. `StorageRuntimeOrphanReportService` - `DSL.table()` and `DSL.field()` with compile-time string constants
  3. `TimelineRevisionRepository` - `table()` and `field()` (static import) with compile-time string constants
- All identifier values are compile-time string literals
- No user-supplied or runtime-derived identifiers

**Rationale:** All jOOQ DSL identifiers in these three locations are compile-time string constants. No injection risk.

**Identity Note:** DI-012 is a separate canonical decision item that was incorrectly labeled as the original PRC-012 in the first Decision Candidate. The original PRC-012 (from the jOOQ inventory) covered three different locations that have been remapped to PRC-011/DI-011 and PRC-003/DI-003. See the PRC-012 Identity Reconciliation section below.

**Owner:** architecture-team
**Verification:** Code review confirms all identifiers from trusted sources

---

### DI-013: @Deprecated RoleRepository.deleteUserRoleAssignment

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- Method marked @Deprecated
- Javadoc says "workspace-scoped revocation to avoid cross-workspace data loss"
- Uses string DSL for delete operation
- Replacement: workspace-scoped deletion method

**Rationale:** Deprecated method with clear replacement path. Remove after callers migrated.

**Owner:** identity-access-module
**Replacement:** Workspace-scoped role deletion
**Deadline:** 2026-09-01
**Verification:** No callers of deleteUserRoleAssignment remain

---

### DI-014: @Deprecated RenderJobRepository.requeueExecutingJob

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- Method marked `@Deprecated(since = "execution-stack-simplification")`
- Javadoc says "Use createRetryJob after marking the old job FAILED"
- Implements same-row retry (DRIFT from canonical contract)

**Rationale:** Deprecated in favor of createRetryJob which follows the "retry = new RenderJob" frozen rule.

**Owner:** render-module
**Replacement:** createRetryJob
**Deadline:** 2026-09-01
**Verification:** No callers of requeueExecutingJob remain

---

### DI-015: @Deprecated NoopFontStackResolver

**Disposition:** KEEP

**Facts:**
- Marked @Deprecated with "Use BasicFontStackResolver for production"
- Implements FontStackResolver interface
- Used as fallback when BasicFontStackResolver unavailable

**Rationale:** Intentional SPI placeholder for development/testing. Not dead code.

**Owner:** render-module/font
**Verification:** FontStackResolver interface contract maintained

---

### DI-016: @Deprecated NoopMissingGlyphDetector

**Disposition:** KEEP

**Facts:**
- Marked @Deprecated with "Use BasicMissingGlyphDetector for production"
- Implements MissingGlyphDetector interface
- Used as fallback

**Rationale:** Intentional SPI placeholder. Same pattern as DI-015.

**Owner:** render-module/font
**Verification:** MissingGlyphDetector interface contract maintained

---

### DI-017: LegacyHmacJwtDecoder

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- Located at `platform-app/src/main/java/com/example/platform/security/LegacyHmacJwtDecoder.java`
- Has corresponding test file
- Legacy HMAC-based JWT decoder

**Rationale:** Legacy security implementation should be replaced by modern JWT handling. Security boundary item.

**Owner:** platform-app/security
**Replacement:** Modern JWT decoder (Spring Security OAuth2)
**Deadline:** 2026-09-01
**Verification:** LegacyHmacJwtDecoder removed, all auth flows use modern decoder

---

### DI-018: compatibility-migration-module

**Disposition:** DEPRECATE_WITH_DEADLINE

**Facts:**
- Entire module with 20+ files
- Contains MigrationAdapter, MigrationController, MigrationService, etc.
- Supports ExtensionScript, Java, JsonPatch, WASM migration adapters
- Module appears to be framework for future compatibility migrations

**Rationale:** Module is not on current critical path. May be needed for future compatibility work but should be explicitly scoped.

**Owner:** architecture-team
**Replacement:** Direct migration scripts when needed
**Deadline:** 2026-10-01
**Verification:** Module removed or explicitly justified for retention

---

### DI-019 through DI-037: Noop/Stub Implementations

**Disposition:** KEEP (all)

**Items:**
- DI-019: StubChatProvider (ai-module)
- DI-020: NoopHighlightDetectionProvider (ai-module/video)
- DI-021: NoopSilenceDetectionProvider (ai-module/video)
- DI-022: NoopSpeechToTextProvider (ai-module/video)
- DI-023: NoopSubtitleTranslationProvider (ai-module/video)
- DI-024: NoopVideoUnderstandingProvider (ai-module/video)
- DI-025: NoopSecurityAlertAdapter (audit-compliance-module)
- DI-026: NoopKillBillBillingEngine (billing-module)
- DI-027: StubCloudResourceProvider (cloud-resource-module)
- DI-028: NoopMedusaCatalogAdapter (commerce-module)
- DI-029: NoopFederatedQueryGateway (datasource-module)
- DI-030: NoopHyperswitchPaymentProvider (payment-module)
- DI-031: NoopStripePaymentProvider (payment-module)
- DI-032: NoopRenderAuditEventSink (render-module)
- DI-033: NoopFontSecurityScanner (render-module/font)
- DI-034: NoopFontSubsetter (render-module/font)
- DI-035: NoopFontValidator (render-module/font)
- DI-036: NoopSandboxWorkerAdapter (sandbox-runtime-module)
- DI-037: StubPlatformAdapter (social-publish-module)

**Rationale:** All Noop/Stub implementations are intentional SPI (Service Provider Interface) placeholders. They implement defined interfaces and provide fallback behavior when real implementations are not available. This is a standard pattern for:
- Development environments without external service dependencies
- Testing with deterministic behavior
- Gradual feature rollout

These are NOT dead code. They are architectural extension points.

**Owner:** respective module owners
**Verification:** Each implements a defined interface

---

## PRC-001 through PRC-012 Decisions

| PRC ID | Summary | Primary Disposition | Decision |
|--------|---------|---------------------|----------|
| PRC-001 | RenderTestSchemaFixture timestamp vs TIMESTAMPTZ | REMOVE_BEFORE_MAINLINE | Align fixture with V1 schema |
| PRC-002 | StorageTestSchemaFixture missing production columns | REMOVE_BEFORE_MAINLINE | Add missing columns to fixture |
| PRC-003 | NLQ QueryExecutionService security | QUARANTINE | Quarantine until read-only enforcement |
| PRC-004 | PostgreSQL-specific Plain SQL | KEEP | Intentional with parameter binding |
| PRC-005 | Production string jOOQ DSL (653+) | DEPRECATE_WITH_DEADLINE | Migrate to codegen by 2026-10-01 |
| PRC-006 | Test string jOOQ DSL (307) | DEPRECATE_WITH_DEADLINE | Migrate to codegen by 2026-10-01 |
| PRC-007 | Unused codegen plugin declaration | REMOVE_BEFORE_MAINLINE | Remove from build.gradle.kts |
| PRC-008 | Codegen/runtime version drift | REMOVE_BEFORE_MAINLINE | Clean up version references |
| PRC-009 | Case naming inconsistency | DEPRECATE_WITH_DEADLINE | Migrate to codegen by 2026-10-01 |
| PRC-010 | Test DDL fixtures | KEEP | Intentional minimal fixtures |
| PRC-011 | Dynamic WHERE patterns | KEEP | Controlled Condition API usage |
| PRC-012 | Constant-based identifier construction (DI-012) | KEEP | Three repositories/services, all identifiers from compile-time constants |

**Note:** DI-012 is a separate canonical decision item covering constant-based identifier construction. The original PRC-012 finding from the jOOQ inventory (three dynamic identifier sites) has been remapped: see PRC-012 Identity Reconciliation below.

---

### PRC-012 Identity Reconciliation

**Reconciliation Task:** ARCH-CODE-GOV-MAINLINE-READINESS-BATCH-AUTHORITY-DECISION.1-PRC-012-EVIDENCE-RECONCILIATION.1
**Classification:** DECISION_CANDIDATE_REDEFINED_PRC_012

#### First Authoritative Source

- **Task:** ARCH-CODE-GOV-MAINLINE-READINESS-JOOQ-HARD-CODING-CODEGEN-INVENTORY.1
- **Evidence:** `24-dynamic-identifier-inventory.tsv`
- **Finding:** HC-DYNAMIC-IDENTIFIER
- **Original description:** Three dynamic identifier sites

#### Original Three Locations

1. `MarketplaceListingRepository` dynamic WHERE (lines 66-80) — conditional WHERE construction from service-layer parameters
2. `MarketplaceListingRepository` ORDER BY selection (line 88) — ternary between two hardcoded ORDER BY strings
3. `QueryExecutionService` NLQ SQL execution (line 84) — user/AI-generated SQL via JdbcTemplate

#### Authority-Stage Remapping

The Authority Decision stage remapped the original PRC-012 occurrences by risk dimension:

| Original Location | Target PRC | Target DI | Disposition | Rationale |
|---|---|---|---|---|
| MarketplaceListingRepository dynamic WHERE | PRC-011 | DI-011 | KEEP | Controlled Condition API, validated inputs |
| MarketplaceListingRepository ORDER BY selection | PRC-011 | DI-011 | KEEP | Hardcoded ternary, no injection risk |
| QueryExecutionService NLQ SQL execution | PRC-003 | DI-003 | QUARANTINE | Full SQL execution capability, requires security controls |

**This remapping is a normalization at the Authority Decision stage. It does not delete the original finding. It does not create three new Decision Items. It does not change the 37-item total.**

#### Relationship to PRC-011 / DI-011

PRC-011 (DI-011) covers validated MarketplaceListingRepository dynamic identifier behavior. The two MarketplaceListingRepository occurrences from the original PRC-012 are subsumed under PRC-011 because they share the same risk dimension: controlled dynamic WHERE/ORDER BY construction with validated inputs.

#### Relationship to PRC-003 / DI-003

PRC-003 (DI-003) covers the NLQ QueryExecutionService security boundary. The QueryExecutionService occurrence from the original PRC-012 is remapped to PRC-003 because it represents full SQL execution capability risk, which is a different (and higher) risk dimension than identifier construction. PRC-003 remains QUARANTINE.

#### DI-012 Separate Canonical Identity

DI-012 is a separate canonical decision item covering constant-based identifier construction in three repositories/services. It was incorrectly labeled as the original PRC-012 in the first Decision Candidate. DI-012 covers:

1. `RenderJobLifecycleEventRepository` — `DSL.table()` / `DSL.field()` with compile-time string constants
2. `StorageRuntimeOrphanReportService` — `DSL.table()` / `DSL.field()` with compile-time string constants
3. `TimelineRevisionRepository` — `table()` / `field()` with compile-time string constants

DI-012's KEEP disposition is factually supported: all identifiers are compile-time string constants with no injection risk.

#### Impact on Decision Matrix

- **No duplicate decision counting:** DI-003, DI-011, and DI-012 are each counted once
- **No change to 37-item total:** The original PRC-012 occurrences are remapped, not added
- **No new PRC numbering:** PRC-013 is not created
- **Disposition totals unchanged:** 0/4/7/1/25

---

## NLQ Authority Security Decision

**Feature:** QueryExecutionService (federation-query-module)
**Decision ID:** DI-003 (PRC-003)

### Security Controls Assessment

| Control | Status | Evidence |
|---------|--------|----------|
| SqlSafetyValidator | PRESENT | Regex-based validation |
| SELECT/WITH restriction | PRESENT | Pattern enforced |
| DDL/DML prohibition | PRESENT | Pattern enforced |
| Multi-statement prohibition | PRESENT | Semicolon detection |
| LIMIT enforcement | PRESENT | Pattern detected, capped |
| Timeout enforcement | PRESENT | SqlCostEstimator |
| Max rows enforcement | PRESENT | jdbcTemplate.setMaxRows |
| Result redaction | PRESENT | ResultRedactionService |
| Sensitive field filtering | PRESENT | Hardcoded sensitive field list |
| Read-only database principal | NOT ESTABLISHED | No evidence |
| Read-only transaction boundary | NOT ESTABLISHED | No evidence |
| Schema/table allowlist (DB-level) | NOT ESTABLISHED | Validator-level only |
| SQL injection resistance | PARTIAL | Regex-based, bypassable |
| Audit logging | PRESENT | Query ID, timing logged |

### Authority Decision

**Feature Status:** QUARANTINE (development and testing only)

**Allowed SQL:** SELECT and WITH statements only, validated by SqlSafetyValidator

**Read-only Database Principal:** REQUIRED before any production exposure
**Read-only Transaction:** REQUIRED before any production exposure
**Schema and Table Allowlist:** REQUIRED at database level (not just validator)

**Validator Bypass:** PROHIBITED. All queries must pass SqlSafetyValidator.

**Logging and Audit:** Query ID, timing, row count logged. Add: full SQL audit trail, user identity.

**Maximum Runtime:** Controlled by SqlCostEstimator (existing)
**Maximum Returned Rows:** Controlled by jdbcTemplate.setMaxRows (existing)

**Sensitive-Field Handling:** ResultRedactionService filters sensitive fields (existing)

**Release Eligibility Conditions:**
1. Read-only database principal configured
2. Read-only transaction boundary enforced
3. Database-level schema/table allowlist configured
4. SQL audit trail with user identity
5. Penetration testing of SqlSafetyValidator bypass vectors
6. Rate limiting on query execution

---

## Automated Broken-Link Baseline Decision

**Baseline File:** docs/architecture/governance/automated-guards/broken-link-debt-baseline.json
**Metric:** 126 unique docs/ broken-link identities
**Pinned Baseline Commit:** 1af5ceb8a682cb946dd04a1891a4bf68125794a9

### Decision

**Disposition:** KEEP_AS_NON_REGRESSION_BASELINE

**Primary Mapping:** KEEP

**Rationale:**
1. The 126 figure is a historical automated scan baseline for docs/ broken links
2. It is NOT a code debt metric and must NOT be treated as 126 code fix tasks
3. It is NOT comparable to the curated 40 raw occurrences
4. The baseline serves as a non-regression reference point

**Required Actions:**
1. Continue retaining the baseline file
2. Do NOT rebase on current commit (preserve historical reference)
3. Block new broken-link identities from being introduced
4. Existing 126 identities: batch cleanup in a dedicated documentation task (not mixed with code)
5. Re-establish baseline after documentation cleanup campaign

**Mainline Blocking:** NO

---

## .agent-tasks Retention Decision

**Current State:**
- 213 Git-tracked paths (including deleted paths and logs)
- 174 current .md/.json/.txt files
- 31 current .log archive candidates

### Category Dispositions

| Category | Retention Decision | Rationale |
|----------|-------------------|-----------|
| Source-of-truth decisions | KEEP_IN_REPOSITORY | Long-term governance facts |
| Final reports | KEEP_IN_REPOSITORY | Attestation evidence |
| Manifests | KEEP_IN_REPOSITORY | Integrity verification |
| Raw logs | ARCHIVE_AS_SINGLE_BUNDLE | Temporary execution evidence |
| Duplicate intermediate evidence | DELETE_AFTER_ARCHIVE | Superseded by final reports |
| Superseded reports | ARCHIVE_AS_SINGLE_BUNDLE | Historical reference only |
| Active task definitions | KEEP_IN_REPOSITORY | Current governance state |

### Retention Rules

1. **Final decision documents** (FINAL_DECISION.md, FINAL_REPORT.md): KEEP_IN_REPOSITORY permanently
2. **Evidence manifests** (MANIFEST.json, SHA256SUMS): KEEP_IN_REPOSITORY permanently
3. **Raw execution logs** (.log files): ARCHIVE_AS_SINGLE_BUNDLE, delete originals after archive
4. **Intermediate agent reports** (01-*.md, 02-*.md etc.): KEEP_IN_REPOSITORY until superseded by final
5. **Duplicate evidence**: DELETE_AFTER_ARCHIVE in single batch operation

### Required Index

After archival, maintain an index file listing:
- Task ID
- Final decision SHA
- Evidence bundle SHA
- Retention category

**Archive scope:** Single batch task, not mixed with code changes
**SHA preservation:** All final decision SHAs must be preserved in the index

---

## Final Mainline Blocker List

**CONFIRMED MAINLINE BLOCKERS: NONE**

| Category | Count | IDs |
|----------|-------|-----|
| BLOCKER | 0 | — |
| REMOVE_BEFORE_MAINLINE | 4 | DI-001, DI-002, DI-007, DI-008 |
| DEPRECATE_WITH_DEADLINE | 7 | DI-005, DI-006, DI-009, DI-013, DI-014, DI-017, DI-018 |
| QUARANTINE | 1 | DI-003 |
| KEEP | 25 | DI-004, DI-010, DI-011, DI-012, DI-015, DI-016, DI-019–DI-037 |

**OpenCueExecutionBackend:** FUTURE_FEATURE_NOT_STALE_DEBT

---

## Authorized Remediation Batches

### BATCH-B-TEST-BUILD-HYGIENE

**Required before Mainline verification:** YES

**Item IDs:**
- DI-001 (PRC-001): RenderTestSchemaFixture timestamp alignment
- DI-002 (PRC-002): StorageTestSchemaFixture column alignment
- DI-007 (PRC-007): Remove unused codegen plugin declaration
- DI-008 (PRC-008): Clean up version references

**Allowed files/modules:**
- render-module/src/test/java/.../RenderTestSchemaFixture.java
- storage-module/src/test/java/.../StorageTestSchemaFixture.java
- build.gradle.kts
- infra/scripts/generate-jooq.sh

**Forbidden scope:**
- Production code changes
- New migrations
- New test cases (only fixture alignment)
- CI changes

**Required tests:**
- All existing tests must pass after fixture changes
- No new test assertions required

**Expected commit count:** 1-2

**Independent verification:** YES

---

### BATCH-C-DEAD-COMPATIBILITY

**Required before Mainline verification:** NO (post-Mainline)

**Item IDs:**
- DI-005 (PRC-005): Production string DSL migration
- DI-006 (PRC-006): Test string DSL migration
- DI-009 (PRC-009): Case naming migration
- DI-013: RoleRepository deprecated method
- DI-014: RenderJobRepository deprecated method
- DI-017: LegacyHmacJwtDecoder
- DI-018: compatibility-migration-module

**Scope:**
- Can batch delete: DI-013, DI-014 (after caller migration)
- Requires verification first: DI-017 (reflection/config reachability)
- Must retain as intentional: DI-018 (until explicitly justified)

**Forbidden:**
- Bulk rewriting 960+ jOOQ call points in one commit
- Removing compatibility-migration-module without explicit approval

**Required tests:**
- All existing tests pass after each removal
- No new string DSL identifiers introduced

**Expected commit count:** 3-5 (phased)

**Independent verification:** YES

---

## Mainline Readiness Transition Rules

Since BLOCKER = 0 and REMOVE_BEFORE_MAINLINE > 0:

1. Execute BATCH-B-TEST-BUILD-HYGIENE first
2. Re-verify REMOVE_BEFORE_MAINLINE items are resolved
3. Proceed to Mainline Readiness independent verification
4. DEPRECATE, QUARANTINE, and KEEP items do not block Mainline
5. BATCH-C-DEAD-COMPATIBILITY is post-Mainline work

**Direct Mainline Verification Authorized:** NO (pending BATCH-B completion)

---

## Frozen Architecture

The following architecture decisions are frozen and must not be changed by this decision or any authorized remediation batch:

- Product canonical
- TimelineRevision immutable
- RenderJob = immutable execution attempt
- Retry creates new RenderJob
- Execution facts retained
- Fallback not implemented
- Future fallback creates new RenderJob
- OpenCue = ExecutionEnvironment
- FFmpeg / Remotion / GPAC / Blender = ExecutionBackend
- Unknown backend explicit failure
- No silent fallback
- Artifact DAG deferred and extension-only
- Timeline Git before Artifact DAG
- Frontend remains paused

---

## Credential and Remote-Publication Boundaries

### Credential Exception

**Exception ID:** ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1
**Status:** ACTIVE_TEMPORARY_EXCEPTION
**Review/Expiry:** 2026-08-04
**Token Rotated:** NO
**Residual Risk:** PRESENT

This decision does NOT close or extend this exception.

### Remote Publication

**Remote Push:** NOT AUTHORIZED
**GitHub Release:** NOT AUTHORIZED
**OpenCue Implementation:** NOT AUTHORIZED

---

## First Production Release Zero-Debt Rebaseline

**Rebaseline Task:** ARCH-CODE-GOV-ZERO-DEBT-FIRST-PRODUCTION-RELEASE-REBASELINE.1
**Rebaseline Date:** 2026-07-22
**Rebaseline Status:** CANDIDATE

### Gate Separation

This document establishes Mainline Readiness. Mainline Readiness is accepted.

First Production Release Readiness is a separate, higher gate. Mainline Readiness does not satisfy First Production Release requirements.

```
Mainline Readiness: GOVERNANCE_ACCEPTED
First Production Release Readiness: NOT_ACCEPTED
Known technical debt: PRESENT
```

### Reclassified Items

The following items are reclassified from `DEPRECATE_WITH_DEADLINE` to `REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE`:

| ID | Module | Description |
|----|--------|-------------|
| DI-005 | all modules | Production string DSL identifiers (653+) |
| DI-006 | all test modules | Test string identifiers (307) |
| DI-009 | identity-access-module | Mixed identifier naming conventions |
| DI-013 | identity-access-module | Deprecated compatibility API |
| DI-014 | render-module | Deprecated compatibility API |
| DI-017 | platform-app/security | Deprecated security boundary |
| DI-018 | compatibility-migration-module | Compatibility migration module |

Gate enforcement: `FIRST_PRODUCTION_RELEASE = PROHIBITED` when any item is not closed.

### Additional Release Gate Requirements

| Condition | Required Value |
|-----------|---------------|
| DI-003 Quarantine | SECURE_AND_ENABLE or REMOVE_FROM_FIRST_RELEASE |
| jOOQ untyped identifiers | 0 |
| Broken-link identities | 0 |
| .agent-tasks unindexed evidence | 0 |
| Credential exceptions | 0 |
| OpenCue production enablement | PROHIBITED |

### Authority

The full zero-debt policy, implementation batches, and gate matrix are defined in:
`docs/architecture/governance/release/first-production-release-zero-debt-policy.md`

That document is the single authority for First Production Release gate decisions.

