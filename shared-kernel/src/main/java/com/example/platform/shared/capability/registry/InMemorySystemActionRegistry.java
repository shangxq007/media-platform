package com.example.platform.shared.capability.registry;

import com.example.platform.shared.capability.CapabilityErrorCode;
import com.example.platform.shared.capability.SystemAction;

import java.util.*;

/**
 * In-memory implementation of SystemActionRegistry.
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Runtime execution is not implemented.</p>
 */
public class InMemorySystemActionRegistry implements SystemActionRegistry {

    private final Map<String, SystemAction> actions = new LinkedHashMap<>();

    @Override
    public void register(SystemAction action) {
        if (action == null) {
            throw new IllegalArgumentException("SystemAction must not be null");
        }

        String actionKey = action.actionKey();
        if (actionKey == null || actionKey.isBlank()) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.INVALID_REQUEST,
                "SystemAction key must not be blank"
            );
        }

        if (actions.containsKey(actionKey)) {
            throw new CapabilityRegistryException(
                CapabilityErrorCode.CONFLICT,
                "SystemAction already registered: " + actionKey
            );
        }

        actions.put(actionKey, action);
    }

    @Override
    public Optional<SystemAction> findByKey(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(actions.get(actionKey));
    }

    @Override
    public List<SystemAction> list() {
        return Collections.unmodifiableList(new ArrayList<>(actions.values()));
    }

    @Override
    public boolean contains(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return false;
        }
        return actions.containsKey(actionKey);
    }
}
