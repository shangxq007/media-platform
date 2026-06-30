# Storage Strategy Comparison

## Overview

Two storage strategies for OpenCue execution:

| Strategy | Use Case | Status |
|----------|----------|--------|
| Shared Path | Local Docker, single PVE, controlled LAN | WORKS |
| Object Storage Materialization | Production, cross-host, cross-provider | NOT_IMPLEMENTED |

## Shared Path Strategy

### When to Use

- Local Docker development
- Single PVE node testing
- Same-LAN workers with NFS/SMB mounts
- Controlled smoke testing

### Model

```
StorageRuntime (or local harness)
  → prepares input under shared root
  → worker reads input path
  → worker writes output path
  → host/platform reads output path
```

### Path Layout

```
Host:   build/opencue-shared/media-platform-smoke/
Mount:  /mnt/opencue-shared/media-platform-smoke/
```

### CJSL Command Example

```xml
<cmd>
  <arg>ffmpeg -i /mnt/opencue-shared/inputs/video.mp4 -c:v libx264 /mnt/opencue-shared/outputs/frame-${CUE_FRAME}.mp4</arg>
</cmd>
```

### Limitations

- Not portable across cloud providers
- Not portable across regions
- Requires consistent mounts on all workers
- Requires path discipline
- Harder to scale safely
- Not suitable for untrusted workers

## Object Storage Materialization Strategy

### When to Use

- Production
- Cross-host execution
- Cross-service-provider execution
- Cloud workers
- Remote workers without shared filesystem

### Model

```
StorageRuntime has input StorageReference
  → Worker receives internal materialization manifest
  → Worker downloads input to local scratch
  → Provider runs against local scratch paths
  → Worker uploads output to object storage
  → Worker writes output manifest
  → Platform registers StorageReference
  → ProductRuntime creates/updates Product
```

### Manifest Structure

```json
{
  "manifestId": "manifest-001",
  "inputs": [
    {
      "assetId": "video-001",
      "storageReferenceId": "sr-001",
      "objectKey": "inputs/video.mp4",
      "localPath": "/scratch/job-001/input/video.mp4",
      "checksum": "sha256:..."
    }
  ],
  "outputs": [
    {
      "artifactType": "FINAL_RENDER",
      "objectKeyPattern": "outputs/job-001/frame-${CUE_FRAME}.mp4",
      "localPathPattern": "/scratch/job-001/output/frame-${CUE_FRAME}.mp4"
    }
  ],
  "scratchRoot": "/scratch/job-001",
  "cleanupPolicy": "POST_JOB"
}
```

### CJSL Command Example

```xml
<cmd>
  <arg>media-worker-materialize-and-render --manifest /mnt/opencue-shared/job-manifests/job-001/manifest.json</arg>
</cmd>
```

### Safety Constraints

- Signed URLs may be used internally only if necessary
- Signed URLs must NOT be exposed through public API
- Signed URLs must NOT be persisted as product identity
- Local paths must NOT be exposed through public API
- Object keys and storage provider internals must NOT leak through public API

## Worker Local Scratch Strategy

### Definition

Worker local scratch is execution-local and disposable:
- Not Product identity
- Not StorageReference identity
- Must be cleaned after job completion where possible

### Path Convention

```
/scratch/{job-id}/input/    — materialized inputs
/scratch/{job-id}/output/   — execution outputs
/scratch/{job-id}/temp/     — temporary files
```

### Cleanup Policy

| Policy | Behavior |
|--------|----------|
| POST_JOB | Clean after job completion (default) |
| NEVER | Leave for debugging (dev only) |
| ON_SUCCESS | Clean only on success |

## Hybrid Strategy

```
Shared path can remain an ExecutionEnvironment-local optimization.
Object storage is the portable production abstraction.
Platform can select materialization strategy per ExecutionEnvironment.
```

### Selection Logic

```java
StorageStrategy selectStrategy(ExecutionEnvironment env, ExecutionPolicy policy) {
    if (env.isLocalDocker() || env.isSingleNode()) {
        return StorageStrategy.SHARED_PATH;
    }
    if (policy.isProduction() || env.isCrossHost()) {
        return StorageStrategy.OBJECT_STORAGE_MATERIALIZATION;
    }
    return StorageStrategy.SHARED_PATH; // default to simpler strategy
}
```

## Decision Matrix

| Scenario | Strategy | Rationale |
|----------|----------|-----------|
| Local Docker smoke | SHARED_PATH | Simplest, works today |
| Single PVE node | SHARED_PATH | NFS mount available |
| Same-LAN workers | SHARED_PATH | NFS/SMB mount available |
| Cross-host | OBJECT_STORAGE | No shared filesystem |
| Cross-provider | OBJECT_STORAGE | No shared filesystem |
| Production | OBJECT_STORAGE | Portable, scalable |
| Untrusted workers | OBJECT_STORAGE | No shared access |

## What Must Not Be Exposed Publicly

| Item | Reason |
|------|--------|
| Signed URLs | Security risk, time-limited |
| Local filesystem paths | Not portable, security risk |
| Object keys | Internal storage detail |
| Bucket names | Internal storage detail |
| Storage provider internals | Architecture boundary |
| Worker scratch paths | Execution-local detail |
