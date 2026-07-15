# STORAGE-OPENDAL-EVALUATION.0 — Apache OpenDAL Feasibility Assessment

**Date:** 2026-07-15
**Status:** EVALUATION_COMPLETE
**Authority:** STORAGE-OPENDAL-EVALUATION.0
**Lifecycle State:** EVALUATED_GO_WITH_LIMITS → POC
**Scope:** DOCS_EVALUATION_FIRST — no production path changes

---

## 1. Executive Summary

Apache OpenDAL (v0.46.4) is a graduated Apache project providing a unified data access
layer across 50+ storage backends via a Rust core with JNI bindings. The Java binding
is functional but carries native library packaging risk. Our codebase already declares
the dependency and has enum/profile scaffolding, but the existing `OpenDalMaterializer`
does NOT use the OpenDAL API — it is a pure Java NIO shim.

**Verdict:** OpenDAL is architecturally viable as an experimental provider adapter behind
the `BlobStorage` SPI. Current implementation is STUB/NIO-only and needs a proper
`OpenDalBlobStorageProvider` to become a real POC. No production urgency — R2/S3 SDK
path is stable and verified.

**Decision:** EVALUATED_GO_WITH_LIMITS — proceed to POC with constraints.

---

## 2. Apache OpenDAL Project Maturity

| Dimension | Assessment |
|-----------|-----------|
| ASF Status | Graduated project (2024-01-18) |
| License | Apache 2.0 |
| Rust Core | Stable, 50+ service backends |
| Java Binding | `opendal-java` on Maven Central |
| Current Version | 0.46.4 (already in our build.gradle.kts) |
| 2025 Roadmap | Focus on production adoption, documentation, binding stability |
| 2026 Status | Active development, cross-language binding improvements |
| Community | Active ASF project with regular releases |

**Maturity Assessment:** The Rust core is production-grade. The Java binding is
functional but younger — platform-specific native library packaging is the primary
risk vector.

---

## 3. Java Binding Technical Analysis

### 3.1 Architecture

OpenDAL Java binding uses JNI to call the Rust core. The binding ships a
platform-specific native library as a classifier-based Maven artifact.

```
Java Application
    ↓
opendal-java (JNI bridge)
    ↓
Rust core (native .so/.dylib/.dll)
    ↓
Storage backends (S3, GCS, Azure, fs, memory, etc.)
```

### 3.2 Maven Coordinates

```xml
<dependency>
    <groupId>org.apache.opendal</groupId>
    <artifactId>opendal-java</artifactId>
    <version>0.46.4</version>
</dependency>
```

Gradle (current):
```kotlin
implementation("org.apache.opendal:opendal-java:0.46.4")
```

### 3.3 Native Library Packaging

The Java binding requires a platform-specific native library. Options:

1. **OS Detector Plugin** (recommended): `kr.motd.maven:os-maven-plugin` or
   `gradle.plugin.com.google.cloud.artifactregistry:artifactregistry-gradle-plugin`
   auto-selects the correct classifier.

2. **Manual classifier**: `opendal-java-0.46.4-linux-x86_64.jar` etc.

3. **Supported platforms**: linux-x86_64 (glibc), linux-x86_64-musl, darwin-x86_64,
   darwin-aarch64, windows-x86_64.

### 3.4 Docker Compatibility

Our Dockerfile uses `eclipse-temurin:25-jre-jammy` (Ubuntu Jammy, glibc).
OpenDAL's `linux-x86_64` classifier targets glibc — **COMPATIBLE**.

**Risk:** If we ever migrate to Alpine (musl), we need `linux-x86_64-musl` classifier.
Current trajectory does not indicate this.

### 3.5 API Surface (Core Classes)

```java
// Create operator for a scheme
Operator op = Operator.of("fs", Map.of("root", "/tmp/opendal-test"));

// Write
op.write("path/to/file", "content".getBytes());

// Read
byte[] data = op.read("path/to/file");

// Stat
Metadata meta = op.stat("path/to/file");
meta.getContentLength();
meta.getContentType();

// Delete
op.delete("path/to/file");

// List
List<Entry> entries = op.list("path/to/prefix");
```

**Key API:** `Operator` is the unified entry point. Scheme-based dispatch
("fs", "s3", "gcs", "azblob", "memory", etc.) replaces per-backend client construction.

---

## 4. Current State in Our Codebase

### 4.1 What Exists

