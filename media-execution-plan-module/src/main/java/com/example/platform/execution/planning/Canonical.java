package com.example.platform.execution.planning;

import com.example.platform.extension.domain.CapabilityRequirement;
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
 * encoding; deterministic collection ordering where order is non-semantic;
 * preserved positional order where order IS semantic; unknown future variants
 * FAIL CLOSED; NO Object.toString() semantic authority; no locale-dependent
 * formatting; no object identity/hashCode. CapabilityRequirement.toString()
 * omits alternatives — explicit field encoding is mandatory.
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

    public static String contractRange(com.example.platform.extension.domain.ContractVersionRange range) {
        if (range == null) {
            return "any";
        }
        return range.toString(); // version-range encoding (semantic, stable)
    }

    /** RenderExecutionRequirement: gpu | determinism | sandboxed. */
    public static String executionIntent(RenderExecutionRequirement er) {
        return (er.gpu() != null ? er.gpu().name() : "null")
                + "|" + (er.determinism() != null ? er.determinism().name() : "null")
                + "|" + er.sandboxedIntent();
    }

    /** RenderOutputRequirement: role + color/raster presence (full semantics). */
    public static String outputRequirement(RenderOutputRequirement o) {
        Objects.requireNonNull(o, "o");
        return (o.role() != null ? o.role().name() : "null")
                + "|" + (o.colorDescription() != null
                        ? (o.colorDescription().isEmpty() ? "empty" : "present") : "null")
                + "|" + (o.rasterSample() != null
                        ? (o.rasterSample().isEmpty() ? "empty" : "present") : "null");
    }

    /** RenderMaterializationRequirement: explicit variant tag + full payload. */
    public static String materialization(RenderMaterializationRequirement m) {
        Objects.requireNonNull(m, "m");
        return variantTag(m) + "[" + m.toString() + "]";
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
            // exact semantic payload: AudioMixInput (sealed typed value)
            return "AUDIO_INPUT|" + (ai.mixInput() != null ? ai.mixInput().toString() : "null");
        }
        if (d instanceof RenderDependency.SubtitleRaster) {
            return "SUBTITLE_RASTER";
        }
        if (d instanceof RenderDependency.CompositeInput) {
            return "COMPOSITE_INPUT";
        }
        throw new IllegalStateException("unknown RenderDependency variant — FAIL CLOSED: " + d.getClass());
    }

    private static String variantTag(Object o) {
        String n = o.getClass().getSimpleName();
        // strip trailing "MaterializationRequirement"
        return n.endsWith("MaterializationRequirement")
                ? n.substring(0, n.length() - "MaterializationRequirement".length()).toUpperCase()
                : n.toUpperCase();
    }

    static String sorted(java.util.List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder())
                .reduce((x, y) -> x + "\n" + y).orElse("");
    }
}
