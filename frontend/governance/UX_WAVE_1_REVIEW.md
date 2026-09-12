# Frontend product interaction and UX wave 1

HUMAN_REVIEW_REQUIRED=YES
HUMAN_REVIEW_STATUS=DEFERRED

This is uncommitted, UNFROZEN implementation work. No freeze, integration, remote operation, publication or visual approval occurred. Hermes will collect screenshots separately; none are claimed here.

## Scope and instruction precedence

Task: FRONTEND_PRODUCT_INTERACTION_AND_UX_WAVE_1. Sole implementation writer, no delegation. Worktree: `/home/user/Documents/workspace/projects/.worktrees/frontend-product-interaction-ux-wave-1`. Branch: `agent/frontend-product-interaction-ux-wave-1`. Starting and retained HEAD: `2b0c306467c98ca8b67da4ad5f7b228e52f7107a`; parent: `681aadaec61f7e0fe222bb554075ab05471ad3c3`. Initial working tree was clean. One pre-existing stash was present and left untouched.

Root `AGENTS.md` applies to all changes. No nested frontend instruction files were found. The explicit Owner task overrides the root requirement to freeze before verification: verification runs against uncommitted files, without a candidate SHA. Instruction alignment is deferred to a separate governance task; AGENTS.md and skills are untouched. Only frontend files are changed. No backend/Gradle tests, installations, Git mutations, or canonical-root operations were performed.

Inspected before implementation: product canvas model and component; Review history/compare and request tests; NLE gateway operation preview/apply/readback state machine and regression tests; app shell, route scope and design-system primitives; foundation tests; architecture guard and its negative controls.

## Changes and exact human review flows

Use the installed frontend dev server (`cd frontend && npm run dev -- --host 127.0.0.1`). URLs below use its default port 3000; use the printed port if occupied. Replace `WORKSPACE_ID` and `PROJECT_ID` with authorized server-projected IDs (URL-encode each). No demo route or production mock import was added.

1. `http://127.0.0.1:3000/w/WORKSPACE_ID/projects/PROJECT_ID/canvas`: Tab to the canvas; arrows pan, unmodified `=`/`-` zoom, buttons stop at 50% and 200%, Reset viewport returns to 100% at origin. Tab to a node: focus alone does not select; Enter/Space or click selects. Arrow keys on a node select and move that node by 24 local units. Edit Local title/X/Y in the inline local inspector; placement is bounded to −2000…2000 local units. Visual guides follow local positions without changing reference identity or relationships. Clear selection, Escape on the canvas/node, and clicking canvas background clear selection and return focus to the canvas. Show selected node brings an arrangement back into view. Modified shortcuts and text controls retain their native behavior. Navigate to another project or workspace and verify title, placement, zoom and selection reset. Titles and layout are explicitly not saved.
2. `http://127.0.0.1:3000/w/WORKSPACE_ID/projects/PROJECT_ID/review`: Semantic Changes starts with history loading; failure has an alert and Retry history; empty history has No revisions available and Refresh history; a single distinct revision explains why comparison is disabled. Select two different projected revisions. Compare stays disabled for missing/equal selections and while its request is pending. Starting comparison clears the previous result. Change either selector during an outstanding request; only the newly requested pair may render. Failure has an alert and Retry comparison. Server success with no entities shows a distinct loaded-empty state. Navigate across project/workspace scopes while history or comparison is pending; old responses must not appear. Arrow keys/Home/End navigate the linked tabs and tabpanels. Other sections continue to state that their projection is unavailable; merge remains disabled.
3. `http://127.0.0.1:3000/w/WORKSPACE_ID/projects/PROJECT_ID/edit`: The presentation stage labels playback as simulation, not decoded media. Scrub local steps 0–20, use Previous/Next or Left/Right, and Home/End for bounds. Play advances one local step per second, pauses at the limit, and scrubbing pauses simulation. On a focused lane, Enter/Space selects; Up/Down focuses and selects the adjacent lane; Escape clears. Space on a lane retains native button activation; Space on the timeline background toggles simulation. Inspector shows lane selection and local step. The advanced Add Media Clip preview/confirm/apply/readback state machine is retained; only its presentation-step reducer branch is bounded. Do not submit real operations merely to review the local controls.

At widths near 390, 768, 1024 and 1440 pixels, review wrapping toolbars, focus indicators, lane controls, long revision IDs, and the local inspector. Canvas and NLE no longer force a 900px shell minimum; shell side panels stack on narrow screens and the inline canvas inspector stacks when the center area is narrow. Native accessibility and layout need Hermes/human browser review; DOM interaction tests are not screenshot evidence.

## State behavior, fakes and gaps

History and comparison use the existing TimelineQueryGateway; no client diff, inferred HEAD, canonical merge or new endpoint exists. Request generations reject superseded responses; project/workspace keys isolate sessions. Existing ScriptedTimelineQueryGateway and ScriptedOperationGateway are used only in tests, including deferred and rejected responses. Production routes retain real gateways and existing explicit unavailable boundaries.

Canvas reference IDs, semantic identity and visual-guide definitions remain separate from local display state. NLE V1/A1 are explicitly presentation lanes; the 0–20 range is neither media duration nor authoritative timeline geometry. No clip drag/trim/editing or decoded playback was invented. The empty diff prompt now works for both explicit Review pairs and NLE comparisons.

Proposals only are recorded in `BACKEND_ENABLEMENT_REQUESTS.tsv`, with explicit blocking levels, current MOCK behavior, contract needs and acceptance criteria. Existing unresolved context, source-pin, playback, extended review and layout-persistence gaps remain visible.

## Unresolved UX questions

- Should local canvas arrangements persist, and should selection persist separately from layout? Current behavior intentionally resets on leaving scope.
- Should the generic shell inspector eventually host these selection details instead of a surface-local panel? Current generic host remains an unavailable projection placeholder.
- What server time basis, duration and safe media access should drive actual playback? Current local steps cannot answer that.
- Should unavailable Review tabs remain visible for discoverability or be hidden until projected capabilities arrive?
- What browser dimensions, touch behavior and screen-reader combinations does the Owner want as ongoing acceptance coverage? Human responsive/focus review is still deferred.

## Control-plane verification and current handoff

