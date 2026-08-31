package com.example.platform.timeline.app;

import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Contract P serializer (PTCSG_REAL_RENDER_SUBTITLE_VERTICAL_SLICE_V1).
 *
 * <p>Serializes the ORIGINAL {@link TimelineDocument} to the governed snapshot-payload
 * JSON using the exact Jackson configuration of {@code TimelineContentDigester}
 * (JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS disabled) so the payload base JSON is
 * digest-equivalent to the original document.</p>
 *
 * <p>Governed caption expansion (PTADTF-C contract-p-representation-authority): when
 * {@link TimelineMetadata} properties contain the frozen key {@value #CAPTIONS_V1_METADATA_KEY}
 * (a JSON array of {@code {id, text, startMs, durationMs[, styleRef]}} cue objects), the
 * payload JSON additionally carries a {@code textOverlays} array in the shape already
 * consumed by {@code TimelineScriptParser} / the render caption pipeline. No
 * TimelineDocument model change, no parser change, no new grammar.</p>
 *
 * <p>Pure and deterministic: no repository, network, current-time, or randomness access.</p>
 */
public final class TimelineDocumentJsonSerializer {

    /** Frozen governed caption metadata key (PTADTF-C contract-p-representation-authority). */
    public static final String CAPTIONS_V1_METADATA_KEY = "captions.v1";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private TimelineDocumentJsonSerializer() {
    }

    /**
     * Serialize the original document (digest-equivalent base) and append the governed
     * caption expansion when present.
     */
    public static String serializeWithCaptions(TimelineDocument document) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.valueToTree(document);
            ArrayNode textOverlays = expandCaptions(document);
            if (textOverlays != null) {
                root.set("textOverlays", textOverlays);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize timeline document snapshot payload", e);
        }
    }

    /** Serialize the original document exactly (no caption expansion). */
    public static String serialize(TimelineDocument document) {
        try {
            return MAPPER.writeValueAsString(document);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize timeline document", e);
        }
    }

    /**
     * Decode a persisted canonical Timeline payload through the sole production
     * reader authority. Governed render projections (for example textOverlays)
     * are ignored because they are derived from the TimelineDocument itself.
     */
    public static TimelineDocument deserialize(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("timeline payload must not be blank");
        }
        try {
            return MAPPER.readValue(payloadJson, TimelineDocument.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize canonical TimelineDocument", e);
        }
    }

    /** The Jackson mapper used for the governed payload representation (digest-equivalent config). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ArrayNode expandCaptions(TimelineDocument document) {
        if (document == null || document.getMetadata() == null || document.getMetadata().properties() == null) {
            return null;
        }
        String captions = document.getMetadata().properties().get(CAPTIONS_V1_METADATA_KEY);
        if (captions == null || captions.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(captions);
            if (!node.isArray()) {
                return null;
            }
            ArrayNode out = MAPPER.createArrayNode();
            for (JsonNode cue : node) {
                ObjectNode c = MAPPER.createObjectNode();
                c.put("id", cue.path("id").asText(""));
                c.put("text", cue.path("text").asText(""));
                c.put("startMs", cue.path("startMs").asLong(0L));
                c.put("durationMs", cue.path("durationMs").asLong(0L));
                out.add(c);
            }
            return out;
        } catch (Exception e) {
            // Malformed governed caption metadata: the document still persists unchanged;
            // no textOverlays expansion is emitted.
            return null;
        }
    }
}
