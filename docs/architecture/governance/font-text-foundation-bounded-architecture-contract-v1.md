---
type: bounded-architecture-contract
milestone: ROADMAP_19
name: FONT_TEXT_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1
status: FROZEN
date: 2026-08-16
base: db348cb1a153757f2c7234f963ed898a9769df40
authority: ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY_V1
---

# FONT_TEXT_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1

Repository reality (measured): zero canonical Font model; fontFamily raw String
authority = 77 occurrences (auto-captions/API/DTO consumers, unshipped);
zero Timeline text element; zero LanguageTag/Rights domain types; zero
WASM/Skia/CanvasKit infrastructure; MediaAsset.license = observation String.
Greenfield applies: NO_HISTORICAL_COMPATIBILITY_BURDEN_V1,
ONE_CANONICAL_MODEL_NO_COMPATIBILITY_TRACKS_V1,
OBSOLETE_UNSHIPPED_DESIGNS_ARE_DELETED_NOT_DEPRECATED_V1.

## C1 MODULE OWNERSHIP
FONT_RESOURCE_SEMANTIC_OWNER = font-text-module (resource subpackage).
TEXT_TYPOGRAPHY_SEMANTIC_OWNER = font-text-module (text subpackage).
PHYSICAL_MODULE_LAYOUT = M1 single pure module (two logical subpackages;
zero existing font/text module — two physical modules would be aesthetic only).
Dependencies: font-text-module → color-image-module (reuse #18 color values;
acyclic). Zero outward deps to media/timeline/render/provider/artifact/
platform-app. Artifact association lives in application/infrastructure.

## C2 FONT BYTE / DATA-PLANE BOUNDARY
FONT_BYTES_ARE_ARTIFACT_DATA_PLANE_NOT_TEXT_DOMAIN_STATE_V1. Canonical font
semantics never contain filesystem path / URL / registry key / host font name
/ fontconfig identity / CSS resolution / byte[].

## C3 EXACT FONT CONTENT IDENTITY
FONT_EXACT_CONTENT_PIN_MODEL = FC3 typed FontContentDigest (SHA-256, immutable,
pattern-aligned with ColorProfileContentDigest; zero Artifact dependency).
FONT_ARTIFACT_ASSOCIATION_MODEL = application/infrastructure layer maps
FontContentDigest ↔ ArtifactId (outside font-text-module). Storage reference
is NOT canonical (FONT_STORAGE_REFERENCE_IS_CANONICAL = NO).

## C4 FONT COLLECTION FACE IDENTITY
FONT_COLLECTION_MODEL = FONT_COLLECTION_FACE_IDENTITY_IS_EXPLICIT_V1.
ResolvedFontFace = FontContentIdentity + FaceIndex (TTC/OTC). Family/
subfamily display names are manifest metadata, never exact identity.
FONT_FACE_IDENTITY_MODEL = EXPLICIT.

## C5 FONT FACE MANIFEST
FontFaceManifest = deterministic facts parsed from exact validated content.
Three-way classification: canonical parsed fact (format, face index, axes,
named instances, STAT presence, glyph count, Unicode coverage, GSUB/GPOS/GDEF
presence, color-font technologies), derived diagnostic (script coverage,
shaping-conformance observations), execution capability observation (runtime
support — outside manifest canonical state). Localized family name = metadata
not identity.

## C6 FONT SECURITY LIFECYCLE
RawFontArtifact → FontSecurityValidation (structural) → Sanitization (safe
normalized execution artifact) → Conformance (bounded sampled execution:
load face, Latin shape, declared complex scripts, metrics, default instance,
axis min/max, named instance, color glyph, subset, raster smoke) →
ValidatedExecutionFontArtifact. Raw untrusted bytes NEVER enter canonical
production shaping directly (RAW_FONT_DIRECT_SHAPING_ALLOWED = NO).

## C7 VALIDATION / SANITIZATION / CONFORMANCE DISTINCT
FONT_STRUCTURAL_VALIDATION_SANITIZATION_AND_EXECUTION_CONFORMANCE_ARE_DISTINCT_V1.
Conformance is sampled, never exhaustive continuous axis-space claim.

