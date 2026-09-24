# Typed Artifact and Conversion Foundation handoff

Status: frozen candidate pending independent review. No delivery or push requested.

- Branch: `feature/typed-artifact-conversion-foundation-20260925`
- Worktree: `/home/user/Documents/workspace/projects/media-platform/Documents/workspace/projects/.worktrees/typed-artifact-conversion-foundation-20260925`
- Baseline: `8875461a796c6cee5a54500529dfe5ea86e0e182` (tree `8652a332abd18622e4a4d30a351373295795c2c5`)
- Candidate commit: `53314bc98bdb36e3a08bc9efd88091d703d1e9a1`
- Candidate tree: `824c480dc07854f04f09eb901698ed5f72c83d7b`

Changed files:
- `artifact-module/src/main/java/com/example/platform/artifact/domain/typed/*`: platform-owned kinds, typed artifact view, requirements, version ranges, declarative contracts, specifications, validator and stable errors.
- `artifact-module/src/test/java/com/example/platform/artifact/domain/typed/TypedArtifactFoundationTest.java`: taxonomy, ranges, fingerprints, scope and parameter validation.
- `platform-app/src/main/resources/db/migration/V17__typed_conversion_specifications.sql`: immutable declarative specification persistence with scoped idempotency and constraints.
- `contracts/http/media-api/openapi.base.yaml`, `contracts/http/media-api/openapi.candidate.yaml`: inspection/validation paths and platform-owned schemas.
- `docs/adr/typed-artifact-conversion-foundation.md`, `docs/typed-artifact-conversion-governance.md`: authority and architecture decisions.
- `CR1_CHANGE_LEDGER.tsv`: ledger entry.

Authority mapping: existing Artifact catalog remains artifact identity authority; MediaAsset remains media ownership authority; Storage remains data-plane authority. V17 stores plans only and does not create a result repository, queue, admission authority, worker lifecycle or Temporal path.

Migration: V17 only; no backfill. Rollback requires a reviewed forward compensating migration because plans are immutable; historical V1–V16 were not changed.

API/OpenAPI: `POST /artifacts/typed/validate` and `GET /conversion-specifications/{specificationId}` only inspect/validate immutable contracts/specifications. No execution claim and no provider registry exposure.

Tests:
- Fresh: `./gradlew :artifact-module:test --tests '*TypedArtifactFoundationTest' --no-daemon` — PASS, 4 tests.
- Reused/overlapping: none for this candidate.
- Broader `./gradlew :artifact-module:test --no-daemon` — 116 tests completed; 110 passed, 6 failed during Testcontainers Docker client initialization (`DockerClientProviderStrategy`, Docker unavailable). BLOCKED; not reported as passing.
- OpenAPI YAML parse and schema presence check — PASS.

NOT_RUN/BLOCKED: provider execution, Temporal runtime/activity, FFmpeg/BMF/OpenCV/AI, storage writes/materialization, MediaAsset registration, external providers, deployment/PVE/GitOps, production data, hermetic PostgreSQL migration test (Docker unavailable).

Follow-up required: runtime planning/execution must separately map compatible providers to these contracts without changing the contracts or introducing provider identifiers into lineage.

Explicit statement: no provider execution or deployment was performed.
