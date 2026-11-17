# Timeline Move/Delete — Independent Hermes Review

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Reviewer:** Hermes — independent read-only reviewer agent (separate context; no writes, no git writes)
**Producer:** DSH (the only writing agent)
**Base:** `7435f912793e79ad06c5f65c65369de964213335`

> This file records the actual review. DSH's conclusions and the reviewer's independent findings are
> kept separate; conditions are listed with their resolution.

## Review result

| Dimension | Verdict |
|---|---|
| OPERATION_AUTHORITY | PASS |
| CLIP_IDENTITY | PASS |
| REVISION_CAS | PASS |
| FRONTEND_INTERACTION | PASS |
| MULTI_CLIP_SEMANTICS | PASS |
| FFMPEG_RENDER | PASS |
| TRIM_REGRESSION | PASS |
| ARCHITECTURE_GUARDS | PASS |
| DIFF_SCOPE | PASS |
| **RESULT** | **APPROVE** (initial APPROVE_WITH_CONDITIONS; all 4 conditions closed and confirmed by the reviewer delta) |

## Independently verified facts

- Canonical MOVE (`OperationPlanner.planMove`) clones the clip via `withPlacement`, preserving clip ID,
  media references, source trim, temporal mapping and effects; only the target is replaced; same track,
  no ripple.
- Canonical DELETE removes exactly the scoped clip and cleans relationships; source/artifact is never
  written.
- The candidate is validated before preview/apply; a positive same-track overlap is fatal
  (`TIMELINE_CLIP_OVERLAP`, 422).
- The editor drag calls preview then apply with one command id, compares the accepted revision/hash to
  the authoritative head, and re-reads HEAD on failure (no optimistic-only success).
- Delete is apply-only (no preview) but CAS/HEAD-verified; Delete/Backspace has a form-focus guard and
  there is an explicit delete button; busy guards prevent duplicate submits.
- Trim handles stop propagation; trim and move cannot both start.
- The final E2E evidence was independently re-probed: 3.000 s h264 320×240, frames 0–47 red,
  48–71 blue, no green; revisions trim `e4f6709e`, delete `dd8ce24f`, move `86a9e1d8`; job
  `rj_c1d140ba…`, artifact `render-task-0b499e…`, delivery `dlv_1e8f0a1c…`.
- `git diff --name-status` shows docs + frontend tests/UI + the built static bundle only; no backend
  production change, no new endpoint, no provider change.
- No authority bypass, no parallel editing model, no compatibility layer.

## Conditions and resolution

| # | Condition | Resolution |
|---|---|---|
| 1 | Product report cites a nonexistent Hermes review file | This file is added; the reference resolves. |
| 2 | E2E report claimed a `#20→#21→#22 bound-graph identity` not present in the product evidence | The claim is removed; the report now states that the bound-graph identity is an internal #22 concern not exposed by the product API evidence. |
| 3 | Design doc said same-track overlaps are legal, contradicting the fatal validator | Design doc now states that gaps are legal but positive same-track overlap is a fatal canonical error (422) surfaced by the editor. |
| 4 | Command-id fingerprint omitted the trim edge; no overlap-rejection test | `commandIdFor` now includes the edge; a new component test asserts the 422 overlap rejection surfaces, never applies, and re-reads the server. |

## Delta confirmation (final candidate `fbd27ae0`)

The reviewer re-checked the four conditions on the final candidate and confirmed all closed, then
re-probed the final E2E output (sha256 `29fba4bc…`, 3.000 s, red 0–2 s, blue 2–3 s, no green) and the
delta diff scope (docs/frontend/static only). Final verdict: **APPROVE**, residuals documentation-only
and closed in the following docs commit.

## Remaining items

- None blocking. The rejected/overlap scenario is covered at the component level and by the canonical
  validator; the accepted multi-clip scenario is covered by the real Chrome E2E.
- The three pre-existing baseline test failures remain out of scope and are recorded in the test matrix.
