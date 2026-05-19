package com.example.platform.policy.featureflag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags: Unleash (via OpenFeature) is optional; when disabled, an in-memory provider returns defaults.
 */
@ConfigurationProperties(prefix = "app.features")
public class AppFeaturesProperties {

    private Unleash unleash = new Unleash();

    public Unleash getUnleash() {
        return unleash;
    }

    public void setUnleash(Unleash unleash) {
        this.unleash = unleash != null ? unleash : new Unleash();
    }

    public static class Unleash {

        /** When false, OpenFeature uses {@link dev.openfeature.sdk.providers.memory.InMemoryProvider} (defaults only). */
        private boolean enabled;

        private String apiUrl = "http://localhost:4242/api/";

        private String appName = "media-platform";

        private String instanceId = "singleton";

        /** Optional; OSS Unleash may use a client token. */
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
