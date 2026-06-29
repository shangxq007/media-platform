---
status: current
last_verified: 2026-06-29
scope: preview
truth_level: implemented
owner: platform
---

# Current System State

> **Last validated:** 2026-06-28 (Source of Truth Validation)

## 1. Validated Startup Profiles

### dev-postgres,preview

| Metric | Value | Status |
|--------|-------|--------|
| Startup Time | ~30 seconds | ✅ |
| Flyway Migration | All applied | ✅ |
| Recurring Errors | 0 | ✅ |
| Endpoints Accessible | All smoke-tested | ✅ |

**Active configuration:**
- Security: permit-all (no auth required)
- Database: PostgreSQL via Docker
- Disabled modules: AI, workflow, scheduler, worker, payment, commerce, sandbox, cloud-resource, social-publish
- Outbox dispatcher: disabled
- Scheduling: disabled

### prod,safe-mode

| Metric | Value | Status |
|--------|-------|--------|
| Startup Time | 19.108 seconds | ✅ |
| Security Bean Wiring | Fixed | ✅ |
| Flyway Migration | All applied | ✅ |
| Post-startup stability | Known issue | ⚠️ |

**Active configuration:**
- Security: JWT auth (OAuth2 disabled)
- Feature flags: safe-mode=true, all experimental features off
- Render providers: FFmpeg + Libass only
- Pipeline DAG: enabled (parallel-external disabled)
- Production checks: enabled

### test

| Metric | Value | Status |
|--------|-------|--------|
| Test Suite | `:platform-app:test` | ✅ PASS |
| Tasks Executed | 75 | ✅ |

---

## 2. Database Status

| Attribute | Value |
|-----------|-------|
| Engine | PostgreSQL 16 (Docker: `postgres:16-alpine`) |
| Flyway Migrations | 1 version (V1 - consolidated baseline, 2339 lines) |
| Tables | 133 |
| Schema Location | `classpath:db/migration` |
| Test Schema | PostgreSQL via Testcontainers |
| H2 Support | ❌ Not supported |
| jOOQ Codegen | Not configured — all manual DSL |

### Key Tables

| Domain | Tables |
|--------|--------|
| Core | `render_job`, `notification_event`, `config_item` |
| Identity | `tenant`, `project`, `user`, `api_key`, `workspace` |
| Commerce | `commerce_product`, `checkout_session`, `purchase_order`, `payment_attempt` |
| Billing | `billing_invoice`, `subscription_contract`, `quota_usage` |
| Entitlement | `entitlement_grant`, `entitlement_override`, `feature_definition` |
| Operations | `outbox_events`, `audit_records`, `schedules` |
| Content | `artifact`, `timeline_snapshot`, `timeline_revision`, `prompt_template` |

### Schema Health

- `outbox_events` schema aligned with dispatcher leasing (`locked_at`, `locked_by`, `max_retries`)
- Indexes: ~40 indexes defined (V6 migration)
- Constraints: FK relationships validated

---

## 3. Security Status

### P0 Issues (Closed)

| Issue | Resolution |
|-------|-----------|
| NotificationController tenant isolation | Fixed — tenantId scoping added |
| X-User-Id header trust | Fixed — removed untrusted header usage |
| Duplicate exception handlers | Fixed — consolidated handler chains |
| MDC/logback fields | Fixed — traceId + requestId populated |

### P1 Issues (Open)

| Issue | Impact | Owner |
|-------|--------|-------|
| `StorageKeyPolicy` path traversal (substring check) | Potential path traversal bypass | security |
| `SafeDownloadUrlValidator` SSRF kill-switch (global mutable) | SSRF risk if toggled | security |
| `BillingUsageDataLoader` thread-safety (TenantContext swap) | Data leak across tenants | billing |
| `TenantGuard` silent fallback | Silent pass-through on null context | platform |

### Security Configuration Matrix

