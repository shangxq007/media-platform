package com.example.platform.runtime;

import com.example.platform.datasource.DataSourceConfiguration;
import com.example.platform.storage.infrastructure.StorageModuleConfiguration;
import com.example.platform.storage.infrastructure.StorageRootConfiguration;
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

/** Independent non-web ffmpeg worker. It hosts the canonical graph and Artifact-capability
 * activities on the same queue; planning, execution and Artifact owners are shared with API/COMBINED.
 * The worker profile supplies the complete activity/workflow discovery scope and bounded runtime.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ComponentScan(
        basePackages = {
            "com.example.platform.storage",
            "com.example.platform.artifact",
            "com.example.platform.datasource",
            "com.example.platform.shared",
            "com.example.platform.workerfabric",
            "com.example.platform.outbox",
            // P2-5b-2b-1a: the worker-scoped provider plugin wiring and the typed PF4J
            // contribution vocabulary the catalog is projected from.
            "com.example.platform.providerplugin",
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
            // Mirrors the filter @SpringBootApplication installs by default: an explicit
            // @ComponentScan must opt in to it, otherwise @TestConfiguration classes living on the
            // test classpath inside the scanned packages are pulled into the worker bean graph.
            @ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = org.springframework.boot.context.TypeExcludeFilter.class)
        })
@Import({com.example.platform.production.PlatformReadinessHealthIndicator.class,
    com.example.platform.lifecycle.TemporalWorkerHealthIndicator.class,com.example.platform.persistence.binding.JooqBoundGraphInputStore.class,
    com.example.platform.render.api.GraphRenderRuntimeConfiguration.class,
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
