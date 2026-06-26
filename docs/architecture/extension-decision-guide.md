---
status: architecture-decision-guide
created: 2026-06-25
scope: platform-wide
truth_level: authoritative
owner: platform
---

# Extension Decision Guide

> **Target Audience:** Platform developers implementing new capabilities.
> **Purpose:** Decide the right extension mechanism for any new feature.
> **Linked ADR:** [ADR-005](adr/ADR-005-extension-decision-guide.md)

---

## 1. Extension / Plugin Inventory

| Component | Purpose | Loading | Authoritative? | Type |
|-----------|---------|---------|---------------|------|
| `ExtensionRegistryService` | Plugin lifecycle: register, execute, unload, rollback | `@PostConstruct` by Spring Beans | ✅ Single source of truth for extensions | Registry |
| `ProviderExtensionSPI` | Contract for provider plugins | Implemented by `@Component` beans | ✅ All providers must implement this | SPI |
| `PF4J PluginManager` | External JAR plugin loading | Configured, **dormant** | Future only | Plugin Framework |
| `TaskHandlerRegistry` | Capability-based handler lookup | Spring `@PostConstruct` | ✅ Handler lookup | Registry |
| `ExecutionBackendRegistry` | Execution backend by capability | Spring constructor injection | ✅ Backend lookup | Registry |
| `OutboxEventRouter` | Event type → Java class routing | `@PostConstruct` registration | ✅ Event routing | Registry |
| `SemanticMetadataProviderRegistry` | Legacy SPI registry | Spring injection | ⚠️ Being deprecated in favor of ExtensionRegistryService | Deprecated |

---

## 2. Current Plugin Loading Mechanism

### Built-in Providers (Current)

```
Spring @Component Bean
    ↓
@PostConstruct
    ↓
ExtensionRegistryService.registerProviderExtension(key, this, trustLevel, "system")
    ↓
ExtensionRegistryService.executeProvider(key, inputJson, tenantId, traceId)
```

**This is NOT external JAR loading.** Providers are compiled-in Spring beans that self-register via `@PostConstruct`. This is the built-in plugin pattern — suitable for all current AI providers (Whisper, Tesseract, Vision, Embedding).

### External Plugin Loading (Future)

```
PF4J PluginManager → scans plugins-dir/ → loads .jar files
    ↓
ExtensionRegistryService.registerProviderExtension(key, plugin, trustLevel, "pf4j")
    ↓
ExtensionRegistryService.executeProvider(key, ...)
```

**PF4J is configured but dormant.** It will become the external plugin loading entry point when:
- Marketplace supports plugin distribution
- Third-party providers are uploaded as JARs
- Tenant-specific plugins need dynamic loading/unloading

### No Multiple Plugin Systems

There is ONE plugin system: `ExtensionRegistryService` + `ProviderExtensionSPI`. The question is **how plugins arrive** — compiled-in (Spring beans) vs. external JARs (PF4J). The registry and execution path are identical for both.

---

## 3. Decision Rules

### When to Hardcode

| Criteria | Example |
|----------|---------|
| Stable, internal behavior with one implementation | Core domain invariant ("a clip must have a timelineStart") |
| Simple DTO mapping with no alternative path | `Asset → AssetWorkbenchDto` |
| Internal validation rules | `StorageKeyPolicy.assertValidPath()` |

**NEVER hardcode:** Provider, Backend, Extension, or anything that could have multiple implementations or be externally provided.

### When to Use Strategy / SPI

| Criteria | Example |
|----------|---------|
| Multiple implementations of the same capability | `ExecutionBackend` (LocalProcess, future BMF, OpenCue) |
| All implementations are built-in (not externally loaded) | `TaskHandler` (ASR, OCR, Vision, Embedding) |
| No lifecycle management needed | No enable/disable, no version tracking, no rollback |

### When to Use Registry

| Criteria | Example |
|----------|---------|
| Runtime lookup by type/capability | `TaskHandlerRegistry.resolve(capability)` |
| Registry has a single authoritative responsibility | `OutboxEventRouter` (event type → class) |
| Registry is NOT a second source of truth | ExtensionRegistryService is authoritative; TaskHandlerRegistry is a capability index |

**NEVER create** a provider-specific registry (OcrProviderRegistry, VisionProviderRegistry).

### When to Use Extension Runtime (`ExtensionRegistryService`)