Hermes reran frontend-only validation after the writer exited. The current inventory is 16 frontend paths and 0 non-frontend paths. The writer-generated build was preserved outside the worktree; the three historical tracked dist files were restored byte-for-byte from HEAD. The final production build used `npm run build -- --outDir .ux-wave1-final-build`, then its output was preserved externally. Generated bundle names are not part of the current source delta.

Current results: 167/167 frontend tests pass across 23 files; lint exits 0 with 46 warnings; typecheck and production build pass. Dependency installation, installed tree, and manifest/lock consistency pass. The unchanged architecture guard still fails: exactly four new paths lack entries in the out-of-scope docs ledger (the two governance files and two Canvas test files); stale and duplicate ledger counts are zero. Guard tests are 69/70 passing, with only the repository positive control failing on this path-accounting condition. No guard was weakened. Alignment needs separate authorization for `docs/architecture/governance/frontend-product-path-classification-v1.tsv`.

Actual Firefox browser validation on the final production build passed 12 explicit checks and produced 11 screenshots, including loading/error/empty states and narrow-screen Canvas. All HTTP data was deliberately synthetic, served by an isolated read-only UX fixture server. No backend integration, decoded media playback, or human approval is claimed. One expected handled API 503 console event occurred during the deliberately failing scenario; no fatal runtime event was recorded.

Stable final evidence and screenshot gallery:
`/home/user/Documents/workspace/audit-runs/FRONTEND_PRODUCT_INTERACTION_AND_UX_WAVE_1/FINAL_REPORT.txt`
`/home/user/Documents/workspace/audit-runs/FRONTEND_PRODUCT_INTERACTION_AND_UX_WAVE_1/UX_REVIEW.html`

The mobile first viewport is dominated by stacked navigation, placeholders and the scope warning. The Canvas stage is below the fold; a separate scrolled screenshot shows it. Desktop NLE likewise devotes substantial space to empty side panels and revision metadata. These hierarchy questions remain open for human review, alongside the existing questions below. Local Canvas changes are lost when the surface unmounts, not only when the project identity changes.

HUMAN_REVIEW_REQUIRED=YES; HUMAN_REVIEW_STATUS=DEFERRED. No candidate commit, freeze, merge, remote update, or backend test was performed. The base is the pre-existing cached origin/main at `2b0c306467c98ca8b67da4ad5f7b228e52f7107a`; live canonical synchronization remains a pre-freeze prerequisite. Runtime writer route was Codex/ChatGPT subscription, account acc3, gpt-6-astra, high reasoning effort. An auxiliary analyst route failed with credit exhaustion and supplies no independent acceptance evidence. Hermes changed only this handoff documentation and generated-output placement after writer exit, not implementation source. Hermes also updated the frontend validation skill reference to document the Vite output-directory trap; the writer did not modify skills.

## Writer-stage verification (historical, superseded above)

Final command results and exact changed paths follow. Full command logs and machine-readable test reports are retained in `/tmp/ux-wave-1-*` for this session; no screenshot evidence is present.

The unchanged architecture guard reconciles every frontend file, including generated dist files, against `docs/architecture/governance/frontend-product-path-classification-v1.tsv`. That ledger is outside the authorized change scope. Required new tests/governance files and changed build artifact names need Owner-governed ledger alignment before this path gate can pass. This implementation does not weaken the guard, modify its baselines, or bypass the negative controls.


All commands below ran from `frontend/`, except `git diff --check` at the worktree root. The final Vitest JSON report and Node TAP report were parsed to check count arithmetic.

| Actual command | Exit/result | Evidence |
| --- | --- | --- |
| `npm run test -- --reporter=default --reporter=json --outputFile=/tmp/ux-wave-1-tests-final.json` | 0; 23 files, 167 tests: 167 passed, 0 failed, 0 skipped/todo | `/tmp/ux-wave-1-tests-final.log`, `/tmp/ux-wave-1-tests-final.json` |
| `npm run lint` | 0; 0 errors, 46 warnings, none in edited files | `/tmp/ux-wave-1-lint-final.log` |
| `npm run typecheck` | 0 | `/tmp/ux-wave-1-typecheck-final.log` |
| `npm run build -- --outDir dist` | 0; output confined to frontend/dist; main JS chunk size warning (601.17 kB minified) | `/tmp/ux-wave-1-build.log` |
| `npm run architecture:guard` | 1; all authority/bounded counts zero; 11 unclassified paths, 2 stale paths | `/tmp/ux-wave-1-architecture.log`, `/tmp/ux-wave-1-path-ledger.json` |
| `npm run architecture:guard:test` | 1; 70 tests: 69 passed, 1 failed, 0 cancelled/skipped/todo | `/tmp/ux-wave-1-architecture-tests.log` (TAP) |
| `git diff --check` | 0 | Final worktree whitespace check |

The one guard-test failure is `governed frontend passes with all authority counts at zero`: the repository positive control fails because of the unchanged external path ledger. Every other guard test, including negative controls, passed. There are no unexpected/missing governed runtime modules, API runtime modules, duplicate ledger paths, or authority-rule violations. The 11 new paths are the seven generated assets, two canvas tests and two governance documents listed below. The two stale ledger entries are the previous generated assets replaced by the required build. No guard or guard test was changed.

The first full Vitest run found two route-test assumptions affected by the new named canvas region and live project label. Assertions now target the named region and breadcrumb and additionally prove local title/selection reset after navigation; the complete rerun passed. An earlier targeted run passed 78 tests across five files. An initial npm invocation from the repository root failed before running tests and was corrected to the frontend working directory; it is not counted as a gate result.

The build replaced tracked dist outputs and emitted seven new assets; these remain local build artifacts for review, not published content. HEAD, branch and the pre-existing stash are unchanged. No final candidate SHA exists because this work is expressly UNFROZEN. Session evidence with source/artifact hashes and gate results is `/tmp/ux-wave-1-verification.json`.

## Exact changed files

Statuses are relative to the supplied base: M = modified, D = removed by the scoped build, A = new untracked file. Nothing outside frontend appears in this inventory.

