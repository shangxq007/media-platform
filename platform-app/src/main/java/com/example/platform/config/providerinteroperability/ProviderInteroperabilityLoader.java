package com.example.platform.config.providerinteroperability;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import java.util.List;
import java.util.Objects;

/**
 * Pure, stateless loader that turns the V0 configuration carrier into the declared
 * provider-boundary compatibility declaration set.
 *
 * <p>Fail-closed rules:
 * <ul>
 *   <li>the {@code format} token must equal {@link #SUPPORTED_FORMAT}; any other (or absent)
 *       value is rejected rather than interpreted;</li>
 *   <li>a non-empty {@code declarations} value is rejected — the V0 declaration item schema is
 *       deliberately NOT frozen, so no item is ever interpreted. The empty set is the only
 *       supported V0 output.</li>
 * </ul>
 */
public final class ProviderInteroperabilityLoader {

    /** The frozen V0 format token for provider interoperability configuration. */
    public static final String SUPPORTED_FORMAT = "p25a.provider-interoperability.v1";

    private ProviderInteroperabilityLoader() {
    }

    /**
     * Builds the declared compatibility declaration set from the V0 configuration.
     *
     * @throws IllegalStateException when the format token is unsupported/absent, or when a
     *     non-empty declaration set is supplied (V0 schema not frozen)
     */
    public static List<ProviderBoundaryCompatibilityDeclaration> load(
            ProviderInteroperabilityProperties properties) {
        Objects.requireNonNull(properties, "properties");
        String format = properties.getFormat();
        if (format == null || !SUPPORTED_FORMAT.equals(format)) {
            throw new IllegalStateException(
                    "unsupported provider interoperability format: expected '"
                            + SUPPORTED_FORMAT + "' but was '" + format + "'");
        }
        List<Object> declarations = properties.getDeclarations();
        if (declarations != null && !declarations.isEmpty()) {
            throw new IllegalStateException(
                    "provider interoperability declaration items are not supported in V0 format '"
                            + SUPPORTED_FORMAT + "'; declarations must be empty");
        }
        return List.of();
    }
}
