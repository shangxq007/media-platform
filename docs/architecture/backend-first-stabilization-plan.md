# Backend-first Stabilization Plan

**Created:** 2026-06-11
**Branch:** main
**Based on:** [platform-fact-gathering-report.md](./platform-fact-gathering-report.md) + P0 security/observability fixes

---

## 1. Executive Summary

The platform has completed a Vue-to-React frontend migration and a round of P0 security/observability fixes. However, the frontend remains a thin shell (1 test file, 3 routes, no real editing workflow), while the backend has substantially more implemented domain logic (timeline, render, subtitle, effect, billing, entitlement).

**Recommendation: Adopt a Backend-first stabilization strategy for the next engineering phase.**

This means:
- **Frontend:** Maintain the React shell for smoke testing and manual QA. Do not invest in complex editor features (multi-track timeline, effect panel, font picker) until backend API contracts stabilize.
- **Backend:** Prioritize security hardening, persistence boundary cleanup, render orchestration stabilization, provider SPI formalization, and font/subtitle pipeline decisions.
- **Rationale:** Building advanced frontend features against unstable backend contracts leads to rework. Stabilizing the backend first gives the frontend a solid foundation to build against.

This is not abandoning the frontend — it is sequencing the work to avoid waste.

---

## 2. Current Facts

Reference: [platform-fact-gathering-report.md](./platform-fact-gathering-report.md)

### Frontend

| Fact | Evidence |
|------|----------|
| React 19 migration complete | Zero `.vue` files, `package.json` has React deps only |
| Near-zero test coverage | 1 test file (`EditorPage.test.tsx`) |
| Typecheck baseline | 3 errors remaining (missing `graphql-request`, `oidc-client-ts` npm deps) |
| Build works | `npm run build` succeeds (173 modules, 328KB JS) |
| Editor is a thin shell | 3 routes, hardcoded templates, no store integration, no API calls |
| No real editing workflow | `Timeline.tsx` displays captions but has no multi-track, drag-drop, or clip manipulation |

### Backend

| Fact | Evidence |
|------|----------|
| 31 Gradle modules + platform-app | `settings.gradle.kts` |
| 1,526 main Java files, 410 test files | File count scan |
| Modulith boundaries real and tested | `ModularityTest` passing |
| RenderOrchestratorService is a God Object | 29 constructor params, 682 lines, touches every subsystem |
| render_job has no repository | 52 inline jOOQ refs across 12+ service files |
| 2,310 total inline jOOQ calls | All string-based, no codegen |
| 9 implemented render providers | FFmpeg (primary), MLT, GStreamer, GPAC, Libass, Skia, Remote, Bento4, Shaka |
| 2 stub providers | Blender, Remotion — no `@Component`, not registered as Spring beans |
| 3 skeleton providers | Shotstack (real API client, not wired), Natron (FFmpeg fallback), VapourSynth (FFmpeg fallback) |
| Font subsystem is skeleton | 40+ files with noop/placeholder implementations |
| Timeline backend is richest area | ~70 source files with domain model, revision history, sync, AI editing |
| Subtitle closest to usable | SRT/VTT/ASS parsing, libass burn-in, auto-captions |

### Security (Post P0 Fix)

| Status | Items |
|--------|-------|
| P0 closed | NotificationController tenant isolation, X-User-Id trust, duplicate exception handlers, MDC/logback |
| P1 open | StorageKeyPolicy path traversal, SafeDownloadUrlValidator SSRF kill-switch, BillingUsageDataLoader thread-safety, TenantGuard silent fallback |
| P2 open | SSRF TOCTOU (documented), tenant ID in URLs |

### Tests

| Status | Items |
|--------|-------|
| Zero-test modules | `config-module` (0 tests) |
| Near-zero | `frontend` (1 test), `social-publish-module` (1 test), `compatibility-migration-module` (1 test) |
| Low coverage | `payment-module` (10%), `scheduler-module` (14%), `quota-billing-module` (13%) |
| Pre-existing failure | `RenderNatronEffectsIT` — requires Natron binary not installed |

---

## 3. Decision

