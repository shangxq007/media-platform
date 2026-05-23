package com.example.platform.render.infrastructure.vapoursynth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.vapoursynth")
public class VapourSynthRenderProviderProperties {

    private boolean enabled = true;
    private String binary = "vspipe";
    private boolean stubOnMissingBinary = true;
    private boolean fallbackToFfmpeg = true;
    private long timeoutMillis = 600_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBinary() {
        return binary;
    }

    public void setBinary(String binary) {
        this.binary = binary;
    }

    public boolean isStubOnMissingBinary() {
        return stubOnMissingBinary;
    }

    public void setStubOnMissingBinary(boolean stubOnMissingBinary) {
        this.stubOnMissingBinary = stubOnMissingBinary;
    }

    public boolean isFallbackToFfmpeg() {
        return fallbackToFfmpeg;
    }

    public void setFallbackToFfmpeg(boolean fallbackToFfmpeg) {
        this.fallbackToFfmpeg = fallbackToFfmpeg;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
