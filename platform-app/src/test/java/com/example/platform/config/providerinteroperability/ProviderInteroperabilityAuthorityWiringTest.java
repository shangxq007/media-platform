package com.example.platform.config.providerinteroperability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Phase 1 acceptance P1-E: the Authority is injectable from a Spring context. */
class ProviderInteroperabilityAuthorityWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProviderInteroperabilityConfiguration.class);

    @Test
    void authorityBeanIsRegisteredAndInjected() {
        contextRunner
                .withPropertyValues(
                        "platform.provider-interoperability.format=p25a.provider-interoperability.v1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderInteroperabilityAuthority.class);
                    assertThat(context.getBean(ProviderInteroperabilityAuthority.class).declarations())
                            .isEmpty();
                });
    }

    @Test
    void bodylessDeclarationsBindToEmptySet() {
        contextRunner
                .withPropertyValues(
                        "platform.provider-interoperability.format=p25a.provider-interoperability.v1",
                        "platform.provider-interoperability.declarations[0]=ignored")
                .run(context -> assertThat(context).hasFailed());
    }
}
