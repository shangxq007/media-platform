package com.example.platform.render.infrastructure.shaka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "render.providers.shaka")
public class ShakaPackagingProviderProperties {

    private boolean enabled;
    private String packagerBin = "packager";
    private long timeoutMillis = 600_000;
    private boolean stubOnMissingBinary = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPackagerBin() {
        return packagerBin;
    }

    public void setPackagerBin(String packagerBin) {
        this.packagerBin = packagerBin;
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