## C8 HISTORICAL VALIDATED FONT PIN
HISTORICAL_FONT_EXECUTION_PIN_MODEL = dual pin: source FontContentDigest +
immutable ValidatedExecutionFontArtifact identity. Historical render NEVER
re-runs today's sanitizer (HISTORICAL_FONT_EXECUTION_NEVER_RE_SANITIZES_
MUTABLE_TOOLCHAIN_OUTPUT_V1; HISTORICAL_RE_SANITIZATION_ALLOWED = NO).

## C9 UNICODE TEXT CONTENT
TEXT_CONTENT_IS_UNICODE_SEMANTICS_NOT_PROVIDER_GLYPH_IDS_V1. Canonical Text
stores logical Unicode; zero HarfBuzz/FreeType/DirectWrite/CoreText/Skia
glyph ids as domain authority.

## C10 UNICODE NORMALIZATION POLICY
CANONICAL_UNICODE_NORMALIZATION_POLICY = AUTHORED_UNICODE_SEQUENCE_IS_PRESERVED
_UNLESS_EXPLICIT_TEXT_OPERATION_CHANGES_IT_V1. NFC/NFD/NFKC/NFKD never applied
silently; normalization only as explicit operation/analysis helper/index
projection. U+00E9 vs U+0065 U+0301 are distinct canonical content.

## C11 CANONICAL TEXT RANGE INDEX UNIT
CANONICAL_TEXT_RANGE_INDEX_UNIT = Unicode scalar value offsets (code points).
UTF16_CODE_UNIT_IS_CANONICAL_TEXT_RANGE = NO. UI projects to UTF-16/grapheme;
font resolver operates on shaping-safe clusters.

## C12 GRAPHEME / SHAPING CLUSTER / RANGE
CanonicalTextRange ≠ GraphemeBoundary ≠ ShapingCluster. Combining marks, ZWJ
emoji, variation selectors, Indic conjuncts, Arabic contexts handled by
FONT_RESOLUTION_SEGMENTATION_MODEL (shaping-safe cluster segmentation for
fallback; grapheme segmentation for editing UX; canonical range for semantics).

## C13 LANGUAGE TAG
LANGUAGE_IDENTITY_IS_EXTENSIBLE_TYPED_TAG_NOT_CLOSED_ENUM_V1. BCP-47-style
typed tag (en, zh-Hans, zh-Hant, sr-Latn, sr-Cyrl, ar, ja, ko…). Zero locale/
business localization semantics.

## C14 SCRIPT
Script distinct from Language; typed ISO-15924-style (Latin, Arabic, Han,
Hiragana, Katakana, Cyrillic, Devanagari, Common, Inherited…). Common/
Inherited resolved by text analysis logic, never one-character fallback.

## C15 DIRECTION / BIDI
TEXT_LOGICAL_ORDER_AND_VISUAL_GLYPH_ORDER_ARE_DISTINCT_V1. Canonical content
preserves logical order; visual reorder is derived. Authored direction intent:
LTR | RTL | AUTO/UNSPECIFIED. AUTO historical determinism = deterministic
recomputation from immutable canonical content via versioned engine contract
(no host browser authority). BIDI_MODEL = EXPLICIT (logical preserved; layout
projection derived at engine boundary).

## C16 MIXED LANGUAGE MODEL
TextSemanticRun = TextRange + optional LanguageTag + optional Script +
optional Direction; explicit vs unspecified vs derived-analysis distinguished
(never collapsed). Single StyledText supports multi-language/script/direction.

## C17 STYLED TEXT / RUN MODEL
StyledText = TextContent + bounded non-overlapping canonical runs (TextStyle
runs, semantic runs, paragraph breaks) with explicit cascade resolved BEFORE
canonical state (RUN_OVERLAP_POLICY = overlap forbidden in canonical form;
composition resolved prior). Deterministic ordering/validation/equality.

