package com.example.platform.render.infrastructure.font;

import java.io.InputStream;
import java.nio.file.Path;

public interface FontValidator {

    String validatorName();

    default boolean enabled() { return true; }

    FontValidationResult validate(Path fontFile);

    FontValidationResult validate(InputStream fontData, String fileName);
}
