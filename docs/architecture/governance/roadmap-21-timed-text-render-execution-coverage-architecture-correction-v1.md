# ROADMAP #21 — TIMED_TEXT RENDER-EXECUTION-COVERAGE TEMPORAL BRIDGE — ARCHITECTURE CORRECTION V1

STATUS=FROZEN_ARCHITECTURE_CORRECTION
BASE_SHA=cf8b36f456f15473e4729dbea91b9dbb2d461ac7
SCOPE=TIMED_TEXT_TO_RENDER_EXECUTION_COVERAGE_TEMPORAL_BRIDGE_ONLY

## 1. PROBLEM (B1)

Correction 3 left TIMED_TEXT.executionCoverage=null because TextElement timing
uses FontRational rather than MediaTime. But TextElement carries exact authored
timing (start, duration); an out-of-extent TIMED_TEXT can never be extent-pruned
under C12/C13 Option A. The architecture question is ONLY: how does #20
losslessly and authoritatively project authored FontRational timing into
RenderExecutionCoverage's timeline/render coordinate domain?

## 2. REPOSITORY-REALITY INSPECTION (MANDATORY, EVIDENCE)

FONT_RATIONAL_UNIT_SEMANTICS=exact rational (BigInteger numerator/denominator);
canonical typography numerics (ROADMAP_19 C5/C27); TextElement.start/duration
are authored timeline-time quantities — repository fixture evidence:
TestPlans.textElement() = FontRational.whole(0) start + FontRational.whole(5)
duration on a timeline whose clip range is [0,10) — same coordinate domain as
MediaTime-based clip.timelineRange. FontRational.whole(n) = n/1 (integer
seconds-scale timeline time).

MEDIA_TIME_UNIT_SEMANTICS=exact rational seconds (record(long ticks,
long timeScale)); timeline/render-request coordinate domain; the canonical
timeline time representation used by RenderExecutionCoverage / RenderExtent.

LOSSLESS_CONVERSION_EXISTS=YES (same exact-rational seconds semantic; direct
numerator→ticks / denominator→timeScale mapping is value-preserving)

LOSSLESS_CONVERSION_RANGE=BOUNDED — MediaTime stores long ticks/long timeScale;
FontRational stores BigInteger numerator/denominator (unbounded) →
BOUNDED-representability check REQUIRED (T2)

OVERFLOW_BEHAVIOR=FAIL_CLOSED (typed materialization/planning failure —
PLANNING_UNSUPPORTED diagnostic; no clamp, no approximation)

ROUNDING_REQUIRED=NO
ROUNDING_ALLOWED=NO

## 3. SELECTED_OPTION

SELECTED_OPTION=T2 — checked exact #20-owned projection with bounded
representability guard.

CONVERSION_AUTHORITY=#20 renderplan domain (DefaultRenderMaterializer /
ExactTextTimelineTimeProjection helper owned by #20)

CONVERSION_LOCATION=render-module renderplan package — consumed inside #20;
#21 NEVER sees FontRational

EXACTNESS_RULE=coverageStart = exact(start as rational); coverageEnd =
exact(start + duration as rational); both converted via
ticks=long(numerator), timeScale=long(denominator) after normalization;
BigInteger→long overflow → FAIL_CLOSED (no rounding)

OVERFLOW_RULE=value not exactly representable in MediaTime (long ticks/
timeScale) → typed deterministic failure (materializer emits
PLANNING_UNSUPPORTED diagnostic; no TIMED_TEXT node with invented/approximate
coverage)

TIMED_TEXT_COVERAGE_RULE=every TIMED_TEXT node receives non-null
RenderExecutionCoverage [exact(start), exact(start+duration)] in timeline
coordinates (once representable); unrepresentable → fail closed, never null-
with-silent-pass

TEXT_AUTHORITY_UNCHANGED=YES (TextElement remains Timeline/font-text authored
authority; FontRational timing untouched)
TIMELINE_AUTHORITY_UNCHANGED=YES (no Timeline authority redefinition)
RENDER_EXECUTION_COVERAGE_AUTHORITY_UNCHANGED=YES (#20 renderplan owns
coverage; derived projection)
ROADMAP_21_AUTHORITY_UNCHANGED=YES (pruning still compares coverage vs extent;
#21 never interprets FontRational)
ROADMAP_22_NOT_STARTED=YES

## 4. FORBIDDEN

- #21 consuming FontRational directly
- string rational values in RenderExecutionCoverage
- milliseconds/float/double conversion
- BigDecimal approximate conversion
- TIMED_TEXT remaining permanently null when the exact bridge is available
- changing RenderExecutionCoverage into a generic union
- redefining Timeline/font-text authority

ARCHITECTURE_ESCALATION=RESOLVED_FOR_TIMED_TEXT_BRIDGE (T2 within the
pre-authorized envelope; no authority conflict — FontRational is exact rational
timeline time, losslessly projectable with bounded-representability guard)
