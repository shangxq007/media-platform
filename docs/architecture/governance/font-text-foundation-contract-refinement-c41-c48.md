---
type: bounded-architecture-contract
milestone: ROADMAP_19
name: FONT_TEXT_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1
status: FINAL_FROZEN
date: 2026-08-16
base: db348cb1a153757f2c7234f963ed898a9769df40
revision: REFINEMENT_CORRECTION_V1 (C41-C48 appended; C1-C40 preserved)
---

# FONT_TEXT_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 — REFINEMENT (C41-C48)

C1-C40 remain authoritative as frozen in the original contract (M1 module;
FC3 digest; Raw→Validation→Sanitization→Conformance→ValidatedExecutionArtifact;
Unicode scalar ranges; BCP-47/ISO-15924; StyledText non-overlapping runs;
ResolvedFontInstance exact; Variable Font first-class; subset derived-only;
Timeline-owned TextElement T1; ShapedGlyphRun excluded from Timeline; nine
operations; provider-neutral engine contract; three determinism levels; 23-item
defer list). The following refinements resolve pre-implementation authority
ambiguities. No parallel V2 contract (PARALLEL_FONT_TEXT_CONTRACT_COUNT = 0).

## C41 CREDENTIAL EVIDENCE AUTHORITY
CREDENTIAL_EVIDENCE_AUTHORITY_IS_TRACKED_EVIDENCE_AND_ACTUAL_SCAN_RESULT_NOT_
TRANSIENT_UI_MASKING_V1. Authoritative credential PASS = actual scan result
(CREDENTIAL_SCAN_RESULT_COUNT = 0) + tracked evidence (CREDENTIAL_RESIDUE_FINAL
= 0). Hermes transient UI may render the literal field "***" (presentation-only;
HERMES_TRANSIENT_UI_MASKING = YES); this is never grounds for architecture
re-opening or repository correction. Secret values always protected;
residue counts always numeric. REAL_SECRET_VALUE_DISCLOSURE_COUNT = 0.

## C42 FONT TECHNICAL RESOLUTION VS RIGHTS AUTHORIZATION
FONT_TECHNICAL_RESOLUTION_AND_RIGHTS_AUTHORIZATION_ARE_DISTINCT_AUTHORITIES_V1.
Replaces the combined formula: TechnicalFontCandidate = Validated ∩ Coverage ∩
ShapingConformance ∩ RuntimeCapability. EffectiveFontUse = TechnicalFontCandidate
∩ EffectiveRightsAuthorization. FontResolver owns TECHNICAL resolution ONLY
(FONT_TECHNICAL_RESOLUTION_AUTHORITY_COUNT = 1). Rights authorization belongs
exclusively to future RIGHTS_AND_USAGE_POLICY_FOUNDATION_V1 (cross-media;
RIGHTS_AUTHORIZATION_AUTHORITY_COUNT = 1 future). FONT_RESOLUTION_DOES_NOT_OWN_
RIGHTS_AUTHORIZATION_V1. Font/Text never decides legal allow/deny.

## C43 PARAGRAPH STYLE SOLE LINE-HEIGHT AUTHORITY
PARAGRAPH_STYLE_IS_THE_SOLE_V1_LINE_HEIGHT_AUTHORITY_V1. TextStyle.lineHeight
REMOVED from V1 (LINE_HEIGHT_CANONICAL_AUTHORITY_COUNT = 1; authority =
ParagraphStyle.lineHeight). LINE_HEIGHT_VALUE_MODEL = exact tagged Rational
multiplier-ratio OR absolute exact length — bounded tagged union; zero CSS
strings ("1.2"/"normal"/"24px"); zero unpinned implicit host semantics. Future
inline leading override must be a distinct explicit feature, never a second
line-height field.

