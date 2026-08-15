---
type: architecture-governance-record
milestone: ROADMAP_19
name: ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY_V1
status: PASS (DECISION_RECOVERY_CLOSED_IMPLEMENTATION_PENDING)
date: 2026-08-16
base: db348cb1a153757f2c7234f963ed898a9769df40
---

# ROADMAP_19 FONT / TEXT FOUNDATION — DECISION RECOVERY

## Repository reality (measured)
EXISTING_FONT_MODEL = none (fontFamily raw String authority = 77 occurrences in
unshipped auto-captions/API/DTO consumers). EXISTING_TEXT_MODEL = none.
EXISTING_SUBTITLE_MODEL = none (DTO-level only). EXISTING_TIMELINE_TEXT_CONSUMER
= none. EXISTING_LANGUAGE_MODEL = none (PlatformException locale only).
EXISTING_RIGHTS_MODEL = none (MediaAsset.license observation String).
EXISTING_WASM_INFRASTRUCTURE = none (compatibility adapter unrelated).
EXISTING_SKIA_INFRASTRUCTURE = none. Greenfield applies.

## Key decisions (FONT_TEXT_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1, C1-C40)
- C1 single pure font-text-module (resource + text subpackages); -> color-image
  (acyclic); zero outward deps.
- C2/C3 font bytes = Artifact data plane; typed FontContentDigest (SHA-256)
  pin; ArtifactId association at application layer; storage never canonical.
- C4 ResolvedFontFace = FontContentIdentity + FaceIndex (TTC/OTC explicit).
- C5 FontFaceManifest: canonical parsed facts / derived diagnostics / execution
  capability observations three-way classification.
- C6-C8 Raw -> Validation -> Sanitization -> Conformance -> ValidatedExecution
  FontArtifact; historical pin = source digest + immutable validated execution
  artifact (never re-sanitize).
- C9-C12 Unicode logical content (zero glyph ids); authored sequence preserved
  (no silent NFC); canonical range = Unicode scalar offsets (UTF-16 never
  canonical); grapheme ≠ shaping cluster ≠ canonical range.
- C13-C16 BCP-47 typed LanguageTag; ISO-15924 Script; Direction LTR/RTL/AUTO
  with deterministic AUTO recomputation; TextSemanticRun mixed-language ranges.
- C17 StyledText non-overlapping canonical runs with pre-resolved cascade.
- C18-C20 TextStyle/ParagraphStyle/TextFrame V1 bounded; exact Rational
  numerics; START ≠ LEFT; zero glyph geometry in canonical state.
- C21-C24 FontSelectionIntent (author intent only) -> FontResolver (Validated ∩
  Rights ∩ Coverage ∩ Shaping ∩ Capability) -> ResolvedFontInstance (content +
  face + exact variation coordinates), historically reproducible.
- C25 typed missing-glyph diagnostics (8 kinds); no silent tofu.
- C26-C28 VariationAxisTag extensible typed; Rational coordinates; opsz AUTO
  resolved to exact coordinate before atomic apply.
- C29 color-font capability explicit; C30 subsetting derived-only (two derived
  variants); C31 Rights hook = technical observations only (zero local font
  rights authority); C32 provider-neutral TextShapingRequest -> ShapedGlyphRun
  (never Timeline canonical); C33 determinism L1/L2/L3; C34 Timeline-owned
  TextElement (OPTION T1, zero SourceBinding impact); C35 hash inputs defined
  (validation metadata/shaped glyphs excluded); C36 typed range diff/merge,
  TimelineMergeEngine sole authority, zero CRDT; C37 9 minimal text operations,
  font resolution before atomic apply; C38 RenderPlan handoff contract; C39
  provider/tool boundaries (HarfBuzz/FreeType/Skia/CanvasKit/Vulkan/WebGPU/
  DOM roles frozen); C40 defer list (23 items).

## Validation
Architecture drift 214/214 PASS; Modulith PASS; bootJar PASS; pfirr1 PASS;
CREDENTIAL_RESIDUE_FINAL = 0. Zero production implementation; zero DB schema;
zero Roadmap #20 implementation. Contract decision count = 40; UNRESOLVED_TBD
COUNT = 0. BLOCKERS = 0. ARCHITECTURE_ESCALATION = NONE.

## Final
READY_FOR_FONT_TEXT_FOUNDATION_IMPLEMENTATION = YES.
ROADMAP_19_STATUS = DECISION_RECOVERY_CLOSED_IMPLEMENTATION_PENDING.
MAIN_INTEGRATION_PERFORMED = NO (branch agent/font-text-foundation retained;
worktree .worktrees/font-text-foundation retained clean).
NEXT_ACTION = ROADMAP_19_FONT_TEXT_FOUNDATION_BOUNDED_IMPLEMENTATION.
NEXT_MANDATORY_CHATGPT_REVIEW = AFTER_ROADMAP_19.