| Status | Exact path | Scope classification |
| --- | --- | --- |
| D | `frontend/dist/assets/index-BMcgZaKx.css` | Generated frontend build output |
| D | `frontend/dist/assets/index-B_O_iL1c.js` | Generated frontend build output |
| M | `frontend/dist/index.html` | Generated frontend build output |
| M | `frontend/src/app/routeTree.test.tsx` | Interaction / regression test |
| M | `frontend/src/components/design-system/index.tsx` | Presentation implementation / accessibility |
| M | `frontend/src/product/canvas/WorkspaceCanvas.tsx` | Presentation implementation / accessibility |
| M | `frontend/src/product/canvas/model.ts` | Presentation implementation / accessibility |
| M | `frontend/src/product/review/ReviewWorkspace.test.tsx` | Interaction / regression test |
| M | `frontend/src/product/review/ReviewWorkspace.tsx` | Presentation implementation / accessibility |
| M | `frontend/src/product/timeline/NleWorkspace.test.tsx` | Interaction / regression test |
| M | `frontend/src/product/timeline/NleWorkspace.tsx` | Presentation implementation / accessibility |
| M | `frontend/src/product/timeline/SemanticDiff.tsx` | Presentation implementation / accessibility |
| M | `frontend/src/product/timeline/editor-state.test.ts` | Interaction / regression test |
| M | `frontend/src/product/timeline/editor-state.ts` | Presentation implementation / accessibility |
| M | `frontend/src/styles/foundation.css` | Presentation implementation / accessibility |
| A | `frontend/dist/assets/FoundationPages-J8Y6WSC5.js` | Generated frontend build output |
| A | `frontend/dist/assets/NleWorkspace-D8bkm_YR.js` | Generated frontend build output |
| A | `frontend/dist/assets/ReviewWorkspace-Db1vaNTi.js` | Generated frontend build output |
| A | `frontend/dist/assets/SemanticDiff-D33tyCVc.js` | Generated frontend build output |
| A | `frontend/dist/assets/WorkspaceCanvas-CYhDLQ93.js` | Generated frontend build output |
| A | `frontend/dist/assets/index-DmzUeVFh.js` | Generated frontend build output |
| A | `frontend/dist/assets/index-DvHXc8NM.css` | Generated frontend build output |
| A | `frontend/governance/BACKEND_ENABLEMENT_REQUESTS.tsv` | Review governance / capability proposal |
| A | `frontend/governance/UX_WAVE_1_REVIEW.md` | Review governance / capability proposal |
| A | `frontend/src/product/canvas/WorkspaceCanvas.test.tsx` | Interaction / regression test |
| A | `frontend/src/product/canvas/model.test.ts` | Interaction / regression test |


## Owner continuation status — read-only recent Projects (2026-09-08)

This appendix preserves all historical review conclusions above. It records the current Owner direction; it is not a new acceptance ledger or Slice number.

- Notification focus correction `c1d45edff185a99f3a961a9b29598ea2b34d0f18` is Owner-accepted within its recorded Chromium scope; evidence `16848a46bf15e1cfff38ce869b86454f18a6e3b6`, public manifest `baa499b7eacdf6d1fe6df4d30b4617eeb14333f2fd780c716fc47d3084bf2a1e`. Its regression coverage is retained, not reopened.
- Current task: `FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V1`. Selected existing `frontend-foundation-checkpoint-a-implementation-v1.md` → Compact surface design records → Workspace → PRIMARY_ACTIONS: “search Projects, inspect recent work”, bounded to the established Projects list route and read-only discovery/inspection. This is not the whole roadmap or canonical Project opening.
- Frontend behavior: local literal search, opaque status filter, deterministic name sort, reset, one shared read-only detail dialog, refresh/cancel/retry, bounded/limited snapshot and distinct unavailable/unknown/denied/unsupported/error/empty outcomes. Local presentation is not a server Revision or persisted result.
- Ordinary Projects loading remains unavailable until a correctly scoped authenticated adapter is supplied; the old dashboard wrapper cannot establish this consumer's identity/permission/receipt guarantees. The existing Home consumer is unchanged. Explicit localhost fixture data demonstrates the new flow without real authentication or transport.
- Dependency disposition: clarify existing `UXW1-001` / `FB-GAP-001` (related `FB-GAP-009`), add no requirement ID, endpoint or Operation contract. Future integration is a separately authorized single read-only recent-snapshot path.
- Status: unfrozen local feature increment; independent review required. Exact final identities, executed gates and additive delivery receipt belong to the external task report at `/home/user/Documents/workspace/audit-runs/FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V1/FINAL_REPORT.md`.
- Boundaries: no product freeze/commit/index change/merge/push/deployment, no real integration, no backend EP19 work; historical H4 and formal Slice1C status/publication gates unchanged.


## Owner adopted bounded technical review — recent Projects / V2 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED
REVIEWED_IMPLEMENTATION_TREE=0b76ac9352ad40fc155c7203ed69f6362b0db89f
PROJECTS_UNCONFIGURED_BEHAVIOR=ACCEPTABLE_WITHIN_FRONTEND_ONLY_SCOPE
ADDITIONAL_PRODUCT_CORRECTION_REQUIRED=NO
REAL_BACKEND_INTEGRATION=NOT_ESTABLISHED
DETACHED_RECEIPT_PUBLICATION=BLOCKED_AS_REPORTED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Owner adoption under FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V2. ChatGPT's review checked 141 public text payloads against the manifest, patch replay against 15 supplied final endpoints, 630 unique passing full-suite identities (54 additions/no removals versus 576), 232 unique passing targeted identities, 36 recorded Chromium checks bound to the final tree, 17 chunks reconstructing six JS artifacts, all eight build artifacts, and desktop-list/narrow-Chinese-detail screenshots. These are independently reviewed recorded results, not newly executed tests, full public-package verification by ChatGPT, or real-backend acceptance. Broader H4/Slice1C/release statuses are unchanged; this is an append to the existing status record, not another acceptance ledger.


### Current V2 increment status

`FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V2` selects the foundation plan's Compact surface design records → Operations → search/filter/sort and open backed projections, limited to existing `/operations/renders` and explicit single-Project safe RenderJobSummary inspection. Scope/acceptance and exact final verification live in the task-owned external `IMPLEMENTATION_REPORT_ZH.md`; independent review is required. No new Slice or release acceptance. Storage, accepted recent Projects and other accepted creator features remain unchanged. The original V1 detached receipt remains BLOCKED_AS_REPORTED, separate from this implementation.


