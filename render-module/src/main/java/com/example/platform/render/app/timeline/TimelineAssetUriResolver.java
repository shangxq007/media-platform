package com.example.platform.render.app.timeline;

import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Resolves clip media URIs from Internal Timeline 1.0 assetRegistry entries.
 * Fails fast when registry entry is missing, tombstoned, or has no concrete storage URI.
 */
@Component
public class TimelineAssetUriResolver {

    private final ErrorCodeRegistry errorCodeRegistry;

    public TimelineAssetUriResolver(ErrorCodeRegistry errorCodeRegistry) {
        this.errorCodeRegistry = errorCodeRegistry;
    }

    public String resolve(JsonNode clipNode, String assetId, JsonNode assetRegistry) {
        if (clipNode != null && clipNode.has("uri")) {
            String direct = clipNode.get("uri").asText("");
            if (!direct.isBlank() && !direct.startsWith("asset://")) {
                return direct;
            }
        }
        if (assetRegistry == null || !assetRegistry.isObject() || !assetRegistry.has(assetId)) {
            throw MediaAssetErrors.assetNotFound(errorCodeRegistry, assetId);
        }
        JsonNode entry = assetRegistry.get(assetId);
        String status = entry.path("status").asText("ACTIVE");
        if ("TOMBSTONED".equalsIgnoreCase(status) || "PURGED".equalsIgnoreCase(status)) {
            throw MediaAssetErrors.assetTombstoned(errorCodeRegistry, assetId);
        }
        String uri = entry.path("uri").asText("");
        if (uri.isBlank() || uri.startsWith("asset://")) {
            throw MediaAssetErrors.assetNotFound(errorCodeRegistry, assetId);
        }
        return uri;
    }

    public void assertRegistryEntryActive(String assetId, JsonNode assetRegistry) {
        try {
            resolve(null, assetId, assetRegistry);
        } catch (PlatformException e) {
            throw e;
        }
    }
}
