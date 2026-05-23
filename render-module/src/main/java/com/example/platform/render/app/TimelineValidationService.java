package com.example.platform.render.app;

import com.example.platform.render.app.timeline.InternalTimelineJson;
import com.example.platform.render.app.timeline.InternalTimelineValidationService;
import com.example.platform.render.domain.timeline.TimelineValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Validates Internal Timeline Schema 1.0 JSON.
 */
@Service
public class TimelineValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InternalTimelineValidationService internalTimelineValidationService;

    public TimelineValidationService(InternalTimelineValidationService internalTimelineValidationService) {
        this.internalTimelineValidationService = internalTimelineValidationService;
    }

    public TimelineValidationResult validateJson(String timelineJson) {
        List<String> errors = new ArrayList<>();

        if (timelineJson == null || timelineJson.isBlank()) {
            return TimelineValidationResult.invalid(List.of("Timeline JSON is empty"));
        }
        if (!timelineJson.trim().startsWith("{")) {
            return TimelineValidationResult.invalid(List.of("Timeline must be a JSON object"));
        }

        try {
            JsonNode root = MAPPER.readTree(timelineJson);
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return TimelineValidationResult.invalid(List.of(
                        "Internal Timeline 1.0 required: schemaVersion 1.x and composition block. "
                                + "Use POST import_otio / import_edl to convert exchange formats."));
            }
            TimelineValidationResult internal = internalTimelineValidationService.validate(root);
            errors.addAll(internal.errors());
            if (!errors.isEmpty()) {
                return TimelineValidationResult.invalid(errors);
            }
            return internal.warnings().isEmpty()
                    ? TimelineValidationResult.ok()
                    : TimelineValidationResult.okWithWarnings(internal.warnings());
        } catch (Exception e) {
            return TimelineValidationResult.invalid(List.of("Invalid JSON: " + e.getMessage()));
        }
    }
}