## Owner adopted Render review — V3 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED_WITH_REPORT_ERRATA
REVIEWED_IMPLEMENTATION_TREE=afc520c2fb8da37961cc6931e71743256b60a3e5
PRODUCT_CORRECTION_REQUIRED=NO
REPORT_ERRATA_REQUIRED=2
REAL_BACKEND_INTEGRATION=NOT_ESTABLISHED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Owner adopts independently reviewed recorded results: 141 public text hashes, patch replay against 15 final endpoints, 680 unique passing full identities (50 additions/0 removals versus 630), 282 targeted identities, 33 browser checks, 6 JS reconstructed from 16 chunks, 8 build artifacts, desktop and narrow Chinese detail screenshots. This is not fresh execution, complete 219-file verification by ChatGPT, live integration or release approval.

Append-only errata: [/home/user/Documents/workspace/audit-runs/FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V3/PRIOR_RENDER_REPORT_ERRATA.md](/home/user/Documents/workspace/audit-runs/FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V3/PRIOR_RENDER_REPORT_ERRATA.md). Correct proposed real-source key: `render.job.summary.query`; actual Render request scope: principalId, tenantId, sessionId, projectId, not Workspace. Prior anonymous Git verification remains complete as reported; raw HTTP remains 216/219 with three timeouts. Older Projects detached receipt stays blocked and is not retried. Historical reports and broader H4/Slice1C dispositions unchanged.

V3 independently authorizes one planned local Workflow node-arrangement increment, not canonical definition editing/validation/execution. Final technical result belongs in the task-owned V3 report; no product freeze or publication authority follows.

## Owner adopted bounded technical review — Workflow local sketch / V4 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED
REVIEWED_IMPLEMENTATION_TREE=79541c256a4405e5e8cd3dd53e36e1611e9959d1
ACCEPTED_SCOPE=WORKFLOW_LOCAL_UNSAVED_NONEXECUTING_NODE_ARRANGEMENT
PRODUCT_CORRECTION_REQUIRED=NO_FOR_REVIEWED_SCOPE
REAL_BACKEND_INTEGRATION=NOT_ESTABLISHED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Owner adoption under `FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V4`. V3 evidence commit `af3927e393d17ce8c900c4411035c546d6d291c3`, public manifest SHA-256 `d6531540674393f4bc87705f4545fc0f9660cf654a78556cceca76f754e7dd8c`, prefix `tasks/FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V3/20260908T025934Z-workflow-local-sketch-v1/` in `https://github.com/shangxq007/media-platform-evidence`.

Accepted only: seven category cards with local addition, shared selection, rename, movement, reveal, removal and confirmed discard. The sketch is local, unsaved and unexecuted; it defines no canonical Workflow connections, parameters, versions or execution. Browser checks used simulated project context. Narrow-screen internal board scrolling and inherited English headings remain disclosed. No complete mobile editor, physical-device accessibility or real backend integration was accepted.

Historical V3 results: 695 unique passing full-test identities, 297 unique passing targeted identities, 15 additions/0 removals versus the prior 680-test baseline, seven engineering gates passed, 22 recorded browser passes and eight external build files. These are V3 records, not V4 executions. V3 sealed reports, package and local closeout receipt remain unchanged and are not republished by V4. Historical H4, formal Slice1C ledger integration and product release gates remain separate; this adoption does not resolve their broader dispositions.

### V4 selected planned interaction — session-local Commands shortcut

Existing plan: `frontend-product-information-architecture-v1.md`, Commands and keyboard architecture, lines 314–325: one registry; shortcuts remappable/discoverable with text-entry, IME and browser/OS precedence. Existing `ShortcutOverrides` only changes display while AppShell hardcodes Ctrl/Meta+K; V4 closes this bounded runtime/UI gap for the existing palette-open command only.

A compact shared-dialog editor changes or resets the active binding within the current shell/context. Supported subset: Mod+K, Mod+Alt+K and Mod+Alt+P; Mod denotes exactly one Ctrl or Meta. No global keyboard recorder, permission/action registry, account settings, storage, canonical Operation or real backend integration. Commands remains directly reachable by button. Existing selection/local drafts and contextual dispatcher semantics remain authoritative. Browser/OS/assistive-technology conflicts beyond this bounded subset require device-specific assessment and are not universally certified.

No backend capability is required for this local tool. Existing UXW1-002 and the API gap ledger explicitly distinguish optional Project-layout persistence from out-of-scope account/global shortcut storage; no new requirement ID or API contract. No new runtime paths planned; historical H4 and tracked-dist dispositions unchanged. Exact V4 source tree, fresh engineering/browser results, scoped preservation and evidence publication belong to the external V4 task directory, not inferred from this pre-validation progress appendix. Independent review remains required; product commit/freeze/merge/push/publication is not performed.


## Owner adopted bounded technical review — V4 shortcut / V5 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED
REVIEWED_IMPLEMENTATION_TREE=66b3b9abd9b79161c8f4c902acfd2acb336a387a
ACCEPTED_SCOPE=SESSION_LOCAL_COMMAND_PALETTE_SHORTCUT_REMAPPING
PRODUCT_CORRECTION_REQUIRED=NO_FOR_REVIEWED_SCOPE
REAL_BACKEND_INTEGRATION=NOT_ESTABLISHED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Owner adoption under FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V5. Evidence repository https://github.com/shangxq007/media-platform-evidence; commit 80bb16cfafcb709ca5499e86eaf487ccaa063d90; public manifest SHA-256 e0a2ab789361134a9cfb59be1ca600c1b3e334384f25779cbd9a7d1e7f5184b9; prefix tasks/FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V4/20260908T035937Z-command-shortcut-v1/.

Only the command-palette shortcut is remappable: Mod+K, Mod+Alt+K, Mod+Alt+P. Overrides belong to the current Shell/context, not account preferences, and clear on context/owner retirement and unmount. Registry collision checks do not guarantee universal OS/browser/accessibility compatibility. Browser validation used simulated project context. Canonical commands gained no authorization.

Historical V4 results, NOT fresh V5 executions: full tests 704 unique passing identities; targeted 279; versus V3 9 added / 0 removed; seven engineering gates passed; 21 recorded browser passes; eight external build files. V4 sealed reports and delivery records remain unchanged. H4 history, tracked-dist disposition, formal Slice1C and release remain separate.

### V5 selected planned feature — Canvas session-local layout undo/redo

