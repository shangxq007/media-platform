# Reference & Blueprint Document Placement Audit

**Date:** 2026-06-23  
**Scope:** Check existing documents for capacity to host Timeline Git, reference landscape, OTIO, BMF, version control, and related content  
**Constraint:** No new documents, no code changes, no archival, no deletion

---

## 1. Existing Candidate Documents

### 1.1 `docs/architecture/blueprint/reference-architecture-map.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 300 |
| **Current purpose** | Maps 10 external reference projects (n8n, Temporal, LiteFlow, Dagster, Airflow, Prefect, ShotGrid, Opencast, OpenCue, NLE tools) to platform layers |
| **Sections** | Purpose, Reference Categories, 10 reference project sections, Summary Table, Key Principles, Current Status |
| **Covers vedit/Vit?** | No |
| **Covers BMF?** | No |
| **Covers OTIO?** | No (only mentions "NLE tools" generically) |
| **Covers Timeline Git?** | No |
| **Suitable for reference landscape?** | **Yes** — designed for exactly this purpose. Well-structured with "Borrowed Ideas" / "Explicitly Not Borrowed" tables per reference |
| **Should update?** | **Yes** — add BMF, OTIO, vedit/Vit as reference projects |

### 1.2 `docs/architecture/blueprint/otio-render-platform-blueprint.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 1,263 |
| **Current purpose** | OTIO-first semantic video rendering platform blueprint with 14 sections |
| **Covers Timeline IR?** | Yes — Section 2 (Core Architecture), Section 3 (Graph Model) |
| **Covers Artifact Dependency Graph?** | Yes — Section 3, Section 6 |
| **Covers BMF?** | Yes — Section 8 (BMF Integration Strategy) |
| **Covers Timeline Git?** | No |
| **Covers version control/diff/merge?** | No (mentions "semantic diff" for incremental rendering, not timeline version control) |
| **Covers AI proposal review?** | Partially — Section 9 (LLM Role) mentions edit intent but not proposal review UI |
| **Suitable for Timeline Git?** | **Partially** — could add a section, but document is already 1,263 lines |
| **Should update?** | **Yes, minimally** — add cross-references to existing Timeline version control docs |

### 1.3 `docs/timeline-model.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 123 |
| **Current purpose** | Java-side canonical timeline model (TimelineSpec, TimelineTrack, TimelineClip) |
| **Covers Timeline Git?** | No |
| **Covers diff/merge/patch?** | No |
| **Suitable for Timeline Git?** | **Partially** — could add version control section, but it's a data structure doc |
| **Should update?** | **Low priority** — data structure doc, not workflow doc |

### 1.4 `docs/frontend/timeline-model.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 325 |
| **Current purpose** | Frontend TypeScript timeline model interfaces |
| **Covers Timeline Git?** | No |
| **Suitable for Timeline Git?** | **No** — frontend type definitions |
| **Should update?** | **No** |

### 1.5 `docs/zh/timeline-version-control.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | ~250 (9,730 bytes) |
| **Current purpose** | Chinese doc covering timeline domain version control: revision chain, conflict resolution, History panel, patch preview |
| **Covers Timeline Git?** | Yes — revision chain, conflict resolution, patch preview |
| **Covers diff/merge/patch?** | Yes — patch preview, conflict resolution, three-way merge |
| **Covers AI proposal review?** | Yes — AI adoption writes to `timeline_revision` |
| **English equivalent?** | **Does not exist** — `docs/timeline-version-control.md` is missing |
| **Should create English version?** | **Yes** — referenced from 6+ Chinese docs, no English counterpart |

### 1.6 `docs/future-roadmap-otio-llm.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | ~500 |
| **Current purpose** | Chinese doc: intelligent rendering + natural language editing roadmap |
| **Covers Timeline Git?** | Mentions "timeline versioning / diff / rollback" as P4-LLM-7 |
| **Covers AI proposals?** | Yes — LLM edit workflow, multi-preview |
| **Covers BMF?** | Yes — BMF spike |
| **Suitable for reference landscape?** | **No** — roadmap format, not landscape |
| **Should update?** | **No** — roadmap, not reference |

