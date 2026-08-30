package com.example.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ProviderPluginManagerConfigurationTest {

    @Test
    void externalPf4jDirectoryLoadingIsDisabledByDefault(@TempDir Path writablePlugins)
            throws Exception {
        Files.writeString(writablePlugins.resolve("arbitrary-external.jar"), "not a trusted plugin");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test", java.util.Map.of(
                            "app.extensions.plugins-dir", writablePlugins.toString())));
            context.register(ProviderPluginManagerConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(org.pf4j.PluginManager.class)).isEmpty();
            assertThat(context.getBeansOfType(ProviderPluginHost.class)).isEmpty();
            assertThat(context.getBean(ProviderPluginCatalog.class).contributions()).isEmpty();
            assertThat(Files.exists(writablePlugins.resolve("arbitrary-external.jar"))).isTrue();
        }
    }
}
