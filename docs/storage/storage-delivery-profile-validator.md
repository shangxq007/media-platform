# Storage Delivery Profile Validator

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-DELIVERY-PROFILE-VALIDATOR.0

---

## Context

Storage Delivery Profile Contract and DTOs complete. This task adds local validation only. Runtime switching is not implemented. Current R2 behavior remains unchanged.

---

## Implementation

| Component | Status |
|-----------|--------|
| StorageDeliveryProfileValidator | ✅ CREATED |
| Single-profile validation | ✅ |
| Catalog validation | ✅ |
| No remote calls | ✅ |

---

## Validation Rules

| Category | Rules |
|----------|-------|
| Required fields | ID, status, provider, backend, access mode, capabilities, security policy |
| RuntimeSelectable | Requires enabled=true, PREVIEW_VERIFIED/VERIFIED status |
| Lab/Design | Cannot be runtimeSelectable, must be disabled |
| Access mode | SIGNED_URL requires presignRead, etc. |
| Security | No bucket/objectKey/storageReferenceId exposure |
| Preview R2 | Must be enabled, runtimeSelectable, S3_COMPATIBLE/R2 |

---

## Tests

| Test | Result |
|------|--------|
| Preview R2 validates | ✅ PASSED |
| Signed URL requires presign | ✅ PASSED |
| Signed URL must not persist | ✅ PASSED |
| RuntimeSelectable requires enabled | ✅ PASSED |
| Lab profiles disabled | ✅ PASSED |
| Local path not user-facing | ✅ PASSED |
| Catalog validation | ✅ PASSED |

---

## Status

- STORAGE-DELIVERY-PROFILE-VALIDATOR.0: COMPLETE
- Runtime switching: NOT_IMPLEMENTED
- Current R2 path: KEEP_STABLE
