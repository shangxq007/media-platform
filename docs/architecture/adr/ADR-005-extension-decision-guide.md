---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-005: Extension Decision Guide

## Context

The platform has five extension mechanisms: hardcoding, Strategy/SPI, Registry, Extension Runtime, and Plugin. Without decision rules, developers choose inconsistent patterns for new capabilities.

## Decision

Formalize the following decision hierarchy:

1. **Hardcode** — for stable, single-implementation, internal behavior only
2. **Strategy/SPI** — for multiple built-in implementations of the same capability
3. **Registry** — for runtime lookup by type/capability with a single authoritative source
4. **Extension Runtime** (`ExtensionRegistryService` + `ProviderExtensionSPI`) — for plugins with lifecycle, descriptor, trust level, and future external loading
5. **Plugin (PF4J)** — for externally loaded JAR plugins (future)
6. **New Runtime** — requires ADR; must not overlap with existing runtimes

## Consequences

- All current providers (Whisper, Tesseract, Vision, Embedding) use Extension Runtime — consistent
- Future BMF, OpenCue providers will follow the same path
- No provider-specific registries, runtimes, or direct TaskHandler→Provider calls
- PF4J remains dormant until marketplace plugin distribution is needed

## Rules

See [Extension Decision Guide](../extension-decision-guide.md) for full rules and examples.

## Examples

- Adding Deepgram ASR → `ProviderExtensionSPI` plugin in `ExtensionRegistryService` (NOT new registry)
- BMF execution → `ExecutionBackend` (NOT new runtime)
- OpenCue scheduling → `ExecutionBackend` adapter (NOT replace Coordination Runtime)

## Migration Path

- `SemanticMetadataProviderRegistry` → deprecated in favor of `ExtensionRegistryService`
- All built-in providers → already registered via `@PostConstruct` in `ExtensionRegistryService`
- External plugins → PF4J when marketplace plugin distribution is ready
