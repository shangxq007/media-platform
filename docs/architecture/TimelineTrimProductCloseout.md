# Timeline Trim Product Closure — Closeout

**Task:** `MEDIA_PLATFORM_TIMELINE_TRIM_PRODUCT_CLOSURE_R1`
**Branch:** `task/timeline-trim-product-closure-r1`
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-timeline-trim-product-closure-r1`

```text
BASE_COMMIT: 0d02229eda682e090b75ade8f2c9b4512cca1d41
BASE_TREE:   693e2a590a72f0838c9f90386ccc44f6da81fd6e

CANDIDATE_COMMIT: 5e10be10cca18e31812917afb0a99ba1203bb825
CANDIDATE_TREE:   12eddbdd0ba50a690ad2ac11b008b4ebd5b88981
（本报告所在提交只增加本文与测试矩阵）
```

## 1. What already existed (Phase A census)

| Capability | State before this round | Evidence |
|---|---|---|
| `TRIM_CLIP` Operation (edge START/END, exact rational delta) | **ALREADY_WORKING** | `OperationPlanner.planTrim`, `TimelineClipEditCommand`, `timeline.trim` v1 |
| Trim invariants and CAS | **ALREADY_WORKING** | planner rejects non-positive delta, exhausted ranges, negative source in; `apply_command` + revision CAS |
| Clip edit HTTP apply | **ALREADY_WORKING** | `POST /timeline-operations/clips/apply` |
| Clip edit **preview** | **MISSING** | only `/apply` existed; no side-effect-free transport |
| Timeline clip visualization / edge handles | **MISSING** | `ClipEditor` listed clips only as `<option>`; no strip, no handles |
| Trim before apply | **PARTIAL_IMPLEMENTATION** | a form field (`Exact time` + `Edge`) applied immediately with no preview |
| Frontend state after trim | **ALREADY_WORKING** | apply → authoritative HEAD check → canonical snapshot refresh |
| Refresh recovery | **ALREADY_WORKING** | canonical snapshot read on mount |
| Render of a trimmed timeline | **PARTIAL_IMPLEMENTATION** | render extent start was fixed at 0, so a head-trimmed clip (whose timeline position advances) could not be rendered |
| FFmpeg source window execution | **ALREADY_WORKING** | decode unit lowers the clip's source window to `-ss`/`-t` |

## 2. Product change

1. **Timeline strip with drag handles.** `ClipEditor` now renders the canonical clips from the
   revision snapshot as bars positioned by exact timeline time, each with a start and an end
   handle. Dragging projects the result locally; nothing is written until the user applies.
2. **Side-effect-free preview transport.** New `POST /timeline-operations/clips/preview` runs the
   canonical `OperationInvocationPort.validate`, i.e. the same planner that enforces trim
   invariants, and persists nothing. It reuses `TimelineClipEditCommand`, so there is no
   `TRIM_V2` or parallel contract.
3. **Preview → apply.** Releasing a handle previews the trim and shows the projected source window
   and visible duration. `Apply trim` submits the same command id (one revision per confirmed
   edit), re-reads authoritative HEAD, refuses a false success when HEAD moved, and refreshes from
   the canonical snapshot. `Cancel trim` discards the projection with no write.
4. **Precise numeric path.** The edge/delta inputs remain and use the same preview → apply path.
5. **Render from the trimmed position.** The render panel gained an output start so a timeline
   whose clip begins at 2 s can be rendered as 2–5 s.
6. **Rebuilt committed UI bundle.** `platform-app/src/main/resources/static` is the served bundle;
   it was rebuilt from these sources (`npm run build` publishes into it) so the feature exists for
   real users, and the OIDC bundle guard stays green.

Trim semantics (unchanged, mirrored exactly by `trim.ts`):

| Edge | timeline start | timeline end | source in | source out |
|---|---|---|---|---|
| START | +δ | unchanged | +δ × rate | unchanged |
| END | unchanged | −δ | unchanged | −δ × rate |

Non-destructive: the uploaded source artifact is never modified; only the clip's reference range
changes.

## 3. Real product verification (L3, task-owned runtime)

Chrome 157 driven through the real editor at `/w/workspace-fr-e2e/projects/project-fr-e2e/edit`,
with a 6 s synthetic source: red 0–2 s, green 2–4 s, blue 4–6 s
(sha256 `021b06f99e1608e888652e89ea7aa3b7a2cfd318e3808c6f0c85dbef72f7df5f`).

| Step | Result |
|---|---|
| source upload (real UI) | `gradient-6s.mp4` accepted as Source Artifact |
| add clip (preview + confirm) | clip 0–6 s on `track-1`, revision accepted |
| **drag head handle +2 s** → preview | `Preview valid: source 2/1s → 6/1s, 4.00s visible` (server `200`, no revision yet) |
| apply head trim | new revision `dbf141cd…`, snapshot: timeline `2/1→6/1`, source `2/1→6/1` |
| **drag tail handle −1 s** → preview | `Preview valid: source 2/1s → 5/1s` (server `200`) |
| apply tail trim | new revision `bb9c82f4…`, snapshot: timeline `2/1→5/1`, source `2/1→5/1` |
| browser reload | same revision restored; snapshot identical to the applied trim |
| RenderJob | `rj_5422d737bd324fc3889ab735d2e94c70` → `COMPLETED` |
| Artifact | `render-task-741f6c136e2dc8b9ca1ccec321ee2f93f22a15b5a0bb36c2318b` |
| Delivery | `dlv_6b773b188c984f0790b4279ffb58f9fc`, downloaded through the formal API |
| output media | MP4, H.264, 320×240, 24/1 fps, **3.000 s**, 3512 bytes, sha256 `9f48a7b6…` |
| full decode | `ffmpeg -v error -i … -f null -` exit 0 |

**Before/after content proof** (1×1 RGB sampling of the delivered file):

| Output time | Expected source position | Observed RGB | Colour |
|---|---|---|---|
| 0.2 s | 2.2 s (green) | `[1, 128, 2]` | green |
| 1.0 s | 3.0 s (green) | `[1, 128, 2]` | green |
| 2.6 s | 4.6 s (blue) | `[0, 0, 255]` | blue |

The red 0–2 s segment is absent from the output, so the head trim really removed the beginning;
the blue tail is present up to 5 s, so the tail trim removed only 5–6 s. The output duration equals
the 3 s of the extent, not the 6 s of the source.

Negative checks performed in the same run: invalid upload rejected; anonymous source read `401`;
stale-revision clip edit rejected (`404`/`409`); nonexistent artifact rejected (`422`);
anonymous delivery download `401`.

## 4. Tests

| Level | Coverage | Result |
|---|---|---|
| L1 frontend | `trim.test.ts` 12 tests (both edges, rate scaling, rejections, clamping, pixel conversion); `ClipEditor.test.tsx` 6 tests (drag-preview without write, preview-then-apply with one command id, cancel, canonical rejection, stale HEAD) | PASS |
| L1 frontend suite | `vitest run src/product/timeline` 124 tests | PASS |
| L1 frontend static | `tsc --noEmit`, `eslint` on changed files, `npm run architecture:guard` | PASS |
| L1 backend | `TimelineClipEditOperationControllerTest` 5 (2 new preview tests), `TimelineClipEditOperationServiceTest` 7, `ClipEditPlanningTest` 1, `OperationPlanner*` 16 | PASS |
| L2 | preview/apply/revision/snapshot/planning/FFmpeg source window exercised by the real L3 above; unit coverage for the planner and the preview transport | PASS (covered by L3) |
| L3 | Chrome NLE E2E end to end, including reload, RenderJob, Artifact, Delivery, download and pixel checks | PASS |
| Guards | `scripts/check-architecture-drift.sh` → `PASS (220 checks)`; `scripts/dev/verify guards` 7/7; OIDC bundle guard PASS | PASS |

## 5. Known limits

- Single clip, single video track, no audio; ripple/rolling/slip/slide, multi-clip linkage,
  rate changes and reverse playback stay out of scope. The planner already rejects non-constant-rate
  trims and would reject unsupported combinations rather than mis-render.
- The preview shows the projected **range**; it does not decode frames. The authoritative content
  proof is the rendered output above.
- Head trim keeps the clip's right edge fixed and advances its timeline position (the product's
  existing trim semantics — it is not a ripple); rendering a head-trimmed timeline therefore needs
  an explicit output start, which the panel now provides.
- The drag strip scrolls horizontally when a clip is wider than the panel; the far handle is
  reachable by scrolling (the E2E scrolls it into view, like a user).
- `docs/api/openapi-preview-current.json` is a generated runtime export and was not refreshed; the
  new preview endpoint will appear there after the next export.

## 6. Not verified

- No multi-track or audio trim behaviour was exercised.
- No browser run on a non-Chromium engine.
- No performance measurement of very long timelines in the strip.

## 7. Reproduction

```bash
python3 -B scripts/dev/first-render --output <new-external-dir> \
  --timeline-operation-editing --nle-browser

cd frontend && npx vitest run src/product/timeline
./gradlew :platform-app:test --tests '*TimelineClipEditOperationControllerTest*' --no-daemon
bash scripts/check-architecture-drift.sh --quiet
python3 -B scripts/dev/verify guards
```
