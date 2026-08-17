# GCR-2 CONFLICT / AUTHORITY MATRIX

Base: 3a6d2618 / 35af2f8a. Phase B output — every competing authority identified and resolved.

| # | Conflict | Resolution | Decision |
|---|---|---|---|
| 1 | Artifact (domain, in-memory) vs ArtifactCatalogEntry (projection, persisted) | Artifact is canonical identity+persistence; catalog is projection. Canonical writes route through Artifact commit domain; catalog rebuild cannot mutate identity | EVOLVE |
| 2 | Artifact vs shared-kernel ArtifactRef | ArtifactRef deleted; 1 production caller (PluginExecutionResult) migrates to typed ArtifactId | DELETE |
| 3 | ArtifactId vs ContentDigest | distinct by contract: identity vs integrity; both immutable, both shared-kernel primitives | KEEP |
| 4 | ArtifactId vs storageUri | artifact table drops storage_uri (moves to artifact_replica); URI never identity | EVOLVE |
| 5 | Artifact persistence (artifact-module catalog repo) vs storage-module ArtifactRepository | storage-module ArtifactRepository REPLACED by artifact-module jOOQ ArtifactRepository; storage keeps data-plane only | REPLACE |
| 6 | Artifact lifecycle (artifact-module) vs TimelineAssetGcService (render) | render GC operates on timeline docs (representation-level), NOT artifact rows — not artifact authority; artifact-module GC gains pin awareness | KEEP both, EVOLVE artifact GC |
| 7 | Artifact vs ExecutionCacheEntry (render RenderCache*) | cache is rebuildable optimization; cache delete must not delete protected canonical artifacts; cache not authoritative | KEEP |
| 8 | Artifact vs MediaAsset | separate domains; media_asset_artifact link preserved; no collapse | KEEP |
| 9 | Artifact pin validation (debt #14) vs TimelineSourceReferenceValidator | validator gains artifact existence+digest validation via timeline-owned port (fail closed); canonical JSON shape validation untouched | EVOLVE |
| 10 | Artifact storage binding vs storage-module abstractions | ArtifactReplicaBinding (artifact-module domain) uses StorageObjectId/StorageReplicaId/StorageProviderId (storage contract); artifact_replica table persists bindings | KEEP |
| 11 | Artifact existence vs physical replica availability | artifact table = canonical existence; artifact_replica = mutable physical state; GC of last usable replica of pinned artifact rejected | EVOLVE |
| 12 | artifact_node/artifact_graph (render provenance) vs canonical Artifact | render provenance graph stays render-side (FK render_job); distinct from canonical artifact table | KEEP |
| 13 | storage_object/storage_reference vs Artifact | storage data-plane ledger; not artifact authority | KEEP |

No ambiguous dual authority remains after implementation: exactly one canonical Artifact persistence
(artifact-module jOOQ ArtifactRepository), one lifecycle authority (artifact-module), one pin
protection mechanism (artifact_pin), one integrity primitive (shared-kernel ContentDigest).
