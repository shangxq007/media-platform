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
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.example.platform.thumbnail",
            "com.example.platform.media",
            "com.example.platform.storage",
            "com.example.platform.artifact",
            "com.example.platform.datasource",
            "com.example.platform.identity",
            "com.example.platform.entitlement",
            "com.example.platform.policy",
            "com.example.platform.shared",
            "com.example.platform.providerplugin",
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
    StorageModuleConfiguration.class,
    StorageRootConfiguration.class,
    TemporalEnablementConfiguration.class,
    PlatformRuntimeRoleGuard.class
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
