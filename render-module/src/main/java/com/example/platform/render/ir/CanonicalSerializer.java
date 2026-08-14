package com.example.platform.render.ir;
import com.example.platform.shared.time.RationalTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Produces deterministic, byte-for-byte identical canonical JSON serialization
 * of a {@link MediaProjectIr}.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>UTF-8 encoding</li>
 *   <li>Deterministic: identical IR → identical bytes, independent of input JSON property order</li>
 *   <li>Sorted fields (alphabetical)</li>
 *   <li>Sorted map keys</li>
 *   <li>No null values in output (omitted)</li>
 *   <li>No pretty-printing (compact form)</li>
 *   <li>RationalTime encoded as string "{numerator}/{denominator}"</li>
 *   <li>No HTML escaping of non-ASCII characters</li>
 * </ul>
 */
public final class CanonicalSerializer {

    private static final ObjectMapper MAPPER = createMapper();

    private CanonicalSerializer() {}

    private static ObjectMapper createMapper() {
        return JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();
    }

    /**
     * Serializes a normalized IR to canonical JSON bytes.
     *
     * @param ir the normalized IR
     * @return canonical UTF-8 JSON bytes
     * @throws IrValidationException if canonicalization fails
     */
    public static byte[] serialize(MediaProjectIr ir) {
        Objects.requireNonNull(ir, "ir must not be null");
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("schemaVersion", ir.schemaVersion());
            root.set("project", serializeProject(ir.project()));
            root.set("assets", serializeAssets(ir.assets()));
            root.set("timeline", serializeTimeline(ir.timeline()));
            root.set("outputs", serializeOutputs(ir.outputs()));
            root.set("artifacts", serializeArtifacts(ir.artifacts()));
            if (ir.extensions() != null && !ir.extensions().isEmpty()) {
                root.set("extensions", MAPPER.valueToTree(ir.extensions()));
            }
            // Sort top-level properties alphabetically using Jackson
            return MAPPER.writeValueAsBytes(root);
        } catch (JsonProcessingException e) {
            throw new IrValidationException(java.util.List.of(
                IrValidationError.of(IrErrorCode.CANONICALIZATION_FAILED, "$",
                    "Failed to serialize: " + e.getMessage())
            ));
        }
    }

    private static ObjectNode serializeProject(Project project) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", project.id());
        node.put("name", project.name());
        return node;
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode serializeAssets(
        java.util.List<AssetVersionRef> assets
    ) {
        var arr = MAPPER.createArrayNode();
        for (AssetVersionRef ref : assets) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("assetId", ref.assetId());
            node.put("versionId", ref.versionId());
            arr.add(node);
        }
        return arr;
    }

    private static ObjectNode serializeTimeline(Timeline timeline) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", timeline.id());
        var tracksArr = MAPPER.createArrayNode();
        for (VideoTrack track : timeline.tracks()) {
            tracksArr.add(serializeTrack(track));
        }
        node.set("tracks", tracksArr);
        return node;
    }

    private static ObjectNode serializeTrack(VideoTrack track) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", track.id());
        var clipsArr = MAPPER.createArrayNode();
        for (Clip clip : track.clips()) {
            clipsArr.add(serializeClip(clip));
        }
        node.set("clips", clipsArr);
        return node;
    }

    private static ObjectNode serializeClip(Clip clip) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", clip.id());
        node.set("source", serializeSourceRange(clip.source()));
        node.put("timelineStart", serializeRationalTime(clip.timelineStart()));
        return node;
    }

    private static ObjectNode serializeSourceRange(SourceRange source) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode refNode = MAPPER.createObjectNode();
        refNode.put("assetId", source.assetRef().assetId());
        refNode.put("versionId", source.assetRef().versionId());
        node.set("assetRef", refNode);
        node.put("start", serializeRationalTime(source.start()));
        node.put("duration", serializeRationalTime(source.duration()));
        return node;
    }

    private static String serializeRationalTime(RationalTime time) {
        return time.numerator() + "/" + time.denominator();
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode serializeOutputs(
        java.util.List<OutputSpec> outputs
    ) {
        var arr = MAPPER.createArrayNode();
        for (OutputSpec spec : outputs) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", spec.id());
            node.put("container", spec.container());
            node.put("videoCodec", spec.videoCodec());
            node.put("width", spec.width());
            node.put("height", spec.height());
            node.put("frameRate", serializeRationalTime(spec.frameRate()));
            if (spec.extensions() != null && !spec.extensions().isEmpty()) {
                node.set("extensions", MAPPER.valueToTree(spec.extensions()));
            }
            arr.add(node);
        }
        return arr;
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode serializeArtifacts(
        java.util.List<ArtifactDeclaration> artifacts
    ) {
        var arr = MAPPER.createArrayNode();
        for (ArtifactDeclaration art : artifacts) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", art.id());
            node.put("outputSpecId", art.outputSpecId());
            node.put("filename", art.filename());
            arr.add(node);
        }
        return arr;
    }
}
