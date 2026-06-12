# Platform Fact Gathering Report

**Generated:** 2026-06-11
**Branch:** main
**Commit:** b9b9e19 `chore: complete Vue removal - React-first frontend`
**Last updated:** 2026-06-11 (P0 security/observability fixes applied)
**Uncommitted changes:** P0 fixes applied (see Section 20)

---

## 1. Executive Summary

This report is a comprehensive factual scan of the media-platform repository, prepared to support architecture decisions, refactoring prioritization, and risk assessment. **No code was modified.**

### Key Findings

| Area | Status |
|------|--------|
| **Frontend** | React 19 migration is **complete** — zero `.vue` files, `package.json` has React deps only. One dead Vue entry point (`main.ts`) remains as cleanup debt. Only 1 frontend test file exists. |
| **Backend** | 31 Gradle modules + platform-app. 1,526 main Java files, 410 test files. Spring Boot 4.0.4 + Java 25 + jOOQ + Temporal + LiteFlow. Modulith boundaries are real and tested. |
| **Render Providers** | 9 implemented+active (FFmpeg primary), 2 deprecated, 2 stub (Blender/Remotion — no `@Component`), 3 skeleton (Shotstack/Natron/VapourSynth — not wired). |
| **Timeline/Effect/Font/Subtitle** | Backend: Timeline is richest area (~70 files). Font is skeleton with noop impls. Subtitle is closest to usable. Frontend: bare-minimum shells for all four. |
| **Security** | 2 P0 issues: NotificationController ignores tenantId + trusts X-User-Id header. 4 P1 issues including thread-safety, SSRF kill-switch, path traversal. |
| **Persistence** | 2,310 inline jOOQ calls. `render_job` has no repository (52 inline refs across 6+ services). No jOOQ codegen configured. |
| **Hardcoded Rules** | 12 HIGH-risk findings: hardcoded entitlement policies, commerce catalog, outbox routing. 7 services use ConcurrentHashMap as primary storage. |
| **Observability** | MDC fields (`tenantId`, `projectId`) referenced in logback but never populated by filters. `jobId`/`workflowId`/`eventId`/`errorCode` missing from log pattern entirely. |
| **Tests** | config-module has zero tests. Frontend has 1 test file. payment/scheduler/quota have <15% coverage. No Playwright/Cypress E2E. |

### Decisions Still Requiring Human Input

1. Whether to accept staged decomposition of `RenderOrchestratorService` (29 deps)
2. Whether `quota-billing-module` should be deprecated or merged into `entitlement-module`
3. Whether jOOQ codegen should be adopted in the near term
4. Whether the Render Provider SPI should be formalized before adding new providers
5. Whether the font system (OTS/fontTools/HarfBuzz/FreeType/Pango/Skia) should enter the near-term roadmap
6. Whether the frontend React architecture is considered stable
7. Worker deployment strategy: same-image vs multi-image target

---

## 2. Scope and Method

**Scan path:** `/home/bluepulse/Documents/code-lab/media-platform/platform`

**Commands used:**
- `find` for file discovery
- `grep -R` for pattern scanning across Java, TypeScript, YAML, SQL, and Markdown
- `git status/diff/log` for repository state
- `wc -l` for file metrics
- Direct file reads for targeted analysis

**Limitations:**
- Build output (`build/`, `node_modules/`) excluded from scans
- No tests were executed (fact gathering only)
- Runtime behavior not verified — only static code analysis
- Some file counts may include generated code in source directories

---

## 3. Repository and Module Inventory

**Root project:** `media-platform`
**Modules:** 32 (31 included in settings.gradle.kts + platform-app composition root)

| Module | Purpose | Internal Dependencies | Notes |
|--------|---------|----------------------|-------|
| `shared-kernel` | Canonical models, ports, cross-cutting utilities | *(none)* | Root dependency; `@ApplicationModule(Type.OPEN)` |
| `platform-app` | Spring Boot composition root | ALL 30 modules | Entry point, DI wiring, security config |
| `render-module` | Render orchestration (10+ backends) | shared-kernel, ai, storage, extension, entitlement | Largest module (453 main files) |
| `workflow-module` | Temporal workflow definitions | policy-governance, render, delivery | |
| `ai-module` | AI service abstraction | shared-kernel | Thin module |
| `notification-module` | Multi-channel notifications | shared-kernel | |
| `storage-module` | S3-compatible object storage | shared-kernel | |
| `delivery-module` | File delivery (SFTP/SMB/WebDAV) | shared-kernel, storage, secrets-config | |
| `prompt-module` | Prompt template management | shared-kernel | |
| `config-module` | Runtime configuration | *(none)* | No shared-kernel dep |
| `cloud-resource-module` | Cloud resource provisioning | shared-kernel | Stub provider only |
| `secrets-config-module` | Vault/secrets management | shared-kernel | |
| `extension-module` | Plugin framework (PF4J) | shared-kernel | |
| `datasource-module` | Multi-datasource routing | shared-kernel | |
| `observability-module` | Observability cross-cutting | shared-kernel | |
| `outbox-event-module` | Transactional outbox pattern | shared-kernel | |
| `audit-compliance-module` | Audit logging & compliance | shared-kernel | |
| `scheduler-module` | Task scheduling | shared-kernel | In-memory only |
| `identity-access-module` | IAM, authz, user management | shared-kernel, entitlement, artifact-catalog, storage | |
| `quota-billing-module` | Quota tracking & metering | shared-kernel | Overlaps with entitlement |
| `commerce-module` | Commerce/order management | shared-kernel | |
| `payment-module` | Payment processing (Stripe/Hyperswitch) | shared-kernel | |
| `billing-module` | Billing/invoicing/credit wallets | shared-kernel | |
| `entitlement-module` | Feature entitlement/quota profiles | shared-kernel, policy-governance | |
| `policy-governance-module` | Policy rules, feature flags | shared-kernel | |
| `artifact-catalog-module` | Artifact/media catalog | shared-kernel, storage | |
| `sandbox-runtime-module` | Sandboxed code execution | *(none)* | No shared-kernel dep |
| `sandbox-worker` | Sandbox worker process | *(none)* | Separate deployable |
| `federation-query-module` | GraphQL federation/gateway | 12 modules | Highest fan-in |
| `user-analytics-module` | User analytics | shared-kernel, identity-access | |
| `compatibility-migration-module` | Migration helpers | shared-kernel, policy, extension, audit, outbox, scheduler | |
| `remote-render-worker` | Remote render worker | shared-kernel, render, storage, ai | Separate deployable |
| `social-publish-module` | Social media publishing | shared-kernel | No `package-info.java` |

**Notable:**
- `sandbox-runtime-module` and `sandbox-worker` are fully isolated (no shared-kernel)
- `config-module` has no shared-kernel dependency
- `federation-query-module` is the heaviest internal consumer (12 deps) — acts as GraphQL aggregation layer
- Billing cluster (quota-billing, billing, payment, commerce, entitlement) has **zero inter-dependencies**

---

## 4. Backend Stack Facts

