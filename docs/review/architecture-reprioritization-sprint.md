---
status: strategy-decision
created: 2026-06-24
scope: platform-wide
truth_level: confirmed
owner: platform
---

# Architecture Re-Prioritization Sprint

> **Decision Date:** 2026-06-24
> **Context:** Three Timeline Git sprints + Asset Registry Phase 1 + Product Readiness Assessment
> **Method:** Full code audit + API inventory + user journey walkthrough + competitive analysis

---

## 1. Executive Summary

### Has Strategy Changed?

**Yes — productization is now the highest priority.** Three sprints built a solid Timeline Git engine (revision, patch, diff, merge, conflict, resolution — 22 tests passing). The bottleneck is not engineering. It's APIs.

Three REST endpoints would unlock 80% of user value from existing code. Building new engines (branch, rebase, OpenLineage) before exposing existing ones is premature optimization.

### Has Source of Truth Changed?

**No — the four pillars remain. A fifth pillar is forming.**

| Pillar | Domain | Status |
|--------|--------|--------|
| Timeline IR | Editing truth | ✅ Production |
| Timeline Git | Version truth | ✅ Engine complete |
| Asset Registry | Identity truth | ✅ Data layer complete |
| Artifact DAG | Execution truth | ✅ Production |
| **Asset Ecosystem** | **Discovery truth** | **Blueprint only** (NEW) |

### Is Timeline Git the Core Capability?

**Yes.** Timeline Git (history, diff, merge, restore, AI review) is the primary product differentiator. No other video platform has entity-level semantic diff, structured AI proposal review, or Git-like timeline version control. Timeline Git delivers more user value than multi-provider rendering, OpenLineage, or Knowledge Graph combined.

### Is Asset Ecosystem the Fifth Pillar?

**Yes — but in blueprint phase only.** The Asset Registry provides identity and governance. The Asset Ecosystem will provide discovery (search, marketplace, sharing). This is the platform's long-term value: "discover, install, use, and share assets across projects, tenants, and the marketplace."

### What Is the Most Important Direction for the Next Year?

**Productize Timeline Git first. Design Asset Ecosystem second. Defer infrastructure layers.**

```
Now (next 1-2 sprints):   Timeline Git Productization (Merge API, Asset API)
Next (following sprints):  Asset Ingestion Blueprint (Upload, ASR, OCR, Vision)
Later (Q3-Q4 2026):        Asset Search, Marketplace Foundation
Deferred (2027+):           OpenLineage, OpenAssetIO, Knowledge Graph, Cloudflare Worker
```

---

## 2. What Changed

### Before (Original Blueprint Priority)

```
P0: Observability, Outbox, Audit
P1: Identity, Scheduler, Quota
P2: Workflow, Render, AI, Notification, Storage
P3: Commerce, Billing, Entitlement
P4: Extension, Prompt, Policy, Artifact, Sandbox
P5: Infrastructure-as-code, Runbooks, Smoke tests
```

### After (Re-Prioritized)

```
P0: Timeline Git Productization (Merge API, Asset API)        ← NEW #1 priority
P1: Asset Ingestion Blueprint (Upload, ASR, OCR, Vision)      ← NEW #2 priority
P2: Asset Search                                              ← NEW
P3: Marketplace Foundation                                    ← NEW
P4: Branch model                                              ← DEMOTED from P1
P5: Rebase engine                                             ← DEMOTED from P2
Deferred: OpenLineage, OpenAssetIO, Knowledge Graph           ← DEMOTED to 2027+
```

### What Got Demoted

| Capability | Old Priority | New Priority | Rationale |
|-----------|-------------|-------------|-----------|
| **OpenLineage** | P2-3 | Deferred | Internal event stream — zero user-facing value |
| **OpenAssetIO** | P2-3 | Deferred | Integration layer — no value without connected DAM/MAM |
| **Knowledge Graph** | P2-3 | Deferred | Search layer — needs Asset Search foundation first |
| **Branch model** | P1 | P4 | Merge engine works without branches — API gap is higher priority |
| **Rebase** | P2 | P5 | Sequential patch replay — needs branch model first |