## C44 FONT SELECTION INTENT SOLE SELECTION AUTHORITY
FONT_SELECTION_INTENT_IS_THE_SOLE_FONT_FACE_AND_VARIATION_SELECTION_AUTHORITY_V1.
FontSelectionIntent = family/face preference + weightIntent + stretchIntent +
slantIntent + opticalSizingIntent + explicitVariationAxisOverrides.
FONT_WEIGHT_SELECTION_AUTHORITY_COUNT = FONT_STRETCH_SELECTION_AUTHORITY_COUNT =
FONT_SLANT_SELECTION_AUTHORITY_COUNT = FONT_VARIATION_OVERRIDE_AUTHORITY_COUNT =
1, all inside FontSelectionIntent. TextStyle = FontSelectionIntent + FontSize +
Tracking + TextDecoration + OpenTypeFeatureIntent (+ Fill only per C47);
TextStyle MUST NOT duplicate weight/stretch/slant/opsz/axes. Intent ≠ exact
resolution: FontSelectionIntent = authored request; ResolvedFontInstance =
exact historical result (content digest + face + exact coordinates); never
re-resolve historical revisions from mutable catalogs
(HISTORICAL_FONT_RE_RESOLUTION_COUNT_MUST_REMAIN_ZERO_V1).

## C45 PARAGRAPH BASE DIRECTION VS RANGE OVERRIDE
PARAGRAPH_BASE_DIRECTION_AND_RANGE_DIRECTION_OVERRIDE_ARE_DISTINCT_V1.
ParagraphStyle.baseDirection = AUTO | LTR | RTL (paragraph BiDi base).
TextSemanticRun.directionOverride = NONE | LTR | RTL (optional authored range
override). Precedence: explicit range override → paragraph baseDirection →
Unicode BiDi resolution. Never two generic "direction" fields. BiDi control
characters (LRI/RLI/FSI/PDI/LRE/RLE/PDF) preserved in canonical content —
directionOverride never silently deletes/rewrites them; security diagnostics
are NOT silent rewriting (TEXT_SECURITY_DIAGNOSTIC_IS_NOT_SILENT_TEXT_
REWRITING_V1 stays frozen).

