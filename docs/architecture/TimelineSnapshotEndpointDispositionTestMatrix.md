# Timeline Snapshot Endpoint Disposition — Test Matrix

**Task:** `MEDIA_PLATFORM_TIMELINE_SNAPSHOT_ENDPOINT_DISPOSITION_R1`
**Base:** `c4096d792ffa2fc9ba569ec842800c1ddca848c4` / `51d275ea9aae48cc061755e5c4814c97e5f58ed7`
**Candidate (code, L3-verified):** `3ec6a342a261ab55d0abfb8664b8de932bed7467` / `eda2992d39c82eee7e0ba1628bbb9cb3b5ec486f` (the reports commit adds only these two documents)
**Disposition:** `RETIRED`

Every row states the command actually run and its outcome. Rows that reuse earlier verified evidence
say so explicitly and are never presented as fresh product validation.

| Gate | Result | Command / evidence |
|---|---|---|
| `ENDPOINT_REGISTRATION` | **PASS** | Live app: `POST /api/render/timeline-snapshots` → `404 NOT_FOUND` (`exercise_first_render.http-results.json → retired_snapshot_endpoint_absent`), asserted in all three scenario fixtures. Source: no `@RequestMapping` for the route (`grep` empty). Durable guard: `bash scripts/check-architecture-drift.sh --quiet` → `PASS: retired timeline-snapshot import route absent from production sources`. Spring context compiled and served requests, so the 404 is a routing result, not a build artifact. |
| `OPERATION_AUTHORITY` | **PASS** | `./gradlew verifyGcr1CorrectionV2IngressAuthority --no-daemon` → `OK: GCR-1 CORRECTION V2 ingress authority verified` (no web controller carries `ImportTimelineParameters`/`TimelineConversionService`; retired controller absent). Runtime: `initialize` `201`, `add-media-clip/preview` `200`, `add-media-clip/apply` `201 APPLIED` for `rj_90266f7785db4a088b5371b50ba74b5e`. |
| `REVISION_CAS` | **PASS** (reused, unchanged path) | This task does not touch CAS code: `TimelineRevisionSaveService`, `apply_command`, `wf_*` and the Operation plan/apply path are byte-identical to the verified BMF R1 tree `7c9ad806…`. The Operation apply used `baseRevisionId`/`baseContentHash` from the previous revision and produced a divergent new revision (`9e06b774… → fd8d5fd6…`). CAS staleness assertions are covered by `OperationPlanConcurrencyIT` / `TimelineRevisionSaveServiceIntegrationTest` (PG18 ITs, not re-run here) and were PASS in the two prior formal L3 runs on the same code. |
| `CANONICAL_SNAPSHOT` | **PASS** | `GET /api/render/projects/{projectId}/timeline/revisions/{revisionId}/snapshot` returned the canonical document used by `runtime_proof` (`REAL_HTTP_PRODUCT_FACTS_AND_FFPROBE_PASS`); the `timeline_snapshot` model and reader are untouched. |
| `RENDERJOB_SUBMISSION` | **PASS** | `POST /api/render-jobs` → `201` `rj_90266f7785db4a088b5371b50ba74b5e`; lifecycle `QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED`; artifact `render-task-adf5032e…` 1645 bytes; download sha256 `3721f7bc…`; probe `h264` 320×240 1.000 s; quota operations = 1. |
| `AUTHORIZATION` | **PASS** | Unauthenticated `POST /api/render-jobs` → `401`; artifact negatives `401/403/404/400` in `product-closure.json`; authorization negatives in `authorization-negatives.json`; `retired` route requires no new authz surface. |
| `API_CONTRACT` | **PASS** (no authoritative change needed) | `contracts/http/media-api/openapi.{base,candidate,breaking,nonbreaking}.yaml` never declared the route (0 occurrences). `scripts/api/verify-composition-openapi.py` unaffected (composition-only). The generated `docs/api/openapi-preview-current.json` still lists the old path and must be refreshed by `scripts/api/export-openapi.sh`; it is documented as generated and was not hand-edited. |
| `ARCHITECTURE_GUARDS` | **PARTIAL** | `python3 -B scripts/dev/verify guards` → exit 1, 5/6 components pass (`architecture-drift` 1; jooq-parity, oidc-bundle, instruction governance + self-test, document-governance self-test 0). `bash scripts/check-architecture-drift.sh --quiet` → `ARCHITECTURE_DRIFT: FAIL (220 checks)`. Task-caused regressions **fixed and passing**: H7 canonical-transaction authority, H8 operation-invocation boundary, and the new retired-route check. Remaining failures are pre-existing on the base and unrelated: `CIPG1 credential residue (3)` and `CIPG2EG1 credential residue (3)` — the `sk-[A-Za-z0-9]{20,}` regex matches inside `render-task-<hex>` Artifact ids recorded in the BMF R1 reports. Base commit shows the same two failures without this change. Not modified per the task's "do not modify unrelated guards" rule. |
| `DOCUMENT_GOVERNANCE` | **PARTIAL** | `bash scripts/check-document-governance.sh --head HEAD --base main` → `Guards: 15/16 passed, decision: FAIL`. The only failing guard is `DG-016`, which delegates to the architecture drift guard above; it fails solely on the same two pre-existing credential false positives. Instruction governance `PASS` (4 files, no conflicts), document-governance self-test `PASS` (10/10), instruction self-test `PASS` (7/7). The three updated current docs pass the remaining 15 guards. |

