# Document Governance Guard Policy

## Purpose

Automate enforcement of document governance rules established in phases .1-.5.

## Guard Execution

All guards run via unified entry point:
```bash
scripts/check-document-governance.sh --head HEAD --output /tmp/report.json
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | ALL_GUARDS_PASS |
| 1 | GOVERNANCE_VIOLATION |
| 2 | INVALID_USAGE |
| 3 | INPUT_OR_SCHEMA_ERROR |
| 4 | BASELINE_OR_CONFIGURATION_ERROR |
| 5 | INTERNAL_GUARD_ERROR |

## Protected Baseline Changes

Any change to `protected-document-baseline.json` requires explicit governance approval. CI must fail with `MANUAL_GOVERNANCE_APPROVAL_REQUIRED`.

## Broken-Link Baseline

Baseline established at 128 unique broken links (from commit 5551400...). Can decrease (resolved links) but must not increase.

## Bypass Prohibition

No environment variable may bypass guards. No `SKIP_GOVERNANCE`, `IGNORE_GUARDS`, or `ALLOW_ALL`.

## .6A Boundary

This policy covers repository-level guards only. Root receipt, systemd, mount, gateway, and delegated-tool guards are in .6A.
