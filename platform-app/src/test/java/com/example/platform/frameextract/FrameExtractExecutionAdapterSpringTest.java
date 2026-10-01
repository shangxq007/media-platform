package com.example.platform.frameextract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.coverimage.CoverImageContracts;
import com.example.platform.thumbnail.ThumbnailContracts;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Spring-composition proof for the unified worker execution adapter.
 *
 * <p>This is the merged successor of {@code ThumbnailProviderInvokerSpringTest}: the retired
 * slice-local registries and the per-capability invokers are replaced by
 * {@link FrameExtractExecutionAdapter} — a worker-side execution adapter only (capability
 * discovery/registration happens through the platform capability registry,
 * {@code FrameExtractPlatformRegistration}). The adapter is discovered exactly the way production
 * discovers it — {@code @Component} scanning, i.e. a {@code ScannedGenericBeanDefinition} with the
 * default {@code AUTOWIRE_NO} mode — so it must expose a single public constructor and must not rely
 * on {@code @Autowired}.
 */
class FrameExtractExecutionAdapterSpringTest {

    private final ApplicationContextRunner canonicalRunner = new ApplicationContextRunner()
            // The adapter is worker-role composition (platform.runtime.role=WORKER); the scanned
            // production component is gated on that role.
            .withPropertyValues("platform.runtime.role=WORKER")
            .withUserConfiguration(ScannedAdapterConfiguration.class, CanonicalProviderConfiguration.class);

    @Test
    void adapterExposesExactlyOnePublicConstructorAndNoAutowired() throws Exception {
        Constructor<?>[] publicConstructors =
                Arrays.stream(FrameExtractExecutionAdapter.class.getDeclaredConstructors())
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                        .toArray(Constructor[]::new);
        assertThat(publicConstructors).hasSize(1);
        assertThat(publicConstructors[0].getParameterTypes())
                .containsExactly(List.class, String.class);
        for (Constructor<?> constructor : FrameExtractExecutionAdapter.class.getDeclaredConstructors()) {
            assertThat(constructor.isAnnotationPresent(Autowired.class))
                    .as("@Autowired must not be required to select a constructor")
                    .isFalse();
        }
    }