### What Got Promoted

| Capability | Old Priority | New Priority | Rationale |
|-----------|-------------|-------------|-----------|
| **Timeline Git Productization** | Implicit | **P0** | Engine complete, no APIs — highest user value gap |
| **Asset Ingestion Blueprint** | Implicit | **P1** | Foundation for search and marketplace |
| **Asset Search** | Not listed | **P2** | Users need to find assets across projects |
| **Marketplace** | Not listed | **P3** | Two-sided marketplace = platform moat |

---

## 3. Key Decisions Recorded

### Decision 1: Productize Before Building

**Decision:** Ship Merge API, Asset version API, Asset governance API before building branch or rebase.

**Rationale:** Three engines exist but have no REST APIs. Users cannot merge, browse asset versions, or query asset governance today. Productization (3-6 days) unlocks more value than building new engines (2-3 weeks).

**Impact:** Sprint 004 is now "Merge API + Asset API" instead of "Branch + Rebase."

### Decision 2: Asset Ecosystem is the Fifth Pillar

**Decision:** The platform has five pillars. Asset Ecosystem (discovery) joins the existing four (editing, versioning, identity, execution).

**Rationale:** Long-term platform value comes from the asset marketplace, not from multi-provider rendering. Rendering is a commodity. A two-sided marketplace connecting asset creators and video producers is a moat.

**Impact:** New blueprint document: `asset-ecosystem-blueprint.md`. New section in reference architecture map: Unity Asset Store, Unreal Marketplace, GitHub Marketplace, Figma Community.

### Decision 3: Defer Infrastructure Layers

**Decision:** OpenLineage, OpenAssetIO, Knowledge Graph, and Cloudflare Worker are deferred to 2027+.

**Rationale:** These are infrastructure layers that deliver zero user-facing value on their own. They enable future capabilities but don't deliver product value today. Build what users need first.

**Impact:** Removed from current roadmap. Preserved as deferred items for future reference.

### Decision 4: Branch is P4, Not P0

**Decision:** Branch model is deferred behind productization and asset search.

**Rationale:** Merge engine works without a branch table. Two editors can merge their revision chains regardless of named branches. Branch = named revision pointer — UX sugar, not capability unlock. Fix merge API first.

**Impact:** Branch model moved from P1 to P4 in all blueprints.

---

## 4. Updated Blueprints

| Document | Changes |
|----------|---------|
| `otio-render-platform-blueprint.md` | Reality Check updated; §19 Strategic Re-Prioritization added; §20 Related Documents updated |
| `timeline-git-blueprint.md` | §16 roadmap re-prioritized (productization first); §20 Productization chapter added; related docs updated |
| `reference-architecture-map.md` | §18 Editorial Version Control added; §19 Asset Ecosystem added (Unity, Unreal, GitHub, Figma references) |
| `asset-ecosystem-blueprint.md` | NEW — fifth pillar strategy, asset types, lifecycle, search, marketplace |
| `current-timeline-git-status.md` | NEW — capability matrix, test coverage, sprint history |
| `README.md` | Updated; new reading order; Asset Ecosystem added to blueprints |

---

## 5. Recommended Next Sprint

### Sprint 004 — Timeline Git Productization

**Goal:** Expose existing engines through REST APIs.

```
Tasks:
  1. Merge REST API        — POST /merge (wrap TimelineMergeService)
  2. Merge with resolutions — POST /merge/resolve (wrap threeWayMergeWithResolutions)
  3. Asset version API     — GET /assets/{id}/versions
  4. Asset governance API  — PUT /assets/{id}/governance
  5. JSON-LD export API    — GET /assets/{id}/jsonld
```

**Effort:** 3-6 days
**Risk:** Low — services exist and are tested. Controllers are thin wrappers.
**Modules:** `platform-app` (controllers only) + `render-module` (no changes needed)

---

## 6. Validation

| Constraint | Status |
|-----------|--------|
| No code changes | ✅ |
| No Flyway changes | ✅ |
| No new modules | ✅ |
| No new ADRs | ✅ |
| Only blueprint + assessment docs | ✅ |