**Adopt Backend-first stabilization for the next engineering phase.**

| Principle | Application |
|-----------|-------------|
| Defer advanced frontend features | No multi-track timeline, effect panel, font picker, subtitle style designer until backend contracts stabilize |
| Maintain minimal React shell | Keep `EditorPage`, `RenderJobsPage`, `CapabilitiesPage` for smoke testing |
| Prioritize backend security | Close P1 security items before adding new features |
| Stabilize persistence boundaries | Extract `RenderJobRepository` before adding new domain logic |
| Formalize provider SPI | Document capability matrix and status labels before adding new providers |
| ADR before action | Write ADRs for quota/entitlement boundary, jOOQ codegen, worker deployment, font roadmap before implementing |

---

## 4. Frontend Scope During Backend-first Phase

### Allowed

| Task | Rationale |
|------|-----------|
| Delete Vue remnants | Already completed (FRONTEND-REACT-UTILITY-CLEANUP) |
| Fix remaining typecheck errors | Install missing npm deps (`graphql-request`, `oidc-client-ts`) |
| Maintain test/build baseline | `npm run typecheck`, `npm run test`, `npm run build` must pass |
| Add smoke test for shell | 1-2 tests covering `RootLayout`, `routeTree`, basic navigation |
| Fix pre-existing Natron IT profile | Guard with `@DisabledIfEnvironmentVariable` or proper tag exclusion |

### Deferred

| Feature | Reason |
|---------|--------|
| Multi-track timeline drag/drop | Backend timeline API contract not finalized |
| Advanced effect panel | Effect taxonomy + OFX provider still in flux |
| Font upload/preview UI | Font subsystem is skeleton/noop |
| Subtitle style designer | Subtitle burn-in pipeline needs stabilization |
| Provider capability editor | Provider SPI not formalized |
| Remotion template editor | Remotion provider is stub |
| Full NLE UI | Server NLE architecture not decided |

---

## 5. Backend Priority Areas

### 5.1 Security Hardening

| Issue | File | Risk | Fix Strategy |
|-------|------|------|-------------|
| StorageKeyPolicy path traversal | `StorageKeyPolicy.java:55,81` | P1 | Add `URLDecoder.decode()` + `Path.normalize()` before `..` check |
| SafeDownloadUrlValidator kill-switch | `SafeDownloadUrlValidator.java:23` | P1 | Replace `static volatile boolean` with injectable `DnsResolver` interface |
| BillingUsageDataLoader thread-safety | `BillingUsageDataLoader.java:35-55` | P1 | Use `TaskDecorator` or dedicated executor for tenant context propagation |
| TenantGuard silent fallback | `TenantGuard.java:38-52` | P2 | Review `assertSameTenantIfContextPresent` — document when silent pass is intentional vs accidental |

### 5.2 Persistence Boundary

| Table | Inline Refs | Repository? | Priority |
|-------|------------|-------------|----------|
| `render_job` | 52 | No | **First extraction target** |
| `outbox_events` | 38 | No | Second target |
| `audit_records` | 16 | No | Third target |
| `delivery_job` | 16 | No | Fourth target |
| `notification_*` (5 tables) | 31 | Partial | Consolidate |

**Strategy:** Extract `RenderJobRepository` first. Migrate `RenderJobService` (226 lines, 11 jOOQ calls) as the first consumer. Then migrate `RenderOrchestratorService` references. No jOOQ codegen in this phase.

### 5.3 Render Orchestration Stabilization

**Current state:** `RenderOrchestratorService` has 29 constructor params, 682 lines, 6 public methods, 10 optional dependencies.

**Strategy (staged, not one-shot):**

| Step | Description | Risk |
|------|-------------|------|
| 1 | Add characterization tests for `submitRenderJob` and `executeExistingRenderJob` | Low |
| 2 | Extract `RenderJobSubmissionService` (job creation + validation + snapshot resolution) | Low |
| 3 | Extract `RenderArtifactQueryService` (artifact retrieval + caching) | Low |
| 4 | Reduce optional dependencies by promoting frequently-used ones to required | Medium |
| 5 | Extract `TimelineResolutionService` (timeline loading + script parsing) | Medium |

