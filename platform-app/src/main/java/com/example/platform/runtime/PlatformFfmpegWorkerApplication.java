package com.example.platform.runtime;

import com.example.platform.datasource.DataSourceConfiguration;
import com.example.platform.storage.infrastructure.StorageModuleConfiguration;
import com.example.platform.storage.infrastructure.StorageRootConfiguration;
import com.example.platform.thumbnail.ThumbnailArtifactReadService;
import com.example.platform.thumbnail.ThumbnailService;
import com.example.platform.workflow.temporal.TemporalEnablementConfiguration;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * The single runtime-grouped platform ffmpeg worker (COVER-THUMBNAIL-REBUILD-001, action 3;
 * {@code EXEC-CAN-009}).
 *
 * <p>The worker is grouped by <em>runtime</em>, not by capability enumeration: one process hosts the
 * ffmpeg-based {@code media.cover-image} and {@code media.thumbnail} capabilities (both run the same
 * {@code ffmpeg.cpu.frame-extract.v1} provider under the same bubblewrap sandbox on the same
 * {@code media-platform-tasks} queue). It merges the retired {@code CoverImageWorkerApplication} and
 * {@code ThumbnailWorkerApplication}.
 *
 * <p>Web application type is NONE, no controller/security package is scanned, Temporal auto-discovery
 * is limited to the two capability packages, and exactly one worker is registered on the canonical
 * queue. The API process never starts this context, and this context never starts the API.
 *
 * <p>Worker-role wiring: the API-level {@code com.example.platform.config} package is NOT scanned;
 * the shared clock configuration is imported explicitly because storage/worker-fabric beans require a
 * {@code Clock}. The worker profile is applied <em>after</em> {@code temporal} so its single-queue
 * worker list wins by profile precedence (additional profiles are prepended to the active list, so
 * the worker profile must be declared last here). {@link PlatformRuntimeRoleGuard} fails the role
 * closed unless the discovery packages are exactly the ffmpeg capability packages, the queue is
 * canonical, HTTP is disabled and the sandbox executables exist.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ComponentScan(
        basePackages = {
            "com.example.platform.coverimage",
            "com.example.platform.frameextract",
            "com.example.platform.thumbnail",
            "com.example.platform.storage",
            "com.example.platform.artifact",
            "com.example.platform.datasource",
            "com.example.platform.shared",
            "com.example.platform.workerfabric",
            "com.example.platform.outbox",
            // P2-5b-2a-1-2-R3b-R2 (owner-authorized exception): the worker-scoped bounded host
            // registration wiring lives with the worker runtime classes. Only worker-profile
            // configurations under this package are picked up (@ConditionalOnProperty WORKER).
            "com.example.platform.runtime"
        },
        excludeFilters = {
            @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                    "com\\.example\\.platform\\..*Controller",
                    "com\\.example\\.platform\\.security\\..*"
                }),
            // The thumbnail HTTP-surface beans belong to the API process (their canonical
            // authorization port requires an authenticated actor the worker never assembles).
            @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {ThumbnailService.class, ThumbnailArtifactReadService.class}),
            // Mirrors the filter @SpringBootApplication installs by default: an explicit
            // @ComponentScan must opt in to it, otherwise @TestConfiguration classes living on the
            // test classpath inside the scanned packages are pulled into the worker bean graph.
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
public final class PlatformFfmpegWorkerApplication {

    /**
     * Worker-role profiles in precedence order. The worker profile is last on purpose: it must
     * override the base {@code application-temporal.yml} worker list.
     */
    public static final List<String> WORKER_PROFILES = List.of("temporal", "ffmpeg-worker");

    private PlatformFfmpegWorkerApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(PlatformFfmpegWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(WORKER_PROFILES.toArray(String[]::new))
                .run(args);
    }
}
