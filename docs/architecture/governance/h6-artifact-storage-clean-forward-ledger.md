# H6 Artifact/Storage consumer migration ledger

Scope: production Java under `*/src/main`, production frontend under `frontend/src`, and the
Artifact/Storage authority guard. Tests, generated jOOQ records, built frontend assets, docs,
and fixtures are excluded from production-hit arithmetic.

## Mechanical inventory

Inventory command:

```text
rg -n -i --glob '**/src/main/**/*.java' --glob 'frontend/src/**' \
  --glob '!typed-schema-module/src/main/**' --glob '!platform-app/src/main/resources/**' \
  '(storageUri|storage_uri|storageUrl|storage_url|artifactUri|artifact_uri|objectKey|object_key|bucketName|bucket_name|filesystem|filePath|file_path)'
```

The current tree contains 732 location/mechanics lines. Each line belongs to exactly one
module family below. `INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED` means the value remains in a
byte movement, provider/runtime handoff, import rollback, delivery, GC, or maintenance path
and is not returned as canonical Artifact/product state. Runtime/provider entries are recorded
but not redesigned by H6. `FALSE_POSITIVE` entries are sandbox filesystem-policy vocabulary,
not media locations.

| Production family | Hits | Disposition | Classification evidence |
|---|---:|---|---|
| `artifact-module` | 3 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Replica maintenance/access mechanics; public summary/access records contain no coordinates. |
| `audit-compliance-module` | 1 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Non-Artifact runtime event handling; Artifact-created audit payload is redacted. |
| `delivery-module` | 32 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Delivery source/destination transport and admin reconciliation mechanics. |
| `extension-module` | 3 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Extension package retrieval mechanics, not Artifact identity. |
| `ffmpeg-provider-module` | 6 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Provider-local input/output file mechanics. |
| `identity-access-module` | 21 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Legacy import mechanics outside the reconciled project-import writer remain inventoried; project Artifact enumeration/export signer shadow was deleted. |
| `outbox-event-module` | 1 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Non-Artifact runtime event transport; Artifact-created payload is coordinate-free. |
| `platform-app` | 45 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Admin orphan/GC, ingest, and remote-worker runtime mechanics; asset product DTO coordinates were removed. |
| `remote-render-worker` | 10 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Remote provider callback/runtime mechanics, explicitly outside H6 provider redesign. |
| `render-module` | 317 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Render/provider execution mechanics; Render Artifact query/content/access product shadows were deleted. |
| `sandbox-isolation-module` | 131 | FALSE_POSITIVE | Filesystem isolation/capability policy vocabulary. |
| `sandbox-worker` | 1 | FALSE_POSITIVE | `readOnlyRootFilesystem` sandbox hardening vocabulary. |
| `shared-kernel` | 9 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Non-Artifact runtime/delivery events and storage maintenance ports; Artifact events are redacted. |
| `storage-module` | 121 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Canonical byte data-plane contracts and provider mechanics. |
| `storage-provider-opendal` | 16 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | OpenDAL provider configuration/location codec mechanics. |
| `timeline-module` | 14 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Import/materialization mechanics; immutable Artifact pin authority remains typed and exact. |
| `worker-fabric-module` | 1 | INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED | Worker execution filesystem requirement. |
| **Current production arithmetic** | **732** |  | **600 INTERNAL_STORAGE_IMPLEMENTATION_ALLOWED + 132 FALSE_POSITIVE = 732** |

## Migrated and subtracted surfaces

