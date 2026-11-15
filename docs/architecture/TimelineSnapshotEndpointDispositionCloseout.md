# Timeline Snapshot Endpoint Disposition — Closeout

**Task:** `MEDIA_PLATFORM_TIMELINE_SNAPSHOT_ENDPOINT_DISPOSITION_R1`
**Branch:** `task/timeline-snapshot-endpoint-disposition-r1`
**Worktree:** `/home/user/Documents/workspace/projects/media-platform-timeline-snapshot-endpoint-disposition-r1`

```text
BASE_COMMIT: c4096d792ffa2fc9ba569ec842800c1ddca848c4
BASE_TREE:   51d275ea9aae48cc061755e5c4814c97e5f58ed7

ENDPOINT: POST /api/render/timeline-snapshots  (TimelineSnapshotController)

ID_LENGTH_ROOT_CAUSE:
  commandId = "import-" + sha256(tenant \0 project \0 baseRevisionId \0 contentHash)
            = 7 + 64 = 71 characters
  It is passed as OperationInvocationContext.invocationId -> RevisionWriteCommand.commandId()
  and inserted as apply_command.apply_command_id, which is varchar(64).
  Single point defect: every other write entry passes a client-supplied short commandId
  (e.g. "init-1", "add-clip-0", "m-1"), so no shared id generator is at fault.

PRODUCTION_CONSUMERS:   NO_CONSUMER_FOUND
  - frontend/src/api/timelineSync.ts#saveSnapshotInternal: zero callers, wrong request shape
    (sends editorTimeline/schemaVersion/ensureInternal; the controller required payloadJson and
    had no such fields) and its sibling /render/timeline-sync/* routes do not exist in the backend.
  - no SDK, MCP tool, agent entry, RenderJob submitter or production service calls the route.
TEST_CONSUMERS:        ACTIVE_TEST_CONSUMER (legacy First Render fixture) + endpoint-only MockMvc tests
  - scripts/dev/_local_validation/exercise_first_render.py  -> migrated
  - TimelineMutationControllersMockMvcTest (3 snapshot-only tests) -> removed
DOCUMENTATION:         DOCUMENTATION_ONLY references in current docs (updated) and frozen records

INDEPENDENT_PRODUCT_RESPONSIBILITY:
  It was the only HTTP surface of the canonical IMPORT_TIMELINE operation (whole TimelineDocument
  import in one call). It was NOT a second write authority: it entered the same
  OperationInvocationPort -> IMPORT_TIMELINE -> Revision CAS path. That capability was also
  non-functional: every request failed at apply_command persistence, so no consumer could use it.
  The maintained Operation API (initialize, add-media-clip, clip edits, text, alignment) covers all
  current product flows; incremental authoring cannot express an arbitrary pre-built document, and
  that difference is recorded here rather than hidden.

OPERATION_REPLACEMENT:
  timeline-operations/initialize -> add-media-clip/preview -> add-media-clip/apply -> canonical
  revision snapshot (read) -> POST /api/render-jobs. Verified end to end below.

DISPOSITION: RETIRED

CHANGED_PATHS:
  D platform-app/src/main/java/com/example/platform/web/render/TimelineSnapshotController.java
  M platform-app/src/test/java/com/example/platform/web/render/TimelineMutationControllersMockMvcTest.java
  M build.gradle.kts                                  (GCR-1 ingress check now asserts no web controller carries ImportTimelineParameters)
  M scripts/check-architecture-drift.sh                (retired route must stay absent from production sources)
  M scripts/guards/h7-v2-architecture-guard.py         (drop the deleted controller from the auth baseline)
  M scripts/guards/h8-operation-invocation-boundary-guard.py (drop it from the authorized port-consumer list)
  M scripts/dev/_local_validation/exercise_first_render.py (upload + Operation writes + 404 assertion)
  M scripts/dev/_local_validation/exercise_timeline_operation_editing.py (404 assertion)
  M scripts/dev/_local_validation/exercise_single_video_no_audio.py      (404 assertion)
  M frontend/src/api/timelineSync.ts                   (remove the dead client method)
  M docs/testing/first-render.md
  M docs/architecture/current/current-timeline-git-status.md
  M docs/zh/platform-guide/04-implementation.md

CANDIDATE_COMMIT: 3ec6a342a261ab55d0abfb8664b8de932bed7467   (code; the exact tree the L3 ran)
CANDIDATE_TREE:   eda2992d39c82eee7e0ba1628bbb9cb3b5ec486f
REPORTS_COMMIT:   the commit that contains this file (its only difference from the code candidate
                  is this closeout and the test matrix)

FINAL_RESULT: PARTIAL
  Endpoint disposition: complete (RETIRED), Operation authority preserved, Revision/Snapshot/
  RenderJob path verified. Two guard gates stay partially red for one pre-existing false positive
  that predates this task (see §5); no guard was weakened and nothing unrelated was modified.
```

## 1. What was verified in a real application

The existing First Render E2E was run unchanged (plain entry) on the candidate:

