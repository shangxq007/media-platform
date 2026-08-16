package com.example.platform.timeline.app;

import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnosticSeverity;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * GCR-1 CORRECTION V2: Timeline-owned canonical validation authority for
 * Internal Timeline Schema 1.0 documents.
 *
 * <p>This service is the SOLE production canonical validator: it runs the E1b
 * canonical gate (internal-1.0 -&gt; {@link TimelineCandidate} -&gt;
 * {@link TimelineCanonicalValidator} -&gt; {@link TimelineCanonicalNormalizer})
 * on the input document and reports diagnostics as a timeline-owned result. No
 * production code outside timeline-module independently accepts or rejects
 * canonical Timeline schema. There is no second, compatibility, or
 * representation-level validator in render.</p>
 */
@Service
public class InternalTimelineValidationService {

    /** Timeline-owned validation result (endpoint-friendly shape). */
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

    public InternalTimelineValidationResult validate(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            return InternalTimelineValidationResult.invalid(List.of("Timeline JSON is empty"));
        }
        if (!timelineJson.trim().startsWith("{")) {
            return InternalTimelineValidationResult.invalid(List.of("Timeline must be a JSON object"));
        }
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return InternalTimelineValidationResult.invalid(List.of(
                        "Internal Timeline 1.0 required: schemaVersion 1.x and composition block. "
                                + "Use POST import_otio / import_edl to convert exchange formats."));
            }
            TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("validation", timelineJson);
            TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
            if (validation.hasFatalErrors()) {
                return InternalTimelineValidationResult.invalid(toMessages(validation, TimelineDiagnosticSeverity.ERROR));
            }
            if (TimelineCanonicalNormalizer.normalize(candidate).isEmpty()) {
                return InternalTimelineValidationResult.invalid(List.of(
                        "Timeline candidate is not canonical-normalizable"));
            }
            List<String> warnings = toMessages(validation, TimelineDiagnosticSeverity.WARNING);
            List<String> infos = toMessages(validation, TimelineDiagnosticSeverity.INFO);
            warnings.addAll(infos);
            return warnings.isEmpty()
                    ? InternalTimelineValidationResult.ok()
                    : InternalTimelineValidationResult.okWithWarnings(warnings);
        } catch (TimelineCanonicalRejectionException e) {
            return InternalTimelineValidationResult.invalid(List.of(e.getMessage()));
        } catch (Exception e) {
            return InternalTimelineValidationResult.invalid(List.of("Invalid JSON: " + e.getMessage()));
        }
    }

    private static List<String> toMessages(TimelineValidationResult validation,
                                           TimelineDiagnosticSeverity severity) {
        List<String> messages = new ArrayList<>();
        for (TimelineDiagnostic diagnostic : validation.diagnostics()) {
            if (diagnostic.severity() == severity) {
                messages.add(diagnostic.message());
            }
        }
        return messages;
    }
}
