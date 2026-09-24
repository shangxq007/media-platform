# ADR: Typed Artifact and Conversion Foundation

Status: accepted candidate. Artifact is the sole artifact identity and authority; MediaAsset runtime authority is retired. `LogicalArtifactKind` describes meaning; format, MIME, encoding, schema version and technical facts describe the physical representation. `ConversionContract` and immutable `ConversionSpecification` are declarative compatibility and provenance records, never Temporal workflows. Providers may advertise compatibility only through these platform contracts.

V18 migrates existing MediaAsset rows into Artifact and `artifact_media_details`, preserves lineage and storage references, and protects renamed historical relations from mutation. V17 remains unchanged. No result repository, queue, worker, provider, storage write, or execution path is added. Rollback is a reviewed forward compensating migration after archival review because immutable facts must not be silently deleted.
