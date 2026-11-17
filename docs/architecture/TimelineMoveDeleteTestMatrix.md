# Timeline Move/Delete — Test Matrix (R1)

**Task:** `MEDIA_PLATFORM_TIMELINE_MOVE_DELETE_PRODUCT_R1`
**Base:** `7435f912793e79ad06c5f65c65369de964213335` / `7012e61bc775b18ccb3d1f35d30b19d889583519`

## Automated

| # | Check | Command | Result |
|---|---|---|---|
| 1 | Frontend unit/component (all) | `cd frontend && npx vitest run` | 43 files, **410 tests, 0 failures** |
| 2 | Move projection + drag math | `npx vitest run src/product/timeline/move.test.ts` | 6 tests PASS (duration/source preservation, origin clamp, negative rejection, signed snap) |
| 3 | Clip editor move/delete | `npx vitest run src/product/timeline/ClipEditor.test.tsx` | 13 tests PASS (drag→preview→apply same command id, threshold, delete key, focus guard, delete button, rejection rollback; trim suite unchanged) |
| 4 | TypeScript | `npx tsc --noEmit` | 0 errors |
| 5 | ESLint (changed files) | `npx eslint src/product/timeline/move.ts …` | 0 errors |
| 6 | Frontend architecture guard | `npm run architecture:guard` | PASS (new paths classified, 28 governed paths) |
| 7 | Timeline suite | `npx vitest run src/product/timeline` | 135 tests PASS |
| 8 | Backend clip-edit tests | `./gradlew :platform-app:test --tests '*TimelineClipEditOperationControllerTest*' --tests '*TimelineMediaClipOperationControllerTest*' :operation-module:test --tests '*OperationPlannerFieldPreservationTest*' :render-module:test --tests '*TimelineClipEditOperationServiceTest*'` | 29 tests, 0 failures |
| 9 | Compile | `./gradlew compileJava compileTestJava` | PASS |
| 10 | Modulith | `./gradlew :platform-app:test --tests '*ModularityTest'` | PASS (0 unexpected, 0 cycles, 3 retained graph rules) |
| 11 | BMF provider contract suite | `./gradlew :bmf-provider-module:test` | 22 tests, 0 failures (unchanged) |

## Real E2E

| Item | Value |
|---|---|
| Harness | `scripts/dev/first-render --timeline-operation-editing --nle-browser` |
| Run dir | `evidence/l3-move-delete-20261117T1620Z` |
| Result | `FIRST_RENDER_LOCAL_VALIDATION_PASS` |
| Scenario | 3 clips (A 0–2 red, B 2–4 green, C 4–6 blue) → trim C to 4–5 → delete B → move C to 2–3 → refresh → render 0–3000 ms |
| Revisions | initial `475732a9…`; trim `5b3620cf…`; delete `f678982e…`; move `7cd15b0b…` |
| RenderJob / Artifact / Delivery | `rj_7ec066a4…` / `render-task-6249…` / `dlv_6b5b52…` |
| Output | 3.0 s h264 320×240 24 fps, sha256 `29fba4bc…` |
| Pixel proof | red @0.4/1.6 s, blue @2.2/2.8 s, probe @1.0 s red (deleted green absent) |
| Negatives | invalid upload rejected; unauthenticated source/delivery `401`; stale edit on a deleted clip `404` |

## Baseline failures (unchanged, out of scope)

| Test | Reason |
|---|---|
| `Cfrhi1LegacyWriteAuthorityGuardTest` | stale restore-guard expectation; controller unchanged from base |
| `RevisionCreatedEventTest` | requires the controlled-launch PostgreSQL harness |
| `JooqVersionAuthorityTest` | expects 3.19.30 while `gradle.properties` pins 3.21.9 |

No new failures were introduced by this round; none of the three is caused by the Move/Delete changes.
