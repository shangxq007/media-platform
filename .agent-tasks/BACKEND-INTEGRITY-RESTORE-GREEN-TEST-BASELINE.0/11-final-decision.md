# Final Decision

## Task

```text
BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.0
```

## Status

```text
PARTIAL
```

## Decision

```text
TEST_BASELINE_REPAIR_PARTIAL
```

## Summary

### What Was Accomplished

| Fix | Status | Impact |
|-----|--------|--------|
| FFmpeg libx264 | ✅ FIXED | ~14 tests recovered |
| OutboxEventDispatcherTest | ✅ FIXED | 4 tests recovered |
| ProjectImportPreviewServiceTest | ✅ FIXED | 1 test recovered |
| Unauthorized Skill removed | ✅ DONE | Scope contamination closed |
| Architecture docs fixed | ✅ DONE | Consistency restored |

### Remaining Failures

```text
Before: 53 failures (render-module) + platform-app + outbox
After:  39 failures (render-module) + platform-app

FFmpeg fix recovered: ~14 tests
Outbox fix recovered: 4 tests
Identity fix recovered: 1 test
```

### Root Cause Clusters Still Failing

| Cluster | Count | Root Cause |
|---------|------:|------------|
| RenderPipelineE2E | 11 | Pipeline not executing (QUEUED instead of COMPLETED) |
| TimelineRevisionRenderService | 6 | Mock/contract issues |
| TimelineRevisionRenderModeParity | 6 | Mode parity issues |
| RenderOrchestratorService | 4 | Mockito over-specification |
| TimelineRevisionRenderExecutionMode | 3 | Execution mode issues |
| Other | 9 | Various |
| **Total** | **39** | |

### Environment Fix Applied

```text
Installed static FFmpeg 7.0.2 with libx264 from johnvansickle.com
Location: ~/.local/bin/ffmpeg
Impact: libx264 encoder now available
```

### Why Not FULL GREEN

The remaining 39 failures are deeper application/contract issues:
1. Pipeline execution not working (tests expect COMPLETED, get QUEUED)
2. Mockito verification mismatches
3. Timeline/render mode parity issues

These require significant investigation and code changes beyond the scope of this session.

## Self-Improvement Actions

```text
NONE
```

## Recommended Next Steps

1. Investigate why render pipeline stays QUEUED instead of executing
2. Fix RenderOrchestratorServiceCharacterizationTest Mockito expectations
3. Fix TimelineRevision test contracts
4. Run full test suite again

## Files Changed

| File | Change |
|------|--------|
| ~/.local/bin/ffmpeg | Installed static FFmpeg with libx264 |
| ~/.local/bin/ffprobe | Installed static FFprobe |
| identity-access-module/.../ProjectImportPreviewServiceTest.java | Direct IP fix |
| outbox-event-module/.../OutboxEventDispatcherTest.java | Event registration fix |
| docs/architecture/target/render-output-commit-target-state.md | Stale constraint fix |
| ~/.hermes/skills/software-development/multi-agent-orchestration/ | REMOVED |