| Technology | Evidence | Status | Notes |
|-----------|----------|--------|-------|
| Java 25 | `build.gradle.kts:23` | Active | Toolchain configured for all subprojects |
| Spring Boot 4.0.4 | `build.gradle.kts:7` | Active | BOM at `build.gradle.kts:29` |
| Spring Modulith 2.0.4 | `build.gradle.kts:37`, `platform-app:54-55` | Active | Annotation-only in modules, runtime in platform-app |
| jOOQ 3.19.18 | `build.gradle.kts:8`, `platform-app:39` | Active | ~15 modules use jOOQ. **No codegen** — all manual DSL |
| Flyway | `platform-app:42-44` | Active | 2 migration files (V1, V6). Default off, activated per profile |
| Temporal 1.33.0 | `platform-app:56` | Active, disabled by default | Enabled via `temporal` profile + `TEMPORAL_ENABLED=true` |
| Kafka/RabbitMQ | Not found | **Not present** | Outbox pattern used but no broker |
| Redis | Not found | **Not present** | |
| Micrometer + Prometheus | `ai-module:9`, `commerce-module:11`, `outbox-event-module:9` | Active | OTLP export configured |
| Sentry | `application.yml:377-381` | Configured, disabled by default | `SENTRY_ENABLED=false` |
| OpenTelemetry (OTLP) | `application.yml:47-54` | Active | Metrics + tracing to `localhost:4318` |
| PostgreSQL | `platform-app:46` | Active | Production runtime dependency |
| H2 | `platform-app:45` | Active | Default dev (PostgreSQL compatibility mode) |
| OAuth2/OIDC | `platform-app:49` | Active | Authentik OIDC, JJWT 0.12.6 |
| Testcontainers | `platform-app:63-64` | Active | PostgreSQL only, used in FlywaySchemaIntegrationTest |
| LiteFlow 2.15.3.2 | `platform-app:58`, `render-module:18` | Active | Policy/routing decisions |
| PF4J 3.15.0 | `platform-app:59`, `extension-module:6` | Active | Plugin framework |
| Spring AI 2.0.0-M3 | `build.gradle.kts:31`, `platform-app:57` | Active | OpenAI-compatible, LiteLLM proxy |
| springdoc OpenAPI 3.0.2 | `platform-app:47` | Active | Swagger UI at `/swagger-ui.html` |
| Spring Security | `platform-app:48-50` | Active | CORS, JWT, OAuth2 resource server |
| Spring GraphQL | `platform-app:35` | Configured, disabled | `graphql.enabled: false` |
| Vault (HashiCorp) | `application-vault.yml` | Configured, disabled by default | KV v2, token + AppRole auth |
| Unleash | `application.yml:148-153` | Configured, disabled by default | Feature flags |
| JaCoCo 0.8.13 | `build.gradle.kts:44` | Active | XML+HTML reports |

**Gaps vs AGENTS.md:**
- OpenFeature: **Not found** — Unleash configured instead
- Kafka/RabbitMQ: **Not found**
- Redis: **Not found**
- Flyway as source of truth: **Partial** — only 2 migration files; `schema.sql` (1300+ lines) serves as test schema

---

## 5. Frontend React Facts

### Stack

| Category | Value |
|----------|-------|
| React | `^19.0.0` |
| TypeScript | `~5.7.2` |
| Build Tool | Vite `^6.0.7` (`@vitejs/plugin-react-swc`) |
| Router | `@tanstack/react-router ^1.90.0` |
| State Management | `zustand ^5.0.0` |
| Server State | `@tanstack/react-query ^5.60.0` |
| API Client | `axios ^1.7.9` + native `fetch` |
| UI Library | Tailwind CSS `^3.4.17` (no Radix/shadcn in deps) |
| Video | `remotion ^4.0.0` + `@remotion/player ^4.0.0` |
| Monitoring | `@sentry/react ^10.53.1` |
| Schema | `zod ^3.24.0` |
| Test Framework | Vitest `^3.0.0` + `@testing-library/react ^16.0.0` + `happy-dom` |
| Linter | ESLint `^10.4.0` + `typescript-eslint ^8.59.4` |

### Vue Remnants

| Check | Result |
|-------|--------|
| `.vue` files | **NONE** — zero found |
| `frontend/src/main.ts` (Vue entry) | **EXISTS** — dead code, imports `vue`, `pinia`, `@sentry/vue` |
| Vue deps in `package.json` | **NONE** |
| Vue references in `frontend/src/` | **NONE** (only in `docs/` historical docs) |

**Cleanup needed:** `frontend/src/main.ts` and `frontend/src/vite-env.d.ts` (declares `*.vue` module types) are dead code.

### Core Components

| Component | Path | Purpose |
|-----------|------|---------|
| `EditorPage` | `frontend/src/editor/EditorPage.tsx` | Main editor layout |
| `Timeline` | `frontend/src/editor/timeline/Timeline.tsx` | Caption timeline display |
| `Inspector` | `frontend/src/editor/inspector/Inspector.tsx` | Property inspector |
| `PlaybackControls` | `frontend/src/editor/playback/PlaybackControls.tsx` | Play/pause/seek |
| `CaptionEditor` | `frontend/src/editor/captions/CaptionEditor.tsx` | Add/edit captions |
| `TemplateSelector` | `frontend/src/editor/templates/TemplateSelector.tsx` | Template picker |
| `RemotionPreview` | `frontend/src/remotion/player/RemotionPreview.tsx` | Remotion placeholder |
| `RenderJobsPage` | `frontend/src/render-job/RenderJobsPage.tsx` | Render job list |
| `CapabilitiesPage` | `frontend/src/shared/CapabilitiesPage.tsx` | Platform capabilities |

### Test Files

| File | Status |
|------|--------|
| `frontend/src/editor/EditorPage.test.tsx` | Only test file |

**Recommended commands:**
- Typecheck: `npm run typecheck` (`tsc --noEmit`)
- Test: `npm run test` (`vitest run`)
- Lint: `npm run lint` (`eslint src --ext .ts,.tsx --fix`)
- Build: `npm run build` (`vite build`)

### API Layer (28 modules)

`api/index.ts` (axios instance), `api/admin/*` (13 admin modules), plus `ai-timeline.ts`, `analytics.ts`, `commerce.ts`, `delivery.ts`, `graphqlClient.ts`, `import-metadata.ts`, `me.ts`, `navigation.ts`, `prompt.ts`, `publish.ts`, `render-incremental.ts`, `timelineRevision.ts`, `timelineSync.ts`, `workspace.ts`

---

## 6. Core Service Complexity