The existing IA plan Commands and keyboard architecture (lines 314–329) permits local undo only for unapplied presentation drafts. Canvas currently overwrites local title/positions without recovery. V5 adds bounded undo/redo for those existing local edits, including atomic completed group drag, via the existing dispatcher/contextual discovery and Canvas toolbar. Selection/camera and canonical history are not undo targets; no persistence/network. Full pre-edit selection and acceptance criteria are in the V5 external task directory SELECTION_AND_SCOPE.md. No new source paths planned; UXW1-002 remains a separate optional layout-persistence proposal. Fresh validation and delivery will be reported externally, not inferred from this selection record.

## Owner adopted bounded technical review — V5 Canvas local history / V6 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED
REVIEWED_IMPLEMENTATION_TREE=f62687609a556dff204fc6939416c11abd45f945
ACCEPTED_SCOPE=CANVAS_SESSION_LOCAL_LAYOUT_UNDO_REDO
PRODUCT_CORRECTION_REQUIRED=NO_FOR_REVIEWED_SCOPE
REAL_BACKEND_INTEGRATION=NOT_ESTABLISHED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Accepted limits are exact: Canvas history covers titles and positions only, retains at most 50 edits, treats one completed group drag as one edit, does not restore Selection or camera, has no persistence or canonical Revision undo, and records each title-input change as one edit. Browser evidence used simulation; physical-device, IME, screen-reader, and backend acceptance were not established.

Historical V5 results, NOT fresh V6 executions: full tests 724 unique passing identities; targeted 299; 20 added / 0 removed versus V4; seven engineering gates passed; 32 recorded browser passes. V5 evidence remains separate and unchanged. H4 history, formal Slice1C, real integration, and release/publication remain separate.

## Owner adopted bounded technical review — V6 Scene/Shot read-only / V7 continuation

INDEPENDENT_TECHNICAL_REVIEW=PASS_BOUNDED_STATIC_REVIEW_ADOPTED
REVIEWED_SCOPE=PROJECT_SCOPED_SCENE_SHOT_DISCOVERY_AND_READ_ONLY_INSPECTION
EVIDENCE_COMMIT=7362b5e9e2e6df680b03a9666d5b8db264d9e413
PUBLIC_MANIFEST_SHA256=08ff3f82a0b607e28e98b24acf1db0eb1d868bd304fef784a63a9b34157e0e33
REAL_BACKEND_CONTRACT_PERMISSION_INTEGRATION=NOT_ESTABLISHED
PRODUCT_PUBLICATION=NOT_AUTHORIZED_BY_THIS_REVIEW

Owner adopts the V6 static review for its recorded bounded frontend-only scope. Accepted behavior is Project-scoped Scene/Shot discovery, explicit supplied relationships, local search/status filter/stable sort/reset, shared read-only detail, strict source/access/identity receipts, and stale-owner/source cancellation. The ordinary route remains unavailable; simulated validation used an explicitly injected source and does not establish a real contract, server permission, authentication, authorization, backend integration, or canonical Production authority.

The V6 review does not certify physical-device behavior, narrow-screen behavior beyond the recorded internal-scroll checks, IME behavior beyond the recorded cases, or a complete screen-reader assessment. It preserves the reported current-snapshot and reference-access limits and does not rewrite sealed V6 evidence. Historical H4 governance, tracked-dist disposition, formal Slice1C, release, deployment, and publication remain separate.

Current V7 work selects only the next adopted priority, Render observability. It extends the existing Operations Render browser and may reuse V6 lifecycle/safety patterns, but it does not reopen V6, authorize Workflow UX, create a backend lane, or imply acceptance. Exact V7 changes and writer checks remain external evidence pending independent review.

## Bounded Artifact metadata access correction — writer RED/GREEN handoff

Task: `FRONTEND_WAVE2_RENDER_ARTIFACT_METADATA_ACCESS_CORRECTION_V1`. V7 overall acceptance remains **PENDING**; this appendix records only interim writer validation. All prior report content is preserved. The historical 789-test baseline and V6-to-V7 retained 748 / removed 49 / added 41 accounting remain history, not fresh execution or current acceptance.

The real denied-item regression demonstrated that an available Artifact collection rendered a denied item's name, ID, type, availability, version and task link alongside a denial badge. The minimal correction now branches before those fields render: only an explicit inspectable item emits metadata after existing outer access/binding, ownership and relationship checks; denied/unknown/unavailable/stale items emit only the existing localized state badge. No protected item values enter text, attributes, links or hidden DOM. Authorized task metadata remains independently visible in its task section. Collection-level outcomes and the existing read-only Render workflow are retained.

Focused Vitest runs used the installed binary from `frontend/`, `--configLoader runner --no-cache`, and default plus JSON reporters. RED attempt `red-01`: exit 1, 42 tests = 41 passed + 1 failed, 0 skipped; the one new regression failed on six metadata-disclosure assertions before production changed. GREEN attempt `green-01`: exit 0, 91 passed, 0 failed/skipped, with an act warning in the new adapter replacement test. After an explicit response wait and added collection-level coverage, `green-02`: exit 0, 95 passed, 0 failed/skipped, no stderr warning. Counts are verified against the machine-readable reports. All 41 pre-correction focused identities remain, with 54 additions and no removals; the original RED test and its assertions remain intact.

Coverage includes all five item states, mixed allowed/restricted items, same-ID permission transitions, old DOM removal at refresh, no cached fill after unavailable/stale/error/cancel, denial followed by late old success and a new valid allow, ready and pending scope/access/binding/adapter/owner replacement, abort signals, missing/invalid metadataAccess and invalid ownership/task relationships. The schema, lifecycle, fixtures, CSS and localization files required no changes. Existing FB-GAP-005 records now explicitly require backend response trimming: DOM omission is not a confidentiality boundary, and neither a real contract nor real backend integration is established.

Writer evidence is prepared at `/tmp/FRONTEND_WAVE2_RENDER_ARTIFACT_METADATA_ACCESS_CORRECTION_V1/`, including raw logs, JSON reports, before/after evidence and `WRITER_HANDOFF.md`. The session filesystem permits writes only to the task worktree and `/tmp`; Hermes must copy these artifacts into the requested evidence root `/home/user/Documents/workspace/audit-runs/FRONTEND_WAVE2_RENDER_ARTIFACT_METADATA_ACCESS_CORRECTION_V1`. Final engineering and browser evidence will be supplied by Hermes there after this handoff; no full-gate or browser PASS is claimed. No commit, freeze, stage, Git history/ref operation, remote operation, build, backend/EP19 test, guard change, dependency change or publication was performed. The Owner brief explicitly overrides candidate freezing for this unfrozen implementation; durable instruction alignment remains separate.


