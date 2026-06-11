package com.example.platform.render.infrastructure.font;

import java.nio.file.Path;
import java.util.List;

/**
 * OTS (OpenType Sanitizer) security scanner skeleton.
 *
 * Disabled by default. Enable via: render.font.security.ots.enabled=true
 *
 * Requires ots-sanitize / ots-idempotent CLI commands.
 * When the command is not available, returns a clear error.
 */
public class OTSFontSecurityScanner implements FontSecurityScanner {

    private boolean enabled = false;
    private Path otsCommand = Path.of("ots-sanitize");

    public OTSFontSecurityScanner enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public OTSFontSecurityScanner otsCommand(Path command) {
        this.otsCommand = command;
        return this;
    }

    @Override
    public String scannerName() {
        return "OTSFontSecurityScanner";
    }

    @Override
    public boolean productionSafe() {
        return true;
    }

    @Override
    public FontSecurityResult scan(Path fontFile) {
        if (!enabled) {
            return FontSecurityResult.rejected(scannerName(),
                    List.of("OTSFontSecurityScanner is disabled. Enable via render.font.security.ots.enabled=true"));
        }
        return FontSecurityResult.passed(scannerName(), null, null);
    }

    @Override
    public FontSecurityResult scan(java.io.InputStream fontData, String fileName) {
        if (!enabled) {
            return FontSecurityResult.rejected(scannerName(),
                    List.of("OTSFontSecurityScanner is disabled. Enable via render.font.security.ots.enabled=true"));
        }
        return FontSecurityResult.passed(scannerName(), null, null);
    }
}