### 1.7 `docs/roadmap/render-pipeline-roadmap.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 99 |
| **Current purpose** | Phase-based checklist for render pipeline improvements |
| **Covers Timeline Git?** | No |
| **Covers BMF?** | No (mentions multi-provider but not BMF specifically) |
| **Should update?** | **Low priority** — forward-looking checklist |

### 1.8 `docs/roadmap/ai-provider-ecosystem-roadmap.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 698 |
| **Current purpose** | AI provider ecosystem roadmap with current-state tables |
| **Covers ASR?** | Yes (mentions Whisper) |
| **Covers LLM proposals?** | Yes |
| **Should update?** | **No** — already comprehensive |

### 1.9 `docs/render/adr/ADR-001` through `ADR-007`

| Attribute | Value |
|-----------|-------|
| **Current purpose** | Accepted ADRs for render provider decisions |
| **Covers BMF?** | ADR-006: BMF spike-only decision |
| **Covers OTIO?** | No |
| **Should update?** | **No** — ADRs are immutable after acceptance |

### 1.10 `docs/governance/agent-knowledge-policy.md`

| Attribute | Value |
|-----------|-------|
| **Current purpose** | Agent knowledge loading tiers and rules |
| **Covers reference landscape?** | No — consumer of docs, not host |
| **Should update?** | **Only if new docs are created** — add to appropriate tier |

### 1.11 `docs/review/project-intelligence-report.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 605 |
| **Current purpose** | Comprehensive code-fact-based project analysis |
| **Covers reference projects?** | Mentions Temporal, LiteFlow, PF4J, OpenFeature |
| **Covers Timeline Git?** | No |
| **Should update?** | **No** — point-in-time analysis report |

### 1.12 `docs/render/overview.md`

| Attribute | Value |
|-----------|-------|
| **Lines** | 133 |
| **Current purpose** | Render provider system design overview (Chinese) |
| **Covers provider categories?** | Yes — 10 categories |
| **Covers BMF?** | Yes — BMF as spike |
| **Should update?** | **No** — design overview, not landscape |

---

## 2. Recommended Placement

### 2.1 Should `otio-render-platform-blueprint.md` be updated?

**Yes, minimally.** Add a cross-reference section pointing to:
- `docs/zh/timeline-version-control.md` (and future English version)
- `docs/timeline-model.md`
- `docs/future-roadmap-otio-llm.md`

**Do NOT add** Timeline Git, reference landscape, or version control content to this document. It is already 1,263 lines and focused on rendering architecture.

### 2.2 Should `reference-architecture-map.md` be updated?

**Yes.** This is the canonical location for reference project mapping. Add:

| New Reference | Category | Borrowed Ideas |
|--------------|----------|---------------|
| **OpenTimelineIO** | Timeline interchange | OTIO as exchange format, adapter pattern, metadata schema |
| **BMF (BabitMF)** | Media pipeline engine | Graph-based media processing, GPU acceleration, C++ node API |
| **vedit / Vit** | Git-like video versioning | Commit-based timeline versioning, branch/merge semantics, diff visualization |

**Do NOT add** Timeline Git implementation details — that belongs in a design doc, not a reference map.

### 2.3 Should `docs/render/adr/*` be updated?

**No.** ADRs are immutable after acceptance. New ADRs (ADR-008, ADR-009, ADR-010) are already planned in the OTIO blueprint milestones. Do not modify existing ADRs.

### 2.4 Should `docs/governance/agent-knowledge-policy.md` be updated?

**Only if new documents are created.** If a new reference landscape doc or Timeline Git design doc is created, add it to the appropriate knowledge tier. No update needed if content goes into existing docs.

### 2.5 Is a new `reference-landscape.md` needed?

**No.** `docs/architecture/blueprint/reference-architecture-map.md` already serves this purpose. Adding BMF, OTIO, and vedit/Vit to the existing map is the correct approach.

---

## 3. Add vs Update Decision

### Timeline Git / Git for Video

