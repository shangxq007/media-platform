package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class NoopFontValidator implements FontValidator {
    private static final Logger log = LoggerFactory.getLogger(NoopFontValidator.class);

    @Override
    public String validatorName() {
        return "NoopFontValidator";
    }

    @Override
    public FontValidationResult validate(Path fontFile) {
        log.warn("NoopFontValidator used for file: {}. This is NOT production-safe.", fontFile);
        return new FontValidationResult(
                validatorName(), "WARNING_PASS",
                List.of(), List.of("NoopFontValidator does not perform real validation"),
                null, null, null, null,
                false, false, false, false, false, false, false, false
        );
    }

    @Override
    public FontValidationResult validate(InputStream fontData, String fileName) {
        log.warn("NoopFontValidator used for stream: {}. This is NOT production-safe.", fileName);
        return new FontValidationResult(
                validatorName(), "WARNING_PASS",
                List.of(), List.of("NoopFontValidator does not perform real validation"),
                null, null, null, null,
                false, false, false, false, false, false, false, false
        );
    }
}
