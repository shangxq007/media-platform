package com.example.platform.coverimage;

import com.example.platform.datasource.DataSourceConfiguration;
import com.example.platform.workflow.temporal.TemporalEnablementConfiguration;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * Worker-only entry point for the cover-image capability.
 *
 * <p>Web application type is NONE, no controller or security package is scanned, Temporal
 * auto-discovery is restricted to the cover-image package, and exactly one worker is registered on
 * media-platform-tasks by the cover-image-worker profile. The API process never starts this context,
 * and this context never starts the API.
 *
 * <p>Worker-role wiring (COVER-PROVIDER-001 defects 1 and 2): the API-level
 * {@code com.example.platform.config} package is NOT scanned — it assembles the PF4J provider host,
 * the API-side composition materialization port and a fixed LocalProcess/BMF execution-backend
 * composition, none of which a cover worker owns — while the shared clock configuration is imported
 * explicitly because storage/worker-fabric beans require a {@code Clock}. The worker profile is
 * applied <em>after</em> {@code temporal} so its single-queue worker list wins by profile precedence
 * (additional profiles are prepended to the active list, so the worker profile must be declared last
 * here).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.example.platform.coverimage",
            "com.example.platform.storage",
            "com.example.platform.artifact",
            "com.example.platform.datasource",
            "com.example.platform.shared",
            "com.example.platform.workerfabric",
            "com.example.platform.outbox"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                    "com\\.example\\.platform\\..*Controller",
                    "com\\.example\\.platform\\.security\\..*"
                }))
@Import({
    DataSourceConfiguration.class,
    TemporalEnablementConfiguration.class,
    com.example.platform.config.PlatformClockConfiguration.class
})
public final class CoverImageWorkerApplication {

    /**
     * Worker-role profiles in precedence order. The worker profile is last on purpose: it must
     * override the base {@code application-temporal.yml} worker list (see the class javadoc).
     */
    public static final List<String> WORKER_PROFILES = List.of("temporal", "cover-image-worker");

    private CoverImageWorkerApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(CoverImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(WORKER_PROFILES.toArray(String[]::new))
                .run(args);
    }
}
