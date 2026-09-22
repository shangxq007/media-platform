# Media thumbnail correction handoff

Candidate: `bf16398b49d8b76125c2c93e9c4b4f00d93b030f`
Tree: `9280b67291fce8046eaacaf81eb3fb86b13de7eb`
Remote delivery: NOT_RUN; no push, deployment, GitOps, webhook, or provider call.
Prior verdict: CHANGES_REQUIRED (preserved). This candidate remains review-requested; no approval or delivery claim.

## Implemented

- Temporal Activity no longer constructs or launches FFmpeg/ffprobe processes.
- `media.thumbnail` resolves through `ThumbnailCapabilityRegistry` to the pinned `ffmpeg.cpu.frame-extract.v1` provider manifest.
- Provider execution uses the existing sandbox runtime boundary and cancellation callback.
- Durable task admission uses the database uniqueness boundary, validates conflicting idempotency reuse, and consumes quota only for newly inserted tasks inside the transaction.
- Cancellation is persisted, signalled to the workflow, observed by provider execution, and fenced before output/artifact commitment.
- Frontend uses the authenticated Workspace tenant projection, authorized project asset listing, durable status polling, cancellation, committed preview, and authorized download/open.
- Flyway V10 is recorded as the current forward migration and migration guards now verify V1–V10.
- Added `WorkerRuntime` and a registered `ThumbnailExecutionBackend`; the provider now reaches FFmpeg/ffprobe through the typed `ExecutionBackend` registry and rejects unregistered provider identities.
- Added controlled race tests for duplicate admission/quota charging and retry/artifact single-winner fencing.
- Added local acceptance compose topology for PostgreSQL 16.4, Temporal 1.26.2, and MinIO (exact image identity recorded in `docker-compose.thumbnail-acceptance.yml`).
- Quota denial now removes the uncharged admission row; cancellation is checked before quota/workflow start.

## Fresh checks

- `./gradlew --no-daemon :platform-app:compileJava`: PASS.
- `./gradlew --no-daemon :platform-app:test --tests com.example.platform.thumbnail.ThumbnailArchitectureGuardTest --tests com.example.platform.thumbnail.ThumbnailContractsTest`: PASS.
- `npm run typecheck`: PASS.
- `npm run lint`: PASS, existing 46 warnings and 0 errors.
- `bash scripts/check-api-contract-governance.sh`: PASS, 5/5.
- `./gradlew --no-daemon :platform-app:test --tests com.example.platform.thumbnail.ThumbnailRuntimeAuthorityIntegrationTest --tests com.example.platform.thumbnail.ThumbnailConcurrencySemanticsTest --tests com.example.platform.thumbnail.ThumbnailArchitectureGuardTest --tests com.example.platform.thumbnail.ThumbnailContractsTest --rerun`: PASS (6 tests, 6 passed, fresh).
- `./gradlew --no-daemon :platform-app:compileJava`: PASS after WorkerRuntime/backend changes (fresh).
- `./gradlew --no-daemon verifyGcr2ArtifactAuthority verifyGcr5Gcr6DatabaseCanonicalization`: PASS.
- `bash scripts/check-architecture-drift.sh`: PASS after binding the candidate source tree.
- `python3 scripts/phase19-clean-forward-guards.py`: PASS.

## NOT_RUN / environment-gated

- Real PostgreSQL + Temporal + Storage end-to-end thumbnail flow: NOT_RUN; compose file and exact command are present, but no service run was completed in this candidate.
- `FfmpegThumbnailProviderIntegrationTest` with `render-integration`: ATTEMPTED fresh, NOT_PASS. `:platform-app:renderIntegrationTest --tests ...` entered the real tagged test and did not complete within the bounded run; it was interrupted. No mock or fixture result was recorded.
- Browser verification: NOT_RUN.
- Concurrent PostgreSQL admission/quota race tests: NOT_RUN.
- PVE, production OIDC, external providers, GitOps, deployment triggers: NOT_RUN.

Real provider toolchain identity available locally: `/usr/bin/ffmpeg`, `/usr/bin/ffprobe`, `/usr/bin/bwrap`; provider manifest pins provider `ffmpeg.cpu.frame-extract.v1` version `1.0.0`, capability `media.thumbnail`, 60-second timeout, 512 MiB input limit, and trusted worker-runtime requirements.

## Runtime call graph and semantics

`API/session authorization -> ThumbnailService admission (PostgreSQL uniqueness + quota) -> Temporal workflow -> ThumbnailActivitiesImpl -> ThumbnailCapabilityRegistry (pinned manifest) -> WorkerRuntime -> registered ThumbnailExecutionBackend -> bubblewrap sandbox -> ffprobe/ffmpeg -> staged output -> StorageOutputPort issuance -> ArtifactCommitService -> task COMPLETED -> scoped artifact retrieval.`

The activity contains no FFmpeg construction. Duplicate idempotency requests return the original task; conflicting reuse is rejected. Only a newly inserted admission can consume quota, and quota denial deletes the uncharged admission. Cancellation updates durable state, signals Temporal, fences activity and commit CAS updates, and removes local staged files. Storage writes are idempotent by `thumbnail:<taskId>`; artifact commit is single-winner by deterministic artifact ID. Downstream cleanup remains a recoverable limitation: the current Storage API lacks receipt deletion, so a failed artifact commit can leave a non-published receipt record whose object bytes must be cleaned by recovery.

Changed files: 14 files, approximately 193 added lines / 52 removed lines (including the new acceptance topology, runtime boundary, and tests; see candidate diff). Fresh checks above; prior handoff checks are reused and explicitly labeled. Browser verification, full PostgreSQL/Temporal/Storage acceptance, and the complete frontend/OpenAPI/Semgrep/package gate matrix remain NOT_RUN.
