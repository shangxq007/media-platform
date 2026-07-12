# Ingest Preflight Policy Evaluator Config Binding

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-PREFLIGHT-POLICY-EVALUATOR-CONFIG-BINDING.0

---

## Context

Report-only evaluator exists. Evaluator is integrated into upload report-only hook. Config binding now defines safe report-only evaluator configuration. No enforcement is enabled.

---

## Config Shape

```yaml
ingest:
  preflight:
    policy:
      report-only:
        enabled: false
        mode: report_only
        profile: preview_safe
        fail-open: true
        include-warning-findings: true
        include-media-technical-findings: true
        include-reject-candidates: true
        max-findings: 50
        log-result: true
```

---

## Defaults

| Property | Default | Notes |
|----------|---------|-------|
| enabled | false | Disabled by default |
| mode | report_only | Only allowed value |
| profile | preview_safe | Safe profile |
| fail-open | true | Cannot be false |
| max-findings | 50 | Bounded 1-1000 |

---

## Unsafe Config

| Config | Result |
|--------|--------|
| mode: enforce | REJECTED |
| fail-open: false | REJECTED |
| profile: strict | REJECTED |
| max-findings: 0 | REJECTED |
| max-findings: 1001 | REJECTED |

---

## Tests

| Test | Result |
|------|--------|
| Default config valid | ✅ PASSED |
| Default values | ✅ PASSED |
| Enforce mode rejected | ✅ PASSED |
| Fail-open false rejected | ✅ PASSED |
| Max findings bounds | ✅ PASSED |
| Invalid profile rejected | ✅ PASSED |

---

## Status

- INGEST-PREFLIGHT-POLICY-EVALUATOR-CONFIG-BINDING.0: COMPLETE
- Enforcement: NOT_ENABLED
- Upload behavior: UNCHANGED
