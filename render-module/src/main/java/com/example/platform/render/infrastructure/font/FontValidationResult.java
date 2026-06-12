package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontValidationResult(
        String validator,
        String validationStatus,
        List<String> missingRequiredTables,
        List<String> warnings,
        String fontFamily,
        String fontSubfamily,
        Integer weight,
        String style,
        boolean hasCmap,
        boolean hasGlyf,
        boolean hasHead,
        boolean hasHhea,
        boolean hasMaxp,
        boolean hasOs2,
        boolean hasPost,
        boolean hasName
) {
    public boolean isValid() {
        return "PASSED".equals(validationStatus) && missingRequiredTables.isEmpty();
    }

    public static FontValidationResult failed(String validator, String error) {
        return new FontValidationResult(
                validator, "FAILED",
                List.of(), List.of(error),
                null, null, null, null,
                false, false, false, false, false, false, false, false
        );
    }
}
