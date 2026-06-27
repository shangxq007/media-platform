# Remotion Provider POC Plan

> **Status:** implementation-ready checklist — no code implementation in this task
> **Created:** 2026-06-28
> **Updated:** 2026-06-28
> **Scope:** planning document for future Remotion provider implementation

## Implementation Readiness Checklist

### Phase 1: POC (Basic CLI)

- [ ] Create `RemotionRenderProvider` implementing `RenderProvider` interface
- [ ] Add `@ConditionalOnProperty(name = "render.providers.remotion.enabled")` gating
- [ ] Implement `render()` method calling Remotion CLI via `ProcessBuilder`
- [ ] Add `RemotionInputProps` record for composition parameters
- [ ] Implement `TimelineSpec` → `RemotionInputProps` mapping
- [ ] Add `ProviderMetadata` with `ProviderStatus.POC`, `autoDispatch=false`
- [ ] Register in `RenderProviderRegistry` via auto-configuration
- [ ] Add unit tests for props validation and mapping
- [ ] Add smoke test: generate static composition, verify output mp4

### Phase 2: SPIKE (Caption Burn-in)

- [ ] Extend `RemotionInputProps` with caption style and word-level timing
- [ ] Implement font manifest integration with platform font pipeline
- [ ] Add transparent overlay output (RGBA PNG/WebM)
- [ ] Add caption fidelity tests (word-level timing, style presets)
- [ ] Add CJK font support tests

### Phase 3: Production (Auto-dispatch)

- [ ] Upgrade `ProviderStatus` to `PRODUCTION`
- [ ] Enable `autoDispatch=true`
- [ ] Add capability-based routing via `ProviderEligibility`
- [ ] Add health check via `RenderProviderHealthCheck`
- [ ] Add OpenCue worker compatibility

## Why Remotion Matters

| Capability | Value |
|-----------|-------|
| React/Frontend consistency | Same React components for preview and render |
| Caption fidelity | Word-level timing, animated captions, style presets |
| Font manifest/subset alignment | Font pipeline integration with platform font management |
| Preview/render contract | Consistent output between browser preview and server render |
| Transparent overlay | RGBA PNG/WebM output for compositing |

## Proposed Provider Status

| Phase | Status | Description |
|-------|--------|-------------|
| POC | Manual dispatch only | Basic Remotion render via CLI |
| SPIKE | Experimental | Caption burn-in with word-level timing |
| Production | Auto-dispatch enabled | Full integration with provider registry |

Initial status: **POC/SPIKE** — not production-ready.

## Required Contracts

### RemotionInputProps

```java
public record RemotionInputProps(
    String compositionId,
    Map<String, Object> props,
    int width,
    int height,
    int fps,
    double durationSeconds,
    String outputFormat
) {}
```

### TimelineRevision/RenderJob Mapping

- TimelineRevision → TimelineSpec → Remotion composition props
- RenderJob → execution context → Remotion CLI invocation
- Output → StorageRuntime → Product

### Font Manifest

- Platform font management → Remotion font loading
- Font subsetting for web delivery
- CJK font support

### Caption Style Contract

- Caption style presets → Remotion component props
- Word-level timing → per-word React components
- Animation presets → Framer Motion integration

## Runtime and Sandbox Requirements

| Requirement | Description |
|-------------|-------------|
| Node.js runtime | Node 18+ required for Remotion CLI |
| Remotion CLI | `npx remotion render` for server-side rendering |
| No arbitrary user JS | Only platform-defined compositions (no user-provided code) |
| Bounded render duration | Configurable timeout (default: 300s) |
| File access restriction | Only temp dirs and StorageRuntime paths |
| Network restriction | No outbound network from render process |
| SSRF prevention | No user-controlled URLs in composition props |
| Path traversal prevention | Validate all file paths before passing to Remotion |
| Dependency isolation | Remotion deps in isolated node_modules |

## Safety Constraints

| Constraint | Description |
|-----------|-------------|
| Node sandbox | Remotion runs in sandboxed Node.js process |
| No arbitrary user JS | Only platform-defined compositions |
| Bounded render duration | Timeout enforcement |
| No public path/signed URL exposure | Architecture boundary |

## Relationship to StorageRuntime

- **Input:** Materialized through StorageRuntime (S3 or LOCAL)
- **Output:** Registered through StorageRuntime → ProductRuntime
- No direct filesystem access from Remotion

## Relationship to OpenCue

- Remotion provider can run under Local or OpenCue ExecutionEnvironment
- OpenCue would dispatch to Remotion-capable workers
- No direct dependency — provider is environment-agnostic

## Tests Needed

| Test | Description |
|------|-------------|
| Static props validation | Validate RemotionInputProps structure |
| Font contract validation | Validate font manifest integration |
| Local smoke | Basic Remotion render via CLI |
| API safety | No sensitive data in responses |
| Output validation | Verify output Product properties |

## Non-Goals

- No production auto-dispatch yet
- No public provider exposure
- No frontend integration in this task
- No MLT/Blender/Natron/OpenFX implementation

## Implementation Estimate

| Phase | Effort | Dependencies |
|-------|--------|-------------|
| POC (basic CLI) | 2-3 days | None |
| SPIKE (caption burn-in) | 3-5 days | POC, font pipeline |
| Production (auto-dispatch) | 5-10 days | SPIKE, provider registry integration |

## Related Documents

- `docs/render/capability-matrix.md` — Provider capability matrix
- `docs/architecture/public-capability-architecture.md` — Capability architecture
- `docs/review/render-tool-capability-inventory.md` — Tool inventory
