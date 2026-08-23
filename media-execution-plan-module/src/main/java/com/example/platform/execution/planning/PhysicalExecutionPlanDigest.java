package com.example.platform.execution.planning;

import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlanDigest (C16/C17, Blocker H/B4).
 *
 * <p>Distinct layer digest from LogicalExecutionGraphDigest. Covers ALL
 * physical semantic content via the explicit {@link Canonical} encoder:
 * unit stable typed identity, logical refs, typed source identity/kind/op
 * key, typed inputs (ExecutionInputId + artifact refs + dependency payloads
 * + windows), typed outputs (ExecutionOutputId + requirements + artifact
 * expectations), typed dependencies, capability/execution intent refs,
 * temporal windows, coverage, propagated extent and cacheability metadata.
 *
 * <p>ExecutionPlanId / createdAt / correlation / trace excluded. Same frozen
 * semantic input → same digest.
 */
public record PhysicalExecutionPlanDigest(String sha256Hex) {

    public PhysicalExecutionPlanDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static PhysicalExecutionPlanDigest compute(
            String formatVersion,
            com.example.platform.execution.domain.ExecutionPlanSchemaVersion schemaVersion,
            List<PhysicalPlanUnit> units,
            RenderPlanFingerprint planFingerprint,
            RenderExtent propagatedExtent) {
        StringBuilder sb = new StringBuilder();
        sb.append("PHYSICAL_EXECUTION_PLAN_V1\n");
        sb.append("formatVersion=").append(formatVersion).append('\n');
        sb.append("schemaVersion=").append(schemaVersion.value()).append('\n');
        sb.append("planFingerprint=").append(planFingerprint.sha256Hex()).append('\n');
        sb.append("extent=").append(LogicalExecutionGraphBuilder.canonicalExtent(propagatedExtent)).append('\n');
        var sorted = units.stream()
                .sorted(Comparator.comparing(u -> u.stepId().value()))
                .toList();
        for (var u : sorted) {
            sb.append("unit|").append(u.stepId().value())
                    .append('|').append(u.logicalNodeId())
                    .append('|').append(u.sourceRenderNodeId().value())
                    .append('|').append(Canonical.renderNodeKind(u.sourceRenderNodeKind()))
                    .append('|').append(u.operationKey()).append('\n');
            for (var i : u.typedInputs()) {
                sb.append("in|").append(i.inputId().value())
                        .append('|').append(i.producerLogicalNodeId())
                        .append('|').append(Canonical.dependency(i.dependencyVariant()))
                        .append('|').append(Canonical.sourceArtifact(i.sourceArtifact()))
                        .append('|')
                        .append(LogicalExecutionGraphBuilder.canonicalWindow(i.requiredSampleWindow()))
                        .append('\n');
            }
            for (var o : u.typedOutputs()) {
                sb.append("out|").append(o.outputId().value()).append('\n');
                for (var or : o.outputRequirements()) {
                    sb.append("outreq|").append(Canonical.outputRequirement(or)).append('\n');
                }
                for (var mr : o.materializationRequirements()) {
                    sb.append("mat|").append(Canonical.materialization(mr)).append('\n');
                }
                for (var ia : o.intermediateArtifactExpectations()) {
                    sb.append("interm|").append(Canonical.intermediateArtifact(ia)).append('\n');
                }
                for (var fa : o.finalArtifactExpectations()) {
                    sb.append("final|").append(Canonical.finalArtifact(fa)).append('\n');
                }
            }
            for (var d : u.typedDependencies()) {
                sb.append("dep|").append(d.producerLogicalNodeId())
                        .append('|').append(d.consumerLogicalNodeId())
                        .append('|').append(Canonical.dependency(d.dependencyVariant())).append('\n');
            }
            for (var cr : u.capabilityRequirementRefs()) {
                sb.append("cap|").append(Canonical.capability(cr.declaration())).append('\n');
            }
            for (var er : u.executionIntentRefs()) {
                sb.append("intent|").append(Canonical.executionIntent(er.declaration())).append('\n');
            }
            sb.append("window|")
                    .append(LogicalExecutionGraphBuilder.canonicalWindow(u.temporalWindow())).append('\n');
            sb.append("coverage|")
                    .append(LogicalExecutionGraphBuilder.canonicalCoverage(u.executionCoverage())).append('\n');
            sb.append("unitExtent|")
                    .append(LogicalExecutionGraphBuilder.canonicalExtent(u.propagatedExtent())).append('\n');
            sb.append("cacheable|").append(u.deterministicallyCacheable()).append('\n');
        }
        return new PhysicalExecutionPlanDigest(sha256(sb.toString()));
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
