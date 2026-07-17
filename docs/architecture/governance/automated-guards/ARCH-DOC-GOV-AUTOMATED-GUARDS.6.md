# Automated Guards — Phase .6

## Purpose

Automate enforcement of document governance rules from phases .1-.5.

## Guards Implemented

16 guards (DG-001 through DG-016) covering:
- Metadata validation (DG-003, DG-004)
- Semantic body protection (DG-006)
- Governance boundaries (DG-007, DG-008, DG-009, DG-010)
- Link debt management (DG-011)
- Receipt validation (DG-012, DG-013)
- Commit scope (DG-014)
- Architecture guard integration (DG-016)

## Baselines

- Protected documents: 18 (canonical contracts + candidates)
- Broken links: 128 (pre-existing, can decrease only)
- Metadata entries: 1117

## Usage

```bash
# Current state check
scripts/check-document-governance.sh --head HEAD --output /tmp/report.json

# Transition check
scripts/check-document-governance.sh --base origin/main --head HEAD --output /tmp/transition-report.json
```

## .6A Boundary

Root receipt, systemd, mount, gateway, and delegated-tool guards are in .6A.
