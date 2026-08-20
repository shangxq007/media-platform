package com.example.platform.render.domain.renderplan;

import com.example.platform.colorimage.Chromaticity;
import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.ColorPrimaries;
import com.example.platform.colorimage.RasterSampleDescription;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.fonttext.typography.VariationCoordinate;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.extension.domain.CapabilityRequirement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic canonical encoding + fingerprinting (C8). NO Jackson, NO
 * Object.toString() (except where a type's toString() IS its canonical form —
 * MediaTime, FrameRate, enums by name()).
 *
 * <p>Encoding scheme: every scalar string is length-prefixed ({@code len:value})
 * so concatenation is unambiguous regardless of content. Collections are sorted
 * by their element encoding before emission. Fixed explicit field order per type.
 * This single class canonicalizes both the plan and the graph (C8).
 */
public final class RenderPlanCanonicalCodec {

    private static final String LIST_START = "[";
    private static final String LIST_END = "]";
    private static final String ELEM_SEP = ",";
    private static final String OPTIONAL_PRESENT = "1";
    private static final String OPTIONAL_ABSENT = "0";

    /** Plan format version (C25). */
    public static final String PLAN_FORMAT_VERSION = "renderplan-format-v1";

    /** Graph format version (C25). */
    public static final String GRAPH_FORMAT_VERSION = "rendergraph-format-v1";

    /** Shared stateless codec instance. */
    public static final RenderPlanCanonicalCodec INSTANCE = new RenderPlanCanonicalCodec();

    private RenderPlanCanonicalCodec() {
    }

    // ── Fingerprint entry points ─────────────────────────────────────────────

    /**
     * Canonical string whose digest is the {@link RenderPlanFingerprint}.
     * Participants (R4-A3): formatVersion, revision.revisionId,
     * revision.contentDigest, authored Effect semantic reference (contract
     * version + content pin), request.extent, request.outputs, sorted nodes,
     * sorted edges.
     * EXCLUDED: request.id, plan id, status, diagnostics, resolution state,
     * capability context, provenance, execution requirements, timestamps,
     * provider/worker/device anything.
     */
    public String planFingerprintCanonical(RenderPlan plan) {
        return planFingerprintCanonical(plan.revision(), plan.effectSemanticReference(),
                plan.request(), plan.nodes(), plan.edges());
    }

    /**
     * Component-based variant (C7): computes the plan fingerprint canonical string
     * directly from the plan's ingredients, without requiring a constructed
     * RenderPlan. Used by the planner, which must compute the fingerprint before
     * assembling the plan record (the record requires the fingerprint field).
     */
    public String planFingerprintCanonical(
            TimelineRevisionReference revision,
            EffectSemanticReference effectSemanticReference,
            RenderRequest request,
            List<RenderNode> nodes,
            List<RenderDependencyEdge> edges) {
        StringBuilder sb = new StringBuilder();
        s(sb, PLAN_FORMAT_VERSION);
        s(sb, revision.revisionId());
        s(sb, contentDigestCanonical(revision.contentDigest()));
        // R4-A3: the authored Effect semantic reference is a canonical
        // fingerprint participant — authored Effect semantic change ALWAYS
        // changes the fingerprint even when materialized nodes stay identical.
        s(sb, effectSemanticReference.semanticContractVersion().value());
        s(sb, effectSemanticReference.effectStateDigest());
        s(sb, extentCanonical(request.extent()));
        s(sb, outputsCanonical(request.outputs()));

        // nodes sorted by RenderNodeId.value()
        List<String> nodeEncodings = new ArrayList<>();
        for (RenderNode node : nodes) {
            nodeEncodings.add(nodeCanonical(node));
        }
        Collections.sort(nodeEncodings);
        list(sb, nodeEncodings);

        // edges sorted by (producer, consumer, variant)
        List<String> edgeEncodings = new ArrayList<>();
        for (RenderDependencyEdge edge : edges) {
            edgeEncodings.add(edgeCanonical(edge));
        }
        Collections.sort(edgeEncodings);
        list(sb, edgeEncodings);

        return sb.toString();
    }

