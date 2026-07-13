# Frontend DEV Diagnostics Route Map

**Date:** 2026-07-13
**Status:** COMPLETE
**Authority:** FRONTEND-DEV-DIAGNOSTICS-ROUTE-MAP.0
**Decision:** FRONTEND_DEV_DIAGNOSTICS_ROUTE_MAP_READY_WITH_LIMITS

---

## Canonical DEV Namespace

```
/dev/diagnostics
```

---

## Route Catalog

| Route | Title | Status | Contract | Client | Query Key |
|-------|-------|--------|----------|--------|-----------|
| /dev/diagnostics/storage-delivery | Storage Delivery Profiles | READY | dev.storageDeliveryProfiles | dev.storageDeliveryProfiles | dev.storageDeliveryProfiles |
| /dev/diagnostics/ingest-preflight-policy | Ingest Preflight Policy | READY | dev.ingestPreflightPolicy | dev.ingestPreflightPolicy | dev.ingestPreflightPolicy |
| /dev/diagnostics/safe-preflight-reports | Safe Preflight Reports | READY | dev.safePreflightReports | dev.safePreflightReports | dev.safePreflightReports |
| /dev/diagnostics/safe-preflight-reports/$recordId | Report Detail | READY | dev.safePreflightReports | dev.safePreflightReports | dev.safePreflightReports |
| /dev/diagnostics/retention-dry-run | Retention Dry-run | READY | dev.retentionDryRun | dev.retentionDryRun | dev.retentionDryRun |

---

## App Isolation Rules

- No DEV route exposed from /app
- No safe preflight persistence route in /app
- No retention dry-run route in /app
- Normal user workspace must not link to DEV diagnostics

---

## Status

- FRONTEND-DEV-DIAGNOSTICS-ROUTE-MAP.0: COMPLETE
- No complex DEV UI implemented
- Safe preflight persistence: DEV_ONLY, PAUSED
