package com.example.platform.ingest.experimental.tika;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tika experimental detector configuration.
 * Disabled by default. Not for production use.
 */
@ConfigurationProperties(prefix = "ingest.experimental.tika")
public class TikaExperimentalProperties {
    private boolean enabled = false;
    private String mode = "detector-only";
    private boolean extractText = false;
    private boolean ocrEnabled = false;
    private int maxDetectBytes = 8192;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isExtractText() { return extractText; }
    public void setExtractText(boolean extractText) { this.extractText = extractText; }
    public boolean isOcrEnabled() { return ocrEnabled; }
    public void setOcrEnabled(boolean ocrEnabled) { this.ocrEnabled = ocrEnabled; }
    public int getMaxDetectBytes() { return maxDetectBytes; }
    public void setMaxDetectBytes(int maxDetectBytes) { this.maxDetectBytes = maxDetectBytes; }
}