| Profile | Auth | OAuth2 | JWT | API Key | Dev Endpoints |
|---------|------|--------|-----|---------|---------------|
| `dev-postgres` | None | Off | Off | Off | Enabled |
| `preview` | None | Off | Off | Off | Disabled |
| `safe-mode` | JWT | Off | On | Off | Disabled |
| `prod` | Full | On | On | Configurable | Disabled |

---

## 4. Known Issues

### Critical (Blocks Production)

| Issue | Impact | Workaround |
|-------|--------|------------|
| `ProductionSafetyValidator` `NoUniqueBeanDefinitionException` | `prod,safe-mode` fails after startup | Use `prod` profile with OAuth2 configured |

### Medium

| Issue | Impact | Status |
|-------|--------|--------|
| `PrometheusMeterRegistry` tag mismatch | Warning log on startup | Non-blocking |
| Some API endpoints return 404 | Parameter name reflection issue | Non-blocking |
| Actuator `/info` endpoint empty | Not configured | Non-blocking |

### Low

| Issue | Impact |
|-------|--------|
| Dead Vue entry point (`frontend/src/main.ts`) | Cleanup debt |
| 3 TypeScript typecheck errors | Missing npm deps (`graphql-request`, `oidc-client-ts`) |
| Pre-existing `RenderNatronEffectsIT` failure | Requires Natron binary not installed |

---

## 5. Disabled/Isolated Modules

| Module | Mechanism | Profile |
|--------|-----------|---------|
| Spring AI | Isolated in `ai-module`, not in `platform-app` runtime path | All |
| GraphQL | Auto-config excluded | All |
| Outbox Dispatcher | `app.outbox.dispatcher-enabled: false` | preview |
| Scheduling | `spring.task.scheduling.enabled: false` | preview |
| Vault | `app.secrets.vault.enabled: false` | All (default) |
| Temporal | `TEMPORAL_ENABLED=false` | dev/test |

---

## 6. Endpoint Smoke Test Results (dev-postgres,preview)

| Endpoint | Status | Result |
|----------|--------|--------|
| `/actuator/health` | 200 | `{"status":"UP"}` |
| `/actuator/health/readiness` | 200 | `{"status":"UP"}` |
| `/v3/api-docs` | 200 | OpenAPI spec |
| `/swagger-ui/index.html` | 200 | Swagger UI |
| `/api/v1/render/jobs` | 200 | Accessible |
| `/api/v1/artifact/catalog/overview` | 200 | Accessible |

---

## 7. Execution Environment and Provider Capability Status

### OpenCue Execution Environment

| Attribute | Value |
|-----------|-------|
| Status | Disabled-by-default stub |
| Conditional Bean | `@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true")` |
| Default | `opencue.enabled=false` |
| Real Client | Not implemented (stub only) |
| Docker Service | Not in docker-compose.dev.yml |
| Documentation | `docs/review/opencue-runtime-foundation.md` |

### Render Provider Registry

| Provider | Status | Priority | Auto Dispatch | Integration Smoke |
|----------|--------|----------|---------------|-------------------|
| FFmpeg | PRODUCTION | P0 | ✅ | ✅ R8 real render |
| Remotion | POC | P1 | ❌ | ✅ dry-run metadata |
| MLT | POC | P1 | ❌ | ✅ dry-run metadata |
| GPAC | POC | P1 | ❌ | ✅ dry-run metadata |
| Blender | SPIKE | P1 | ❌ | ✅ dry-run metadata |
| GStreamer | HOLD | P2 | ❌ | ✅ dry-run metadata |
| Natron | HOLD | P3 | ❌ | ✅ dry-run metadata |
| Libass | POC | P1 | ❌ | ✅ via FFmpeg |
| BMF | SPIKE | P2-P3 | ❌ | ❌ not implemented |
| OFX | DEPRECATED | P3 | ❌ | ❌ capability model only |

### Render Tool Capability Inventory

