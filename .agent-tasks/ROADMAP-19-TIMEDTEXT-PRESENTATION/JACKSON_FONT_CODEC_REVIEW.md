# ROADMAP #19 — JACKSON / FONT CODEC REVIEW

## Jackson annotations added (bounded decode plumbing only)

font-text-module (24 types):
FontRational, ResolvedFontRun, TextContent, TextSemanticRun, TextStyleRun,
TextStyle, TextRange, LanguageTag, ScriptTag, FontSize, FontFamilyName,
ValidatedFontExecutionReference, FaceIndex, VariationAxisTag, VariationCoordinate,
OpenTypeFeatureTag, StyledText, FontFallbackPolicy, ResolvedFontInstance,
TextFrame, ParagraphStyle, FontSelectionIntent, OpenTypeFeatureIntent, LineHeight,
OpticalSizingIntent (private ctor), FontContentDigest (private ctor)

timeline-module (1 type): TextElementId (@JsonValue + delegating @JsonCreator —
value type, String representation consistent with its single-field domain)

## Review invariants

FT-J1 semanticFingerprint derives ONLY from explicit canonicalValue: YES
  (TimedTextCanonicalSemantics.semanticFingerprint = writeValueAsString(canonicalValue))
FT-J2 encodeElements derives ONLY from explicit canonical representation: YES
FT-J3 default bean serialization is not semantic identity: YES
  (diff/patch use TimedTextCanonicalSemantics; no content.hashCode/toString left)
FT-J4 annotations added no authored semantic fields: YES
  (only @JsonCreator/@JsonProperty on existing single public constructors;
  param names verified == existing private field names)
FT-J5 annotations did not change equals/hash/domain behavior: YES
  (annotations are Jackson metadata; equals/hashCode untouched; existing
  TextElementTimelineIntegrationTest + TextElementDiffMergeTest + font-text tests green)
FT-J6 no provider/runtime fields introduced: YES
  (no drawtext/ffmpeg/fontfile strings in canonical path)
FT-J7 encode→decode→encode stable: PASS (s2EncodeDecodeEncodeStable + F5 delete-last
  reload + F1-F6 merged reloads)
FT-J8 map insertion order non-semantic: PASS (deepSorted in canonicalValue)
FT-J9 ordered runs preserve order: PASS (styleRuns/semanticRuns keep List order;
  s2t4-style ordering semantics in diff equality)
FT-J10 validation enforced after decode: PASS (TextElement ctor validates duration>0;
  StyledText validates run boundaries vs content scalar count; TextContent rejects
  unpaired surrogates — all run on decode path)

## Font execution boundary (FONT-T1..T12)

- FontFamilyName appears in FontSelectionIntent/fallback preferences = selection
  intent only (FONT_T1 canonical semantics)
- ResolvedFontRun/ResolvedFontInstance/ValidatedFontExecutionReference/FontContentDigest/
  FaceIndex/VariationCoordinate survive canonical round-trip exactly: PASS
  (a1/a4a5 round-trip + F1-F6 merged reloads carry resolvedFontRuns)
- TextShapingRequest consumes ResolvedFontInstance (not FontFamilyName): YES
  (TextShapingRequest fields: font = ResolvedFontInstance — repository reality)
- No production renderer performs host FontFamilyName lookup in canonical path:
  YES (canonical state = semantics + historical-frozen ResolvedFontRuns; host
  availability is runtime/preflight only — FONT_T12)
- FONT_FAMILY_NAME_IS_SELECTION_INTENT_NOT_EXECUTION_IDENTITY_V1 = PASS
- FONT_EXECUTION_REQUIRES_IMMUTABLE_RESOLVED_FONT_RESOURCE_V1 = PASS
- silent host-font fallback possible = NO (fallbackPolicy explicit enum; resolved
  runs frozen per TextElement javadoc "historical never re-resolves")