| Service | File | Lines | Ctor Params | Public Methods | Optional Deps | jOOQ Inline | @Autowired Field | Risk |
|---------|------|------:|------------:|---------------:|--------------:|------------:|----------------:|------|
| **RenderOrchestratorService** | `render-module/.../RenderOrchestratorService.java` | 682 | 29 | 6 | 10 | 69 | 0 | **CRITICAL** |
| **RenderJobService** | `render-module/.../RenderJobService.java` | 226 | 4 | 10 | 0 | 76 | 0 | HIGH |
| **EntitlementService** | `entitlement-module/.../EntitlementService.java` | 348 | 3 | 14 | 0 | 0 | 0 | HIGH |
| **NotificationController** | `notification-module/.../NotificationController.java` | 517 | 9 | 32 | 2 | 0 | 0 | HIGH |
| **OutboxEventDispatcher** | `outbox-event-module/.../OutboxEventDispatcher.java` | 239 | 4 | 7 | 0 | 0 | 0 | MEDIUM |
| **AuditService** | `audit-compliance-module/.../AuditService.java` | 184 | 2 | 7 | 1 | 35 | 0 | MEDIUM |
| **CommerceCatalogService** | `commerce-module/.../CommerceCatalogService.java` | 105 | 0 | 4 | 0 | 0 | 0 | MEDIUM |
| **TenantGuard** | `shared-kernel/.../web/TenantGuard.java` | 53 | 0 | 4 | 0 | 0 | 0 | LOW |
| **TenantIsolationService** | `shared-kernel/.../tenant/TenantIsolationService.java` | 34 | 0 | 4 | 0 | 0 | 0 | LOW |
| **SafeDownloadUrlValidator** | `shared-kernel/.../security/SafeDownloadUrlValidator.java` | 142 | 0 | 3 | 0 | 0 | 0 | LOW |
| **CommerceController** | `commerce-module/.../CommerceController.java` | 82 | 1 | 8 | 1 | 0 | 0 | LOW |

### RenderOrchestratorService Dependencies (29)

`DSLContext`, `RenderQuotaService`, `AiGatewayPort`, `RenderProviderRouter`, `NotificationEventPublisher`, `StorageCatalogPort`, `BlobStorage`, `ApplicationEventPublisher`, `RenderJobStatusHistoryRepository`, `TimelineScriptParser`, `TimelineSpecResolver`, `IncrementalRenderOrchestrationService`, `RenderArtifactStorageService`, `TimelineSnapshotService`, `EditorTimelineConverter`, `EffectTimelineInspector`, `RenderProfileResolver`, `EffectEntitlementPort`, `RenderWorkerQueueService`, `RenderWorkerQueueProperties`, `PipelineDagExecutorService`, `TimelineExtensionsReader`, `EntitlementPort`, `RenderCacheTenantGuard`, `RenderCacheHashInvalidationNotifier`, `AiRenderScriptNormalizer`, `AiTimelineEditService`, `BaseJobTimelineLoader`, `RenderJobSubmitContinuation`

**Optional (10):** `EffectEntitlementPort`, `RenderWorkerQueueService`, `RenderWorkerQueueProperties`, `PipelineDagExecutorService`, `EntitlementPort`, `RenderCacheTenantGuard`, `RenderCacheHashInvalidationNotifier`, `AiRenderScriptNormalizer`, `AiTimelineEditService`, `RenderJobSubmitContinuation`

**Calls:** Storage, Quota, Entitlement, DAG, AI, Timeline, Artifact, Notification, Rendering — touches **every subsystem**.

---

## 7. Persistence and jOOQ Facts

### Summary

| Metric | Value |
|--------|-------|
| Total inline jOOQ occurrences | **2,310** |
| jOOQ codegen configured | **No** (plugin declared but not applied) |
| Generated jOOQ directories | **0** |
| All column references | String-based `field("tenant_id")` — no type safety |
| Repository files found | **~50** (mix of jOOQ and JDBC) |

### Tables WITHOUT Repositories (Highest Impact)

| Table | Inline In | jOOQ Refs |
|-------|-----------|-----------|
| **render_job** | RenderJobService, RenderOrchestratorService, + 10 more services | **52** |
| **outbox_events** | OutboxEventService | **38** |
| **delivery_destination** | DeliveryController, DeliveryAdminController | **18** |
| **delivery_job** | DeliveryJobService | **16** |
| **audit_records** | AuditService | **16** |
| **notification_event** | SpringNotificationEventPublisher, NotificationEventHandler | 5 |
| **notification_template** | NotificationRenderingService, NotificationTemplateService | 7 |
| **notification_event_definition** | NotificationEventCatalogService | 8 |
| **notification_channel_binding** | NotificationChannelBindingService | 7 |
| **notification_user_inbox** | NotificationInboxService | 7 |
| **config_entry** | ConfigService | 4 |
| **effect_pack** | EffectPackCatalogService | 8 |
| **timeline_snapshot** | TimelineSnapshotService | 8 |

### Tables WITH Repositories (Working Correctly)

`artifact`, `artifact_relation`, `billing_invoice`, `checkout_session`, `commerce_cart`, `purchase_order`, `entitlement_bundle`, `entitlement_grant`, `entitlement_override`, `quota_profile`, `api_key`, `project`, `tenant`, `user`, `role`, `workspace`, `workspace_member`, `workspace_group`, `payment_attempt`, `provider_webhook_event`, `subscription_contract`, `timeline_revision`, `client_export_session`, `font_asset`, `social_post`

### Recommended First Repository to Extract

**`RenderJobRepository`** — 52 inline refs across 12+ service files. Highest cleanup impact.

---

## 8. Module Boundary and Modulith Facts

| Fact | Detail |
|------|--------|
| ModularityTest | **PASSING** — `ApplicationModules.detectViolations()` with 2 known allowlisted violations |
| Registered debt | 8 entries (all `identity → artifact/storage` for ProjectImportService) |
| Unregistered violations | **None** |
| Package-info declarations | **29/30 modules** have `@ApplicationModule`. `social-publish-module` is missing. |
| platform-app role | Composition root — depends on all 30 modules. This is correct, not a "big ball of mud." |

### Cross-Module Dependencies (Non-trivial)

| From | To | Allowed? |
|------|----|----------|
| render | ai, storage, extension, entitlement | Yes |
| federation | 12 modules (highest fan-in) | Yes (declared) |
| workflow | render, delivery, policy-governance | Yes |
| identity | entitlement, artifact-catalog, storage | ent: yes; artifact/storage: registered debt |
| entitlement | policy-governance | Yes |
| delivery | storage, secrets-config | Yes |
| compatibility | policy, extension, audit, outbox, scheduler | Yes |
| user-analytics | identity-access | Yes |

---

## 9. Render Provider Facts