| Component | Location |
|-----------|----------|
| Runtime inventory | `RenderToolCapabilityInventory` (render-module) |
| Capability matrix | `docs/render/capability-matrix.md` |
| Inventory doc | `docs/review/render-tool-capability-inventory.md` |
| Provider eligibility | `ProviderEligibility` (render-module) |
| Provider status enum | `ProviderStatus` (9 values: PRODUCTION, POC, OPTIONAL, STUB, SKELETON, HOLD, SPIKE, DEPRECATED, MOCK) |
| Integration smoke | `ProviderIntegrationSmokeTest` (render-module) |

### Remotion Provider POC

| Attribute | Value |
|-----------|-------|
| Status | POC (dry-run smoke test) |
| Documentation | `docs/review/remotion-provider-poc-plan.md` |
| Smoke test | `RemotionProviderSmokeTest` (metadata + eligibility validation) |
| Real render | Blocked by missing Remotion CLI |
| Runtime requirements | Node.js 18+, Remotion CLI |

### OpenFX Capability Model

| Attribute | Value |
|-----------|-------|
| Status | Reserved (capability model only) |
| Current implementation | `OFXRenderProvider` (deprecated Java2D simulation) |
| Documentation | `docs/review/openfx-capability-model-reservation.md` |
| Future work | OFX host integration (Natron or custom host) |

## 8. Timeline DAG Foundation (N4+) + Provider Binding (N5+) + Execution Plan (N6+) + Local Runner (N7) + Plan-Based Switch (N7.1) + Idempotency (N7.2) + PLAN_BASED Default (N7.4) + Stabilization (N7.5)

| Component | Status | Location |
|-----------|--------|----------|
| Timeline Compile Contract v0 | ✅ Defined | `docs/review/timeline-compile-contract-v0.md` |
| NormalizedTimeline v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/` |
| TimelineNormalizationService | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| ArtifactDependencyGraph v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/` |
| ArtifactGraphCompiler | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| LogicalCapabilityGraph v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/` |
| CapabilityGraphCompiler | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| ProviderExecutionDocument | ✅ Reserved | `docs/review/provider-execution-document-model.md` |
| ProviderBindingPlan v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/binding/` |
| ProviderBindingCompiler | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| ProviderExecutionDocumentDraft v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/execution/` |
| ProviderExecutionDocumentDraftCompiler | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| RenderExecutionPlan v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/executionplan/` |
| RenderExecutionPlanCompiler | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| RenderPlanPolicyGuard v0 | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| Execution Policy v0 | ✅ Implemented | `render-module/.../domain/timeline/compile/executionplan/` |
| LocalExecutionPlanRunner v0 | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| RenderExecutionStepExecutor v0 | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| PlanBasedTimelineRevisionRenderService | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| Golden Fixture Tests (N4+) | ✅ 10 tests | `TimelineCompileGoldenFixtureTest` |
| Binding Compiler Tests | ✅ 12 tests | `ProviderBindingCompilerTest` |
| Execution Draft Tests | ✅ 8 tests | `ProviderExecutionDocumentDraftCompilerTest` |
| Binding Golden Fixture Tests | ✅ 8 tests | `ProviderBindingGoldenFixtureTest` |
| Execution Plan Compiler Tests | ✅ 12 tests | `RenderExecutionPlanCompilerTest` |
| Policy Guard Tests | ✅ 12 tests | `RenderPlanPolicyGuardTest` |
| Execution Plan Golden Fixture Tests | ✅ 7 tests | `RenderExecutionPlanGoldenFixtureTest` |
| Local Execution Plan Runner Tests | ✅ 8 tests | `LocalExecutionPlanRunnerTest` |
| Plan-Based Render Smoke Tests | ✅ 5 tests | `PlanBasedTimelineRevisionRenderSmokeTest` |
| TimelineRenderExecutionMode | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| TimelineRenderExecutionProperties | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| TimelineRevisionRenderFacade | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| Render Facade Tests | ✅ 6 tests | `TimelineRevisionRenderFacadeTest` |
| Execution Mode Tests | ✅ 6 tests | `TimelineRevisionRenderExecutionModeTest` |
| Plan-Based Switch Doc | ✅ Defined | `docs/review/timeline-render-plan-based-switch-v0.md` |
| RenderRequestFingerprint | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| RenderDeduplicationService | ✅ Implemented | `render-module/.../app/timeline/compile/` |
| Fingerprint Tests | ✅ 9 tests | `RenderRequestFingerprintGeneratorTest` |
| Dedup Service Tests | ✅ 8 tests | `RenderDeduplicationServiceTest` |
| Render Idempotency Doc | ✅ Defined | `docs/review/render-request-idempotency-v0.md` |
| Provider Binding Compile Doc | ✅ Defined | `docs/review/provider-binding-compile-v0.md` |
| Render Execution Plan Doc | ✅ Defined | `docs/review/render-execution-plan-v0.md` |
| Local Execution Plan Runner Doc | ✅ Defined | `docs/review/local-execution-plan-runner-v0.md` |
| OpenCue Submit | ❌ Future work | Not implemented |

