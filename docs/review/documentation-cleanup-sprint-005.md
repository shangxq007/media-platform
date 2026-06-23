# Documentation Cleanup Sprint 005

**Date:** 2026-06-23  
**Scope:** Blueprint realignment, reference consolidation, strategic priority adjustment  
**Constraint:** No new blueprints, no new reference documents, no ADR modifications, no code changes

---

## Executive Summary

This sprint realigns the documentation architecture around a new strategic framing:

**Previous:** Multi-provider Render Platform (execution-first, provider-centric)  
**New:** Semantic Timeline Platform (editing-first, timeline-centric)

The shift recognizes that the platform's core product value is **Timeline Git** — the ability to snapshot, patch, diff, merge, and review timeline edits (including AI-generated proposals). Artifact-aware rendering and multi-provider execution are important but secondary optimization layers.

---

## Changes

### 1. `docs/architecture/blueprint/reference-architecture-map.md`

**Added 3 new sections:**

| Section | Content |
|---------|---------|
| **11. Timeline Versioning References** | OpenTimelineIO (interchange, editorial semantics, adapter architecture), vedit (snapshots, branch, diff, merge, AI agent workflow), Vit (Git-backed metadata, collaboration, serialization) |
| **12. Execution References** | BMF (media graph runtime, AI inference, GPU execution, hybrid processing), Temporal (workflow orchestration, retry, recovery, long-running execution) |
| **13. What We Intentionally Reject** | 5 rejected patterns: Timeline=ExecutionGraph, provider-specific timeline, LLM-generated commands, Artifact DAG as editing truth, provider-owned project models |

Each reference includes "Borrowed Ideas" and "Explicitly Not Borrowed" tables.

### 2. `docs/architecture/blueprint/otio-render-platform-blueprint.md`

**Added 3 new sections:**

| Section | Content |
|---------|---------|
| **15. Related Documents** | Cross-references to 9 related documents (reference map, timeline version control, roadmaps, timeline models, ADRs) |
| **16. Strategic Priority Adjustment** | 8-priority ordering: Timeline IR → Snapshot → Patch → Diff → Merge → AI Proposal → Artifact DAG → Multi-provider Execution. Rationale for Timeline Git as Phase 1 product value. |
| **17. Timeline Git Positioning** | Three sources of truth (Timeline IR, Artifact DAG, Execution Graph). Timeline Git concepts table. AI Proposal = Timeline Patch Proposal. 5 key invariants. |

### 3. `docs/README.md`

**Updated Blueprint Documents section:**
- Added OTIO Render Platform Blueprint as **Primary blueprint**
- Added Reference Architecture Map to blueprint list
- Added **Recommended Reading Order** (9-step sequence from AGENTS.md to Known Limitations)

---

## Strategic Shift

```
Previous:  Multi-provider Render Platform
           (execution-first, provider-centric)
           Priority: Provider Registry → Capability → Execution → Timeline

New:       Semantic Timeline Platform
           → Timeline Git (editing-first, timeline-centric)
           → AI Proposal Review (intelligence layer)
           → Artifact-aware Rendering (execution optimization)
           → Multi-provider Execution (provider flexibility)
           Priority: Timeline IR → Snapshot → Patch → Diff → Merge → AI Proposal → Artifact DAG → Execution
```

### Why the Shift

1. **Users care about editing, not rendering.** The editor is the product; the renderer is the engine.
2. **Timeline Git is the differentiator.** No other video platform offers Git-like version control for timelines.
3. **AI Proposal Review requires Timeline Git.** LLM-generated edits are timeline patches — they need diff, review, and merge infrastructure.
4. **Artifact DAG is an optimization.** It enables cache-aware rendering and multi-provider flexibility, but it's not the product.
5. **Multi-provider execution is a capability.** It enables cost optimization and engine flexibility, but users don't choose a video editor because it supports 7 render backends.

---

## Outcome

| Question | Answer |
|----------|--------|
| Does Timeline Git become Phase 1 mainline? | **Yes.** Timeline IR → Snapshot → Patch → Diff → Merge → AI Proposal Review is the primary development track. |
| Does Artifact DAG get demoted to optimization layer? | **Yes.** Artifact Dependency Graph is Phase 2 — important for cache-aware rendering but not the product value. |
| Does Temporal belong to execution layer references? | **Yes.** Temporal is an execution reference (workflow orchestration), not a timeline reference. |
| Does BMF belong to execution backend references? | **Yes.** BMF is an execution reference (media graph runtime, GPU), not a timeline reference. |

---

## Verification

| Check | Status |
|-------|--------|
| No new blueprint created | ✅ Only modified existing `reference-architecture-map.md` and `otio-render-platform-blueprint.md` |
| No new reference document created | ✅ No `reference-landscape.md` or similar |
| No ADR modified | ✅ ADR-001 through ADR-007 unchanged |
| No code modified | ✅ Only documentation files changed |
| Only 4 files modified | ✅ `reference-architecture-map.md`, `otio-render-platform-blueprint.md`, `docs/README.md`, this report |

---

## Documentation Health Score

| Dimension | Before Sprint 005 | After Sprint 005 |
|-----------|-------------------|------------------|
| **Documentation Architecture Score** | 8.5/10 | **9/10** |
| **Knowledge Management Score** | 8/10 | **8.5/10** |
| **Agent Readiness Score** | 9/10 | **9/10** |

### Improvement Rationale

- **Architecture (8.5→9):** Reference map now covers all key references (OTIO, BMF, vedit, Vit, Temporal) with explicit borrow/reject decisions. Blueprint now has strategic priority alignment and Timeline Git positioning.
- **Knowledge Management (8→8.5):** Reading order established. Three sources of truth clearly defined. Intentional rejections documented.
- **Agent Readiness (9→9):** Maintained — agents now have clearer strategic context but the tier system is unchanged.

---

## Files Modified

| # | File | Lines Added | Lines Changed |
|---|------|------------|---------------|
| 1 | `docs/architecture/blueprint/reference-architecture-map.md` | ~100 | 0 |
| 2 | `docs/architecture/blueprint/otio-render-platform-blueprint.md` | ~110 | 0 |
| 3 | `docs/README.md` | ~20 | 5 |
| 4 | `docs/review/documentation-cleanup-sprint-005.md` | ~130 | 0 |
