# GCR-2 DATABASE REALITY — Artifact

Base: 3a6d2618 / 35af2f8a. Only canonical migration: platform-app/src/main/resources/db/migration/V1__initial_schema.sql (single V1; FLYWAY_SCRIPT_COUNT=1).

## Artifact-related tables in V1

1. `artifact` — RENDER-OUTPUT ledger (NOT canonical Artifact):
   - id varchar(64) PK
   - render_job_id varchar(64) NOT NULL
   - project_id varchar(64) NOT NULL
   - storage_uri text NOT NULL        ← URI is REQUIRED on the identity record
   - format varchar(32), resolution varchar(32), duration bigint
   - created_at timestamp NOT NULL
   - status varchar(32) NOT NULL DEFAULT 'ACTIVE'
   - tombstoned_at timestamp
   - NO tenant_id, NO content_digest, NO byte_length, NO media_type, NO artifact_kind, NO schema_version
   - indexes: ix_artifact_render_job_id, ix_artifact_project_id, ix_artifact_status

2. `artifact_relation` — id PK, source_artifact_id/target_artifact_id FK→artifact(id) on delete restrict, relation_type, created_at.

3. `media_asset_artifact` — (media_asset_id, artifact_id, relationship) PK; FKs media_asset(id) + artifact(id) on delete restrict.

4. `artifact_node` — render provenance graph node: id PK, job_id FK→render_job(id), type, uri, parent_artifact_ids, hash, metadata, created_at; NOT FK'd to artifact table.

5. `artifact_graph` — render provenance graph: graph_id PK, job_id FK→render_job(id), root_artifact_id FK→artifact_node(id).

6. `storage_object` — blob storage ledger (storage domain): id, bucket, object_key, content_hash, size, created_at (line 149).

7. `storage_reference` — storage_reference_id PK, provider_type, storage_class, root_path, relative_path, checksum, content_hash, file_size, mime_type; UNIQUE(provider_type, root_path, relative_path).

## Answers to directive §9 questions

1. What table represents canonical Artifact? — NONE. `artifact` is a render-output ledger. The canonical
   Artifact domain (ArtifactId+ContentDigest+mediaType+kind+state) has NO table.
2. Multiple competing Artifact tables? — One `artifact` table, TWO competing persistence adapters
   (artifact-module ArtifactCatalogRepository + storage-module ArtifactRepository), both writing
   render-output shape.
3. Where is ContentDigest persisted? — NOWHERE in V1. No content_digest column exists on artifact.
   storage_reference.checksum/content_hash and storage_object.content_hash are storage-side hashes.
4. Is storage URI stored on the identity record? — YES: artifact.storage_uri NOT NULL (identity-adjacent).
5. Are replicas modeled explicitly? — NO artifact_replica table. ArtifactReplicaBinding exists only in
   artifact-module domain (in-memory).
6. Can multiple locations exist per Artifact? — Only via storage_reference (unrelated to artifact id);
   artifact has a single storage_uri column.
7. What DB constraints protect identity/digest? — PK on id only; NO UNIQUE on storage_uri, NO digest
   constraint, NO CHECK on status, NO tenant scoping.
8. Missing same-context constraints? — media_asset_artifact PK covers (asset,artifact,relationship);
   artifact_relation FKs OK. No FK between artifact and storage_reference. No pin table.
9. How are Timeline revision Artifact pins represented? — ONLY inside the internal-1.0 JSON
   (MediaStreamSourceBinding artifactId+contentDigest fields). No relational pin representation.
10. How can GC know a historical revision still requires an Artifact? — IT CANNOT today. No pin table,
    no revision→artifact projection. TimelineAssetGc*/ArtifactGcService have no pin awareness.
11. Does ArtifactCatalog duplicate canonical storage? — The catalog IS the only artifact-table writer
    besides storage-module's ArtifactRepository; the two disagree on shape (catalog adds checksum/
    status/tombstoned_at; storage adds nothing extra). Both are render-output-shaped, so neither is
    canonical.
12. Are render/cache tables hidden Artifact authority? — artifact_node/artifact_graph are render
    provenance (FK render_job), not Artifact authority. storage_object/storage_reference are storage
    data-plane. No table is a hidden canonical Artifact authority; the canonical model simply has no
    persistence at all.

## DB change plan (bounded, GCR-2 scope, single V1 rewrite)

- `artifact` → canonical Artifact record: id (ArtifactId), tenant_id NOT NULL, content_digest NOT NULL,
  byte_length NOT NULL, media_type NOT NULL, artifact_kind NOT NULL, state NOT NULL, schema_version NOT
  NULL, created_at NOT NULL, tombstoned_at NULL. DROP render_job_id NOT NULL → nullable provenance FK
  column render_job_id (kept nullable for render-origin trace, no longer identity). storage_uri moved
  to replica table (see below); artifact keeps NO uri column.
- NEW `artifact_replica`: artifact_id FK→artifact(id), replica_id, provider_id, storage_object_id,
  region, state, created_at; UNIQUE(artifact_id, replica_id).
- NEW `artifact_pin`: pin_id PK, revision_id (timeline revision), artifact_id FK→artifact(id),
  content_digest NOT NULL, project_id, pinned_at; UNIQUE(revision_id, artifact_id) — the historical
  revision pin protection table.
- `artifact_relation`, `media_asset_artifact`, `artifact_node`, `artifact_graph` kept (FK targets
  unchanged; media_asset_artifact now links the canonical artifact table).
- jOOQ regeneration required (typed-schema-module/regenerate-jooq-schema.sh).
