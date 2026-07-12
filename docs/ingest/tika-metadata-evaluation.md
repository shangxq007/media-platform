# Apache Tika Metadata Evaluation

**Date:** 2026-07-12
**Status:** COMPLETE
**Authority:** INGEST-TIKA-METADATA-EVALUATION.0
**Recommendation:** GO_WITH_LIMITS

---

## Context

Preview baseline merged to main. R2 signed access has authz. Integration Lab complete. Tika is EVALUATION_READY_LATER. RAW_MEDIA ingest needs safer metadata/content detection path.

---

## Evaluation Goals

- MIME detection
- Extension mismatch detection
- Light metadata extraction
- Upload preflight support
- Safe experimental provider boundary
- Comparison with media-specific tools

## Non-goals

- No production integration
- No FFprobe replacement
- No full text extraction
- No OCR
- No malware scanning claim

---

## Tika Overview

Apache Tika detects and extracts metadata from 1000+ file types through a single interface.

**Key capabilities:**
- MIME type detection (magic bytes, filename, content)
- Metadata extraction
- Text extraction (not recommended by default)
- Parser/detector architecture

---

## Dependency Assessment

| Aspect | Finding |
|--------|---------|
| Core artifact | `org.apache.tika:tika-core` |
| Full parsers | `org.apache.tika:tika-parsers-standard-package` |
| Java version | JDK 8+ |
| Native libs | Not required for detection |
| OCR | Requires external Tesseract binary |
| Footprint | tika-core: small, tika-parsers: large |

**Recommendation:** Use `tika-core` for detection only. Avoid full parser package initially.

---

## Capability Matrix

| Capability | Tika | FFprobe | MediaInfo | ExifTool | Platform Native |
|------------|------|---------|-----------|----------|-----------------|
| MIME detection | ✅ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| Magic bytes | ✅ | ✅ | ✅ | ✅ | ❌ |
| Extension mismatch | ✅ | ❌ | ❌ | ❌ | ❌ |
| Video duration | ❌ | ✅ | ✅ | ❌ | ❌ |
| Audio duration | ❌ | ✅ | ✅ | ❌ | ❌ |
| Codec info | ❌ | ✅ | ✅ | ❌ | ❌ |
| Resolution | ❌ | ✅ | ✅ | ✅ | ❌ |
| Container metadata | ⚠️ | ✅ | ✅ | ❌ | ❌ |
| Image metadata | ✅ | ❌ | ❌ | ✅ | ❌ |
| Document metadata | ✅ | ❌ | ❌ | ❌ | ❌ |
| Text extraction | ✅ | ❌ | ❌ | ❌ | ❌ |
| Streaming safe | ✅ | ✅ | ✅ | ✅ | ✅ |
| Large file safe | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| Dependency footprint | Small (core) | External | External | External | None |

**Conclusion:** Tika is best for generic content detection. FFprobe/MediaInfo remain primary for video/audio technical metadata.

---

## Security / Privacy Risks

| Risk | Level | Mitigation |
|------|-------|------------|
| Parser vulnerabilities | Medium | Use tika-core only, limit parsers |
| Zip bombs | Medium | Size limits, timeouts |
| Large files | Medium | Stream detection, size limits |
| Embedded resources | Low | Disable network access |
| Document macros | Low | No full parsing by default |
| Metadata privacy | Medium | Metadata allowlist |
| CPU/memory pressure | Medium | Bounded timeouts |
| Dependency CVEs | Low | Apache project, active maintenance |

### Required Safety Rules

- Tika disabled by default
- MIME detection first, full parsing optional later
- No full text extraction by default
- No OCR by default
- No network access from parsers
- Size limits on detection
- Timeouts on parsing
- Metadata allowlist
- Redacted logs
- No raw extracted text in Product metadata by default

---

## Architecture Options

### Option 1: Tika as Detector-only provider
```
IngestMetadataService
  ├── BasicMimeDetector
  └── TikaDetectorProvider   experimental, disabled by default
```
**Pros:** Low risk, useful for upload validation
**Cons:** Limited metadata
**Recommendation:** START HERE

### Option 2: Tika as light metadata provider
```
IngestMetadataService
  ├── FFprobeMediaMetadataProvider
  ├── BasicMimeDetector
  └── TikaLightMetadataProvider
```
**Pros:** More metadata, still bounded
**Cons:** More complexity
**Recommendation:** FUTURE

### Option 3: Tika as full parser provider
```
DocumentMetadataService
  └── TikaParserProvider
```
**Pros:** Document ingestion capability
**Cons:** High risk, large dependency
**Recommendation:** DEFER

### Option 4: Defer Tika
Keep current validation. Use FFprobe/MediaInfo later for media.
**Pros:** Zero risk
**Cons:** Miss detection improvement
**Recommendation:** FALLBACK

---

## Future Metadata Contract (Design-only)

```java
record IngestMetadataResult(
    String detectedContentType,
    String declaredContentType,
    String filenameExtension,
    boolean extensionMatchesDetectedType,
    String detector,
    double confidence,
    long sizeBytes,
    Map<String, String> safeMetadata,
    List<String> warnings,
    String rejectedReason
) {}
```

**Note:** Design-only. Not implemented.

---

## Recommendation

### GO_WITH_LIMITS

**Rationale:**
1. Tika-core is lightweight and useful for MIME detection
2. Can improve upload validation without replacing FFprobe
3. Security risks are manageable with tika-core only
4. Should be disabled by default experimental provider

**Proposed scope for INGEST-TIKA-METADATA-POC.0:**
- Add tika-core dependency (isolated, disabled by default)
- Implement detector-only provider
- MIME detection and extension mismatch check
- No full parsing, no text extraction, no OCR
- Verify Docker compatibility
- Document findings

---

## Final Decision

| Item | Decision |
|------|----------|
| Tika status | EVALUATED_GO_WITH_LIMITS |
| Production path | DEFERRED |
| Allowed first scope | Detector-only / light metadata |
| Default runtime | DISABLED |
| Next task | INGEST-TIKA-METADATA-POC.0 |

---

## Status

- INGEST-TIKA-METADATA-EVALUATION.0: COMPLETE
- Recommendation: GO_WITH_LIMITS
- Current R2 path: UNCHANGED
