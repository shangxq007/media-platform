package com.example.platform.execution.planning;

import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 PhysicalExecutionPlanDigest (C16/C17).
 *
 * <p>Distinct layer digest from LogicalExecutionGraphDigest. Different layer
 * digests are NOT semantic-equivalence proof. Same frozen semantic input →
 * same physical plan content → same digest.
 */
public record PhysicalExecutionPlanDigest(String sha256Hex) {

    public PhysicalExecutionPlanDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static PhysicalExecutionPlanDigest compute(
            List<PhysicalPlanUnit> units,
            RenderPlanFingerprint planFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("PHYSICAL_EXECUTION_PLAN_V1\n");
        sb.append("planFingerprint=").append(planFingerprint.sha256Hex()).append('\n');
        var sorted = units.stream()
                .sorted(java.util.Comparator.comparing(PhysicalPlanUnit::planUnitId))
                .toList();
        for (var u : sorted) {
            sb.append("unit|").append(u.planUnitId())
                    .append('|').append(u.logicalNodeId())
                    .append('|').append(u.sourceRenderNodeId().value()).append('\n');
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
