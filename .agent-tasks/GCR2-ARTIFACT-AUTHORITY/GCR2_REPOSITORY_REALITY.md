# GCR-2 REPOSITORY REALITY — Artifact Authority

Milestone: GCR2_ARTIFACT_AUTHORITY
Base: 3a6d2618151bc2702356bdf27bd0d174946456d4 / 35af2f8a426d5398ea9584ee881f6a5103a16e2e
Method: behavior-oriented scan of production code, V1 schema, typed jOOQ, tests, guards.

## 1. Type placement today

| Type | Module | Package | Notes |
|---|---|---|---|
| ArtifactId | shared-kernel | shared.identity | tiny immutable identity primitive; used by artifact/timeline/media/extension |
| ContentDigest | storage-module | storage.contract | immutable SHA-256 integrity primitive; used by artifact/timeline/media-execution-plan/storage internals |
| ArtifactRef | shared-kernel | shared.capability | obsolete competing abstraction; PRODUCTION users: extension PluginExecutionResult only (+1 test) |
| Artifact | artifact-module | artifact.domain | immutable canonical Artifact record (ArtifactId+ContentDigest+byteLength+mediaType+kind+state) |
| ArtifactCatalogEntry | artifact-module | artifact.domain | render-output-oriented projection (renderJobId, storageUri, format, resolution, duration, checksum) |
| ArtifactReplicaBinding | artifact-module | artifact.domain | typed binding Artifact→StorageObjectId/StorageReplicaId/StorageProviderId |
| ArtifactCommitService / InMemory impl | artifact-module | artifact.domain | canonical commit contract; IN-MEMORY ONLY — no persistence adapter |
| ArtifactQueryService / InMemory impl | artifact-module | artifact.domain | canonical query contract; IN-MEMORY ONLY |
| ArtifactCatalogService/Repository | artifact-module | artifact.app | persists ArtifactCatalogEntry into `artifact` table (jOOQ) |
| ArtifactGcService / ArtifactLifecycleService | artifact-module | artifact.app | tombstone→purge of catalog entries + BlobStorage delete |
| ArtifactStorageIntegrityScanner | artifact-module | artifact.app | catalog checksum scan |
| ArtifactRelationRepository | artifact-module | artifact.app | artifact_relation rows |
| ArtifactRepository (+ArtifactMetadata) | storage-module | storage.app | SECOND writer of `artifact` table (render-output shape) — DUAL WRITE |
| StorageCatalogPort/Service | storage-module | storage.api/app | registerArtifact → ArtifactRepository (artifact table) + ArtifactRef nested record |
| StorageReference | storage-module | storage.contract | storage_reference table projection (checksum/contentHash) |
| StorageObjectId/StorageReplicaId/StorageProviderId | storage-module | storage.contract | typed storage identity |
| TimelineAssetGc*/Lifecycle/ReferenceScanner/UriResolver | render-module | render.app.timeline | operate on TIMELINE assetRegistry representation-level metadata, NOT artifact rows |
| RenderArtifactRegistry | render-module | render.app.timeline | in-memory render registry |
| RenderCache* | render-module | render.app.timeline | execution cache (artifact fetcher/hasher/reuse/uri) |
| RenderArtifactStorageService | render-module | render.infrastructure | uploads render output → BlobStorage + registerArtifact via StorageCatalogPort |
| RenderArtifactQueryService | render-module | render.app | reads artifacts via StorageCatalogPort (storage-module) |
| RenderJobExecutionService | render-module | render.app | publishes ArtifactCreatedEvent + writes artifact_graph/artifact_node |

## 2. Key findings

1. **DUAL WRITE of `artifact` table**: artifact-module ArtifactCatalogRepository AND storage-module
   ArtifactRepository both insert into jOOQ `ARTIFACT` with different shapes. The `artifact` table
   itself is RENDER-OUTPUT-oriented (render_job_id NOT NULL, storage_uri NOT NULL) — it does NOT
   represent the canonical Artifact domain model (no content_digest column, no tenant, no
   byte_length, no media_type, no replica modeling).