### Stale Artifact read-refresh guidance correction — writer continuation (2026-09-09)

Hermes identified a literal acceptance gap: the stale item badge named staleness but did not explain that read data must be fetched again. This continuation extends only the existing `renders.artifactStale` values in `frontend/src/localization/catalogs.ts`: EN “Metadata stale; refresh Render data to check again.” and zh “元数据已过期；请刷新渲染数据后再次检查。” The guidance refers to refreshing Render read data. It introduces no item action, render retry, key, path or framework. The real restricted-item branch is unchanged, as are all five metadataAccess states: inspectable, denied, unknown, unavailable and stale.

This corrects the earlier appendix's statement that localization required no changes; that statement records the earlier implementation. The stale item now includes generic read-refresh guidance alongside its state. The earlier appendix and all historical runs remain intact.

Before the catalog edit, a new behavioral regression exercised both EN and zh stale items, checking exact generic guidance, exclusion of every protected Artifact field from the serialized section, absence of item controls and render-retry guidance, and a single read without an automatic retry. Real `stale-guidance-red`: exit 1, 97 total = 95 passed + 2 failed solely on the missing guidance. After the two catalog values and the earlier correction's exact stale-label expectation changed, `stale-guidance-green`: exit 0, 97 passed, 0 failed/skipped/todo/runtime error suites across two focused files; no stderr warning. All 95 preceding focused identities remain, with 2 added and 0 removed. Original V7 test bodies/identities and assertions remain preserved; no assertion was weakened.

Raw logs, JSON reports and exact commands are in the new `stale-guidance-red/` and `stale-guidance-green/` directories under `/tmp/FRONTEND_WAVE2_RENDER_ARTIFACT_METADATA_ACCESS_CORRECTION_V1/`. Additional before/after hashes, patches and preservation checks are in `stale-guidance-preservation/`; the existing writer handoff receives a separate append. Only catalogs, RenderBrowser tests and this review change in the worktree during this continuation. HEAD/index and other inventoried paths remain unchanged. No build, backend, dependency, dist/static, guard, Skill/Memory, credential or Git mutation was performed; external browser preparation and the Hermes evidence root were not touched. Final gates, browser evidence and overall acceptance remain Hermes's responsibility and pending.


## Owner adoption — prior Render metadata correction, bounded frontend scope

Owner accepts the prior Render Artifact metadata correction only for frontend consumption and display; the prior item-level gap is closed. Fixed review: https://github.com/shangxq007/media-platform-evidence/blob/db9a50f83f6d0c3a26bb2796df5cde805117c76b/tasks/FRONTEND_WAVE2_RENDER_ARTIFACT_METADATA_ACCESS_CORRECTION_V1/20260908T201005Z-metadata-access-correction-v1/REVIEW_INDEX.md . Evidence commit `db9a50f83f6d0c3a26bb2796df5cde805117c76b`, implementation tree `c72044119a32be6417ed8d43282f1f37c7f51e00`, manifest SHA256 `b6aa2227088440df10fce8f8c671d8ce996d73fd5c5cb7db3d33dcb848a18233`.

Recorded prior full verification: 845 passed, retained V7 789 + 56 added, removed 0, duplicates 0. Recorded browser evidence has 700 pass records and 657 distinct names, including repetitions across stages/viewports; neither count means independent user scenarios. These are supplied historical results, not fresh specialty reruns. No overall backend/authentication, cropping, physical-device, screen-reader or platform acceptance is implied. Historical H4/debt/tracked-dist decisions are unchanged.

## V8 Workflow local authoring — attempt-02 implementation and attempt-03 identity correction, final review pending

The Owner authorizes the existing Workflow arrangement plan (foundation checkpoint Workflow PRIMARY_ACTIONS, seven categories) and continuation of the V3 sketch. The current bounded flow is empty start → add → shared selection/check → title and placement editing → confirm one-card removal → continue. Category values, 12-card capacity, 120-character title bound, X 16–896 / Y 16–560 and 24-unit arrow movement remain. English/Chinese page guidance and category explanations distinguish local card layout from execution order, definitions and connections. Invocation remains disabled for every canonical access projection.

Implementation adds correctable title drafts inside the shared Selection inspector, model/dispatcher rejection of empty/whitespace/overlong titles, and a Workflow-only Agent preview check before inherited proposal truncation. Pointer direction controls and coordinate fields use the existing move dispatcher. The shared inspector opens properties through InteractionDialog and returns focus to its launcher; a single-card dialog binds confirmation to the exact target, selection revision, owner lifetime and active confirmation occurrence. Cancellation keeps content, removal clears selection/details and focuses a remaining card or the first add entry. Existing reset confirmation is retained and protected against cancelled callbacks. New cards reuse vacant default positions without relocating remaining cards. The board scrolls internally; browser layout validation remains pending.

SelectionProvider remains the sole Selection owner. Local state and retained adapters retire on store/lifetime changes, including StrictMode cleanup and document retirement. On the actual Workflow route, Project parameters/context and tenant changes or changes to the existing `workflow.invoke` EffectiveAccess projection remount the Workflow shell/Selection owner. This consumes that existing key only for retirement; it never grants canonical or local server-write authority. BLOCKED Project context continues to permit disposable presentation with its existing warning; unavailable/error contexts still remove it.

**Attempt-03 identity/session correction:** the Owner expanded the allowlist by exactly `frontend/src/auth/oidcClient.ts`. Its `subscribeOidcSessionRetirement(onRetire): () => void` observes the existing UserManager UserLoaded, UserUnloaded, AccessTokenExpired, UserSignedIn, UserSignedOut and UserSessionChanged events, plus signOutOidc initiation. Workflow subscribes in a layout effect and synchronously remounts its existing shell/Selection owner on notification, withdrawing old cards, editors and confirmations before returning to the SDK. Cleanup is symmetric and suppresses retained callbacks; unconfigured OIDC is a no-op. No async identity hydration, principal/session key, token serialization, storage hint authority, polling or second identity store is introduced. Any UserLoaded event, including renewal/reauthentication or late completion, conservatively discards current local drafts; failed signout also leaves prior drafts retired. Native SDK notifications are the detection boundary: remote identity-provider changes not emitted by the existing SDK configuration are not newly detected; no auth/session-monitor settings are changed. Real authentication and backend lifecycle integration remain unverified.

