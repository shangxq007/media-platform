# Human Review Workflow

**Status:** ACTIVE
**Authority:** HERMES-REVIEW.0 / HERMES.7

---

## Overview

All code changes from multi-agent coding tasks require human review before merge.

## Review Flow

```
1. Agent completes task in worktree
2. Pre-review checks run automatically
3. Review Packet generated
4. Claude Code performs architecture boundary check
5. Human reviews packet + diff
6. Human approves or requests changes
7. Human pushes and merges
```

## Pre-Review Gate

All 7 checks must pass before review:

| # | Check | Command |
|---|-------|---------|
| 1 | Compiles | `./gradlew compileJava` |
| 2 | Domain tests | `./gradlew :render-module:test --tests "com.example.platform.render.domain.*"` |
| 3 | No forbidden paths | Check diff against forbidden path list |
| 4 | Architecture boundary | Claude Code read-only review |
| 5 | Diffstat | `git diff --stat` |
| 6 | Semgrep rules | `semgrep --config .semgrep/` |
| 7 | No secrets | `git diff \| grep -i "api_key\|token\|password"` |

## Review Packet

Each task produces a Review Packet containing:
- Task summary (ID, objective, agent, model)
- Changes (commits, diffstat, file list)
- Test results
- Safety checks (forbidden paths, architecture boundaries, secret scan)
- AI reviewer findings
- Human review checklist
- Merge recommendation

Template: `docs/review/review-packet-template.md`

## Architecture Boundary Checklist

1. Timeline remains canonical editing model
2. Product remains canonical result object
3. StorageRuntime/ProductRuntime boundaries not violated
4. OpenCue treated as ExecutionEnvironment, not Provider
5. Remotion not production-dispatched
6. FFmpeg/libass remains production baseline
7. Provider binding is deterministic eligibility + priority
8. Artifact DAG not implemented as active runtime
9. Spring AI not introduced
10. Template/Plugin/Workflow cannot generate raw commands
11. No secrets or local path exposure in public API

## Branch Protection

- PR required before merge
- 1 approval required (Phase 2)
- Stale reviews dismissed
- No force push
- No deletions
- Administrators included
