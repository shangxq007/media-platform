---
status: blueprint
created: 2026-06-28
scope: platform-wide
truth_level: target
owner: platform
---

# External Channel Extension Model

> **Purpose:** Reserve future extension points for external input/output channels without implementing them.
> **Scope:** Documentation-only reservation. No code, no tables, no controllers, no APIs.
> **Cross-references:** [Storage Runtime](../storage-runtime.md), [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md), [Platform Constitution](../platform-constitution-v1.md)

---

## 1. StorageRuntime Boundary Clarification

### What StorageRuntime Is

StorageRuntime is the **platform-controlled canonical storage runtime**. It owns:

- Registering internal `StorageReference` records
- Materialization (downloading/streaming objects to local paths)
- Checksum verification (SHA-256)
- Controlled internal read/write
- Render input access
- Future retention/lifecycle policy
- Product storage lineage support

### Current StorageRuntime Provider Types

The accepted `StorageProviderType` values are intentionally minimal:

| Value | Description |
|-------|-------------|
| `LOCAL` | Local filesystem |
| `S3` | Generic S3-compatible object storage (preferred) |
| `S3_COMPATIBLE` | Explicit S3-compatible alias |
| `OBJECT_STORAGE` | Storage-neutral alias |

### What StorageRuntime Is NOT

StorageRuntime is **not** the whole storage abstraction for the platform. It is an internal runtime for platform-controlled canonical storage.

### S3-Compatible Protocol Clarification

- S3-compatible is an **object storage protocol/adapter** under StorageRuntime, not the whole storage abstraction.
- **RustFS** is the current dev S3-compatible backend.
- **SeaweedFS** is a future S3-compatible compatibility target.
- **MinIO** is not default and not an active provider type.
- **GCS/Azure/OSS** are future native provider candidates, not current active S3-compatible values.
- **SFTP/FTP/WebDAV** are not current StorageRuntime provider types.

### Provider Type Policy

- Backend-specific names (RustFS, SeaweedFS, MinIO) are **not** provider types — they are deployment backends behind a generic S3-compatible endpoint.
- GCS, Azure Blob, OSS may become future native providers but require explicit compatibility validation before acceptance.
- `S3_COMPATIBLE` and `OBJECT_STORAGE` serve as the storage-neutral aliases for any S3-compatible endpoint.

---

## 2. External Channel Extension Model

External Channel concepts represent **user-owned systems, external storage, client-pushed assets, external triggers, and output destinations**. They are outside StorageRuntime.

### Extension Surface

```
ExternalChannel
  ├── ClientPush
  ├── ExternalStorageConnection
  ├── IngestSource
  ├── WatchSource / TriggerSource
  └── DeliveryTarget
```

### Core Principle

External Channel concepts do **not** replace Product canonical storage by default. The platform's canonical Product storage remains StorageRuntime-controlled.

### Relationship to Existing Platform Concepts

| External Channel Concept | Relates To | Relationship |
|--------------------------|-----------|--------------|
| ExternalStorageConnection | StorageRuntime | External connection is outside StorageRuntime; ingested objects flow through StorageRuntime to become Products |
| IngestSource | ProductRuntime, StorageRuntime | Ingest creates RAW_MEDIA Products via StorageRuntime.register() |
| WatchSource / TriggerSource | WorkflowDefinition, WorkflowRun | Triggers create WorkflowRuns or CapabilityRequests |
| DeliveryTarget | Product, StorageRuntime | Delivery reads FINAL_RENDER Products and writes to external targets |
| ClientPush | Product, TimelineRevision, RenderJob | Client uploads create Products and optionally TimelineRevisions |

---

## 3. Client Push Reservation

### Concept

Client Push enables local clients (CLI, desktop app) to push media assets and OTIO/timeline manifests to the platform.

### Future Flow

```
local client / CLI / desktop app
  → push media assets + OTIO/timeline manifest
  → platform creates Product / TimelineRevision / RenderJob or CapabilityRequest
  → platform performs render/analyze/package/delivery
  → client polls status or pulls result
```

### Reserved Future Concepts

| Concept | Purpose |
|---------|---------|
| ClientPushSession | Tracks a client push session (authentication, project context) |
| UploadSession | Manages chunked upload of media assets |
| UploadPart | Individual chunk of an upload |
| UploadComplete | Signals upload completion, triggers ingest |
| TimelineManifestImport | Imports OTIO/timeline manifest from client |
| ClientRenderSubmit | Submits render from client context |
| ClientProductPull | Client pulls completed Product result |

### Future CLI Flow

```bash
media-platform login
media-platform project init
media-platform asset upload ./input.mp4
media-platform timeline push ./timeline.otio
media-platform render submit --profile default_1080p
media-platform render status
media-platform product pull final-render
```

### Constraints

- Client Push is an **input channel**.
- Client Push must go through **public API boundaries**.
- Client Push must **not** directly write Product tables.
- Client Push must **not** expose internal StorageReference/bucket/key/local path.
- Upload may use a future controlled upload capability, but Product result APIs must not expose signed URLs by default.