## C18 TEXT STYLE V1
Bounded: FontSelectionIntent, FontSize (exact Rational), weight intent,
width/stretch intent, slant/style intent, letter-spacing (Rational),
line-height (Rational), fill (color-image value), text decoration, bounded
OpenType feature intent, variation-axis overrides. Zero raw double; zero CSS
typography superset.

## C19 PARAGRAPH STYLE V1
Bounded: alignment (START/END distinct from LEFT/RIGHT — RTL exists),
justification, line-height, wrapping policy, paragraph direction, line
breaking policy. Zero CSS superset.

## C20 TEXT FRAME / LAYOUT INTENT
AUTHORED_TEXT_LAYOUT_INTENT_AND_SHAPED_GLYPH_GEOMETRY_ARE_DISTINCT_V1.
TextFrame = width constraint, optional height, horizontal/vertical alignment,
wrap behavior, overflow behavior. Zero per-glyph x/y in canonical state
(shaped geometry = execution derivation).

## C21 FONT SELECTION INTENT
FontSelectionIntent = author intent only: family preference (typed name or
FontContentReference preference), weight, stretch, slant, optical sizing
intent, explicit variation-axis overrides, preferred face semantics. Zero
path / host font object / CSS-computed face / provider id inside intent.

## C22 FONT FALLBACK POLICY
FontFallbackPolicy = explicit ordered canonical intent: default[] chain +
scriptOverrides[] + languageOverrides[] + emoji[]. Fallback order is
semantically meaningful and preserved in serialization. Model supports future
multilingual fallback without schema break; resolved result pins exact
ResolvedFontInstance.

## C23 FONT RESOLVER AUTHORITY
FontResolver consumes FontSelectionIntent + text/script/language context +
explicit fallback policy + validated font catalog + rights/effective-use view
+ runtime capability view → ResolvedFontInstance. Candidate = Validated ∩
RightsAllowed ∩ Coverage ∩ ShapingConformance ∩ RuntimeCapability. No silent
host fallback. FONT_RESOLUTION_MODEL = EXPLICIT.

## C24 RESOLVED FONT INSTANCE
RESOLVED_FONT_INSTANCE_IS_EXACT_HISTORICAL_EXECUTION_INPUT_V1.
ResolvedFontInstance = exact FontContentIdentity + FaceIndex + exact
VariationCoordinates. Historically reproducible; zero host lookup results.

## C25 MISSING GLYPH DIAGNOSTICS
Typed diagnostics: MISSING_GLYPH, UNSUPPORTED_SCRIPT,
SHAPING_CONFORMANCE_FAILURE, FONT_UNAVAILABLE, FONT_VALIDATION_FAILED,
FONT_RIGHTS_DENIED, COLOR_FONT_CAPABILITY_UNAVAILABLE,
VARIABLE_FONT_CAPABILITY_UNAVAILABLE. Carry TextRange + safe scalar sequence +
script + language + attempted font identities + reason. Silent tofu is never
canonical behavior (preview vs final policy may differ).

## C26 VARIABLE FONT AXIS MODEL
VariationAxisTag = typed extensible 4-byte/4-char tag (wght/wdth/opsz/slnt/
ital/GRAD constants; zero closed enum). VariationCoordinate = tag + exact
Rational coordinate. Zero Map<String,Double>; zero binary float canonical.

## C27 VARIABLE FONT INSTANCE IDENTITY
Instance identity = FontContent + FaceIndex + exact axis coordinates
(VARIABLE_FONT_INSTANCE_IDENTITY_IS_FONT_CONTENT_PLUS_FACE_PLUS_EXACT_AXIS_
COORDINATES_V1). Named instance = authoring selection, not canonical instance
identity. Coordinates = design-space intent, never provider-normalized.

## C28 OPTICAL SIZING
OpticalSizingIntent = DISABLED | AUTO | EXPLICIT(exact coordinate).
AUTO_OPTICAL_SIZING_RESOLUTION_POINT = before atomic Timeline apply (operation
preview exposes exact opsz; historical commit stores exact coordinate —
HISTORICAL_VARIABLE_FONT_INSTANCE_NEVER_RE_RESOLVES_MUTABLE_AXIS_DEFAULTS_V1).

