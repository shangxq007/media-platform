package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Spring-composition proof for the thumbnail capability registry.
 *
 * <p>The registry is discovered exactly the way production discovers it — {@code @Component}
 * scanning, i.e. a {@code ScannedGenericBeanDefinition} with the default {@code AUTOWIRE_NO} mode —
 * so a multi-constructor class would fail here the same way it fails in a real application
 * ({@code No default constructor found}). The registry must therefore expose a single public
 * constructor and must not rely on {@code @Autowired}.
 */
class ThumbnailCapabilityRegistrySpringTest {

    private final ApplicationContextRunner canonicalRunner = new ApplicationContextRunner()
            // The registry is worker-role composition (platform.runtime.role=WORKER), exactly like the
            // cover-image registry; the scanned production component is gated on that role.
            .withPropertyValues("platform.runtime.role=WORKER")
            .withUserConfiguration(ScannedRegistryConfiguration.class, CanonicalProviderConfiguration.class);

    @Test
    void registryExposesExactlyOnePublicConstructorAndNoAutowired() throws Exception {
        Constructor<?>[] publicConstructors = Arrays.stream(ThumbnailCapabilityRegistry.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toArray(Constructor[]::new);
        assertThat(publicConstructors).hasSize(1);
        assertThat(publicConstructors[0].getParameterTypes()).containsExactly(List.class);
        for (Constructor<?> constructor : ThumbnailCapabilityRegistry.class.getDeclaredConstructors()) {
            assertThat(constructor.isAnnotationPresent(Autowired.class))
                    .as("@Autowired must not be required to select a constructor")
                    .isFalse();
        }
    }

    @Test
    void componentScanInstantiatesRegistryWithoutAutowired() {
        canonicalRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ThumbnailCapabilityRegistry.class);
            ThumbnailCapabilityRegistry registry = context.getBean(ThumbnailCapabilityRegistry.class);
            assertThat(registry.provider()).isInstanceOf(CanonicalProvider.class);
            assertThat(registry.provider(ThumbnailContracts.PROVIDER)).isSameAs(registry.provider());
            assertThat(registry.provider(ThumbnailContracts.PROVIDER).manifest().providerVersion()).isEqualTo("1.0.0");
            assertThat(registry.provider(ThumbnailContracts.PROVIDER).manifest().providerId())
                    .isEqualTo("platform.ffmpeg");
            assertThat(registry.provider(ThumbnailContracts.PROVIDER).manifest().providerImplementationId())
                    .isEqualTo("ffmpeg.cpu.frame-extract.v1");
        });
    }

    @Test
    void secondaryProviderIsRegisteredWithoutDisplacingThePinnedProvider() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedRegistryConfiguration.class,
                        CanonicalProviderConfiguration.class,
                        SecondaryProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ThumbnailCapabilityRegistry registry = context.getBean(ThumbnailCapabilityRegistry.class);
                    assertThat(registry.provider()).isInstanceOf(CanonicalProvider.class);
                    assertThat(registry.provider("thumbnail.secondary")).isInstanceOf(SecondaryProvider.class);
                });
    }

    @Test
    void duplicateProviderIdsFailClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedRegistryConfiguration.class,
                        CanonicalProviderConfiguration.class,
                        DuplicateProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("duplicate thumbnail provider");
                });
    }

    @Test
    void missingPinnedProviderFailsClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(ScannedRegistryConfiguration.class, SecondaryProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("pinned media.thumbnail provider is not registered");
                });
    }

    @Test
    void implementationIdInTheProviderFamilySlotFailsClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedRegistryConfiguration.class, MisplacedIdentityProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("must differ");
                });
    }

    @Test
    void staticFactoryKeepsTheSingleProviderPathWithoutSecondConstructor() {
        CanonicalProvider provider = new CanonicalProvider();
        ThumbnailCapabilityRegistry registry = ThumbnailCapabilityRegistry.of(provider);
        assertThat(registry.provider()).isSameAs(provider);
        assertThat(registry.provider(ThumbnailContracts.PROVIDER)).isSameAs(provider);
    }

    /**
     * Production discovery path: component scan, default autowire mode (no {@code @Autowired}).
     *
     * <p>These nested fixtures are {@code @TestConfiguration}, not plain {@code @Configuration}: the
     * real worker context test bootstraps {@code ThumbnailWorkerApplication} from the test classpath,
     * and its production component scan of {@code com.example.platform.thumbnail} would otherwise
     * pick these nested configurations up and register duplicate/foreign thumbnail providers.
     * {@code @TestComponent} semantics keep them exclusively wired through the explicit
     * {@code withUserConfiguration(...)} calls below.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = ThumbnailCapabilityRegistry.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE, classes = ThumbnailCapabilityRegistry.class))
    static class ScannedRegistryConfiguration {}

    @TestConfiguration(proxyBeanMethods = false)
    static class CanonicalProviderConfiguration {
        @Bean
        ThumbnailCapabilityProvider canonicalProvider() {
            return new CanonicalProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecondaryProviderConfiguration {
        @Bean
        ThumbnailCapabilityProvider secondaryProvider() {
            return new SecondaryProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DuplicateProviderConfiguration {
        @Bean
        ThumbnailCapabilityProvider duplicateCanonicalProvider() {
            return new CanonicalProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MisplacedIdentityProviderConfiguration {
        @Bean
        ThumbnailCapabilityProvider misplacedIdentityProvider() {
            // The pre-fix defect shape: the implementation id repeated in the provider family slot.
            return new MisplacedIdentityProvider();
        }
    }

    static class CanonicalProvider implements ThumbnailCapabilityProvider {
        @Override
        public ThumbnailCapabilityProvider.Manifest manifest() {
            return providerManifest(List.of(new ThumbnailCapabilityProvider.CapabilityDeclaration(
                            ThumbnailContracts.CAPABILITY, ThumbnailContracts.CAPABILITY_VERSION)),
                    ThumbnailContracts.PROVIDER, ThumbnailContracts.PROVIDER_IMPLEMENTATION);
        }

        @Override
        public ThumbnailCapabilityProvider.Result extract(
                String capabilityId, ThumbnailContracts.Request request, byte[] input,
                BooleanSupplier cancelled) {
            return ThumbnailCapabilityProvider.Result.failure("NOT_RUN");
        }
    }

    static final class SecondaryProvider implements ThumbnailCapabilityProvider {
        @Override
        public ThumbnailCapabilityProvider.Manifest manifest() {
            return providerManifest(List.of(new ThumbnailCapabilityProvider.CapabilityDeclaration(
                            ThumbnailContracts.CAPABILITY, ThumbnailContracts.CAPABILITY_VERSION)),
                    "thumbnail.secondary", "thumbnail.secondary.impl");
        }

        @Override
        public ThumbnailCapabilityProvider.Result extract(
                String capabilityId, ThumbnailContracts.Request request, byte[] input,
                BooleanSupplier cancelled) {
            return ThumbnailCapabilityProvider.Result.failure("NOT_RUN");
        }
    }

    static final class MisplacedIdentityProvider implements ThumbnailCapabilityProvider {
        @Override
        public ThumbnailCapabilityProvider.Manifest manifest() {
            return providerManifest(List.of(new ThumbnailCapabilityProvider.CapabilityDeclaration(
                            ThumbnailContracts.CAPABILITY, ThumbnailContracts.CAPABILITY_VERSION)),
                    "ffmpeg.cpu.frame-extract.v1", "ffmpeg.cpu.frame-extract.v1");
        }

        @Override
        public ThumbnailCapabilityProvider.Result extract(
                String capabilityId, ThumbnailContracts.Request request, byte[] input,
                BooleanSupplier cancelled) {
            return ThumbnailCapabilityProvider.Result.failure("NOT_RUN");
        }
    }

    private static ThumbnailCapabilityProvider.Manifest providerManifest(
            List<ThumbnailCapabilityProvider.CapabilityDeclaration> capabilities,
            String providerId, String providerImplementationId) {
        return new ThumbnailCapabilityProvider.Manifest(providerId,
                providerImplementationId, "1.0.0", capabilities, "test-toolchain",
                Set.of("video/*"), Set.of("image/png"), 0, 60, 16, 4096, 1024, 10,
                "trusted-provider", "test-runtime");
    }
}
