# First Production Release Zero Known Tech Debt Policy

## Authority and Scope

**Task:** ARCH-CODE-GOV-ZERO-DEBT-FIRST-PRODUCTION-RELEASE-REBASELINE.1
**Role:** MEDIA-ZERO-DEBT-FIRST-RELEASE-GOVERNANCE-DECISION-AGENT
**Base Commit:** 8916f9cb9d45e39c4e28eb6e15fdd5e56d32ee45
**Base Status:** MAINLINE_READINESS_GOVERNANCE_ACCEPTED

This document is the single authority for first production release zero-debt gate policy. It establishes that the first production release requires zero known technical debt.

This is a governance decision document only. No implementation, migration, test, Gradle, or CI changes are authorized by this document.

---

## Core Principle

```
FIRST_PRODUCTION_RELEASE_ZERO_KNOWN_TECH_DEBT
```

The first production release must satisfy:

```
BLOCKER:                              0
REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE: 0
DEPRECATE_WITH_DEADLINE:              0
QUARANTINE:                           0
UNRESOLVED_BROKEN_LINK_IDENTITIES:    0
UNRESOLVED_REPOSITORY_HYGIENE_ITEMS:  0
EXPIRED_EXCEPTIONS:                   0
```

Risk acceptance, deferral deadlines, historical baselines, and Quarantine status must not carry known technical debt into the first production release.

---

## Mainline vs First Production Release Gate Separation

```
Mainline Readiness:
  Codebase is suitable for continued mainline development.
  Status: GOVERNANCE_ACCEPTED

First Production Release Readiness:
  Codebase is suitable for first production deployment.
  Status: NOT_ACCEPTED

Known technical debt:
  PRESENT (7 items reclassified, 1 quarantine, 1 exception, 126 broken links)
```

Mainline Readiness acceptance is NOT revoked. It remains valid for development continuation. However, Mainline Readiness does not satisfy First Production Release requirements.

---

## Reclassified Debt Items

### Items Reclassified from DEPRECATE_WITH_DEADLINE to REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE

| ID | Module | Description | Original Disposition | New Disposition |
|----|--------|-------------|---------------------|-----------------|
| DI-005 | all modules | Production string DSL identifiers (653+) | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-006 | all test modules | Test string identifiers (307) | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-009 | identity-access-module | Mixed identifier naming conventions | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-013 | identity-access-module | Deprecated compatibility API | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-014 | render-module | Deprecated compatibility API | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-017 | platform-app/security | Deprecated security boundary | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |
| DI-018 | compatibility-migration-module | Compatibility migration module | DEPRECATE_WITH_DEADLINE | REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE |

Original deadlines may be retained as internal planning dates but must not substitute for release gate closure.

**Gate enforcement:** When any item above is not closed:

```
FIRST_PRODUCTION_RELEASE = PROHIBITED
```

---

## jOOQ Zero Debt Requirement

### Current State

| Metric | Value |
|--------|-------|
| Production string DSL identifiers | 653+ |
| Test string identifiers | 307 |
| jOOQ codegen tasks | 0 |
| Generated jOOQ source | 0 |

### Required Release State

The following requirements must be satisfied before first production release:

1. Production code must not depend on scattered `table("...")` / `field("...")` strings.
2. Tests must not maintain a separate, drift-prone schema identifier string system.
3. Schema changes must fail at compile time or an independent generation verification stage.
4. PostgreSQL-specific Plain SQL must be retained only in approved narrow scopes.
5. Dynamic identifiers must come from enums, constants, or strict allowlists.
6. No new untyped jOOQ identifiers may be introduced.
7. Clean checkout must not depend on developer-machine-generated files.
8. The generation process must be deterministic, repeatable, and CI-executable.

### Decision Path

At least one of the following approaches must be selected and completed:

- **jOOQ code generation** (full codegen with generated sources committed or CI-generated)
- **Central typed schema metadata** (compile-time-verified constants derived from schema)
- **Generated constants without full codegen** (lightweight generation of identifier constants)
- **Another compile-time-verified equivalent** (must satisfy all 8 requirements above)