## C29 COLOR FONT CAPABILITY
COLOR_FONT_TECHNOLOGY_IS_EXPLICIT_CAPABILITY_NOT_PLATFORM_ACCIDENT_V1.
Manifest capability: COLR/CPAL, COLRv1, SVG-in-OpenType, CBDT/CBLC, sbix —
bounded extensible representation. Runtime support = capability resolution,
not silent substitution.

## C30 SUBSETTING BOUNDARY
FONT_SUBSET_IS_DERIVED_EXECUTION_ARTIFACT_NOT_CANONICAL_FONT_AUTHORITY_V1.
Future derived artifacts: EDITABLE_VARIABLE_PRESERVING_SUBSET and
FROZEN_STATIC_INSTANCE_SUBSET (distinct). Closure: glyph deps + GSUB/GPOS/GDEF
+ composites + variation deps + color deps. Subset NEVER replaces canonical
font identity (SUBSET_CANONICAL_AUTHORITY = NO). No implementation now.

## C31 RIGHTS HOOK
FONT_LOCAL_RIGHTS_AUTHORITY_CREATED = NO. Font pipeline exposes technical
observations only (embedding flags, subsetting technical flag, font metadata
observations) feeding future RIGHTS_AND_USAGE_POLICY_FOUNDATION_V1 (cross
media: video/audio/image/generated/templates/recipes/plugins/derived/
publication). RIGHTS_FOUNDATION_IMPLEMENTATION_COUNT = 0. Rights ≠ capability
≠ entitlement ≠ worker capability (distinct dimensions).

## C32 SHARED TEXT ENGINE CONTRACT
Provider-neutral boundary: TextShapingRequest (logical Unicode range + exact
ResolvedFontInstance + script + language + direction + OpenType features +
shaping params) → ShapedGlyphRun (glyph identity + cluster mapping + advance +
offset + exact font instance ref). ShapedGlyphRun is NOT Timeline canonical
state (preview cache / RenderPlan derived input / execution cache /
conformance artifact). Zero HarfBuzz classes in contract.

## C33 DETERMINISM LEVELS
L1 SEMANTIC_CONSISTENCY (Unicode + font pins + face + axes + fallback +
language/script/direction + typography intent); L2 LAYOUT_CONSISTENCY
(resolved runs + line breaks + glyph ids + clusters + advances + positions);
L3 PIXEL_DETERMINISM (rasterizer + hinting + AA + compositing + color +
reference env). Interactive authoring ≥ L1; canonical web/native preview = L2
target; canonical deterministic final render = L3 when requested.

## C34 TIMELINE TEXT INTEGRATION
TIMELINE_TEXT_INTEGRATION_MODEL = OPTION T1: Timeline-owned TextElement
(title/text element) — TITLE_TEXT_AND_TYPOGRAPHY_INTENT_ARE_TIMELINE_
COMPOSITION_SEMANTICS_V1. Aligned with #17 source-agnostic Timeline; zero
external Text database authority; zero SourceBinding change. Title and timed
subtitle share Text primitives but never one TextGodObject (Subtitle domain
deferred).

## C35 CANONICAL SERIALIZATION / HASH
TEXT_CANONICAL_HASH_INPUTS = logical text content + range/style semantics +
paragraph semantics + layout intent + authored font selection AND resolved
historical font identity (content pin + face + exact variation coordinates) +
fallback semantics. EXCLUDED: validation timestamps, scanner/tool versions,
cache ids, paths, worker/provider ids, host OS, Skia/HarfBuzz identity, font
URLs. Font validation metadata never hashed (FTG20). ShapedGlyphRun never
hashed. Source visual metadata (Media-owned) remains outside Timeline hash.

## C36 DIFF / MERGE
TEXT_DIFF_MODEL = typed field/range diff (content change, style-range change,
font selection change, resolved font instance change, paragraph change,
layout change all detectable). TEXT_MERGE_MODEL = conflict on overlapping
incompatible text edits; TimelineMergeEngine remains sole Timeline semantic
merge authority. CRDT_IMPLEMENTED = NO; zero second text revision graph.