| Provider | @Component | @ConditionalOnProperty | Real Runtime | Tests | Auto-Dispatch | **Status** |
|----------|-----------|----------------------|-------------|-------|--------------|-----------|
| **FFmpegRenderProvider** | Yes | Yes (`ffmpeg.enabled`) | Yes (`ProcessToolRunner → ffmpeg`) | Yes (unit + golden + smoke) | Yes | **implemented** |
| **MltRenderProvider** | Yes | Yes (`mlt.enabled`) | Yes (`ProcessToolRunner → melt`) | Yes | Yes | **implemented (POC)** |
| **GStreamerRenderProvider** | Yes | Yes (`gstreamer.enabled`) | Yes (`ProcessToolRunner → gst-launch-1.0`) | Yes | No | **implemented (HOLD)** |
| **GPACRenderProvider** | Yes | Yes (`gpac.enabled`) | Yes (`ProcessToolRunner → MP4Box`) | Yes | Yes | **implemented (POC)** |
| **LibassOverlayRenderProvider** | Yes | Yes (`libass.enabled`) | Yes (FFmpeg `ass=` filter) | Yes | Yes | **implemented (POC)** |
| **SkiaStickerOverlayProvider** | Yes | Yes (`skia.enabled`) | Yes (Java2D raster + FFmpeg overlay) | Yes | Yes | **implemented (POC)** |
| **RemoteRenderProvider** | Yes | No | Yes (HTTP dispatch) | Yes | Yes | **implemented** |
| **Bento4PackagingProvider** | Yes | Yes (`bento4.enabled`) | Yes (`ProcessToolRunner → mp4fragment/mp4dash`) | Yes | N/A | **implemented (POC)** |
| **ShakaPackagingProvider** | Yes | Yes (`shaka.enabled`) | Yes (`ProcessToolRunner → shaka-packager`) | Yes | N/A | **implemented (POC)** |
| **MockRenderProvider** | Yes | No | No (simulates) | Yes | Yes | **mock** |
| **JavaCVRenderProvider** | Yes | No | Yes (JavaCV JNI) | Yes | No | **deprecated** |
| **OFXRenderProvider** | Yes | No | No (Java2D simulation) | Yes | No | **deprecated** |
| **BlenderRenderProvider** | **No** | No | Stub (`stubOnMissingBinary=true`) | No | No | **stub** |
| **RemotionRenderProvider** | **No** | No | Stub (`stubOnMissingCli=true`) | Command builder only | No | **stub** |
| **ShotstackRenderProvider** | **No** | No | Yes (real API client) | Yes | No | **skeleton** |
| **NatronRenderProvider** | **No** | No | FFmpeg fallback | Yes | No | **skeleton** |
| **VapourSynthRenderProvider** | **No** | No | FFmpeg fallback | No | No | **skeleton** |

**Key rules enforced:**
- AI providers (Claude/GPT/etc) are NOT listed as render providers
- Blender/Remotion have no `@Component` — they are not Spring beans, not auto-registered
- Natron explicitly admits "Currently FFmpeg vignette fallback, not real Natron integration"
- OFX is "Java2D simulation, NOT real OFX plugin" per its own Javadoc
- Shotstack has a real API client but is not wired (`@Component` absent)

---

## 10. Timeline / Effect / Font / Subtitle Facts

| Feature | Backend | Frontend | Docs | Status |
|---------|---------|----------|------|--------|
| **Timeline** | ~70 source files. Rich domain: `TimelineSpec`, `TimelineClip`, `TimelineTrack`, `TimelineSegment`, `TimelineTextOverlay`, `TimelineSticker`, `TimelineTransition`. Services: `TimelineExecutorService`, `TimelinePatchService`, `TimelineSnapshotService`, `TimelineRevisionService`, `AiTimelineEditService`. Standards: OTIO, AAF, EDL, FCP XML adapters. DB: `timeline_snapshot` + `timeline_revision` tables. 20+ test files. | `Timeline.tsx` — renders caption blocks on a time ruler. No multi-track, no drag-and-drop, no clip manipulation. | Extensive: 6+ architecture docs | **Partial** (rich backend, bare frontend) |
| **Effect** | ~25 files. Domain: `TimelineClipEffect`, `EffectDescriptor`, `EffectKeyframe`. Services: `AdvancedEffectsPipeline`, `EffectPackCatalogService`. DB: `effect_pack` + `effect_pack_effect` tables. | None. No effect panel or picker. | `effect-taxonomy-v1.md`, `advanced-effects-pipeline.md`, `ofx-provider.md` | **Partial** (backend catalog, no frontend) |
| **Font** | ~40 files under `render-module/.../font/`. Includes `FontAsset`, `FontManifest`, `FontRegistryService`, `FontValidator`, `FontSecurityScanner`, `FontSubsetter`, `FontCoverageChecker`. **Most are noop/placeholder** (`NoopFontValidator`, `NoopFontSecurityScanner`, `NoopFontSubsetter`). Known tech debt: subset generation is a copy, not real subsetting. | Types only: `SubtitleFont`, `customFontsAllowed` flag. No font upload/preview UI. | 13 docs (font-pipeline, font-validation, font-security, font-subsetting, etc.) | **Partial (skeleton)** — interfaces + noop impls, heavy docs ahead of implementation |
| **Subtitle/Caption** | ~30 files. Domain: `SubtitleCue`, `SubtitleTrack`, `SubtitleFont`. Standards: `SrtSubtitleAdapter`, `WebVttSubtitleAdapter`. Services: `SubtitleBurnInService`, `LibassSubtitleCompositor`, `AutoCaptionsService`. DB: `max_subtitle_tracks` in entitlement. 8 test files. | `CaptionEditor.tsx` (basic add/edit), `subtitleParser.ts` (SRT/VTT/ASS parsing). No styling UI, no timing editor. | `subtitle-render-api-mvp.md`, `subtitle-burn-in-service.md`, `multi-language-subtitle.md` | **Partial** (closest to usable) |

**Cross-cutting gap:** All four features have strong backend domain models and extensive documentation, but the frontend editor is a thin demo with no real editing workflow. The `InternalTimeline` ↔ editor sync contract (`TimelineEditorSyncController`) exists on the backend but has no frontend consumer.

**Font/text-render stack NOT registered:** fontTools, HarfBuzz, FreeType, Pango, Skia — none mentioned in any doc.

---

## 11. Security Facts

| # | Finding | Evidence | Risk | Human Review? |
|---|---------|----------|------|---------------|
| 1 | NotificationController ignores `tenantId` path variable — returns unscoped data | `NotificationController.java:64-98` | **P0** | Yes |
| 2 | NotificationController trusts `X-User-Id` header — any caller can impersonate any user | `NotificationController.java:166,178,193,...` (14 endpoints) | **P0** | Yes |
| 3 | `BillingUsageDataLoader` swaps `TenantContext` across async threads on shared ForkJoinPool | `BillingUsageDataLoader.java:35-55` | **P1** | Yes |
| 4 | `SafeDownloadUrlValidator` has global mutable `skipDnsResolution` kill-switch | `SafeDownloadUrlValidator.java:23,30-32` | **P1** | Yes |
| 5 | `StorageKeyPolicy` path traversal check is substring-based, no URL-decode normalization | `StorageKeyPolicy.java:55,81` | **P1** | Yes |
| 6 | Dev compose ships real-looking default JWT secret as fallback | `docker-compose.dev.yml:32` | **P1** | Yes |
| 7 | `TenantGuard.tenantOrDefault()` silent pass-through when TenantContext is null | `TenantGuard.java:38-52` | **P2** | Yes |
| 8 | SSRF TOCTOU race — no per-request DNS pinning | `SafeDownloadUrlValidator.java:82-93` | **P2** | Yes |
| 9 | `ToolRegistry` same `..` substring weakness | `ToolRegistry.java:141` | **P2** | Yes |
| 10 | Multiple controllers expose tenantId in public URL paths (leaked in logs/CDN) | `RenderController`, `QuotaController`, `EntitlementController` | **P2** | Yes |
| 11 | Font security scanners are skeleton-only (`OTSFontSecurityScanner` disabled) | `docs/render/font-qa-roadmap.md:15,130` | **P2** | Yes |
| 12 | K8s secrets committed with placeholder values | `k8s/base/secret.yaml:10-16` | **P2** | No |
| 13 | Sandbox worker ingress allows traffic from any `app: media-platform` pod | `k8s/base/networkpolicy-sandbox-worker.yaml:22-29` | **P3** | No |

