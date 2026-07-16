# Forced Test Verification

## Provider Durability Integration Test

| Verification | Total | Passed | Failures | Errors | Skipped | Executed |
|-------------|------:|-------:|---------:|-------:|--------:|---------:|
| Provider durability | 8 | 8 | 0 | 0 | 0 | YES |

## .2A Repository Runs (commit eb8521f — before Provider durability test)

| Verification | Total | Passed | Failures | Errors | Skipped | Executed |
|-------------|------:|-------:|---------:|-------:|--------:|---------:|
| Repository run 1 | 5,685 | 5,644 | 0 | 0 | 41 | YES |
| Repository run 2 | 5,685 | 5,644 | 0 | 0 | 41 | YES |

## .2B Agent E Repository Runs (commit fba3c66 — after Provider durability test)

| Verification | Total | Passed | Failures | Errors | Skipped | Executed |
|-------------|------:|-------:|---------:|-------:|--------:|---------:|
| Repository run 1 | 5,693 | 5,652 | 0 | 0 | 41 | YES |
| Repository run 2 | 5,693 | 5,652 | 0 | 0 | 41 | YES |

## Arithmetic Verification

- .2A: 5,644 + 0 + 0 + 41 = 5,685 ✅
- .2B: 5,652 + 0 + 0 + 41 = 5,693 ✅
- Difference: 5,693 - 5,685 = 8 (Provider durability tests)

## Notes

- All runs used `--rerun-tasks --no-build-cache --no-daemon --stacktrace`
- No UP-TO-DATE, FROM-CACHE, or NO-SOURCE
- No manual cleanup between runs
- Same committed state for each pair of runs
