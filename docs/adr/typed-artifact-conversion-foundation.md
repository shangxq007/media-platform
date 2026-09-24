# ADR: Typed Artifact and Conversion Foundation

Status: accepted candidate. The existing Artifact catalog remains the artifact authority and MediaAsset remains media ownership authority. `LogicalArtifactKind` describes meaning; format, MIME, encoding, schema version and technical facts describe the physical representation. `ConversionContract` and immutable `ConversionSpecification` are declarative compatibility and provenance records, never Temporal workflows. Providers may advertise compatibility only through these platform contracts.

The V17 migration adds only immutable conversion specifications with tenant/workspace-scoped deterministic idempotency. No result repository, queue, worker, provider, storage write, or execution path is added. Backfill is none; rollback is a forward compensating migration after archival review because immutable plans must not be silently deleted.
