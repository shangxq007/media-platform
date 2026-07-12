# Dev Diagnostics Endpoints

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** DEV-DIAGNOSTICS-ENDPOINTS.0

---

## Context

Storage read-only diagnostics complete. Ingest read-only diagnostics complete. Frontend /dev/diagnostics hub complete. This task exposes safe internal GET endpoints.

---

## Endpoint Catalog

### Storage Delivery Profile

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/dev/storage-delivery-profiles` | GET | Registry diagnostics |
| `/dev/storage-delivery-profiles/{profileId}` | GET | Profile diagnostics |
| `/dev/storage-delivery-profiles/validation` | GET | Validation diagnostics |

### Ingest Preflight Policy

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/dev/ingest/preflight-policy` | GET | Policy diagnostics |
| `/dev/ingest/preflight-policy/config` | GET | Config diagnostics |
| `/dev/ingest/preflight-policy/decision-semantics` | GET | Decision semantics |

---

## Safety Contract

- All endpoints are read-only GET
- All endpoints are internal /dev only
- No provider selection
- No signed URL generation
- No upload/preflight execution
- No persistence
- No sensitive field exposure

---

## Status

- DEV-DIAGNOSTICS-ENDPOINTS.0: COMPLETE
- Runtime behavior: UNCHANGED
