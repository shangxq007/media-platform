---
metadata_schema_version: 1
document_id: "architecture-governance-greenfield-baseline-specification"
title: "Greenfield Baseline Schema Specification"
artifact_type: "ARCHITECTURE_CONTRACT"
domain: "schema-governance"
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED_WITH_TAG_FINALIZATION_REQUIRED"
owner: "architecture-governance"
document_version: "3.0"
created_at: "2026-07-20"
last_reviewed_at: "2026-07-21"
review_cadence_days: null
supersedes: ["2.0 (Contract.1, b509be5)"]
superseded_by: []
canonical_contracts: ["schema-intent", "flyway-migration-baseline"]
source_of_truth_domains: ["database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: true
blocks_v5: false
---

# Greenfield Baseline Schema Specification

**Contract ID:** greenfield-baseline-specification
**Authority Status:** GOVERNANCE_CANONICAL
**Effective Date:** 2026-07-21
**Authorization Commit:** 866ca920d9937d9a5e0994f4286d029f6c97de3f
**Normalization Commit:** b1f974ed694203998d49d8d555e30bd85a7940b7
**Contract Amendment:** Contract.2 (this document) supersedes Contract.1 (b509be5) for time-type fields

## GB-001: Target Schema Equation

The Greenfield V1 Target Schema is defined as:

```text
Legacy V1-V4 Final Schema
+
GREENFIELD-SCHEMA-DELTA-DG-001
=
Greenfield V1 Target Schema
```

Where:
- **Legacy reference:** 096e8ce3a6e1880b7facec3593a4402ff8a92645
- **Normalization commit:** b1f974ed694203998d49d8d555e30bd85a7940b7
- **Legacy V1-V4 Final Schema:** The frozen Flyway migration bytes from V1 through V4, applied to a clean database.
- **GREENFIELD-SCHEMA-DELTA-DG-001:** The single approved schema delta, documented in `schema-delta-registry/greenfield-schema-delta-DG-001.json`.

### Supersession

The previous equation (strict Legacy V1-V4 = Greenfield consolidated V1) is **SUPERSEDED** by this specification. The previous equation did not account for the approved delta DG-001.

**Old equation (pre-amendment):**
```text
Legacy V1-V4 Final Schema = Greenfield consolidated V1
```

**New equation (this amendment):**
```text
Legacy V1-V4 Final Schema + GREENFIELD-SCHEMA-DELTA-DG-001 = Greenfield V1 Target Schema
```

### Equations Not Permitted

- DG-001 is a single precisely approved delta; it does NOT authorize general additive differences
- Unrecorded Candidate-only objects are NOT permitted
- Outside DG-001, strict semantic equivalence continues to be required

## GB-002: Approved Delta Scope

GREENFIELD-SCHEMA-DELTA-DG-001 is the **only** explicitly approved schema delta.

DG-001 is:
- A single-column addition: `public.render_job.updated_at`
- An explicitly authorized governance exception for exactly one schema object
- NOT a general compatibility exception
- NOT permission for arbitrary additive differences
- NOT permission for unrecorded schema deltas

Outside DG-001, **all** schema objects continue to require strict semantic equivalence between Legacy V1-V4 and Greenfield V1.

## GB-003: DG-001 Definition Summary

| Property | Value |
|----------|-------|
| Delta ID | GREENFIELD-SCHEMA-DELTA-DG-001 |
| Status | APPROVED_IMPLEMENTED_AND_REVERIFIED |
| Object type | COLUMN |
| Schema | public |
| Table | render_job |
| Column | updated_at |
| Legacy state | ABSENT |
| Target state | PRESENT |
| PostgreSQL type | TIMESTAMP WITH TIME ZONE |
| PostgreSQL alias | TIMESTAMPTZ |
| PostgreSQL UDT | timestamptz |
| Nullable | YES |
| Default | NONE |
| Creation value | NULL |
| Database trigger | ABSENT |
| Update owner | APPLICATION |
| Java persistence type | java.time.Instant |
| Java cutoff type | java.time.Instant |
| Temporal semantics | ABSOLUTE_INSTANT |
| Business semantics | LAST_PERSISTED_STATE_CHANGE_TIME |

Full definition: `schema-delta-registry/greenfield-schema-delta-DG-001.json`

### Historical Note

Contract.1 (b509be5) originally defined DG-001 as TIMESTAMP WITHOUT TIME ZONE with OffsetDateTime persistence. The normalization commit (b1f974ed) changed the implementation to TIMESTAMPTZ + Instant. Contract.2 aligns the governance contract with the verified implementation.

## GB-004: Null Semantics

`updated_at IS NULL` means:

> The RenderJob has not undergone any contract-defined business state change since creation.

Specifically:
- NULL is **NOT** an unknown database error
- NULL is **NOT** an unknown timezone
- NULL is **NOT** the current time
- NULL is **NOT** an alias for created_at
- NULL does **NOT** mean the job was not created
- NULL is **NOT** produced by a database default value
- NULL is **NOT** produced by a database trigger
- NULL **ONLY** means no contract-defined successful business state change has been persisted since record creation

## GB-005: Business Semantics

`public.render_job.updated_at` represents an absolute time point indicating when the same RenderJob execution attempt's externally observable persistent business state was last successfully written to the database by the application.

- At record creation, the column is NULL.
- Only after a successful transaction commit does the value represent a persisted state change.
- Failed, rolled back, or unexecuted write operations must not be treated as update time changes.

## GB-006: Update Responsibility

Update ownership is **APPLICATION_MANAGED**.

- No database trigger maintains this field.
- No database default value exists.
- The application Repository or persistence layer is responsible for explicitly setting the value during approved state change operations.
- All approved updates must use `java.time.Instant` (see GB-007).

## GB-007: Time Convention

`render_job.updated_at` uses absolute time semantics:

- **PostgreSQL type:** TIMESTAMP WITH TIME ZONE (alias TIMESTAMPTZ, UDT timestamptz)
- **Java write type:** `java.time.Instant`
- **Java read cutoff type:** `java.time.Instant`
- **Temporal semantics:** ABSOLUTE_INSTANT
- **Storage:** TIMESTAMPTZ stores and compares absolute time points
- **Database Session timezone:** Affects text display only; does not change the represented absolute instant
- **JVM default timezone:** Does not affect persistence results
- **OS timezone:** Does not affect persistence results
- **Container timezone:** Does not affect persistence results
- **Comparisons:** WHERE, ORDER BY, MIN operate on absolute time semantics
- **Precision:** PostgreSQL stores microseconds; Java Instant nanoseconds are truncated to microsecond precision
- **Deterministic:** YES — across all deployment environments, timezone configuration does not affect absolute time semantics

### Historical Note (Contract.1 Superseded)

Contract.1 (b509be5) defined this as TIMESTAMP WITHOUT TIME ZONE with OffsetDateTime.now() preserving JVM timezone and opaque local timestamp ordering. That definition is **SUPERSEDED** by this contract. The normalization commit (b1f974ed) implemented TIMESTAMPTZ + Instant, independently verified by Normalization Reverification.1.

Source: `schema-delta-registry/greenfield-schema-delta-DG-001.json` timezone_contract section.

## GB-008: Retry Contract

**Retry contract changed:** NO

Authorization of `updated_at` does NOT allow Retry to reuse or overwrite old RenderJobs.

- Retry must continue to create new RenderJob execution attempts.
- The old RenderJob and its `updated_at` continue as historical fact.
- TIMESTAMPTZ does not authorize reusing or overwriting old RenderJobs.
- `updated_at` tracks changes within a single execution attempt only.

## GB-009: Fallback Contract

**Fallback contract changed:** NO

- No Fallback implementation currently exists.
- DG-001 does NOT authorize implicit Fallback, state overwrite, or Backend switching within the same RenderJob.
- Future Fallback must still create a new RenderJob.

## GB-010: Immutable Execution Attempt Contract

RenderJob identity and execution-attempt facts are **immutable**.

Authorization of `updated_at` does NOT expand to:
- Modifying RenderJob identity
- Overwriting historical execution facts
- Changing Retry lineage
- Modifying TimelineRevision identity
- Modifying Backend identity
- Reusing completed RenderJobs

Within the same execution attempt, updating status, progress, error, result, and `updated_at` **is** permitted.

## GB-011: Verification Requirements

See `schema-verification-contract.json` for the complete verification contract.

Key points:
- 22 schema categories must continue to be checked
- Only one difference allowed: DG-001 (public.render_job.updated_at)
- DG-001 must match exactly: TIMESTAMPTZ, UDT timestamptz, nullable, no default, no trigger
- Reference Data must be actually scanned and verified
- Reports must distinguish strict equivalence from approved-target equivalence
- Reports must separately report raw/approved/unapproved/unexplained difference counts

## GB-012: Tag Finalization Gate

This specification does **NOT** authorize Tag Finalization.

Only the following task sequence can authorize Tag Finalization:
1. `ARCH-CODE-GOV-GREENFIELD-BASELINE-CONSOLIDATION.2A-REVERIFICATION.6` → obtain `GREENFIELD_BASELINE_CONSOLIDATION_ACCEPTED_WITH_TAG_FINALIZATION_REQUIRED`
2. `ARCH-CODE-GOV-GREENFIELD-BASELINE-TAG-FINALIZATION.2A-TAG.1` → execute Tag Finalization
3. `ARCH-CODE-GOV-GRADLE-QUALITY-TASKS.2B` → execute Gradle quality tasks

## GB-013: Change Authority

- SCHEMA_MIGRATION_REVIEW
- ADR_ACCEPTANCE
- Explicit user approval required for any modification to this specification
