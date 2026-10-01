package com.example.platform;

import com.example.platform.datasource.DataSourceConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.platform.app",
    // COVER-PROVIDER-FINAL-FIX-001: the cover capability's API-side admission surface
    // (CoverImageController + CoverImageService + CoverImageTaskStore) lives in this package. The
    // worker-only beans of the same package are gated on platform.runtime.role=WORKER, so the API
    // process registers the controller/service and never the provider runtime.
    "com.example.platform.coverimage",
    // COVER-THUMBNAIL-UNIFY-001: the single capability-neutral ffmpeg frame-extract contribution
    // (one PluginDescriptor declaring media.cover-image + media.thumbnail) lives in this package on the
    // platform (API) side. Its worker-only provider/adapter beans are gated on
    // platform.runtime.role=WORKER, so the API process registers only the registration.
    "com.example.platform.frameextract",
    // THUMBNAIL-SLICE-API-WIRING-001: the thumbnail capability's API-side admission surface
    // (ThumbnailController + ThumbnailService + ThumbnailTaskStore + ThumbnailArtifactReadService)
    // lives in this package. The worker-only beans of the same package are gated on
    // platform.runtime.role=WORKER (the same mechanism the cover slice uses), so the API process
    // registers the admission surface and never the provider runtime.
    "com.example.platform.thumbnail",
    "com.example.platform.security",
    "com.example.platform.production",
    "com.example.platform.render",
    "com.example.platform.shared",
    "com.example.platform.storage",
    "com.example.platform.audit",
    "com.example.platform.notification",
    "com.example.platform.workflow",
    "com.example.platform.identity",
    "com.example.platform.artifact",
    "com.example.platform.media",
    "com.example.platform.billing",
    "com.example.platform.entitlement",
    "com.example.platform.policy",
    "com.example.platform.ai",
    "com.example.platform.datasource",
    "com.example.platform.config",
    "com.example.platform.openapi",
    "com.example.platform.commerce",
    "com.example.platform.delivery",
    "com.example.platform.payment",
    "com.example.platform.extension",
    "com.example.platform.observability",
    "com.example.platform.outbox",
    "com.example.platform.scheduler",
    "com.example.platform.prompt",
    "com.example.platform.social",
    "com.example.platform.federation",
    "com.example.platform.secrets",
    "com.example.platform.web",
    "com.example.platform.ingest",
    "com.example.platform.timeline",
    "com.example.platform.operation",
    "com.example.platform.health",
    "com.example.platform.workerfabric",
    "com.example.platform.composition"
    },
    // Mirrors the filter @SpringBootApplication installs by default: an explicit @ComponentScan must
    // opt in to it, otherwise @TestComponent/@TestConfiguration classes living on the test classpath
    // inside a scanned package are pulled into the context. This is required now that the thumbnail
    // slice is scanned (its package carries test fixtures, the same reason the thumbnail worker
    // application opts in). Test-only classes never exist in the production jar, so production
    // registration is unchanged.
    excludeFilters = @ComponentScan.Filter(
            type = FilterType.CUSTOM, classes = org.springframework.boot.context.TypeExcludeFilter.class))
@EnableScheduling
@Import({BuiltinDataBootstrapRunner.class,
    com.example.platform.lifecycle.PlatformGracefulShutdownCoordinator.class,
    com.example.platform.lifecycle.TemporalWorkerHealthIndicator.class,
    com.example.platform.analytics.AnalyticsConfiguration.class, com.example.platform.cloudresource.CloudResourceConfiguration.class, com.example.platform.marketplace.MarketplaceConfiguration.class, DslContextConfiguration.class, DataSourceConfiguration.class, PlatformBeanConfiguration.class, FlywayConfiguration.class})
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
