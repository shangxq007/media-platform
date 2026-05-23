package com.example.platform.render.app;

import com.example.platform.render.infrastructure.RenderProviderCapability;
import com.example.platform.render.infrastructure.RenderProviderHealthCheck;
import com.example.platform.render.infrastructure.RenderProviderRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Reports L3–L7 layer registration and health. */
@Service
public class NleLayerCatalogService {

    private final RenderProviderRegistry registry;

    public NleLayerCatalogService(RenderProviderRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> catalog() {
        Map<String, Object> layers = new LinkedHashMap<>();
        layers.put("L3_remotion", layerEntry("remotion", "Remotion React template worker"));
        layers.put("L3_shotstack", layerEntry("shotstack", "Shotstack cloud template API"));
        layers.put("L4_blender", layerEntry("blender", "Blender 3D segment worker"));
        layers.put("L5_natron", layerEntry("natron", "Natron OFX segment worker"));
        layers.put("L6_libass", layerEntry("libass", "libass ASS subtitle burn-in"));
        layers.put("L6_skia", layerEntry("skia", "Skia-compatible sticker overlay"));
        layers.put("L7_gpac", packagingEntry("gpac"));
        layers.put("L7_bento4", packagingEntry("bento4"));
        layers.put("L7_shaka", packagingEntry("shaka"));
        List<String> providerKeys = registry.getAllCapabilities().stream()
                .map(RenderProviderCapability::providerKey)
                .toList();
        return Map.of("layers", layers, "registeredProviders", providerKeys);
    }

    private Map<String, Object> layerEntry(String key, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("description", description);
        m.put("registered", registry.getProvider(key).isPresent());
        RenderProviderHealthCheck health = registry.getHealthCheck(key);
        m.put("healthy", health == null || health.healthy());
        if (health != null && health.message() != null) {
            m.put("healthMessage", health.message());
        }
        var cap = registry.getAllCapabilities().stream()
                .filter(c -> key.equals(c.providerKey()))
                .findFirst();
        cap.ifPresent(c -> m.put("profiles", new ArrayList<>(c.availableInProfiles())));
        return m;
    }

    private Map<String, Object> packagingEntry(String key) {
        Map<String, Object> m = layerEntry(key, "Packaging provider " + key);
        m.put("packagingOnly", true);
        return m;
    }
}