**Compile Pipeline v0:**
```text
TimelineRevision
→ NormalizedTimeline (deterministic, provider-neutral)
→ ArtifactDependencyGraph (deterministic, acyclic, provider-neutral)
→ LogicalCapabilityGraph (deterministic, provider-neutral)
→ ProviderBindingPlan (deterministic, provider-bound, mode-aware)
→ List<ProviderExecutionDocumentDraft> (planning artifacts, generationReady=false)
→ RenderExecutionPlan (deterministic, step plan, executionReady=false)
→ RenderPlanPolicyResult (validation verdict, VALID_FOR_DRY_RUN)
→ LocalExecutionPlanRunner (FFmpeg baseline execution)
→ StorageRuntime output registration
→ ProductRuntime READY Product
→ ProductDependency lineage
```

**Key Constraints:**
- v0 supports single-clip, single-track timelines deterministically
- Multi-clip/multi-track produces valid graph but single-primary-input render only
- Clip effects fail closed (unsupported in v0)
- Provider binding is internal only — no provider names in public APIs
- FFmpeg remains the only PRODUCTION baseline provider
- Non-FFmpeg providers remain POC/SPIKE/HOLD/OPTIONAL and are not executable
- LocalExecutionPlanRunner is internal only
- PLAN_BASED is now the default execution mode (was LEGACY)
- LEGACY remains available via config: `media.render.timeline.execution-mode: LEGACY`
- OpenCue submit remains future work
- Execution document drafts are planning artifacts only — no command generation
- RenderExecutionPlan is internal only — all steps are placeholders (executionReady=false)
- RenderPlanPolicyGuard validates plans against 14 safety constraints
- FFmpeg remains the only PRODUCTION baseline provider
- Non-FFmpeg providers remain POC/SPIKE/HOLD/OPTIONAL
- LocalExecutionPlanRunner (actual execution) remains future work
- OpenCue submit remains future work

## 9. Caption Template Render Backend MVP (P2C.0–P2C.6)

Caption Template Render backend MVP loop is complete through safe result lookup. A caller can submit caption segments + style + source video Product and receive a READY FINAL_RENDER Product via FFmpeg/libass baseline.

| Component | Status |
|-----------|--------|
| MVP contract (P2C.0) | ✅ Implemented |
| Timeline adapter (P2C.1) | ✅ Implemented |
| API endpoint (P2C.2) | ✅ Implemented |
| Audit/correlation (P2C.2) | ✅ Implemented |
| Service E2E smoke (P2C.3) | ✅ Verified |
| API E2E smoke (P2C.4) | ✅ Verified |
| Safe result lookup (P2C.5) | ✅ Implemented |
| Readiness review + demo runbook (P2C.6) | ✅ Documented |
| Safe delivery resolver | ❌ Future work |
| Download/preview URL | ❌ Not exposed |
| Remotion execution | ❌ Not implemented |

