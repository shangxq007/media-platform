# OpenDAL Evaluation

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** STORAGE-OPENDAL-EVALUATION.0
**Recommendation:** GO_WITH_LIMITS

---

## Context

Preview baseline merged to main. Integration Lab Architecture complete. OpenDAL is EVALUATION_READY. Current R2 path is verified and must remain stable.

---

## OpenDAL Overview

Apache OpenDAL is a unified data access layer that provides a single API for various storage services.

**Key facts:**
- Apache 2.0 licensed
- Rust core with bindings for Java, Python, Node.js, etc.
- Java binding: `org.apache.opendal:opendal-java`
- Supports 40+ storage services including S3, R2, fs, memory, etc.

---

## Java Binding Assessment

| Aspect | Finding |
|--------|---------|
| Maven coordinates | `org.apache.opendal:opendal-java` |
| JDK requirement | JDK 8+ |
| Native library | Required (Rust-based) |
| Platform classifiers | Required (linux-x86_64, darwin-x86_64, etc.) |
| os-maven-plugin | Recommended for auto-detection |
| All backends bundled | Yes, single native lib |

### Native Library Packaging Risk

**HIGH** — Requires platform-specific native libraries. Docker image must include correct classifier.

---

## Capability Matrix

| Capability | Current S3/R2 | OpenDAL Java | Gap | Risk |
|------------|---------------|--------------|-----|------|
| Local fs write/read/stat | ✅ | ✅ | None | Low |
| S3-compatible write/read/stat | ✅ | ✅ | None | Low |
| R2 endpoint compatibility | ✅ | ✅ (S3 backend) | None | Low |
| Path-style access | ✅ | ✅ | None | Low |
| Region auto | ✅ | ✅ | None | Low |
| Chunked encoding disabled | ✅ | ✅ (config) | None | Low |
| Metadata/content length | ✅ | ✅ | None | Low |
| Content type | ✅ | ✅ | None | Low |
| Streaming read | ✅ | ✅ | None | Low |
| Streaming write | ✅ | ✅ | None | Low |
| **Presigned GET** | ✅ | ⚠️ Async only | **Partial** | **Medium** |
| TTL control | ✅ | ⚠️ Via config | **Partial** | Low |
| Timeout control | ✅ | ✅ | None | Low |
| Retry behavior | ✅ | ✅ | None | Low |
| Lazy init | ✅ | ✅ | None | Low |
| Docker compatible | ✅ | ⚠️ Needs native lib | **Gap** | **Medium** |
| bootJar compatible | ✅ | ⚠️ Needs classifier | **Gap** | **Medium** |
| CI compatible | ✅ | ⚠️ Needs setup | **Gap** | Low |

---

## R2 Compatibility

| Aspect | Assessment |
|--------|------------|
| R2 via S3 backend | ✅ Supported |
| Endpoint config | ✅ Via S3 config |
| Access key/secret | ✅ Via S3 config |
| Path-style access | ✅ Via S3 config |
| Region auto | ✅ Via S3 config |
| Chunked encoding | ✅ Configurable |
| **Presigned GET** | ⚠️ Async API only, may need AWS SDK presigner |

**Key finding:** OpenDAL supports R2 via S3-compatible backend, but presigned URL is async-only and may require hybrid approach with existing AWS SDK presigner.

---

## Architecture Options

### Option 1: OpenDAL as full materializer provider
```
StorageRuntimeService
  ├── S3ObjectMaterializer (current)
  └── OpenDalMaterializer (new)
```
**Pros:** Unified API, future multi-backend
**Cons:** High risk, presigned URL gap, native packaging complexity
**Recommendation:** NOT NOW

### Option 2: OpenDAL only for non-S3 backends
```
S3/R2 path → current AWS SDK provider
OpenDAL → fs/minio/other experimental backends
```
**Pros:** Low risk to R2 path, safe experimentation
**Cons:** Dual provider maintenance
**Recommendation:** SAFE_START

### Option 3: OpenDAL as internal client under provider
```
OpenDalStorageProvider
  uses OpenDAL for data ops
  delegates signed URL to S3 presigner
```
**Pros:** Best of both worlds
**Cons:** Complexity, hybrid maintenance
**Recommendation:** FUTURE

### Option 4: Defer OpenDAL
Keep current S3/R2 provider only.
**Pros:** Zero risk
**Cons:** Miss flexibility opportunity
**Recommendation:** FALLBACK

---

## Recommendation

### GO_WITH_LIMITS

**Rationale:**
1. OpenDAL Java binding exists and supports S3/R2 via S3 backend
2. Native library packaging is a solvable risk
3. Presigned URL is async-only, but hybrid approach works
4. Can start with local fs backend only, safe experimentation
5. Current R2 path remains unchanged

**Proposed scope for STORAGE-OPENDAL-PROVIDER-POC.0:**
- Add OpenDAL dependency (isolated, disabled by default)
- Implement local fs backend only
- No R2/S3 integration yet
- No presigned URL integration yet
- Verify Docker packaging
- Verify bootJar compatibility
- Document findings

---

## Security / Safety

| Rule | Status |
|------|--------|
| No secret logging | ✅ Required |
| No signed URL persistence | ✅ Required |
| No bucket/objectKey exposure | ✅ Required |
| No default startup dependency | ✅ Required |
| No broad bucket listing/deletion | ✅ Required |

---

## Operational Risks

| Risk | Level | Mitigation |
|------|-------|------------|
| Native packaging | Medium | Docker multi-stage, classifier testing |
| Platform compatibility | Medium | Test linux-x86_64 first |
| Debugging complexity | Low | Good logging |
| Docker image changes | Medium | Document changes |
| Dependency maturity | Low | Apache project, active development |
| Observability | Low | OpenDAL has logging |

---

## Final Decision

| Item | Decision |
|------|----------|
| OpenDAL status | EVALUATED_GO_WITH_LIMITS |
| Production path | DEFERRED |
| Current R2 path | KEEP_STABLE |
| Next task | STORAGE-OPENDAL-PROVIDER-POC.0 |

---

## Status

- STORAGE-OPENDAL-EVALUATION.0: COMPLETE
- Recommendation: GO_WITH_LIMITS
- Current R2 path: UNCHANGED