## C46 VERSIONED TEXT LAYOUT ALGORITHM PROFILE
TEXT_LAYOUT_ALGORITHM_PROFILE_IS_VERSIONED_EXECUTION_INPUT_V1. Provider-neutral
TextLayoutAlgorithmProfile = UnicodeDataProfile + BidiProfile + LineBreakProfile
+ GraphemeProfile + TextLayoutContractVersion. Zero HarfBuzz/Skia/browser/OS
object identity as semantic authority. TEXT_LAYOUT_ALGORITHM_PROFILE_IS_NOT_
AUTHORED_TIMELINE_TEXT_STATE_V1: Timeline owns WHAT text means; #20 RenderPlan
owns WITH WHICH execution profile it is laid out. Therefore
TEXT_LAYOUT_ALGORITHM_PROFILE_IN_TIMELINE_HASH = NO;
TEXT_LAYOUT_ALGORITHM_PROFILE_RENDERPLAN_HANDOFF = YES (future #20 pin).
Level-2 conformance = same profile + same logical content + same exact resolved
instances → same runs/breaks/glyph ids/clusters/advances/positions across
conforming providers. Historical exact font results (digest/face/axes/fallback
identity) are frozen before atomic apply and never re-resolved by profile
changes (HISTORICAL_FONT_RE_RESOLUTION_COUNT = 0).

## C47 TEXT FILL VS SOURCE COLOR INTERPRETATION
TEXT_AUTHORED_FILL_COLOR_AND_SOURCE_COLOR_INTERPRETATION_ARE_DISTINCT_V1.
Repository audit: color-image-module owns ONLY source color interpretation
values (ColorDescription/Chromaticity/StaticHdrMetadata/SourceVisualDescription
…); NO authored paint color value exists.
TEXT_FILL_COLOR_CLASSIFICATION = NO_SUITABLE_COLOR_VALUE_FOUND.
TEXT_FILL_V1 = DEFERRED (TEXT_FILL_DEFERRED_REASON = NO_EXISTING_CANONICAL_
AUTHORED_PAINT_COLOR_VALUE). `fill` removed from TextStyle V1. ColorDescription/
Primaries/Transfer/Matrix/StaticHdr NEVER used as text paint
(SOURCE_COLOR_DESCRIPTION_USED_AS_TEXT_FILL = NO). DUPLICATE_COLOR_SEMANTIC_
MODEL_COUNT_MUST_REMAIN_ZERO_V1 (no TextRGBA/TextColorSpace/CssColor/StringColor
in #19). font-text → color-image dependency: NOT required in V1 (no fill
reference); may be revisited only when an authored paint foundation exists.
Roadmap #20 or future bounded foundation may establish authored paint semantics
without mutating #18 source color authority.

## C48 RAW FONT FAMILY STRING RETIREMENT
UNSHIPPED_RAW_FONT_FAMILY_STRING_AUTHORITIES_ARE_DELETED_OR_MIGRATED_NOT_WRAPPED_V1.
Verified baseline: RAW_FONT_FAMILY_STRING_AUTHORITY_VERIFIED_COUNT = 77
(38 files: provider/infra 20, domain 9, application 4, test 4, API/controller 1);
FONT_PATH_URL_AUTHORITY_COUNT = 21. All unshipped → zero compatibility burden.
Bounded implementation MUST end with: RAW_FONT_FAMILY_STRING_AUTHORITY_FINAL = 0,
FONT_PATH_CANONICAL_AUTHORITY_FINAL = 0, FONT_URL_CANONICAL_AUTHORITY_FINAL = 0,
HOST_FONT_LOOKUP_CANONICAL_AUTHORITY_FINAL = 0, LEGACY_FONT_SEMANTIC_ALIAS_COUNT
= 0. Migration policy: authored intent → typed FontSelectionIntent; provider
observation → explicit non-authoritative adapter observation only; unused →
delete; test fixtures → canonical typed semantics. Forbidden: LegacyFontFamily,
FontFamilyStringAdapter-for-compatibility, deprecated fontFamily field, dual
serialization, @JsonAlias("fontFamily"), endpoint aliases (external provider
payload fields kept strictly inside adapter observation models are NOT
compatibility code).

## Updated implementation gates (FTG23-FTG37)
FTG23 FontResolver makes no legal/Rights allow decision.
FTG24 No implicit allow when Rights authority absent (RIGHTS_EVALUATION =
NOT_EVALUATED; never ALLOWED by default).
FTG25 TextStyle lineHeight count = 0.
FTG26 ParagraphStyle lineHeight sole authority.
FTG27 Font weight/stretch/slant/axes live only in FontSelectionIntent.
FTG28 Paragraph baseDirection and range directionOverride distinct.
FTG29 TextLayoutAlgorithmProfile provider-neutral and versioned.
FTG30 TextLayoutAlgorithmProfile absent from Timeline hash.
FTG31 Text fill does not misuse ColorDescription/source colorimetry.
FTG32 Duplicate color model count = 0.
FTG33 RAW_FONT_FAMILY_STRING_AUTHORITY_FINAL = 0.
FTG34 FONT_PATH_CANONICAL_AUTHORITY_FINAL = 0.
FTG35 FONT_URL_CANONICAL_AUTHORITY_FINAL = 0.
FTG36 HOST_FONT_LOOKUP_CANONICAL_AUTHORITY_FINAL = 0.
FTG37 No compatibility wrapper around raw fontFamily.

## Operation boundary refinement
SET_FONT_SELECTION operates on FontSelectionIntent; SET_VARIABLE_FONT_AXIS
updates FontSelectionIntent.explicitVariationAxisOverrides; both derive a new
exact result before apply — never mutate ResolvedFontInstance directly.
SET_PARAGRAPH_STYLE owns lineHeight/baseDirection; no
SET_TEXT_STYLE_LINE_HEIGHT in V1. Preview exposes resolved exact fonts, fallback
choices, missing glyphs, unsupported scripts, validation/capability/axis/
color-font failures, and RightsRequirement (or RIGHTS_EVALUATION = NOT_EVALUATED
when no Rights authority exists — never ALLOWED by default).