**Endpoints:**
- `POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render`
- `GET /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}`

**Key constraints:**
- FFmpeg/libass is the only executable provider
- sourceProductId must be an existing RAW_MEDIA Product asset ID
- downloadAvailable=false, previewAvailable=false in v0.2
- No storage/provider internals exposed in public API
- Remotion remains non-executable
- Safe delivery resolver is future work

## 10. General Template System Design (P2T.0)

General Template System design has been accepted (ADR-022). Caption Template Render remains the first vertical MVP profile. Templates compile to TimelineSpec/TimelinePatch, can be orchestrated by WorkflowSteps, and may be extended by controlled plugin points. Providers remain hidden behind PLAN_BASED capability binding.

| Component | Status |
|-----------|--------|
| ADR-022 (General Template System) | ✅ Accepted |
| General Template System design doc | ✅ Documented |
| Template Workflow Integration design | ✅ Documented |
| Caption Template reframed as first profile | ✅ Updated |
| TemplateDefinition domain skeleton | ❌ Future (P2T.1) |
| Workflow semantic model | ❌ Future (P2W.0) |
| Plugin registry | ❌ Future (P2P.0) |

## References

- [Release Candidate Readiness Checklist](../../review/release-candidate-readiness-2026-06-17.md)
- [Platform Fact Gathering Report](../platform-fact-gathering-report.md)
- [Backend-first Stabilization Plan](../backend-first-stabilization-plan.md)
- [External Channel Extension Model](../blueprint/external-channel-extension-model.md) — Reserved extension points for external input/output channels
- [Storage Runtime Foundation](../../review/storage-runtime-foundation.md) — StorageReference locator semantics, R10B output key strategy
- [OpenCue Runtime Foundation](../../review/opencue-runtime-foundation.md) — OpenCue disabled-by-default foundation
- [Render Tool Capability Inventory](../../review/render-tool-capability-inventory.md) — Local tool detection
- [Remotion Provider POC Plan](../../review/remotion-provider-poc-plan.md) — Remotion implementation readiness
- [OpenFX Capability Model Reservation](../../review/openfx-capability-model-reservation.md) — OpenFX as capability model only
- [Multi-Provider POC Integration Report](../../review/multi-provider-poc-integration-report.md) — Provider integration pattern and smoke test results
- [Timeline Merge Preview Service](../../review/timeline-merge-preview-service-v0.md) — Side-effect-free merge preview with conflict analysis, status, summary, and issues
- [Artifact DAG Indefinite Deferral and Extension Boundary](../../review/artifact-dag-indefinite-deferral-extension-boundary-v0.md) — Artifact DAG is indefinitely deferred and retained only as an extension layer. Not on current roadmap. Not a dependency for rendering, Timeline Git, effects/transitions, Provider Binding, Render Execution Plan, OpenCue, Product API, or E2E validation. May be reconsidered only after measured production bottleneck.
- [Artifact DAG Deferred Optimization Boundary](../../review/artifact-dag-deferred-optimization-boundary-v0.md) — Superseded by P2A.2 indefinite deferral.
- [Constrained Render DAG and Timeline Operation Safety](../../review/constrained-render-dag-and-timeline-operation-safety-v0.md) — Render DAG and timeline graph structures are constrained media-domain DAGs, not arbitrary user-programmable graphs or global optimization systems. Provider binding uses deterministic eligibility and priority rather than global combinatorial optimization. Artifact DAG is indefinitely deferred and cannot drive default render execution.
- [Timeline Branch and Commit Semantics](../../review/timeline-branch-and-commit-semantics-v0.md) — P2V.5 pure domain vocabulary for branch, commit, pointer, checkout, rollback, and branch-switch plans. Side-effect-free, no persistence, no rendering.
- [Timeline Checkout and Rollback Application Service](../../review/timeline-checkout-rollback-application-service-v0.md) — P2V.6 pure application services for checkout, rollback planning, and branch switching. Side-effect-free, no persistence, no rendering, no Product creation.
- [Timeline Non-conflicting Merge Plan](../../review/timeline-non-conflicting-merge-plan-v0.md) — P2V.7 pure merge plan classifying operations into safe/conflict/unsupported/blocked/duplicate buckets. Side-effect-free, no patch application, no merged snapshot, no persistence.
- [Visual Capability Contract for Effects and Transitions](../../review/visual-capability-contract-effects-transitions-v0.md) — P2R.0 platform-owned Visual Capability Contract. Effects and transitions represented as bounded semantic capabilities with explicit status, provider consistency, fallback behavior, and safety rules. Does not implement rendering, does not expose raw provider commands, does not use Artifact DAG.
- [Basic Timeline Editing Model and Validation](../../review/basic-timeline-editing-model-validation-v0.md) — P2TLE.0 basic Agent/API-editable Timeline Editing Model and Validation layer. Supports structural editing concepts for tracks, clips, captions, watermarks, effects, transitions, and output profile validation. Side-effect-free: no persistence, no rendering, no Product creation, no StorageRuntime/ProductRuntime calls, no Artifact DAG, no OpenCue execution, and no public API controllers.
- [FFmpeg Baseline Effect Plan](../../review/ffmpeg-baseline-effect-plan-v0.md) — P2R.1 pure FFmpeg Baseline Effect Plan. Maps semantic timeline effect references to bounded internal FFmpeg baseline effect operations, with typed parameter validation and safety boundaries. Does not execute FFmpeg, does not generate public raw filtergraphs, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not use OpenCue, and does not use Artifact DAG.
- [FFmpeg Baseline Transition Plan](../../review/ffmpeg-baseline-transition-plan-v0.md) — P2R.2 pure FFmpeg Baseline Transition Plan. Maps semantic timeline transition references to bounded internal FFmpeg baseline transition operations, with typed parameter validation, clip relationship validation, deterministic ordering, conservative policy, and safety boundaries. Does not execute FFmpeg, does not generate public raw filtergraphs, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not use OpenCue, and does not use Artifact DAG.
- [FFmpeg/libass Basic Timeline Render Plan](../../review/ffmpeg-libass-basic-timeline-render-plan-v0.md) — P2R.3 pure FFmpeg/libass Basic Timeline Render Plan. Composes BasicTimeline validation, FFmpeg baseline effect planning, FFmpeg baseline transition planning, caption/watermark overlay semantics, and output profile validation into a deterministic internal render plan. Does not execute FFmpeg/libass, does not generate public raw filtergraphs, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not use OpenCue, and does not use Artifact DAG.

