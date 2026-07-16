# Forced Run Matrix

All runs used: `--rerun-tasks --no-build-cache --no-daemon --stacktrace`

| Scope | Run | Forced execution | Actual tests | Passed | Failed | Skipped | Outcome |
|-------|----:|----------------:|-------------:|-------:|-------:|--------:|---------|
| Render module | 1 | YES | 2,763 | 2,746 | 0 | 17 | EXECUTED_SUCCESSFULLY |
| Render module | 2 | YES | 2,763 | 2,746 | 0 | 17 | EXECUTED_SUCCESSFULLY |
| Platform-app | 1 | YES | 459 | 439 | 0 | 20 | EXECUTED_SUCCESSFULLY |
| Platform-app | 2 | YES | 459 | 439 | 0 | 20 | EXECUTED_SUCCESSFULLY |
| Repository | 1 | YES | 5,685 | 5,644 | 0 | 41 | EXECUTED_SUCCESSFULLY |
| Repository | 2 | YES | 5,685 | 5,644 | 0 | 41 | EXECUTED_SUCCESSFULLY |

## Verification

- No run was UP-TO-DATE
- No run was FROM-CACHE
- No run executed zero tests
- All runs had 29/72/144 Gradle tasks executed (not cached)
- Test counts consistent across both runs
- No manual cleanup between runs
- Same committed state (eb8521f) for all runs
