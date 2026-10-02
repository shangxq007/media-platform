package com.example.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.PlatformApplication;
import com.example.platform.extension.api.port.CapabilityRegistryPort;
import com.example.platform.frameextract.FfmpegCpuProvider;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import com.example.platform.frameextract.FrameExtractPlatformProvider;
import com.example.platform.frameextract.FrameExtractPlatformRegistration;
import com.example.platform.shared.capability.CapabilityId;
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
 * API-process registration proof for the thumbnail capability (THUMBNAIL-SLICE-API-WIRING-001),
 * mirroring {@code CoverImageApiContextRegistrationTest}.
 *
 * <p>The API process runs {@link PlatformApplication} with its explicit {@code @ComponentScan}, which
 * <em>suppresses</em> the {@code @SpringBootApplication} default scan. The thumbnail package was
 * missing from that list, so the advertised admission surface
 * ({@code POST/GET /api/tenants/{tenantId}/projects/{projectId}/thumbnails}) was never registered and
 * the worker-only provider runtime was not previously guarded against the API scan.
 *
 * <p>This test boots the real API context (default profile, no Temporal cluster) and asserts the exact
 * thumbnail bean split: the API-side admission surface is registered, and every worker-only thumbnail
 * bean — capability registry, provider, execution backend, commit fence, activities and the worker
 * application itself — is absent. No test-scoped stub is used: the assertion is about the production
 * scan configuration.
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    // Same API-test prerequisites the repository's other PlatformApplication context tests use.
    "app.security.enabled=true",
    "app.security.oauth2.enabled=false",
    "app.security.jwt.secret-key=thumbnail-api-context-key-at-least-256-bits",
    "app.identity.api-key-auth-enabled=false",
    "app.outbox.dispatcher-enabled=false",
    "storage.s3.enabled=false"})
class ThumbnailApiContextRegistrationTest extends PostgresTestContainerSupport {

    /** Exactly the thumbnail beans the API process may own. */
    private static final Set<String> API_SIDE_THUMBNAIL_BEANS = Set.of(
            ThumbnailController.class.getName(),
            ThumbnailService.class.getName(),
            ThumbnailTaskStore.class.getName(),
            ThumbnailArtifactReadService.class.getName());

    /**
     * Exactly the frame-extract beans the API process may own: the one capability-neutral platform
     * registration (COVER-THUMBNAIL-UNIFY-001). Its worker-only provider and execution adapter are
     * gated on {@code platform.runtime.role=WORKER} and must be absent here.
     */
    private static final Set<String> API_SIDE_FRAME_EXTRACT_BEANS = Set.of(
            FrameExtractPlatformRegistration.class.getName());

    @Autowired ConfigurableApplicationContext context;
    @Autowired CapabilityRegistryPort capabilityRegistry;

    @Test
    void apiContextRegistersTheThumbnailAdmissionSurfaceAndNoWorkerOnlyThumbnailBeans() {
        assertThat(context.isActive()).isTrue();

        // The advertised admission surface is really registered in the API process.
        assertThat(context.getBeansOfType(ThumbnailController.class)).hasSize(1);
        assertThat(context.getBeansOfType(ThumbnailService.class)).hasSize(1);
        assertThat(context.getBeansOfType(ThumbnailTaskStore.class)).hasSize(1);
        assertThat(context.getBeansOfType(ThumbnailArtifactReadService.class)).hasSize(1);
        assertThat(thumbnailRoutePatterns())
                .contains("/api/tenants/{tenantId}/projects/{projectId}/thumbnails");

        // Worker-only thumbnail beans must never exist in the API process.
        assertThat(context.getBeansOfType(FrameExtractExecutionAdapter.class)).isEmpty();
        assertThat(context.getBeansOfType(FfmpegCpuProvider.class)).isEmpty();
        assertThat(context.getBeansOfType(ThumbnailExecutionBackend.class)).isEmpty();
        assertThat(context.getBeansOfType(ThumbnailWorkerRuntimeConfiguration.class)).isEmpty();
        assertThat(context.getBeansOfType(ThumbnailCommitService.class)).isEmpty();
        assertThat(context.getBeansOfType(ThumbnailActivitiesImpl.class)).isEmpty();
        assertThat(context.getBeansOfType(
                com.example.platform.runtime.PlatformFfmpegWorkerApplication.class)).isEmpty();

        // The whole thumbnail package contributes nothing else to the API graph.
        assertThat(thumbnailBeans()).isEqualTo(new TreeSet<>(API_SIDE_THUMBNAIL_BEANS));
        // The one capability-neutral frame-extract contribution is the only frameextract API bean.
        assertThat(frameExtractBeans()).isEqualTo(new TreeSet<>(API_SIDE_FRAME_EXTRACT_BEANS));
    }

    /**
     * COVER-THUMBNAIL-UNIFY-001: with the real API context up, the platform capability registry must
     * expose {@code media.thumbnail} for the one frame-extract contribution registered by the API
     * process.
     */
    @Test
    void platformRegistriesExposeMediaThumbnailForTheFrameExtractContribution() {
        assertThat(context.getBeansOfType(FrameExtractPlatformRegistration.class)).hasSize(1);
        assertThat(context.getBean(FrameExtractPlatformRegistration.class).registered())
                .as("the platform process registered the contribution")
                .isTrue();

        var implementations = capabilityRegistry
                .findCapabilityImplementations(CapabilityId.of(ThumbnailContracts.CAPABILITY));
        assertThat(implementations).hasSize(1);
        assertThat(implementations.getFirst().pluginId())
                .isEqualTo(FrameExtractPlatformProvider.PLUGIN_ID);
    }

    private Set<String> thumbnailBeans() {
        Set<String> names = new TreeSet<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            String className = type != null
                    ? type.getName()
                    : context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
            if (className != null && className.startsWith("com.example.platform.thumbnail.")) {
                // Spring proxies (transactional services) carry a $$ suffix; compare the declared type
                // so the allowlist stays exact.
                names.add(className.contains("$$") ? className.substring(0, className.indexOf("$$")) : className);
            }
        }
        return names;
    }

    private Set<String> thumbnailRoutePatterns() {
        Set<String> patterns = new TreeSet<>();
        for (ThumbnailController controller : context.getBeansOfType(ThumbnailController.class).values()) {
            RequestMapping mapping = controller.getClass().getAnnotation(RequestMapping.class);
            if (mapping != null) {
                patterns.addAll(Set.of(mapping.value()));
            }
        }
        return patterns;
    }

    private Set<String> frameExtractBeans() {
        Set<String> names = new TreeSet<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            String className = type != null
                    ? type.getName()
                    : context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
            if (className != null && className.startsWith("com.example.platform.frameextract.")) {
                names.add(className.contains("$$") ? className.substring(0, className.indexOf("$$")) : className);
            }
        }
        return names;
    }
}
