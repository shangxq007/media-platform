package com.example.platform.ai.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiRoutingProperties {

    /** Spring bean name used when no capability route matches. */
    private String defaultProvider = "stubChatProvider";

    private Map<String, CapabilityRouting> routing = new LinkedHashMap<>();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, CapabilityRouting> getRouting() {
        return routing;
    }

    public void setRouting(Map<String, CapabilityRouting> routing) {
        this.routing = routing != null ? routing : new LinkedHashMap<>();
    }

    public static class CapabilityRouting {
        private RouteEndpoint primary;
        private List<RouteEndpoint> fallback = List.of();

        public RouteEndpoint getPrimary() {
            return primary;
        }

        public void setPrimary(RouteEndpoint primary) {
            this.primary = primary;
        }

        public List<RouteEndpoint> getFallback() {
            return fallback;
        }

        public void setFallback(List<RouteEndpoint> fallback) {
            this.fallback = fallback != null ? fallback : List.of();
        }
    }

    public static class RouteEndpoint {
        /** {@link com.example.platform.ai.domain.ChatProvider} bean name. */
        private String provider;
        private String model;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