"Too many to fix" is not an acceptable justification for retaining hardcoded identifiers.

### Batch

BATCH-ZD-A-JOOQ-TYPED-SCHEMA

---

## NLQ Zero Debt Requirement

### Current State

```
PRC-003 / DI-003: QUARANTINE
```

### Required Release State

The first production release requires one of two final states:

#### Option A: SECURE_AND_ENABLE

Requirements:
- Read-only database principal
- Read-only transaction enforcement
- Database-level schema/table allowlist
- Validator bypass prevention
- Production configuration tests
- Security integration tests
- Audit and redaction verification

#### Option B: REMOVE_FROM_FIRST_RELEASE

Requirements:
- Production configuration must not enable NLQ
- Production call path must not exist
- No production callers
- No silent activation
- Build and test proof that the capability does not enter the first release

The first release must not carry `QUARANTINE` status.

### Batch

BATCH-ZD-C-NLQ-SECURITY-CLOSURE

---

## Deprecated and Compatibility Cleanup

### Items to Close

| ID | Module | Required Decisions |
|----|--------|--------------------|
| DI-013 | identity-access-module | Replacement API, caller migration, reflection/config reachability, module dependency removal, test migration, final deletion condition |
| DI-014 | render-module | Replacement API, caller migration, reflection/config reachability, module dependency removal, test migration, final deletion condition |
| DI-017 | platform-app/security | Replacement API, caller migration, reflection/config reachability, module dependency removal, test migration, final deletion condition |
| DI-018 | compatibility-migration-module | Replacement API, caller migration, reflection/config reachability, module dependency removal, test migration, final deletion condition |

`compatibility-migration-module` must not be retained for "future possible need." The project has never been in production; there are no historical production data consumers that require compatibility paths.

### Batch

BATCH-ZD-B-DEPRECATED-COMPATIBILITY-REMOVAL

---

## Broken-Link Zero Debt Requirement

### Historical Baseline

```
126 unique docs/ broken-link identities
```

### Required Current Value

```
Current broken-link identities: 0
New broken-link introductions: 0
```

The historical 126 count must not serve as the current acceptable value. Documentation debt cleanup may be done in independent commits and must not be mixed with production code changes.

### Batch

BATCH-ZD-D-DOCUMENTATION-EVIDENCE-HYGIENE

---

## .agent-tasks and Evidence Hygiene

### Required Release State

| Item | Disposition |
|------|-------------|
| Final decisions | KEEP_IN_REPOSITORY |
| Required manifests and indexes | KEEP_IN_REPOSITORY |
| Raw logs | ARCHIVE_AS_SINGLE_BUNDLE |
| Duplicate intermediate evidence | DELETE_AFTER_ARCHIVE |
| Superseded reports | Retain index and SHA; archive or delete body per governance rules |

### Gate Requirements

```
Unindexed raw evidence: 0
Duplicate intermediate evidence: 0
Unmanifested archive files: 0
```

### Batch

BATCH-ZD-D-DOCUMENTATION-EVIDENCE-HYGIENE

---

## Credential Exception Release Boundary

### Current State

```
Exception: ARCH-CODE-GOV-EXCEPTION-INJECTION-4-PAT-EXPOSURE.1
Review/expiry: 2026-08-04
Residual risk: PRESENT
```

### Required Release State

This exception must not exist at first production release. Before release:

- Token rotation or revocation
- Scope verification
- Active configuration secret scan
- Repository and evidence rescan
- Exception closure governance

A project-specific account is not a permanent justification for retaining an unrotated token.

---

## KEEP Items Positive Retention Criteria

The 25 items currently classified as `KEEP` are not automatically debt, but must satisfy positive retention criteria:

1. Clear current purpose
2. Clear invocation boundary
3. Clear test or governance contract
4. No silent fallback
5. No placeholder for unfinished implementation
6. No hidden production-unavailable capability

Items retained only for the following reasons must be reclassified:

- "Might be useful later"
- "No time to remove right now"
- "No failures have occurred"
- "Agent not authorized to modify"
- "Just a Stub/Noop"

