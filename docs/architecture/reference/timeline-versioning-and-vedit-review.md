# Timeline Versioning and vedit Reference Review

## 1. Purpose

Deep review of timeline diff/merge, vedit, Merkle-DAG, and future Canonical Timeline Diff design for the media-platform. Compare external reference systems against the platform's current graph models.

## 2. Scope and Non-goals

**Scope:** explicit09/vedit, pyvedit, OpenTimelineIO, Git commit DAG, Merkle-DAG, Merkle-CRDT, content-addressed object store, structured timeline diff, timeline patch, timeline conflict resolution, local-first timeline editing.

**Non-goals:** Add runtime dependencies, install vedit/pyvedit, implement TimelineDiff, implement TimelineGit, implement content-addressed object store, execute rendering.

## 3. Current Platform Timeline / Template / Workflow Context

The platform has 9 conceptual layers relevant to timeline versioning:

| Layer | Description | Current State |
|-------|-------------|---------------|
| L1 OTIO Exchange | External interchange format | Supported as input |
| L2 Canonical Timeline IR | Platform's canonical editing model | TimelineSpec, NormalizedTimeline |
| L3 TemplateApplication | Template-applied timeline intent | TemplateApplicationRequest, operations |
| L4 WorkflowStep(APPLY_TEMPLATE) | Workflow orchestration | Semantic model only |
| L5 Artifact Dependency Graph | Compile-time dependency DAG | Implemented |
| L6 Logical Capability Graph | Provider-neutral capabilities | Implemented |
| L7 Provider Binding Plan | Provider binding | Implemented |
| L8 Render Execution Graph | Volatile execution steps | Implemented |
| L9 ProductRuntime / ProductDependency | Product lifecycle and lineage | Implemented |

## 4. Current Platform Graph Inventory Related to Timeline Versioning

| Graph | DAG Type | Versioned | Content-address candidate | Merge candidate |
|-------|----------|-----------|--------------------------|----------------|
| Timeline semantic graph | Semantic | Yes (TimelineRevision) | Yes (revision hash) | Yes |
| TemplateDefinition graph | Semantic | Yes (TemplateVersion) | Yes | Yes |
| WorkflowDefinition graph | Workflow DAG | Yes (WorkflowVersion) | Yes | Yes |
| Composite Template graph | Composition | Yes (future) | Yes | Yes |
| Artifact Dependency Graph | Dependency | No (volatile) | Yes (graph hash) | No |
| ProductDependency lineage | Lineage | No | Yes | No |
| Cache identity graph | Merkle | No | Yes | N/A |

## 5. Timeline Versioning Problem Statement

The platform needs a timeline diff/merge model that spans:

1. **OTIO exchange changes** — external format differences
2. **Canonical timeline semantic changes** — track/clip/caption style changes
3. **Template-applied changes** — TemplateApplication intent differences
4. **Composite template expansion** — child template composition changes
5. **Workflow step changes** — APPLY_TEMPLATE step modifications
6. **Artifact DAG impact** — which artifacts must re-render
7. **Render cache impact** — which outputs can be reused
8. **Product lineage impact** — what provenance to record

OTIO diff alone is not sufficient. The platform must own its Canonical Timeline Diff.

## 6. Reference System: vedit

**Source:** https://github.com/explicit09/vedit
**Primary language:** C/C++
**License:** Requires verification (check repository)
**Maturity:** Experimental/research

**Core concepts:**
- Version control for video editing timelines
- OTIO-centric timeline representation
- DAG-based version history
- Diff and merge operations
- Branching and snapshots
- Content-addressed object storage
- AI-agent edit history tracking

**Architecture:**
- Object store for timeline snapshots
- Commit graph linking versions
- Diff engine comparing OTIO timelines
- Merge engine combining concurrent edits
- CLI interface

**Relevance to media-platform:**
- Timeline versioning mental model
- OTIO diff engine
- Commit DAG for timeline history
- Content-addressed storage concept
- AI-agent edit tracking

**Limitations:**
- Experimental maturity
- C/C++ runtime (Java integration challenge)
- OTIO-centric (platform needs broader Canonical Timeline Diff)
- No Template/Workflow awareness
- No ProductRuntime/StorageRuntime awareness
- No provider-neutral architecture

