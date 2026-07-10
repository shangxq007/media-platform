# Storage Docs Reality Check

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** STORAGE-DOCS-REALITY-CHECK.0

---

## Executive Summary

Most of the recent platform baseline is backed by actual code. Storage provider documentation needed precision.

**Key corrections:**
- R2 support is CODE_BACKED via S3-compatible materialization path
- S3StorageProvider is a STUB, not a full provider
- S3ObjectMaterializer is the real S3/R2 implementation anchor
- Provider matrix is DOCS_ONLY unless runtime capability code exists

---

## Code-Backed Capabilities

| Capability | Status | Evidence |
|------------|--------|----------|
| Timeline Git API | CODE_BACKED | TimelineRevisionController 8 endpoints |
| Worker once/poll | CODE_BACKED | FFmpegWorkerRunner actual code |
| Recovery | CODE_BACKED | RenderWorkerRecoveryService actual code |
| Retry | CODE_BACKED | RenderWorkerRetryService actual code |
| Claim hardening | CODE_BACKED | RenderJobRepository claim methods |
| Lifecycle Events | CODE_BACKED | V2 migration + service actual code |
| Metrics | CODE_BACKED | RenderWorkerMetricsService actual code |
| Output idempotency | CODE_BACKED | Output idempotency guard |
| Output cleanup | CODE_BACKED | RenderWorkerOutputCleanupService |
| Orphan report | CODE_BACKED | StorageRuntimeOrphanReportService |
| Local physical check | CODE_BACKED / LOCAL_ONLY | generatePhysicalReport |
| Frontend routes | CODE_BACKED | 4 .tsx files exist |
| S3ObjectMaterializer | CODE_BACKED / R2_COMPATIBLE | 191 lines, AWS S3 SDK |
| S3ClientSettingsResolver | CODE_BACKED | 61 lines, R2 config support |
| S3CompatibilityMode | CODE_BACKED | GENERIC and R2 modes |
| StorageS3Properties | CODE_BACKED | 104 lines, full config |
| S3 smoke tests | CODE_BACKED | 3 test files |

---

## Overstated Claims (Corrected)

| Claim | Previous Wording | Actual Finding | Corrected Wording |
|-------|------------------|----------------|-------------------|
| S3StorageProvider | "provider exists" | STUB / architecture validation | STUB / ARCHITECTURE_VALIDATION_ONLY |
| R2 independent provider | "R2 provider exists" | No independent class | NOT_PRESENT / NOT_REQUIRED_CURRENTLY |
| R2 physical check | "COMPLETE" | Not verified in report path | NOT_IN_REPORT_PATH / UNVERIFIED |
| Provider matrix | "COMPLETE" | Docs-only | DOCS_ONLY / ARCHITECTURE_MATRIX |
| StorageRuntime interface | "COMPLETE" | StorageRuntimeService only | NOT_FORMALIZED |

---

## Understated Claims (Corrected)

| Claim | Previous Wording | Actual Finding | Corrected Wording |
|-------|------------------|----------------|-------------------|
| R2 support | "docs-only / not found" | S3ObjectMaterializer with R2 config | CODE_BACKED_VIA_S3_COMPATIBLE |
| S3/R2 materialization | "not implemented" | S3ObjectMaterializer 191 lines | CODE_BACKED / R2_COMPATIBLE |

---

## Corrected Storage Status

### S3ObjectMaterializer
- **Status:** CODE_BACKED / R2_COMPATIBLE
- **Evidence:** 191 lines, uses AWS S3 SDK
- **R2 support:** Via S3ClientSettingsResolver (region=auto, path-style, no chunked)

### S3ClientSettingsResolver
- **Status:** CODE_BACKED
- **Evidence:** 61 lines, R2-specific config resolution

### S3CompatibilityMode
- **Status:** CODE_BACKED
- **Evidence:** GENERIC and R2 modes

### StorageS3Properties
- **Status:** CODE_BACKED
- **Evidence:** 104 lines, full S3/R2 config contract

### S3StorageProvider
- **Status:** STUB / ARCHITECTURE_VALIDATION_ONLY
- **Evidence:** Comment says "architecture validation stub"

### R2 Support
- **Status:** CODE_BACKED_VIA_S3_COMPATIBLE
- **Evidence:** S3ObjectMaterializer + S3ClientSettingsResolver + S3CompatibilityMode.R2
- **Note:** No independent R2StorageProvider class (not required)

### R2 Physical Report Check
- **Status:** NOT_IN_REPORT_PATH / UNVERIFIED
- **Reason:** generatePhysicalReport uses local file check, not S3 HEAD/stat

### R2 Signed Access
- **Status:** DEFERRED / NOT_IMPLEMENTED
- **Reason:** No presigned URL code found

### Provider Matrix
- **Status:** DOCS_ONLY / ARCHITECTURE_MATRIX
- **Reason:** No runtime capability service exists

### StorageRuntime Interface
- **Status:** NOT_FORMALIZED
- **Reason:** StorageRuntimeService exists but no formal provider interface

---

## Corrected Roadmap

### Immediate
1. **STORAGE-RUNTIME-S3-PROVIDER-BOUNDARY.0** — Clarify S3ObjectMaterializer / S3StorageProvider stub / StorageRuntimeService responsibilities

### Near-term
2. **STORAGE-R2-PHYSICAL-CHECK.0** — Verify or implement R2 HEAD/stat in physical report
3. **STORAGE-R2-SIGNED-ACCESS.1** — Access-layer signed URL if needed

### Later
4. **FRONTEND-USER-RENDER-RESULT-DETAIL.1** — User result detail
5. **STORAGE-R2-PREVIEW-SMOKE-CI.1** — Automated smoke

---

## Guardrails

| Guardrail | Status |
|-----------|--------|
| Do not treat S3StorageProvider stub as full provider | ✅ |
| Do not say R2 is docs-only | ✅ |
| Do not require independent R2StorageProvider | ✅ |
| Do not assume R2 HEAD/stat in physical report | ✅ |
| Do not assume signed access exists | ✅ |
| OpenDAL DEFERRED | ✅ |
| OpenCue NOT_STARTED | ✅ |
| Artifact DAG POSTPONED | ✅ |

---

## Status

- STORAGE-DOCS-REALITY-CHECK.0: COMPLETE
- Code evidence: VERIFIED
- Overstatements: CORRECTED
- Understatements: CORRECTED
