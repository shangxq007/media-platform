package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class NoopFontSecurityScanner implements FontSecurityScanner {
    private static final Logger log = LoggerFactory.getLogger(NoopFontSecurityScanner.class);

    @Override
    public String scannerName() {
        return "NoopFontSecurityScanner";
    }

    @Override
    public boolean productionSafe() {
        return false;
    }

    @Override
    public FontSecurityResult scan(Path fontFile) {
        log.warn("NoopFontSecurityScanner used for file: {}. This is NOT production-safe.", fontFile);
        return new FontSecurityResult(
                scannerName(), "WARNING_PASS", java.time.Instant.now().toString(),
                false,
                List.of("NoopFontSecurityScanner does not perform real security checks"),
                null, null, false, false, false
        );
    }

    @Override
    public FontSecurityResult scan(InputStream fontData, String fileName) {
        log.warn("NoopFontSecurityScanner used for stream: {}. This is NOT production-safe.", fileName);
        return new FontSecurityResult(
                scannerName(), "WARNING_PASS", java.time.Instant.now().toString(),
                false,
                List.of("NoopFontSecurityScanner does not perform real security checks"),
                null, null, false, false, false
        );
    }
}
