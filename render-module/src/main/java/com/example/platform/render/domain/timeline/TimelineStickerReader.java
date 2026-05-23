package com.example.platform.render.domain.timeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TimelineStickerReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String META_STICKERS = "platform.stickers";

    public List<TimelineSticker> fromSpec(TimelineSpec spec) {
        if (spec == null || spec.metadata() == null) {
            return List.of();
        }
        String json = spec.metadata().get(META_STICKERS);
        return parseJson(json);
    }

    public List<TimelineSticker> fromJsonRoot(JsonNode root) {
        if (root != null && root.has("stickers") && root.get("stickers").isArray()) {
            try {
                return MAPPER.convertValue(root.get("stickers"), new TypeReference<List<TimelineSticker>>() {});
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    public List<TimelineSticker> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<TimelineSticker>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public boolean hasStickers(TimelineSpec spec) {
        return !fromSpec(spec).isEmpty();
    }

    public boolean hasStickerEffects(TimelineSpec spec) {
        if (spec.tracks() == null) {
            return false;
        }
        return spec.tracks().stream()
                .flatMap(t -> t.clips().stream())
                .flatMap(c -> c.effects().stream())
                .anyMatch(e -> e.effectKey() != null && e.effectKey().startsWith("video.sticker_"));
    }

    public boolean requiresSkiaOverlay(TimelineSpec spec) {
        return hasStickers(spec) || hasStickerEffects(spec);
    }
}
