package com.example.platform.thumbnail;

import com.example.platform.datasource.DataSourceConfiguration;
import com.example.platform.runtime.PlatformRuntimeRoleGuard;
import com.example.platform.storage.infrastructure.StorageModuleConfiguration;
import com.example.platform.storage.infrastructure.StorageRootConfiguration;
import com.example.platform.workflow.temporal.TemporalEnablementConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Worker-only entry point. It deliberately scans no web/security package and starts
 * with WebApplicationType.NONE. Temporal auto-discovery is restricted by the worker
 * profile to the thumbnail package, so this process owns only the existing thumbnail
 * workflow/activity contract on media-platform-tasks.
 *
 * <p>The scan is worker-scoped, mirroring {@code CoverImageWorkerApplication}: the API-level
 * {@code com.example.platform.config} package is NOT scanned (it assembles the PF4J provider host, the
 * API-side composition materialization port and a fixed LocalProcess/BMF execution-backend
 * composition — none of which a thumbnail worker owns), and neither are the identity, entitlement,
 * policy, media and provider-plugin packages, whose services are API-side authorities (identity
 * services alone require an {@code AuditPort} the worker never assembles). The shared clock
 * configuration is imported explicitly instead because storage/worker-fabric beans require a
 * {@code Clock}.
 *
 * <p>The thumbnail HTTP-surface beans of the scanned package ({@link ThumbnailController},
 * {@link ThumbnailService}, {@link ThumbnailArtifactReadService}) are excluded: they belong to the
 * API process and depend on the Artifact HTTP project-authorization port, which a web-disabled
 * worker role never assembles.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.example.platform.thumbnail",
            "com.example.platform.storage",
            "com.example.platform.artifact",
            "com.example.platform.datasource",
            "com.example.platform.shared",
            "com.example.platform.workerfabric",
            "com.example.platform.outbox"
        },
        excludeFilters = {
            @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                    "com\\.example\\.platform\\..*Controller",
                    "com\\.example\\.platform\\.security\\..*"
                }),
            // Worker-role boundary: the thumbnail HTTP-surface beans belong to the API process. Their
            // canonical authorization boundary (ArtifactProjectAuthorizationPort) is an Artifact HTTP
            // surface port that requires an authenticated actor, and the worker role scans no web or
            // security package, so assembling them here would fail the worker context closed with no
            // qualifying bean. The worker owns only the workflow, activity, task store and commit path.
            @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {ThumbnailService.class, ThumbnailArtifactReadService.class}),
            // Mirrors the filter @SpringBootApplication installs by default: an explicit
            // @ComponentScan must opt in to it, otherwise @TestComponent/@TestConfiguration classes
            // living on the test classpath inside this scanned package are pulled into the worker bean
            // graph (they are never part of the production jar, so this only affects test contexts).
            @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = org.springframework.boot.context.TypeExcludeFilter.class)
        })
@Import({
    DataSourceConfiguration.class,
    StorageModuleConfiguration.class,
    StorageRootConfiguration.class,
    TemporalEnablementConfiguration.class,
    PlatformRuntimeRoleGuard.class,
    com.example.platform.config.PlatformClockConfiguration.class
})
public final class ThumbnailWorkerApplication {
    private ThumbnailWorkerApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(ThumbnailWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("thumbnail-worker", "temporal")
                .run(args);
    }
}
