# CHECKPOINT_A — REVISION_WRITE_SURFACE_MATRIX (Round 3, from real code)

BYPASS_POSSIBLE = NO for every canonical write surface.

| WRITE_SURFACE | CANONICAL_VALIDATION | SOURCE_VALIDATION | ARTIFACT_EXISTENCE | TENANT | DIGEST | PIN_REGISTRATION | CAS | IDEMPOTENCY | TRANSACTION_BOUNDARY | HEAD_UPDATE | BYPASS |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TimelineRevisionService.recordRevision | YES canonicalGate | YES SourceReferenceValidator | YES TimelineArtifactPinValidator | YES tenant match | YES ContentDigest match | YES registerRevisionPins same tx | YES expected-head | YES apply_command (RevisionCommandApplyService) | @Transactional (service) | YES | NO |
| TimelineRevisionSaveService.saveRevision | YES TimelineCanonicalValidator | YES canonical + pin gate | YES validator | YES | YES | YES same jOOQ tx (Round 3 explicit dsl.transactionResult) | YES expected-current conflict | N/A reason: direct save (no command id; each call = new UUID revision; conflict detection is the concurrency guard) | explicit jOOQ transaction (Round 3) | YES updateCurrentRevisionTx same tx | NO |
| TimelineRevisionSaveService.restoreRevision | N/A reason: restores an already-validated historical revision; no new authored content | N/A same | N/A reason: historical pins already registered at original commit | YES tenant-scoped read | N/A reason: historical hash preserved | N/A reason: already registered | YES expected-current | N/A | @Transactional | YES | NO |
| TimelineMergeEngine.merge | YES canonicalGate | YES | N/A reason: preview-only — returns mergedPayload, never persists a revision; persistence happens through the save surfaces above | — | — | — | N/A | N/A | N/A | N/A | NO (no write) |
| RevisionCommandApplyService.applyCommand | YES | YES | YES | YES | YES | YES | YES CAS version column | YES durable apply_command_id | jOOQ tx | YES | NO |