---

## 12. Hardcoded Rules and In-Memory Storage

### HIGH Risk (12 findings)

| # | Finding | File | Kind |
|---|---------|------|------|
| 1 | Hardcoded product catalog (prices, SKUs) | `CommerceCatalogService.java:13` | hardcoded catalog |
| 2 | Hardcoded order values in switch | `PurchaseOrderCreatedEvent.java:15` | hardcoded catalog |
| 3 | Hardcoded quota limits in switch (10000, 1000, 100000) | `EntitlementService.java:251` | hardcoded quota |
| 4 | Hardcoded fallback entitlement snapshot | `EntitlementService.java:320` | hardcoded quota |
| 5 | Hardcoded tier→provider/preset/resolution policy | `ProviderAccessPolicy.java:17`, `ExportCapabilityPolicy.java:21`, `EntitlementPolicy.java:57` | hardcoded catalog |
| 6 | Hardcoded outbox event→class routing (6 types) | `OutboxEventDispatcher.java:217` | hardcoded event routing |
| 7 | In-memory ConcurrentHashMap as primary storage (4 maps) | `CheckoutOrchestrator.java:37-40` | in-memory persistence |
| 8 | In-memory ConcurrentHashMap carts | `CommerceCartService.java:21` | in-memory persistence |
| 9 | In-memory ConcurrentHashMap templates (5 maps) | `PromptTemplateService.java:29-33` | in-memory persistence |
| 10 | In-memory ConcurrentHashMap report executions | `ReportExecutionService.java:30` | in-memory persistence |
| 11 | In-memory ConcurrentHashMap query history | `QueryHistoryService.java:22` | in-memory persistence |
| 12 | In-memory ConcurrentHashMap extension audit events | `ExtensionAuditService.java:25` | in-memory persistence |

### MEDIUM Risk (5 findings)

| # | Finding | File | Kind |
|---|---------|------|------|
| 13 | Hardcoded tier→preset mapping | `EntitlementPolicyService.java:298` | hardcoded catalog |
| 14 | In-memory query datasets | `QueryCatalogService.java:19` | in-memory persistence |
| 15 | In-memory SLA/usage/incident maps | `ThirdPartyProviderHealthService.java:24-27` | in-memory persistence |
| 16 | In-memory extension registry (6 maps) | `ExtensionRegistryService.java:25-30` | in-memory persistence |
| 17 | Hardcoded provider cost lookup switch | `ThirdPartyProviderHealthService.java:60` | hardcoded catalog |

### LOW Risk / Acceptable (8 findings)

Hardcoded discount type switch, security regex patterns, language set switches, migration adapter switches, sandbox language defaults — all acceptable as compiled constants or finite enum dispatch.

---

## 13. Error Model and Observability

| Area | Evidence | Gap | Priority |
|------|----------|-----|----------|
| ProblemDetail | 30 usages across modules | Per-controller `@ExceptionHandler` duplicates logic | P1 |
| Exception Handlers | 2 global `@RestControllerAdvice` classes | **Duplicate handler chains** — execution order undefined | **P0** |
| ErrorCode Infrastructure | `ErrorCode`, `CommonErrorCode`, `ConfigurableErrorCode`, `ErrorCodeRegistry` in shared-kernel | Solid foundation. No gap. | — |
| PlatformException | Used in prompt, federation, extension modules | commerce-module and extension-module throw raw exceptions instead | P1 |
| Scattered RuntimeExceptions | ~30 raw `throw new RuntimeException/IllegalStateException/IllegalArgumentException` | No structured error codes; free-text messages | P1 |
| Logback Config | JSON structured: `traceId`, `requestId`, `tenantId`, `projectId` | **Missing**: `jobId`, `workflowId`, `eventId`, `errorCode` | **P0** |
| MDC Population | `PlatformTraceCorrelationFilter` sets `traceId` + `requestId` only | **`tenantId` and `projectId` referenced in logback but never populated by any filter** | **P0** |
| Observability in Events | `tenantId` in 12+ events; `jobId`/`workflowId` in render events only | `projectId` absent from all events; no convention enforced | P1 |
| Audit Logging | `AdminAuditLogger` (JSON, structured); `QueryAuditService` in federation | Admin audit only; no general-purpose business operation audit trail | P2 |

---

## 14. Tests and CI

### Per-Module Test Coverage

| Module | Main Files | Test Files | Ratio | Risk |
|--------|-----------|-----------|-------|------|
| config-module | 4 | 0 | 0% | **CRITICAL** |
| frontend | N/A | 1 | ~0% | **CRITICAL** |
| social-publish-module | 29 | 1 | 3% | HIGH |
| compatibility-migration-module | 19 | 1 | 5% | HIGH |
| payment-module | 30 | 3 | 10% | HIGH |
| cloud-resource-module | 9 | 1 | 11% | HIGH |
| sandbox-worker | 7 | 1 | 14% | HIGH |
| remote-render-worker | 8 | 1 | 13% | HIGH |
| scheduler-module | 7 | 1 | 14% | HIGH |
| quota-billing-module | 8 | 1 | 13% | HIGH |
| render-module | 453 | 120 | 26% | LOW |
| platform-app | 119 | 50 | 42% | LOW |
| identity-access-module | 100 | 34 | 34% | LOW |
| federation-query-module | 111 | 44 | 40% | LOW |
| policy-governance-module | 39 | 20 | 51% | LOW |

### CI Pipeline

| Job | Trigger | Commands |
|-----|---------|----------|
| backend | All pushes + PRs | `./gradlew test` + `bootJar` + Docker build |
| frontend | All pushes + PRs | `npm ci` + `npm run lint` + `npx vitest run` + `npm run build` |
| images | Main branch only | Build 3 Docker images → `ghcr.io`, render staging GitOps manifests |
| deploy-staging | Manual dispatch | Render manifests, create GitOps PR |
| promote-production | Manual dispatch | Strict validation, explicit imageTag required |

### Test Infrastructure

| Item | Status |
|------|--------|
| Testcontainers | PostgreSQL only (FlywaySchemaIntegrationTest) |
| render-integration profile | Exists (`platform-app/build.gradle.kts:77`), tag on `RenderPipelineDagIT` |
| E2E | Shell script only (`scripts/smoke/e2e-render-flow.sh`) — no Playwright/Cypress |
| Golden Render | `test-assets/golden-render-project-v1/` — synthetic assets, validation scripts |