| Surface | Disposition | Clean-forward result |
|---|---|---|
| Render `ArtifactInfoResponse` raw result DTO | DELETE_SHADOW | Deleted. |
| Render `RenderArtifactQueryService` catalog/byte facade | DELETE_SHADOW | Deleted; no Render-owned Artifact query authority remains. |
| Render `ArtifactAccessService` raw-coordinate presigner | DELETE_SHADOW | Deleted. |
| Render unscoped Artifact list/content/access routes | DELETE_SHADOW | Deleted; the duplicate scoped access route was also removed. |
| `RenderOrchestratorPort` Artifact query/content methods and Workflow fakes | DELETE_SHADOW | Deleted without changing Workflow invocation methods. |
| Frontend Artifact transport contract | MIGRATE_TO_ARTIFACT_SUMMARY | Now validates the full redacted summary: identity, kind/type, digest, length, lifecycle, integrity, time. |
| Frontend diagnostic Artifact list | MIGRATE_TO_SCOPED_QUERY | Uses tenant + project + render-job route and list envelope. |
| Frontend diagnostic preview/download | MIGRATE_TO_ARTIFACT_ACCESS | Requests ephemeral access on action; no canonical/content URL is stored. |
| Frontend legacy Render Artifact access descriptor/client | DELETE_SHADOW | Deleted in favor of the canonical Artifact client. |
| Project linked-asset export catalog enumeration/signer bridge | DELETE_SHADOW | Adapter, raw DTO, and port deleted; mode fails closed because no exact render-job scope exists. |
| Client-export Artifact compatibility port/adapter | DELETE_SHADOW | Deleted; upload remains session-local and exposes no canonical Artifact registration option until Storage issuance is available. |
| Project-import catalog registration | DELETE_SHADOW | Raw catalog registration caller deleted; `download_and_register` fails closed before side effects until Storage issuance can precede the Artifact owner write. |
| Artifact raw-placement integrity scanner | DELETE_SHADOW | Deleted; Artifact no longer interprets logical Storage identity as a physical URI or probes `BlobStorage`. |
| Artifact contribution to raw URI orphan index | DELETE_SHADOW | Deleted; platform maintenance no longer reconstructs a physical URI from Artifact replica bindings. |
| S3 Artifact access materializer | DELETE_SHADOW | Deleted because it interpreted logical `StorageObjectId` as an S3 coordinate; access now requires a future Storage-owned typed grant provider. |
| Render output physical-to-logical writer | DELETE_SHADOW | Finalization now fails closed before side effects until Storage returns canonical identity and placement evidence. |
| Artifact catalog in-memory registration/fallback map | DELETE_SHADOW | Deleted; catalog residue is read-only lifecycle projection and cannot write identity. |
| `ArtifactCatalogEntry.storageUri` | DELETE_SHADOW | Coordinate removed from the lifecycle projection. |
| Artifact-created/tombstoned raw-coordinate events | MIGRATE_TO_ARTIFACT_SUMMARY | Created event retains safe identity/scope/time only; tombstone-to-Timeline event/listener deleted. |
| Asset version/workbench/search/embedding coordinate fields and raw registration/enrichment routes | DELETE_SHADOW | Coordinate fields/routes removed without introducing another media authority. |

Completed-surface arithmetic: 20 rows = 16 `DELETE_SHADOW` + 2
`MIGRATE_TO_ARTIFACT_SUMMARY` + 1 `MIGRATE_TO_SCOPED_QUERY` + 1
`MIGRATE_TO_ARTIFACT_ACCESS`;
the table itself is authoritative and contains no deferred or compatibility disposition.

## Closed counts

The semantic guard computes these from current production definitions/callers and runs an
independent mutation for every counter:

```text
RAW_STORAGE_URI_PRODUCT_PROJECTION_COUNT=0
UNSCOPED_ARTIFACT_ENUMERATION_COUNT=0
OLD_ARTIFACT_ACCESS_CALLER_COUNT=0
OLD_STORAGE_URI_DTO_USAGE_COUNT=0
COMPATIBILITY_WRAPPER_COUNT=0
DUAL_ARTIFACT_AUTHORITY_COUNT=0
MUTABLE_LATEST_ARTIFACT_AUTHORITY_COUNT=0
ARTIFACT_TO_TIMELINE_AUTHORITY_COUNT=0
STORAGE_CANONICAL_MEDIA_AUTHORITY_COUNT=0
UNCLASSIFIED=0
```
