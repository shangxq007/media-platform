package com.example.platform.render.infrastructure.bento4;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.bento4")
public class Bento4PackagingProviderProperties {

    private boolean enabled = false;
    private String mp4fragmentBin = "mp4fragment";
    private String mp4dashBin = "mp4dash";
    private long timeoutMillis = 300_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMp4fragmentBin() {
        return mp4fragmentBin;
    }

    public void setMp4fragmentBin(String mp4fragmentBin) {
        this.mp4fragmentBin = mp4fragmentBin;
    }

    public String getMp4dashBin() {
        return mp4dashBin;
    }

    public void setMp4dashBin(String mp4dashBin) {
        this.mp4dashBin = mp4dashBin;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
