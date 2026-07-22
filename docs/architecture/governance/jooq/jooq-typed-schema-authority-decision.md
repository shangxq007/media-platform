# jOOQ Typed Schema Authority Decision

## Authority and Scope

**Task:** ARCH-CODE-GOV-ZERO-DEBT-BATCH-ZD-A-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION.1
**Role:** MEDIA-JOOQ-TYPED-SCHEMA-AUTHORITY-DECISION-AGENT
**Base Commit:** 64f9ade3bf1fc9ebed014df4f9774da06f2ed962
**Base Status:** MAINLINE_READINESS_GOVERNANCE_ACCEPTED, ZERO_DEBT_FIRST_PRODUCTION_RELEASE_REBASELINE_GOVERNANCE_ACCEPTED

This document is the single authority for jOOQ Typed Schema architecture selection. It selects the architecture, defines the naming convention, establishes drift guards, and authorizes implementation slices for BATCH-ZD-A.

This is a governance decision document only. No implementation, migration, test, Gradle, or CI changes are authorized by this document.

---

## Debt Items in Scope

| ID | Description | Disposition |
|----|-------------|-------------|
| DI-005 | Production string DSL identifiers (653+) | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-006 | Test string identifiers (307) | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-009 | Mixed identifier naming conventions | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| PRC-004 | PostgreSQL-specific Plain SQL retention boundary | ALLOWLIST_REVIEW |
| PRC-011 | Validated dynamic identifier behavior | ALLOWLIST_REVIEW |
| PRC-012 | Remapped occurrences | ALLOWLIST_REVIEW |
| DI-012 | Controlled constant-based identifier sites | ALLOWLIST_REVIEW |

---

## Current State

| Metric | Value |
|--------|-------|
| Runtime jOOQ | 3.19.30 (Spring Boot 4.0.4 BOM) |
| Previous codegen plugin | 3.19.18 (REMOVED) |
| Codegen script | scripts/generate-jooq.sh (3.19.18, H2, Kotlin) |
| Generated sources | 0 (gitignored) |
| Central typed schema | NONE |
| V1 tables | 147 |
| Production modules using jOOQ | 15 |
| Test modules using jOOQ | 6 |
| Active V2-V5 | 0 |
| Production untyped identifier constructions | 1130 |
| Test untyped identifier constructions | 181 |
| Plain SQL sites | 4 |
| Dynamic identifier sites | 19 |

### Inventory Notes

The production identifier construction count (1130) exceeds the governance spec estimate (653+) because the spec counted at a coarser granularity. The actual count includes every unique (module, file, type, value) tuple across 15 production modules. The test count (181) is lower than the spec estimate (307), likely because the spec included broader pattern matching. Both counts must be verified during implementation slices.

---

## Architecture Options Evaluated

### Option A: jOOQ Code Generation (DDL_DATABASE_OFFLINE)

**Compile-time verified:** YES
**Clean checkout:** YES (committed generated sources)
**Deterministic:** YES (same DDL + config = same output)
**PostgreSQL native:** YES (DDL_DATABASE_OFFLINE with PostgreSQL dialect)
**CI verifiable:** YES (regenerate + diff)
**Drift detectable:** YES (compile-time + CI guard)
**Version aligned:** YES (generator = runtime = 3.19.30)

**Score: 9/10 — STRONGLY PREFERRED**

### Option B: Central Typed Schema Metadata

**Compile-time verified:** YES
**Clean checkout:** YES
**Deterministic:** YES
**PostgreSQL native:** PARTIAL (manual maintenance)
**CI verifiable:** PARTIAL
**Drift detectable:** PARTIAL (no automatic detection)
**Version aligned:** YES

**Score: 7/10 — FALLBACK ONLY**

Manual maintenance of 147 tables × N columns is error-prone and does not scale.

### Option C: Deterministically Generated Constants

**Compile-time verified:** YES
**Clean checkout:** CONFIGURABLE
**Deterministic:** YES
**PostgreSQL native:** PARTIAL
**CI verifiable:** YES
**Drift detectable:** PARTIAL
**Version aligned:** YES

**Score: 7/10 — FALLBACK ONLY**

Does not provide type-safe record mapping; same tooling cost as full codegen with less benefit.

### Option D: Compile-time Verified Equivalent

**Compile-time verified:** YES
**Clean checkout:** CONFIGURABLE
**Deterministic:** YES
**PostgreSQL native:** PARTIAL
**CI verifiable:** YES
**Drift detectable:** PARTIAL
**Version aligned:** YES

**Score: 5/10 — NOT RECOMMENDED**

No established tooling; would require custom development from scratch.

---

## Selected Architecture

