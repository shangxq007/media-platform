---
status: blueprint
last_verified: 2026-06-18
scope: all
truth_level: target
owner: platform
---

# Module Blueprint: Artifact & Storage

## 1. Purpose

The Artifact & Storage module manages media artifacts, storage objects, and file lifecycle across the platform.

## 2. Responsibilities

- Manage artifact metadata and relationships
- Coordinate with storage providers (S3, GCS, local)
- Handle artifact lifecycle (creation, archival, deletion)
- Provide artifact search and retrieval
- Manage storage policies and quotas

## 3. Non-Responsibilities

- Actual file upload/download (delegated to providers)
- Media processing (delegated to render-module)
- Billing for storage (delegated to billing-module)

## 4. Public Ports / APIs

### REST API
- `GET /api/v1/artifacts/{id}` - Get artifact
- `GET /api/v1/artifacts` - List artifacts
- `DELETE /api/v1/artifacts/{id}` - Delete artifact
- `GET /api/v1/artifacts/{id}/download` - Download artifact

### Internal APIs
- `ArtifactService` - Core artifact management
- `StorageProvider` - Storage abstraction
- `ArtifactLifecycleManager` - Lifecycle management

## 5. Domain Model

### Artifact
- id, render_job_id, project_id
- storage_uri, format, resolution
- status, created_at, tombstoned_at

### StorageObject
- id, provider_code, bucket
- object_key, content_type
- checksum_sha256, file_size_bytes

## 6. Events Published

- `ArtifactCreated` - When artifact is stored
- `ArtifactDeleted` - When artifact is removed
- `ArtifactArchived` - When artifact is archived

## 7. Events Consumed

- `RenderJobCompleted` - From render-module

## 8. Dependencies Allowed

- `shared-kernel` - For common types
- `identity-access-module` - For tenant context

## 9. Dependencies Forbidden

- Direct storage provider access from other modules
- Direct file system access

## 10. Extension Points

- `StorageProvider` interface - For new storage backends
- `ArtifactTransformer` interface - For artifact processing

## 11. Security / Tenant Rules

- Artifacts are tenant-scoped
- Access control via entitlements
- Signed URLs for temporary access
- Encryption at rest

## 12. Persistence Ownership

- `artifact` table
- `artifact_relation` table
- `storage_object` table

## 13. Observability

- Metrics: storage usage, artifact count
- Traces: upload/download operations
- Logs: lifecycle events

## 14. Current Status

**Status: Partially Implemented**

### Implemented
- Basic artifact CRUD
- Local storage provider
- Simple metadata management

### Not Implemented
- S3/GCS providers
- Advanced lifecycle policies
- Content delivery network integration
- Artifact versioning

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| Cloud storage providers | Local only | High |
| CDN integration | Not implemented | High |
| Lifecycle policies | Basic | Medium |
| Artifact versioning | Not implemented | Medium |
| Search/indexing | Not implemented | Medium |