**Do NOT:** Rewrite the entire service in one shot. Each extraction must preserve the external API.

### 5.4 Provider SPI Formalization

**Current state:** 9 implemented providers, 2 deprecated, 2 stub, 3 skeleton. No formal SPI contract.

| Action | Description |
|--------|-------------|
| Document capability matrix | Which providers support which profiles, features, GPU, remote execution |
| Define status labels | `implemented`, `poc`, `stub`, `skeleton`, `deprecated`, `planned` |
| Add `@ConditionalOnProperty` to all active providers | Already done for most; verify completeness |
| Define `RenderProvider` SPI contract | `render()`, `getSupportedProfiles()`, `supports()`, `validateEnvironment()`, `getCapability()` |
| Do NOT add new runtime providers | Until SPI is stable and tested |

### 5.5 Font / Subtitle Pipeline

**Current state:** Font subsystem has 40+ files with noop/placeholder implementations. 13 docs exist ahead of implementation.

| Action | Description |
|--------|-------------|
| Document font intake pipeline | OTS → fontTools → HarfBuzz → FreeType → Pango → Skia → libass |
| Identify noop implementations | `NoopFontValidator`, `NoopFontSecurityScanner`, `NoopFontSubsetter`, `FontBakeryValidator` returns "DISABLED" |
| Decide roadmap entry | Does font system enter near-term engineering, or remain documentation-only? |
| Do NOT build frontend FontPicker | Until backend font pipeline is implemented |

**Subtitle** is closer to usable: SRT/VTT/ASS parsing, libass burn-in, auto-captions, Remotion caption rendering. Focus subtitle effort on backend pipeline stabilization rather than frontend UI.

### 5.6 Billing / Entitlement Boundary

**Current state:** 5 modules cover billing/commerce/payment/entitlement/quota with zero inter-dependencies. `quota-billing-module` overlaps with `entitlement-module`.

| Action | Description |
|--------|-------------|
| Write ADR | Document the boundary decision: deprecate quota-billing or merge into entitlement |
| Do NOT merge modules directly | ADR must be accepted first |
| Consolidate hardcoded policies | `ProviderAccessPolicy`, `ExportCapabilityPolicy`, `EntitlementPolicy` → DB-backed |

### 5.7 Observability and Tests

| Action | Description |
|--------|-------------|
| Add `config-module` tests | Zero-test module — add property binding tests |
| Add notification security tests | Verify tenant isolation and X-User-Id rejection |
| Add outbox event tests | Verify idempotency, dead letter, retry |
| Add render characterization tests | Golden render flow, provider selection, failure handling |
| Guard Natron IT | `RenderNatronEffectsIT` requires Natron binary — add `@DisabledIfEnvironmentVariable` |

---

## 6. Phase Plan

### Phase 0: Close Open P0/P1 Validation (Current)

| Task | Status |
|------|--------|
| P0 security/observability fixes | ✅ Done |
| Frontend React utility cleanup | ✅ Done |
| Frontend Vue errors eliminated | ✅ Done |
| Natron IT profile guard | ✅ Done — tagged `render-integration`, excluded from default suite |
| Verify all P0 fixes pass tests | ✅ Done |

### Phase 1: Security Hardening

| Task | Effort | Status |
|------|--------|--------|
| StorageKeyPolicy path traversal fix | Small | ✅ Done — percent-decode + segment validation |
| SafeDownloadUrlValidator SSRF fix | Small | ✅ Done — injectable DnsResolver, no global kill-switch |
| BillingUsageDataLoader thread-safety | Medium | ✅ Done — removed TenantContext manipulation, explicit tenantId |
| TenantGuard fallback documentation | Small | Pending |
| Security regression tests | Medium | ✅ Done — 38 + 22 + 6 tests pass |

### Phase 2: Persistence Boundary

