# Timeline Move/Delete — Design and Capability Record (R1)

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Base:** `7435f912793e79ad06c5f65c65369de964213335` / `7012e61bc775b18ccb3d1f35d30b19d889583519`
**Branch:** `task/timeline-move-delete-product-r1`

## A1. Existing capability at the base

| Layer | Status | Evidence |
|---|---|---|
| MOVE Operation | ALREADY_IMPLEMENTED | `OperationPlanner.planMove` (absolute + relative), `TimelineClipEditCommand` (`MOVE_CLIP`), `TimelineClipEditOperationController` preview/apply |
| DELETE Operation | ALREADY_IMPLEMENTED | `OperationPlanner.planDelete` (clip removal + relationship cleanup), `TimelineClipEditCommand` (`DELETE_CLIP`) |
| Operation authority | PRESENT | every edit goes through `OperationRequest` → plan → CAS apply; controllers only express intent |
| Revision persistence / CAS | SUPPORTED | existing revision authority; `baseRevisionId` + `baseContentHash` on every command |
| Frontend integration | PARTIAL | trim drag existed; move/delete existed only as a form (dropdown + "Apply operation"), no drag-to-move and no delete shortcut |
| Multi-clip render | SUPPORTED | render planning accepts multiple clips; existing operation-editing E2E proves two-clip rendering |

## A2/A3. Semantics

**MOVE** (non-ripple):
- targets one existing clip (`ExplicitObjectSelection`, `EXACT`);
- keeps clip ID, track, source range (`trimStart`/`trimEnd`), duration, effects, temporal mapping;
- absolute mode sets the timeline start to an exact rational; relative mode adds a non-negative exact rational;
- no other clip moves; gaps are legal, but a positive same-track overlap is a fatal canonical
  validation error (`TIMELINE_CLIP_OVERLAP`, HTTP 422) — the editor surfaces that rejection and
  re-reads the authoritative revision, so no invalid timeline is ever persisted;
- a target start before 0 is impossible by construction: `MediaTime` rejects negative ticks and the
  transport regex accepts only non-negative exact rationals;
- the UI clamps the drag at 0.

**DELETE** (non-ripple):
- removes exactly the selected clip from the canonical document;
- removes relationships that reference the deleted clip (sync removed; group membership updated or the
  group removed when fewer than two members remain) — existing `planDelete` behavior;
- no other clip moves; source media/artifact is never touched;
- deleting an unknown clip fails through the canonical `clipOf` rejection.

## A4. Changes made in this round

| Area | Change |
|---|---|
| `frontend/src/product/timeline/move.ts` (new) | exact-rational non-ripple projection, signed drag delta, origin clamp |
| `frontend/src/product/timeline/ClipEditor.tsx` | clip-body pointer drag → server preview → apply (one command id); Delete/Backspace on the editor section with focus guard; explicit "Delete selected clip" button; move status/rollback |
| `frontend/src/product/timeline/move.test.ts` | projection/drag evidence |
| `frontend/src/product/timeline/ClipEditor.test.tsx` | drag move, threshold, delete key, focus guard, delete button, rejection rollback |
| `frontend/scripts/nle-browser-e2e.mjs` | multi-clip Chrome scenario: 3 clips, trim C, delete B, move C, refresh, render, sample pixels |
| governance ledger + guard | new frontend paths classified |

No backend production change was required: the canonical planner and CAS authority already implement the
semantics. No new endpoint, no second write path, no compatibility layer, no new position field.

## Frozen contracts

Untouched: Timeline canonical identity, Revision CAS, Operation authority, provider binding, render
planning contracts, Modulith declarations. `MediaTime` exact rationals are used everywhere; no floating
time enters the canonical layer.

## Owner decisions / risks

- None blocking. The retained graph rules and the three pre-existing test failures are unchanged and out of
  scope (recorded in the test matrix).
- Same-track overlap render semantics are not exercised by this round's acceptance scenario; the chosen
  scenario ends fully covered and non-overlapping, which is the currently accepted planner behavior.
