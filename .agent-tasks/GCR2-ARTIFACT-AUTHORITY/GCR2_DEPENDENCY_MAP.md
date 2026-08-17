# GCR-2 DEPENDENCY MAP — Artifact

Base: 3a6d2618 / 35af2f8a. Compiled from build.gradle.kts declarations + verified imports.

## Module dependency facts (verified)

| Module | Depends on | Verified usage |
|---|---|---|
| artifact-module | render-module | DECLARED (line 29) but 0 imports in main → STALE, removable |
| artifact-module | storage-module | 12 imports (ContentDigest, StorageObjectId, StorageReplicaId, StorageProviderId, BlobStorage, StorageObjectRef) |
| artifact-module | shared-kernel | ArtifactId, Ids, events, audit, web |
| artifact-module | typed-schema-module | ArtifactCatalogRepository (jOOQ ARTIFACT) |
| storage-module | (no artifact-module dep) | — |
| timeline-module | storage-module | ONLY ContentDigest (MediaStreamSourceBinding) — GCR-1 debt |
| timeline-module | media-module, audio-module, font-text-module, shared-kernel, typed-schema-module | app services |
| render-module | storage-module | StorageCatalogPort, BlobStorage |
| render-module | artifact-module | NONE |
| media-module | artifact-module | NO (media_asset_artifact link is jOOQ direct) |
| media-execution-plan-module | artifact-module | YES (implementation) |
| identity-access-module | artifact-module | YES (api) |
| platform-app | artifact-module | YES |

## The #14 cycle claim — REALITY CHECK

TimelineSourceReferenceValidator javadoc says: "the current dependency graph is
artifact-module -> render-module, so render cannot query the artifact catalog without a cycle".

Verified reality:
- artifact-module → render-module is DECLARED but UNUSED in main code (0 `import com.example.platform.render`).
  The dependency was likely left from an earlier architecture where BlobStorage lived in render.
- BlobStorage actually lives in storage-module (storage.domain.BlobStorage) — artifact-module already
  imports it from storage-module, NOT render.
- Removing `implementation(project(":render-module"))` from artifact-module/build.gradle.kts is safe
  (main compiles without it; testFixtures dep can stay or be dropped per test needs).

After removal, no cycle exists for:
- timeline-module → artifact-module (NEW: pin existence validation via ArtifactQueryService port)
- render-module → artifact-module (NOT needed; render keeps using storage data-plane for uploads)

## Target dependency direction (GCR-2)

```
shared-kernel (ArtifactId, ContentDigest)          ← tiny immutable primitives
   ↓
artifact-module (canonical Artifact domain + persistence + lifecycle + pins)
   ↓
timeline-module (pins exact ArtifactId+ContentDigest; validates via artifact port)
   ↓
media-execution-plan-module / render-module (consume artifacts downstream)
```

- timeline-module → storage-module FORBIDDEN after ContentDigest moves to shared-kernel
  (TIMELINE_TO_STORAGE_DEPENDENCY_FOR_CONTENT_DIGEST_ONLY = 0)
- artifact-module → render-module FORBIDDEN (stale dep removed)
- artifact-module → storage-module ALLOWED (storage data-plane contracts: StorageObjectId etc.)
- No artifact-module → timeline-module cycle (artifact does not query timeline internals; timeline
  registers pins via artifact-owned API)

## ContentDigest placement decision

Move `storage.contract.ContentDigest` → `shared-kernel` (e.g. `shared.identity.ContentDigest` or a new
`shared.digest` package), because:
- it is a tiny immutable cross-domain primitive (validation only, no lifecycle/storage/persistence)
- exactly one definition today
- sharing it from shared-kernel prevents the timeline→storage cycle
- ArtifactId already lives in shared-kernel (precedent)
- directive §5 permits a tiny cross-domain immutable primitive in shared-kernel when sharing prevents
  dependency cycles AND placement is documented and guarded

Migration: update imports in artifact-module (Artifact, ArtifactCommitRequest, ArtifactQueryService,
InMemoryArtifactQueryService), timeline-module (MediaStreamSourceBinding), media-execution-plan-module
(ExecutionInputBinding), storage-module internals (contract/memory/provider/serialization/validation/
write), storage-provider-opendal. Keep the record shape/validation byte-identical. Guard:
`shared-kernel.ContentDigest` exactly one definition; timeline-module has no storage-module dep.