---

## OpenCue Boundary

### Current State

```
OpenCue Submission Slice local implementation: AUTHORIZED (candidate)
OpenCue production enablement: PROHIBITED
```

### First-Release Eligibility

```
OpenCue first-release eligibility: PENDING_ZERO_DEBT_GATE
```

Before this governance rebaseline is accepted:

- No further OpenCue Polling / Completion / E2E Slice may be authorized.
- The authorized but unexecuted Submission Slice may remain in candidate authorization, but its priority must not exceed the zero-debt plan.

---

## Implementation Batches

### BATCH-ZD-A-JOOQ-TYPED-SCHEMA

**Covers:** DI-005, DI-006, DI-009, PRC-004/PRC-011/PRC-012 retention boundary review

**Scope:**
- Select and implement typed schema approach
- Remove all production string DSL identifiers
- Remove all test string identifiers
- Verify compile-time schema change detection
- Verify clean checkout independence

**Verification:** Independent verification of zero untyped identifiers, compile-time failure on schema change, CI-executable generation

---

### BATCH-ZD-B-DEPRECATED-COMPATIBILITY-REMOVAL

**Covers:** DI-013, DI-014, DI-017, DI-018

**Scope:**
- Determine replacement APIs
- Migrate all callers
- Verify reflection/config reachability removed
- Remove module dependencies
- Migrate tests
- Delete deprecated code and compatibility-migration-module

**Verification:** Independent verification of zero deprecated API callers, module removal proof, test migration proof

---

### BATCH-ZD-C-NLQ-SECURITY-CLOSURE

**Covers:** DI-003

**Scope:**
- Select SECURE_AND_ENABLE or REMOVE_FROM_FIRST_RELEASE
- If SECURE_AND_ENABLE: implement all 7 security requirements
- If REMOVE_FROM_FIRST_RELEASE: prove production unreachability

**Verification:** Independent security verification of selected path

---

### BATCH-ZD-D-DOCUMENTATION-EVIDENCE-HYGIENE

**Covers:** 126 broken-link identities, .agent-tasks raw logs, duplicate intermediate evidence, archive indexes and manifests

**Scope:**
- Fix or remove all 126 broken-link identities
- Archive raw governance logs as single bundle
- Delete duplicate intermediate evidence
- Verify manifest completeness

**Verification:** Independent verification of zero broken links, zero unindexed evidence, zero duplicate evidence

---

## Batch Execution Order

```
Zero-Debt Governance Rebaseline
→ BATCH-ZD-A-JOOQ-TYPED-SCHEMA
→ Independent Verification
→ Governance Acceptance
→ BATCH-ZD-B-DEPRECATED-COMPATIBILITY-REMOVAL
→ Independent Verification
→ Governance Acceptance
→ BATCH-ZD-C-NLQ-SECURITY-CLOSURE
→ Independent Security Verification
→ Governance Acceptance
→ BATCH-ZD-D-DOCUMENTATION-EVIDENCE-HYGIENE
→ Independent Verification
→ Zero-Debt First-Release Verification
```

A/B/C order may be adjusted based on dependencies. Parallel implementation and skipped verification are prohibited.

---

## First-Release Gate

```
FIRST_PRODUCTION_RELEASE_READY
```

Granted only when all conditions are satisfied:

| Condition | Required Value |
|-----------|---------------|
| Known debt items | 0 |
| Deprecated APIs | 0 |
| Compatibility-only modules | 0 |
| Quarantined features | 0 |
| Unresolved jOOQ identifier debt | 0 |
| Broken-link identities | 0 |
| Unarchived raw governance logs | 0 |
| Expired or active temporary credential exceptions | 0 |
| Full quality | PASS |
| Security verification | PASS |
| Schema verification | PASS |
| Release configuration verification | PASS |

---

## Frozen Architecture

This document does not alter the Frozen Architecture. All frozen rules remain in force.

---

## Document Lineage

| Version | Date | Status |
|---------|------|--------|
| 1 | 2026-07-22 | CANDIDATE |
