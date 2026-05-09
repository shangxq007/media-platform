package com.example.platform.identity.app;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.identity")
public class IdentityProperties {
    private boolean apiKeyAuthEnabled = false;
    private Map<String, String> apiKeys = new LinkedHashMap<>();

    public boolean isApiKeyAuthEnabled() {
        return apiKeyAuthEnabled;
    }

    public void setApiKeyAuthEnabled(boolean apiKeyAuthEnabled) {
        this.apiKeyAuthEnabled = apiKeyAuthEnabled;
    }

    public Map<String, String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(Map<String, String> apiKeys) {
        this.apiKeys = apiKeys;
    }
}