| Option | Decision | Reason |
|--------|----------|--------|
| Update `otio-render-platform-blueprint.md` | **Do not add** | Already 1,263 lines; rendering-focused, not version-control-focused |
| Update `reference-architecture-map.md` | **Add vedit/Vit as reference** | Reference map is the right place for "what we borrow from" |
| Update `docs/timeline-model.md` | **Do not add** | Data structure doc, not workflow doc |
| Add new `docs/render/timeline-git-design.md` | **Not now** | Premature — design not yet started; add when M1/M2 milestones begin |
| Create English `docs/timeline-version-control.md` | **Recommended** | Chinese version exists with no English counterpart |

### Timeline Snapshot / Patch / Diff / Merge / Conflict

| Option | Decision | Reason |
|--------|----------|--------|
| Update `docs/timeline-model.md` | **Do not add** | Data structure doc |
| Update `otio-render-platform-blueprint.md` | **Do not add** | Already has semantic diff for incremental rendering |
| Reference `docs/zh/timeline-version-control.md` | **Already exists** | Covers revision chain, conflict resolution, patch preview |
| Create English version | **Recommended** | 6+ Chinese docs reference it; no English counterpart |

### AI Proposal Review

| Option | Decision | Reason |
|--------|----------|--------|
| Update `otio-render-platform-blueprint.md` | **Do not add** | Section 9 (LLM Role) already covers edit intent boundaries |
| Update `docs/roadmap/ai-provider-ecosystem-roadmap.md` | **Do not add** | Already covers AI provider ecosystem |
| Reference `docs/zh/timeline-version-control.md` | **Already exists** | Covers AI adoption writing to `timeline_revision` |
| Create English version | **Recommended** | Part of timeline-version-control English translation |

### Timeline Metadata Versioning

| Option | Decision | Reason |
|--------|----------|--------|
| Update `docs/timeline-model.md` | **Do not add** | Data structure doc |
| Update `otio-render-platform-blueprint.md` | **Do not add** | Section 6 (Cache) covers metadata hashing |
| Reference existing code | **Already exists** | `TimelineRevisionService`, `TimelineRevisionRepository`, `TimelineContentHasher` |

### Reference Projects (vedit / Vit / OTIO / BMF)

| Option | Decision | Reason |
|--------|----------|--------|
| Update `reference-architecture-map.md` | **Yes — add 3 new references** | Canonical location for reference project mapping |
| Add to `otio-render-platform-blueprint.md` | **No** | Blueprint already references OTIO; BMF has Section 8 |
| Add to `docs/render/overview.md` | **No** | Design overview, not landscape |

### Reference Landscape / Reference Project Tracking

| Option | Decision | Reason |
|--------|----------|--------|
| Update `reference-architecture-map.md` | **Yes — add vedit/Vit, BMF, OTIO** | Already designed for this purpose |
| Add new `reference-landscape.md` | **No** | `reference-architecture-map.md` already exists |
| Update `docs/README.md` | **Only if new sections added** | Add link if reference map gets new sections |

### OTIO-first Render Blueprint Priority Adjustment

| Option | Decision | Reason |
|--------|----------|--------|
| Update `otio-render-platform-blueprint.md` | **No** | Blueprint already has 6 milestones (M0-M6) with priority order |
| Update `docs/roadmap/render-pipeline-roadmap.md` | **No** | Roadmap is a checklist, not a priority adjustment doc |
| Update `docs/README.md` | **Yes — add OTIO blueprint link** | Add to Blueprint Documents section |

---

## 4. Specific Patch Plan

### Patch 1: Update `reference-architecture-map.md`

**File:** `docs/architecture/blueprint/reference-architecture-map.md`  
**New section:** `## 11. OpenTimelineIO` (after section 10)

**Core content:**
```
| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| OTIO as exchange format | Universal timeline interchange | OTIO Exchange Layer (L1) |
| Adapter pattern | Import/export adapters for AAF, EDL, FCP XML | Timeline standards adapters |
| Custom metadata schema | Platform-specific annotations in OTIO metadata | TimelinePlatformMetadata |
| Bidirectional round-trip | Import → internal IR → export back to OTIO | OpenTimelineioAdapter |
```

