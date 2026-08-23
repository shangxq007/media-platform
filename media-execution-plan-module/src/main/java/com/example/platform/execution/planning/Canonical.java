package com.example.platform.execution.planning;

import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.RasterSampleDescription;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import java.util.Comparator;
import java.util.Objects;

/**
 * Roadmap #21 explicit canonical encoder (Blocker B4).
 *
 * <p>Principles: fixed field order; explicit variant tag; explicit scalar
 * encoding (full semantic values — no presence-only tokens); deterministic
 * collection ordering where order is non-semantic; preserved positional order
 * where order IS semantic; unknown future variants FAIL CLOSED; NO
 * Object.toString() semantic authority; no locale-dependent formatting; no
 * object identity/hashCode. CapabilityRequirement.toString() omits
 * alternatives — explicit field encoding is mandatory.
 */
public final class Canonical {

    private Canonical() {
    }

    /** CapabilityRequirement: id | required | range | sorted alternatives. */
    public static String capability(CapabilityRequirement cr) {
        Objects.requireNonNull(cr, "cr");
        StringBuilder sb = new StringBuilder();
        sb.append(cr.capabilityId().value())
                .append('|').append(cr.required())
                .append('|').append(contractRange(cr.contractRange()));
        if (cr.alternatives() != null && !cr.alternatives().isEmpty()) {
            var alts = cr.alternatives().stream()
                    .map(a -> a.value())
                    .sorted()
                    .toList();
            sb.append('|');
            for (int i = 0; i < alts.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(alts.get(i));
            }
        }
        return sb.toString();
    }

    /** ContractVersionRange: explicit min.major.minor-max.major.minor. */
    public static String contractRange(ContractVersionRange range) {
        if (range == null) {
            return "any";
        }
        return version(range.min()) + "-" + version(range.max());
    }

    private static String version(ContractVersion v) {
        return v.major() + "." + v.minor();
    }

    /** RenderExecutionRequirement: gpu | determinism | sandboxed. */
    public static String executionIntent(RenderExecutionRequirement er) {
        return (er.gpu() != null ? er.gpu().name() : "null")
                + "|" + (er.determinism() != null ? er.determinism().name() : "null")
                + "|" + er.sandboxedIntent();
    }

    /**
     * RenderOutputRequirement: FULL value encoding — role + complete
     * ColorDescription variant payload + complete RasterSampleDescription.
     * No presence-only tokens (B4: full color/raster digest participation).
     */
    public static String outputRequirement(RenderOutputRequirement o) {
        Objects.requireNonNull(o, "o");
        StringBuilder sb = new StringBuilder();
        sb.append(o.role() != null ? o.role().name() : "null");
        sb.append('|');
        if (o.colorDescription() != null) {
            sb.append(colorDescription(o.colorDescription().orElse(null)));
        } else {
            sb.append("null");
        }
        sb.append('|');
        if (o.rasterSample() != null) {
            sb.append(rasterSample(o.rasterSample().orElse(null)));
        } else {
            sb.append("null");
        }
        return sb.toString();
    }

    /** ColorDescription: sealed variant tag + full typed fields. */
    public static String colorDescription(ColorDescription cd) {
        if (cd == null) {
            return "null";
        }
        if (cd instanceof ColorDescription.ParametricColorDescription p) {
            return "PARAMETRIC|" + primaries(p.primaries())
                    + "|" + (p.transfer() != null ? p.transfer().name() : "null")
                    + "|" + (p.matrix() != null ? p.matrix().name() : "null")
                    + "|" + (p.range() != null ? p.range().name() : "null");
        }
        if (cd instanceof ColorDescription.ProfileBasedColorDescription pb) {
            return "PROFILE_BASED|" + (pb.profileFormat() != null ? pb.profileFormat().name() : "null")
                    + "|" + (pb.profileContentDigest() != null
                            ? pb.profileContentDigest().sha256Hex() : "null");
        }
        throw new IllegalStateException("unknown ColorDescription variant — FAIL CLOSED: " + cd.getClass());
    }

