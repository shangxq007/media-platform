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

public record ValidationResult(boolean valid, List<String> violations) {
        public ValidationResult {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }
}
