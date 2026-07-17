---
metadata_schema_version: 1
document_id: "architecture-governance-canonical-contracts-platform-kernel-contract"
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

# Platform Kernel Contract

**Contract ID:** platform-kernel
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED

## PK-001: Platform Positioning
The Media Capability Platform **MUST** be understood as a full-stack media production platform, not merely an editor, render farm, AI application, or FFmpeg wrapper.

## PK-002: Platform Kernel Boundary
The Platform Kernel **MUST** provide stable SPIs for Product, Timeline, RenderJob, and Provider abstractions. Module boundaries **MUST** be respected.

## PK-003: Stable SPI Boundary
Public interfaces **MUST NOT** change without ADR acceptance. Breaking changes **MUST** go through governance review.

## PK-004: Module Ownership
- `render-module`: execution, rendering, providers
- `platform-app`: API, controllers, DTOs, migrations
- `shared-kernel`: common abstractions

## PK-005: Extension Layer
Extension-layer designs (Artifact DAG) **MUST NOT** be treated as core platform requirements.

## Non-Goals
- Platform is NOT merely an editor
- Platform is NOT merely a render farm
- Platform is NOT merely an AI application
- Platform is NOT merely an FFmpeg wrapper

## Change Authority
- USER_EXPLICIT_APPROVAL
- ADR_ACCEPTANCE
