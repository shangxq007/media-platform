# StorageRuntime Physical Object Check

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-RUNTIME-PHYSICAL-CHECK.1

---

## Physical Check Scope

| Setting | Default | Description |
|---------|---------|-------------|
| Physical checks | DISABLED | Optional, report-only |
| Scope | Referenced StorageReferences only | No bucket scan |
| Limit | Configurable | Bounded |

---

## Issue Types

| Issue Type | Severity | Description |
|------------|----------|-------------|
| STORAGE_OBJECT_MISSING | HIGH | Referenced object does not exist |
| STORAGE_OBJECT_CHECK_UNSUPPORTED | LOW | Provider does not support check |
| STORAGE_OBJECT_CHECK_FAILED | MEDIUM | Transient check error |

---

## Report-only Guarantee

| Guarantee | Status |
|-----------|--------|
| Never deletes Product | ✅ |
| Never deletes Artifact | ✅ |
| Never deletes StorageReference | ✅ |
| Never deletes files | ✅ |
| Never deletes remote objects | ✅ |
| Never mutates state | ✅ |
| Never repairs missing objects | ✅ |

---

## Implementation

| Component | Status |
|-----------|--------|
| StorageRuntimeOrphanReportService.generatePhysicalReport | ✅ CREATED |
| Local file existence check | ✅ |
| Symlink following prevented | ✅ |
| Path traversal prevented | ✅ |

---

## Status

- STORAGE-RUNTIME-PHYSICAL-CHECK.1: COMPLETE
- Physical check: IMPLEMENTED
- Report-only: GUARANTEED
