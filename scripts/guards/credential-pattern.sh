#!/usr/bin/env bash
# Single authority for the CIPG1/CIP2EG1 credential-residue pattern.
#
# Consumed by scripts/check-architecture-drift.sh and by
# scripts/guards/test_credential_residue_pattern.sh so the guard and its regression tests can never
# drift apart. The engine is POSIX ERE (git grep -E), which has no lookbehind; the `sk-` token
# therefore uses an explicit leading boundary instead: a credential-shaped token starts at the
# beginning of a line or after a non-alphanumeric character. This keeps real key detection intact
# while a substring inside an identifier such as `render-task-<hex>` no longer matches.
CREDENTIAL_RESIDUE_PATTERN='ghp_[A-Za-z0-9]{36}|(^|[^A-Za-z0-9])sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]+'
# Lines carrying these markers are documentation/redaction samples, not live credentials.
CREDENTIAL_RESIDUE_EXCLUSIONS='AKIAIO|REDACTED|xxx'
