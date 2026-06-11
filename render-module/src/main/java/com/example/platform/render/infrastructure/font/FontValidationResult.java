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
}
