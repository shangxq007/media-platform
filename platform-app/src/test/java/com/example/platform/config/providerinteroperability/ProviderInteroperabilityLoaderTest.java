package com.example.platform.config.providerinteroperability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 1 acceptance P1-A..P1-D for the Provider Interoperability Authority V0. */
class ProviderInteroperabilityLoaderTest {

    private static ProviderInteroperabilityProperties propertiesWithFormat(String format) {
        ProviderInteroperabilityProperties properties = new ProviderInteroperabilityProperties();
        properties.setFormat(format);
        return properties;
    }

    @Test
    void matchingFormatLoadsAuthority() {
        // P1-A: the supported format token loads.
        ProviderInteroperabilityProperties properties =
                propertiesWithFormat(ProviderInteroperabilityLoader.SUPPORTED_FORMAT);

        ProviderInteroperabilityAuthority authority =
                new ConfigurationProviderInteroperabilityAuthority(properties);

        assertThat(authority.declarations()).isNotNull();
    }

    @Test
    void emptyDeclarationsProduceEmptyList() {
        // P1-B: declarations: [] -> the empty list is the official output.
        ProviderInteroperabilityProperties properties =
                propertiesWithFormat(ProviderInteroperabilityLoader.SUPPORTED_FORMAT);
        properties.setDeclarations(List.of());

        assertThat(ProviderInteroperabilityLoader.load(properties)).isEqualTo(List.of());
    }

    @Test
    void equalConfigurationProducesEqualOutputAcrossCalls() {
        // P1-C: determinism / repeatable calls.
        ProviderInteroperabilityAuthority authority = new ConfigurationProviderInteroperabilityAuthority(
                propertiesWithFormat(ProviderInteroperabilityLoader.SUPPORTED_FORMAT));

        assertThat(authority.declarations()).isEqualTo(authority.declarations());
        assertThat(authority.declarations()).isSameAs(authority.declarations());
    }

    @Test
    void incompatibleFormatFailsClosed() {
        // P1-D: a non-matching format token fails closed.
        assertThatThrownBy(() -> ProviderInteroperabilityLoader.load(propertiesWithFormat("unknown")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported provider interoperability format");
    }

    @Test
    void missingFormatFailsClosed() {
        assertThatThrownBy(() -> ProviderInteroperabilityLoader.load(propertiesWithFormat(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonEmptyDeclarationsFailClosedInV0() {
        // V0 declaration item schema is NOT frozen -> a non-empty set is rejected, never interpreted.
        ProviderInteroperabilityProperties properties =
                propertiesWithFormat(ProviderInteroperabilityLoader.SUPPORTED_FORMAT);
        properties.setDeclarations(List.of(java.util.Map.of("unknown", "entry")));

        assertThatThrownBy(() -> ProviderInteroperabilityLoader.load(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declaration items are not supported");
    }
}
