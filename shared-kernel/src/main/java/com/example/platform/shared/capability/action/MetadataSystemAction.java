package com.example.platform.shared.capability.action;

import com.example.platform.shared.capability.CapabilityStability;
import com.example.platform.shared.capability.SystemAction;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata-only implementation of SystemAction for built-in actions.
 *
 * <p>MetadataSystemAction provides action metadata without execution logic.
 * It is used to register built-in actions in the SystemActionRegistry.</p>
 *
 * <p><strong>Contract only:</strong> This defines action metadata.
 * Runtime execution is not implemented.</p>
 */
public record MetadataSystemAction(
    String actionKey,
    String displayName,
    String description,
    String version,
    String inputSchemaRef,
    String outputSchemaRef,
    Set<String> requiredPermissions,
    Duration timeout,
    boolean idempotent,
    CapabilityStability stability,
    SystemActionCategory category
) implements SystemAction {

    public MetadataSystemAction {
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("actionKey must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        requiredPermissions = requiredPermissions != null ? Set.copyOf(requiredPermissions) : Set.of();
        timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        stability = stability != null ? stability : CapabilityStability.STABLE;
        category = category != null ? category : SystemActionCategory.MEDIA;
    }

    @Override
    public boolean isIdempotent() {
        return idempotent;
    }
}