| Component | Status | Actual Content |
|-----------|--------|---------------|
| `opendal-java:0.46.4` dependency | DECLARED | `storage-module/build.gradle.kts:11` |
| `StorageProviderType.OPENDAL` | EXISTS | Enum value in delivery contract |
| `StorageDeliveryProfileId.LAB_OPENDAL_FS_INTERNAL` | EXISTS | Profile ID in contract |
| `StorageDeliveryProfileCatalog.labOpenDalFsInternal()` | EXISTS | EXPERIMENTAL, disabled, internal-only |
| `OpenDalExperimentalProperties` | EXISTS | `enabled=false`, backend=fs, mode=poc |
| `OpenDalMaterializer` | EXISTS | **Pure Java NIO — does NOT use OpenDAL API** |
| `OpenDalLocalFsSmokeTest` | EXISTS | Tests NIO operations, not OpenDAL |
| `StorageDeliveryProfileValidator` | EXISTS | Has LAB_OPENDAL_FS_INTERNAL validation rule |

### 4.2 Critical Finding: OpenDAL API Is Unused

`OpenDalMaterializer` uses `java.nio.file.Files` for all operations. It does NOT
import `org.apache.opendal.Operator` or any OpenDAL class. The dependency is declared
but never exercised.

**Evidence:**
- `import org.apache.opendal` → 0 matches in codebase
- `OpenDalMaterializer.write()` → `Files.write(target, data)`
- `OpenDalMaterializer.read()` → `Files.readAllBytes(source)`

### 4.3 Architecture Disconnect

The existing OpenDAL code sits outside both storage SPIs:

```
storage-module/domain/BlobStorage.java     ← SPI (put/get/delete/presign/list)
storage-module/infrastructure/S3BlobStorageProvider.java  ← implements BlobStorage
storage-module/infrastructure/LocalFsStorageProvider.java ← implements BlobStorage
storage-module/infrastructure/experimental/opendal/OpenDalMaterializer.java ← NO SPI alignment
```

`OpenDalMaterializer` has its own `write/read/exists/size` methods that don't
match `BlobStorage` or `StorageProvider` interfaces.

---

## 5. Architecture Fit Assessment

### 5.1 BlobStorage SPI Mapping

| BlobStorage Method | OpenDAL Equivalent | Gap |
|--------------------|--------------------|-----|
| `code()` | N/A (config) | Trivial |
| `put(PutObjectCommand)` | `Operator.write(key, bytes)` | Content type mapping needed |
| `get(bucket, objectKey)` | `Operator.read(key)` | Bucket concept differs per backend |
| `delete(bucket, objectKey)` | `Operator.delete(key)` | Direct match |
| `listObjects(bucket, prefix, max)` | `Operator.list(prefix)` | Bucket scoping needed |
| `presign(objectKey)` | **NOT SUPPORTED** by OpenDAL | Must fall back to S3 SDK for presigning |
| `presign(bucket, objectKey)` | **NOT SUPPORTED** | Same |

### 5.2 Presigned URL Gap

OpenDAL does NOT provide presigned URL generation. For S3-compatible backends
(R2, AWS S3, RustFS), presigning requires the native SDK.

**Impact:** An `OpenDalBlobStorageProvider` would need a hybrid approach:
- read/write/delete/list → OpenDAL Operator
- presign → S3Presigner (AWS SDK) or not supported

This is acceptable for internal-stream profiles (LAB_OPENDAL_FS_INTERNAL uses
`INTERNAL_STREAM` access mode, not `SIGNED_URL`).

### 5.3 StorageProvider SPI Mapping (render-module)

| StorageProvider Method | OpenDAL Equivalent | Gap |
|------------------------|--------------------|-----|
| `store(id, data, metadata)` | `Operator.write(key, bytes)` | Direct |
| `fetch(id)` | `Operator.read(key)` | Direct |
| `delete(id)` | `Operator.delete(key)` | Direct |
| `exists(id)` | `Operator.stat(key)` | Direct |
| `metadata(id)` | `Operator.stat(key)` → Metadata | Partial (no custom metadata) |

---

## 6. Native Library / Docker Risk Assessment

### 6.1 Risk Matrix

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Native .so not found at runtime | HIGH | LOW | OS detector plugin, Docker validation |
| musl/glibc mismatch | HIGH | LOW | Current image is glibc (Jammy) |
| JNI crash takes down JVM | MEDIUM | LOW | OpenDAL core is well-tested |
| Startup latency from native load | LOW | MEDIUM | Lazy init (already have pattern) |
| Multi-arch (ARM) support | MEDIUM | LOW | darwin-aarch64 exists; linux-aarch64 TBD |

### 6.2 Docker Validation Required Before POC

The POC must validate:
1. Native library loads successfully in `eclipse-temurin:25-jre-jammy`
2. `Operator.of("fs", ...)` works end-to-end
3. `Operator.of("s3", ...)` works against R2 endpoint
4. No startup hang from JNI initialization
5. Graceful failure when native lib missing (not JVM crash)

### 6.3 Spring Boot Integration Pattern

```java
@Component
@ConditionalOnProperty(prefix = "storage.experimental.opendal", name = "enabled", havingValue = "true")
public class OpenDalBlobStorageProvider implements BlobStorage {

    private volatile Operator operator;

    // Lazy init — no native library load in constructor
    private Operator getOperator() {
        if (operator == null) {
            synchronized (this) {
                if (operator == null) {
                    operator = Operator.of(properties.getBackend(), Map.of(
                        "root", properties.getRoot()
                    ));
                }
            }
        }
        return operator;
    }
}
```

