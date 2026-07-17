# Protected Governance Change Policy

## Protected Objects

The following cannot be changed without explicit governance approval:

1. `protected-document-baseline.json` — semantic body hashes
2. `broken-link-debt-baseline.json` — link debt baseline (expansion prohibited)
3. Guard policy — cannot be weakened
4. Guard catalog — required guards cannot be disabled

## Change Authority

| Change | Authority |
|--------|-----------|
| Protected baseline | USER_EXPLICIT_APPROVAL + GOVERNANCE_REVIEW |
| Link baseline (decrease) | GOVERNANCE_REVIEW |
| Link baseline (increase) | PROHIBITED |
| Guard disable | PROHIBITED for required guards |
| Guard policy weaken | PROHIBITED |

## CI Behavior

Any protected change in a PR must output `MANUAL_GOVERNANCE_APPROVAL_REQUIRED` and return non-zero exit code.

## Bypass Prohibition

No environment variable may bypass these guards. No `SKIP_GOVERNANCE`, `IGNORE_GUARDS`, or `ALLOW_ALL`.
