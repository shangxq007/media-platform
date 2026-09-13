package com.example.platform.timeline.api.composition;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import java.util.List;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineValidation {
TimelineValidationResult validateDocument(
            String timelineId,
            com.example.platform.timeline.canonical.TimelineDocument document);

public record InternalTimelineValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings) {

        public static InternalTimelineValidationResult ok() {
            return new InternalTimelineValidationResult(true, List.of(), List.of());
        }

        public static InternalTimelineValidationResult okWithWarnings(List<String> warnings) {
            return new InternalTimelineValidationResult(true, List.of(), warnings);
        }

        public static InternalTimelineValidationResult invalid(List<String> errors) {
            return new InternalTimelineValidationResult(false, errors, List.of());
        }

        public static InternalTimelineValidationResult invalid(String error) {
            return new InternalTimelineValidationResult(false, List.of(error), List.of());
        }
    }

InternalTimelineValidationResult validate(String timelineJson);
}