    /** ColorPrimaries: sealed variant — WellKnown enum or explicit custom chromaticities. */
    static String primaries(com.example.platform.colorimage.ColorPrimaries cp) {
        if (cp == null) {
            return "null";
        }
        if (cp instanceof com.example.platform.colorimage.ColorPrimaries.WellKnown w) {
            return "WELL_KNOWN|" + w.name();
        }
        if (cp instanceof com.example.platform.colorimage.ColorPrimaries.Custom cu) {
            return "CUSTOM|" + chromaticity(cu.red())
                    + "|" + chromaticity(cu.green())
                    + "|" + chromaticity(cu.blue())
                    + "|" + chromaticity(cu.whitePoint());
        }
        throw new IllegalStateException("unknown ColorPrimaries variant — FAIL CLOSED: " + cp.getClass());
    }

    static String chromaticity(com.example.platform.colorimage.Chromaticity ch) {
        if (ch == null) {
            return "null";
        }
        return (ch.x() != null ? ch.x().toString() : "null")
                + "," + (ch.y() != null ? ch.y().toString() : "null");
    }

    /** RasterSampleDescription: full typed fields. */
    public static String rasterSample(RasterSampleDescription r) {
        if (r == null) {
            return "null";
        }
        return (r.family() != null ? r.family().name() : "null")
                + "|" + (r.organization() != null ? r.organization().name() : "null")
                + "|" + r.bitDepth()
                + "|" + (r.chromaSubsampling() != null ? r.chromaSubsampling().name() : "null")
                + "|" + (r.chromaLocation() != null ? r.chromaLocation().name() : "null")
                + "|" + r.alphaComponentPresent();
    }

    /**
     * RenderMaterializationRequirement: explicit sealed-variant full payload
     * encoding — NEVER variantTag + toString().
     */
    public static String materialization(RenderMaterializationRequirement m) {
        Objects.requireNonNull(m, "m");
        if (m instanceof com.example.platform.render.domain.renderplan.EffectMaterializationRequirement e) {
            return "EFFECT|" + (e.category() != null ? e.category().name() : "null")
                    + "|" + effectParameters(e)
                    + "|" + (e.effectInstanceId() != null ? e.effectInstanceId() : "null")
                    + "|" + (e.effectDefinitionId() != null ? e.effectDefinitionId() : "null")
                    + "|" + (e.effectDefinitionVersion() != null ? e.effectDefinitionVersion() : "null")
                    + "|" + e.enabled()
                    + "|" + (e.applicationRange() != null
                            ? (e.applicationRange().start() != null
                                    ? e.applicationRange().start().ticks() + "/" + e.applicationRange().start().timeScale() : "null")
                            + "-" + (e.applicationRange().end() != null
                                    ? e.applicationRange().end().ticks() + "/" + e.applicationRange().end().timeScale() : "null")
                            : "null")
                    + "|" + automationBindings(e)
                    + "|" + (e.temporalBehavior() != null ? e.temporalBehavior().name() : "null")
                    + "|" + effectTarget(e);
        }
        if (m instanceof com.example.platform.render.domain.renderplan.AudioProcessMaterializationRequirement a) {
            return "AUDIO_PROCESS|" + a.toString(); // typed immutable value record
        }
        if (m instanceof com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement t) {
            return "TIMED_TEXT|" + t.toString(); // typed immutable value record
        }
        throw new IllegalStateException(
                "unknown RenderMaterializationRequirement variant — FAIL CLOSED: " + m.getClass());
    }

    private static String effectParameters(
            com.example.platform.render.domain.renderplan.EffectMaterializationRequirement e) {
        if (e.parameters() == null || e.parameters().isEmpty()) {
            return "none";
        }
        return e.parameters().stream()
                .sorted(Comparator.comparing(p -> p.key() != null ? p.key() : ""))
                .map(p -> (p.key() != null ? p.key() : "null") + "=" + (p.value() != null ? p.value() : "null"))
                .reduce((x, y) -> x + "," + y)
                .orElse("none");
    }

