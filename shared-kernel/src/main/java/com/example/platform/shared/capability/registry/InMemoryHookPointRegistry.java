package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;
import com.example.platform.shared.capability.hook.HookPhase;
import com.example.platform.shared.capability.hook.HookPoint;

import java.util.*;

/**
 * In-memory implementation of HookPointRegistry.
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Hook runtime is not implemented.</p>
 */
public class InMemoryHookPointRegistry implements HookPointRegistry {

    private final Map<String, HookPoint> hookPoints = new LinkedHashMap<>();

    @Override
    public void register(HookPoint hookPoint) {
        if (hookPoint == null) {
            throw new IllegalArgumentException("HookPoint must not be null");
        }

        String key = hookPoint.key();
        HookPhase phase = hookPoint.phase();

        if (key == null || key.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "HookPoint key must not be blank"
            );
        }

        if (phase == null) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "HookPoint phase must not be null"
            );
        }

        String compositeKey = key + ":" + phase.name();
        if (hookPoints.containsKey(compositeKey)) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.CONFLICT,
                "HookPoint already registered: " + compositeKey
            );
        }

        hookPoints.put(compositeKey, hookPoint);
    }

    @Override
    public Optional<HookPoint> find(String key, HookPhase phase) {
        if (key == null || key.isBlank() || phase == null) {
            return Optional.empty();
        }
        String compositeKey = key + ":" + phase.name();
        return Optional.ofNullable(hookPoints.get(compositeKey));
    }

    @Override
    public List<HookPoint> list() {
        return Collections.unmodifiableList(new ArrayList<>(hookPoints.values()));
    }

    @Override
    public boolean contains(String key, HookPhase phase) {
        if (key == null || key.isBlank() || phase == null) {
            return false;
        }
        String compositeKey = key + ":" + phase.name();
        return hookPoints.containsKey(compositeKey);
    }
}
