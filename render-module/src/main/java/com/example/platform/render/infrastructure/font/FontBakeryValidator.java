package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class FontBakeryValidator implements FontValidator {
    private static final Logger log = LoggerFactory.getLogger(FontBakeryValidator.class);

    @Override
    public String validatorName() {
        return "FontBakeryValidator";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public FontValidationResult validate(Path fontFile) {
        log.warn("FontBakeryValidator is disabled. Returning empty validation result for: {}", fontFile);
        return new FontValidationResult(validatorName(), "DISABLED",
                List.of(), List.of("FontBakeryValidator is disabled"),
                null, null, null, null,
                false, false, false, false, false, false, false, false);
    }

    @Override
    public FontValidationResult validate(InputStream fontData, String fileName) {
        log.warn("FontBakeryValidator is disabled. Returning empty validation result for: {}", fileName);
        return new FontValidationResult(validatorName(), "DISABLED",
                List.of(), List.of("FontBakeryValidator is disabled"),
                null, null, null, null,
                false, false, false, false, false, false, false, false);
    }
}