```
JOOQ_CODE_GENERATION_DDL_DATABASE_OFFLINE
```

### Selection Rationale

1. **V1 is the single schema source of truth.** V1__initial_schema.sql contains all 147 tables. Active V2-V5: 0. No runtime database needed.

2. **PostgreSQL type coverage is complete.** DDL_DATABASE_OFFLINE with PostgreSQL dialect parses V1 DDL natively. H2-based generation (REJECTED) has known gaps: TIMESTAMP type mismatch, JSON/JSONB handling, case sensitivity.

3. **Generator/runtime version alignment is explicit.** Both at 3.19.30. Authority: Spring Boot 4.0.4 BOM. Drift allowed: 0.

4. **Generation is deterministic and repeatable.** Same V1 DDL + same generator config = same output. No randomness, no developer-machine state.

5. **Clean checkout compiles without codegen.** Generated sources committed to repository. Developers need Docker only for regeneration, not for compilation.

6. **No production database dependency.** DDL_DATABASE_OFFLINE reads DDL files, not a live PostgreSQL instance.

7. **No developer-machine history dependency.** CI regenerates from scratch. Committed sources serve as compile-time cache.

8. **CI drift verification is mechanical.** Regenerate → diff → fail on mismatch. No human review needed.

9. **No inert plugin declaration.** Previous 3.19.18 plugin was REMOVED. New approach uses active generation at 3.19.30.

10. **V1 is not modified for the generator.** Generator adapts to V1, not vice versa.

---

## Schema Source of Truth Chain

```
V1__initial_schema.sql
  ↓ (DDL input)
jOOQ Code Generator (DDL_DATABASE_OFFLINE, PostgreSQL dialect)
  ↓ (generated Java sources)
com.example.platform.jooq.generated.*
  ↓ (compile-time references)
Production and test code
```

**Chain rules:**
1. V1 is the SOLE schema source of truth
2. Generator reads V1, does NOT read live database
3. Generated sources are committed to repository
4. CI regenerates and verifies against committed sources
5. Any drift = build failure
6. V1 is NEVER modified to accommodate generator

---

## Version Contract

| Item | Value | Authority |
|------|-------|-----------|
| Runtime jOOQ | 3.19.30 | Spring Boot 4.0.4 BOM |
| Generator jOOQ | 3.19.30 | Explicit declaration matching BOM |
| Version drift allowed | 0 | This decision |
| Previous inert declaration | 3.19.18 (REMOVED) | Must not be restored |

---

## Generated Source Ownership

| Item | Decision |
|------|----------|
| Committed to Git | YES |
| Directory | shared-kernel/src/main/java/com/example/platform/jooq/generated/ |
| Package | com.example.platform.jooq.generated |
| Generator | JavaGenerator (not KotlinGenerator) |
| Clean checkout behavior | Compiles without Docker/database |
| CI verification | Regenerate + diff against committed |
| .gitignore | Remove old build/generated-sources/jooq/ pattern |

---

## Naming Convention

| Pattern | Rule | Example |
|---------|------|---------|
| Table class | PascalCase from snake_case | render_job → RenderJob |
| Field constant | UPPER_SNAKE_CASE | project_id → PROJECT_ID |
| Package | com.example.platform.jooq.generated | — |
| Reserved words | jOOQ handles quoting internally | "user" → User |
| Acronyms | No special handling | gpu_count → GPU_COUNT |
| Plural/singular | Preserve V1 naming | outbox_events → OutboxEvents |
| Quoted identifiers | All V1 identifiers are unquoted | — |

Mixed naming debt (DI-009): 0 after migration (generated types enforce V1 naming exactly).

---

## Plain SQL Retention Boundary

PRC-004 Plain SQL sites require allowlist approval. Criteria for retention:

1. jOOQ Typed DSL has no equivalent or safer expression
2. All parameters are bound
3. Dynamic identifiers do not come from external input
4. SQL is locally encapsulated
5. Tests exist for PostgreSQL-specific behavior
6. Specific retention reason documented
7. Does not form a general string SQL escape hatch

**Approved sites:** To be determined during ZD-A4 closure
**Unapproved sites:** Must be 0 at first production release

---

## Dynamic Identifier Boundary

PRC-011, PRC-012, DI-012 dynamic identifier sites require allowlist approval.

**Allowed sources:** enum, generated constant, or closed allowlist
**Forbidden:** User or AI arbitrary identifiers
**Failure mode:** Unknown values must fail explicitly
**Default/fallback:** None (no silent fallback)

---

## No-New-Debt Guards