| Task | Effort | Status |
|------|--------|--------|
| Extract `RenderJobRepository` | Medium | ✅ Done — 16 methods, centralized render_job DSL |
| Migrate `RenderJobService` to repository | Medium | ✅ Done — zero inline jOOQ remaining in service |
| Migrate `RenderOrchestratorService` references | Medium | Pending — 17 inline refs, Phase 2.2 |
| Add repository tests | Medium | ✅ Done — 14 tests covering CRUD, tenant isolation |
| Extract `OutboxEventRepository` | Medium | Pending |

### Phase 3: Render Orchestrator Stabilization

| Task | Effort | Status |
|------|--------|--------|
| Characterization tests for core methods | Medium | ✅ Done — 12 tests covering submit/execute/artifact/tenant/timeline |
| Extract `RenderJobSubmissionService` | Medium | ✅ Done — submit path delegated, 0 inline jOOQ |
| Extract `RenderArtifactQueryService` | Small | ✅ Done — artifact query delegated |
| Extract `RenderJobExecutionService` | Medium | ✅ Done — execute/finish delegated, 0 inline jOOQ |
| Extract `RenderJobTimelineQueryService` | Small | ✅ Done — timeline loading delegated |
| Collapse orchestrator to pure facade | Small | ✅ Done — 78 lines, 5 deps, 0 DSLContext, 0 inline jOOQ |

**Phase 3 Complete.** RenderOrchestratorService is now a pure facade with zero domain logic.

### Phase 4: Architecture Decisions

| ADR | Decision Needed | Blocks | Status |
|-----|----------------|--------|--------|
| QUOTA-ENTITLEMENT-BOUNDARY | Deprecate quota-billing or merge into entitlement | Module lifecycle | Pending |
| JOOQ-CODEGEN-ADOPTION | Adopt codegen or continue manual DSL | Type safety | Pending |
| WORKER-ROLE-DEPLOYMENT | Same-image vs multi-image | Infrastructure | Pending |
| RENDER-PROVIDER-SPI | Formalize SPI contract before new providers | Provider additions | ✅ Done — status enum, eligibility rules, capability matrix |
| FONT-SUBTITLE-ROADMAP | Enter near-term roadmap or remain docs-only | Font subsystem | Pending |

### Phase 5: Frontend Re-entry

Only after Phases 1-4 are substantially complete.

| Task | Prerequisite |
|------|-------------|
| Define stable API contracts | Backend Phases 1-3 complete |
| Add React Query hooks for timeline/render/subtitle | Stable API contracts |
| TimelineEditor v1 | Timeline API stable, characterization tests pass |
| SubtitleEditor v1 | Subtitle burn-in pipeline stable |
| EffectPanel v1 | Effect taxonomy + provider SPI stable |
| FontPicker v1 | Font pipeline implemented (not noop) |

---

## 7. Recommended Kilo Task Backlog

### Immediate (Can Be Done Now)

| Task ID | Description | Risk | Effort |
|---------|-------------|------|--------|
| `NATRON-IT-PROFILE-GUARD` | Guard `RenderNatronEffectsIT` with `@DisabledIfEnvironmentVariable` or proper tag | Low | Small |
| `FRONTEND-MISSING-DEPS` | Install `graphql-request` and `oidc-client-ts`, resolve typecheck errors | Low | Small |
| `FRONTEND-SMOKE-TEST` | Add 1-2 tests for `RootLayout`, `routeTree`, basic navigation | Low | Small |
| `CONFIG-MODULE-TESTS` | Add property binding tests for zero-test config-module | Low | Small |
| `NOTIFICATION-SECURITY-TESTS` | Add tests for tenant isolation and X-User-Id rejection in NotificationController | Low | Medium |

### Backend Core (Requires Sequencing)

