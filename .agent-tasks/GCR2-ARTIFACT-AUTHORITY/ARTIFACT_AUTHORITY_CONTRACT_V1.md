# ARTIFACT_AUTHORITY_CONTRACT_V1

Milestone: GCR2_ARTIFACT_AUTHORITY
Base: 3a6d2618151bc2702356bdf27bd0d174946456d4 / 35af2f8a426d5398ea9584ee881f6a5103a16e2e
Status: FROZEN (Phase C). Any weakening requires ARCHITECTURE_ESCALATION_REQUIRED=YES.

## Frozen items C1–C20

C1. Artifact module is the SOLE Artifact semantic authority. Canonical Artifact identity,
    persistence, lifecycle, and pin protection live in artifact-module.

C2. ArtifactId is stable logical Artifact identity (shared-kernel `shared.identity.ArtifactId`).
    ArtifactId does NOT change when replica location changes.

C3. ContentDigest is content integrity, immutable after Artifact creation. Moved to shared-kernel
    (`shared.digest.ContentDigest`) as a tiny cross-domain primitive (exactly one definition;
    sharing prevents the timeline→storage cycle). Byte-identical record semantics.

C4. Storage location is NOT Artifact identity. `artifact.storage_uri` removed from the canonical
    record; physical locations live in `artifact_replica`.

C5. An Artifact may have 0..N replica/storage bindings (`artifact_replica`), each typed via
    storage-module contracts (StorageObjectId/StorageReplicaId/StorageProviderId). Replica state is
    mutable infrastructure state.

C6. ArtifactCatalogEntry is a PROJECTION/read model only. Catalog rebuild/delete must not mutate
    canonical Artifact identity. No canonical mutation routes through the catalog.

C7. MediaAsset and Artifact remain separate domain concepts; `media_asset_artifact` link preserved;
    no UniversalAsset collapse.

C8. ExecutionCacheEntry (render RenderCache*/RenderArtifactRegistry) is NOT canonical Artifact
    authority; cache delete must not delete protected canonical artifacts; cache hit/miss must not
    change Timeline semantics.

C9. Derived/generated/intermediate durable media uses Artifact + provenance (ArtifactCommitRequest
    provenanceDeclarations); no new competing GeneratedAsset/DerivedAsset/RenderAsset hierarchy.

C10. Historical Timeline revisions protect Artifact reproducibility via a NEW `artifact_pin` table
     (revision_id, artifact_id FK, content_digest, project_id, pinned_at; UNIQUE(revision_id,
     artifact_id)). Pinned artifacts cannot be destructively GC'd; last usable required replica
     deletion rejected.

C11. Missing ArtifactId or digest mismatch FAILS CLOSED before a new revision may commit a
     SourceBinding pin.

C12. Reference-existence validation is APPLICATION/reference-integrity validation
     (TimelineSourceReferenceValidator + artifact lookup port), NOT pure Timeline JSON
     canonicalization. E1b canonical JSON gate unchanged.

C13. Artifact GC/lifecycle is NOT Render authority. Render TimelineAssetGc* stay timeline-side
     (representation-level assetRegistry only); artifact-module GC owns canonical lifecycle and
     consults pins.

C14. Pinned Artifact deletion / last-required-replica deletion is REJECTED by ArtifactGcService/
     ArtifactLifecycleService (HISTORICAL_PIN_GC_BYPASS_COUNT = 0).

C15. shared-kernel ArtifactRef is REMOVED (1 production caller migrated to typed ArtifactId;
     storage nested record replaced; K1 guard updated to assert absence).

C16. No storage URI is canonical identity anywhere (artifact table drops storage_uri; timeline
     canonical JSON unchanged — representation-level storageUri fields in timeline docs remain
     non-identity and are out of scope unless they claim canonical identity).

C17. No compatibility/dual model retained: storage-module ArtifactRepository REPLACED by
     artifact-module jOOQ ArtifactRepository; no ArtifactRefV2/LegacyArtifactRef/adapters.

C18. Canonical Artifact persistence represented in single V1 (artifact canonical columns,
     artifact_replica, artifact_pin) and regenerated typed jOOQ.

C19. Artifact persistence and storage/data-plane concerns remain distinct: artifact-module owns
     semantic persistence; storage-module owns physical operations; artifact-module depends on
     storage-module contracts (allowed), NOT the reverse.

C20. Full Intermediate Artifact Lifecycle / Provenance foundation remains OUTSIDE GCR-2 except
     minimum hooks: pin protection table, pin existence/digest validation, GC pin checks,
     artifact_replica for binding. No billing/economics/retention tiers.

## Dependency constraints

- artifact-module → render-module: REMOVED (stale; 0 main imports) — fixes the #14 cycle premise.
- timeline-module → storage-module: REMOVED (ContentDigest moves to shared-kernel) →
  TIMELINE_TO_STORAGE_DEPENDENCY_FOR_CONTENT_DIGEST_ONLY = 0.
- timeline-module → artifact-module: ADDED (artifact lookup port for pin validation).
- artifact-module → storage-module: KEPT (storage contracts).
- No artifact-module → timeline-module, no artifact-module → render-module.

## DB (single V1 rewrite, FLYWAY_SCRIPT_COUNT=1)

- `artifact`: id PK, tenant_id NOT NULL, content_digest NOT NULL, byte_length NOT NULL,
  media_type NOT NULL, artifact_kind NOT NULL, state NOT NULL, schema_version NOT NULL,
  created_at NOT NULL, tombstoned_at NULL, render_job_id NULL (nullable provenance trace).
- `artifact_replica`: artifact_id FK→artifact(id), replica_id, provider_id, storage_object_id,
  region, state, created_at; UNIQUE(artifact_id, replica_id).
- `artifact_pin`: pin_id PK, revision_id, artifact_id FK→artifact(id), content_digest NOT NULL,
  project_id, pinned_at; UNIQUE(revision_id, artifact_id).
- artifact_relation / media_asset_artifact / artifact_node / artifact_graph / storage_object /
  storage_reference: unchanged.
- jOOQ regenerated from empty PostgreSQL; parity verified.
