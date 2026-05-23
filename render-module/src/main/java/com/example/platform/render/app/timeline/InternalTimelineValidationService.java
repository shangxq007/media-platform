package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Validates Internal Timeline Schema 1.0 documents.
 */
@Service
public class InternalTimelineValidationService {

    public TimelineValidationResult validate(JsonNode root) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (root == null || !root.isObject()) {
            return TimelineValidationResult.invalid(List.of("Timeline root must be a JSON object"));
        }

        if (!root.has("schemaVersion") || !root.get("schemaVersion").asText("").startsWith("1")) {
            errors.add("schemaVersion must be 1.0");
        }
        if (!root.has("id")) {
            errors.add("Missing required field: id");
        }
        if (!root.has("composition")) {
            errors.add("Missing required field: composition");
        } else {
            JsonNode tracks = root.path("composition").path("tracks");
            if (!tracks.isArray() || tracks.isEmpty()) {
                warnings.add("composition.tracks is empty");
            }
        }
        if (!root.has("project")) {
            warnings.add("Missing project block (recommended)");
        }
        if (!root.has("assetRegistry")) {
            warnings.add("Missing assetRegistry (recommended for incremental render)");
        }
        if (root.has("security")) {
            JsonNode sec = root.get("security");
            if (sec.has("sandboxPolicy") && sec.get("sandboxPolicy").asText("").isBlank()) {
                warnings.add("security.sandboxPolicy is empty");
            }
        }

        validateStableIds(root.path("composition").path("tracks"), "clip", errors);
        JsonNode layers = root.path("renderGraph").path("layers");
        if (layers.isArray()) {
            for (JsonNode layer : layers) {
                if (!layer.has("id") || layer.get("id").asText("").isBlank()) {
                    errors.add("renderGraph.layers entry missing id");
                }
            }
        }

        if (errors.isEmpty()) {
            return warnings.isEmpty()
                    ? TimelineValidationResult.ok()
                    : TimelineValidationResult.okWithWarnings(warnings);
        }
        return TimelineValidationResult.invalid(errors);
    }

    private void validateStableIds(JsonNode tracks, String childArray, List<String> errors) {
        if (!tracks.isArray()) {
            return;
        }
        for (JsonNode track : tracks) {
            if (!track.has("id")) {
                errors.add("Track missing stable id");
            }
            JsonNode clips = track.get(childArray);
            if (clips != null && clips.isArray()) {
                for (JsonNode clip : clips) {
                    if (!clip.has("id") || clip.get("id").asText("").isBlank()) {
                        errors.add("Clip missing stable id");
                    }
                }
            }
        }
    }
}