| Task ID | Description | Depends On | Effort |
|---------|-------------|------------|--------|
| `STORAGE-KEY-POLICY-TRAVERSAL-FIX` | Add URL-decode + Path.normalize() before `..` check | — | Small |
| `SSRF-KILL-SWITCH-FIX` | Replace `static volatile boolean` with injectable `DnsResolver` | — | Small |
| `BILLING-USAGE-DATALOADER-FIX` | Fix TenantContext async propagation with TaskDecorator | — | Medium |
| `RENDER-JOB-REPOSITORY-EXTRACTION` | Extract `RenderJobRepository`, migrate `RenderJobService` first | — | Medium |
| `OUTBOX-EVENT-REPOSITORY-EXTRACTION` | Extract `OutboxEventRepository` from `OutboxEventService` | — | Medium |
| `RENDER-ORCHESTRATOR-CHARACTERIZATION-TESTS` | Add tests for `submitRenderJob`, `executeExistingRenderJob` | — | Medium |
| `RENDER-JOB-SUBMISSION-SERVICE-EXTRACT` | Extract job creation/validation/snapshot from orchestrator | Characterization tests | Medium |
| `RENDER-PROVIDER-STATUS-MATRIX-FIX` | Update docs to reflect accurate provider status (stub/skeleton/planned) | — | Small |

### ADR (Requires Tech Lead Acceptance)

| Task ID | Description | Blocks |
|---------|-------------|--------|
| `QUOTA-ENTITLEMENT-BOUNDARY-ADR` | Deprecate quota-billing or merge into entitlement | Module lifecycle |
| `JOOQ-CODEGEN-ADOPTION-ADR` | Adopt codegen or continue manual DSL | Type safety strategy |
| `WORKER-ROLE-DEPLOYMENT-ADR` | Same-image vs multi-image worker | Infrastructure |
| `RENDER-PROVIDER-SPI-ADR` | Formalize SPI contract | New provider additions |
| `FONT-SUBTITLE-ROADMAP-ADR` | Enter near-term roadmap or remain docs-only | Font subsystem |

### Deferred Frontend (After Backend Stabilization)

| Task ID | Description | Prerequisite |
|---------|-------------|-------------|
| `TIMELINE-EDITOR-V1` | Multi-track timeline with drag-drop, zoom, undo/redo | Timeline API stable |
| `SUBTITLE-EDITOR-V1` | Subtitle styling, timing, font assignment UI | Subtitle pipeline stable |
| `EFFECT-PANEL-V1` | Effect picker, parameter editor, chain visualization | Effect taxonomy + SPI stable |
| `FONT-PICKER-V1` | Font upload, preview, coverage check, license display | Font pipeline implemented |
| `REMOTION-TEMPLATE-EDITOR-V1` | Remotion composition editor | Remotion provider implemented |

---

## 8. Non-goals

The following are explicitly **not** in scope for this phase:

| Non-goal | Reason |
|----------|--------|
| Microservice extraction | Modular monolith is working; premature extraction adds complexity |
| Internal REST/gRPC between modules | Spring Modulith + events is sufficient; adds network overhead |
| Continue stacking complex frontend | Frontend against unstable backend contracts leads to rework |
| Direct quota-billing module merge | Requires ADR first |
| One-shot RenderOrchestratorService rewrite | High risk; staged decomposition is safer |
| Direct BMF/MLT/Skia runtime integration | No production use case validated; SPI not formalized |
| CUDA/GPU worker | No GPU infrastructure provisioned |
| Mark skeleton providers as production-ready | Blender, Remotion, Shotstack, Natron, VapourSynth are not wired |

---

## 9. Acceptance Criteria

The Backend-first stabilization phase is considered **complete** when:

| Criterion | Verification |
|-----------|-------------|
| P0/P1 security issues closed or accepted with documented rationale | Security scan + ADR |
| `render_job` repository exists with tests | `RenderJobRepositoryTest` passing |
| `RenderOrchestratorService` has characterization tests | Tests covering `submitRenderJob`, `executeExistingRenderJob` |
| Provider status matrix is accurate | Docs match code — no skeleton/stub misrepresented as implemented |
| Font/subtitle roadmap decided | ADR accepted — either enters roadmap or remains docs-only |
| Frontend baseline stable | `npm run typecheck` (≤3 errors from known missing deps), `npm run test` passes, `npm run build` succeeds |
| ADR backlog resolved or prioritized | All 5 ADRs either accepted, rejected, or explicitly deferred with rationale |

---