    /**
     * Canonical string whose digest is the {@link RenderGraphFingerprint}.
     * Participants: formatVersion, planFingerprint, sorted node ids, sorted edges.
     */
    public String graphFingerprintCanonical(RenderGraph graph) {
        StringBuilder sb = new StringBuilder();
        s(sb, GRAPH_FORMAT_VERSION);
        s(sb, graph.planFingerprint().sha256Hex());

        List<String> nodeIds = new ArrayList<>();
        for (RenderNode node : graph.nodes()) {
            nodeIds.add(node.id().value());
        }
        Collections.sort(nodeIds);
        list(sb, nodeIds);

        List<String> edgeEncodings = new ArrayList<>();
        for (RenderDependencyEdge edge : graph.edges()) {
            edgeEncodings.add(edgeCanonical(edge));
        }
        Collections.sort(edgeEncodings);
        list(sb, edgeEncodings);

        return sb.toString();
    }

    /**
     * Canonical string whose digest is the node requirements fingerprint (C6):
     * the node's (artifactReferences + capabilityRequirements + outputRequirements).
     */
    public String nodeRequirementsFingerprintCanonical(RenderNode node) {
        return requirementsFingerprintCanonical(
                node.artifactReferences(),
                node.capabilityRequirements(),
                node.outputRequirements(),
                List.of());
    }

    /**
     * Variant for effect nodes (C6): the requirements fingerprint additionally
     * encodes the effect's category + parameters deterministically, so an effect
     * parameter change changes the effect node's identity. The supplied
     * paramEncodings are pre-sorted "key=value" strings derived from the authored
     * effect parameter map (the codec never references Map&lt;String,…&gt; itself).
     */
    public String nodeRequirementsFingerprintCanonical(RenderNode node, List<String> paramEncodings) {
        return requirementsFingerprintCanonical(
                node.artifactReferences(),
                node.capabilityRequirements(),
                node.outputRequirements(),
                paramEncodings);
    }

    /**
     * Requirements fingerprint from raw requirement lists (C6). Used by the
     * materializer to compute a node's id before the node record exists.
     *
     * @param paramEncodings pre-sorted "key=value" encodings of the effect parameters
     */
    public String requirementsFingerprintCanonical(
            List<RenderArtifactReference> artifactReferences,
            List<com.example.platform.extension.domain.CapabilityRequirement> capabilityRequirements,
            List<RenderOutputRequirement> outputRequirements,
            List<String> paramEncodings) {
        StringBuilder sb = new StringBuilder();
        list(sb, sortedEncodings(artifactReferences.stream()
                .map(this::artifactReferenceCanonical).toList()));
        list(sb, sortedEncodings(capabilityRequirements.stream()
                .map(this::capabilityRequirementCanonical).toList()));
        list(sb, sortedEncodings(outputRequirements.stream()
                .map(this::outputRequirementCanonical).toList()));
        // effect parameters participate in the effect node's requirement encoding
        list(sb, paramEncodings);
        return sb.toString();
    }

    /** SHA-256 hex (lowercase) over the canonical bytes of the given string. */
    public String sha256Hex(String canonical) {
        MessageDigest digest = sha256();
        byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
        return toHex(bytes);
    }

    // ── Per-type canonical encodings ─────────────────────────────────────────

    private String nodeCanonical(RenderNode node) {
        StringBuilder sb = new StringBuilder();
        s(sb, node.id().value());
        s(sb, node.kind().canonicalName());
        s(sb, node.componentPath().toString());
        s(sb, node.operationKey());
        list(sb, sortedEncodings(node.artifactReferences().stream()
                .map(this::artifactReferenceCanonical).toList()));
        list(sb, sortedEncodings(node.capabilityRequirements().stream()
                .map(this::capabilityRequirementCanonical).toList()));
        list(sb, sortedEncodings(node.outputRequirements().stream()
                .map(this::outputRequirementCanonical).toList()));
        // F1: typed materialized WHAT participates in node canonical encoding.
        list(sb, sortedEncodings(node.materializationRequirements().stream()
                .map(this::materializationRequirementCanonical).toList()));
        optionalSampleWindow(sb, node.requiredSampleWindow());
        return sb.toString();
    }

