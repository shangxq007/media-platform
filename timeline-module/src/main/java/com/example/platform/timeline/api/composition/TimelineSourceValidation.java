package com.example.platform.timeline.api.composition;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineSourceValidation {
ValidationResult validate(MediaStreamSourceBinding binding);

ValidationResult validate(MediaStreamSourceBinding binding,
                                     String expectedTenantId,
                                     String expectedProjectId,
                                     TrackType expectedTrackType);

/** Validate the shared source window when a video clip also feeds an explicit audio route. */
default ValidationResult validateRoutedVideo(MediaStreamSourceBinding binding, String tenant, String project) {
    return new ValidationResult(false, List.of("Routed embedded audio range verification unavailable"));
}

public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