## 10. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Backend stabilization takes longer than expected | Medium | Medium | Phase plan allows each phase to be independently valuable |
| Frontend team blocked waiting for stable APIs | Low | Low | Smoke UI remains functional; no frontend team actively blocked |
| RenderOrchestratorService decomposition introduces regressions | Medium | High | Characterization tests before any extraction |
| ADR decisions delayed | Medium | Medium | ADRs can be deferred — phases 1-3 don't require them |

---

## 11. Render Farm Readiness

**Design document:** [render-farm-readiness-and-worker-lease-design.md](./render-farm-readiness-and-worker-lease-design.md)

**Current maturity:** Level 3 — Persistent worker registry + DB job lease ✅
**Target maturity:** Level 4 — Capability-based scheduling

**Level 3 MVP completed (2026-06-12):**
- `render_worker` + `render_job_lease` tables (Flyway V7)
- `RenderWorkerRepository` + `RenderJobLeaseRepository`
- `RenderWorkerRegistryService` (register, heartbeat, drain, offline, prune)
- `RenderJobLeaseService` (claim, renew, complete, fail, expire) with provider eligibility filtering
- `StaleRenderJobLeaseCompensationService` (scheduled stale lease expiration)
- `RenderFarmWorkerController` — internal HTTP endpoints for worker lifecycle
- Provider eligibility: STUB/SKELETON/DEPRECATED/MOCK never dispatched, POC needs explicit allow
- 50+ tests covering all operations and eligibility rules
- **Render Farm MVP stopping point reached** — next steps only on real scaling demand

**Key design decisions:**
- DB lease queue (Phase 1) → Temporal bridge (Phase 3) → broker only if scale requires
- Same codebase + multi-image target for worker deployment
- Worker self-reports capabilities; platform validates against ProviderStatus/Eligibility
- STUB/SKELETON/DEPRECATED/MOCK providers never dispatched to workers
- Lease claim via `SELECT FOR UPDATE SKIP LOCKED` for atomicity
- 10-minute default lease duration, 3 max attempts, dead-letter after exhaustion

**Implementation deferred** until ADR acceptance and Phase 1-3 backend stabilization complete.

---

## 12. Subtitle & Font Pipeline Readiness

**Assessment document:** [../media-rendering/subtitle-font-pipeline-readiness.md](../media-rendering/subtitle-font-pipeline-readiness.md)

**Subtitle Rendering Strategy ADR:** [../media-rendering/subtitle-rendering-strategy-adr.md](../media-rendering/subtitle-rendering-strategy-adr.md)

**Subtitle pipeline:** Productized — ASS injection P0 fixed, subtitle path injection P0 fixed, FFmpeg filter path P1 fixed. End-to-end characterization tests added. SubtitleBurnInNode now delegates to SubtitleBurnInService. FFmpeg/libass is the production baseline.

**Font pipeline:** MVP complete — FontIdPolicy, BasicFontValidator, BasicFontStackResolver, BasicMissingGlyphDetector implemented. NoopFontSecurityScanner replaced by BasicFontSecurityScanner as default.

**Remotion boundary:** STUB status, not dispatch eligible. Deferred for advanced visual subtitles (templates, karaoke, word highlight). Not a dependency for baseline subtitle rendering.

**Subtitle burn-in productization:** Complete — SRT/WebVTT parse, ASS sanitized output, libass burn-in, provider selection, artifact output, error handling all verified. See [subtitle-burn-in-productization.md](../media-rendering/subtitle-burn-in-productization.md).

**Recommended next route:** Soft Subtitle Mux ADR or Timeline/Effect API Productization.

**Timeline/Effect API productization:** Complete — 49 domain models, 11 services, 4 controllers. TimelineSpec validation, effect extraction, render pipeline integration all verified. See [timeline-effect-api-productization.md](../media-rendering/timeline-effect-api-productization.md).

**Explicit non-goals:**
- No soft subtitle mux (needs packaging provider)
- No karaoke/animated captions (needs Remotion — deferred)
- No real STT for auto captions (needs Whisper/Deepgram)
- No RTL/shaping (needs HarfBuzz)
- No font subsetting in production (pyftsubset disabled)
| Font roadmap decision deferred indefinitely | Low | Low | Font subsystem is not blocking any current feature |
