package com.example.platform.workflow.temporal;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Sets {@code spring.temporal.namespace} to {@code media-platform-{env}} when Temporal is enabled and namespace is unset.
 */
public class TemporalNamespaceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "mediaPlatformTemporalNamespace";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("app.temporal.enabled", Boolean.class, false)) {
            return;
        }
        String existing = environment.getProperty("spring.temporal.namespace");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        AppTemporalProperties props = new AppTemporalProperties();
        props.setNamespace(environment.getProperty("app.temporal.namespace", ""));
        props.setEnvironment(environment.getProperty("app.temporal.environment", ""));
        props.setNamespacePrefix(environment.getProperty("app.temporal.namespace-prefix", "media-platform"));

        String resolved = TemporalNamespaceResolver.resolve(
                props,
                environment.getProperty("TEMPORAL_NAMESPACE"),
                environment.getProperty("PLATFORM_ENV", environment.getProperty("platform.environment")));

        Map<String, Object> map = new HashMap<>();
        map.put("spring.temporal.namespace", resolved);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }
}