| Criteria | Example |
|----------|---------|
| Plugin needs descriptor (providerId, version, capabilities) | `AiProviderDescriptor` |
| Plugin needs lifecycle (register, enable, disable, unload, rollback) | `onUnload()`, `onRollback()` |
| Plugin has trust level and resource limits | `ExtensionTrustLevel.FULLY_TRUSTED` |
| Plugin may be externally loaded in future | PF4J JAR loading |
| Plugin execution is called generically | `executeProvider(key, inputJson, tenantId, traceId)` |

**Use EXTENSION RUNTIME for:** AI Providers, Media Providers, future Storage Providers, future Workflow Providers.

### When to Add a New Runtime

**Extremely rare.** Must satisfy ALL of:
1. Outbox Runtime cannot handle it (not event delivery)
2. Coordination Runtime cannot handle it (not fan-out/fan-in/retry/lease)
3. Execution Runtime cannot handle it (not process execution)
4. Extension Runtime cannot handle it (not plugin registration/execution)

**AND** must be approved by ADR.

---

## 4. Runtime / Registry Convergence

### Single Source of Truth

| Concern | Authoritative Component |
|---------|------------------------|
| Extensions / Plugins | `ExtensionRegistryService` |
| Task Handling | `TaskHandlerRegistry` (capability index) |
| Execution Location | `ExecutionBackendRegistry` |
| Event Routing | `OutboxEventRouter` |

### Forbidden Patterns

- `NewProviderRegistry`, `OcrProviderRegistry`, `VisionProviderRegistry`
- `PluginRuntime2`, `MediaProviderRuntime`
- Direct `TaskHandler → Provider` (skip ExtensionRuntime)

---

## 5. Provider Governance (Linked from Sprint 043/045)

All Providers MUST:
1. Implement `ProviderExtensionSPI`
2. Register in `ExtensionRegistryService` via `@PostConstruct`
3. Declare `AiProviderDescriptor` (capabilities, models, languages)
4. Declare `ExtensionTrustLevel` and `ExtensionResourceLimits`
5. Execute through `ExtensionRegistryService.executeProvider()`
6. Write to `AssetSemanticMetadata` (source of truth)
7. Publish `AssetEnrichedEvent` (via outbox)
8. NOT create own runtime, registry, retry, scheduling, or state store

---

## 6. Decision Examples

### Example 1: New Whisper Alternative Provider

**Question:** How to add a Deepgram ASR provider?

**Decision:** `ProviderExtensionSPI` plugin. Register in `ExtensionRegistryService` with key `"deepgram"`. Same pattern as Whisper.

**NOT:** hardcode, new runtime, new registry.

### Example 2: BMF Execution Backend

**Question:** BMF is both a media processing framework AND an execution location.

**Decision:** BMF as execution location → implement `ExecutionBackend` (same level as `LocalProcessExecutionBackend`). BMF as media processing capability → implement `ProviderExtensionSPI` (same level as Whisper).

**NOT:** New runtime.

### Example 3: OpenCue Distributed Render Farm

**Question:** OpenCue provides render farm scheduling.

**Decision:** OpenCue as distributed execution backend → implement `ExecutionBackend` (same level as LocalProcess). OpenCue job submission → may be wrapped as `ProviderExtensionSPI` plugin.

**NOT:** Replace Coordination Runtime. Coordination handles fan-out/fan-in; OpenCue handles distributed execution.

### Example 4: S3 Storage Provider

**Question:** Multiple cloud storage backends.

**Decision:** Storage Provider plugin → `ProviderExtensionSPI` (or new StorageProviderSPI if ProviderExtensionSPI doesn't fit). Registers in `ExtensionRegistryService`. Future: PF4J external plugin from marketplace.

**NOT:** Hardcode S3 SDK into AssetRepository.

### Example 5: Timeline Merge Algorithm

**Question:** New merge strategy.

**Decision:** Internal Strategy pattern. Not a plugin.

**NOT:** ProviderExtensionSPI, ExtensionRegistryService, new runtime.

---

## 7. Related Documents

| Document | Relationship |
|----------|-------------|
| [ADR-005](adr/ADR-005-extension-decision-guide.md) | Formal ADR |
| [Runtime Governance](runtime-governance.md) | Runtime responsibilities |
| [Provider Governance](../review/provider-governance.md) | Provider rules (7 rules) |
| [Provider Extension Runtime](../review/provider-extension-runtime.md) | Extension runtime design |