---

## 15. Documentation Status

### Existing Architecture Docs

| Doc | Location |
|-----|----------|
| Architecture README | `docs/architecture/README.md` |
| System Architecture | `docs/architecture/01-system-architecture.md` |
| Backend Architecture | `docs/architecture/02-backend-architecture.md` |
| Module Architecture | `docs/architecture/03-module-architecture.md` |
| Frontend Architecture | `docs/architecture/04-frontend-architecture.md` |
| Request Flows | `docs/architecture/05-request-flows.md` |
| Data Architecture | `docs/architecture/06-data-architecture.md` |
| Architecture Decisions (8 ADRs) | `docs/architecture/07-architecture-decisions.md` |
| Deployment Architecture | `docs/architecture/08-deployment-architecture.md` |
| Platform Architecture Assessment | `docs/architecture/platform-architecture-assessment.md` |
| Core Editing/Rendering Architecture | `docs/architecture/core-editing-rendering-architecture.md` |
| Import/Export Architecture | `docs/architecture/p4-import-export-architecture.md` |

### Missing Docs

| Doc | Status |
|-----|--------|
| `font-subtitle-rendering-stack.md` | **Not found** — no unified cross-stack pipeline doc |
| `backend-architecture-review-triage.md` | **Not found** |
| `same-image-vs-multi-image-worker-deployment.md` | **Not found** |

### Tech Stack Registration

| Technology | Registered in Docs? |
|-----------|-------------------|
| OTS | Yes |
| Remotion | Yes (extensively) |
| MLT | Yes |
| BMF | Spike only |
| Libass | Yes |
| fontTools | **No** |
| HarfBuzz | **No** |
| FreeType | **No** |
| Pango | **No** |
| Skia | **No** |

### Docs Needing React Correction

~55 occurrences across ~20 docs still reference Vue/Pinia/composables. Key files:
- `docs/architecture/01-system-architecture.md` (lines 13, 121)
- `docs/architecture/07-architecture-decisions.md` (ADR-009, lines 144-155)
- `docs/architecture/08-deployment-architecture.md` (line 12)
- `docs/code-derived-system-overview.md` (6 occurrences)
- `docs/frontend-ui-review-report.md` (50+ occurrences)
- `docs/zh/platform-guide/` (5 files)
- `docs/review/` (4 files)

### Production-Ready Misstatements

**None found.** All claims are accurate or self-correcting.

---

## 16. Decision Inputs Needed From Human

| # | Decision | Context |
|---|----------|---------|
| 1 | **RenderOrchestratorService decomposition** | 29 deps, 682 lines. Accept staged decomposition? Which sub-services first? |
| 2 | **quota-billing vs entitlement boundary** | quota-billing-module has no DB deps, entitlement-module is far more mature. Deprecate or merge? |
| 3 | **jOOQ codegen adoption** | 2,310 inline string-based calls. Codegen plugin declared but not applied. Adopt in near term? |
| 4 | **Render Provider SPI formalization** | 9 active providers, 5 dead-code providers. Formalize SPI contract before adding new providers? |
| 5 | **Font system roadmap** | Extensive interfaces with noop impls. 13 docs ahead of implementation. Enter near-term roadmap? |
| 6 | **Frontend React architecture stability** | Only 1 test file, 3 routes, skeleton editor. Is the React architecture considered stable for investment? |
| 7 | **Worker deployment strategy** | Same Docker image with role flag vs separate images per worker type? |
| 8 | **OpenFeature vs Unleash** | AGENTS.md says OpenFeature, code uses Unleash. Which is canonical? |

---

## 17. Recommended Next Actions

### A. Can Be Immediately Assigned to Kilo (Low Risk)

| Task ID | Description | Reason |
|---------|-------------|--------|
| `FRONTEND-DEAD-CODE-CLEANUP` | Delete `frontend/src/main.ts` and `frontend/src/vite-env.d.ts` Vue remnants | Dead code, no runtime impact |
| `FRONTEND-DOCS-REACT-CONSISTENCY-FIX` | Update ~55 Vue/Pinia references across ~20 docs to React/Zustand | Documentation accuracy |
| `RENDER-PROVIDER-STATUS-MATRIX-FIX` | Update render provider docs to reflect stub/skeleton status accurately | Prevents misrepresentation |
| `FONT-SUBTITLE-STACK-DOCS-CONSISTENCY-FIX` | Register fontTools/HarfBuzz/FreeType/Pango/Skia in docs; create unified pipeline doc | Documentation completeness |
| `ARCHITECTURE-TRIAGE-DOCS` | Create `backend-architecture-review-triage.md` from this scan | Decision support |
| `SOCIAL-PUBLISH-PACKAGE-INFO` | Add `package-info.java` to social-publish-module | Modulith compliance |

### B. Require ADR First (Need Tech Lead Acceptance)

| Task ID | Description | Reason |
|---------|-------------|--------|
| `RENDER-ORCHESTRATOR-DECOMPOSITION-ADR` | Decompose RenderOrchestratorService (29 deps) into 3-4 focused services | Needs decomposition strategy |
| `QUOTA-ENTITLEMENT-BOUNDARY-ADR` | Deprecate quota-billing-module or merge into entitlement-module | Module lifecycle decision |
| `JOOQ-CODEGEN-ADOPTION-ADR` | Adopt jOOQ codegen for type-safe queries | Build pipeline change |
| `TENANT-GUARD-UNIFICATION-ADR` | Unify TenantGuard + TenantIsolationService | Dual-implementation cleanup |
| `WORKER-ROLE-DEPLOYMENT-ADR` | Same-image vs multi-image worker deployment | Infrastructure strategy |
| `OPENFEATURE-UNLEASH-CANONICAL-ADR` | Resolve OpenFeature vs Unleash discrepancy | Feature flag strategy |

### C. NOT Recommended Now

| Action | Reason |
|--------|--------|
| Direct microservice extraction | Modular monolith is working; premature extraction adds complexity |
| Internal REST/gRPC between modules | Spring Modulith + events is sufficient; adds network overhead |
| One-shot RenderOrchestratorService rewrite | High risk; staged decomposition is safer |
| Direct BMF/MLT/Skia runtime integration | No production use case validated yet |
| Direct CUDA/GPU worker | No GPU infrastructure provisioned |
| Playwright/Cypress E2E adoption | Frontend is too thin; shell script E2E is sufficient for now |

---

## 18. Suggested Priority Ranking