### Guard 1: No New Untyped jOOQ Identifiers
- **Detection:** grep for `table("`, `field("`, `DSL.table("`, `DSL.field("`
- **Scope:** All production and test Java source files
- **Allowlist:** Exact file:line entries in `.jooq-allowlist.txt`
- **Action:** Build failure on violation

### Guard 2: Generated Source Drift
- **Detection:** CI regeneration + diff
- **Action:** Build failure if committed sources differ from regenerated

### Guard 3: Generator/Runtime Version Alignment
- **Detection:** Gradle dependency resolution check
- **Action:** Build failure if generator != runtime (both must be 3.19.30)

### Guard 4: No New Plain SQL
- **Detection:** grep for `DSL.sql(`, raw SQL patterns
- **Allowlist:** Exact file:line entries in `.plain-sql-allowlist.txt`
- **Action:** Build failure on violation

### Guard 5: No New Dynamic Identifiers
- **Detection:** grep for `table(variable)`, `field(variable)`
- **Allowlist:** Exact file:line entries in `.dynamic-identifier-allowlist.txt`
- **Action:** Build failure on violation

All guards are mechanical (grep + diff), executable locally and in CI.

---

## Implementation Slices

### ZD-A1: Typed Schema Foundation and Drift Guard

**Scope:** Foundation only — generate types, establish guards
**Item IDs:** DI-005 (partial), DI-009 (partial)
**Modules:** shared-kernel, root build
**Identifier reduction:** 0 (foundation, no migration)
**Allowed files:** Generated sources, build config, guard scripts, allowlist files
**Forbidden scope:** All production source, test source, migration, CI files
**Expected commits:** 1-2
**Required tests:** Compilation of generated types, guard script smoke test
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:** Generated sources committed, compile passes, guard scripts executable, V1 unchanged, generator version=3.19.30

### ZD-A2: Production Repository Migration

**Scope:** Replace all production untyped identifiers with generated types
**Item IDs:** DI-005 (production)
**Modules:** render-module, notification-module, entitlement-module, delivery-module, identity-access-module, outbox-event-module, commerce-module, platform-app, artifact-catalog-module, billing-module, payment-module, audit-compliance-module, storage-module, secrets-config-module, config-module
**Identifier reduction:** ~1130 unique constructions → 0
**Allowed files:** Production Java source files in affected modules
**Forbidden scope:** Test files, migration files, Gradle files, CI files
**Expected commits:** 3-5
**Required tests:** Per-module compilation and existing test suite
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:** Production untyped identifiers=0, all module tests pass, no new Plain SQL

### ZD-A3: Test and Fixture Identifier Migration

**Scope:** Replace all test untyped identifiers with generated types
**Item IDs:** DI-006 (test)
**Modules:** render-module (test), outbox-event-module (test), audit-compliance-module (test), extension-module (test), notification-module (test), platform-app (test)
**Identifier reduction:** ~181 unique constructions → 0
**Allowed files:** Test Java source files in affected modules
**Forbidden scope:** Production files, migration files, Gradle files
**Expected commits:** 2-3
**Required tests:** Full test suite pass
**Full quality trigger:** YES
**Independent verification:** YES
**Exit criteria:** Test untyped identifiers=0, full test suite pass, test DDL preserved

### ZD-A4: Legacy String API Removal and Zero-Debt Closure

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
**Exit criteria:** Production=0, Test=0, Naming=0, Plain SQL approved only, Dynamic IDs approved only, Guard PASS, Clean checkout PASS

---

## Zero-Debt Closure Gate

| Gate | Metric | Required Value |
|------|--------|---------------|
| G-001 | Production untyped jOOQ identifiers | 0 |
| G-002 | Test untyped jOOQ identifiers | 0 |
| G-003 | Mixed naming violations | 0 |
| G-004 | Unapproved Plain SQL sites | 0 |
| G-005 | Unapproved dynamic identifiers | 0 |
| G-006 | Generated metadata drift | 0 |
| G-007 | Generator/runtime version drift | 0 |
| G-008 | Clean checkout | PASS |
| G-009 | Full quality | PASS |
| G-010 | NLQ Quarantine | UNCHANGED |

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

---

## Execution Order

```
This Decision (ZD-A Authority)
→ Independent Decision Verification
→ Governance Acceptance
→ ZD-A1: Typed Schema Foundation and Drift Guard
→ Independent Verification
→ ZD-A2: Production Repository Migration
→ Independent Verification
→ ZD-A3: Test and Fixture Identifier Migration
→ Independent Verification
→ ZD-A4: Zero-Debt Closure
→ Independent Verification
→ Zero-Debt First-Release Verification (all batches)
```

---

## Document Lineage

| Version | Date | Status |
|---------|------|--------|
| 1 | 2026-07-22 | CANDIDATE |
