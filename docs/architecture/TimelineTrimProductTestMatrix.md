# Timeline Trim Product Closure — Test Matrix

**Task:** `MEDIA_PLATFORM_TIMELINE_TRIM_PRODUCT_CLOSURE_R1`
**Base:** `0d02229eda682e090b75ade8f2c9b4512cca1d41` / `693e2a590a72f0838c9f90386ccc44f6da81fd6e`
**Code candidate:** `5e10be10cca18e31812917afb0a99ba1203bb825` / `12eddbdd0ba50a690ad2ac11b008b4ebd5b88981`
**Final candidate:** the commit that contains this matrix (adds only this file and the closeout)

All rows below are from this round on this candidate. The L3 row is a real Chrome session against a
task-owned PG18/Temporal/API/Worker/FFmpeg runtime.

| Gate | Result | Command / evidence |
|---|---|---|
| `FRONTEND_TRIM_INTERACTION` | **PASS** | Real Chrome 157 at `/w/workspace-fr-e2e/projects/project-fr-e2e/edit`: dragged the clip's start handle +160 px (2 s) and end handle −80 px (1 s); each drag produced a visible pending projection and a server preview before any write. Stages `HEAD_TRIM_PREVIEWED`, `HEAD_TRIM_APPLIED`, `TAIL_TRIM_PREVIEWED`, `TAIL_TRIM_APPLIED` in `04-tests/nle-browser/browser-result.json`; screenshots `trim-head-preview.png`, `timeline-restored.png`, `nle-final.png`. |
| `OPERATION_PREVIEW` | **PASS** | `POST /api/tenants/{t}/projects/{p}/timeline-operations/clips/preview` → `200` twice (head and tail), no revision created by preview (apply produced the only new revisions). Controller tests prove `validate` is called and `invoke` never is. |
| `OPERATION_APPLY` | **PASS** | `POST …/clips/apply` `201 APPLIED` for both trims; new revisions `dbf141cd…` (head) and `bb9c82f4…` (tail). |
| `REVISION_CAS` | **PASS** | Stale-base `MOVE_CLIP` from the pre-edit revision rejected (`404`, expected 404/409); planner tests reject exhausted/invalid ranges; editor refuses to apply when authoritative HEAD moved after preview (component test). |
| `SNAPSHOT_PERSISTENCE` | **PASS** | Canonical snapshots after each apply: head `timeline 2/1→6/1, source 2/1→6/1`; tail `timeline 2/1→5/1, source 2/1→5/1`. Persisted through the Operation path only. |
| `REFRESH_RECOVERY` | **PASS** | After `page.reload()` the same revision and the identical clip JSON were read back from the canonical snapshot (`REFRESH_AUTHORITATIVE_RESTORE`). |
| `TRIM_SOURCE_WINDOW` | **PASS** | Source window moved 0–6 → 2–6 (START advances timeline start and source in, end unchanged) → 2–5 (END retreats timeline end and source out, start/in unchanged). Zero/negative/exhausted trims rejected by the planner and by the client projection. |
| `RENDER_PLANNING` | **PASS** | RenderJob submitted for extent 2000–5000 ms on the trimmed revision; the produced output is 3.000 s (extent length) from a source-2–5 clip, proving the plan used the trimmed source window. |
| `FFMPEG_RENDER` | **PASS** | `rj_5422d737bd324fc3889ab735d2e94c70` reached `COMPLETED`; worker executed the FFmpeg provider on the 2–5 s source window. |
| `ARTIFACT_COMMIT` | **PASS** | `render-task-741f6c136e2dc8b9ca1ccec321ee2f93f22a15b5a0bb36c2318b`, 3512 bytes, `h264` 320×240 24/1 3.000 s, full decode clean. |
| `DELIVERY` | **PASS** | `dlv_6b773b188c984f0790b4279ffb58f9fc` created through the UI, downloaded through the formal result endpoint; anonymous download `401`. |
| `MEDIA_CONTENT_VALIDATION` | **PASS** | Delivered sha256 `9f48a7b66fc2109d08aca4ef513efc6362690ff11de8a5860977de25a9dea271`; 1×1 RGB samples: 0.2 s `[1,128,2]` green, 1.0 s `[1,128,2]` green, 2.6 s `[0,0,255]` blue. Red 0–2 s absent ⇒ head trim removed the opening; blue present to 5 s ⇒ tail trim removed only 5–6 s. Source sha256 `021b06f9…` (6 s) differs from the output. |
| `ARCHITECTURE_GUARDS` | **PASS** | `bash scripts/check-architecture-drift.sh --quiet` → `ARCHITECTURE_DRIFT: PASS (220 checks)`; `python3 -B scripts/dev/verify guards` → 7/7 components, `results_pass: true` (architecture drift, credential residue pattern, jOOQ parity, OIDC bundle, instruction governance + self-test, document-governance self-test); frontend `npm run architecture:guard` PASS with the new paths registered in the product path ledger. |

## Supporting L1 / L2

| Check | Result | Command |
|---|---|---|
| Trim projection unit tests | **PASS** 12/12 | `npx vitest run src/product/timeline/trim.test.ts` |
| Clip editor interaction tests | **PASS** 6/6 | `npx vitest run src/product/timeline/ClipEditor.test.tsx` |
| Timeline product suite | **PASS** 124/124 | `npx vitest run src/product/timeline` |
| TypeScript | **PASS** | `npx tsc --noEmit` (no errors) |
| ESLint (changed files) | **PASS** | `npx eslint src/product/timeline/trim.ts …` (no errors) |
| Frontend architecture guard | **PASS** | `npm run architecture:guard` |
| OIDC bundle guard | **PASS** | `bash scripts/check-oidc-bundle-config.sh` (rebuilt committed bundle) |
| Clip edit controller (preview + apply) | **PASS** 5/5 | `./gradlew :platform-app:test --tests '*TimelineClipEditOperationControllerTest*'` |
| Clip edit service | **PASS** 7/7 | `./gradlew :render-module:test --tests '*TimelineClipEditOperationServiceTest*'` |
| Clip edit planning | **PASS** 1/1 | `./gradlew :render-module:test --tests '*ClipEditPlanningTest*'` |
| Operation planner (incl. trim) | **PASS** 16/16 | `./gradlew :operation-module:test --tests '*OperationPlanner*'` |
| L2 integration | **PASS** | Covered by the L3 run (preview, apply, revision, snapshot, planning, FFmpeg, artifact, delivery) — no separate PG18 suite was needed for this change. |

## Not run / not applicable

| Item | Status | Reason |
|---|---|---|
| Ripple / rolling / slip / slide, multi-clip linkage | `NOT_RUN` | explicitly out of scope for this round; unchanged by this change |
| Audio / multi-track trim | `NOT_RUN` | out of scope; no regression introduced |
| Non-Chromium browsers | `NOT_RUN` | the owned E2E facility uses Chrome |
| Generated OpenAPI preview refresh | `NOT_DONE` → `STALE_GENERATED_ARTIFACT` | requires a deployment export; the new preview endpoint appears after the next export |
| mainline merge / push | `NOT_RUN` | not authorized for this task |
