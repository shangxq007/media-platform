package com.example.platform.extension.domain;

import java.util.Objects;

/**
 * #16 (R2/C5): independent capability implementation identity.
 *
 * <p>A CapabilityImplementationId is NOT the {@code (pluginId, capabilityId)}
 * tuple — one plugin may provide MULTIPLE distinct implementations of the SAME
 * capability (e.g. CPU / GPU / HQ implementations of {@code audio.timeStretch}).
 * The (plugin, capability) pair is only the <em>provides relation</em>. The
 * implementation id is stable, deterministic and registry-unique.
 */
public record CapabilityImplementationId(String value) {

    public CapabilityImplementationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("implementation id must not be blank");
        }
        if (value.contains(" ") || value.contains("..") || value.startsWith(".") || value.endsWith(".")) {
            throw new IllegalArgumentException("malformed implementation id: " + value);
        }
    }

    public static CapabilityImplementationId of(String value) {
        return new CapabilityImplementationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
