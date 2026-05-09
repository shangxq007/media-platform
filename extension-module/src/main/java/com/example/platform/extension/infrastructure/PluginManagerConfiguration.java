package com.example.platform.extension.infrastructure;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class PluginManagerConfiguration {
    @Bean
    public PluginManager pluginManager(@Value("${app.extensions.plugins-dir:./plugins}") String pluginsDir) {
        return new DefaultPluginManager(Path.of(pluginsDir));
    }
}
