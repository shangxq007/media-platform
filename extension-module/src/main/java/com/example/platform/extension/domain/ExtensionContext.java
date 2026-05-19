package com.example.platform.extension.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record ExtensionContext(
        String tenantId,
        String userId,
        String traceId,
        String extensionKey,
        String extensionVersion,
        ExtensionTrustLevel trustLevel,
        Map<String, String> config,
        Map<String, Object> attributes
) {
    public ExtensionContext {
        config = config != null ? Map.copyOf(config) : Map.of();
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String userId;
        private String traceId;
        private String extensionKey;
        private String extensionVersion;
        private ExtensionTrustLevel trustLevel = ExtensionTrustLevel.SEMI_TRUSTED;
        private final Map<String, String> config = new HashMap<>();
        private final Map<String, Object> attributes = new HashMap<>();

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder extensionKey(String extensionKey) { this.extensionKey = extensionKey; return this; }
        public Builder extensionVersion(String extensionVersion) { this.extensionVersion = extensionVersion; return this; }
        public Builder trustLevel(ExtensionTrustLevel trustLevel) { this.trustLevel = trustLevel; return this; }
        public Builder config(String key, String value) { this.config.put(key, value); return this; }
        public Builder attribute(String key, Object value) { this.attributes.put(key, value); return this; }

        public ExtensionContext build() {
            return new ExtensionContext(tenantId, userId, traceId, extensionKey, extensionVersion, trustLevel, config, attributes);
        }
    }
}
