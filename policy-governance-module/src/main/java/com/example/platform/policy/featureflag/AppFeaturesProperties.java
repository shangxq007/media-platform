package com.example.platform.policy.featureflag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Platform feature evaluation settings. PostgreSQL control-plane storage is the runtime default. */
@ConfigurationProperties(prefix = "app.features")
public class AppFeaturesProperties {
    private boolean localDevelopment;
    private final Flagd flagd = new Flagd();

    public boolean isLocalDevelopment() { return localDevelopment; }
    public void setLocalDevelopment(boolean localDevelopment) { this.localDevelopment = localDevelopment; }
    public Flagd getFlagd() { return flagd; }
    /** @deprecated retained only for source compatibility; Unleash is not a runtime provider. */
    @Deprecated public Flagd getUnleash() { return flagd; }
    @Deprecated public void setUnleash(Flagd ignored) { /* compatibility only; runtime remains platform-owned */ }

    public static class Flagd {
        private boolean enabled;
        private String endpoint = "http://localhost:4242/api/";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint; }
        @Deprecated public String getApiUrl() { return endpoint; }
        @Deprecated public void setApiUrl(String value) { this.endpoint = value == null ? "" : value; }
        @Deprecated public String getAppName() { return "media-platform"; }
        @Deprecated public String getInstanceId() { return "singleton"; }
        @Deprecated public String getApiKey() { return ""; }
    }
}
