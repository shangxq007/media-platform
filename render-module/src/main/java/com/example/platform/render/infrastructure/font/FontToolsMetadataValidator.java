package com.example.platform.render.infrastructure.font;

import java.nio.file.Path;
import java.util.List;

/**
 * FontTools-based metadata validator skeleton.
 *
 * Disabled by default. Enable via: render.font.tools.enabled=true
 *
 * Requires fontTools Python package.
 */
public class FontToolsMetadataValidator implements FontValidator {

    private boolean enabled = false;

    public FontToolsMetadataValidator enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public String validatorName() {
        return "FontToolsMetadataValidator";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public FontValidationResult validate(Path fontFile) {
        if (!enabled) {
            return new FontValidationResult(validatorName(), "DISABLED",
                    List.of(), List.of("FontToolsMetadataValidator is disabled"), null, null, null, null,
                    false, false, false, false, false, false, false, false);
        }
        return new FontValidationResult(validatorName(), "NOT_IMPLEMENTED",
                List.of(), List.of("FontTools integration not yet implemented"), null, null, null, null,
                false, false, false, false, false, false, false, false);
    }

    @Override
    public FontValidationResult validate(java.io.InputStream fontData, String fileName) {
        return validate(Path.of(fileName));
    }
}
