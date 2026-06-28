# Timeline Git / vedit POC Plan (P2R.1)

## 1. Purpose

Future POC to evaluate vedit's diff/merge capabilities against media-platform timeline needs. This plan is documentation only — not executed now.

## 2. POC Non-goals

- Production integration
- Java runtime integration
- Forking vedit
- Real tenant data
- Real media files
- Real render execution

## 3. POC Safety Rules

- POC only, not production
- Synthetic timelines only
- No secrets or tenant data
- No network dependencies
- No package installation into repository
- Subprocess isolation if pyvedit is used

## 4. Candidate Test Timelines

1. Single video clip, single track
2. Two sequential clips
3. Single clip + caption overlay
4. Single clip + watermark overlay
5. Multi-track (video + audio)
6. Template-generated timeline

## 5. Candidate OTIO Inputs

1. OTIO JSON from CaptionTemplateTimelineAdapter output
2. OTIO JSON from WatermarkTemplate profile output
3. Manually crafted OTIO for edge cases

## 6. vedit Evaluation Questions

1. Can vedit diff two OTIO timelines?
2. What diff granularity does it provide?
3. Can it detect clip trim changes?
4. Can it detect clip reorder?
5. Can it detect caption text changes?
6. Can it detect overlay additions/removals?
7. Can it branch and merge non-conflicting changes?
8. Can it detect and report conflicts?
9. What conflict resolution does it offer?
10. Can its object store be inspected safely?

## 7. Expected POC Outputs

1. vedit capability report
2. Sample OTIO fixtures
3. Sample diff outputs
4. Mapping table: vedit diff → CanonicalTimelineDiff categories
5. Gap list: what vedit cannot do that platform needs
6. Recommendation: continue adapter work or focus on platform-owned diff

## 8. Adapter Boundary

```
vedit CLI/subprocess
    ↓ OTIO input
vedit diff output
    ↓ mapper
CanonicalTimelineDiff (platform-owned)
    ↓ analyzer
TimelineRenderImpact + ArtifactDAGImpact
```

## 9. Benchmark / Oracle Role

vedit can serve as:
- Oracle for OTIO-level diff correctness
- Benchmark for diff/merge performance
- Reference for conflict detection heuristics

## 10. What Would Qualify vedit for Further Consideration

- Reliable OTIO diff/merge
- Clean subprocess interface
- Active maintenance
- Compatible license
- Diff output maps cleanly to platform categories

## 11. What Would Disqualify vedit

- Unreliable diff output
- Incompatible license
- Abandoned project
- No clean CLI interface
- Diff output cannot map to platform categories

## 12. Commands Not To Run in Production

- `vedit diff` (not installed, not production dependency)
- `vedit merge` (not installed, not production dependency)
- `pyvedit` subprocess (not production dependency)
- `npm install vedit` (not applicable)

## 13. Future P2V.0 Task Proposal

**P2V.0: Timeline Diff Domain Skeleton**

- Define platform-owned TimelineDiff, TimelinePatch, TimelineChangeOperation
- Define diff categories (track/clip/style/template/workflow/artifact/lineage)
- Define merge conflict taxonomy
- Define render impact analysis interface
- Do not implement diff algorithm yet
- Do not add vedit dependency
