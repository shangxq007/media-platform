# Timeline Move/Delete — Product Report (R1)

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Base:** `7435f912793e79ad06c5f65c65369de964213335` / `7012e61bc775b18ccb3d1f35d30b19d889583519`
**Branch:** `task/timeline-move-delete-product-r1`

```text
MOVE_OPERATION:        IMPLEMENTED (reused the canonical planner; no new backend path)
DELETE_OPERATION:      IMPLEMENTED (reused the canonical planner; no new backend path)
MOVE_SEMANTICS:        NON_RIPPLE exact-rational absolute/relative placement; identity/track/source
                       trim/duration preserved; origin-clamped; same-track overlap rejected by the
                       canonical validator
DELETE_SEMANTICS:      NON_RIPPLE removal of exactly the target clip; relationships cleaned by the
                       existing planner; source media/artifact retained; unknown clip rejected

FRONTEND_MOVE:         PASS (clip-body drag → server preview → apply with one command id; status,
                       failure alert and authoritative rollback)
FRONTEND_DELETE:       PASS (Delete/Backspace with focus guard + explicit delete button)
REVISION_PERSISTENCE:  PASS (each edit is a canonical revision; refresh restores HEAD)
REVISION_CAS:          PASS (stale base → 404/409; the E2E stale move on a deleted clip is rejected)
MULTI_CLIP_TIMELINE:   PASS (A 0–2, B 2–4, C 4–6 → trim C → delete B → move C to 2–3)
FFMPEG_REAL_RENDER:    PASS (formal RenderJob, 3.0 s output, pixel-proven ordering, deleted clip absent;
                       the internal #22 bound-graph identity is not exposed by the product API evidence)
TRIM_REGRESSION:       PASS (trim drag + apply still works; trim→delete→move sequence in one E2E)
ARCHITECTURE_GUARDS:   PASS (Modulith 0 unexpected, frontend guard, drift, doc governance)
HERMES_REVIEW:         APPROVE (see TimelineMoveDeleteHermesReview.md)

PROVIDER_NEUTRALITY:   PASS (Timeline/Operation/canonical snapshot contain no provider-specific
                       semantics; the same revision feeds #20→#21→#22 and the provider is chosen there)
BMF_SAME_SNAPSHOT_CAPABILITY: UNSUPPORTED
   BMF declares video.decode / render.composite / render.output / media.transcode and its lowerer
   requires exactly one materialized input and one authoritative output per unit
   (BmfCpuTranscodeLowerer.java:43-60), and refuses timed-text composite inputs. The edited
   multi-clip snapshot needs a composite of ≥2 inputs, which BMF V1 cannot lower. Exact gap, no
   BMF lowering was added (out of scope).
BMF_REAL_E2E:          NOT_APPLICABLE (unsupported by the current BMF V1 capabilities)

BATCH_OPERATION_CONTRACT_PROBE: COMPLETE (see below)
BATCH_IMPLEMENTATION:  DEFERRED (owner decision item; the current single-operation authority is kept)

FINAL_CANDIDATE_COMMIT: (the commit containing this report)
FINAL_RESULT:          PASS
```

## Batch Operation Contract Probe (read-only)

```text
CURRENT_MODEL:                 PARTIAL — OperationBatch is a model-only ordered intent envelope
                               (OperationBatch.java: "bounded flat single-base intent envelope … NO
                               intermediate canonical state, NO intra-batch planning"); no batch
                               apply/invocation path exists.
EXISTING_ENTRYPOINT:           Single operation only: OperationInvocationPort.invoke(OperationRequest,
                               context); controllers expose one request per apply.
EXISTING_ATOMICITY:            One accepted operation → one revision; CAS on
                               (baseRevisionId, baseContentHash); per-command idempotency in
                               apply_command (apply_command_id PK, fingerprint, result revision).
EXISTING_REVISION_SEMANTICS:   Git-like revisions: timeline_revision(parent_revision_id,
                               revision_number, snapshot_id, content_hash), head pointer per project;
                               one revision per apply.
PROPOSED_MODEL:                ORDERED_OPERATIONS_TO_ONE_REVISION (owner direction)
CAN_REUSE_EXISTING_AUTHORITY:  NO — the planner is single-operation and the apply authority persists
                               one operation per revision; a batch needs a new application contract
                               that folds ordered operations into one candidate while reusing the
                               planner and CAS primitives.
REQUIRES_FROZEN_CONTRACT_CHANGE: YES (new batch apply semantics + batch-level idempotency)
REQUIRES_DATABASE_CHANGE:      YES (batch identity/order persistence and batch-scoped idempotency, or
                               an explicit batch_id on apply_command/revision provenance)
RECOMMENDATION:                SEPARATE_TASK (owner decision before implementation)
```

## What changed

**Frontend** (the only production change):
- `frontend/src/product/timeline/move.ts` — exact-rational non-ripple projection, signed drag delta,
  origin clamp, describe helper.
- `frontend/src/product/timeline/ClipEditor.tsx` — clip-body drag move (preview → apply, same command
  id, authoritative rollback), Delete/Backspace with form-focus guard, explicit delete button, move
  status; trim handles stop propagation so trim and move never collide.
- `frontend/scripts/nle-browser-e2e.mjs` — real multi-clip Chrome scenario (see E2E report).
- Tests: `move.test.ts`, extended `ClipEditor.test.tsx`.
- Governance ledger/guard entries for the new frontend paths.

**Backend**: no production change. The canonical planner already implemented MOVE/DELETE correctly;
the transport already rejects negative rationals and `MediaTime` cannot represent them. Adding a
redundant planner check was evaluated and reverted as unreachable dead code.

## Verification

- Frontend: 43 files / 411 tests PASS; tsc clean; ESLint clean on changed files; frontend
  architecture guard PASS.
- Backend: clip-edit planner/controller/service tests 29 PASS; `compileJava compileTestJava` PASS;
  `ModularityTest` PASS (0 unexpected, 0 cycles, 3 retained graph rules).
- Real E2E: `FIRST_RENDER_LOCAL_VALIDATION_PASS`; Chrome→Operation→Revision→RenderJob→FFmpeg→
  Artifact→Delivery→download; pixel sampling proves the edited timeline and the deleted clip's absence.
- Guards: `check-architecture-drift.sh` PASS, `verify guards` 7/7, document governance PASS.
- Baseline failures unchanged: `Cfrhi1LegacyWriteAuthorityGuardTest`, `RevisionCreatedEventTest`,
  `JooqVersionAuthorityTest` (recorded in the test matrix, not touched).

## Out of scope (per task and addendum)

Ripple edit, magnetic timeline, snapping, group/multi-select move, undo/redo, transitions, keyframes,
BMF lowering extension, batch operation implementation, module governance, Flyway changes.
