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
 * Roadmap #21 explicit canonical encoder (Correction 4, B3).
 *
 * <p>EVERY scalar is LENGTH-PREFIXED ({@code len:value}) so arbitrary
 * supported strings (parameter values, automation references, ids) can never
 * collide with framing — no delimiter-only concatenation for free strings.
 * Fixed field order; explicit variant tags; explicit scalar encoding
 * (full semantic values); deterministic collection ordering where order is
 * non-semantic; FAIL CLOSED on unknown variants; NO Object.toString()
 * semantic authority.
 */
public final class Canonical {

    private Canonical() {
    }

    // ------------------------------------------------------------------
    // Length-prefixed scalar framing (delimiter-collision-safe)
    // ------------------------------------------------------------------

    /** Frame an arbitrary string: {@code len:value} — unambiguous, injective. */
    public static String framed(String value) {
        String v = value == null ? "" : value;
        return v.length() + ":" + v;
    }

    // ------------------------------------------------------------------
    // Capability / contract
    // ------------------------------------------------------------------

    /** CapabilityRequirement: id | required | range | sorted alternatives. */
    public static String capability(CapabilityRequirement cr) {
        Objects.requireNonNull(cr, "cr");
        StringBuilder sb = new StringBuilder();
        sb.append(framed(cr.capabilityId().value()))
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
                sb.append(framed(alts.get(i)));
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

    // ------------------------------------------------------------------
    // Output requirement / color / raster (full values)
    // ------------------------------------------------------------------

    public static String outputRequirement(RenderOutputRequirement o) {
        Objects.requireNonNull(o, "o");
        StringBuilder sb = new StringBuilder();
        sb.append(o.role() != null ? o.role().name() : "null");
        sb.append('|');
        sb.append(o.colorDescription() != null
                ? colorDescription(o.colorDescription().orElse(null)) : "null");
        sb.append('|');
        sb.append(o.rasterSample() != null
                ? rasterSample(o.rasterSample().orElse(null)) : "null");
        return sb.toString();
    }

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

    public static String primaries(com.example.platform.colorimage.ColorPrimaries cp) {
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

    public static String chromaticity(com.example.platform.colorimage.Chromaticity ch) {
        if (ch == null) {
            return "null";
        }
        return framed(rational(ch.x())) + "," + framed(rational(ch.y()));
    }

    private static String rational(com.example.platform.colorimage.Rational r) {
        if (r == null) {
            return "null";
        }
        return r.numerator() + "/" + r.denominator();
    }

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

    // ------------------------------------------------------------------
    // Materialization — explicit sealed variants, full payload
    // ------------------------------------------------------------------

    private static final com.example.platform.render.domain.renderplan.RenderPlanCanonicalCodec CODEC =
            com.example.platform.render.domain.renderplan.RenderPlanFingerprintCalculator.codec();

    /**
     * Materialization canonical encoding — DELEGATED to the #20 authoritative
     * RenderPlanCanonicalCodec (Correction 4 B3): length-prefixed scalar
     * framing, explicit sealed-variant full payload (Effect / AudioProcess /
     * TimedText), FAIL CLOSED on unknown variants. #21 does not reimplement
     * #20 semantic encoding; the #20 codec remains the single canonical owner.
     */
    public static String materialization(RenderMaterializationRequirement m) {
        Objects.requireNonNull(m, "m");
        return CODEC.materializationRequirementCanonicalPublic(m);
    }

                                                // ------------------------------------------------------------------
    // Artifacts — explicit sealed variants
    // ------------------------------------------------------------------

    public static String sourceArtifact(RenderArtifactReference.SourceArtifact a) {
        if (a == null) {
            return "null";
        }
        return "SOURCE|" + framed(a.artifactId().value())
                + "|" + (a.contentDigest() != null && a.contentDigest().algorithm() != null
                        ? a.contentDigest().algorithm().name() : "null")
                + "|" + (a.contentDigest() != null ? framed(a.contentDigest().value()) : "null");
    }

    public static String intermediateArtifact(RenderArtifactReference.IntermediateArtifactExpectation a) {
        Objects.requireNonNull(a, "a");
        return "INTERMEDIATE|" + framed(a.logicalId() != null ? a.logicalId().value() : "null")
                + "|" + (a.role() != null ? a.role().name() : "null");
    }

    public static String finalArtifact(RenderArtifactReference.FinalArtifactExpectation a) {
        Objects.requireNonNull(a, "a");
        return "FINAL|" + (a.role() != null ? a.role().name() : "null");
    }

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

    // ------------------------------------------------------------------
    // Dependencies — explicit sealed variants
    // ------------------------------------------------------------------

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
            String trackId = ai.mixInput() != null ? ai.mixInput().trackId() : "null";
            String clipId = ai.mixInput() != null ? ai.mixInput().clipId() : "null";
            return "AUDIO_INPUT|" + framed(trackId) + "|" + framed(clipId);
        }
        if (d instanceof RenderDependency.SubtitleRaster) {
            return "SUBTITLE_RASTER";
        }
        if (d instanceof RenderDependency.CompositeInput) {
            return "COMPOSITE_INPUT";
        }
        throw new IllegalStateException("unknown RenderDependency variant — FAIL CLOSED: " + d.getClass());
    }

    // ------------------------------------------------------------------
    // Node identity — explicit (no record toString)
    // ------------------------------------------------------------------

    /** RenderNodeKind: explicit sealed variant tag. */
    public static String renderNodeKind(com.example.platform.render.domain.renderplan.RenderNodeKind k) {
        if (k == null) {
            return "null";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.Decode) {
            return "DECODE";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.Effect) {
            return "EFFECT";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.AudioProcess) {
            return "AUDIO_PROCESS";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.AudioMix) {
            return "AUDIO_MIX";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.TimedText) {
            return "TIMED_TEXT";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.Composite) {
            return "COMPOSITE";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.Output) {
            return "OUTPUT";
        }
        if (k instanceof com.example.platform.render.domain.renderplan.RenderNodeKind.Source) {
            return "SOURCE";
        }
        throw new IllegalStateException("unknown RenderNodeKind variant — FAIL CLOSED: " + k.getClass());
    }

    /** RenderComponentPath: explicit kind + ordered segments. */
    public static String componentPath(com.example.platform.render.domain.renderplan.RenderComponentPath p) {
        if (p == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(p.kind() != null ? p.kind().name() : "null");
        if (p.segments() != null) {
            for (String s : p.segments()) {
                sb.append('|').append(framed(s));
            }
        }
        return sb.toString();
    }

    static String sorted(java.util.List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder())
                .reduce((x, y) -> x + "\n" + y).orElse("");
    }
}