> P2X.0 introduced an internal API/Agent Scenario Runner and E2E Validation Harness. It validates the current core planning flow from timeline editing through visual capability validation, FFmpeg baseline effect planning, FFmpeg baseline transition planning, and FFmpeg/libass basic timeline render planning. It does not execute FFmpeg, does not call OpenCue, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not expose public APIs, and does not use Artifact DAG.

## 11. P2X.0 — Current Planning Chain and Roadmap Position (2026-06-29)

### Current Implemented Planning Chain

```text
BasicTimeline / TimelineSpec
  → BasicTimelineValidator (P2TLE.0)
  → VisualCapabilityContract (P2R.0)
  → FFmpegBaselineEffectPlanner (P2R.1)
  → FFmpegBaselineTransitionPlanner (P2R.2)
  → FFmpegLibassBasicRenderPlanner (P2R.3)
  → InternalScenarioRunner (P2X.0)
```

This chain is fully implemented, pure, side-effect-free, and validated by 10 internal scenarios.

### Current Implemented Compile Pipeline (Separate Path)

```text
TimelineRevision
  → NormalizedTimeline
  → ArtifactDependencyGraph
  → LogicalCapabilityGraph
  → ProviderBindingPlan
  → RenderExecutionPlan
  → LocalExecutionPlanRunner (FFmpeg baseline)
```

