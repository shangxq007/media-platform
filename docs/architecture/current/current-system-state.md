---
status: current
last_verified: 2026-06-28
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

## 8. Timeline DAG Foundation (N4+) + Provider Binding (N5+) + Execution Plan (N6+) + Local Runner (N7) + Plan-Based Switch (N7.1) + Idempotency (N7.2) + PLAN_BASED Default (N7.4)

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