---

## 4. External Storage Trigger Reservation

### Concept

External storage triggers enable the platform to detect new media in user-owned storage systems and automatically create ingest/render jobs.

### Future Flow

```
user-owned S3/SFTP/WebDAV/NAS/GCS/Azure/etc.
  → user grants read/list/watch access
  → user creates media files, manifest, or ready marker
  → platform detects event
  → platform creates IngestJob / WorkflowRun / CapabilityRequest
```

### Reserved Future Concepts

| Concept | Purpose |
|---------|---------|
| ExternalStorageConnection | User-owned storage connection (credentials, endpoint, access grants) |
| CredentialRef | Reference to stored credentials (Vault, env, etc.) |
| StorageWatchSource | Configuration for watching a storage location |
| StorageTriggerEvent | Event detected from external storage |
| ManifestTrigger | Trigger based on manifest file appearance |
| ReadyMarkerTrigger | Trigger based on ready-marker file appearance |
| IngestJob | Job that ingests external media into platform Products |

### Trigger Modes

| Mode | Description | Suitable For |
|------|-------------|-------------|
| POLLING | Periodically check for new files | SFTP, FTP, WebDAV, NAS |
| WEBHOOK | Receive push notifications | S3 (SNS), GCS (Pub/Sub), Azure (Event Grid) |
| CLOUD_EVENT | CloudEvents-compatible events | S3 (EventBridge), GCS, Azure |
| MANIFEST_SCAN | Scan for manifest files listing new assets | S3, any object store |
| READY_MARKER | Watch for ready-marker file indicating ingest readiness | S3, any object store |

### Storage-Specific Notes

- **S3-style object stores** should prefer manifest or ready-marker patterns because object stores do not have real directory semantics.
- **SFTP/FTP/WebDAV/NAS** may use polling.
- **AWS S3** may later use EventBridge/SNS/SQS.
- **GCS** may later use Pub/Sub.
- **Azure Blob** may later use Event Grid.
- These are **future trigger integrations**, not current provider types.

### Constraints

- ExternalStorageConnection is a **platform-level boundary resource**, not a WorkflowNode.
- Credentials must be stored securely (Vault or equivalent), not in plaintext config.
- Trigger rules should not be embedded directly inside workflow node definitions.
- Ingest must flow through StorageRuntime to create canonical Products.

---

## 5. DeliveryTarget Reservation

### Concept

DeliveryTarget enables the platform to deliver FINAL_RENDER Products to user-owned storage/systems.

### Future Flow

```
FINAL_RENDER Product
  → DeliveryJob
  → user-owned storage/system
  → DeliveryReceipt
```

### Reserved Future Concepts

| Concept | Purpose |
|---------|---------|
| DeliveryTarget | External delivery destination (type, endpoint, credentials) |
| DeliveryJob | Job that delivers a Product to a DeliveryTarget |
| DeliveryReceipt | Records external delivery status, checksum, timestamp, external reference |
| DeliveryPolicy | Rules governing automatic delivery (triggers, retries, retention) |
| DeliveryCredentialRef | Reference to stored credentials for delivery target |

### Future Target Types

| Type | Description | Status |
|------|-------------|--------|
| S3_COMPATIBLE | S3-compatible object storage endpoint | Future |
| SFTP | SFTP file transfer | Future |
| FTPS | FTPS file transfer | Future |
| WEBDAV | WebDAV endpoint | Future |
| HTTP_CALLBACK | HTTP webhook callback | Future |
| MOUNTED_EXPORT | Mounted filesystem export | Future |
| GCS_NATIVE | Google Cloud Storage native API | Future |
| AZURE_BLOB | Azure Blob Storage native API | Future |
| ALIYUN_OSS_NATIVE | Alibaba Cloud OSS native API | Future |

### Constraints

- DeliveryTarget is **not** Product canonical storage.
- Product remains stored in platform-controlled StorageRuntime.
- DeliveryReceipt records external delivery status internally.
- Public APIs must not expose secrets, internal bucket/key, raw paths, or signed URLs by default.

---

## 6. Relationship with Componentized Workflow Nodes

### Core Principle

ExternalStorageConnection / WatchSource / IngestSource / DeliveryTarget are **platform-level boundary resources**. WorkflowNode is an **execution-level orchestration step**. They are related by **references**, not merged into one concept.

### Recommended Mapping

```
WatchSource / TriggerSource
  → creates StorageTriggerEvent
  → creates WorkflowRun or CapabilityRequest

IngestNode
  → references IngestSource or StorageTriggerEvent
  → creates Product

RenderNode
  → references Product / TimelineRevision
  → creates FINAL_RENDER Product

DeliveryNode
  → references DeliveryTarget
  → creates DeliveryJob / DeliveryReceipt
```

### GitHub Actions Analogy

| Platform Concept | GitHub Actions Analogy |
|-----------------|----------------------|
| WatchSource | `on: push / schedule / workflow_dispatch` |
| WorkflowDefinition | workflow YAML |
| WorkflowNode | job/step |
| Product | artifact |
| DeliveryTarget | deployment target |
| ExecutionEnvironment | runner |