2. **Canonical Artifact domain has NO persistence**: ArtifactCommitService/ArtifactQueryService
   are implemented only by InMemory services. The rich canonical model (Artifact + replicas +
   provenance) exists but nothing persists it. The `artifact` table is a render-output ledger.

3. **ContentDigest in storage-module is the GCR-1 accepted debt**: timeline-module depends on
   storage-module ONLY for MediaStreamSourceBinding's ContentDigest pin. ContentDigest is a pure
   immutable value (validation only) used by 4+ modules — a textbook tiny cross-domain primitive.

4. **shared-kernel ArtifactRef is nearly retired already**: only production user is
   extension-module PluginExecutionResult (List<ArtifactRef> output field); storage StorageCatalogPort
   has its own NESTED ArtifactRef record (unrelated); workflow StartWorkflowExecutionCommand mentions
   "ArtifactRef" only in a comment; render FfmpegRenderToolSelfDescription uses string literal
   "ArtifactReference". K1 guard currently REQUIRES ArtifactRef retention (K1-AR-04/05) — must flip.

5. **Timeline #14 debt confirmed**: TimelineSourceReferenceValidator javadoc explicitly DEFERS
   artifact pin existence validation ("artifact-module -> render-module, so render cannot query the
   artifact catalog without a cycle"). The cycle claim is STALE: artifact-module main code imports
   0 render types; the render-module dependency in artifact-module/build.gradle.kts is unused
   (testFixtures only). timeline-module does NOT currently depend on artifact-module at all.

6. **GC/lifecycle today**: artifact-module ArtifactGcService purges tombstoned CATALOG entries
   (artifact table) and deletes blobs; it does NOT check any historical pin. render TimelineAssetGc*
   operate on timeline document assetRegistry (representation-level) — they are Timeline-side, not
   Artifact authority. No historical revision pin protection mechanism exists anywhere.

7. **media_asset_artifact** FK to `artifact(id)` exists; `artifact` table has no tenant column,
   so the link is tenant-blind. artifact_node/artifact_graph are render provenance (FK render_job).

8. **DB artifact tables**: artifact, artifact_relation, media_asset_artifact, artifact_node,
   artifact_graph, storage_object, storage_reference. NO artifact_replica table, NO artifact_pin/
   reference table, NO content_digest column on artifact.

9. **Dependency facts**: artifact-module → render-module declared but UNUSED in main (0 imports);
   artifact-module → storage-module used (12 imports); artifact-module → shared-kernel used;
   artifact-module → typed-schema-module used (ArtifactCatalogRepository). render-module →
   artifact-module: NO. timeline-module → artifact-module: NO (currently). timeline-module →
   storage-module: YES (ContentDigest only).

## 3. Authority classification summary

- Artifact identity authority: artifact-module (domain Artifact/ArtifactId) — correct home
- Artifact persistence authority: SPLIT/DUAL today (artifact-module catalog repo + storage-module
  ArtifactRepository) → must consolidate to artifact-module
- Artifact storage-binding authority: artifact-module ArtifactReplicaBinding (typed) + storage-module
  StorageObjectRef/StorageReference (physical data-plane) — distinct, correct
- Artifact lifecycle/GC: artifact-module (catalog-based) + render TimelineAssetGc* (timeline-side,
  representation-level) — render GC is NOT artifact authority (it mutates timeline docs)
- Artifact catalog: artifact-module ArtifactCatalogService/Entry — projection onto artifact table
- Execution cache: render RenderCache*/RenderArtifactRegistry — cache, not canonical
- Historical pins: NONE — must be created (GCR-2 core)
- shared-kernel ArtifactRef: obsolete; 1 production user — retire

## 4. Phase A output files

- GCR2_ARTIFACT_AUTHORITY_MANIFEST.tsv (57+ rows generated separately)
- GCR2_DEPENDENCY_MAP.md
- GCR2_DB_REALITY.md
- GCR2_REFERENCE_AND_GC_REALITY.md
