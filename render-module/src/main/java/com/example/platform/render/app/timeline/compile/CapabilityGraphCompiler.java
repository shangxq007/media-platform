package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles an ArtifactDependencyGraph into a LogicalCapabilityGraph.
 *
 * <p>Provider-neutral: maps artifact nodes to capability requirements
 * without selecting any specific provider. The capability graph can
 * later feed into ProviderBindingPlan (future work).</p>
 *
 * <p>v0 capability mapping:
 * <ul>
 *   <li>INPUT_MEDIA → MEDIA_INPUT</li>
 *   <li>TRIMMED_MEDIA → VIDEO_DECODE + VIDEO_TRIM</li>
 *   <li>SUBTITLE_OVERLAY → SUBTITLE_BURN_IN</li>
 *   <li>AUDIO_MIX → AUDIO_DECODE + AUDIO_MIX</li>
 *   <li>FINAL_ENCODE → VIDEO_ENCODE + AUDIO_ENCODE + CONTAINER_MUX</li>
 *   <li>FINAL_RENDER → FINAL_RENDER / MEDIA_FILE_OUTPUT</li>
 * </ul>
 */
@Service
public class CapabilityGraphCompiler {

    private static final Logger log = LoggerFactory.getLogger(CapabilityGraphCompiler.class);

    /**
     * Compile an ArtifactDependencyGraph into a LogicalCapabilityGraph.
     *
     * @param artifactGraph the artifact dependency graph
     * @return the logical capability graph
     */
    public LogicalCapabilityGraph compile(ArtifactDependencyGraph artifactGraph) {
        if (artifactGraph == null) {
            throw TimelineCompileException.missingField("ArtifactDependencyGraph");
        }

        List<LogicalCapabilityNode> nodes = new ArrayList<>();
        List<LogicalCapabilityEdge> edges = new ArrayList<>();

        // Map each artifact node to a capability node
        for (ArtifactNode artifactNode : artifactGraph.nodes()) {
            ArtifactRequirement requirement = mapCapability(artifactNode);
            ArtifactNode enrichedNode = new ArtifactNode(
                    artifactNode.nodeId(),
                    artifactNode.type(),
                    artifactNode.label(),
                    artifactNode.sourceAssetId(),
                    artifactNode.clipId(),
                    artifactNode.trackId(),
                    artifactNode.parameters(),
                    requirement);
            nodes.add(LogicalCapabilityNode.fromArtifact(enrichedNode));
        }

        // Map edges
        for (ArtifactEdge artifactEdge : artifactGraph.edges()) {
            edges.add(LogicalCapabilityEdge.fromArtifactEdge(artifactEdge));
        }

        String graphId = computeGraphId(artifactGraph.graphId(), nodes);

        log.info("Capability graph compiled: timelineId={} nodes={} edges={}",
                artifactGraph.timelineId(), nodes.size(), edges.size());

        return new LogicalCapabilityGraph(graphId, artifactGraph.timelineId(),
                List.copyOf(nodes), List.copyOf(edges));
    }

    /**
     * Map an artifact node to its capability requirement.
     */
    private ArtifactRequirement mapCapability(ArtifactNode node) {
        return switch (node.type()) {
            case INPUT_MEDIA -> ArtifactRequirement.of("MEDIA_INPUT");
            case TRIMMED_MEDIA -> ArtifactRequirement.of(List.of("VIDEO_DECODE", "VIDEO_TRIM"));
            case SUBTITLE_OVERLAY -> ArtifactRequirement.of(List.of("SUBTITLE_BURN_IN", "FONT_RESOLUTION"));
            case AUDIO_MIX -> ArtifactRequirement.of(List.of("AUDIO_DECODE", "AUDIO_MIX"));
            case FINAL_ENCODE -> ArtifactRequirement.of(
                    List.of("VIDEO_ENCODE", "AUDIO_ENCODE", "CONTAINER_MUX"));
            case FINAL_RENDER -> ArtifactRequirement.of("MEDIA_FILE_OUTPUT");
        };
    }

    /**
     * Compute a deterministic graph ID.
     */
    private String computeGraphId(String artifactGraphId, List<LogicalCapabilityNode> nodes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(artifactGraphId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (LogicalCapabilityNode node : nodes) {
                md.update(node.nodeId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (node.requirement() != null) {
                    for (String cap : node.requirement().requiredCapabilities()) {
                        md.update(cap.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
            return "cg-" + java.util.HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return "cg-" + artifactGraphId;
        }
    }
}
