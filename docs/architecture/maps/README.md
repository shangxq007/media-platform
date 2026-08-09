# Architecture Maps

**Status:** ACTIVE
**Authority:** ARCH-MAP.0 / ADR-027

---

## Dual-Track Model

| Track | Tool | Role | Editing |
|-------|------|------|---------|
| AS-BUILT STRUCTURE | Spring Modulith generated C4 | What Spring modules exist, named interfaces, module dependencies, actual built structure | **GENERATED — DO NOT EDIT** |
| ARCHITECTURE INTENT | LikeC4 | Semantic / deployment / trust views, runtime authorities, durable orchestration, target boundaries | HUMAN CURATED |

Neither replaces: ADRs, architecture contracts, or source code.

### Spring Modulith Generated C4 (AS-BUILT)

- Source: `ApplicationModules.of(PlatformApplication.class)` — same authority `ModularityTest` verifies
- Output: `docs/architecture/maps/generated/modulith/` (PlantUML C4 component view + per-module diagrams)
- Regeneration: `./gradlew :platform-app:test --tests ModulithDocumentationGenerationTest`
- Deterministic: no DB, no Docker, no network, no app startup side effects
- **GENERATED — DO NOT EDIT.** Hand edits are overwritten on regeneration.
- It is an AS-BUILT STRUCTURAL VIEW — NOT a business-architecture authority.

### LikeC4 (ARCHITECTURE INTENT)

- Source: `docs/architecture/maps/likec4/media-platform.likec4`
- LikeC4 expresses what Spring cannot: actors, external systems, deployment units,
  trust boundaries, runtime authorities, durable orchestration, effect execution,
  control-plane relationships, major data authorities, target semantic boundaries.
- CURRENT vs TARGET vs DEFERRED is distinguished in element descriptions.
- LikeC4 is **NOT a manual copy of the Spring module graph** and does not mirror
  bean-level structure (services/repositories/validators/DTOs) unless the element
  is itself an architecture authority or boundary.

## Change Procedure

### Structural code change (module / NamedInterface / dependency)

```text
code
→ Modulith verification (ModularityTest)
→ regenerate generated C4
→ drift check
```

### Semantic architecture change (ADR / contract)

```text
ADR/contract
→ LikeC4 update
→ drift check
```

## PR Requirement

Any change touching: module, NamedInterface, deployment unit, runtime authority,
trust boundary, or major data authority MUST trigger an architecture-map relevance
check (regenerate as-built C4 + review LikeC4).

Ordinary changes (method, DTO field, repository implementation, minor service) do
NOT require LikeC4 updates.

## Drift Guard

`ARCHITECTURE_MAP_DRIFT_GUARD_V1` validates mechanical facts:
- all active Gradle modules have an explicit classification (represented /
  aggregate-covered / intentionally omitted / PMPR candidate / tooling-only)
- all deployment units represented in LikeC4 or explicitly omitted with rationale
- no stale CURRENT element (element claims to exist but does not)
- runtime authority assertions (Capability Registry metadata-only; PluginRuntime
  executes; Temporal orchestrates; sandbox-worker isolation boundary)
- Workflow target includes Workflow → PluginRuntime and never Workflow → ProviderExtensionSPI
- LikeC4 syntax validation + generated C4 determinism

## Authority Level

LikeC4 maps are **Level 4: Visual/Derived** — NOT the canonical source of truth.

Canonical sources:
1. AGENTS.md
2. current-system-state.md
3. production-safety.md
4. blueprint
5. ADRs
6. source code

LikeC4 diagrams must not invent components not present in docs/code.
