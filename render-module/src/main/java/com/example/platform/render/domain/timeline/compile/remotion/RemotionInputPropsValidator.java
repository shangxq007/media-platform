package com.example.platform.render.domain.timeline.compile.remotion;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates RemotionInputProps for safety and completeness.
 *
 * <p>Internal only — validation result is not exposed publicly.</p>
 */
public class RemotionInputPropsValidator {

    /**
     * Validate RemotionInputProps.
     */
    public ValidationResult validate(RemotionInputProps props) {
        List<String> issues = new ArrayList<>();

        if (props == null) {
            return new ValidationResult(false, List.of("Props must not be null"));
        }

        // Schema version
        if (props.schemaVersion() == null || props.schemaVersion().isBlank()) {
            issues.add("schemaVersion is missing");
        }

        // Composition
        if (props.composition() == null) {
            issues.add("composition is missing");
        } else {
            if (props.composition().width() <= 0) issues.add("composition.width must be positive");
            if (props.composition().height() <= 0) issues.add("composition.height must be positive");
            if (props.composition().fps() <= 0) issues.add("composition.fps must be positive");
            if (props.composition().durationInFrames() <= 0) issues.add("composition.durationInFrames must be positive");
            if (props.composition().durationSeconds() <= 0) issues.add("composition.durationSeconds must be positive");
        }

        // Timeline
        if (props.timeline() == null) {
            issues.add("timeline is missing");
        }

        // Media assets
        if (props.mediaAssets() == null || props.mediaAssets().isEmpty()) {
            issues.add("mediaAssets must not be empty");
        }

        // Captions time range
        if (props.captions() != null) {
            for (RemotionCaptionSpec caption : props.captions()) {
                if (caption.endSeconds() <= caption.startSeconds()) {
                    issues.add("Caption " + caption.captionLayerId() + " has invalid time range");
                }
            }
        }

        // Safety: no local paths
        String fullJson = props.toString();
        if (fullJson.contains("/tmp/") || fullJson.contains("/home/") || fullJson.contains("/var/")) {
            issues.add("Props contain local paths");
        }
        if (fullJson.matches(".*[A-Za-z]:\\\\.*")) {
            issues.add("Props contain Windows paths");
        }

        // Safety: no storage internals
        if (fullJson.contains("\"bucket\"") || fullJson.contains("\"objectKey\"")
                || fullJson.contains("\"rootPath\"") || fullJson.contains("\"relativePath\"")) {
            issues.add("Props contain storage internals");
        }

        // Safety: no signed URLs
        if (fullJson.contains("signedUrl") || fullJson.contains("X-Amz-Signature")) {
            issues.add("Props contain signed URLs");
        }

        // Safety: no raw commands
        if (fullJson.contains("ffmpeg ") || fullJson.contains("remotion render")) {
            issues.add("Props contain raw commands");
        }

        return new ValidationResult(issues.isEmpty(), issues);
    }

    /**
     * Validation result — internal only.
     */
    public record ValidationResult(boolean valid, List<String> issues) {}
}
