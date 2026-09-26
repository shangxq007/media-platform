package com.example.platform.coverimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.PlatformApplication;
import com.example.platform.composition.app.CompositionProviderBoundCapabilityAuthority;
import com.example.platform.composition.app.ProviderRegistryBoundary;
import com.example.platform.composition.domain.CompositionModels.Availability;
import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.extension.api.port.PluginRegistryPort;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-process registration proof for the cover capability (COVER-PROVIDER-FINAL-FIX-001).
 *
 * <p>The API process runs {@link PlatformApplication} with its explicit {@code @ComponentScan}, which
 * <em>suppresses</em> the {@code @SpringBootApplication} default scan. The cover package was missing from
 * that list, so the advertised admission surface
 * ({@code POST/GET /api/projects/{projectId}/cover-images}) was never registered even though the
 * capability document claims it is live.
 *
 * <p>This test boots the real API context (default profile, no Temporal cluster) and asserts the exact
 * cover bean split: the API-side admission surface is registered, and every worker-only cover bean —
 * provider, registry, sandbox backend, materializer, local object store, commit fence, activities and
 * the worker application itself — is absent. No test-scoped stub is used: the assertion is about the
 * production scan configuration.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    // Same API-test prerequisites the repository's other PlatformApplication context tests use.
    "app.security.enabled=true",
    "app.security.oauth2.enabled=false",
    "app.security.jwt.secret-key=cover-image-api-context-key-at-least-256-bits",
    "app.identity.api-key-auth-enabled=false",
    "app.outbox.dispatcher-enabled=false",
    "storage.s3.enabled=false"})
class CoverImageApiContextRegistrationTest extends PostgresTestContainerSupport {

    /** Exactly the cover beans the API process may own. */
    private static final Set<String> API_SIDE_COVER_BEANS = Set.of(
            CoverImageController.class.getName(),
            CoverImageService.class.getName(),
            CoverImageTaskStore.class.getName(),
            // COVER-PROVIDER-PLATFORM-REGISTER-001: platform capability registration is API-side.
            CoverImagePlatformRegistration.class.getName());

    @Autowired ConfigurableApplicationContext context;

    @Autowired CapabilityRegistryPort capabilityRegistry;
    @Autowired PluginRegistryPort pluginRegistry;
    @Autowired ProviderRegistryBoundary catalog;
    @Autowired CompositionProviderBoundCapabilityAuthority capabilityAuthority;

    @Test
    void apiContextRegistersTheCoverAdmissionSurfaceAndNoWorkerOnlyCoverBeans() {
        assertThat(context.isActive()).isTrue();

        // The advertised admission surface is really registered in the API process.
        assertThat(context.getBeansOfType(CoverImageController.class)).hasSize(1);
        assertThat(context.getBeansOfType(CoverImageService.class)).hasSize(1);
        assertThat(context.getBeansOfType(CoverImageTaskStore.class)).hasSize(1);
        assertThat(coverRoutePatterns()).contains("/api/projects/{projectId}/cover-images");

        // Worker-only cover beans must never exist in the API process.
        assertThat(context.getBeansOfType(CoverImageCapabilityRegistry.class)).isEmpty();
        assertThat(context.getBeansOfType(CpuFrameExtractCoverImageProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(CoverImageExecutionBackend.class)).isEmpty();
        assertThat(context.getBeansOfType(CoverImageMaterializationConfiguration.class)).isEmpty();
        assertThat(context.getBeansOfType(CoverImageWorkerRuntimeConfiguration.class)).isEmpty();
        assertThat(context.getBeansOfType(LocalObjectStoreStorageProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(CoverImageCommitService.class)).isEmpty();
        assertThat(context.getBeansOfType(CoverImageActivitiesImpl.class)).isEmpty();

        // The whole cover package contributes nothing else to the API graph.
        assertThat(coverBeans()).isEqualTo(new TreeSet<>(API_SIDE_COVER_BEANS));
    }

    /**
     * COVER-PROVIDER-PLATFORM-REGISTER-001: with the real API context up, the platform capability
     * registry must expose {@code media.cover-image} for the cover provider family, and the
     * composition capability catalog must list it on the platform Artifact contract — while never
     * advertising it as composable before the platform execution seam exists (backlog C2).
     */
    @Test
    void platformRegistriesExposeMediaCoverImageForTheCoverProvider() {
        assertThat(context.getBeansOfType(CoverImagePlatformRegistration.class)).hasSize(1);
        assertThat(context.getBean(CoverImagePlatformRegistration.class).registered())
                .as("the platform process registered the capability")
                .isTrue();

        var implementations = capabilityRegistry
                .findCapabilityImplementations(CapabilityId.of(CoverImageContracts.CAPABILITY));
        assertThat(implementations).hasSize(1);
        assertThat(implementations.getFirst().pluginId())
                .isEqualTo(CoverImagePlatformProvider.PLUGIN_ID);
        assertThat(implementations.getFirst().contractVersion().toString())
                .isEqualTo(CoverImageContracts.CAPABILITY_VERSION);

        var candidates = pluginRegistry.findCapabilityCandidates(
                CoverImageContracts.CAPABILITY, CoverImageContracts.CAPABILITY_VERSION);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().capabilities())
                .extracting(capability -> capability.capabilityId())
                .containsExactly(CoverImageContracts.CAPABILITY);

        var entry = catalog.publicAvailability().stream()
                .filter(capability -> capability.capabilityId().equals(CoverImageContracts.CAPABILITY))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "media.cover-image missing from the composition capability catalog"));
        assertThat(entry.input().name()).isEqualTo("Artifact");
        assertThat(entry.output().name()).isEqualTo("Artifact");
        assertThat(entry.availability())
                .as("not advertised as composable before the platform execution seam (C2) exists")
                .isEqualTo(Availability.UNAVAILABLE);
        // COVER-PROVIDER-PLATFORM-REGISTER-FIX-001: the unavailable verdict must have a real runtime
        // effect — provider-bound resolution fails closed for this capability even though a healthy
        // candidate provider is registered above.
        assertThat(capabilityAuthority.resolveProviderBound(
                CoverImageContracts.CAPABILITY, CoverImageContracts.CAPABILITY_VERSION))
                .as("UNAVAILABLE capability must not resolve to a provider binding")
                .isEmpty();
        assertThat(entry.summary())
                .as("the pending-dispatch reason is the observable summary, not a generic default")
                .contains("SLICE_LOCAL_RUNTIME");
    }

    private Set<String> coverBeans() {
        Set<String> names = new TreeSet<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            String className = type != null
                    ? type.getName()
                    : context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
            if (className != null && className.startsWith("com.example.platform.coverimage.")) {
                // Spring proxies (transactional services, @Configuration classes) carry a $$ suffix;
                // compare the declared type so the allowlist stays exact.
                names.add(className.contains("$$") ? className.substring(0, className.indexOf("$$")) : className);
            }
        }
        return names;
    }

    private Set<String> coverRoutePatterns() {
        Set<String> patterns = new TreeSet<>();
        for (CoverImageController controller : context.getBeansOfType(CoverImageController.class).values()) {
            RequestMapping mapping = controller.getClass().getAnnotation(RequestMapping.class);
            if (mapping != null) {
                patterns.addAll(Set.of(mapping.value()));
            }
        }
        return patterns;
    }
}
