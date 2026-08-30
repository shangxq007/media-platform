# H6 Artifact/Storage clean-forward ledger

Scope: application surfaces and their actual callers/writers across `artifact-module`,
`storage-module`, `storage-provider-opendal`, `media-module`, `timeline-module`,
`render-module`, `platform-app`, `delivery-module`, `workflow-module`, the frontend,
and the consolidated schema. Each row has exactly one disposition. A class name is
not treated as proof of authority. This corrected pre-freeze ledger explicitly
excludes the H4-coordinated frontend/render consumer migration and shadow retirement
from the completed H6 boundary.

| ID | Surface / actual role | Disposition | Clean-forward decision |
|---|---|---|---|
| H6-001 | `artifact.domain.Artifact` immutable logical identity/content/lifecycle record | REUSE_AS_CANONICAL | Canonical Artifact truth; application summaries project it without locations. |
| H6-002 | `shared.identity.ArtifactId` | REUSE_AS_CANONICAL | Stable product-level Artifact identity; never derived from a location. |
| H6-003 | `shared.digest.ContentDigest` | REUSE_AS_CANONICAL | Immutable materialization/integrity identity shared by Artifact and Timeline pins. |
| H6-004 | `ArtifactDescriptor` mutable descriptive bag | DEFER | Not persisted or used by current callers; do not place it in the new boundary until a real metadata authority exists. |
| H6-005 | `ArtifactKind` and `ArtifactMediaType` | REUSE_AS_CANONICAL | Stable Artifact-owned classifications included in the redacted summary. |
| H6-006 | `ArtifactState` / `ArtifactStateMachine` | REUSE_AS_CANONICAL | Artifact owns lifecycle state and transitions. |
| H6-007 | `ArtifactCommitService` / typed commit request and result | REUSE_AS_CANONICAL | Sole producer-facing canonical commit contract. |
| H6-008 | `JooqArtifactCommitService` | REUSE_AS_CANONICAL | Sole durable writer of Artifact, first replica and provenance declarations. |
| H6-009 | `ArtifactQueryService` | REUSE_AS_CANONICAL | Exact tenant-scoped identity, replica and bounded provenance query contract. |
| H6-010 | `JooqArtifactQueryService` / `InMemoryArtifactQueryService` | REUSE_AS_CANONICAL | Canonical implementations retained; replica reads now exclude inactive replicas. |
| H6-011 | `ArtifactRepository` | REUSE_AS_CANONICAL | Canonical persistence adapter; application enumeration does not call an unscoped method. |
| H6-012 | `ArtifactReplicaBinding` and `artifact_replica` writes | REUSE_AS_CANONICAL | Artifact-owned binding from logical identity to storage mechanics; never a frontend projection. |
| H6-013 | `ArtifactRelation`, `ProvenanceEdge`, relation repository/traversal | REUSE_AS_CANONICAL | Artifact-owned bounded provenance; no Timeline semantics are inferred. |
| H6-014 | `ArtifactPinService` / `ArtifactPinRepository` | REUSE_AS_CANONICAL | Artifact owns existence/protection of exact pins, not Timeline source meaning. |
| H6-015 | `ArtifactLifecycleService` | REUSE_AS_CANONICAL | Artifact lifecycle and pin-aware deletion authority retained. |
| H6-016 | `ArtifactGcService` / scheduler | REUSE_AS_CANONICAL | Artifact selects lifecycle-eligible records; physical deletion remains storage mechanics. |
| H6-017 | `ArtifactStorageIntegrityScanner` | MIGRATE_REDESIGN | Existing catalog-URI scan is mechanics-oriented and currently receives no location from canonical catalog rows; future scanner should traverse replica bindings. |
| H6-018 | `ArtifactCatalogEntry` raw-location-era read model | REUSE_MECHANICS_ONLY | Kept for existing import/export/lifecycle mechanics; forbidden from generic product responses. |
| H6-019 | `ArtifactCatalogRepository` | REUSE_MECHANICS_ONLY | Operational projection retained byte-for-byte; the new application boundary does not use it for scoped discovery. |
| H6-020 | `ArtifactCatalogService` registration/fallback projection | BLOCKED_BY_OTHER_AUTHORITY | Its unscoped enumeration methods remain because active render/frontend consumers require coordinated H4 migration. It is explicitly excluded from the completed typed boundary. |
| H6-021 | `/api/artifact/catalog/overview` | REUSE_MECHANICS_ONLY | Operational overview only; it returns counts/status, not Artifact enumeration or storage coordinates. |
| H6-022 | `ArtifactLifecycleController` unscoped mutation URLs | MIGRATE_REDESIGN | Artifact owns the operation, but principal/project-scoped lifecycle HTTP should replace this operational surface separately. |
| H6-023 | `ArtifactScope` | REUSE_AS_APPLICATION_PROJECTION | New mandatory tenant/project/render-job discovery scope. |
| H6-024 | `ArtifactSummary` / HTTP `ArtifactSummaryResponse` | REUSE_AS_APPLICATION_PROJECTION | New redacted typed projection with identity, kind/type, digest, size, lifecycle and honest integrity state. |
| H6-025 | `ArtifactAccess` | REUSE_AS_APPLICATION_PROJECTION | New ephemeral explicit access result; no provider/object coordinates. |
| H6-026 | `ArtifactApplicationQuery` | REUSE_AS_APPLICATION_PROJECTION | New persistence-neutral scoped discovery port. |
| H6-027 | `ArtifactApplicationService` | REUSE_AS_APPLICATION_PROJECTION | New single application boundary; tenant guard and DB scope run before replica/access mechanics. |
| H6-028 | `JooqArtifactApplicationQuery` | REUSE_AS_APPLICATION_PROJECTION | New tenant+project+render-job SQL projection; hard result bound and deterministic order. |
| H6-029 | `ArtifactAccessGrantProvider` | REUSE_MECHANICS_ONLY | Internal application-to-storage port called only after Artifact authorization/scope/state checks. |
| H6-030 | `S3ArtifactAccessGrantProvider` | REUSE_MECHANICS_ONLY | Issues ephemeral signed grants and confines bucket/key parsing to infrastructure. |
| H6-031 | `ArtifactApplicationController` scoped list/access URLs | REUSE_AS_APPLICATION_PROJECTION | Canonical REST adapter for frontend and future adapter reuse; flattened redacted JSON contract. |
| H6-032 | `storage.contract.StorageReference` | REUSE_MECHANICS_ONLY | Physical Product/Asset location record; not Artifact identity or generic Artifact projection. |
| H6-033 | `StorageObjectId`, `StorageReplicaId`, `StorageProviderId`, namespace/placement types | REUSE_MECHANICS_ONLY | Storage data-plane identity and placement mechanics only. |
| H6-034 | `BlobStorage`, `StoredObject`, put/read/delete commands | REUSE_MECHANICS_ONLY | Physical byte data plane; no canonical media or Artifact authority. |
| H6-035 | `StorageCatalogPort` / `StorageCatalogService` | REUSE_MECHANICS_ONLY | Provider inventory only; prior Artifact methods are already absent. |
| H6-036 | `StorageController /api/storage/providers` | REUSE_MECHANICS_ONLY | Operational provider inventory, not product Artifact discovery. |
| H6-037 | `S3ObjectMaterializer` / local and S3 providers | REUSE_MECHANICS_ONLY | Materialization, signing and byte mechanics remain storage owned. |
| H6-038 | OpenDAL provider/location/error/capability/runtime contract surfaces | REUSE_MECHANICS_ONLY | Provider-local mechanics only; no semantic conformance or Artifact authority added. |
| H6-039 | `media.domain.MediaAsset` / `MediaAssetId` | REUSE_AS_CANONICAL | Media module keeps canonical media intent/identity; it is not replaced by Artifact. |
| H6-040 | `MediaAssetArtifactLink` and repository | REUSE_AS_CANONICAL | Typed MediaAsset-to-Artifact relationship retained; no universal Asset object created. |
| H6-041 | media probe observations/normalizer/streams | REUSE_AS_CANONICAL | Media technical authority remains media owned; raw probes are observations. |
| H6-042 | `MediaStreamSourceBinding` exact ArtifactId+digest pin | REUSE_AS_CANONICAL | Timeline-owned immutable source semantics; never resolves mutable latest. |
| H6-043 | `TimelineArtifactPinExtractor` | REUSE_AS_CANONICAL | Timeline extracts exact authored pins from canonical documents. |
| H6-044 | `TimelineArtifactPinValidator` | REUSE_AS_APPLICATION_PROJECTION | Existing smallest cross-module validator uses Artifact query for existence/tenant/digest only. |
| H6-045 | revision save/merge extract-validate-register and historical pin copy | REUSE_AS_CANONICAL | Timeline owns revision validity; Artifact pin rows are registered in the same transaction. |
| H6-046 | `RenderArtifactStorageService` producer | BLOCKED_BY_OTHER_AUTHORITY | H1-owned writer correctly uses ArtifactCommitService but supplies tenant `system`; H1 must pass the real tenant for scoped discovery. |
| H6-047 | `RenderArtifactQueryService` | BLOCKED_BY_OTHER_AUTHORITY | Render-owned Artifact enumeration/content facade is a shadow, but deletion requires synchronized H4 consumer migration; restored byte-for-byte and excluded from H6 completion. |
| H6-048 | `ArtifactInfoResponse` | BLOCKED_BY_OTHER_AUTHORITY | Raw `storageUri` product projection remains on the active H4-facing shadow until H4 migrates its immediate consumers. |
| H6-049 | render `app.access.ArtifactAccessService` | BLOCKED_BY_OTHER_AUTHORITY | Raw-coordinate parsing/presigning shadow remains byte-for-byte because its route consumers are H4-owned; future retirement must use the Artifact access boundary. |
| H6-050 | unscoped render Artifact list/content/access routes | BLOCKED_BY_OTHER_AUTHORITY | Existing routes remain byte-for-byte. Coordinated H4 migration and later shadow deletion are required; `DELETE_SHADOW` is not claimed. |
| H6-051 | `render_job.artifact_uri` execution/result field | BLOCKED_BY_OTHER_AUTHORITY | H1 runtime residue remains internal; H6 does not redefine render execution state. It is excluded from Artifact identity/projections. |
| H6-052 | render Product/StorageReference output registration | REUSE_MECHANICS_ONLY | Existing Product runtime mechanics remain separate; Product is not canonical Artifact identity. |
| H6-053 | `ClientExportArtifactAdapter` catalog registration | MIGRATE_REDESIGN | Internal client-export bridge lacks tenant in its port; future change must pass tenant and canonical commit metadata. |
| H6-054 | `KnownStorageUriIndexService` | REUSE_MECHANICS_ONLY | Operational GC/reference URI index only; never exposed as Artifact discovery. |
| H6-055 | `DeliverySourceResolver` | REUSE_MECHANICS_ONLY | Delivery opens an already-selected source location; it does not identify Artifacts. |
| H6-056 | delivery URI index/reference contributors | REUSE_MECHANICS_ONLY | GC/reference mechanics retained; not product projection or Artifact lifecycle authority. |
| H6-057 | frontend app Artifact schemas/fixtures | BLOCKED_BY_OTHER_AUTHORITY | H6 deltas were reversed byte-for-byte. H4 must migrate the contract from raw storage fields to the redacted Artifact projection. |
| H6-058 | frontend Artifact client/query hooks | BLOCKED_BY_OTHER_AUTHORITY | H6 deltas were reversed byte-for-byte; compatibility with the new response shape belongs to the coordinated H4 migration. |
| H6-059 | render dashboard/smoke Artifact components | BLOCKED_BY_OTHER_AUTHORITY | Direct URI presentation remains unchanged because significant frontend product/presentation is active H4 authority. |
| H6-060 | developer console Artifact surface | BLOCKED_BY_OTHER_AUTHORITY | The H6 consumer rewrite was reversed; H4 owns migration to scoped discovery and explicit access. |
| H6-061 | `RenderOrchestratorPort` and workflow test fakes | BLOCKED_BY_OTHER_AUTHORITY | Artifact query/content methods remain byte-for-byte because removal requires coordinated render/workflow consumer migration. |
| H6-062 | consolidated `artifact`, `artifact_replica`, `artifact_pin`, `artifact_relation` schema | REUSE_AS_CANONICAL | Correct separation of logical identity/digest from physical replica location. |
| H6-063 | legacy `artifact_node` / `artifact_graph` render DAG tables | DEFER | Execution graph residue is not used as product Artifact authority; deletion requires H1 ownership review. |
| H6-064 | `storage_reference` schema | REUSE_MECHANICS_ONLY | Physical Product/Asset storage mechanics; not a canonical Artifact table. |
| H6-065 | project-to-workspace relationship needed for distinct workspace scoping | BLOCKED_BY_OTHER_AUTHORITY | Current identity schema has tenant-owned projects and separate workspaces with no project/workspace relation. H6 uses the real tenant/project/job authority and does not invent that relation. |

Ledger result: 65 rows; 22 `REUSE_AS_CANONICAL` + 8
`REUSE_AS_APPLICATION_PROJECTION` + 17 `REUSE_MECHANICS_ONLY` + 3
`MIGRATE_REDESIGN` + 0 `DELETE_SHADOW` + 13
`BLOCKED_BY_OTHER_AUTHORITY` + 2 `DEFER` = 65; `UNCLASSIFIED=0`.
