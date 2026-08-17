# GCR-2 REFERENCE AND GC REALITY — Artifact

Base: 3a6d2618 / 35af2f8a.

## 1. Timeline artifact pin representation (today)

Internal-1.0 timeline JSON → TimelineClip → MediaStreamSourceBinding(artifactId, contentDigest, ...).
The pin (ArtifactId + ContentDigest) lives ONLY in the JSON document. TimelineSourceReferenceValidator
validates MediaAsset/MediaStream existence but EXPLICITLY DEFERS artifact pin existence (debt #14):

    "artifact pin (artifactId/contentDigest) existence check is DEFERRED: the current
     dependency graph is artifact-module -> render-module, so render cannot query the
     artifact catalog without a cycle"

Verified: the cycle premise is stale (artifact-module main has 0 render imports; BlobStorage is in
storage-module). Fixing the dependency graph makes pin validation implementable.

## 2. GC / lifecycle services (behavior, not names)

| Service | Module | Behavior | Artifact authority? |
|---|---|---|---|
| ArtifactGcService | artifact-module app | purges tombstoned catalog rows (artifact table) + deletes blob | YES (catalog-scoped) — but NO pin awareness |
| ArtifactLifecycleService | artifact-module app | delete-check + tombstone on catalog entries | YES (catalog-scoped) |
| ArtifactStorageIntegrityScanner | artifact-module app | scans catalog checksum vs storage | YES (catalog-scoped) |
| ArtifactGcScheduler | artifact-module app | scheduled runGc | YES (catalog-scoped) |
| TimelineAssetGcService | render app.timeline | mutates TIMELINE document assetRegistry (representation-level metadata) + blob delete | NO — timeline-side registry maintenance |
| TimelineAssetLifecycleService | render app.timeline | deleteCheck/tombstone on timeline assetRegistry entries | NO — timeline-side |
| TimelineAssetGcScheduler | render app.timeline | scheduled timeline asset gc | NO — timeline-side |
| TimelineAssetIntegrityScanner | render app.timeline | timeline registry integrity | NO — timeline-side |
| TimelineAssetReferenceScanner | render app.timeline | reference scan helper | NO |
| TimelineAssetUriResolver | render app.timeline | uri resolution for timeline assets | NO |
| RenderArtifactRegistry | render app.timeline | in-memory render artifact registry | NO (cache/registry) |
| RenderCacheArtifactFetcher/Hasher/ReuseValidator/UriResolver | render app.timeline | execution cache | NO (cache) |

Conclusion: render-side GC classes operate on Timeline documents (representation-level assetRegistry),
NOT canonical Artifact rows — they are NOT Artifact authority. The artifact-module GC services ARE
Artifact lifecycle authority but currently ignore historical pins (no pin table exists).

## 3. Required GCR-2 mechanisms

1. **Historical revision pin protection**: NEW `artifact_pin` table (revision_id, artifact_id FK,
   content_digest, project_id, pinned_at; UNIQUE(revision_id, artifact_id)). Timeline revision
   application authority registers pins when a revision containing source bindings is recorded;
   ArtifactGcService rejects destructive purge of pinned artifacts (and of their last usable replica).

2. **Timeline pin existence validation (debt #14)**: timeline-module gains an artifact lookup port
   implemented by artifact-module (ArtifactQueryService.getArtifact(tenant, artifactId) + digest
   match). TimelineSourceReferenceValidator (or revision-record path) validates: artifact exists AND
   ContentDigest matches the pin; missing/mismatch → fail closed at NEW revision creation. Historical
   revisions remain readable, never rewritten.

3. **GC bypass**: ArtifactGcService checks artifact_pin before purge; pinned → reject; unpinned →
   policy may delete. Render GC classes untouched (not artifact authority) but their blob deletes must
   respect artifact-module pin protection (documented boundary, no new coupling).

4. **Replica protection**: artifact_replica table; pinned artifact's last usable replica deletion
   rejected (replica rows for pinned artifacts are protected at artifact lifecycle layer).

## 4. Caller evidence (directive §20) — collected

- ArtifactCatalogService/Repository: artifact-module app/domain; ArtifactController(overview),
  ArtifactLifecycleController(delete-check/tombstone/gc/run), ArtifactGcScheduler.
- ArtifactRepository (storage-module): used ONLY by StorageCatalogService.registerArtifact/
  findArtifactsByJob/findArtifactsByProject/findArtifact; reached from RenderArtifactStorageService
  (render) and RenderArtifactQueryService (render) via StorageCatalogPort.
- ArtifactRef (shared-kernel): PluginExecutionResult (extension) — List<ArtifactRef> output field;
  PluginRuntimeRedMatrixTest (extension test); StorageCatalogPort.ArtifactRef is a SEPARATE nested
  record. Workflow StartWorkflowExecutionCommand inputRefsJson comment-only.
- TimelineSourceReferenceValidator: timeline-module app; called by TimelineRevisionService
  (revision record path, to be verified at implementation) and TimelineEditorSyncService.
- RenderArtifactStorageService.uploadJobOutput: uploads rendered bytes → BlobStorage →
  storageCatalogPort.registerArtifact (artifact table write) — the only production artifact-table
  WRITE from render.