Writer RED/GREEN artifacts and the required handoff are under `/tmp/FRONTEND_WAVE2_WORKFLOW_LOCAL_AUTHORING_UX_V8/attempt-02/`. Raw failed attempts remain separate. Tests cover real invalid-title repair, dispatch bypass, pointer/keyboard movement, capacity/positions, deletion/cancellation/focus, actual retained callbacks and StrictMode ownership; route/localization/shared interaction checks are included. The old model truncation identity is explicitly mapped to rejection coverage in the handoff, with valid 120-character and text-safety assertions retained. Workflow actions create no requests, storage/persistence or canonical mutation. Inherited shell workspace dashboard/auth bootstrap and unrelated legacy test transport diagnostics are separate from Workflow actions.

Attempt-03 actual RED/GREEN and handoff are under `/tmp/FRONTEND_WAVE2_WORKFLOW_LOCAL_AUTHORING_UX_V8/attempt-03/`; attempt-02 evidence and original handoff remain unchanged. Native SDK mocks exercise the real subscription export and Workflow host under StrictMode, synchronous withdrawal, retained title/delete callback rejection with reused local IDs, signout success/failure, unconfigured behavior, symmetric cleanup and late getter/UserLoaded handling. These are simulated native-boundary tests, not real authentication or browser acceptance. The final affected-suite/typecheck/lint results are recorded in the attempt-03 handoff.

Final seven gates, build, browser execution, independent review, integration and publication remain pending Hermes work. No build, backend lane, credentials, Skill/Memory or Git mutations are authorized or performed by this implementation. No overall acceptance is claimed.


## V8 continuation — session renewal and long card titles (writer correction)

This continuation supersedes attempt-03's deliberate all-UserLoaded retirement expectation above; the historical report remains intact. Installed oidc-client-ts 3.5.0 stores a fresh User and raises UserLoaded on normal silent renewal. The real wrapper now hydrates after subscribing and compares valid SDK issuer/audience/subject/session `sid`, optional tenant claims and OAuth scope. Two same-session renewals preserve local nodes, title/positions, shared selection, inspector state, focus and unfinished invalid title input. The first trustworthy hydration establishes the comparison baseline without remounting the editor. Missing session identity or unknown/invalid expiry fails closed for preservation; `sid` is optional for login, and no subject-only or token-string identity is introduced.

Each notification supersedes pending reads, and cleanup deactivates old callbacks under StrictMode/unmount. UserLoaded checks the current SDK user: the payload is not identity authority, so a late notification preserves an already established current session and cannot hide a pending genuine change; actual changes retire before the returned SDK callback promise settles. Signout initiation (redirect success or failure) and definitive native invalidations still retire synchronously. Existing lifetime checks reject actual retained title, placement and deletion callbacks, including reused local node IDs. The SDK lacks source-operation generations: a late operation overwriting its store, or a payloadless old invalidation, cannot be distinguished from current invalidation and fails closed. Automatic silent renewal and existing authentication settings remain unchanged. Real IdP session/tenant behavior and session-bound Project/access integration remain unverified, as recorded in UXW1-002 and FB-GAP-001/002.

Workflow host retirement still uses the existing tenant/Workspace/Project and semantic EffectiveAccess state/factors. Equal access with a fresh observation time or explanation preserves edits. No canonical grant is inferred; provisional Project context and disabled invocation remain. Card CSS uses a fixed 120px border-box height below the existing 136px default row spacing and a two-line, 40px title overflow boundary. Full maximum 120-character Chinese, continuous and mixed titles remain in model data, accessible button names and keyboard-accessible editable inspector fields. Type and coordinates retain their own space. No hover-only detail, collision rules, autolayout or data truncation is added. Desktop/narrow browser geometry and final acceptance remain Hermes gates.

Current RED/GREEN, command exits, exact continuation diff and preservation evidence are in the Owner-authorized continuation `writer/` directory, with final results in WRITER_HANDOFF.md. Historical full 869 / targeted 170 / 46 lint-warning figures are not results of this continuation. No build, backend/EP19 test, staging, commit, freeze, integration, remote operation, dist/static, Skill/Memory or browser-adapter change was performed. Owner no-commit actual-tree delivery overrides the durable candidate-freeze rule for this task.


## Owner adoption — bounded V8 continuation

Owner accepts the V8 continuation for sufficiently stable, trusted SDK session renewal preserving the draft/editor and true context changes retiring it. Attributable old reads/notifications/unmount callbacks are isolated; long titles are bounded on desktop/narrow layouts. Missing sid or unknown validity may still clear. Untagged invalidation and old SDK operations overwriting storage are not universally isolated. Real IdP, backend session, Project and effective-access integration remain unestablished. Supplied historical full890 = 867 retained + 23 new, with 2 removed from baseline869; historical lint has 46 unchanged warnings. These are historical outcomes, not V9 runs. Sealed prior reports remain unchanged.

## V9 — bounded NLE Timeline navigation and inspection, writer implementation

At the existing Project `/edit` entry, ordinary NLE no longer creates V1/A1 lanes, simulated playback, synthetic clip switches or fake progress. Existing TimelineQueryGateway still provides HEAD/history/detail/compare, and existing Add Media Clip preview, frozen confirmation, apply and authoritative readback retain their Operation IDs. A distinct unavailable track/clip projection state does not imply an empty Timeline. Project/Timeline/Revision shown in navigation derive from the existing query projection; the existing HEAD gateway maps backend productId to both Project and Timeline and does not prove an independently resolved Timeline identity.

`TimelineNavigationProvider` or `NleWorkspace.navigationSource` explicitly injects an isolated verification projection. No ordinary fallback, endpoint, server DTO, backend permission or real geometry adapter is added. This frontend-local source reuses logical IDs/source pin fields, existing EffectiveAccessEntry and exact rational strings, validates echoed request/scope/target and loaded relationships, and distinguishes complete/partial loaded scope, loading, true empty, empty track, filtered no-match, unavailable, error/retry, stale and restricted. Filtering covers loaded name/type/ID only; no server totals or whole-Timeline duration are inferred. References remain metadata text without media access links.