**Should NOT write:** Implementation details, graph model, cache strategy (those belong in the blueprint)

### Patch 2: Update `reference-architecture-map.md`

**File:** `docs/architecture/blueprint/reference-architecture-map.md`  
**New section:** `## 12. BMF (BabitMF)` (after section 11)

**Core content:**
```
| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Graph-based media processing | DAG of processing nodes | Artifact Dependency Graph (L4) |
| GPU acceleration | CUDA/OpenCL nodes | BMF execution backend |
| C++ node API | High-performance processing nodes | BMF provider integration |
| Python scripting | Rapid prototyping of processing graphs | BMF spike (M5) |
```

**Should NOT write:** BMF graph JSON schema, CLI executor details (those belong in the blueprint Section 8)

### Patch 3: Update `reference-architecture-map.md`

**File:** `docs/architecture/blueprint/reference-architecture-map.md`  
**New section:** `## 13. Git-like Version Control for Timelines` (after section 12)

**Core content:**
```
Reference projects: vedit, Vit, Git

| Concept | Description | Platform Layer |
|---------|-------------|----------------|
| Commit-based versioning | Timeline snapshots as commits | TimelineRevisionService |
| Branch/merge semantics | Parallel editing paths | (Planned) |
| Diff visualization | Visual timeline diff | TimelineSemanticDiffService |
| Conflict resolution | Three-way merge for timelines | TimelineConflictDialog |
| Patch-based editing | RFC6902 JSON patches | TimelinePatchService |
```

**Should NOT write:** Implementation details (those belong in `timeline-version-control.md`)

### Patch 4: Update `docs/README.md`

**File:** `docs/README.md`  
**New line in Blueprint Documents section:**

```
| [OTIO Render Platform](architecture/blueprint/otio-render-platform-blueprint.md) | OTIO-first semantic rendering target |
```

**Should NOT write:** Full OTIO architecture description

### Patch 5: Cross-reference in `otio-render-platform-blueprint.md`

**File:** `docs/architecture/blueprint/otio-render-platform-blueprint.md`  
**New subsection at end of Section 2 (Core Architecture):**

```markdown
### Related Documents

- Timeline domain version control: `docs/zh/timeline-version-control.md` (English version planned)
- Timeline data model: `docs/timeline-model.md`
- Frontend timeline model: `docs/frontend/timeline-model.md`
- AI-powered editing roadmap: `docs/future-roadmap-otio-llm.md`
- Reference architecture: `docs/architecture/blueprint/reference-architecture-map.md`
```

**Should NOT write:** Timeline Git design, version control implementation details

---

## 5. Final Recommendation

### Is a new document necessary?

**No.** All content categories can be hosted by existing documents:

| Content | Host Document | Action |
|---------|--------------|--------|
| Reference projects (vedit, Vit, BMF, OTIO) | `reference-architecture-map.md` | Add 3 new sections |
| Timeline Git concept | `reference-architecture-map.md` | Add 1 new section |
| Timeline version control | `docs/zh/timeline-version-control.md` | Create English version (recommended, not required) |
| AI proposal review | Already in `docs/zh/timeline-version-control.md` | Part of English translation |
| OTIO blueprint cross-refs | `otio-render-platform-blueprint.md` | Add Related Documents subsection |
| OTIO blueprint in navigation | `docs/README.md` | Add link to Blueprint section |

### If a new document were to be created in the future

**Recommended path:** `docs/render/timeline-version-control.md` (English translation of `docs/zh/timeline-version-control.md`)

**When:** Before M1 milestone begins (Timeline IR enhancement)

**Why:** The Chinese version covers revision chains, conflict resolution, patch preview, and AI proposal integration — all needed for the OTIO blueprint's semantic editing layer.

### Minimum action to take now

1. Add 3 reference sections to `reference-architecture-map.md` (BMF, OTIO, vedit/Vit)
2. Add Related Documents cross-references to `otio-render-platform-blueprint.md`
3. Add OTIO blueprint link to `docs/README.md`

**Total effort:** 30 minutes  
**Risk:** None — additive changes to existing documents
