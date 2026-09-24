# Typed Artifact contract foundation V22

Date: 2026-09-25

This correction establishes platform-owned contracts without migrating feature consumers.
Artifact remains the only identity and lifecycle authority. No migration was added because
these types are contract and port definitions; persistence belongs to the later consumer pass.

## Canonical contracts and owners

| Contract | Owner | Boundary |
|---|---|---|
| `ArtifactUploadAdmission` / `ArtifactUploadAdmissionPort` | `artifact-module` | workspace scope, storage issuance, digest, size, technical metadata, audit, lifecycle, lineage, quota and idempotency |
| `ArtifactSubject` | `artifact-module` | versioned Marketplace subject with scope, visibility, owner, lineage and lifecycle fingerprints |
| `ArtifactProjection`, `ArtifactSearchQuery`, `ArtifactSearchPage`, `ArtifactSearchPort` | `artifact-module` | derived index identity, deterministic page inputs, cursor and consistency state |
| `ArtifactSourceReference` | `artifact-module` | immutable timeline/render input with digest, scope, lineage and revision |
| `ArtifactRetrievalPort` | `artifact-module` | scoped Artifact retrieval; implementation must verify integrity before returning |

All constructors reject missing or conflicting required facts with stable
`ArtifactContractErrorCode` values. Versioned contracts use deterministic fingerprints.
Canonical JSON recursively sorts object keys, preserves array order, and retains null values.
Provider and storage details are excluded from the public Marketplace subject.

## Explicit boundaries

No raw upload, Marketplace, registry/search, timeline, render, database, OpenAPI route,
MediaAsset consumer, queue, Temporal workflow, provider registry, or storage authority was
changed. Existing consumers remain tracked follow-up work until they can adopt these ports
without a compatibility facade or second authority.

Required follow-up tasks:

1. Implement one Artifact authority adapter for upload admission and reversible storage
   compensation, then add persistence constraints and migration tests.
2. Migrate Marketplace subjects and listing reads to `ArtifactSubject` with scope and
   lifecycle verification.
3. Build the derived Artifact projection/index and its stale/rebuild implementation.
4. Replace timeline source bindings with `ArtifactSourceReference` while preserving
   revision and render planning ownership.

## Validation boundary

PASS: artifact contract compile, focused contract tests, deterministic nested JSON
canonicalization, and `git diff --check`.

The pre-existing MediaAsset reachability guard remains PASS for the V21 default-profile
fence. PostgreSQL/Testcontainers, OpenAPI runtime export, Semgrep, oasdiff and Spectral
remain BLOCKED/NOT_RUN when their external tools or fixtures are unavailable. Feature
consumer migration is intentionally NOT_RUN in this bounded foundation.
