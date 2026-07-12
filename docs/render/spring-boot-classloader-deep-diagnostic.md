# Spring Boot Classloader and MVC Route Registration Diagnostic

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** SPRING-BOOT-CLASSLOADER-DEEP-DIAG.0

---

## Root Cause: FOUND

**Dockerfile.incremental copied JAR to wrong path.**

| Path | Content | Used by Java |
|------|---------|-------------|
| `/workspace/app.jar` | New JAR (with new methods) | ❌ Never |
| `/app/app.jar` | Old JAR (without new methods) | ✅ Always |

The `ENTRYPOINT` runs `java -jar /app/app.jar` but `Dockerfile.incremental` copied to `/workspace/app.jar`.

---

## Additional Issues Found and Fixed

| Issue | Fix |
|-------|-----|
| `storageController` bean conflict | Renamed to `StorageRuntimeController` |
| Duplicate `ArtifactContentController` | Deleted (endpoints in `RenderController`) |
| Duplicate `PreviewMediaController` | Deleted (endpoints in `RenderController`) |

---

## Verification

After fix, diagnostic output confirms:
```
Has uploadPreviewMedia: true
Method uploadPreviewMedia annotations: @PostMapping("/preview/media")
```

Full E2E test passes:
- Upload: ✅
- Submit: ✅ COMPLETED
- Artifacts: ✅
- Content download: ✅ HTTP 200, 6996 bytes, MP4

---

## Classification

- REAL-MEDIA-INPUT.0: **COMPLETE**
- SPRING-BOOT-CLASSLOADER-DEEP-DIAG.0: **COMPLETE**
