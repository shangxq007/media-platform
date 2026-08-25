# Roadmap #22 Pre-Phase 15 — CIP2 Architecture Drift Gate Repair V1

MODE=BOUNDED_ARCHITECTURE_GUARD_REPAIR

ARCHITECTURE_AUTHORITY=CHATGPT
ENGINEERING_CONTROL_PLANE=HERMES

BASE_SHA=5b80f0d5c209d99330c0ef32eab2899812cd38a9
BASE_TREE=e85d6f87b38ff909cae0ae30cb5a5075d3ae3979

## Blocker

BLOCKER=CIP2G6/CIP2DG12 authoritative architecture drift guard fails on BASE_SHA
CLASSIFICATION=PREEXISTING_STALE_GUARD
PHASE_15_REGRESSION=NO
ARCHITECTURE_REOPEN=NO

The failing guard was stale because it equated any `platform.color.` reference under `render-module/src/main` with Timeline color metadata leakage.

## Historical Evidence

Roadmap #20 FCV already recorded CIP2G6/CIP2DG12 color-image leakage as PRE_EXISTING_BASELINE_FAILURE and NOT_ROADMAP_20_REGRESSION.

## Superseding Architecture Reality

Render -> ColorImage is a frozen valid dependency.

`render-module` legitimately consumes `color-image-module` under the frozen Render -> ColorImage direction for RenderPlan canonical representation, including color/image semantic WHAT such as ColorDescription, ColorPrimaries, RasterSampleDescription, and Chromaticity.

Therefore:

RENDER_IMPORTS_COLORIMAGE != TIMELINE_COLOR_METADATA_LEAKAGE

## Corrected Guard Semantics

OLD:

`render-module` must contain zero `platform.color.*` references.

NEW:

Timeline production code must contain zero color-image semantic authority imports and zero production dependency on `:color-image-module`, while direct MediaStreamSourceBinding semantic guards remain unchanged.

Layered evidence after repair:

- CIP2G5 / CIP2DG11: direct source-binding shape remains unchanged and contains no ColorDescription / RasterSampleDescription / StaticHdrMetadata authority.
- CIP2G6: timeline-module production Java has zero `com.example.platform.colorimage.*` imports.
- CIP2DG12: timeline-module has zero production `api` or `implementation` dependency on `:color-image-module`.

## Non-Exception Statement

THIS_IS_NOT_AN_ARCHITECTURE_EXCEPTION=YES
THIS_IS_NOT_A_GATE_BYPASS=YES
ARCHITECTURE_REOPEN=NO

No valid Render -> ColorImage code was removed or redesigned. The repair aligns the guard with the already-frozen architecture invariant instead of suppressing the gate.
