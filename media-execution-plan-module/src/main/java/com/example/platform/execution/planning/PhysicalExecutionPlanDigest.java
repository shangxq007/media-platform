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
 * Roadmap #21 PhysicalExecutionPlanDigest (C16/C17, Blocker H).
 *
 * <p>Distinct layer digest from LogicalExecutionGraphDigest. Covers ALL
 * physical semantic content: unit identities (when stable), logical node
 * refs, typed source identity/kind/operation key, typed inputs (dependency
 * variant payloads + windows), typed output declarations, typed dependencies,
 * capability/execution intent refs, temporal windows, propagated extent and
 * deterministic cacheability metadata.
 *
 * <p>Provenance (plan id is identity, not digest; createdAt/correlation/trace)
 * excluded. Same frozen semantic input → same digest.
 *
 * <p>FAOF-1 law: law:physical-digest-content-complete.
 */
public record PhysicalExecutionPlanDigest(String sha256Hex) {

    public PhysicalExecutionPlanDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static PhysicalExecutionPlanDigest compute(
            List<PhysicalPlanUnit> units,
            RenderPlanFingerprint planFingerprint,
            RenderExtent propagatedExtent) {
        StringBuilder sb = new StringBuilder();
        sb.append("PHYSICAL_EXECUTION_PLAN_V1\n");
        sb.append("planFingerprint=").append(planFingerprint.sha256Hex()).append('\n');
        sb.append("extent=").append(LogicalExecutionGraphBuilder.canonicalExtent(propagatedExtent)).append('\n');
        var sorted = units.stream()
                .sorted(Comparator.comparing(PhysicalPlanUnit::planUnitId))
                .toList();
        for (var u : sorted) {
            sb.append("unit|").append(u.planUnitId())
                    .append('|').append(u.logicalNodeId())
                    .append('|').append(u.sourceRenderNodeId().value())
                    .append('|').append(u.sourceRenderNodeKind().toString())
                    .append('|').append(u.operationKey()).append('\n');
            for (var i : u.typedInputs()) {
                sb.append("in|").append(i.producerLogicalNodeId())
                        .append('|').append(i.dependencyVariant().toString())
                        .append('|').append(LogicalExecutionGraphBuilder.canonicalWindow(i.requiredSampleWindow()))
                        .append('\n');
            }
            for (var o : u.typedOutputs()) {
                for (var or : o.outputRequirements()) {
                    sb.append("out|").append(Canonical.output(or)).append('\n');
                }
                for (var mr : o.materializationRequirements()) {
                    sb.append("mat|").append(Canonical.materialization(mr)).append('\n');
                }
            }
            for (var d : u.typedDependencies()) {
                sb.append("dep|").append(d.producerLogicalNodeId())
                        .append('|').append(d.consumerLogicalNodeId())
                        .append('|').append(d.dependencyVariant().toString()).append('\n');
            }
            for (var cr : u.capabilityRequirementRefs()) {
                sb.append("cap|").append(Canonical.capability(cr.declaration())).append('\n');
            }
            for (var er : u.executionIntentRefs()) {
                sb.append("intent|").append(Canonical.executionIntent(er.declaration())).append('\n');
            }
            sb.append("window|")
                    .append(LogicalExecutionGraphBuilder.canonicalWindow(u.temporalWindow())).append('\n');
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
