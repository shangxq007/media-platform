package com.example.platform.config.providerinteroperability;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import java.util.List;
import java.util.Objects;

/**
 * Configuration-backed {@link ProviderInteroperabilityAuthority}.
 *
 * <p>Resolves the declaration set once from the V0 configuration carrier (fail-closed on load)
 * and serves the immutable result deterministically.
 */
public final class ConfigurationProviderInteroperabilityAuthority
        implements ProviderInteroperabilityAuthority {

    private final List<ProviderBoundaryCompatibilityDeclaration> declarations;

    public ConfigurationProviderInteroperabilityAuthority(ProviderInteroperabilityProperties properties) {
        this.declarations = ProviderInteroperabilityLoader.load(
                Objects.requireNonNull(properties, "properties"));
    }

    @Override
    public List<ProviderBoundaryCompatibilityDeclaration> declarations() {
        return declarations;
    }
}
