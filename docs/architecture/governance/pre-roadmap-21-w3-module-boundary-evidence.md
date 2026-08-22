# PRE-ROADMAP-21 W3 — CRITICAL MODULE BOUNDARY HARDENING — IMPLEMENTATION EVIDENCE

## What was corrected (bounded, critical)

### 1. timeline.internal package → timeline.diff.merge (semantic merge surface)

The `com.example.platform.timeline.internal` package contained 16 stable
semantic types (merge requests/results, review decisions, semantic diff
results) consumed by 30+ cross-module sites (platform-app controllers,
render-module services). The `internal` package name is an explicit
non-contract signal (CROSS_MODULE_INTERNAL_TYPE_ACCESS_IS_NOT_A_STABLE_
ARCHITECTURE_CONTRACT_V1), yet these types ARE the stable Timeline semantic
surface (frozen authority map: Timeline owns semantic diff and semantic merge).

Correction: package renamed to `com.example.platform.timeline.diff.merge`
(16 files moved, 32 import sites + 3 fully-qualified references updated).
Cross-module consumption of the merge/diff semantic surface is now an
INTENTIONAL DOMAIN SURFACE, not internal leakage.

### 2. Static Modulith snapshot retired (91 files)

`platform-app/docs/architecture/maps/generated/modulith/` contained 91 static
generated files with NO active generating task — a stale snapshot masquerading
as verification evidence (GOVERNANCE_DEFECT). Removed (CLEAN FORWARD).
MODULITH_ACTIVE_VERIFICATION = NONE; static snapshots are NOT authoritative;
PRE-#21 boundary invariants are enforced by active architecture guards.

## Classified remaining debt (burn-down, NOT blocking PRE-#21)

### D1. Dual timeline conflict models (3 duplicated type names)

| Type | timeline.diff (diff-engine model) | timeline.diff.merge (merge model) |
|---|---|---|
| EntityKind | TRACK/CLIP/AUDIO_MIX | PROJECT/ASSET/CLIP/TRACK/... (17) |
| TimelineConflict | TimelineConflictId/severity/path | conflictId/EntityRef/SemanticChange |
| TimelineConflictType | TRACK_ORDER/CLIP_TIMING/... | SAME_ENTITY_MODIFIED/CLIP_RANGE/... |

Two distinct semantic models share type names. Classification: DUAL_AUTHORITY
(historical evolution of diff vs merge engines). Burn-down: unify into ONE
merge/diff conflict semantic model (requires architecture decision — deferred
to Roadmap #21 boundary work; NOT silently merged in PRE-#21).

### D2. platform-app controllers → render.infrastructure (20 cross-module sites)

AssetController/AssetWorkbenchController/MarketplaceController →
render.infrastructure.asset.*; McpMediaToolsController →
render.infrastructure.{ColorProbeMetadata, MediaProbeResult,
FfprobeMediaProbeExecutor, gpac/shaka/bento4 packaging}; RemoteWorkerController
→ render.infrastructure.remote.*; ProjectDashboardController →
render.infrastructure.asset.*.

Classification: IMPLEMENTATION_INTERNAL_LEAKAGE (historical). These consume
infrastructure implementations directly where application contracts should
exist. Burn-down: expose application contracts (asset listing/search,
media probe, packaging) per use case; NOT blocking PRE-#21 (no architectural
authority violation — render module internals, not cross-domain authority).

### D3. Other module-local infrastructure controller deps (8)

entitlement/notification/secrets controllers → their own module infrastructure
(EntitlementGrantRepository, Mock/NovuNotificationProvider, VaultKv2SecretProvider).
Same-module or app→module infra access. Classification: NON_BLOCKING_HARDENING.

## Guards

Pre21ModuleBoundaryGuardTest:
- CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT = 0 for the timeline.internal
  package (any reference to `timeline.internal` fails the guard)
- static Modulith snapshot absence asserted (no generated/modulith dir)

## RED

RED-3: reintroduce `import com.example.platform.timeline.internal.*` →
guard FAIL (executed).
