# Typed artifact governance

Artifact is platform-owned and authoritative for artifact identity; MediaAsset owns media-domain relationships. Logical kind is separate from a physical container, MIME type, codec, schema/document version and technical properties. A conversion specification records a declarative plan and lineage; a Temporal runtime workflow, template workflow, and Application remain separate concepts. Provider compatibility is an input to future planning and does not define public contracts.

Authority boundaries: Artifact repository (artifact identity), MediaAsset repository (media ownership), Storage contracts (references only), conversion specification (immutable plan/provenance). No provider identifiers are required for lineage. Incomplete scope, version, lineage, or type facts fail closed.

NOT_RUN: provider execution, materialization, storage writes, Temporal runtime, deployment, PVE, external providers, and production data changes.
