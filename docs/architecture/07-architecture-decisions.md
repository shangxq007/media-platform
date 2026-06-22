# Architecture Decision Records

> **Module:** All
> **Last Updated:** 2026-05-18

## ADR-001: Modular Monolith with Spring Modulith

**Status:** Accepted
**Date:** 2026-05-08

**Context:** Need to organize 30+ business modules with clear boundaries while maintaining a single deployable unit.

**Decision:** Use Spring Modulith 2.0.4 with `@ApplicationModule` annotations. `shared-kernel` is `Type.OPEN`; all others are `CLOSED`.

**Consequences:**
- ✅ Enforced module boundaries via `ModularityTest`
- ✅ Easy to split into microservices later
- ✅ Single deployment artifact
- ⚠️ All modules share the same JVM (no process isolation)

---

## ADR-002: Temporal + LiteFlow for Orchestration

**Status:** Accepted
**Date:** 2026-05-08

**Context:** Need both durable workflow orchestration and lightweight local routing.

**Decision:** Temporal for long-running, durable workflows (render jobs, billing cycles). LiteFlow for local, stateless rule chains (provider selection, routing).

**Consequences:**
- ✅ Temporal provides durability, retries, visibility
- ✅ LiteFlow is lightweight for simple routing
- ⚠️ Temporal Server required for production
- ⚠️ Two orchestration systems to maintain

---

## ADR-003: Event-Driven Cross-Module Communication

**Status:** Accepted
**Date:** 2026-05-08

**Context:** Modules need to communicate without creating tight coupling.

**Decision:** Use Spring `ApplicationEventPublisher` for in-process events. `outbox-event-module` for transactional outbox pattern. Cross-module events defined in `shared-kernel`.

**Consequences:**
- ✅ Loose coupling between modules
- ✅ Transactional consistency via Outbox
- ✅ Audit and notification are event-driven
- ⚠️ Event ordering not guaranteed across modules

---

## ADR-004: OpenFeature for Feature Flags

**Status:** Accepted
**Date:** 2026-05-14

**Context:** Need a feature flag system that supports targeting rules, percentage rollout, and remote providers.

**Decision:** Use OpenFeature Java SDK with `LocalFeatureFlagProvider` as default. `OpenFeatureFlagEvaluator` reserved for remote provider (LaunchDarkly, flagd, Unleash).

**Consequences:**
- ✅ Standard API via OpenFeature
- ✅ Local provider supports targeting + rollout
- ✅ Easy to swap to remote provider
- ⚠️ Local provider is in-memory only (not persisted)
- 🔴 Remote provider not configured (production blocker)

---

## ADR-005: JavaCV as Primary Render Provider

**Status:** Accepted
**Date:** 2026-05-12

**Context:** Need a Java-native video processing solution without shelling out to FFmpeg CLI.

**Decision:** JavaCV (Java bindings for FFmpeg) as the primary render provider. No `Runtime.exec()` or `ProcessBuilder` in business code.

**Consequences:**
- ✅ No shell command injection risk
- ✅ JNI-based, better performance than CLI
- ✅ Supports clipping, transcoding, subtitles, watermarks
- ⚠️ Apache Commons Exec still present in `extension-module` for non-video tools
- ⚠️ GPU acceleration not yet implemented

---

## ADR-006: Spring AI for AI Model Abstraction

**Status:** Accepted
**Date:** 2026-05-08

**Context:** Need a unified AI client abstraction that supports multiple model providers.

**Decision:** Use Spring AI BOM 2.0.0-M3 with `spring-ai-starter-model-openai`. `ai-module` exposes `ChatProvider` SPI for model routing.

**Consequences:**
- ✅ Unified API for multiple AI providers
- ✅ Easy to swap models
- ⚠️ Spring AI 2.0.0-M3 is a Milestone release (not GA)
- 🔴 Currently uses `StubChatProvider` (production blocker)

---

## ADR-007: PF4J for Plugin System

**Status:** Accepted
**Date:** 2026-05-12

**Context:** Need a JVM plugin system for dynamic extension loading.

**Decision:** Use PF4J 3.15.0 for plugin management. `extension-module` handles plugin lifecycle. `sandbox-runtime-module` for script execution.

**Consequences:**
- ✅ Classloader isolation for plugins
- ✅ Runtime plugin loading/unloading
- ✅ Version management
- ⚠️ Plugin governance still maturing

---

## ADR-008: jOOQ for Type-Safe SQL

**Status:** Accepted
**Date:** 2026-05-08

**Context:** Need type-safe SQL with complex query support.

**Decision:** Use jOOQ 3.19.18 with Gradle code generation. Flyway for schema migration.

**Consequences:**
- ✅ Type-safe SQL
- ✅ Complex query support
- ⚠️ Build-time code generation required
- ⚠️ Learning curve for developers