This matches the existing `S3BlobStorageProvider` lazy-init pattern.

---

## 7. Recommended Lifecycle State

### Current: EVALUATED_GO_WITH_LIMITS

| Dimension | State |
|-----------|-------|
| Technology maturity | ASF Graduated, Java binding functional |
| Our implementation | NIO shim, NOT real OpenDAL |
| SPI alignment | NOT_ALIGNED |
| Docker validation | NOT_DONE |
| Native library risk | ACCEPTABLE (glibc image) |
| Production urgency | NONE (R2/S3 SDK stable) |

### Target: POC (with constraints)

**Promotion triggers for POC:**
1. ✅ Dependency declared (`opendal-java:0.46.4`)
2. ✅ Enum/profile scaffolding exists
3. ✅ Docker base image compatible (glibc)
4. ❌ Native library Docker validation NOT done
5. ❌ `OpenDalBlobStorageProvider implements BlobStorage` NOT written
6. ❌ OpenDAL Operator API NOT exercised

**Allowed next task:** Write a proper `OpenDalBlobStorageProvider` that:
- Implements `BlobStorage` SPI
- Uses `org.apache.opendal.Operator` (not NIO)
- Is gated by `storage.experimental.opendal.enabled=true`
- Has fixture-based tests (memory backend)
- Validates native library loading in Docker

**Forbidden scope:**
- ❌ Replace R2/S3 SDK path
- ❌ Change default storage provider
- ❌ Add presigned URL via OpenDAL (not supported)
- ❌ Database schema changes
- ❌ Production profile activation
- ❌ Signed URL generation via OpenDAL

---

## 8. Technology Candidate Matrix Update

| Technology | Category | Module | Provider Type | Status | Allowed Next | Forbidden | Promotion Trigger |
|-----------|----------|--------|---------------|--------|-------------|-----------|-------------------|
| Apache OpenDAL | Storage | storage-module | OpenDalBlobStorageProvider | EVALUATED_GO_WITH_LIMITS | Docker native validation, BlobStorage impl | R2 replacement, presign, production | Native lib loads in Docker, SPI aligned |
| RustFS | Storage | storage-lab | S3-compatible lab | LAB_CREATED | OpenDAL S3 smoke | Production storage | Production deployment model |
| Garage | Storage | storage-lab | S3-compatible lab | DESIGN_ONLY | Evaluation | Production storage | Feature evaluation |
| SeaweedFS | Storage | storage-lab | S3-compatible lab | DESIGN_ONLY | Evaluation | Production storage | Feature evaluation |

---

## 9. Decision Record

### 9.1 Why Not Reject?

- OpenDAL is ASF-graduated, not experimental
- Java binding is functional (0.46.4 on Maven Central)
- Unified API across 50+ backends has architectural value
- Native library risk is mitigable (glibc image)
- Dependency already declared — sunk cost is low

### 9.2 Why Not Promote to PREVIEW?

- Current code is NIO shim, not real OpenDAL
- Native library not validated in Docker
- No SPI-aligned implementation exists
- No production use case requires it today
- R2/S3 SDK path is stable and verified

### 9.3 Recommended Approach

1. **Keep existing scaffolding** — enum, profile, properties are correct
2. **Replace NIO shim** with real OpenDAL Operator usage
3. **Align to BlobStorage SPI** — don't create parallel API surface
4. **Docker validation first** — before any code integration
5. **Memory backend for tests** — `Operator.of("memory", Map.of())` for unit tests
6. **Lazy init pattern** — match S3BlobStorageProvider's approach

---

## 10. Summary

| Aspect | Finding |
|--------|---------|
| OpenDAL maturity | ASF Graduated, production-focused 2025 roadmap |
| Java binding | Functional, JNI-based, platform classifiers |
| Docker compatibility | ✅ glibc image (Jammy) compatible |
| Current code | NIO shim, NOT real OpenDAL — dependency unused |
| SPI alignment | NOT_ALIGNED — needs BlobStorage implementation |
| Presigned URLs | NOT supported by OpenDAL — hybrid approach needed |
| Production urgency | NONE — R2/S3 SDK path is stable |
| Recommended state | EVALUATED_GO_WITH_LIMITS → POC |
| Next task | Docker native validation + BlobStorage implementation |
| Risk | LOW — gated, disabled by default, removable |

---

## Status

- STORAGE-OPENDAL-EVALUATION.0: EVALUATION_COMPLETE
- Lifecycle state: EVALUATED_GO_WITH_LIMITS
- Current R2 path: UNCHANGED (guardrail respected)
- OpenDAL dependency: DECLARED but UNUSED in real code
- Recommended next: Docker native validation POC