**Decision:** Reference design and POC candidate. Not production dependency.

## 7. Reference System: pyvedit

**Source:** PyPI package
**Primary language:** Python
**Maturity:** Experimental

**Core concepts:**
- Python bindings for vedit functionality
- Diff/merge API for OTIO timelines
- Offline evaluation capability

**Relevance:**
- Useful for offline POC and benchmark
- Not suitable for Java/Spring production integration
- Subprocess coupling risk

**Decision:** POC/benchmark tool only. Not production dependency.

## 8. Reference System: OpenTimelineIO

**Source:** https://opentimeline.io/
**Maturity:** Production-grade (Pixar-backed)

**Core concepts:**
- Interchange format for editorial timelines
- JSON-based serialization
- Track/clip/transition/effects model
- Plugin architecture for format conversion

**Relevance:**
- Exchange format, not the only canonical model
- Platform uses OTIO as input, not sole representation
- OTIO diff is input signal, not complete Canonical Timeline Diff

**Decision:** Continue as exchange format. OTIO diff adapter is future work.

## 9. Reference System: Git Commit DAG

**Source:** Git documentation, Pro Git
**Maturity:** Production-grade

**Core concepts:**
- Immutable commit objects linked by parent hash
- Content-addressable SHA-256 storage
- Branch/merge/revert/rebase
- Three-way merge
- Merge base detection

**Relevance to platform:**
- TimelineRevision version graph
- TemplateVersion/WorkflowVersion graphs
- Commit-like semantics for version history

**Decision:** Adopt Git-like mental model for versioning. Do not replace Git.

## 10. Reference System: Merkle-DAG / Content Addressing

**Source:** IPFS/IPLD documentation
**Maturity:** Production concepts

**Core concepts:**
- Hash-linked DAG nodes
- Content-addressed identity
- Deduplication via content hash
- Causal history

**Relevance:**
- Cache identity for artifact reuse
- Stable artifact lineage keys
- Content-addressed ProductDependency

**Decision:** Adopt as conceptual reference for cache identity and lineage. No IPFS runtime.

## 11. Reference System: Merkle-CRDT / Local-first DAG Sync

**Source:** Academic papers, research projects
**Maturity:** Research

**Core concepts:**
- CRDT merge for concurrent DAG edits
- Causal ordering
- Conflict-free replication

**Relevance:**
- Concurrent timeline/template editing
- AI-agent collaborative edits
- Local-first editor state

**Decision:** Adopt as future design reference. No runtime now.

## 12. vedit Capability Matrix

| Capability | vedit | media-platform needs |
|-----------|-------|---------------------|
| OTIO timeline diff | ✅ | ✅ (as input) |
| Commit DAG | ✅ | ✅ (conceptual) |
| Content-addressed store | ✅ | ✅ (conceptual) |
| Branch/merge | ✅ | ✅ (future) |
| Three-way merge | Unclear | ✅ (future) |
| Template-aware diff | ❌ | ✅ (required) |
| Workflow-aware diff | ❌ | ✅ (required) |
| Artifact DAG impact | ❌ | ⏸ indefinitely deferred extension only; not required for current Timeline Git, render planning, Product API, OpenCue, or roadmap work |
| Product lineage impact | ❌ | ✅ (required) |
| Provider-neutral model | ❌ | ✅ (required) |
| Java/Spring integration | ❌ | ✅ (required) |
| Production maturity | ❌ | ✅ (required) |

> **Clarification:** The "Artifact DAG impact" row describes future/platform-extension potential only. Artifact DAG is indefinitely deferred and is not a current roadmap dependency. See [ADR-025](../adr/ADR-025-artifact-dag-indefinite-deferral.md).

## 13. vedit Maturity and Risk Assessment

| Risk | Level | Notes |
|------|-------|-------|
| Maturity | High | Experimental project |
| License | Unknown | Requires verification |
| Runtime | High | C/C++ — Java integration challenge |
| Maintenance | Unknown | Single maintainer risk |
| Security | Medium | Content-addressed store needs audit |
| API stability | High | No stable API guarantees |

## 14. vedit Integration Options

