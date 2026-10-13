package com.example.platform.config.providerinteroperability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Provider Interoperability Authority V0.
 *
 * <p>Registers the {@code platform.provider-interoperability} configuration carrier and exposes
 * the {@link ProviderInteroperabilityAuthority} bean. This configuration is picked up by the
 * platform application component scan; it carries no role condition.
 */
@Configuration
@EnableConfigurationProperties(ProviderInteroperabilityProperties.class)
public class ProviderInteroperabilityConfiguration {

    @Bean
    ProviderInteroperabilityAuthority providerInteroperabilityAuthority(
            ProviderInteroperabilityProperties properties) {
        return new ConfigurationProviderInteroperabilityAuthority(properties);
    }
}
