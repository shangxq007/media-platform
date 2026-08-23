package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlanDigest (C16/C17) — end-to-end injective
 * canonical encoding via {@link CanonicalWriter} (Correction 5 B2).
 *
 * <p>Full-stream framing; actual formatVersion + schemaVersion.value +
 * planFingerprint + plan-level extent + per-unit semantic fields (typed
 * inputs/outputs/dependencies, temporal window, coverage, per-unit
 * propagatedExtent, requirement refs, cacheability). ExecutionPlanId and
 * provenance EXCLUDED (identity/provenance, never semantic).
 */
public record PhysicalExecutionPlanDigest(String sha256Hex) {

    public PhysicalExecutionPlanDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static PhysicalExecutionPlanDigest compute(
            String formatVersion,
            ExecutionPlanSchemaVersion schemaVersion,
            List<PhysicalPlanUnit> units,
            RenderPlanFingerprint planFingerprint,
            RenderExtent propagatedExtent) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("PHYSICAL_EXECUTION_PLAN_V1");
        w.field("formatVersion", formatVersion);
        w.field("schemaVersion", Integer.toString(schemaVersion.value()));
        w.field("planFingerprint", planFingerprint.sha256Hex());
        w.field("planExtent", LogicalExecutionGraphBuilder.canonicalExtent(propagatedExtent));
        // units: deterministic order by step identity (structural partition —
        // unit order is NOT semantic)
        List<PhysicalPlanUnit> sortedUnits = new ArrayList<>(units);
        sortedUnits.sort(Comparator.comparing(u -> u.stepId().value()));
        List<String> unitCanonicals = new ArrayList<>();
        for (var u : sortedUnits) {
            CanonicalWriter uw = new CanonicalWriter();
            uw.tag("UNIT");
            uw.field("stepId", u.stepId().value());
            uw.field("logicalNodeId", u.logicalNodeId());
            uw.field("sourceRenderNodeId", u.sourceRenderNodeId().value());
            uw.field("renderNodeKind", Canonical.renderNodeKind(u.sourceRenderNodeKind()));
            uw.field("operationKey", u.operationKey());
            // inputs: NON_SEMANTIC order — upstream #20 codec sorts artifact
            // references (sortedEncodings) that project into input bindings
            // ORDER_SEMANTICS=NON_SEMANTIC_CANONICAL_SORT
            List<String> inputCanonicals = new ArrayList<>();
            for (var i : u.typedInputs()) {
                CanonicalWriter iw = new CanonicalWriter();
                iw.tag("INPUT");
                iw.field("inputId", i.inputId().value());
                iw.field("consumer", i.consumerLogicalNodeId());
                iw.field("producer", i.producerLogicalNodeId());
                iw.field("dependency", Canonical.dependency(i.dependencyVariant()));
                iw.optional(i.sourceArtifact() != null, Canonical.sourceArtifact(i.sourceArtifact()));
                iw.optional(i.requiredSampleWindow() != null,
                        LogicalExecutionGraphBuilder.canonicalWindow(i.requiredSampleWindow()));
                inputCanonicals.add(iw.build());
            }
            uw.list(CanonicalWriter.sorted(inputCanonicals));
            // outputs: NON_SEMANTIC order — upstream #20 codec sorts output
            // requirements (sortedEncodings) projecting into output declarations
            // ORDER_SEMANTICS=NON_SEMANTIC_CANONICAL_SORT
            List<String> outputCanonicals = new ArrayList<>();
            for (var o : u.typedOutputs()) {
                CanonicalWriter ow = new CanonicalWriter();
                ow.tag("OUTPUT");
                ow.field("outputId", o.outputId().value());
                ow.field("logicalNodeId", o.logicalNodeId());
                // C7-C: nested output-declaration collections follow #20
                // NON_SEMANTIC order (codec sorts artifact/output/materialization
                // encodings; derived intermediate/final expectations must not
                // reintroduce insertion-order semantics)
                ow.list(CanonicalWriter.sorted(
                        o.outputRequirements().stream().map(Canonical::outputRequirement).toList()));
                ow.list(CanonicalWriter.sorted(
                        o.materializationRequirements().stream().map(Canonical::materialization).toList()));
                ow.list(CanonicalWriter.sorted(
                        o.intermediateArtifactExpectations().stream()
                                .map(Canonical::intermediateArtifact).toList()));
                ow.list(CanonicalWriter.sorted(
                        o.finalArtifactExpectations().stream().map(Canonical::finalArtifact).toList()));
                outputCanonicals.add(ow.build());
            }
            uw.list(CanonicalWriter.sorted(outputCanonicals));
            // dependencies: NON_SEMANTIC order — logical edge collection is
            // canonical-sorted (C6-A; upstream #20 codec sorts edge encodings)
            // ORDER_SEMANTICS=NON_SEMANTIC_CANONICAL_SORT
            List<String> depCanonicals = new ArrayList<>();
            for (var d : u.typedDependencies()) {
                CanonicalWriter dw = new CanonicalWriter();
                dw.tag("DEP");
                dw.field("edgeId", d.edgeId().value());
                dw.field("producer", d.producerLogicalNodeId());
                dw.field("consumer", d.consumerLogicalNodeId());
                dw.field("dependency", Canonical.dependency(d.dependencyVariant()));
                depCanonicals.add(dw.build());
            }
            uw.list(CanonicalWriter.sorted(depCanonicals));
            uw.optional(u.temporalWindow() != null,
                    LogicalExecutionGraphBuilder.canonicalWindow(u.temporalWindow()));
            uw.optional(u.executionCoverage() != null,
                    LogicalExecutionGraphBuilder.canonicalCoverage(u.executionCoverage()));
            uw.field("unitExtent", LogicalExecutionGraphBuilder.canonicalExtent(u.propagatedExtent()));
            // C7-C: unit requirement refs follow #20 NON_SEMANTIC order
            uw.list(CanonicalWriter.sorted(u.capabilityRequirementRefs().stream()
                    .map(c -> Canonical.capability(c.declaration())).toList()));
            uw.list(CanonicalWriter.sorted(u.executionIntentRefs().stream()
                    .map(e -> Canonical.executionIntent(e.declaration())).toList()));
            uw.field("cacheable", Boolean.toString(u.deterministicallyCacheable()));
            unitCanonicals.add(uw.build());
        }
        w.list(unitCanonicals);
        return new PhysicalExecutionPlanDigest(sha256(w.build()));
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
