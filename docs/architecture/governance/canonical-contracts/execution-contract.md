---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-execution-contract"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "CANONICAL_ACCEPTED"
lifecycle_state: "ACTIVE"
acceptance_state: "NOT_APPLICABLE"
owner: "architecture-governance"
document_version: null
created_at: null
last_reviewed_at: "2026-07-17"
review_cadence_days: null
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: []
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# Execution Contract

**Contract ID:** execution
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED
**Frozen Rules:** F-007, F-008

## EX-001: ExecutionJob
ExecutionJob **MUST** represent a unit of work dispatched to an ExecutionEnvironment.

## EX-002: Task
Task **MUST** represent an atomic work unit within an ExecutionJob.

## EX-003: Command
Command **MUST** represent a concrete executable instruction.

## EX-004: ExecutionEnvironment
OpenCue **MUST** be treated as an ExecutionEnvironment (F-007).

## EX-005: ExecutionBackend
FFmpeg, Remotion, GPAC, and Blender **MUST** be treated as ExecutionBackends (F-008).

## EX-006: Worker Boundary
Worker execution **MUST** be isolated from platform control plane.

## Change Authority
- ADR_ACCEPTANCE
