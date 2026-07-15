# BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2

## Charter

**Task:** BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2
**Mode:** HERMES_NATIVE_FINAL_REPOSITORY_TEST_BASELINE_RECOVERY
**Lead:** backend-engineer
**Starting commit:** 1643274 (fix/pre-v5-readiness-recovery)

## Mission

Restore complete repository test baseline to green. Close the remaining 6 render-module failures, diagnose platform-app OOM, classify all repository failures, and prove the complete test suite passes twice.

## Frozen Constraints

- No V5, no V1–V4 modification
- No RenderOutputCommit implementation
- No retry/fallback/scheduler/cleanup runtime
- No FALLBACKING/RETRYING restoration
- No test disabling/ignoring/exclusion
- No arbitrary heap increase
- No document governance restructuring
- No Skill/Profile/Memory changes

## Expected Outcome

COMPLETE_REPOSITORY_GREEN_BASELINE_RESTORED

## Agent Topology

```
Hermes Lead (backend-engineer)
├── Agent A — Provider failure durability + Timeline error contract (READ-ONLY)
├── Agent B — Schema fixture + adapter nullability + Testcontainers (READ-ONLY)
├── Agent C — Platform-app OOM + repository failures (READ-ONLY)
├── Agent D — Claude Code, sole repository writer
└── Agent E — code-reviewer + Codex, independent verifier (READ-ONLY)
```