| Step | Result |
|---|---|
| retired `POST /api/render/timeline-snapshots` | **404** `NOT_FOUND` from the live Spring app |
| `/source-media` upload | `201`, `art-4bce624e…`, probe `VIDEO` duration `1/1` |
| `timeline-operations/initialize` | `201`, revision `9e06b774…` |
| `add-media-clip/preview` → `apply` | `200` / `201` `APPLIED`, revision `fd8d5fd6…` |
| canonical revision snapshot (read) | `200` through the revision snapshot endpoint |
| `POST /api/render-jobs` | `201`, `rj_90266f7785db4a088b5371b50ba74b5e` |
| job lifecycle | `QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED` |
| artifact | `render-task-adf5032e…`, 1645 bytes, sha256 `3721f7bc…`, quota operations = 1 |
| downloaded media | `h264`, 320×240, 1.000 s |
| cover/thumbnail capability path | `REAL_COVER_THUMBNAIL_EXECUTION_COMMIT_DOWNLOAD_RETRY_PASS` |
| cancellation negative | `REAL_CANCEL_AND_ZERO_OUTPUT_QUOTA_PASS` |
| runtime cleanup | containers, volume and sessions removed (`cleanup PASS`) |

Raw evidence: `…-evidence/l3-first-render-final-20261115T1500Z/` and `…-evidence/first-render-final.log`.

## 2. Why retirement was the right disposition

1. No production consumer exists, and the only client-shaped reference could never have worked
   (wrong DTO), so no product behaviour depends on this route.
2. It was not a second write authority — it wrapped the canonical operation — so removing the HTTP
   adapter removes no domain semantics. `IMPORT_TIMELINE`, its service path, Revision CAS and the
   canonical snapshot model are untouched.
3. It was 100% non-functional on this base, so no capability regressed by deleting it.
4. The maintained Operation path already covers the product vertical slices (proven twice on the
   BMF R1 candidate and again here on this candidate).
5. Removing it leaves exactly one Timeline write authority.

Recorded difference, not hidden: the retired route could accept an arbitrary pre-built
`TimelineDocument` in one request, while the Operation API authorises edits incrementally. If the
product later needs whole-document import, it must be reintroduced as a *sanctioned Operation
transport* with a bounded command id — not as a second write path.

## 3. Guards and contracts

- A durable positive guard now fails if `/api/render/timeline-snapshots` reappears in
  `platform-app/src/main/java` (`scripts/check-architecture-drift.sh`).
- The Gradle GCR-1 ingress verification asserts that no web controller carries
  `ImportTimelineParameters` or `TimelineConversionService`, and that the deleted controller is not
  reintroduced.
- The authoritative API contracts (`contracts/http/media-api/openapi.*.yaml`) never declared this
  route, so no contract change was needed. `docs/api/openapi-preview-current.json` is a generated
  runtime export ("replace only by the export command"); it still lists the old path and must be
  refreshed by `scripts/api/export-openapi.sh` against a deployment. It was deliberately not
  hand-edited.
- Frozen governance records (`docs/architecture/governance/*` ledgers, `docs/architecture/BmfR1*`
  reports, `docs/frontend/react-smoke-editor.md`, `docs/media-rendering/*`,
  `docs/zh/timeline-version-control.md`) still mention the historical route and were left untouched
  as historical evidence.

## 4. Not changed

No Flyway migration, no public RenderJob API, no Operation SPI, no Revision CAS, no Artifact
fencing, no second write API, no compatibility adapter. `Task`/`Snapshot` domain models remain.

## 5. Pre-existing guard failure (not caused by this task, not fixed here)

On the base commit `c4096d79` the architecture drift guard already fails:

```text
❌ FAIL: CIPG1 credential residue > 0 (3)
❌ FAIL: CIPG2EG1 credential residue > 0 (3)
ARCHITECTURE_DRIFT: FAIL (219 checks)     <- on base, before this change
```

Root cause: the credential regex `sk-[A-Za-z0-9]{20,}` matches the substring inside the
`render-task-<64 hex>` Artifact ids recorded in the BMF R1 reports
(`docs/architecture/BmfR1FinalCloseout.md`, `BmfR1FinalTestMatrix.md`). Those are false positives.

Effect on this task: `scripts/dev/verify guards` and `scripts/check-document-governance.sh`
(DG-016 delegates to the architecture guard) cannot report a full PASS. Per the task rule
("不修改与本任务无关的 Guard") this was not modified. Recommended follow-up (owner decision):
tighten the regex with a leading boundary (`(?<![A-Za-z0-9])sk-[A-Za-z0-9]{20,}`), which keeps
real key detection and stops matching `…task-<hex>`. With that one-line fix the base guard returns
to `PASS (220 checks)` and DG-016 follows.

## 6. Reproduction

```bash
python3 -B scripts/dev/first-render --output <new-external-dir>          # plain First Render entry
bash scripts/check-architecture-drift.sh --quiet
./gradlew verifyGcr1CorrectionV2IngressAuthority --no-daemon
./gradlew :platform-app:test --tests '*TimelineMutationControllersMockMvcTest*' --no-daemon
```
