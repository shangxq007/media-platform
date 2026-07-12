# Review Packet Template

**TASK_ID:** [TASK_ID]
**Generated:** [TIMESTAMP]

---

## 1. Task Summary

| Field | Value |
|-------|-------|
| TASK_ID | [TASK_ID] |
| Objective | [OBJECTIVE] |
| Agent | [AGENT] |
| Model | [MODEL] |
| Worktree | [WORKTREE] |
| Branch | [BRANCH] |

## 2. Changes

### Diffstat
```
[git diff --stat]
```

### Changed Files
```
[git diff --name-only]
```

## 3. Test Results

| Test | Result |
|------|--------|
| Compile | [PASS/FAIL] |
| Domain tests | [PASS/FAIL] |

## 4. Safety Checks

### Forbidden Paths

| Path | Touched? |
|------|----------|
| *Controller.java | [YES/NO] |
| *Provider*.java | [YES/NO] |
| *Artifact*.java | [YES/NO] |
| spring-ai-adapter/ | [YES/NO] |

### Architecture Boundaries

- [ ] Timeline canonical editing model
- [ ] Product canonical result object
- [ ] StorageRuntime/ProductRuntime boundaries
- [ ] OpenCue = ExecutionEnvironment
- [ ] No Remotion production dispatch
- [ ] FFmpeg/libass baseline
- [ ] No Spring AI
- [ ] No raw commands from templates
- [ ] No secrets exposure

## 5. AI Reviewer Findings

[Findings from Claude Code]

## 6. Human Review Checklist

- [ ] Task objective met
- [ ] No architecture violations
- [ ] Tests cover changes
- [ ] No secrets in code
- [ ] Code follows conventions

## 7. Merge Recommendation

| Decision | Rationale |
|----------|----------|
| [MERGE/REQUEST_CHANGES/BLOCK] | [RATIONALE] |

## 8. Blockers

[List blockers]

## 9. Next Action

[Next action]
