# Render Output Commit — Implementation Roadmap

## Phase 1: Schema and Repository Constraints

```text
Scope: V5 migration + repository layer
Files: Flyway migration, RenderOutputRepository
Migration: YES (V5)
Tests: Schema validation, unique constraint verification
Completion gate: Migration applies cleanly, constraints enforced
```

## Phase 2: Single Output-Commit Authority

```text
Scope: RenderOutputCommitService as single authority
Files: RenderOutputCommitService, RenderJobExecutionService refactor
Migration: NO
Tests: Single authority verification, no bypass paths
Completion gate: Only RenderOutputCommitService writes COMPLETED
```

## Phase 3: Blob Ownership and Checksum

```text
Scope: Deterministic object key, content-based checksum
Files: RenderArtifactStorageService, checksum computation
Migration: NO
Tests: Deterministic key verification, checksum validation
Completion gate: No URI-based checksum, deterministic replay works
```

## Phase 4: Artifact/Product Publication Coupling

```text
Scope: Artifact + Product creation coupled to render_output
Files: StorageCatalogService, ProductRuntimeService integration
Migration: NO
Tests: Publication ordering, readiness coupling
Completion gate: No false READY without COMMITTED
```

## Phase 5: Quota/Billing Idempotency

```text
Scope: Idempotent consume/ledger operations
Files: BillingEnforcementService, QuotaUsageRepository
Migration: YES (ledger + quota constraints)
Tests: Duplicate call safety, idempotency verification
Completion gate: accepted mutations <= 1 per RenderJob
```

## Phase 6: Disable Stale Compensation

```text
Scope: Disable StaleRenderJobCompensationService
Files: Configuration, scheduler removal
Migration: NO
Tests: Compensation not triggered
Completion gate: No scheduled/startup compensation
```

## Phase 7: Remove canRetry

```text
Scope: Remove misleading canRetry field
Files: RenderJobStatus, DTOs, API responses
Migration: NO
Tests: No canRetry in responses
Completion gate: canRetry absent from contract
```

## Phase 8: Failure-Window Verification

```text
Scope: All 10 failure windows tested
Files: Integration tests
Migration: NO
Tests: Each failure window with reload verification
Completion gate: All windows pass, no false states
```

## Phase 9: Architecture Guards

```text
Scope: Drift guards for protocol compliance
Files: Architecture drift script updates
Migration: NO
Tests: Guard verification
Completion gate: All guards pass
```