    private String edgeCanonical(RenderDependencyEdge edge) {
        StringBuilder sb = new StringBuilder();
        s(sb, edge.producerId().value());
        s(sb, edge.consumerId().value());
        s(sb, edge.dependency().variantKey());
        return sb.toString();
    }

    private String extentCanonical(RenderExtent extent) {
        StringBuilder sb = new StringBuilder();
        s(sb, mediaTimeCanonical(extent.start()));
        s(sb, mediaTimeCanonical(extent.end()));
        s(sb, frameRateCanonical(extent.frameRate()));
        return sb.toString();
    }

    private String outputsCanonical(List<RenderOutputRequirement> outputs) {
        StringBuilder sb = new StringBuilder();
        // authored order preserved (request.outputs is semantic); encode positionally
        sb.append(outputs.size());
        for (RenderOutputRequirement output : outputs) {
            s(sb, outputRequirementCanonical(output));
        }
        return sb.toString();
    }

    private String outputRequirementCanonical(RenderOutputRequirement output) {
        StringBuilder sb = new StringBuilder();
        s(sb, output.role().name());
        // color/raster references are optional typed hooks; encode FULL VALUE so a
        // semantic value change (e.g. BT709 -> BT2020 primaries) changes the
        // fingerprint (C14/C8 participation). R2 B3: NO Object.toString() —
        // explicit field encoding of the sealed ColorDescription and the
        // RasterSampleDescription record.
        optional(sb, output.colorDescription().map(this::colorDescriptionCanonical));
        optional(sb, output.rasterSample().map(this::rasterSampleCanonical));
        return sb.toString();
    }

    /**
     * Explicit value encoding of the sealed {@code ColorDescription} (R2 B3 +
     * R4-M1). Unknown future variants FAIL CLOSED — no empty encoding, no
     * generic fallback, no default-to-existing-subtype semantics.
     */
    private String colorDescriptionCanonical(ColorDescription description) {
        StringBuilder sb = new StringBuilder();
        if (description instanceof ColorDescription.ParametricColorDescription parametric) {
            s(sb, "PARAMETRIC");
            s(sb, colorPrimariesCanonical(parametric.primaries()));
            s(sb, parametric.transfer().name());
            s(sb, parametric.matrix().name());
            s(sb, parametric.range().name());
        } else if (description instanceof ColorDescription.ProfileBasedColorDescription profile) {
            s(sb, "PROFILE");
            s(sb, profile.profileFormat().name());
            s(sb, profile.profileContentDigest().toString());
        } else {
            // R4-M1: future sealed subtype must never silently share canonical
            // bytes or produce empty encoding.
            throw new IllegalArgumentException(
                    "Unsupported ColorDescription variant: " + description.getClass().getName()
                            + " — canonical encoding fails closed (R4-M1)");
        }
        return sb.toString();
    }

    /**
     * Explicit encoding of sealed {@code ColorPrimaries} (WellKnown vs Custom).
     * R3-M1: unknown future variants FAIL CLOSED — never collapse to a generic
     * fallback token (a future subtype must not silently share canonical bytes).
     */
    private String colorPrimariesCanonical(ColorPrimaries primaries) {
        if (primaries instanceof ColorPrimaries.WellKnown wellKnown) {
            return "WELL_KNOWN:" + wellKnown.name();
        }
        if (primaries instanceof ColorPrimaries.Custom custom) {
            return "CUSTOM:" + chromaticityCanonical(custom.red())
                    + ":" + chromaticityCanonical(custom.green())
                    + ":" + chromaticityCanonical(custom.blue())
                    + ":" + chromaticityCanonical(custom.whitePoint());
        }
        throw new IllegalArgumentException(
                "Unsupported ColorPrimaries variant: " + primaries.getClass().getName()
                        + " — canonical encoding fails closed (R3-M1)");
    }

    /** Explicit encoding of {@code Chromaticity} (rational x/y). */
    private String chromaticityCanonical(Chromaticity chromaticity) {
        return chromaticity.x().toString() + "/" + chromaticity.y().toString();
    }

