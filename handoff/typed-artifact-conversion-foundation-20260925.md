# Typed Artifact and Conversion Foundation handoff

Status: candidate frozen for independent review; no delivery or push requested.

- Branch: `feature/typed-artifact-conversion-foundation-20260925`
- Worktree: `/home/user/Documents/workspace/projects/media-platform/Documents/workspace/projects/.worktrees/typed-artifact-conversion-foundation-20260925`
- Baseline: `8875461a796c6cee5a54500529dfe5ea86e0e182`
- Candidate: recorded after commit below

Authority map: Artifact is the only identity, scope, lifecycle, authorization, retrieval and lineage authority. `artifact_media_details` is a one-to-one typed projection keyed by `artifact_id`; Storage supplies object references only. MediaAsset runtime authority and public MediaAsset routes are retired. See `docs/architecture/artifact-authority-map.md` and `docs/adr/artifact-mediaasset-retirement-2026-09-25.md`.

Changed files:
- `artifact-module/src/main/java/com/example/platform/artifact/domain/typed/ArtifactIdentityGuard.java`
- `artifact-module/src/main/java/com/example/platform/artifact/domain/typed/MediaArtifactDetails.java`
- `artifact-module/src/main/java/com/example/platform/artifact/domain/typed/TypedArtifactValidator.java`
- `artifact-module/src/test/java/com/example/platform/artifact/domain/typed/TypedArtifactFoundationTest.java`
- `platform-app/src/main/resources/db/migration/V18__artifact_media_authority_convergence.sql`
- `contracts/http/media-api/openapi.base.yaml`, `openapi.candidate.yaml`, `openapi.breaking.yaml`
- `docs/adr/artifact-mediaasset-retirement-2026-09-25.md`, `docs/adr/typed-artifact-conversion-foundation.md`
- `docs/architecture/artifact-authority-map.md`, `docs/architecture/conversion-contract-foundation.md`
- `docs/typed-artifact-conversion-governance.md`

Migration list: V18 only. V1–V17 were not rewritten. V18 adds Artifact scope/provenance/storage/idempotency facts, migrates existing `media_asset` rows and links into Artifact and `artifact_media_details`, then renames legacy relations and installs mutation guards. Rollback is a reviewed forward compensating migration; no historical rows are silently dropped.

OpenAPI: Artifact retrieval is `GET /artifacts/{artifactId}`; typed validation/specification inspection remain. MediaAsset endpoints and schemas were removed from base, candidate and breaking documents.

Verification:
- `./gradlew :artifact-module:test --tests '*TypedArtifactFoundationTest' --no-daemon` — PASS (6 tests; fresh run after changes).
- Ruby YAML parse for all three media API documents — PASS.
- `git diff --check` — PASS.
- Full artifact-module suite — NOT_RUN in this revision; prior baseline had Testcontainers Docker initialization failures and is not counted as passing.

Fresh tests: 6 passed, 0 failed, 0 skipped. Reused tests: 0. Overlapping tests: 0.
BLOCKED/NOT_RUN: hermetic PostgreSQL migration execution (Docker/Testcontainers availability not established), full suite, runtime provider execution, Temporal activities, FFmpeg/BMF/OpenCV/AI, Storage writes, external providers, deployment/PVE/GitOps, production data.

Follow-up required: independently review V18 against a production schema snapshot and migrate remaining internal callers from MediaAsset classes to Artifact contracts before runtime execution work. This candidate does not claim provider execution, PVE readiness, delivery, merge, push, deployment or production readiness.
