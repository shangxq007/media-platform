# Timeline Git / vedit Evaluation Report (P2R.1)

## 1. Summary

Reviewed explicit09/vedit, pyvedit, and related timeline versioning systems against media-platform needs. vedit is a reference/POC candidate, not a production dependency. Canonical Timeline Diff must be platform-owned.

## 2. Sources Reviewed

| Source | Type | Status |
|--------|------|--------|
| explicit09/vedit | GitHub | Requires verification |
| pyvedit | PyPI | Requires verification |
| OpenTimelineIO | Official docs | Verified |
| Git/Pro Git | Official docs | Verified |
| IPFS/IPLD | Official docs | Verified |
| Merkle-CRDT | Academic papers | Verified |

## 3. vedit Overview

- **Purpose:** Version control for video editing timelines
- **Language:** C/C++
- **OTIO relationship:** OTIO-centric
- **Capabilities:** Diff, branch, snapshot, commit graph, object store
- **Maturity:** Experimental
- **License:** Requires verification

## 4. pyvedit Overview

- **Purpose:** Python bindings for vedit
- **Use case:** Offline POC and benchmark
- **Production use:** Not recommended (subprocess coupling, Python runtime)

## 5. Fit for Direct Adoption

**NO.** Experimental maturity, C/C++ runtime, no Java integration, no Template/Workflow awareness.

## 6. Fit for Forking

**NO for now.** Forking would require C/C++ maintenance, OTIO-only model adaptation, and Template/Workflow extension. Not justified until POC validates concepts.

## 7. Fit for Adapter / POC

**YES.** vedit can serve as:
- Offline diff/merge benchmark
- OTIO diff reference implementation
- POC for evaluating timeline diff semantics

## 8. Fit as Design Reference

**YES.** Concepts to adopt:
- Commit DAG for timeline versioning
- Content-addressed object identity
- Diff/merge semantics framework
- AI-agent edit history concept

## 9. Comparison with media-platform Timeline Needs

| Need | vedit | Gap |
|------|-------|-----|
| OTIO diff | ✅ | None |
| Commit DAG | ✅ | None (conceptual) |
| Template-aware diff | ❌ | Must build |
| Workflow-aware diff | ❌ | Must build |
| Artifact DAG impact | ❌ | Must build |
| Cache impact analysis | ❌ | Must build |
| Product lineage impact | ❌ | Must build |
| Java/Spring integration | ❌ | Must build |

## 10. Required Platform-owned Design Areas

1. CanonicalTimelineDiff — platform-owned diff model
2. TimelinePatch — platform-owned patch model
3. TemplateApplicationDiff — template-aware diff
4. CompositeTemplateDiff — composite template diff
5. WorkflowApplyTemplateStepDiff — workflow step diff
6. ArtifactDAGImpact — render cache impact
7. ProductLineageImpact — lineage provenance

## 11. Recommended Decision

- **Direct production dependency:** NO
- **Fork as core:** NO for now
- **Adapter/POC:** YES
- **Reference design:** YES
- **Platform-owned Canonical Timeline Diff:** YES (future work)

## 12. Known Unknowns

1. vedit license — requires verification
2. vedit merge conflict resolution model — requires investigation
3. pyvedit API stability — requires verification
4. vedit content-addressed store security — requires audit
5. OTIO diff completeness for media timelines — requires validation

## 13. Files Changed

| File | Type |
|------|------|
| `docs/review/timeline-git-vedit-evaluation-v0.md` | NEW |

## 14. Final Recommendation

Use vedit as POC/benchmark and design reference. Build platform-owned Canonical Timeline Diff. No runtime dependency.