## C37 OPERATION MODEL INTEGRATION
TEXT_OPERATION_IMPLEMENTATION_SCOPE (V1 minimal): ADD_TEXT_ELEMENT,
REMOVE_TEXT_ELEMENT, REPLACE_TEXT_CONTENT, SET_TEXT_STYLE_RANGE,
SET_PARAGRAPH_STYLE, SET_FONT_SELECTION, SET_FONT_FALLBACK_POLICY,
SET_VARIABLE_FONT_AXIS, SET_TEXT_LAYOUT. FONT_RESOLUTION_OCCURS_BEFORE_ATOMIC_
TIMELINE_APPLY_V1: intent → request → resolve (validated catalog + fallback +
rights + capability) → OperationPlan preview exposes exact resolved instances
+ missing glyphs + fallback choices → authorize → atomic apply → new revision.
Zero direct text mutation API outside boundary.

## C38 RENDERPLAN HANDOFF
FONT_TEXT_DEFINES_WHAT_RENDERPLAN_DEFINES_EXECUTION_INTENT_V1. Handoff: logical
StyledText + authored layout intent + exact historical font content pins +
face indices + resolved variation coordinates + script/language/direction +
OpenType feature intent + fallback resolution state/exact resolved runs +
required color-font capabilities. #20 decides execution (engine impl, artifact
availability, shaping provider, rasterizer, GPU/CPU, determinism mode,
subsetting, worker placement). ROADMAP_20_IMPLEMENTATION_COUNT = 0 in #19.

## C39 PROVIDER / TOOL BOUNDARY
HarfBuzz-class = shaping HOW; FreeType-class = parse/outline/raster HOW;
Skia-class = 2D drawing/layout/raster/composition HOW; CanvasKit-class =
browser/WASM execution surface HOW; Vulkan/WebGPU = GPU execution backends
only (glyph atlas, SDF/MSDF, color glyph paint, compositing later). None own
Unicode/BiDi/font identity/fallback/shaping semantics/canonical axes. Zero
provider types in canonical domain. DOM/browser native services = IME/
selection/caret/clipboard/accessibility/authoring UX only — never canonical
render authority (HOST_OS_FONT_FALLBACK_IS_NOT_CANONICAL_AUTHORITY_V1;
BROWSER_NATIVE_FONT_ENVIRONMENT_IS_NOT_CANONICAL_TEXT_RENDER_AUTHORITY_V1).

## C40 DEFER LIST
Deferred: full subtitle/caption domain, ASR, translation system, motion
graphics, text animation, text-on-path, 3D text, full CSS typography, browser
layout engine, complete OpenType implementation, custom HarfBuzz fork, font
rasterizer implementation, GPU glyph atlas, SDF/MSDF renderer, Vulkan/WebGPU
text renderer, full Rights engine, font marketplace, font licensing legal
inference, complete font subsetter, complete Unicode security scanner,
collaborative CRDT/OT editing, distributed font CDN, Roadmap #20 RenderPlan.

## Implementation gates (future)
FTG1 no host font canonical lookup; FTG2 no font path/URL canonical authority;
FTG3 untrusted font never enters shaper; FTG4 exact historical font content
pin; FTG5 collection face explicit; FTG6 variable font exact axes; FTG7 no
binary-float canonical axis values; FTG8 mixed-language supported; FTG9
Language≠Script≠Direction; FTG10 UTF-16 indices not canonical; FTG11 missing
glyph explicit; FTG12 fallback explicit; FTG13 no shaping-cluster split;
FTG14 shaping result absent from Timeline canonical state; FTG15 host OS
fallback count = 0; FTG16 subsets derived only; FTG17 font-specific Rights
authority count = 0; FTG18 provider classes absent from canonical domain;
FTG19 Timeline text affects semantic hash; FTG20 font validation metadata not
hashed; FTG21 zero compatibility; FTG22 Roadmap #20 implementation count = 0.
