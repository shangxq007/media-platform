---
status: blueprint
created: 2026-06-25
scope: platform-wide
truth_level: target
owner: platform
---

# Artifact Runtime & Asset Graph Blueprint

## 1. Asset vs Artifact

| Asset | Artifact |
|-------|----------|
| User-owned media object | System-generated derivative |
| Interview.mov | Proxy.mp4, Transcript.json, Thumbnail.jpg |
| Uploaded once | Generated/rebuilt from asset |
| Immutable identity (assetId) | Rebuildable (producer + inputs → output) |
| Governance: classification, license | Governance: inherited from source asset |

## 2. Artifact Model

```
Artifact {
    artifactId: String
    artifactType: PROXY | THUMBNAIL | TRANSCRIPT | SUBTITLE | WAVEFORM
                | OCR | VISION_METADATA | EMBEDDING | FINAL_RENDER
                | MARKETPLACE_PACKAGE | SEARCH_PROJECTION
    producer: String (provider or system)
    sourceAssetId: String
    sourceTimelineRevision: String (optional)
    inputs: List<StorageReference>
    outputs: List<StorageReference>
    checksum: String
    mimeType: String
    status: ArtifactStatus
    metadata: Map<String, Object>
    version: int
    createdByPlan: String (ExecutionPlan id)
    createdByProvider: String
    createdAt: Instant
}
```

## 3. Artifact Lifecycle

| Status | Description |
|--------|-------------|
| CREATED | Registered, not yet processed |
| PROCESSING | Provider is generating |
| READY | Available for consumption |
| FAILED | Generation failed |
| SUPERSEDED | Replaced by newer version |
| ARCHIVED | No longer needed |
| DELETED | Storage freed |

Transitions: CREATED→PROCESSING→READY. CREATED→PROCESSING→FAILED→CREATED (retry). READY→SUPERSEDED (newer version). READY→ARCHIVED. SUPERSEDED→ARCHIVED.

## 4. Artifact Graph

```
Interview.mov
  ├── Proxy (artifactType=PROXY)
  ├── Transcript (artifactType=TRANSCRIPT)
  │     └── Embedding (artifactType=EMBEDDING)
  ├── Vision Metadata (artifactType=VISION_METADATA)
  ├── OCR (artifactType=OCR)
  ├── Thumbnail (artifactType=THUMBNAIL)
  ├── Preview (artifactType=PROXY)
  └── Final Render (artifactType=FINAL_RENDER, sourceTimelineRevision=trev_42)
```

Execution Planner plans Artifact Graph, NOT PlatformTask graph.

## 5. Artifact Registry

```
ArtifactRegistryService:
  register(artifact) → artifactId
  findByAsset(assetId) → List<Artifact>
  findByTimeline(revisionId) → List<Artifact>
  findLatest(assetId, artifactType) → Optional<Artifact>
  findDependencies(artifactId) → List<Artifact>
  findDependents(artifactId) → List<Artifact>
```

## 6. Artifact Lineage

Artifact lineage traces how an artifact was produced:

```
Interview.mov → Transcript v1 → Embedding v1 → Search Projection v2 → Marketplace Listing v3
```

Supports: traceability, rebuild, review, audit.

## 7. Relationship to Execution Planner

Execution Planner consumes Artifact Graph as input. Produces Logical Execution Plan. Artifact Runtime owns artifact state. Execution Planner never owns artifact state.

## 8. Relationship to Storage

Artifact references `StorageReference` (storageUri, checksum, mediaType, size). No storage logic in Artifact Runtime. Storage Runtime (future A5) owns: Object Storage, Upload, Download, Presigned URL, Worker Cache, Range Read, Access Strategy.

## 9. Relationship to Marketplace

Marketplace consumes Artifacts (Preview, Thumbnail, Package) instead of raw Assets. Marketplace Listing references artifact IDs.

## 10. Relationship to Search

Search Projection IS an artifact. Search rebuild uses Artifact Lineage.

## 11. Future Compatibility

Compatible with BMF, OpenCue, FFmpeg, MLT, Remotion — all produce artifacts registered through ArtifactRegistryService.
