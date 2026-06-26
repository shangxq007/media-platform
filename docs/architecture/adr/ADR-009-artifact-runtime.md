---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-009: Artifact Runtime

## Context

The platform generates many derivative outputs (proxies, transcripts, thumbnails, embeddings, final renders, marketplace packages). Currently there is no unified model for representing, tracking, versioning, and relating these generated artifacts.

## Decision

Introduce an Artifact Runtime with:
1. `Artifact` domain model — artifactId, artifactType, producer, sourceAssetId, inputs, outputs, checksum, status, version
2. `ArtifactGraph` — dependency graph of artifacts (Interview.mov → Transcript → Embedding)
3. `ArtifactRegistryService` — register, lookup, findByAsset, findByTimeline, findDependencies
4. Artifact lifecycle: CREATED → PROCESSING → READY (or FAILED, SUPERSEDED, ARCHIVED)
5. Artifact Lineage — traceable chain from source asset to final output

Artifacts reference storage via `StorageReference` only. Storage Runtime (future A5) owns actual storage operations.

## Consequences

- Execution Planner consumes ArtifactGraph as input
- Marketplace consumes Artifacts (not raw Assets)
- Search Projection is an Artifact type
- Creative Planner output (TimelineRevision) triggers artifact regeneration

## Rejected Alternatives

1. Artifacts embedded in Asset model: conflates user-owned and system-generated
2. No artifact registry: no traceability, no dependency tracking, no rebuild capability

## Migration

Phase 1: Artifact domain model + ArtifactRegistryService
Phase 2: Artifact Graph integration with Execution Planner
Phase 3: Artifact Lineage for audit/rebuild

## Amendment A4.1 (2026-06-25)

"Artifact" refined to "Product" as the broader abstraction (see ADR-010). Artifact remains as a file-backed Product subtype (`representationKind = MEDIA_FILE`). Product Graph replaces Artifact Graph. ADR-009 core decisions remain valid — the scope has been widened to include non-file Products (transcripts, embeddings, plans, projections).