    @Test
    void componentScanInstantiatesAdapterWithoutAutowired() {
        canonicalRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FrameExtractExecutionAdapter.class);
            FrameExtractExecutionAdapter adapter = context.getBean(FrameExtractExecutionAdapter.class);
            assertThat(adapter.provider()).isInstanceOf(CanonicalProvider.class);
            assertThat(adapter.provider(FrameExtractExecutionAdapter.PINNED_PROVIDER_ID))
                    .isSameAs(adapter.provider());
            assertThat(adapter.provider().manifest().providerId()).isEqualTo("platform.ffmpeg");
            assertThat(adapter.provider().manifest().providerImplementationId())
                    .isEqualTo("ffmpeg.cpu.frame-extract.v1");
            assertThat(adapter.manifest().supports(CoverImageContracts.CAPABILITY)).isTrue();
            assertThat(adapter.manifest().supports(ThumbnailContracts.CAPABILITY)).isTrue();
        });
    }

    @Test
    void secondaryProviderIsRegisteredWithoutDisplacingThePinnedProvider() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedAdapterConfiguration.class,
                        CanonicalProviderConfiguration.class,
                        SecondaryProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FrameExtractExecutionAdapter adapter =
                            context.getBean(FrameExtractExecutionAdapter.class);
                    assertThat(adapter.provider()).isInstanceOf(CanonicalProvider.class);
                    assertThat(adapter.provider("platform.other")).isInstanceOf(SecondaryProvider.class);
                });
    }

    @Test
    void duplicateProviderIdsFailClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedAdapterConfiguration.class,
                        CanonicalProviderConfiguration.class,
                        DuplicateProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("duplicate frame-extract provider");
                });
    }

    @Test
    void missingPinnedProviderFailsClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(ScannedAdapterConfiguration.class, SecondaryProviderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("pinned frame-extract provider is not registered");
                });
    }

    @Test
    void implementationIdInTheProviderFamilySlotFailsClosed() {
        new ApplicationContextRunner()
                .withPropertyValues("platform.runtime.role=WORKER")
                .withUserConfiguration(
                        ScannedAdapterConfiguration.class, MisplacedIdentityProviderConfiguration.class)
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
        FrameExtractExecutionAdapter adapter =
                FrameExtractExecutionAdapter.of(provider, Path.of("./.data/frame-extract-work"));
        assertThat(adapter.provider()).isSameAs(provider);
        assertThat(adapter.provider(FrameExtractExecutionAdapter.PINNED_PROVIDER_ID)).isSameAs(provider);
    }

    /**
     * Production discovery path: component scan, default autowire mode (no {@code @Autowired}).
     *
     * <p>These nested fixtures are {@code @TestConfiguration}, not plain {@code @Configuration}: the
     * real worker context test bootstraps {@code PlatformFfmpegWorkerApplication} from the test
     * classpath, and its production component scan of {@code com.example.platform.frameextract} would
     * otherwise pick these nested configurations up.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = FrameExtractExecutionAdapter.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE, classes = FrameExtractExecutionAdapter.class))
    static class ScannedAdapterConfiguration {}

    @TestConfiguration(proxyBeanMethods = false)
    static class CanonicalProviderConfiguration {
        @Bean
        FrameExtractProvider canonicalProvider() {
            return new CanonicalProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecondaryProviderConfiguration {
        @Bean
        FrameExtractProvider secondaryProvider() {
            return new SecondaryProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DuplicateProviderConfiguration {
        @Bean
        FrameExtractProvider duplicateCanonicalProvider() {
            return new CanonicalProvider();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MisplacedIdentityProviderConfiguration {
        @Bean
        FrameExtractProvider misplacedIdentityProvider() {
            return new MisplacedIdentityProvider();
        }
    }

    static class CanonicalProvider implements FrameExtractProvider {
        @Override
        public FrameExtractManifest manifest() {
            return providerManifest(
                    FfmpegCpuFrameExtractProvider.PROVIDER_ID,
                    FfmpegCpuFrameExtractProvider.PROVIDER_IMPLEMENTATION_ID,
                    CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        }

        @Override
        public FrameExtractResult render(String capabilityId, Path input, Path workDirectory,
                String imageFormat, Integer width, Integer quality, double timestampSeconds,
                BooleanSupplier cancelled) {
            return FrameExtractResult.failure("NOT_RUN");
        }
    }

    static final class SecondaryProvider implements FrameExtractProvider {
        @Override
        public FrameExtractManifest manifest() {
            return providerManifest(
                    "platform.other", "platform.other.impl",
                    CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        }

        @Override
        public FrameExtractResult render(String capabilityId, Path input, Path workDirectory,
                String imageFormat, Integer width, Integer quality, double timestampSeconds,
                BooleanSupplier cancelled) {
            return FrameExtractResult.failure("NOT_RUN");
        }
    }

    static final class MisplacedIdentityProvider implements FrameExtractProvider {
        @Override
        public FrameExtractManifest manifest() {
            return providerManifest(
                    "ffmpeg.cpu.frame-extract.v1", "ffmpeg.cpu.frame-extract.v1",
                    CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);
        }

        @Override
        public FrameExtractResult render(String capabilityId, Path input, Path workDirectory,
                String imageFormat, Integer width, Integer quality, double timestampSeconds,
                BooleanSupplier cancelled) {
            return FrameExtractResult.failure("NOT_RUN");
        }
    }

    private static FrameExtractManifest providerManifest(
            String providerId, String providerImplementationId, String... capabilityIds) {
        List<FrameExtractCapabilityDeclaration> capabilities = Arrays.stream(capabilityIds)
                .map(capabilityId -> new FrameExtractCapabilityDeclaration(capabilityId, "1.0"))
                .toList();
        return new FrameExtractManifest(providerId, providerImplementationId, "1.0.0",
                capabilities, "test-toolchain", Set.of("video/*"), Set.of("image/png"),
                0, 60, 16, 8192, 1024, 10, "trusted-provider", "test-runtime");
    }
}
