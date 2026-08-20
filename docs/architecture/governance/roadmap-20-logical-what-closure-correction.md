# Roadmap #20 Logical WHAT Closure Correction

Append-forward governance record — Roadmap #20 logical RenderPlan self-containment
correction (ROADMAP20_LOGICAL_WHAT_CLOSURE_CORRECTION).

## Origin

- Original implementation candidate: `887f0c06a22648828ad0b03e340f0b268f9eca6f`
  (tree `2aa99beb9a9b00d77273b110326ed3746b22793d`)
- Original publication: `9538e73ebef87ed71cf4cf92ba9cd6a4c6238821`
  (tree `287451a90bbabf912d0d3b08800e47e192b6047a`)
- Independent final review verdict: **FAIL_CORRECTABLE**
  - ARCHITECTURE_PREMISE_FAILURE = NO
  - MATERIAL_BLOCKERS = 4 (F1, F2, F3, F4) + 1 mandatory major correction (F5)
  - ARCHITECTURE_ESCALATION_REQUIRED = NO

## Correction

- Correction implementation SHA: `8e0a11f488f0a5094f5ff5c63ef68ca9f78f064f`
- Correction implementation tree: `f87fd0ec383521bc9957623192952b704a015531`
- Correction parent: `9538e73ebef87ed71cf4cf92ba9cd6a4c6238821`

## Findings disposition

### F1 — Logical WHAT not self-contained → PASS
Introduced sealed `RenderMaterializationRequirement` with three typed immutable
variants:
- `EffectMaterializationRequirement` — authoritative category + typed
  `List<EffectParameter>` (no opaque hash-only semantics, no Map payload)
- `AudioProcessMaterializationRequirement` — typed gain/mute/balance
- `TimedTextMaterializationRequirement` — typed text/timing/frame/fallback/
  resolved-font-run projection

`RenderNode` carries `materializationRequirements`; canonical codec serializes
typed fields deterministically; all participate in the logical fingerprint.

### F2 — TimedText semantic closure → PASS
`TimedTextMaterializationRequirement` preserves text content, exact timing,
`TextFrame` layout, `FontFallbackPolicy`, and `ResolvedFontRuns` (consumed, never
recomputed — Roadmap #19 authority preserved). New typed graph path:
`TIMED_TEXT --CompositeInput--> COMPOSITE --CompositeInput--> OUTPUT`.
No provider-specific raster commands. No semantically relevant orphan
TIMED_TEXT node.

### F3 — Duplicate capability authority → PASS
Render-local `RenderCapabilityId` / `RenderCapabilityRequirement` retired
(deleted). Logical plan consumes the Roadmap #16 platform capability authority:
`CapabilityId` + `CapabilityRequirement` + `ContractVersionRange`
(`com.example.platform.extension.domain`). Render owns a bounded deterministic
mapping (`RenderCapabilityVocabulary`: `video.decode`, `video.effect.*`,
`audio.process`, `audio.mix`, `subtitle.rasterize`, `render.composite`,
`render.output`). No provider/plugin/worker/device/tier/price identity.
No competing capability namespace.

### F4 — Revision integrity boundary → PASS
Introduced immutable `HydratedTimelineRevision` binding revision identity +
content digest + authored semantic projection (clips, effects, definitions,
audio mix, text elements) as one coherent planning source. Primary planner API:

```
RenderPlanningInput(HydratedTimelineRevision, RenderRequest,
                    SourceResolutionInput, CapabilityContext)
```

No production path accepts revision reference + independently supplied
fragments. Pure planner does not query repositories; hydration/revalidation is
the application-layer trust boundary (documented; not faked in pure domain).

### F5 — TemporalMapping fail-closed → PASS
Removed the silent "unknown mapping → full source range" fallback.
Unsupported TemporalMapping now produces typed `PLANNING_UNSUPPORTED`
diagnostic (fail closed). Sealed hierarchy (ConstantRate + Freeze) makes the
branch unreachable today; source/architecture guard test protects the
expectation.

## Verification evidence

- render-module: 2812 tests, 0 failures, 0 errors (incl. 23 correction tests)
- extension-module: 314 / 0 / 0
- timeline-module: 771 / 0 / 0
- audio-module: 22 / 0 / 0
- full suite: 7503 tests / 0 failures / 0 errors / 43 skipped (at FCV on
  `8e0a11f4`)
- bootJar: PASS
- pfirr1RemediationCheck: PASS
- verifyC20RenderPlanBoundaryGuard: PASS
- Modulith (ModularityTest + ModulithDocumentationGenerationTest): PASS
- git diff --check: PASS

## Pre-existing baseline failures (NOT correction regressions)

- CIP2G6 / CIP2DG12 ("Timeline leakage"): FAIL on BOTH clean baseline
  `9538e73e` and the correction, same signature/path
  (`RenderOutputRequirement` imports `platform.colorimage.ColorDescription` /
  `RasterSampleDescription` — a legitimate typed color-image consumption under
  C14/C8). Classified PRE_EXISTING_BASELINE_FAILURE.
- CORRECTION_REGRESSION = NO.

## Scope audit

All production changes map to F1-F5 or strictly necessary compile adaptation.
No scope drift: no multi-clip composition, no Transition implementation, no
physical planner/optimizer, no provider execution, no GPU, no workflow/DSL/
GraphQL, no database migrations.

## Roadmap #22 forward-compatibility

From Logical RenderPlan alone (no authored re-read):
- effect category + parameters → recoverable from
  `EffectMaterializationRequirement` ✓
- audio gain/mute/balance → recoverable from
  `AudioProcessMaterializationRequirement` ✓
- TimedText raster semantics → recoverable from
  `TimedTextMaterializationRequirement` ✓
- source artifact/content pin → `RenderArtifactReference.SourceArtifact` ✓
- output requirements → `RenderOutputRequirement` ✓
- capability requirements → platform `CapabilityRequirement` ✓

PHYSICAL_PLANNER_CAN_CONSUME_LOGICAL_PLAN_WITHOUT_AUTHORED_REREAD = YES

## Final recommendation

READY_FOR_CHATGPT_INDEPENDENT_REVIEW
(do not merge main; do not push before independent review authorization)