---

## ADR-009: Vue 3 + Vite for Frontend

**Status:** Superseded (2026-06-22 — frontend migrated to React 19)
**Date:** 2026-05-08

> **Superseded by:** [04-frontend-architecture.md](04-frontend-architecture.md), [react-architecture.md](../frontend/react-architecture.md)
> **Reason:** Frontend migrated from Vue 3/Pinia/Apollo to React 19/Zustand/TanStack Query/TanStack Router.
> **See:** `frontend/package.json` for actual dependencies.

**Context:** Need a modern frontend framework for the video editor.

**Decision:** Vue 3 with Vite build tool. Pinia for state management. Apollo Client for GraphQL.

**Consequences:**
- ✅ Fast build with Vite
- ✅ Reactive UI with Vue 3
- ✅ Component-based architecture
- ✅ 639 tests with Vitest

---

## ADR-010: Sentry + OpenReplay for Monitoring

**Status:** Accepted
**Date:** 2026-05-14

**Context:** Need error monitoring and user session replay.

**Decision:** Sentry for error tracking and session replay. OpenReplay for user feedback and session recording.

**Consequences:**
- ✅ Comprehensive error tracking
- ✅ Session replay for debugging
- ✅ User feedback collection
- ✅ Automatic data sanitization
- ⚠️ Requires external service configuration

---

## ADR-011: VFX Engine Strategy (FFmpeg/MLT First, Natron Worker for OFX)

**Status:** Accepted
**Date:** 2026-05-20

**Context:** The product needs high-quality effects (`effectKey`, effect packs, tier entitlements). The in-process `OFXRenderProvider` simulates OFX via FFmpeg/Java2D; real OpenFX plugins require a native host. Options evaluated include Natron, TuttleOFX/Sam, embedding Olive/Oak, and commercial tools (Resolve/Fusion, Nuke, Flame).

**Decision:**

1. Keep the **control plane** on `effectKey` + `providerMappings` + DB catalog (no dependency on a single vendor UI).
2. Use **FFmpeg filtergraph and MLT** as Tier-1 execution for most effects (current path).
3. Plan **Natron (`NatronRenderer`) in isolated render workers** as Tier-2 for real OFX plugin rendering—not inside `platform-app` JVM.
4. Treat **TuttleOFX/Sam** as research/reference for a future native host, not a production default (stale maintenance).
5. Treat **Resolve/Nuke/Flame** as optional **export/handoff** workflows for TEAM+, not embedded runtimes.
6. Use **Olive/Oak** only as UX/architecture reference for the web editor, not as a backend render engine.

**Consequences:**
- ✅ Clear upgrade path from simulated OFX to real plugins without breaking API contracts
- ✅ Aligns with worker-based rendering and GPL/license isolation for Natron
- ⚠️ Additional worker image, GPU ops, and OFX plugin governance required
- ⚠️ TuttleOFX/Sam POC may still be useful for Host design but should not block Natron Worker delivery

---

## ADR-012: Server-Side NLE Seven-Layer Architecture

**Status:** Accepted  
**Date:** 2026-05-20

**Context:** The product needs an extensible server-side NLE that composes FFmpeg, multi-track timeline (MLT/executor), template engines (Remotion), 3D (Blender), OFX (Natron), 2D overlays (Skia/libass), and streaming packaging (GPAC/Shaka/Bento4). MCP clients must submit work via stable job APIs, not raw CLI.

**Decision:**

1. Adopt a **seven-layer logical model** (L1 FFmpeg → L2 timeline → L3 template → L4 3D → L5 Natron → L6 Skia/libass → L7 packaging) as **target architecture**, implemented as a **DAG of Workers** rather than strict serial stages.
2. Keep **control plane** on `effectKey`, timeline snapshots, profiles, and `RenderProvider` / `PackagingProvider` SPI.
3. Expose **MCP** via `/api/v1/mcp/*` mirrors with API Key auth; no direct per-tool MCP unless added later as convenience tools.
4. **GPL/native tools** (MLT, Natron, Blender) run only in **isolated Worker** images.
5. **Remotion** (self-hosted) and **Shotstack** (cloud) are both **L3 template** options; neither replaces L2 MLT for general NLE.

**Consequences:**

- ✅ Clear onboarding doc for engineers and agents ([10-server-nle-layered-architecture.md](../media-rendering/10-server-nle-layered-architecture.md))
- ✅ Aligns with existing Natron/Bento4/Shotstack work
- ⚠️ Multi-track and Skia/libass/Remotion/Blender remain delivery backlog
- ⚠️ RenderPlan orchestration must evolve to schedule layer branches explicitly

**References:** [11-layer-tools-reference.md](../media-rendering/11-layer-tools-reference.md), [03-provider-roadmap.md](../media-rendering/03-provider-roadmap.md)