## Supporting L1

| Check | Result | Command |
|---|---|---|
| `TimelineMutationControllersMockMvcTest` | **PASS** 11/11 (3 removed endpoint-only tests; 11 remaining controller invariants) | `python3 -B scripts/dev/verify l1 --module platform-app --tests '*TimelineMutationControllersMockMvcTest*'` → exit 0 |
| GCR-1 ingress verification | **PASS** | `./gradlew verifyGcr1CorrectionV2IngressAuthority --no-daemon` → exit 0 |
| jOOQ generated-source parity | **PASS** (no schema change) | `./gradlew verifyJooqGeneratedSources --no-daemon` → exit 0 |
| OIDC bundle configuration guard | **PASS** | `bash scripts/check-oidc-bundle-config.sh` → `=== PASS ===` |
| Instruction governance + self-test | **PASS** | `python3 scripts/governance/check-instruction-governance.py`; `…/test_check_instruction_governance.py` |
| Document-governance self-test | **PASS** | `python3 scripts/document-governance/test_document_governance_guard.py` → 10/10 |

## L2 / L3

| Check | Result | Evidence |
|---|---|---|
| First Render E2E (existing plain entry, unmodified flags) | **PASS** | `FIRST_RENDER_LOCAL_VALIDATION_PASS …/l3-first-render-final-20261115T1500Z`; `result.json` `PASS`, `cleanup PASS`, zero leftover sessions |
| Cover/thumbnail capability path | **PASS** | `REAL_COVER_THUMBNAIL_EXECUTION_COMMIT_DOWNLOAD_RETRY_PASS` |
| Cancel negative (no artifacts, no completion quota) | **PASS** | `REAL_CANCEL_AND_ZERO_OUTPUT_QUOTA_PASS` |
| BMF/FFmpeg full L3 re-run | `NOT_RUN` (by design) | The change only removes an unconsumed HTTP adapter; Operation, Revision, Snapshot, Planning, Worker and Artifact code are unchanged, so the task permits reusing the existing acceptance evidence. One maintained product L3 was run instead. |

## Not run / not applicable

| Item | Status | Reason |
|---|---|---|
| Frontend TypeScript typecheck | `NOT_RUN` | `frontend/node_modules` absent; the change deletes one unreachable method. No frontend source references the removed route. |
| PG18 CAS integration suite | `NOT_RUN` (this round) | Unchanged code; covered by prior acceptance and by existing ITs. |
| Generated OpenAPI preview refresh | `NOT_DONE` | Requires a live deployment export; the artifact is generated-only. |
| Credential-regex false-positive fix | `NOT_DONE` | Pre-existing, unrelated to this task; requires owner decision (see closeout §5). |
