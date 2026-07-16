# JUnit Run Matrix

## .2A Runs (commit eb8521f — before Provider durability test)

| Run | Total | Passed | Failures | Errors | Skipped | Arithmetic valid | Evidence |
|-----|------:|-------:|---------:|-------:|--------:|----------------:|---------|
| Repository Run 1 | 5,685 | 5,644 | 0 | 0 | 41 | YES (5644+0+0+41=5685) | .2A/08-forced-run-matrix.md |
| Repository Run 2 | 5,685 | 5,644 | 0 | 0 | 41 | YES (5644+0+0+41=5685) | .2A/08-forced-run-matrix.md |

## .2B Runs (commit fba3c66 — after Provider durability test)

| Run | Total | Passed | Failures | Errors | Skipped | Arithmetic valid | Evidence |
|-----|------:|-------:|---------:|-------:|--------:|----------------:|---------|
| Repository Run 1 | 5,693 | 5,652 | 0 | 0 | 41 | YES (5652+0+0+41=5693) | .2B/09-agent-e-independent-verification.md |
| Repository Run 2 | 5,693 | 5,652 | 0 | 0 | 41 | YES (5652+0+0+41=5693) | .2B/09-agent-e-independent-verification.md |

## Notes

- .2A runs: 5,685 total (before Provider durability test)
- .2B runs: 5,693 total = 5,685 + 8 (Provider durability test)
- Skipped count (41) consistent across all runs
- Both .2B runs identical (same commit, same configuration)
- Previous report incorrectly stated "passed = 5,693" (should be 5,652)
- Correct formula: passed = total - failures - errors - skipped = 5,693 - 0 - 0 - 41 = 5,652

## Run Totals Identical

YES — both .2B runs have identical statistics.
