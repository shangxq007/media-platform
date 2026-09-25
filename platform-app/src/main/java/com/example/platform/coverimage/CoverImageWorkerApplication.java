package com.example.platform.coverimage;

import com.example.platform.datasource.DataSourceConfiguration;
import com.example.platform.workflow.temporal.TemporalEnablementConfiguration;
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
            "com.example.platform.outbox",
            "com.example.platform.config"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                    "com\\.example\\.platform\\..*Controller",
                    "com\\.example\\.platform\\.security\\..*"
                }))
@Import({
    DataSourceConfiguration.class,
    TemporalEnablementConfiguration.class
})
public final class CoverImageWorkerApplication {

    private CoverImageWorkerApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(CoverImageWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("cover-image-worker", "temporal")
                .run(args);
    }
}