This compile pipeline is implemented and tested (N4-N7.5). It is a separate path from the new planning chain above. Both paths coexist.

### What Is Implemented

- Basic Timeline Editing and Validation (P2TLE.0)
- Visual Capability Contract for Effects and Transitions (P2R.0)
- FFmpeg Baseline Effect Plan (P2R.1)
- FFmpeg Baseline Transition Plan (P2R.2)
- FFmpeg/libass Basic Timeline Render Plan (P2R.3)
- Internal Scenario Runner and E2E Validation Harness (P2X.0)
- Timeline Git (revision chain, diff, merge engine, checkout, rollback)
- Compile pipeline (NormalizedTimeline → ArtifactGraph → CapabilityGraph → ProviderBinding → RenderExecutionPlan → LocalExecutionPlanRunner)
- FFmpeg as production baseline provider
- Caption Template Render backend MVP (P2C.0-P2C.6)

### What Is NOT Yet Implemented

- No FFmpeg execution through the new planning chain (planning only, no execution)
- No OpenCue integration
- No Provider Binding DSL runtime integration (P2B.0 design complete; P2B.1/P2B.2/P2B.3 future)
- No public Product-facing API for scenario runner
- No ProductRuntime integration through new planning chain
- No StorageRuntime integration through new planning chain
- No Artifact DAG requirement (indefinitely deferred, P2A.2)
- No Remotion execution (non-executable, POC only)
- No parallel segment/layer rendering
- No incremental render through new planning chain

### Provider Binding DSL Position

P2B.0 introduced the Provider Capability Binding DSL design. The DSL is declarative, YAML/JSON Schema first, fail-closed, and future-oriented. It describes provider capability support, status, consistency, fallback, parameter schema, productionAllowed, and autoDispatchAllowed. It does not allow shell commands, raw FFmpeg filtergraphs, scripts, Remotion component execution, OpenCue job definitions, user-submitted Render DAGs, storage internals, ProductRuntime internals, or Artifact DAG requirements. ANTLR and JavaCC remain future-only and are not adopted now. Runtime integration is deferred to P2B.1/P2B.2/P2B.3.

Design doc: `docs/architecture/provider-capability-binding-dsl.md`
Examples: `docs/examples/provider-bindings/`
Review: `docs/review/provider-capability-binding-dsl-design-v0.md`

### OpenCue Position

OpenCue is the next execution-environment validation target after planning/scenario validation. OpenCue is ExecutionEnvironment, not Provider. OpenCue does not decide visual capability semantics. OpenCue does not require Artifact DAG for initial smoke.

### Remotion Position

Remotion remains non-executable. Remotion is POC/dry-run only. Remotion is not production. Remotion is not auto-dispatch. Remotion execution is not available.

### FFmpeg/libass Position

FFmpeg/libass is the current production baseline for basic full explicit rendering and subtitle/caption overlay semantics.

### Artifact DAG Boundary

Artifact DAG remains indefinitely deferred (P2A.2). Artifact DAG is an extension layer only. Artifact DAG is not a roadmap dependency. Artifact DAG is not required by render planning, Timeline Git, OpenCue, Product API, effects, transitions, or E2E validation.

### Local Render Smoke (P2L.0)

P2L.0 introduced a local-only explicit render smoke harness. Controlled FFmpeg/ffprobe execution with fixed binary allowlist, no shell invocation, List<String> args, timeout enforcement, controlled output directory, execution gated by `-Dmedia.platform.localSmoke.enabled=true`. Does not implement public API, RenderExecutionPlan, OpenCue, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion, or Artifact DAG.

Review: `docs/review/local-explicit-render-smoke-harness-v0.md`

### BasicRenderPlan-to-Local-Runner Bridge (P2L.1)

