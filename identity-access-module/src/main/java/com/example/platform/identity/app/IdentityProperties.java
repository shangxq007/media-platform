package com.example.platform.identity.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.identity")
public class IdentityProperties {
    private boolean apiKeyAuthEnabled = false;
    private Map<String, String> apiKeys = new LinkedHashMap<>();
    private List<String> allowedOrigins = List.of("http://localhost:3000");
    private List<String> ipWhitelist = List.of();
    private int rateLimitRequestsPerMinute = 100;
    private boolean rateLimitEnabled = false;

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

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getIpWhitelist() {
        return ipWhitelist;
    }

    public void setIpWhitelist(List<String> ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public int getRateLimitRequestsPerMinute() {
        return rateLimitRequestsPerMinute;
    }

    public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
        this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }
}
