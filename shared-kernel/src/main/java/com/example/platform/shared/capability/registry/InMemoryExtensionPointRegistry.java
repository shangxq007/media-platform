package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;
import com.example.platform.shared.capability.ExtensionPoint;

import java.util.*;

/**
 * In-memory implementation of ExtensionPointRegistry.
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Runtime execution is not implemented.</p>
 */
public class InMemoryExtensionPointRegistry implements ExtensionPointRegistry {

    private final Map<String, ExtensionPoint> extensionPoints = new LinkedHashMap<>();

    @Override
    public void register(ExtensionPoint extensionPoint) {
        if (extensionPoint == null) {
            throw new IllegalArgumentException("ExtensionPoint must not be null");
        }

        String key = extensionPoint.key();
        String version = extensionPoint.version();

        if (key == null || key.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "ExtensionPoint key must not be blank"
            );
        }

        if (version == null || version.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "ExtensionPoint version must not be blank"
            );
        }

        String compositeKey = key + ":" + version;
        if (extensionPoints.containsKey(compositeKey)) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.CONFLICT,
                "ExtensionPoint already registered: " + compositeKey
            );
        }

        extensionPoints.put(compositeKey, extensionPoint);
    }

    @Override
    public Optional<ExtensionPoint> find(String key, String version) {
        if (key == null || key.isBlank() || version == null || version.isBlank()) {
            return Optional.empty();
        }
        String compositeKey = key + ":" + version;
        return Optional.ofNullable(extensionPoints.get(compositeKey));
    }

    @Override
    public List<ExtensionPoint> list() {
        return Collections.unmodifiableList(new ArrayList<>(extensionPoints.values()));
    }

    @Override
    public boolean contains(String key, String version) {
        if (key == null || key.isBlank() || version == null || version.isBlank()) {
            return false;
        }
        String compositeKey = key + ":" + version;
        return extensionPoints.containsKey(compositeKey);
    }
}