### Design Rules

- ExternalStorageConnection is **not** a node.
- WatchSource is **not** a normal node; it is a **trigger source**.
- Ingest and Delivery may be workflow nodes.
- Connections, credentials, and trigger rules should **not** be embedded directly inside workflow node definitions.
- Workflow nodes should reference stable IDs such as `ingestSourceId`, `deliveryTargetId`, or `watchSourceId`.

### Existing Platform Concepts

| Concept | Current State | Relevance |
|---------|--------------|-----------|
| WorkflowDefinition | Referenced in OTIO blueprint (L5-L7) | Future orchestration backbone |
| WorkflowNode | Referenced in OTIO blueprint | Future execution step |
| ExecutionGraph | Referenced in OTIO blueprint (L7) | Future execution plan |
| CapabilityRequest | Referenced in OTIO blueprint (L5) | Future capability-based dispatch |
| RenderJob | Implemented | Current render submission model |
| Product | Implemented | Canonical communication object |
| ProductDependency | Implemented | Dependency lineage |
| TimelineRevision | Implemented | Timeline versioning |

---

## 7. Product Canonical Storage Principle

### Strong Principle

**Product canonical storage should remain platform-controlled StorageRuntime by default.**

### Ingest Pattern

External user-owned objects should normally be ingested:

```
External object
  → IngestJob
  → StorageRuntime.register()
  → Product RAW_MEDIA
```

### Delivery Pattern

Output delivery should normally follow:

```
FINAL_RENDER Product
  → DeliveryJob
  → external target
  → DeliveryReceipt
```

### What NOT to Do

Do **not** model user-owned external storage as the default canonical Product StorageReference.

### Future Optional Mode: EXTERNAL_REFERENCE

Reserve a future optional mode:

```
EXTERNAL_REFERENCE mode
```

This mode would allow Products to reference external objects directly without ingesting them into StorageRuntime. However, this is **deferred** and should be treated as weaker:

- External availability dependent
- Permission expiry risk
- Reproducibility risk
- Checksum/version pinning required
- Not default

**Do not implement EXTERNAL_REFERENCE now.**

---

## 8. Flow Diagrams

### Client Push Flow

```
Local Client
  → UploadSession
  → StorageRuntime
  → RAW_MEDIA Product
  → TimelineRevision
  → RenderJob / CapabilityRequest
  → FINAL_RENDER Product
  → Product pull or DeliveryTarget
```

### External Storage Trigger Flow

```
ExternalStorageConnection
  → WatchSource
  → StorageTriggerEvent
  → IngestJob
  → StorageRuntime
  → Product
  → WorkflowRun / CapabilityRequest
```

### Delivery Flow

```
FINAL_RENDER Product
  → DeliveryJob
  → DeliveryTarget
  → DeliveryReceipt
```

### Workflow Reference Flow

```
WatchSource
  → WorkflowRun
  → IngestNode
  → RenderNode
  → DeliveryNode
```

---

## 9. Non-Goals / Deferred Implementation

This reservation **does not implement**:

- ExternalStorageConnection tables
- CredentialRef secret storage
- WatchSource polling
- Trigger scheduler
- Client Push API
- UploadSession API
- DeliveryTarget API
- DeliveryJob
- DeliveryReceipt
- Workflow runtime
- SFTP/WebDAV/FTP adapters
- GCS/Azure native providers
- User-owned storage as canonical Product storage
- EXTERNAL_REFERENCE mode

---

## 10. Summary

| Extension Point | Status | Relates To |
|----------------|--------|-----------|
| ClientPush | Reserved (no implementation) | Product, TimelineRevision, RenderJob |
| ExternalStorageConnection | Reserved (no implementation) | StorageRuntime (indirect) |
| IngestSource | Reserved (no implementation) | ProductRuntime, StorageRuntime |
| WatchSource / TriggerSource | Reserved (no implementation) | WorkflowDefinition, WorkflowRun |
| DeliveryTarget | Reserved (no implementation) | Product, StorageRuntime |
| DeliveryReceipt | Reserved (no implementation) | DeliveryTarget |
| EXTERNAL_REFERENCE mode | Reserved (deferred) | Product, StorageRuntime |

---

## 11. Related Documents

| Document | Relationship |
|----------|-------------|
| [Storage Runtime](../storage-runtime.md) | Canonical storage runtime architecture |
| [Storage Runtime Foundation](../../review/storage-runtime-foundation.md) | Current implementation status |
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Render platform architecture (L1-L8) |
| [Platform Constitution](../platform-constitution-v1.md) | Frozen architecture constraints |
| [ADR-011: Storage Runtime](../adr/ADR-011-storage-runtime.md) | Storage runtime ADR |
| [Product Runtime](../product-runtime.md) | Product lifecycle and metadata |
| [Execution Lifecycle](../execution-lifecycle.md) | Execution job/task lifecycle |
| [Platform Roadmap](../platform-roadmap.md) | Platform capability roadmap |
