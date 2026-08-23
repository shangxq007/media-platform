package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Edge-local, deterministic identity over complete typed logical-edge semantics. */
final class LogicalEdgeIdentity {

    private LogicalEdgeIdentity() {
    }

    static ExecutionEdgeId derive(
            RenderNodeId producerRenderNodeId,
            RenderNodeId consumerRenderNodeId,
            RenderDependency dependency) {
        Objects.requireNonNull(producerRenderNodeId, "producerRenderNodeId");
        Objects.requireNonNull(consumerRenderNodeId, "consumerRenderNodeId");
        Objects.requireNonNull(dependency, "dependency");

        CanonicalWriter writer = new CanonicalWriter();
        writer.tag("LOGICAL_EDGE_IDENTITY_V1");
        writer.field("producerRenderNodeId", producerRenderNodeId.value());
        writer.field("consumerRenderNodeId", consumerRenderNodeId.value());
        writer.field("dependency", Canonical.dependency(dependency));
        return new ExecutionEdgeId("le-" + sha256(writer.build()));
    }

    private static String sha256(String canonicalIdentity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalIdentity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
