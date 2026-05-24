package com.example.platform.render.infrastructure.blender;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.blender")
public class BlenderRenderProviderProperties {

    private boolean enabled;
    private String binary = "blender";
    private long timeoutMillis = 1_800_000;
    private boolean stubOnMissingBinary = false;

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

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isStubOnMissingBinary() {
        return stubOnMissingBinary;
    }

    public void setStubOnMissingBinary(boolean stubOnMissingBinary) {
        this.stubOnMissingBinary = stubOnMissingBinary;
    }
}
