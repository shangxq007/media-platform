---
metadata_schema_version: 1
document_id: "render-adr-adr-027-architecture-map-as-code-likec4"
title: ""
artifact_type: "UNKNOWN"
domain: ""
authority_class: "INFORMATIVE"
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
retention_class: "PROJECT_LIFETIME"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: false
blocks_v5: false
---

# ADR-027: Architecture Map as Code with LikeC4

**Date:** 2026-07-01
**Status:** PROPOSED
**Authority:** DOCSYNC.1

---

## Context

The media-platform architecture is documented in markdown files (blueprint, current-system-state, module-boundaries, ADRs). As the system grows, it becomes harder to visualize relationships between components, modules, and boundaries.

LikeC4 is an open-source tool for defining architecture diagrams as code. It generates visual diagrams from structured definitions, keeping architecture documentation in sync with code.

## Decision

Adopt LikeC4 for architecture-as-code visualization:

1. Define component relationships in `.likec4` files
2. Generate visual diagrams from code definitions
3. Keep architecture maps in `docs/architecture/maps/`
4. Update maps when architecture-relevant changes occur
5. Include LikeC4 in CI for diagram validation

## Consequences

### Positive
- Architecture diagrams stay in sync with code
- Version-controlled architecture visualization
- Automated diagram generation
- Clear component relationship documentation

### Negative
- Additional tool dependency
- Learning curve for LikeC4 DSL
- Diagrams may lag behind rapid changes

### Mitigations
- LikeC4 is lightweight and open-source
- DSL is simple and well-documented
- CI validation catches stale diagrams

## Forbidden Assumptions

1. LikeC4 does NOT replace architecture documentation
2. LikeC4 does NOT enforce architecture boundaries (Semgrep does that)
3. LikeC4 diagrams are NOT authoritative (AGENTS.md and ADRs are)
4. LikeC4 does NOT auto-generate from code (manual definition required)
