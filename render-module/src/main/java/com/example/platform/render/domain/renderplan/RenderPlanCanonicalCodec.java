package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.FrameRate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * Participants: formatVersion, revision.revisionId, revision.contentDigest,
     * request.extent, request.outputs, sorted nodes, sorted edges.
     * EXCLUDED: request.id, plan id, status, diagnostics, resolution state,
     * capability context, provenance, execution requirements, timestamps,
     * provider/worker/device anything.
     */
    public String planFingerprintCanonical(RenderPlan plan) {
        return planFingerprintCanonical(plan.revision(), plan.request(), plan.nodes(), plan.edges());
    }

    /**
     * Component-based variant (C7): computes the plan fingerprint canonical string
     * directly from the plan's ingredients, without requiring a constructed
     * RenderPlan. Used by the planner, which must compute the fingerprint before
     * assembling the plan record (the record requires the fingerprint field).
     */
    public String planFingerprintCanonical(
            TimelineRevisionReference revision,
            RenderRequest request,
            List<RenderNode> nodes,
            List<RenderDependencyEdge> edges) {
        StringBuilder sb = new StringBuilder();
        s(sb, PLAN_FORMAT_VERSION);
        s(sb, revision.revisionId());
        s(sb, contentDigestCanonical(revision.contentDigest()));
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
            List<RenderCapabilityRequirement> capabilityRequirements,
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
        // fingerprint (C14/C8 participation). The referenced color-image value
        // types are immutable records/final classes with deterministic toString.
        optional(sb, output.colorDescription().map(Object::toString));
        optional(sb, output.rasterSample().map(Object::toString));
        return sb.toString();
    }

    private String artifactReferenceCanonical(RenderArtifactReference ref) {
        return ref.variantKey();
    }

    private String capabilityRequirementCanonical(RenderCapabilityRequirement req) {
        return req.capabilityId().name();
    }

    private String mediaTimeCanonical(MediaTime time) {
        // MediaTime.toString() IS canonical: "ticks/timeScale" or "0"
        return time.toString();
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