Selection uses the existing LOCAL_EPHEMERAL dispatcher/owner and reconciled SurfaceAdapter. Mouse and semantic keyboard buttons discover/select tracks/clips; exact-time input and locate-selected preserve rational values; Up/Down/Home/End traverse loaded objects, I opens full metadata and Escape closes/refocuses or clears selection. InteractionDialog provides focus containment; target removal falls back to the navigation region. Inspector/equal access timestamps/equal same-context reads preserve position, selection and list scroll when still valid. Labels and metadata are English/Chinese, with bounded narrow layouts and full long names in accessible buttons and details.

Authored `MediaClip.TimeRange.contains` includes both endpoints; navigation reuses that rule with BigInt comparison/difference, including adjacent multiple matches and zero-length point ranges. Render execution half-open intervals are a separate contract and are not reused as authored range semantics. Unknown time basis disables location and derived duration; no assumed FPS, frame conversion, playback, zoom, clip editing or transport clock exists. Source range stays separate from timeline range.

Actual target/source/principal/tenant/session/access/store retirement aborts or isolates old reads, clears detail/selection and rejects retained callbacks. NLE now reuses the existing native OIDC retirement subscription in the Project shell; trusted equal renewal preserves navigation. The existing `timeline.operation.apply` access key is used only as a semantic retirement boundary, never as a read or write grant. The inherited SDK detection limitations above remain, and other product surfaces are unchanged. An injected host must replace its scope/access/adapter when its verification identity changes; fixture scope is not current server identity authority.

Writer native RED, iteration logs, JSON outcomes, coverage/expectation mapping and stable fixture interfaces are in the authorized V9 evidence `writer/WRITER_HANDOFF.md`. Six obsolete simulation tests are explicitly mapped to exact navigation, accessibility, viewport and no-playback coverage; original canonical-flow tests remain. Two shared synthetic Agent tests retain all assertions through a test-local host with no product fixture switch. Parent final seven gates/browser and independent acceptance remain pending. No product commits, Git index/ref/object writes, remote operations, backend/EP19 changes/tests, dependency/config edits, tracked dist/static, Skill/Memory or browser adaptation are part of this writer delivery. No H4 debt or tracked-dist policy is cleared.


## Owner adoption — V9 bounded NLE navigation and inspection

Owner accepts V9 local navigation, locate, selection, metadata inspection and the recorded lifecycle fixes, based on supplied V9 evidence commit `41cb3d631e2851cc2c7c0ee83b90b2522c1193ef` and manifest SHA256 `77505e42e41ff3956b253c00ab392521cd6375ae97aeb51cbea34db618485072`. This is bounded frontend acceptance only. Real NLE/full editing, media playback, platform access integration and complete long-title inspector layout are not accepted; the recorded long-title inspector layout limitation remains. There is no overall product, EP19 or formal Slice1C closure. The sealed V9 report remains unchanged. Historical baseline 918 tests / 46 warnings is supplied evidence, not a V10 result.

## V10 Publication workspace — writer implementation, parent gates pending

The Owner newly prioritizes the read-only Publication workspace after that bounded V9 acceptance; the amendment is appended in the existing IA priority authority. The existing Project shell gains a Publication entry. Ordinary source is unavailable; only explicit isolated host injection supplies the local discovery/list/month/day/inspection flow. DOM-PUBLICATION-001 is recovered as a proposal, not an implemented backend domain: OutputArtifact, plans, attempts and ExternalPublication stay separately identified. Platform/account, schedule/actual publication, unscheduled/indeterminate and complete/partial scope stay distinct. No backend/social/Postiz adapter, writes or analytics are added.

Native writer RED/GREEN logs, commands, JSON reports, scope and preservation evidence are under the current V10 taskroot `writer/`. New behavioral tests cover actual retained callbacks, owner/request retirement, permitted metadata omission, explicit relationships, strict Gregorian/offset/timezone behavior, focus and browsing continuity. Previous work and real index/HEAD/stash are preserved; the Owner no-commit/no-freeze rule overrides the root candidate-freeze requirement. H4 Proposal A exact new paths append in current/classification ledgers only. Parent Hermes owns final seven gates, baseline identity/warning accounting, external build, native Chromium validation and delivery. Writer implementation is not independent review or acceptance.

## V11 Publication NON-SOCKET continuation — bounded contract correction

V10 calendar/list/timezone/inspection/Selection/lifecycle behavior is retained, but its graph and test-only EffectiveAccess keys are explicitly fixture-only proposals. Statuses are bounded display values; novel raw provider strings normalize to `unknown` and do not become filters. Artifact/attempt/external relationships carry explicit supplied, known-empty, unavailable, restricted or pending-core-contract availability. Restricted relations omit values without disclosing existence or count; unavailable relations are not rendered as authoritative empty data.

The separate V11 adapter-v2 and frontend parser prepare only unresolved external observations with provider/instance/account/post references and `PENDING_CORE_CONTRACT`. No external identifier is minted into a core ID, no Project publication binding is inferred, and no attempt/result/actual time/Artifact/copy relation is synthesized. Owner-local pilot access remains narrower than platform EffectiveAccess. Ordinary entry remains unavailable and no legacy social API or provider-specific branch is connected. Adapter checks are pure synthetic mapping/request-construction tests only; all socket, HTTP, browser, real provider/account/credential/send and deployment checks remain not run. This is intermediate engineering evidence, not V11 Outcome A, real integration, independent review or acceptance.

## V11 Publication authorized local-record source — implementation update

The corrected backend source and contract handoff now authorize the ordinary frontend route to consume one Project/account-bound read-only source through the existing authenticated transport. Account discovery, exact-account half-open planned-time list and scoped detail are wired; no `bindingVersion` query, legacy paging fallback, global EffectiveAccess self-grant, fixture fallback or social write exists. The UI retains list/calendar/search/sort/timezone/detail/lifecycle behavior while treating coverage as bounded partial, `scheduledAt` only as planned time, unscheduled records as detail-only, and absent content/time/Artifact/attempt/outcome/provider facts as not provided or unknown.

This is implemented source code, not live runtime acceptance. The current no-socket lane leaves focused/full Vitest, authenticated HTTP/security, browser and backend generated-schema/Gradle/Modulith execution unclosed. Independently feasible typecheck, lint, architecture controls and external-output build are recorded in the task evidence. Formal Slice1C, H4 Proposal A, provider integration and all publication mutations remain separate.