    private static String automationBindings(
            com.example.platform.render.domain.renderplan.EffectMaterializationRequirement e) {
        if (e.automationBindings() == null || e.automationBindings().isEmpty()) {
            return "none";
        }
        return e.automationBindings().stream()
                .sorted(Comparator.comparing(b -> b.parameterKey() != null ? b.parameterKey() : ""))
                .map(b -> (b.parameterKey() != null ? b.parameterKey() : "null")
                        + "->" + (b.automationReference() != null ? b.automationReference() : "null"))
                .reduce((x, y) -> x + "," + y)
                .orElse("none");
    }

    private static String effectTarget(
            com.example.platform.render.domain.renderplan.EffectMaterializationRequirement e) {
        if (e.target() == null) {
            return "null";
        }
        if (e.target() instanceof com.example.platform.timeline.semantics.effect.ClipEffectTarget c) {
            return "CLIP|" + (c.trackId() != null ? c.trackId() : "null")
                    + "|" + (c.clipId() != null ? c.clipId() : "null");
        }
        return "TARGET|" + e.target().toString();
    }

    /** SourceArtifact: explicit artifactId + digest algorithm + digest value. */
    public static String sourceArtifact(RenderArtifactReference.SourceArtifact a) {
        if (a == null) {
            return "null";
        }
        return "SOURCE|" + a.artifactId().value()
                + "|" + (a.contentDigest() != null && a.contentDigest().algorithm() != null
                        ? a.contentDigest().algorithm().name() : "null")
                + "|" + (a.contentDigest() != null ? a.contentDigest().value() : "null");
    }

    /** IntermediateArtifactExpectation: explicit fields (logicalId + role). */
    public static String intermediateArtifact(RenderArtifactReference.IntermediateArtifactExpectation a) {
        Objects.requireNonNull(a, "a");
        return "INTERMEDIATE|" + (a.logicalId() != null ? a.logicalId().toString() : "null")
                + "|" + (a.role() != null ? a.role().name() : "null");
    }

    /** FinalArtifactExpectation: explicit field (role). */
    public static String finalArtifact(RenderArtifactReference.FinalArtifactExpectation a) {
        Objects.requireNonNull(a, "a");
        return "FINAL|" + (a.role() != null ? a.role().name() : "null");
    }

    /** Any artifact reference: explicit sealed-variant encoding. */
    public static String artifact(RenderArtifactReference a) {
        Objects.requireNonNull(a, "a");
        if (a instanceof RenderArtifactReference.SourceArtifact sa) {
            return sourceArtifact(sa);
        }
        if (a instanceof RenderArtifactReference.IntermediateArtifactExpectation ia) {
            return intermediateArtifact(ia);
        }
        if (a instanceof RenderArtifactReference.FinalArtifactExpectation fa) {
            return finalArtifact(fa);
        }
        throw new IllegalStateException("unknown RenderArtifactReference variant — FAIL CLOSED: " + a.getClass());
    }

    /**
     * RenderDependency: explicit sealed-variant encoding with exact semantic
     * payload — NEVER .toString() as the semantic contract. Unknown future
     * subtype fails closed.
     */
    public static String dependency(RenderDependency d) {
        if (d == null) {
            return "NONE"; // root source-artifact input without a producer edge
        }
        Objects.requireNonNull(d, "d");
        if (d instanceof RenderDependency.DecodedFrames) {
            return "DECODED_FRAMES";
        }
        if (d instanceof RenderDependency.EffectInput) {
            return "EFFECT_INPUT";
        }
        if (d instanceof RenderDependency.AudioInput ai) {
            // exact semantic payload: AudioMixInput trackId + clipId
            String trackId = ai.mixInput() != null ? ai.mixInput().trackId() : "null";
            String clipId = ai.mixInput() != null ? ai.mixInput().clipId() : "null";
            return "AUDIO_INPUT|" + (trackId != null ? trackId : "null")
                    + "|" + (clipId != null ? clipId : "null");
        }
        if (d instanceof RenderDependency.SubtitleRaster) {
            return "SUBTITLE_RASTER";
        }
        if (d instanceof RenderDependency.CompositeInput) {
            return "COMPOSITE_INPUT";
        }
        throw new IllegalStateException("unknown RenderDependency variant — FAIL CLOSED: " + d.getClass());
    }

    static String sorted(java.util.List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder())
                .reduce((x, y) -> x + "\n" + y).orElse("");
    }
}
