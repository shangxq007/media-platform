package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.compile.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Compiles a NormalizedTimeline into an ArtifactDependencyGraph.
 *
 * <p>Provider-neutral, deterministic, and acyclic. The graph describes
 * what intermediate artifacts are needed to produce the final output,
 * and their dependency relationships.</p>
 *
 * <p>v0 supports:
 * <ul>
 *   <li>Single video clip → INPUT_MEDIA → TRIMMED_MEDIA → FINAL_ENCODE → FINAL_RENDER</li>
 *   <li>Single video clip + caption → adds SUBTITLE_OVERLAY node</li>
 *   <li>Sequential clips → multiple TRIMMED_MEDIA nodes feeding FINAL_ENCODE</li>
 *   <li>Audio tracks → AUDIO_MIX node</li>
 * </ul>
 */
@Service
public class ArtifactGraphCompiler {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGraphCompiler.class);

    /**
     * Compile a NormalizedTimeline into an ArtifactDependencyGraph.
     *
     * @param timeline the normalized timeline
     * @return the artifact dependency graph
     * @throws TimelineCompileException if the timeline cannot be compiled
     */
    public ArtifactDependencyGraph compile(NormalizedTimeline timeline) {
        if (timeline == null) {
            throw TimelineCompileException.missingField("NormalizedTimeline");
        }

        List<ArtifactNode> nodes = new ArrayList<>();
        List<ArtifactEdge> edges = new ArrayList<>();

        // 1. Create INPUT_MEDIA nodes for each unique asset
        Map<String, String> assetToInputNodeId = new LinkedHashMap<>();
        for (NormalizedAssetRef assetRef : timeline.allAssetRefs()) {
            String nodeId = deterministicId("input", assetRef.assetId());
            ArtifactNode inputNode = ArtifactNode.inputMedia(nodeId, assetRef.assetId(), null);
            nodes.add(inputNode);
            assetToInputNodeId.put(assetRef.assetId(), nodeId);
        }

        // 2. Create TRIMMED_MEDIA nodes for each clip
        Map<String, String> clipToTrimmedNodeId = new LinkedHashMap<>();
        for (NormalizedTrack track : timeline.tracks()) {
            for (NormalizedClip clip : track.clips()) {
                String assetId = clip.assetRef().assetId();
                String inputNodeId = assetToInputNodeId.get(assetId);
                if (inputNodeId == null) {
                    throw TimelineCompileException.invalidData("clip.assetRef",
                            "clip=" + clip.clipId() + " references unknown asset=" + assetId);
                }

                String trimmedNodeId = deterministicId("trim", clip.clipId());
                Map<String, String> params = new LinkedHashMap<>();
                params.put("clipId", clip.clipId());
                params.put("timelineStart", String.valueOf(clip.timelineStart()));
                params.put("assetInPoint", String.valueOf(clip.assetInPoint()));
                params.put("assetOutPoint", String.valueOf(clip.assetOutPoint()));
                params.put("clipDuration", String.valueOf(clip.clipDuration()));

                ArtifactNode trimmedNode = new ArtifactNode(
                        trimmedNodeId,
                        ArtifactNodeType.TRIMMED_MEDIA,
                        "trim:" + clip.clipId(),
                        assetId,
                        clip.clipId(),
                        track.trackId(),
                        Map.copyOf(params),
                        ArtifactRequirement.of("VIDEO_DECODE"));

                nodes.add(trimmedNode);
                clipToTrimmedNodeId.put(clip.clipId(), trimmedNodeId);

                // Edge: TRIMMED_MEDIA DERIVES_FROM INPUT_MEDIA
                edges.add(ArtifactEdge.derivesFrom(trimmedNodeId, inputNodeId));
            }
        }

        // 3. Create AUDIO_MIX node if audio tracks exist
        String audioMixNodeId = null;
        boolean hasAudio = timeline.tracks().stream()
                .anyMatch(t -> t.type() == NormalizedTrack.TrackType.AUDIO);
        if (hasAudio) {
            audioMixNodeId = deterministicId("audio", "mix");
            ArtifactNode audioNode = new ArtifactNode(
                    audioMixNodeId,
                    ArtifactNodeType.AUDIO_MIX,
                    "audio-mix",
                    null, null, null,
                    Map.of(),
                    ArtifactRequirement.of("AUDIO_DECODE"));
            nodes.add(audioNode);

            // Connect audio mix to all input media nodes
            for (String inputNodeId : assetToInputNodeId.values()) {
                edges.add(ArtifactEdge.requiresInput(audioMixNodeId, inputNodeId));
            }
        }

        // 4. Create SUBTITLE_OVERLAY node if captions exist
        String subtitleNodeId = null;
        if (timeline.hasCaptions()) {
            subtitleNodeId = deterministicId("sub", "overlay");
            Map<String, String> subParams = new LinkedHashMap<>();
            subParams.put("captionCount", String.valueOf(timeline.captionLayers().size()));
            ArtifactNode subtitleNode = new ArtifactNode(
                    subtitleNodeId,
                    ArtifactNodeType.SUBTITLE_OVERLAY,
                    "subtitle-overlay",
                    null, null, null,
                    Map.copyOf(subParams),
                    ArtifactRequirement.of("SUBTITLE_BURN_IN"));
            nodes.add(subtitleNode);

            // Connect subtitle to first video trimmed node
            if (!clipToTrimmedNodeId.isEmpty()) {
                String firstTrimmedId = clipToTrimmedNodeId.values().iterator().next();
                edges.add(ArtifactEdge.requiresInput(subtitleNodeId, firstTrimmedId));
            }
        }

        // 5. Create FINAL_ENCODE node
        String finalEncodeId = deterministicId("encode", timeline.timelineId());
        Map<String, String> encodeParams = new LinkedHashMap<>();
        encodeParams.put("format", timeline.outputProfile().format());
        encodeParams.put("resolution", timeline.outputProfile().resolution());
        encodeParams.put("videoCodec", timeline.outputProfile().videoCodec());
        encodeParams.put("audioCodec", timeline.outputProfile().audioCodec());

        ArtifactNode encodeNode = new ArtifactNode(
                finalEncodeId,
                ArtifactNodeType.FINAL_ENCODE,
                "final-encode",
                null, null, null,
                Map.copyOf(encodeParams),
                ArtifactRequirement.of(List.of("VIDEO_ENCODE", "AUDIO_ENCODE", "CONTAINER_MUX")));
        nodes.add(encodeNode);

        // Connect FINAL_ENCODE to all trimmed media nodes
        for (String trimmedId : clipToTrimmedNodeId.values()) {
            edges.add(ArtifactEdge.requiresInput(finalEncodeId, trimmedId));
        }

        // Connect FINAL_ENCODE to AUDIO_MIX if present
        if (audioMixNodeId != null) {
            edges.add(ArtifactEdge.requiresInput(finalEncodeId, audioMixNodeId));
        }

        // 6. Create FINAL_RENDER node
        String finalRenderId = deterministicId("render", timeline.timelineId());
        ArtifactNode renderNode = ArtifactNode.finalRender(finalRenderId);
        nodes.add(renderNode);

        // Connect FINAL_RENDER to FINAL_ENCODE
        edges.add(ArtifactEdge.produces(finalEncodeId, finalRenderId));

        // Connect FINAL_RENDER to SUBTITLE_OVERLAY if present
        if (subtitleNodeId != null) {
            edges.add(ArtifactEdge.requiresInput(finalRenderId, subtitleNodeId));
        }

        // 7. Build deterministic graph ID
        String graphId = computeGraphId(timeline.timelineId(), nodes);

        log.info("Artifact graph compiled: timelineId={} nodes={} edges={}",
                timeline.timelineId(), nodes.size(), edges.size());

        return new ArtifactDependencyGraph(graphId, timeline.timelineId(),
                List.copyOf(nodes), List.copyOf(edges));
    }

    /**
     * Compute a deterministic graph ID from timeline ID and node content.
     */
    private String computeGraphId(String timelineId, List<ArtifactNode> nodes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(timelineId.getBytes(StandardCharsets.UTF_8));
            for (ArtifactNode node : nodes) {
                md.update(node.nodeId().getBytes(StandardCharsets.UTF_8));
                md.update(node.type().name().getBytes(StandardCharsets.UTF_8));
            }
            return "ag-" + HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return deterministicId("graph", timelineId);
        }
    }

    /**
     * Generate a deterministic ID from prefix and content.
     * Uses SHA-256 hash of prefix+content to produce a stable, short ID.
     */
    private static String deterministicId(String prefix, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(prefix.getBytes(StandardCharsets.UTF_8));
            md.update(content.getBytes(StandardCharsets.UTF_8));
            return prefix + "-" + HexFormat.of().formatHex(md.digest()).substring(0, 12);
        } catch (Exception e) {
            // Fallback: should not happen
            return prefix + "-" + Math.abs(content.hashCode());
        }
    }
}