P2L.1 introduced the first bridge from FFmpegLibassBasicRenderPlan to controlled local execution. It consumes a plan, extracts the output profile, maps a conservative supported subset (DECLARE_OUTPUT_PROFILE, ENCODE_OUTPUT, VERIFY_OUTPUT) to controlled FFmpeg/ffprobe execution through the P2L.0 boundary. Uses synthetic testsrc input. Unsupported steps are reported as warnings. Execution remains disabled by default. Does not implement full timeline rendering, RenderExecutionPlan, OpenCue, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion, or Artifact DAG.

Review: `docs/review/basic-render-plan-local-runner-bridge-v0.md`

### Local Caption Overlay Smoke (P2L.2)

P2L.2 expands the BasicRenderPlan-to-local-runner bridge to support caption overlay. Recognizes APPLY_CAPTION_OVERLAY steps, extracts safe typed caption fields, generates a platform-owned ASS subtitle file, and burns it in via FFmpeg/libass. Caption text is sanitized (braces/backslashes removed, length bounded). No raw filtergraph, no raw ASS style, no external subtitle path, no font path. Caption overlay counts included in result/report. Execution remains disabled by default. Does not implement full caption rendering, RenderExecutionPlan, OpenCue, ProductRuntime, StorageRuntime, ProviderBindingRegistry, Remotion, or Artifact DAG.

Review: `docs/review/local-caption-overlay-smoke-v0.md`

### Real Media Source Materialization (P2L.3)

P2L.3 expands the local runner from synthetic testsrc input to controlled real media fixture input. Generates a deterministic input-fixture.mp4 under the controlled output root using FFmpeg testsrc. Validates input and output with ffprobe. Preserves caption overlay support on real media input. Controlled local fixture only — rejects arbitrary user paths, remote URLs, storage references. Input source metadata included in result/report. Execution remains disabled by default. Does not implement arbitrary user media ingestion, StorageRuntime materialization, ProductRuntime, RenderExecutionPlan, OpenCue, ProviderBindingRegistry, Remotion, or Artifact DAG.

Review: `docs/review/real-media-source-materialization-v0.md`

### Local Docker OpenCue Shared-Path Smoke (P2O.0a)

P2O.0a introduced the local Docker OpenCue shared-path smoke preparation. It validates the OpenCue execution-environment model on a single local Docker host before PVE. Defines a local shared path under `build/opencue-shared/media-platform-smoke`, smoke levels for shared-path probe, FFmpeg probe, and local-runner-equivalent output, plus local Docker runbook/examples. Does not implement production OpenCue adapter, RenderExecutionPlan integration, cross-service-provider execution, object storage materialization, StorageRuntime, ProductRuntime, ProviderBindingRegistry, Remotion execution, public API, or Artifact DAG.

Review: `docs/review/local-docker-opencue-shared-path-smoke-v0.md`
Runbook: `docs/operations/opencue-local-docker-smoke-runbook.md`
Examples: `docs/examples/opencue/local-docker-p2o0a/`

### Local Docker Cuebot/RQD Runtime Smoke (P2O.0b)

P2O.0b introduced the local Docker Cuebot/RQD runtime smoke design and validation path. Moves beyond P2O.0a shared-path dry runs by defining how Cuebot submits smoke work to an RQD worker that reads and writes the shared path. Includes runtime smoke submission scripts (host dry-run mode), Docker Compose example with placeholder OpenCue images, runtime smoke runbook, and image status documentation. P2O.0b remains a local Docker execution-environment smoke only. Does not implement production OpenCue adapter, RenderExecutionPlan integration, cross-service-provider execution, object storage materialization, StorageRuntime, ProductRuntime, ProviderBindingRegistry, Remotion execution, public API, or Artifact DAG.

Review: `docs/review/local-docker-cuebot-rqd-runtime-smoke-v0.md`
Runbook: `docs/operations/opencue-local-docker-runtime-smoke-runbook.md`
Examples: `docs/examples/opencue/local-docker-p2o0b/`