    /** Explicit value encoding of {@code RasterSampleDescription} (R2 B3). */
    private String rasterSampleCanonical(RasterSampleDescription sample) {
        StringBuilder sb = new StringBuilder();
        s(sb, sample.family().name());
        s(sb, sample.organization().name());
        s(sb, Integer.toString(sample.bitDepth()));
        s(sb, sample.chromaSubsampling().name());
        s(sb, sample.chromaLocation().name());
        s(sb, Boolean.toString(sample.alphaComponentPresent()));
        return sb.toString();
    }

    private String artifactReferenceCanonical(RenderArtifactReference ref) {
        return ref.variantKey();
    }

    private String capabilityRequirementCanonical(
            com.example.platform.extension.domain.CapabilityRequirement req) {
        // Platform capability authority (F3): capability id + contract range.
        return req.capabilityId().value() + "@" + req.contractRange();
    }

    /**
     * F1/B3: typed materialized WHAT canonical encoding. Deterministic
     * per-variant field order; every scalar length-prefixed. No locale-sensitive
     * formatting; no object identity/hashCode-based serialization; NO reliance
     * on Object.toString() — every nested value type is encoded explicitly by
     * its semantic fields (R2 B3).
     */
    /**
     * R6-C2: THE single canonical encoder for an effect node's complete
     * semantic identity — used by BOTH the effect node requirement fingerprint
     * (node identity) and the final RenderPlan canonical serialization. One
     * grammar, multiple consumers; no second ad-hoc node grammar.
     *
     * @param requirement  the complete typed Logical Effect WHAT
     * @param capabilities the effective node capability requirements
     *                     (category baseline UNION definition-required)
     * @return canonical bytes for the node's semantic identity
     */
    public String effectMaterializationRequirementCanonical(
            EffectMaterializationRequirement requirement,
            List<CapabilityRequirement> capabilities) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(capabilities, "capabilities");
        StringBuilder sb = new StringBuilder();
        // effect semantic WHAT first (single canonical encoding).
        sb.append(materializationRequirementCanonical(requirement));
        // effective capabilities participate in node identity (R6-B/R6-C) —
        // ONE canonical CapabilityRequirement identity = CapabilityId +
        // ContractVersionRange (shared with final plan serialization; §33).
        s(sb, "CAPS");
        countedSorted(sb, capabilities.stream()
                .map(this::capabilityRequirementCanonical).toList());
        return sb.toString();
    }

    /** Canonical encoding of one materialization requirement (semantic fields only). */
    private String materializationRequirementCanonical(RenderMaterializationRequirement requirement) {
        StringBuilder sb = new StringBuilder();
        materializationRequirementCanonicalInto(sb, requirement);
        return sb.toString();
    }

    private void materializationRequirementCanonicalInto(
            StringBuilder sb, RenderMaterializationRequirement requirement) {
        s(sb, requirement.variantKey());
        if (requirement instanceof EffectMaterializationRequirement effect) {
            s(sb, effect.category().name());
            // R5-B + R6-C2: complete typed Logical Effect WHAT — definition
            // identity + version, instance identity, enabled, exact application
            // range, automation references, temporal behavior, typed authored
            // target — length-prefixed explicit framing (no delimiter
            // flattening). Deterministic for semantic-equal state; distinct for
            // semantic-distinct state.
            s(sb, effect.effectInstanceId());
            s(sb, effect.effectDefinitionId());
            s(sb, effect.effectDefinitionVersion());
            s(sb, Boolean.toString(effect.enabled()));
            s(sb, effect.applicationRange().start().toString());
            s(sb, effect.applicationRange().end().toString());
            s(sb, effect.temporalBehavior().name());
            // R6-F: typed authored target participates in canonical semantics.
            if (effect.target() instanceof ClipEffectTarget clipTarget) {
                s(sb, clipTarget.trackId());
                s(sb, clipTarget.clipId());
            } else {
                s(sb, "NO_TARGET");
            }
            // R4-B: effect parameter pairs use the SINGLE shared pair encoder
            // (timeline/effect domain authority) — key and value are framed
            // independently, no key + ":" + value delimiter flattening.
            counted(sb, effect.sortedParameters().stream()
                    .map(p -> EffectSemanticStateCanonicalSemantics.encodeParameterPair(
                            p.key(), p.value()))
                    .toList());
            // R5-B: automation binding references (sorted by parameter key;
            // semantically unordered collection -> deterministic order).
            countedSorted(sb, effect.sortedAutomationBindings().stream()
                    .map(b -> EffectSemanticStateCanonicalSemantics.encodeParameterPair(
                            b.parameterKey(), b.automationReference()))
                    .toList());
        } else if (requirement instanceof AudioProcessMaterializationRequirement audio) {
            // AudioGain/AudioMute/StereoBalance define explicit canonical
            // toString() contracts (value-only, deterministic); enumerated as
            // documented canonical textual representations.
            s(sb, audio.gain().toString());
            s(sb, audio.mute().toString());
            s(sb, audio.balance().toString());
        } else if (requirement instanceof TimedTextMaterializationRequirement text) {
            s(sb, text.id().value());
            s(sb, fontRationalCanonical(text.start()));
            s(sb, fontRationalCanonical(text.duration()));
            styledTextCanonical(sb, text.styledText());
            textFrameCanonical(sb, text.frame());
            fontFallbackPolicyCanonical(sb, text.fallbackPolicy());
            list(sb, sortedEncodings(text.resolvedFontRuns().stream()
                    .map(this::resolvedFontRunCanonical).toList()));
        } else {
            // R3-M1: unknown sealed variant fails closed — a future
            // materialization variant must never silently share canonical bytes.
            throw new IllegalArgumentException(
                    "Unsupported RenderMaterializationRequirement variant: "
                            + requirement.getClass().getName()
                            + " — canonical encoding fails closed (R3-M1)");
        }
    }

    // ── R2 B3 explicit value encoding for font-text value types ──────────────

    /**
     * Explicit canonical encoding of {@code StyledText} (R2 B2/B3): content,
     * semantic runs, style runs and paragraph style — complete authored text
     * WHAT, value-deterministic, no Object.toString().
     */
    private void styledTextCanonical(StringBuilder sb, StyledText styledText) {
        s(sb, styledText.content().value());
        // R3-B2: count-framed sections — semantic runs vs style runs cannot
        // collide (each section carries explicit cardinality).
        countedSorted(sb, styledText.semanticRuns().stream()
                .map(this::textSemanticRunCanonical).toList());
        countedSorted(sb, styledText.styleRuns().stream()
                .map(this::textStyleRunCanonical).toList());
        paragraphStyleCanonical(sb, styledText.paragraphStyle());
    }

    private String textSemanticRunCanonical(TextSemanticRun run) {
        StringBuilder sb = new StringBuilder();
        s(sb, textRangeCanonical(run.range()));
        s(sb, run.language() != null ? run.language().toString() : "");
        s(sb, run.script() != null ? run.script().toString() : "");
        s(sb, run.directionOverride().name());
        return sb.toString();
    }

    private String textStyleRunCanonical(TextStyleRun run) {
        StringBuilder sb = new StringBuilder();
        s(sb, textRangeCanonical(run.range()));
        textStyleCanonical(sb, run.style());
        return sb.toString();
    }

    /** Explicit encoding of {@code TextStyle}: font selection, size, tracking, features. */
    private void textStyleCanonical(StringBuilder sb, TextStyle style) {
        fontSelectionIntentCanonical(sb, style.fontSelection());
        s(sb, fontRationalCanonical(style.fontSize().value()));
        s(sb, fontRationalCanonical(style.tracking()));
        // R4-B: OpenType feature settings pairs framed via the shared pair
        // encoder (tag/state independent framing, no delimiter flattening).
        countedSorted(sb, style.features().settings().stream()
                .map(setting -> EffectSemanticStateCanonicalSemantics.encodeParameterPair(
                        setting.tag().toString(), setting.state().name()))
                .toList());
    }

    private void fontSelectionIntentCanonical(StringBuilder sb, FontSelectionIntent intent) {
        // R3-B2: family preferences count-framed (ordered preference list).
        counted(sb, intent.familyPreferences().stream()
                .map(FontFamilyName::toString).toList());
        s(sb, intent.weight().name());
        s(sb, intent.stretch().name());
        s(sb, intent.slant().name());
        // OpticalSizingIntent: kind + optional explicit coordinate
        s(sb, intent.opticalSizing().kind().name());
        if (intent.opticalSizing().explicitCoordinate() != null) {
            s(sb, fontRationalCanonical(intent.opticalSizing().explicitCoordinate()));
        } else {
            s(sb, "");
        }
        // R3-B2: explicit axis overrides count-framed (sorted by tag).
        countedSorted(sb, intent.explicitAxisOverrides().stream()
                .map(coordinate -> coordinate.axis().toString() + ":"
                        + fontRationalCanonical(coordinate.coordinate()))
                .toList());
    }

    /** Explicit encoding of {@code ParagraphStyle}: all six semantic fields. */
    private void paragraphStyleCanonical(StringBuilder sb, ParagraphStyle style) {
        s(sb, style.alignment().name());
        s(sb, style.justification().name());
        s(sb, style.lineHeight().form().name());
        s(sb, fontRationalCanonical(style.lineHeight().value()));
        s(sb, style.wrapPolicy().name());
        s(sb, style.baseDirection().name());
        s(sb, style.lineBreakPolicy().name());
    }

    /** Explicit encoding of {@code TextFrame}: all six semantic fields (R2 B3). */
    private void textFrameCanonical(StringBuilder sb, TextFrame frame) {
        s(sb, fontRationalCanonical(frame.widthConstraint()));
        s(sb, frame.heightConstraint() != null ? fontRationalCanonical(frame.heightConstraint()) : "");
        s(sb, frame.horizontalAlignment().name());
        s(sb, frame.verticalAlignment().name());
        s(sb, frame.wrapBehavior().name());
        s(sb, frame.overflowBehavior().name());
    }

    /**
     * Explicit encoding of {@code FontFallbackPolicy} (R2 B3 + R3-B2 framing):
     * defaultChain, scriptOverrides, languageOverrides, emojiChain are FOUR
     * adjacent variable-length sections — each is count-framed so
     * {@code defaultChain=[A,B,C], scriptOverrides=[]} can NEVER collide with
     * {@code defaultChain=[A], scriptOverrides=[{script=B, chain=[C]}]}.
     */
    private void fontFallbackPolicyCanonical(StringBuilder sb, FontFallbackPolicy policy) {
        counted(sb, policy.defaultChain().stream()
                .map(FontFamilyName::toString).toList());
        countedSorted(sb, policy.scriptOverrides().stream()
                .map(this::scriptOverrideCanonical).toList());
        countedSorted(sb, policy.languageOverrides().stream()
                .map(this::languageOverrideCanonical).toList());
        counted(sb, policy.emojiChain().stream()
                .map(FontFamilyName::toString).toList());
    }

    private String scriptOverrideCanonical(FontFallbackPolicy.ScriptOverride override) {
        StringBuilder sb = new StringBuilder();
        s(sb, override.script().toString());
        counted(sb, override.chain().stream().map(FontFamilyName::toString).toList());
        return sb.toString();
    }

    private String languageOverrideCanonical(FontFallbackPolicy.LanguageOverride override) {
        StringBuilder sb = new StringBuilder();
        s(sb, override.language().toString());
        counted(sb, override.chain().stream().map(FontFamilyName::toString).toList());
        return sb.toString();
    }

    /** Explicit encoding of {@code ResolvedFontRun}: range + complete font instance (R2 B3). */
    private String resolvedFontRunCanonical(ResolvedFontRun run) {
        StringBuilder sb = new StringBuilder();
        s(sb, textRangeCanonical(run.range()));
        s(sb, run.font().executionReference().sourceFontContentDigest().toString());
        s(sb, run.font().executionReference().validatedExecutionContentDigest().toString());
        s(sb, run.font().executionReference().securityState().name());
        s(sb, run.font().executionReference().format().name());
        s(sb, run.font().executionReference().faceIndex().toString());
        // R3-B2: variation coordinates count-framed (sorted by tag).
        countedSorted(sb, run.font().variationCoordinates().stream()
                .map(coordinate -> coordinate.axis().toString() + ":"
                        + fontRationalCanonical(coordinate.coordinate()))
                .toList());
        return sb.toString();
    }

    /** Explicit encoding of {@code TextRange}: start/end scalar offsets. */
    private String textRangeCanonical(TextRange range) {
        return range.start() + ":" + range.end();
    }

    /**
     * Explicit encoding of {@code FontRational} (numerator/denominator). The
     * type's toString() is a documented canonical textual representation
     * ("num/den"), but encoding via explicit numeric fields is used here to
     * remain fully value-deterministic independent of the value type's contract.
     */
    private String fontRationalCanonical(FontRational rational) {
        return rational.numerator() + "/" + rational.denominator();
    }

    private String mediaTimeCanonical(MediaTime time) {
        // MediaTime.toString() IS canonical: "ticks/timeScale" or "0"
        return time.toString();
    }

    // ── R3 test visibility: package-private canonical encoders ───────────────

    String fallbackPolicyCanonicalForTest(FontFallbackPolicy policy) {
        StringBuilder sb = new StringBuilder();
        fontFallbackPolicyCanonical(sb, policy);
        return sb.toString();
    }

    String styledTextCanonicalForTest(StyledText styled) {
        StringBuilder sb = new StringBuilder();
        styledTextCanonical(sb, styled);
        return sb.toString();
    }

    String fontSelectionCanonicalForTest(FontSelectionIntent intent) {
        StringBuilder sb = new StringBuilder();
        fontSelectionIntentCanonical(sb, intent);
        return sb.toString();
    }

    private String frameRateCanonical(FrameRate rate) {
        // FrameRate.toString() IS canonical: "num/den"
        return rate.toString();
    }

    private String contentDigestCanonical(ContentDigest digest) {
        // ContentDigest.toString() == "ALGORITHM:canonicalValue"
        return digest.toString();
    }

    // ── Low-level encoding primitives ────────────────────────────────────────

    /** Length-prefixed string: guarantees unambiguous concatenation. */
    private void s(StringBuilder sb, String value) {
        sb.append(value.length()).append(':').append(value);
    }

    private void list(StringBuilder sb, List<String> sortedEncodings) {
        sb.append(LIST_START);
        for (int i = 0; i < sortedEncodings.size(); i++) {
            if (i > 0) sb.append(ELEM_SEP);
            s(sb, sortedEncodings.get(i));
        }
        sb.append(LIST_END);
    }

    /**
     * R3-B2: count-framed variable-length section. Emits the element count
     * (length-prefixed) followed by each element's canonical encoding
     * (length-prefixed). Adjacent variable-length sections therefore cannot
     * collide: every section boundary carries an explicit cardinality.
     */
    private void counted(StringBuilder sb, List<String> elementEncodings) {
        s(sb, Integer.toString(elementEncodings.size()));
        for (String encoding : elementEncodings) {
            s(sb, encoding);
        }
    }

    /**
     * R3-B2: count-framed section helper for encodings that are already sorted.
     */
    private void countedSorted(StringBuilder sb, List<String> encodings) {
        counted(sb, sortedEncodings(encodings));
    }

    private void optional(StringBuilder sb, Optional<String> value) {
        if (value.isPresent()) {
            sb.append(OPTIONAL_PRESENT);
            s(sb, value.get());
        } else {
            sb.append(OPTIONAL_ABSENT);
        }
    }

    private void optionalSampleWindow(StringBuilder sb, Optional<RenderSampleWindow> window) {
        if (window.isPresent()) {
            sb.append(OPTIONAL_PRESENT);
            RenderSampleWindow w = window.get();
            s(sb, mediaTimeCanonical(w.start()));
            s(sb, mediaTimeCanonical(w.end()));
            s(sb, frameRateCanonical(w.frameRate()));
        } else {
            sb.append(OPTIONAL_ABSENT);
        }
    }

    private List<String> sortedEncodings(List<String> encodings) {
        List<String> copy = new ArrayList<>(encodings);
        Collections.sort(copy);
        return copy;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