| Priority | Item | Reason | First Safe Step |
|----------|------|--------|-----------------|
| **P0** | NotificationController tenant bypass + X-User-Id trust | Cross-tenant data access vulnerability | Derive tenant from TenantContext, userId from auth principal |
| **P0** | Duplicate GlobalExceptionHandler chains | Undefined execution order | Merge into single handler |
| **P0** | MDC fields never populated | Logs show empty tenantId/projectId | Extend PlatformTraceCorrelationFilter |
| **P0** | Logback missing jobId/workflowId/eventId/errorCode | Violates observability requirements | Add to logback pattern |
| **P0** | Frontend dead Vue code (`main.ts`, `vite-env.d.ts`) | May confuse build tooling | Delete files |
| **P1** | BillingUsageDataLoader thread-safety | Tenant context leak across threads | Use TaskDecorator or dedicated executor |
| **P1** | StorageKeyPolicy path traversal | Encoded traversal bypass | Add URL-decode + Path.normalize() |
| **P1** | RenderJobRepository extraction | 52 inline jOOQ refs across 12+ files | Create repository, migrate RenderJobService first |
| **P1** | Hardcoded entitlement policies | 5 classes with tier→value switch | Consolidate into DB-backed tier_policy table |
| **P1** | Docs React consistency | ~55 stale Vue references | Bulk find-and-replace |
| **P2** | RenderOrchestratorService decomposition | 29 deps → 3-4 focused services | ADR → extract JobSubmissionService first |
| **P2** | quota-billing deprecation | Overlaps with entitlement-module | ADR → merge or deprecate |
| **P2** | jOOQ codegen adoption | 2,310 string-based calls | ADR → start with render_job table |
| **P2** | In-memory persistence replacement | 7 services lose data on restart | Replace with H2 embedded for dev profile |
| **P2** | config-module tests | Zero test coverage | Add property binding tests |
| **P2** | Frontend test expansion | 1 test file | Add component + hook tests |
| **P3** | Font system implementation | Skeleton interfaces, heavy docs | ADR → decide if enters roadmap |
| **P3** | Worker deployment strategy | Same-image vs multi-image | ADR → document decision |
| **P3** | ThreadLocal → ScopedValue migration | Java 25 virtual thread readiness | Long-term optimization |

---

## 19. Appendix: Command Outputs

### Task 1: Git State
```
path: /home/bluepulse/Documents/code-lab/media-platform/platform
branch: main
commit: b9b9e19 chore: complete Vue removal - React-first frontend
dirty files: 0
tags at HEAD: none
```

### Task 2: Module Count
```
32 modules in settings.gradle.kts (31 included + platform-app)
34 build.gradle.kts files total
```

### Task 7: jOOQ Inline Hotspots
```
Top files by inline jOOQ:
  OutboxEventService.java: 28 calls (outbox_events)
  RenderOrchestratorService.java: 16 calls (render_job)
  RenderJobService.java: 11 calls (render_job)
  AuditService.java: 10 calls (audit_records)
  SpringNotificationEventPublisher.java: 9 calls
  DeliveryJobService.java: 8 calls
```

### Task 14: Test File Counts
```
Total Java test files: 410
Zero-test modules: config-module (0), frontend (1)
Highest coverage: policy-governance-module (51%), platform-app (42%), federation-query-module (40%)
```

---

## 20. P0 Security & Observability Fixes (2026-06-11)

### Fixes Applied

| # | Issue | File | Fix | Test |
|---|-------|------|-----|------|
| 1 | NotificationController ignores tenantId | `NotificationController.java` | Added `TenantGuard.assertSameTenant(tenantId)` to all 4 tenant-scoped endpoints | — |
| 2 | NotificationController trusts X-User-Id header | `NotificationController.java` | Replaced all 14 `@RequestHeader("X-User-Id")` with `resolveAuthenticatedUserId(request)` reading from `jwt.subject` request attribute | — |
| 3 | Duplicate GlobalExceptionHandler chains | `GlobalSentryExceptionHandler.java` deleted; `GlobalExceptionHandler.java` updated | Merged Sentry capture + IllegalStateException handler into `GlobalExceptionHandler`; deleted `GlobalSentryExceptionHandler`; added `@Order(0)` | `GlobalExceptionHandlerSentryTest` — 6/6 pass |
| 4 | MDC tenantId/projectId never populated by filter | `PlatformRequestContextFilter.java` | Removed untrusted `X-Tenant-Id` header read; tenantId now set exclusively by `JwtAuthFilter` from JWT; individual `MDC.remove()` instead of `MDC.clear()` | — |
| 5 | Logback missing jobId/workflowId/eventId/errorCode | `logback-spring.xml` | Added `%X{jobId}`, `%X{workflowId}`, `%X{eventId}`, `%X{errorCode}` to JSON pattern | — |
| 6 | Frontend dead Vue entry files | `frontend/src/main.ts` deleted; `frontend/src/vite-env.d.ts` cleaned | Removed Vue entry point and `*.vue` module declaration | Frontend tests 3/3 pass |

### Security Behavior After Fix

- **Tenant isolation:** All `/tenants/{tenantId}/notifications/*` endpoints now call `TenantGuard.assertSameTenant(tenantId)` which throws `PlatformException(403)` if path tenantId doesn't match `TenantContext`
- **User identity source:** All `/me/*` endpoints resolve userId from `request.getAttribute("jwt.subject")` set by `JwtAuthFilter` after JWT validation
- **Header spoofing prevention:** `X-User-Id` header is no longer read by any endpoint; user identity comes exclusively from authenticated JWT claims

### Error Handling After Fix

- **Single handler chain:** `GlobalExceptionHandler` with `@Order(0)` is now the sole `@RestControllerAdvice`
- **Sentry capture:** Merged into `GlobalExceptionHandler.captureSentry()` — called for `IllegalArgumentException`, `IllegalStateException`, and `Exception`
- **ProblemDetail:** All handlers return structured ProblemDetail with `errorCode`, `traceId`, `timestamp`

### Observability After Fix

- **MDC fields populated:** `traceId`, `requestId` (PlatformRequestContextFilter), `tenantId`, `principal` (JwtAuthFilter), `projectId` (X-Project-Id header)
- **Logback fields:** `traceId`, `requestId`, `tenantId`, `projectId`, `jobId`, `workflowId`, `eventId`, `errorCode` — missing fields default to empty string
- **MDC cleanup:** Each filter removes its own keys instead of `MDC.clear()`, preventing cross-filter key loss

### Remaining Vue Remnants (Resolved)

~~Three utility files still import from `'vue'`~~ — **Fixed in FRONTEND-REACT-UTILITY-CLEANUP:**
- `frontend/src/utils/i18n.ts` — replaced `ref<Locale>` with plain module-level variable
- `frontend/src/utils/sentry.ts` — replaced `ref`/`readonly` with plain variables, return `boolean`/`Readonly<SentryConfig>` directly
- `frontend/src/utils/openreplay.ts` — replaced `ref`/`readonly` with plain variables
- `frontend/src/utils/timelinePatchHighlight.ts` — replaced dead `@/stores/timeline` import with local `TimelineStoreLike` interface

**Frontend typecheck status after cleanup:** 3 errors remaining, all from missing npm dependencies:
- `graphql-request` — not declared in package.json, used by `api/graphqlClient.ts`
- `oidc-client-ts` — not declared in package.json, used by `auth/oidcClient.ts`
- Both are actively used by `api/index.ts` (not dead code)

