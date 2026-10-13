package com.example.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityConfiguration;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Phase 2 acceptance P2-C (the provider catalog bean is reachable under COMBINED) and P2-F
 * (the Provider Interoperability Authority is injectable under COMBINED), plus the API regression
 * that the catalog stays unreachable for the API role.
 */
class CombinedRuntimeAssemblyTest {

    @TempDir Path temp;

    @Test
    void combinedRoleMakesTheProviderCatalogReachable() throws Exception {
        // P2-C
        Path plugins = Files.createDirectory(temp.resolve("plugins"));
        new ApplicationContextRunner()
                .withUserConfiguration(FfmpegWorkerPluginConfiguration.class)
                .withPropertyValues(
                        "platform.runtime.role=COMBINED",
                        "platform.ffmpeg-worker.plugins-directory=" + plugins,
                        "platform.ffmpeg-worker.workspace-root=" + temp.resolve("workspace"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderPluginCatalog.class);
                });
    }

    @Test
    void apiRoleStillHasNoProviderCatalog() throws Exception {
        // regression: the catalog condition remains unreachable for API
        Path plugins = Files.createDirectory(temp.resolve("plugins"));
        new ApplicationContextRunner()
                .withUserConfiguration(FfmpegWorkerPluginConfiguration.class)
                .withPropertyValues(
                        "platform.runtime.role=API",
                        "platform.ffmpeg-worker.plugins-directory=" + plugins,
                        "platform.ffmpeg-worker.workspace-root=" + temp.resolve("workspace"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProviderPluginCatalog.class);
                });
    }

    @Test
    void combinedRoleCanInjectTheInteroperabilityAuthority() {
        // P2-F: the Phase 1 authority carries no role condition, so it is injectable under COMBINED.
        new ApplicationContextRunner()
                .withUserConfiguration(ProviderInteroperabilityConfiguration.class)
                .withPropertyValues(
                        "platform.runtime.role=COMBINED",
                        "platform.provider-interoperability.format=p25a.provider-interoperability.v1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderInteroperabilityAuthority.class);
                    assertThat(context.getBean(ProviderInteroperabilityAuthority.class).declarations())
                            .isEmpty();
                });
    }
}
