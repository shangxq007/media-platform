package com.example.platform.render.domain.timeline;

import java.util.List;

/**
 * Result of validating a {@link TimelineSpec}.
 *
 * @param valid    whether the timeline is valid
 * @param errors   list of validation errors (empty if valid)
 * @param warnings list of validation warnings
 */
public record TimelineValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) {

    /**
     * Creates a valid result with no errors or warnings.
     */
    public static TimelineValidationResult ok() {
        return new TimelineValidationResult(true, List.of(), List.of());
    }

    /**
     * Creates a valid result with warnings.
     */
    public static TimelineValidationResult okWithWarnings(List<String> warnings) {
        return new TimelineValidationResult(true, List.of(), warnings);
    }

    /**
     * Creates an invalid result with the given errors.
     */
    public static TimelineValidationResult invalid(List<String> errors) {
        return new TimelineValidationResult(false, errors, List.of());
    }

    /**
     * Creates an invalid result with a single error message.
     */
    public static TimelineValidationResult error(String error) {
        return new TimelineValidationResult(false, List.of(error), List.of());
    }
}
