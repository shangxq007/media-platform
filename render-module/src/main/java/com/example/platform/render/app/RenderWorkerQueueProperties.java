package com.example.platform.render.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.render.worker-queue")
public class RenderWorkerQueueProperties {

    private boolean enabled = false;

    /** When true, a scheduler drains the Natron queue via {@link RenderNatronQueueProcessor}. */
    private boolean consumeEnabled = false;

    private long pollIntervalMs = 5_000L;

    private int maxNatronDepth = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConsumeEnabled() {
        return consumeEnabled;
    }

    public void setConsumeEnabled(boolean consumeEnabled) {
        this.consumeEnabled = consumeEnabled;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxNatronDepth() {
        return maxNatronDepth;
    }

    public void setMaxNatronDepth(int maxNatronDepth) {
        this.maxNatronDepth = maxNatronDepth;
    }
}