---

## 21. P1 Security Hardening (2026-06-11)

### Fixes Applied

| # | Issue | File | Fix | Test |
|---|-------|------|-----|------|
| 1 | StorageKeyPolicy path traversal (substring-based `contains("..")`) | `StorageKeyPolicy.java` | Single-pass percent-decode, backslash rejection, absolute path rejection, Windows drive rejection, segment-level validation, null byte rejection | `StorageKeyPolicyTest` — 38/38 pass |
| 2 | SafeDownloadUrlValidator SSRF kill-switch (`static volatile skipDnsResolution`) | `SafeDownloadUrlValidator.java` | Replaced global kill-switch with injectable `DnsResolver` interface; added Carrier-Grade NAT and benchmarking range checks; fail-closed on DNS failure | `SafeDownloadUrlValidatorTest` — 22/22 pass |
| 3 | BillingUsageDataLoader TenantContext async propagation on ForkJoinPool | `BillingUsageDataLoader.java` | Removed TenantContext manipulation entirely — `UsageMeteringService.getUsageByTenant()` already takes tenantId as explicit parameter | `BillingUsageDataLoaderTest` — 6/6 pass |

### Security Regression Scan

| Pattern | Production Matches | Notes |
|---------|-------------------|-------|
| `skipDnsResolution` / `setSkipDnsResolution` | **0** | ✅ Removed from production code |
| `X-User-Id` header | **1** | `GraphQLContextFactory.java:36` — separate finding, not in scope |
| `contains("..")` in path validation | **3** | `BasicFontSecurityScanner`, `DeliveryPathRenderer`, `ToolRegistry` — same substring pattern as old StorageKeyPolicy; candidates for future hardening |
| `ForkJoinPool.commonPool` | **0** (1 in javadoc) | ✅ No production usage |

### Remaining Security Risks (from fact report)

| Priority | Item | Status |
|----------|------|--------|
| P2 | `GraphQLContextFactory` reads `X-User-Id` header | ✅ Fixed — now uses `SecurityContextHolder` |
| P2 | `BasicFontSecurityScanner`, `DeliveryPathRenderer`, `ToolRegistry` use substring `contains("..")` | ✅ Fixed — segment-level validation with encoded traversal rejection |
| P2 | SSRF TOCTOU (DNS rebinding) | Documented — mitigated by egress proxy recommendation |
| P2 | Tenant ID in public URL paths | Open |
| P3 | Sandbox worker ingress scope | Open |

---

## 22. P2 Security Cleanup (2026-06-12)

### Fixes Applied

| # | Issue | File | Fix | Test |
|---|-------|------|-----|------|
| 1 | GraphQLContextFactory trusts X-User-Id header | `GraphQLContextFactory.java` | Replaced `firstHeader(headers, "X-User-Id")` with `SecurityContextHolder.getContext().getAuthentication().getName()`; roles from auth authorities instead of X-User-Roles header | `GraphQLContextFactoryTest` + `GraphQLContextFactorySecurityTest` |
| 2 | BasicFontSecurityScanner substring `contains("..")` | `BasicFontSecurityScanner.java` | Added `isPathTraversal()` method: rejects `/`, `\`, null bytes, `..`, percent-encoded traversal (`%2e%2e`, `%2f`, `%5c`, double-encoded) | `FontSecurityScannerTest` |
| 3 | DeliveryPathRenderer substring `contains("..")` | `DeliveryPathRenderer.java` | Added `assertValidRenderedPath()`: rejects null bytes, backslashes, absolute paths, `..` segments, percent-encoded traversal, dot segments | `DeliveryPathRendererTest` — 8 tests |
| 4 | ToolRegistry substring `contains("..")` | `ToolRegistry.java` | Replaced with segment-level validation: rejects null bytes, backslashes, percent-encoded traversal, `..` segments | `ToolRegistryTest` — 14 tests |

### Security Regression After P2

| Pattern | Production Matches | Notes |
|---------|-------------------|-------|
| `X-User-Id` in production code | **0** | ✅ All removed |
| `contains("..")` in path validation | **3** | Acceptable: `StorageKeyPolicy.assertValidId()` (multi-check ID validation), ZIP entry validation (zip slip protection) |
| `skipDnsResolution` | **0** | ✅ Previously removed |

---

## 23. RenderJobRepository Extraction (2026-06-12)

### What Changed

| File | Change |
|------|--------|
| `render-module/.../infrastructure/RenderJobRepository.java` | **New** — 16 methods centralizing all render_job jOOQ DSL |
| `render-module/.../app/RenderJobService.java` | **Migrated** — removed all inline jOOQ, now delegates to RenderJobRepository |
| `render-module/.../infrastructure/RenderJobRepositoryTest.java` | **New** — 14 tests covering CRUD, tenant isolation, project lookup |
| `render-module/.../app/RenderJobServiceTest.java` | **Updated** — constructor now uses RenderJobRepository |

### Repository API

| Method | Purpose | Tenant-safe? |
|--------|---------|-------------|
| `create(...)` | Insert new render job | Yes — requires explicit tenantId |
| `findById(jobId)` | Find by ID | No — returns any tenant's job (caller must check) |
| `findByIdAndProjectAndTenant(...)` | Find with project+tenant filter | Yes |
| `listByTenant(tenantId)` | List all jobs for tenant | Yes |
| `listByProjectAndTenant(...)` | List jobs for project within tenant | Yes |
| `listAll()` | List all jobs (admin only) | No — no tenant filter |
| `updateStatus(...)` | Update job status | No — by jobId |
| `updateStatusAndClearError(...)` | Status + clear error (retry) | No — by jobId |
| `updateStatusWithError(...)` | Status + set error (failure) | No — by jobId |
| `updateArtifactUri(...)` | Set artifact URI | No — by jobId |
| `updatePipelinePlan(...)` | Set pipeline plan JSON | No — by jobId |
| `updatePipelineExecution(...)` | Set pipeline execution JSON | No — by jobId |
| `updateAiScript(...)` | Set AI script | No — by jobId |
| `existsByIdAndTenant(...)` | Existence check with tenant | Yes |
| `findTenantIdById(jobId)` | Get tenant for job | No — returns tenantId |
| `findProjectTenantId(projectId)` | Get tenant for project | No — convenience for job creation |

### Remaining render_job Inline jOOQ (Phase 2.2)

| File | Inline Refs | Notes |
|------|-------------|-------|
| `RenderOrchestratorService.java` | 17 | Phase 2.2 — not migrated in this round |
| `PipelinePlanPersistenceService.java` | 4 | Phase 2.2 |
| `StaleRenderJobCompensationService.java` | 2 | Phase 2.2 |
| `RenderCacheCleanupService.java` | 1 | Phase 2.2 |
| `RenderCacheTenantGuard.java` | 1 | Phase 2.2 |
| `BaseJobTimelineLoader.java` | 1 | Phase 2.2 |
| **RenderJobService.java** | **0** | ✅ Fully migrated |
