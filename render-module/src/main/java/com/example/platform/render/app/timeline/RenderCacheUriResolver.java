package com.example.platform.render.app.timeline;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Resolves reusable artifact URIs; supports remote cache keys when local URI is absent.
 */
@Service
public class RenderCacheUriResolver {

    private final RenderCacheProperties properties;

    public RenderCacheUriResolver(RenderCacheProperties properties) {
        this.properties = properties;
    }

    public String resolve(String uri, String cacheKey, String tenantId) {
        if (uri != null && !uri.isBlank() && !uri.startsWith("reuse://")) {
            return uri;
        }
        if (!properties.isRemoteEnabled() || cacheKey == null || cacheKey.isBlank()) {
            return uri;
        }
        String prefix = properties.getRemoteUriPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        return prefix + "/" + tenant + "/" + cacheKey;
    }

    public String resolveRemote(String cacheKey, String tenantId) {
        if (!properties.isRemoteEnabled() || cacheKey == null || cacheKey.isBlank()) {
            return null;
        }
        String prefix = properties.getRemoteUriPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "default";
        return prefix + "/" + tenant + "/" + cacheKey;
    }

    public Map<String, String> resolveTaskIndex(Map<String, String> taskIdToUri,
                                                Map<String, String> taskIdToCacheKey,
                                                String tenantId) {
        Map<String, String> resolved = new LinkedHashMap<>();
        taskIdToUri.forEach((taskId, uri) -> {
            String cacheKey = taskIdToCacheKey.get(taskId);
            resolved.put(taskId, resolve(uri, cacheKey, tenantId));
        });
        return resolved;
    }
}