| Option | Description | Recommendation |
|--------|-------------|---------------|
| A: Direct dependency | Import vedit as library | ❌ Not recommended |
| B: Fork as core | Fork and maintain | ❌ Not now |
| C: Sidecar service | External service wrapper | ❌ Not now |
| D: Offline POC/benchmark | Use pyvedit for evaluation | ✅ Recommended |
| E: Reference design only | Adopt concepts, own implementation | ✅ Recommended |

## 15. Comparison: vedit vs media-platform Canonical Timeline Needs

media-platform needs a Canonical Timeline Diff that accounts for:
- Track/clip structural changes
- Caption text and style changes
- Watermark and overlay changes
- Template application parameter changes
- Composite template child changes
- Workflow step modifications
- Artifact DAG re-render impact
- Render cache invalidation
- Product lineage provenance

vedit provides OTIO-level diff but none of the Template/Workflow/Artifact/Product layers.

## 16. OTIO Diff vs Canonical Timeline Diff

| Aspect | OTIO Diff | Canonical Timeline Diff |
|--------|-----------|----------------------|
| Scope | Exchange format only | Full platform model |
| Tracks | ✅ | ✅ |
| Clips | ✅ | ✅ |
| Styles | Partial | ✅ |
| Templates | ❌ | ✅ |
| Workflows | ❌ | ✅ |
| Artifact impact | ❌ | ✅ |
| Cache impact | ❌ | ✅ |
| Lineage impact | ❌ | ✅ |

## 17. TemplateApplication Diff

TemplateApplication changes include:
- Template ID/version changes
- Target role changes
- Parameter value changes
- New/removed operations

## 18. CompositeTemplate Diff

CompositeTemplate changes include:
- Child template additions/removals
- Target binding changes
- Parameter binding changes
- Merge policy changes

## 19. WorkflowStep(APPLY_TEMPLATE) Diff

Workflow step changes include:
- Step additions/removals
- Dependency changes
- Template spec changes
- Parameter overrides

## 20. Artifact DAG / Render Impact Diff

Timeline changes may invalidate:
- Specific artifacts in Artifact Dependency Graph
- Specific capability nodes
- Specific provider binding decisions
- Specific render execution steps

## 21. ProductDependency Lineage Impact

Timeline changes may affect:
- Output Product validity
- ProductDependency links
- Cache key stability
- Incremental render eligibility

## 22. Recommended Adoption

1. vedit concepts as reference design
2. Git-like version semantics for TimelineRevision
3. Merkle-DAG for cache identity
4. OTIO as exchange input

## 23. Recommended Rejections

1. vedit as production dependency
2. vedit fork as core
3. pyvedit as production runtime
4. IPFS/IPLD runtime
5. Merkle-CRDT runtime
6. OTIO diff as complete Canonical Timeline Diff

## 24. Future POC Plan Summary

Future P2V.0 POC:
- Evaluate vedit diff/merge on sample OTIO timelines
- Map vedit outputs to Canonical Timeline Diff categories
- Benchmark diff performance
- Document gaps and adaptation requirements

## 25. Future ADR Candidates

| Candidate | Trigger |
|-----------|---------|
| Canonical Timeline Diff and Patch Model | When timeline versioning is needed |
| OTIO Diff Adapter Boundary | When OTIO diff integration is needed |
| TemplateApplication Diff Semantics | P2T.4 implementation |
| Timeline Merge Conflict Taxonomy | When concurrent editing is needed |
| Content-addressed Timeline Semantic Hash | When cache identity is needed |
| Render Impact Analysis for Timeline Diffs | When incremental render is needed |

## 26. Source List

| Name | URL | Source Type | Maturity |
|------|-----|-----------|----------|
| explicit09/vedit | https://github.com/explicit09/vedit | GitHub | Experimental |
| pyvedit | PyPI | Package page | Experimental |
| OpenTimelineIO | https://opentimeline.io/ | Official docs | Production |
| Git | https://git-scm.com/doc | Official docs | Production |
| IPFS/IPLD | https://docs.ipfs.tech/ | Official docs | Production concepts |
| Merkle-CRDT | Academic papers | Research | Research |

**Note:** vedit repository URL and details require verification. License, maturity, and API details should be confirmed before any POC work.
